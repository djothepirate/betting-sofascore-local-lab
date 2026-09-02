package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.delivery.JdbcJ7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class J7DeliveryLedgerMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("sofascore_local_lab")
            .withUsername("sofascore_lab")
            .withPassword("integration-test-only");

    private static final AtomicLong SEQUENCE = new AtomicLong(27_000);

    private static JdbcTemplate jdbc;
    private static J7DeliveryLedgerStore store;
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(
                NamedParameterJdbcTemplate.class,
                () -> new NamedParameterJdbcTemplate(dataSource));
        context.registerBean(
                PlatformTransactionManager.class,
                () -> new JdbcTransactionManager(dataSource));
        context.registerBean(JdbcJ7DeliveryLedgerStore.class);
        context.refresh();
        store = context.getBean(J7DeliveryLedgerStore.class);
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @Test
    void claimsAndCompletesOneExactHumanValidatedExportWithoutMutatingJ7() {
        ExportEvidence export = insertValidatedExport();
        Instant startedAt = Instant.parse("2026-09-01T10:00:00Z");

        var claim = store.claim(
                export.exportId(),
                export.fileSha256(),
                export.dataSha256(),
                export.idempotencyKey(),
                1,
                startedAt);

        assertThat(claim.attemptNumber()).isEqualTo(1);
        assertThat(claim.state()).isEqualTo(J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT);
        assertThat(store.find(export.exportId(), export.fileSha256()))
                .hasValueSatisfying(snapshot -> assertThat(snapshot)
                        .extracting(
                                J7DeliveryLedgerStore.DeliverySnapshot::state,
                                J7DeliveryLedgerStore.DeliverySnapshot::attemptCount,
                                J7DeliveryLedgerStore.DeliverySnapshot::idempotencyKey,
                                J7DeliveryLedgerStore.DeliverySnapshot::protocolVersion)
                        .containsExactly(
                                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                                1,
                                export.idempotencyKey(),
                                "1.0"));
        assertThat(jdbc.queryForObject(
                "select protocol_version from j7_delivery where delivery_uuid = ?",
                String.class,
                claim.deliveryId())).isEqualTo("1.0");

        UUID remoteImportId = UUID.fromString("27000000-0000-4000-8000-000000000101");
        Instant completedAt = startedAt.plusSeconds(1);
        var delivered = store.complete(
                claim.deliveryId(),
                claim.attemptNumber(),
                J7DeliveryLedgerStore.DeliveryState.DELIVERED,
                OptionalInt.of(201),
                "IMPORTED",
                Optional.of("c".repeat(64)),
                Optional.of(remoteImportId),
                Optional.of(completedAt),
                completedAt);

        assertThat(delivered.state()).isEqualTo(J7DeliveryLedgerStore.DeliveryState.DELIVERED);
        assertThat(delivered.attemptCount()).isEqualTo(1);
        assertJ7EvidenceUnchanged(export);
        assertThat(jdbc.queryForMap("""
                select terminal_state, http_status, safe_result_code,
                       acknowledgement_sha256, remote_import_id
                from j7_delivery_attempt_result
                where attempt_id = (
                    select id from j7_delivery_attempt where attempt_uuid = ?
                )
                """, jdbc.queryForObject(
                        "select attempt_uuid from j7_delivery_attempt where delivery_id = (select id from j7_delivery where delivery_uuid = ?)",
                        UUID.class,
                        claim.deliveryId())))
                .containsEntry("terminal_state", "DELIVERED")
                .containsEntry("http_status", 201)
                .containsEntry("safe_result_code", "IMPORTED")
                .containsEntry("acknowledgement_sha256", "c".repeat(64))
                .containsEntry("remote_import_id", remoteImportId);

        assertAppendOnlyAndImmutable(claim.deliveryId());
        assertNoSensitiveStorageColumns();
    }

    @Test
    void refusesCandidatesAndEveryIdentityMismatchBeforeClaiming() {
        ExportEvidence candidate = insertCandidateOnly();
        assertThatThrownBy(() -> store.claim(
                candidate.exportId(),
                candidate.fileSha256(),
                candidate.dataSha256(),
                candidate.idempotencyKey(),
                1,
                Instant.parse("2026-09-01T10:10:00Z")))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(J7DeliveryLedgerStore.LedgerFailure.EXPORT_NOT_ELIGIBLE));

        ExportEvidence validated = insertValidatedExport();
        assertThatThrownBy(() -> store.claim(
                validated.exportId(),
                validated.fileSha256(),
                validated.dataSha256(),
                validated.idempotencyKey(),
                1,
                databaseClock().plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");
        assertThatThrownBy(() -> store.claim(
                validated.exportId(),
                "d".repeat(64),
                validated.dataSha256(),
                "j7:" + validated.exportId() + ":sha256:" + "d".repeat(64),
                1,
                Instant.parse("2026-09-01T10:11:00Z")))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(J7DeliveryLedgerStore.LedgerFailure.IDENTITY_MISMATCH));

        assertThat(store.find(candidate.exportId(), candidate.fileSha256())).isEmpty();
        assertThat(store.find(validated.exportId(), validated.fileSha256())).isEmpty();
    }

    @Test
    void keepsOneGlobalInFlightAndRepeatsUnknownOnlyWithTheSameIdentity() {
        ExportEvidence first = insertValidatedExport();
        ExportEvidence second = insertValidatedExport();
        Instant firstStart = Instant.parse("2026-09-01T10:20:00Z");
        var firstClaim = store.claim(
                first.exportId(),
                first.fileSha256(),
                first.dataSha256(),
                first.idempotencyKey(),
                1,
                firstStart);

        assertThatThrownBy(() -> store.claim(
                second.exportId(),
                second.fileSha256(),
                second.dataSha256(),
                second.idempotencyKey(),
                1,
                firstStart.plusMillis(1)))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.ANOTHER_DELIVERY_IN_FLIGHT));

        store.complete(
                firstClaim.deliveryId(),
                1,
                J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                OptionalInt.of(503),
                "HTTP_5XX",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                firstStart.plusSeconds(1));

        Instant repeatStart = firstStart.plusSeconds(2);
        var repeat = store.claim(
                first.exportId(),
                first.fileSha256(),
                first.dataSha256(),
                first.idempotencyKey(),
                2,
                repeatStart);
        assertThat(repeat.deliveryId()).isEqualTo(firstClaim.deliveryId());
        assertThat(repeat.attemptNumber()).isEqualTo(2);
        assertThat(repeat.idempotencyKey()).isEqualTo(firstClaim.idempotencyKey());

        UUID remoteImportId = UUID.fromString("27000000-0000-4000-8000-000000000102");
        store.complete(
                repeat.deliveryId(),
                2,
                J7DeliveryLedgerStore.DeliveryState.DUPLICATE_CONFIRMED,
                OptionalInt.of(200),
                "DUPLICATE",
                Optional.of("e".repeat(64)),
                Optional.of(remoteImportId),
                Optional.of(repeatStart.plusSeconds(1)),
                repeatStart.plusSeconds(1));

        assertThatThrownBy(() -> store.claim(
                first.exportId(),
                first.fileSha256(),
                first.dataSha256(),
                first.idempotencyKey(),
                3,
                repeatStart.plusSeconds(2)))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(J7DeliveryLedgerStore.LedgerFailure.DELIVERY_TERMINAL));

        var secondClaim = store.claim(
                second.exportId(),
                second.fileSha256(),
                second.dataSha256(),
                second.idempotencyKey(),
                1,
                repeatStart.plusSeconds(3));
        store.complete(
                secondClaim.deliveryId(),
                1,
                J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                OptionalInt.of(422),
                "RECEIVER_REJECTED",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                repeatStart.plusSeconds(4));

        assertThat(jdbc.queryForObject(
                "select count(*) from j7_delivery where current_state = 'IN_FLIGHT'",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from j7_delivery_attempt where delivery_id = (select id from j7_delivery where delivery_uuid = ?)",
                        Long.class,
                        firstClaim.deliveryId())).isEqualTo(2);
    }

    @Test
    void refusesAStaleExpectedAttemptAtomicallyWithoutCreatingTheNextAttempt() {
        ExportEvidence export = insertValidatedExport();
        Instant firstStartedAt = Instant.parse("2026-09-01T10:25:00Z");
        var firstClaim = store.claim(
                export.exportId(),
                export.fileSha256(),
                export.dataSha256(),
                export.idempotencyKey(),
                1,
                firstStartedAt);
        store.complete(
                firstClaim.deliveryId(),
                1,
                J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                OptionalInt.empty(),
                "TRANSPORT_TIMEOUT",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                firstStartedAt.plusSeconds(1));

        assertThatThrownBy(() -> store.claim(
                export.exportId(),
                export.fileSha256(),
                export.dataSha256(),
                export.idempotencyKey(),
                1,
                firstStartedAt.plusSeconds(2)))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure
                                        .ATTEMPT_ORDINAL_MISMATCH));

        assertThat(store.find(export.exportId(), export.fileSha256()))
                .hasValueSatisfying(snapshot -> assertThat(snapshot)
                        .extracting(
                                J7DeliveryLedgerStore.DeliverySnapshot::state,
                                J7DeliveryLedgerStore.DeliverySnapshot::attemptCount)
                        .containsExactly(
                                J7DeliveryLedgerStore.DeliveryState
                                        .UNKNOWN_RECONCILIATION_REQUIRED,
                                1));
    }

    @Test
    void serializesTwoConcurrentClaimsAcrossIndependentTransactions() throws Exception {
        ExportEvidence first = insertValidatedExport();
        ExportEvidence second = insertValidatedExport();
        Instant startedAt = databaseClock();
        long deliveryCountBefore = jdbc.queryForObject(
                "select count(*) from j7_delivery", Long.class);
        long unresolvedAttemptCountBefore = jdbc.queryForObject(
                "select count(*) from j7_delivery_attempt attempt left join j7_delivery_attempt_result result on result.attempt_id = attempt.id where result.attempt_id is null",
                Long.class);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> firstOutcome = executor.submit(() -> concurrentClaim(
                    first, startedAt, ready, release));
            Future<Object> secondOutcome = executor.submit(() -> concurrentClaim(
                    second, startedAt, ready, release));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            List<Object> outcomes = List.of(
                    firstOutcome.get(10, TimeUnit.SECONDS),
                    secondOutcome.get(10, TimeUnit.SECONDS));
            assertThat(outcomes)
                    .filteredOn(J7DeliveryLedgerStore.ClaimReceipt.class::isInstance)
                    .hasSize(1);
            assertThat(outcomes)
                    .filteredOn(J7DeliveryLedgerStore.LedgerFailure.class::isInstance)
                    .containsExactly(
                            J7DeliveryLedgerStore.LedgerFailure.ANOTHER_DELIVERY_IN_FLIGHT);
            assertThat(jdbc.queryForObject(
                    "select count(*) from j7_delivery where current_state = 'IN_FLIGHT'",
                    Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from j7_delivery_attempt attempt left join j7_delivery_attempt_result result on result.attempt_id = attempt.id where result.attempt_id is null",
                    Long.class)).isEqualTo(unresolvedAttemptCountBefore + 1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from j7_delivery", Long.class))
                    .isEqualTo(deliveryCountBefore + 1);

            J7DeliveryLedgerStore.ClaimReceipt winner = outcomes.stream()
                    .filter(J7DeliveryLedgerStore.ClaimReceipt.class::isInstance)
                    .map(J7DeliveryLedgerStore.ClaimReceipt.class::cast)
                    .findFirst()
                    .orElseThrow();
            store.complete(
                    winner.deliveryId(),
                    winner.attemptNumber(),
                    J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                    OptionalInt.of(422),
                    "CONCURRENT_TEST_COMPLETE",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    startedAt.plusSeconds(1));
        }
        finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rejectsUnknownFourXxAndInvalidResultShapesInJavaAndSql() {
        ExportEvidence export = insertValidatedExport();
        Instant startedAt = databaseClock();
        var claim = store.claim(
                export.exportId(), export.fileSha256(), export.dataSha256(),
                export.idempotencyKey(), 1, startedAt);
        Instant completedAt = startedAt.plusSeconds(1);

        assertThatThrownBy(() -> store.complete(
                claim.deliveryId(),
                claim.attemptNumber(),
                J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                OptionalInt.of(429),
                "HTTP_4XX_AMBIGUOUS",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                completedAt))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.INVALID_COMPLETION));

        long attemptDatabaseId = jdbc.queryForObject(
                "select id from j7_delivery_attempt where delivery_id = (select id from j7_delivery where delivery_uuid = ?)",
                Long.class,
                claim.deliveryId());
        assertDirectResultRejected(
                attemptDatabaseId, "UNKNOWN_RECONCILIATION_REQUIRED", 429,
                false, false, completedAt);
        assertDirectResultRejected(
                attemptDatabaseId, "DELIVERED", 200,
                true, true, completedAt);
        assertDirectResultRejected(
                attemptDatabaseId, "DUPLICATE_CONFIRMED", 201,
                true, true, completedAt);
        assertDirectResultRejected(
                attemptDatabaseId, "REJECTED_TERMINAL", 503,
                false, false, completedAt);
        assertDirectResultRejected(
                attemptDatabaseId, "DELIVERED", 201,
                true, false, completedAt);
        assertThat(jdbc.queryForObject(
                "select count(*) from j7_delivery_attempt_result where attempt_id = ?",
                Long.class,
                attemptDatabaseId)).isZero();

        store.complete(
                claim.deliveryId(),
                claim.attemptNumber(),
                J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                OptionalInt.of(429),
                "HTTP_4XX_REJECTED",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                completedAt);
    }

    @Test
    void reconcilesOnlyTheExactStaleInFlightAttemptBeforeAnyManualRepeat() {
        ExportEvidence export = insertValidatedExport();
        Instant startedAt = databaseClock();
        var claim = store.claim(
                export.exportId(),
                export.fileSha256(),
                export.dataSha256(),
                export.idempotencyKey(),
                1,
                startedAt);

        assertThatThrownBy(() -> store.reconcileStaleInFlightAsUnknown(
                claim.deliveryId(),
                claim.attemptNumber(),
                claim.startedAt()))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.ATTEMPT_NOT_STALE));
        assertThatThrownBy(() -> store.reconcileStaleInFlightAsUnknown(
                claim.deliveryId(),
                claim.attemptNumber(),
                claim.startedAt().plusMillis(1)))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.ATTEMPT_NOT_ACTIVE));

        jdbc.execute("select pg_sleep(30.1)");
        var unknown = store.reconcileStaleInFlightAsUnknown(
                claim.deliveryId(),
                claim.attemptNumber(),
                claim.startedAt());

        assertThat(unknown.state()).isEqualTo(
                J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(jdbc.queryForMap("""
                select terminal_state, http_status, safe_result_code
                from j7_delivery_attempt_result
                where attempt_id = (
                    select id from j7_delivery_attempt
                    where delivery_id = (
                        select id from j7_delivery where delivery_uuid = ?
                    ) and attempt_number = 1
                )
                """, claim.deliveryId()))
                .containsEntry("terminal_state", "UNKNOWN_RECONCILIATION_REQUIRED")
                .containsEntry("http_status", null)
                .containsEntry(
                        "safe_result_code", "OPERATOR_RECONCILED_STALE_IN_FLIGHT");

        Instant repeatStartedAt = databaseClock();
        var repeat = store.claim(
                export.exportId(),
                export.fileSha256(),
                export.dataSha256(),
                export.idempotencyKey(),
                2,
                repeatStartedAt);
        assertThat(repeat.attemptNumber()).isEqualTo(2);
        store.complete(
                repeat.deliveryId(),
                repeat.attemptNumber(),
                J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                OptionalInt.of(422),
                "RECONCILIATION_TEST_COMPLETE",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                repeatStartedAt.plusSeconds(1));
        assertJ7EvidenceUnchanged(export);
    }

    @Test
    void startsTheStaleAgeAtTheActualAttemptInsertAfterAThirtySecondLockWait()
            throws Exception {
        ExportEvidence export = insertValidatedExport();
        Instant startedAt = databaseClock();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<J7DeliveryLedgerStore.ClaimReceipt> pendingClaim = null;

        try (Connection lockConnection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            lockConnection.setAutoCommit(false);
            try (var statement = lockConnection.createStatement()) {
                statement.execute(
                        "select pg_advisory_xact_lock(hashtextextended('J7_DELIVERY_GLOBAL', 2700))");
            }

            pendingClaim = executor.submit(() -> store.claim(
                    export.exportId(),
                    export.fileSha256(),
                    export.dataSha256(),
                    export.idempotencyKey(),
                    1,
                    startedAt));
            Instant waiterObservedAt = awaitGlobalAdvisoryLockWaiter();

            try (var statement = lockConnection.createStatement()) {
                statement.execute("select pg_sleep(30.1)");
            }
            assertThat(pendingClaim.isDone()).isFalse();
            lockConnection.commit();

            J7DeliveryLedgerStore.ClaimReceipt claim = pendingClaim.get(
                    10, TimeUnit.SECONDS);
            Instant attemptCreatedAt = jdbc.queryForObject("""
                    select attempt.created_at
                    from j7_delivery_attempt attempt
                    join j7_delivery delivery on delivery.id = attempt.delivery_id
                    where delivery.delivery_uuid = ?
                      and attempt.attempt_number = ?
                    """, OffsetDateTime.class, claim.deliveryId(), claim.attemptNumber())
                    .toInstant();
            assertThat(attemptCreatedAt)
                    .isAfterOrEqualTo(waiterObservedAt.plusSeconds(30));

            assertThatThrownBy(() -> store.reconcileStaleInFlightAsUnknown(
                    claim.deliveryId(),
                    claim.attemptNumber(),
                    claim.startedAt()))
                    .isInstanceOfSatisfying(
                            J7DeliveryLedgerStore.LedgerException.class,
                            exception -> assertThat(exception.failure()).isEqualTo(
                                    J7DeliveryLedgerStore.LedgerFailure.ATTEMPT_NOT_STALE));

            Instant completedAt = databaseClock();
            store.complete(
                    claim.deliveryId(),
                    claim.attemptNumber(),
                    J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                    OptionalInt.of(422),
                    "LOCK_WAIT_TEST_COMPLETE",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    completedAt);
        }
        finally {
            if (pendingClaim != null) {
                pendingClaim.cancel(true);
            }
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Instant awaitGlobalAdvisoryLockWaiter() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiters = jdbc.queryForObject("""
                    select count(*)
                    from pg_stat_activity
                    where datname = current_database()
                      and pid <> pg_backend_pid()
                      and wait_event_type = 'Lock'
                      and query like 'select pg_advisory_xact_lock%'
                    """, Integer.class);
            if (waiters != null && waiters > 0) {
                return databaseClock();
            }
            Thread.sleep(10);
        }
        throw new AssertionError("claim did not wait on the global advisory lock");
    }

    private static Instant databaseClock() {
        OffsetDateTime value = jdbc.queryForObject(
                "select clock_timestamp()", OffsetDateTime.class);
        if (value == null) {
            throw new IllegalStateException("PostgreSQL clock is unavailable");
        }
        return value.toInstant();
    }

    @Test
    void databaseGuardsRejectDirectBypassesOfEligibilityAndGlobalConcurrency() {
        ExportEvidence candidate = insertCandidateOnly();
        assertThatThrownBy(() -> jdbc.update("""
                insert into j7_delivery (
                    delivery_uuid, export_manifest_id, export_uuid,
                    file_sha256, data_sha256, file_size_bytes,
                    idempotency_key, protocol_version,
                    current_state, created_at, state_changed_at
                ) select ?, id, export_uuid, content_sha256, data_sha256,
                         content_size_bytes, ?, '1.0', 'NOT_ATTEMPTED', now(), now()
                  from export_manifest where export_uuid = ?
                """,
                UUID.randomUUID(),
                candidate.idempotencyKey(),
                candidate.exportId()))
                .isInstanceOf(DataAccessException.class);

        ExportEvidence first = insertValidatedExport();
        ExportEvidence second = insertValidatedExport();
        var firstClaim = store.claim(
                first.exportId(), first.fileSha256(), first.dataSha256(),
                first.idempotencyKey(), 1, Instant.parse("2026-09-01T10:30:00Z"));
        UUID secondDeliveryId = UUID.randomUUID();
        UUID secondAttemptId = UUID.randomUUID();
        OffsetDateTime secondStartedAt = OffsetDateTime.parse("2026-09-01T10:30:00.001Z");
        assertThat(jdbc.update("""
                insert into j7_delivery (
                    delivery_uuid, export_manifest_id, export_uuid,
                    file_sha256, data_sha256, file_size_bytes,
                    idempotency_key, protocol_version,
                    current_state, created_at, state_changed_at
                ) select ?, id, export_uuid, content_sha256, data_sha256,
                         content_size_bytes, ?, '1.0', 'NOT_ATTEMPTED', ?, ?
                  from export_manifest where export_uuid = ?
                """,
                secondDeliveryId,
                second.idempotencyKey(),
                secondStartedAt,
                secondStartedAt,
                second.exportId())).isEqualTo(1);
        long secondDatabaseId = jdbc.queryForObject(
                "select id from j7_delivery where delivery_uuid = ?",
                Long.class,
                secondDeliveryId);
        assertThat(jdbc.update("""
                insert into j7_delivery_attempt (
                    attempt_uuid, delivery_id, attempt_number, started_at
                ) values (?, ?, 1, ?)
                """,
                secondAttemptId,
                secondDatabaseId,
                secondStartedAt)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                update j7_delivery
                set current_state = 'IN_FLIGHT', state_changed_at = ?
                where delivery_uuid = ?
                """, secondStartedAt, secondDeliveryId))
                .isInstanceOf(DataAccessException.class);
        store.complete(
                firstClaim.deliveryId(),
                1,
                J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                OptionalInt.empty(),
                "TRANSPORT_FAILURE",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Instant.parse("2026-09-01T10:30:01Z"));
    }

    @Test
    void rollsBackDeliveryAndAttemptWhenTheAtomicClaimCannotReachInFlight() {
        ExportEvidence export = insertValidatedExport();
        long attemptCountBefore = jdbc.queryForObject(
                "select count(*) from j7_delivery_attempt",
                Long.class);
        jdbc.execute("""
                create function fail_test_j7_delivery_claim()
                returns trigger
                language plpgsql
                as $$
                begin
                    if new.current_state = 'IN_FLIGHT' then
                        raise exception 'synthetic claim failure';
                    end if;
                    return new;
                end;
                $$
                """);
        jdbc.execute("""
                create trigger fail_test_j7_delivery_claim
                before update on j7_delivery
                for each row execute function fail_test_j7_delivery_claim()
                """);
        try {
            assertThatThrownBy(() -> store.claim(
                    export.exportId(),
                    export.fileSha256(),
                    export.dataSha256(),
                    export.idempotencyKey(),
                    1,
                    Instant.parse("2026-09-01T10:40:00Z")))
                    .isInstanceOfSatisfying(
                            J7DeliveryLedgerStore.LedgerException.class,
                            exception -> assertThat(exception.failure()).isEqualTo(
                                    J7DeliveryLedgerStore.LedgerFailure.STORAGE_UNAVAILABLE));
            assertThat(store.find(export.exportId(), export.fileSha256())).isEmpty();
            assertThat(jdbc.queryForObject(
                    "select count(*) from j7_delivery_attempt",
                    Long.class)).isEqualTo(attemptCountBefore);
        }
        finally {
            jdbc.execute("drop trigger if exists fail_test_j7_delivery_claim on j7_delivery");
            jdbc.execute("drop function if exists fail_test_j7_delivery_claim()");
        }
    }

    @Test
    void rollsBackTheAttemptResultWhenTheProjectionUpdateFails() {
        ExportEvidence export = insertValidatedExport();
        Instant startedAt = databaseClock();
        var claim = store.claim(
                export.exportId(), export.fileSha256(), export.dataSha256(),
                export.idempotencyKey(), 1, startedAt);
        jdbc.execute("""
                create function fail_test_j7_delivery_completion()
                returns trigger
                language plpgsql
                as $$
                begin
                    if old.current_state = 'IN_FLIGHT'
                       and new.current_state <> 'IN_FLIGHT' then
                        raise exception 'synthetic completion failure';
                    end if;
                    return new;
                end;
                $$
                """);
        jdbc.execute("""
                create trigger fail_test_j7_delivery_completion
                before update on j7_delivery
                for each row execute function fail_test_j7_delivery_completion()
                """);
        try {
            assertThatThrownBy(() -> store.complete(
                    claim.deliveryId(),
                    claim.attemptNumber(),
                    J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                    OptionalInt.of(422),
                    "SYNTHETIC_COMPLETION_FAILURE",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    startedAt.plusSeconds(1)))
                    .isInstanceOfSatisfying(
                            J7DeliveryLedgerStore.LedgerException.class,
                            exception -> assertThat(exception.failure()).isEqualTo(
                                    J7DeliveryLedgerStore.LedgerFailure.STORAGE_UNAVAILABLE));
            assertThat(jdbc.queryForObject(
                    "select count(*) from j7_delivery_attempt_result result join j7_delivery_attempt attempt on attempt.id = result.attempt_id join j7_delivery delivery on delivery.id = attempt.delivery_id where delivery.delivery_uuid = ?",
                    Long.class,
                    claim.deliveryId())).isZero();
            assertThat(jdbc.queryForObject(
                    "select current_state from j7_delivery where delivery_uuid = ?",
                    String.class,
                    claim.deliveryId())).isEqualTo("IN_FLIGHT");
        }
        finally {
            jdbc.execute("drop trigger if exists fail_test_j7_delivery_completion on j7_delivery");
            jdbc.execute("drop function if exists fail_test_j7_delivery_completion()");
        }
        store.complete(
                claim.deliveryId(),
                claim.attemptNumber(),
                J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                OptionalInt.of(422),
                "COMPLETION_ROLLBACK_QUALIFIED",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                startedAt.plusSeconds(2));
    }

    private static Object concurrentClaim(
            ExportEvidence export,
            Instant startedAt,
            CountDownLatch ready,
            CountDownLatch release) throws InterruptedException {
        ready.countDown();
        if (!release.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent claim release timed out");
        }
        try {
            return store.claim(
                    export.exportId(), export.fileSha256(), export.dataSha256(),
                    export.idempotencyKey(), 1, startedAt);
        }
        catch (J7DeliveryLedgerStore.LedgerException exception) {
            return exception.failure();
        }
    }

    private static void assertDirectResultRejected(
            long attemptDatabaseId,
            String terminalState,
            int httpStatus,
            boolean acknowledgementShaPresent,
            boolean acknowledgementMetadataComplete,
            Instant completedAt) {
        String acknowledgementSha = acknowledgementShaPresent ? "f".repeat(64) : null;
        UUID remoteImportId = acknowledgementMetadataComplete
                ? UUID.fromString("27000000-0000-4000-8000-000000000199")
                : null;
        java.sql.Timestamp acknowledgedAt = acknowledgementMetadataComplete
                ? java.sql.Timestamp.from(completedAt)
                : null;
        assertThatThrownBy(() -> jdbc.update("""
                insert into j7_delivery_attempt_result (
                    attempt_id, terminal_state, http_status, safe_result_code,
                    acknowledgement_sha256, remote_import_id,
                    acknowledgement_received_at, completed_at
                ) values (?, ?, ?, 'DIRECT_INVALID_SHAPE', ?, ?, ?, ?)
                """,
                attemptDatabaseId,
                terminalState,
                httpStatus,
                acknowledgementSha,
                remoteImportId,
                acknowledgedAt,
                java.sql.Timestamp.from(completedAt)))
                .isInstanceOf(DataAccessException.class);
    }

    private static ExportEvidence insertValidatedExport() {
        ExportEvidence evidence = insertCandidateOnly();
        String validatedPath = "j7-" + evidence.canonicalEventId() + "-"
                + evidence.exportId() + ".validated.json";
        Instant decidedAt = Instant.parse("2026-09-01T09:00:00Z");
        assertThat(jdbc.update("""
                update export_manifest
                set decision_intent_status = 'HUMAN_VALIDATED',
                    decision_intent_at = ?,
                    decision_intent_reason = null,
                    decision_intent_path = ?,
                    decision_intent_content_sha256 = ?,
                    decision_intent_content_size_bytes = 2048
                where export_uuid = ?
                  and validation_status = 'COHERENCE_CHECKED'
                """, java.sql.Timestamp.from(decidedAt), validatedPath,
                evidence.fileSha256(), evidence.exportId())).isEqualTo(1);
        assertThat(jdbc.update("""
                update export_manifest
                set export_path = ?,
                    content_sha256 = ?,
                    content_size_bytes = 2048,
                    validation_status = 'HUMAN_VALIDATED',
                    decided_at = ?,
                    decision_reason = null
                where export_uuid = ?
                """, validatedPath, evidence.fileSha256(),
                java.sql.Timestamp.from(decidedAt), evidence.exportId())).isEqualTo(1);
        return evidence;
    }

    private static ExportEvidence insertCandidateOnly() {
        long sequence = SEQUENCE.incrementAndGet();
        UUID canonicalEventId = UUID.nameUUIDFromBytes(("event-" + sequence).getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        UUID exportId = UUID.nameUUIDFromBytes(("export-" + sequence).getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        String dataSha = sha(sequence * 10 + 1);
        String sourceSetSha = sha(sequence * 10 + 2);
        String candidateSha = sha(sequence * 10 + 3);
        String fileSha = sha(sequence * 10 + 4);
        jdbc.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?)
                """, canonicalEventId, sequence);
        String candidatePath = "j7-" + canonicalEventId + "-" + exportId + ".candidate.json";
        jdbc.update("""
                insert into export_manifest (
                    export_kind, export_uuid, canonical_event_id, schema_id,
                    schema_version, generated_at, data_sha256, source_set_sha256,
                    candidate_content_sha256, content_size_bytes, source_observations,
                    export_path, content_sha256, validation_status,
                    source_snapshot_ids, warnings
                ) values (
                    'J7_CANONICAL_EVENT', ?, ?,
                    'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1',
                    '1.0.0', '2026-09-01T08:00:00Z', ?, ?, ?, 1024,
                    cast(? as jsonb), ?, ?, 'COHERENCE_CHECKED',
                    '{}'::bigint[], '[]'::jsonb
                )
                """,
                exportId,
                canonicalEventId,
                dataSha,
                sourceSetSha,
                candidateSha,
                """
                        [
                          {"component":"EVENT_STATE","snapshotId":null},
                          {"component":"EVENT_DETAILS","snapshotId":null},
                          {"component":"EVENT_STATISTICS","snapshotId":null},
                          {"component":"EVENT_INCIDENTS","snapshotId":null},
                          {"component":"EVENT_LINEUPS","snapshotId":null}
                        ]
                        """,
                candidatePath,
                candidateSha);
        return new ExportEvidence(
                canonicalEventId,
                exportId,
                fileSha,
                dataSha,
                "j7:" + exportId + ":sha256:" + fileSha,
                candidateSha);
    }

    private static void assertJ7EvidenceUnchanged(ExportEvidence export) {
        Map<String, Object> row = jdbc.queryForMap("""
                select validation_status, content_sha256, data_sha256,
                       content_size_bytes
                from export_manifest
                where export_uuid = ?
                """, export.exportId());
        assertThat(row)
                .containsEntry("validation_status", "HUMAN_VALIDATED")
                .containsEntry("content_sha256", export.fileSha256())
                .containsEntry("data_sha256", export.dataSha256())
                .containsEntry("content_size_bytes", 2048L);
    }

    private static void assertAppendOnlyAndImmutable(UUID deliveryId) {
        assertThatThrownBy(() -> jdbc.update(
                "update j7_delivery set idempotency_key = ? where delivery_uuid = ?",
                "j7:" + UUID.randomUUID() + ":sha256:" + "f".repeat(64),
                deliveryId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "delete from j7_delivery where delivery_uuid = ?", deliveryId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                update j7_delivery_attempt
                set started_at = started_at + interval '1 second'
                where delivery_id = (select id from j7_delivery where delivery_uuid = ?)
                """, deliveryId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                update j7_delivery_attempt_result
                set safe_result_code = 'CHANGED'
                where attempt_id = (
                    select id from j7_delivery_attempt
                    where delivery_id = (select id from j7_delivery where delivery_uuid = ?)
                )
                """, deliveryId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                delete from j7_delivery_attempt_result
                where attempt_id = (
                    select id from j7_delivery_attempt
                    where delivery_id = (select id from j7_delivery where delivery_uuid = ?)
                )
                """, deliveryId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                delete from j7_delivery_attempt
                where delivery_id = (select id from j7_delivery where delivery_uuid = ?)
                """, deliveryId)).isInstanceOf(DataAccessException.class);
    }

    private static void assertNoSensitiveStorageColumns() {
        assertThat(jdbc.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name in (
                      'j7_delivery',
                      'j7_delivery_attempt',
                      'j7_delivery_attempt_result'
                  )
                order by table_name, ordinal_position
                """, String.class))
                .noneMatch(name -> name.matches(
                        ".*(payload|body|diagnostic|cookie|token|secret|private_key|certificate).*"));
    }

    private static String sha(long value) {
        return String.format("%064x", value);
    }

    private record ExportEvidence(
            UUID canonicalEventId,
            UUID exportId,
            String fileSha256,
            String dataSha256,
            String idempotencyKey,
            String candidateSha256) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }
}
