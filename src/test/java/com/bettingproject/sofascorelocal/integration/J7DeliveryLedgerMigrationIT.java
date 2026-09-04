package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.delivery.JdbcJ7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
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
    private static NamedParameterJdbcTemplate namedJdbc;
    private static TransactionTemplate transactionTemplate;
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
        namedJdbc = context.getBean(NamedParameterJdbcTemplate.class);
        transactionTemplate = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
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
        Instant initialRemoteReceivedAt = firstStart.plusMillis(250);
        store.complete(
                repeat.deliveryId(),
                2,
                J7DeliveryLedgerStore.DeliveryState.DUPLICATE_CONFIRMED,
                OptionalInt.of(200),
                "DUPLICATE",
                Optional.of("e".repeat(64)),
                Optional.of(remoteImportId),
                Optional.of(initialRemoteReceivedAt),
                repeatStart.plusSeconds(1));

        assertThat(jdbc.queryForObject("""
                select acknowledgement_received_at
                from j7_delivery_attempt_result result
                join j7_delivery_attempt attempt on attempt.id = result.attempt_id
                where attempt.delivery_id = (
                    select id from j7_delivery where delivery_uuid = ?
                )
                  and attempt.attempt_number = 2
                """, OffsetDateTime.class, repeat.deliveryId()).toInstant())
                .isEqualTo(initialRemoteReceivedAt);

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
    void persistsImportedAcknowledgementFromAnIndependentReceiverClock() {
        ExportEvidence export = insertValidatedExport();
        Instant senderStartedAt = Instant.parse("2026-09-03T10:00:00Z");
        var claim = store.claim(
                export.exportId(),
                export.fileSha256(),
                export.dataSha256(),
                export.idempotencyKey(),
                1,
                senderStartedAt);
        Instant receiverReceivedAt = senderStartedAt.plusSeconds(120);
        Instant senderCompletedAt = senderStartedAt.plusSeconds(1);

        var completed = store.complete(
                claim.deliveryId(),
                claim.attemptNumber(),
                J7DeliveryLedgerStore.DeliveryState.DELIVERED,
                OptionalInt.of(201),
                "IMPORTED_DISTINCT_RECEIVER_CLOCK",
                Optional.of("d".repeat(64)),
                Optional.of(UUID.fromString("27000000-0000-4000-8000-000000000103")),
                Optional.of(receiverReceivedAt),
                senderCompletedAt);

        assertThat(completed.state()).isEqualTo(J7DeliveryLedgerStore.DeliveryState.DELIVERED);
        assertThat(jdbc.queryForObject("""
                select acknowledgement_received_at
                from j7_delivery_attempt_result result
                join j7_delivery_attempt attempt on attempt.id = result.attempt_id
                where attempt.delivery_id = (
                    select id from j7_delivery where delivery_uuid = ?
                )
                """, OffsetDateTime.class, claim.deliveryId()).toInstant())
                .isEqualTo(receiverReceivedAt);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0001-01-01T00:00:00Z",
            "9999-12-31T23:59:59.999999Z"
    })
    void persistsAndReadsBackTheExactReceiverTimestampBoundaries(String boundary) {
        ExportEvidence export = insertValidatedExport();
        Instant senderStartedAt = databaseClock();
        var claim = store.claim(
                export.exportId(),
                export.fileSha256(),
                export.dataSha256(),
                export.idempotencyKey(),
                1,
                senderStartedAt);
        Instant receiverReceivedAt = Instant.parse(boundary);

        var completed = store.complete(
                claim.deliveryId(),
                claim.attemptNumber(),
                J7DeliveryLedgerStore.DeliveryState.DELIVERED,
                OptionalInt.of(201),
                "IMPORTED_BOUNDARY_RECEIVER_CLOCK",
                Optional.of("d".repeat(64)),
                Optional.of(UUID.randomUUID()),
                Optional.of(receiverReceivedAt),
                senderStartedAt.plusSeconds(1));

        assertThat(completed.state()).isEqualTo(J7DeliveryLedgerStore.DeliveryState.DELIVERED);
        assertThat(jdbc.queryForObject("""
                select acknowledgement_received_at
                from j7_delivery_attempt_result result
                join j7_delivery_attempt attempt on attempt.id = result.attempt_id
                where attempt.delivery_id = (
                    select id from j7_delivery where delivery_uuid = ?
                )
                """, OffsetDateTime.class, claim.deliveryId()).toInstant())
                .isEqualTo(receiverReceivedAt);
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
                J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                OptionalInt.of(422),
                "LOCAL_COMPLETION_PRECEDES_START",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                startedAt.minusNanos(1_000)))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.ATTEMPT_NOT_ACTIVE));

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

        assertThatThrownBy(() -> store.complete(
                claim.deliveryId(),
                claim.attemptNumber(),
                J7DeliveryLedgerStore.DeliveryState.DELIVERED,
                OptionalInt.of(201),
                "NON_PERSISTABLE_RECEIVER_TIMESTAMP",
                Optional.of("d".repeat(64)),
                Optional.of(UUID.fromString("27000000-0000-4000-8000-000000000104")),
                Optional.of(Instant.parse("2026-09-03T10:00:00.123456789Z")),
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
    void registersAndAtomicallyConsumesOneExactProviderDerivedOwnerGo() {
        ExportEvidence export = insertProviderValidatedExport();
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant(
                export, now.minusSeconds(30), now.plusSeconds(300));

        var registered = store.registerProviderDerivedOwnerGo(grant);
        assertThat(registered.status()).isEqualTo(J7ProviderDerivedOwnerGo.Status.AVAILABLE);
        Map<String, Object> canonicalEvidence = jdbc.queryForMap("""
                select j7_provider_owner_go_canonical_block(owner_go) as canonical_block,
                       pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
                           j7_provider_owner_go_canonical_block(owner_go), 'UTF8')), 'hex')
                           as computed_hash
                from j7_provider_delivery_owner_go_grant owner_go
                where owner_go.go_uuid = ?
                """, grant.goId());
        assertThat(canonicalEvidence.get("canonical_block"))
                .isEqualTo(grant.canonicalDecisionBlock());
        assertThat((String) canonicalEvidence.get("canonical_block"))
                .doesNotContain("\r")
                .endsWith("\n");
        assertThat(canonicalEvidence.get("computed_hash"))
                .isEqualTo(grant.ownerDecisionBlockSha256());
        assertThat(store.findProviderDerivedOwnerGo(grant.reference()))
                .hasValueSatisfying(snapshot -> assertThat(snapshot.grant()).isEqualTo(grant));

        var claim = store.claimProviderDerived(new J7ProviderDerivedOwnerGo.Claim(
                grant, export.idempotencyKey(), now));
        assertThat(claim.attemptNumber()).isEqualTo(1);
        assertThat(claim.startedAt()).isBetween(now, databaseClock());
        assertThat(jdbc.queryForObject("""
                select payload_class
                from j7_delivery_attempt
                where delivery_id = (
                    select id from j7_delivery where delivery_uuid = ?
                )
                """, String.class, claim.deliveryId())).isEqualTo("PROVIDER_DERIVED");
        assertThat(jdbc.queryForObject("""
                select count(*)
                from j7_provider_delivery_owner_go_consumption consumption
                join j7_delivery_attempt attempt on attempt.id = consumption.attempt_id
                join j7_delivery delivery on delivery.id = attempt.delivery_id
                where delivery.delivery_uuid = ?
                """, Long.class, claim.deliveryId())).isOne();
        assertThat(store.findProviderDerivedOwnerGo(grant.reference()))
                .hasValueSatisfying(snapshot -> {
                    assertThat(snapshot.status())
                            .isEqualTo(J7ProviderDerivedOwnerGo.Status.CONSUMED);
                    assertThat(snapshot.deliveryId()).contains(claim.deliveryId());
                    assertThat(snapshot.attemptNumber()).hasValue(1);
                });

        assertThatThrownBy(() -> store.claimProviderDerived(
                new J7ProviderDerivedOwnerGo.Claim(
                        grant, export.idempotencyKey(), databaseClock())))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.OWNER_GO_CONSUMED));
        assertThatThrownBy(() -> store.revokeProviderDerivedOwnerGo(
                grant.reference(), "a".repeat(64)))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.OWNER_GO_CONSUMED));
        assertThat(jdbc.queryForObject("select count(*) from j7_delivery_attempt where delivery_id = (select id from j7_delivery where delivery_uuid = ?)",
                Long.class, claim.deliveryId())).isOne();

        store.complete(
                claim.deliveryId(),
                1,
                J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                OptionalInt.empty(),
                "SYNTHETIC_LOOPBACK_NOT_EXECUTED",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                claim.startedAt().plusSeconds(1));
    }

    @Test
    void derivesWindowStatusFromTheDatabaseClockAndPersistsExactRevocation() {
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant future = providerGrant(
                insertProviderValidatedExport(), now.plusSeconds(120), now.plusSeconds(180));
        J7ProviderDerivedOwnerGo.Grant expired = providerGrant(
                insertProviderValidatedExport(), now.minusSeconds(180), now.minusSeconds(120));
        J7ProviderDerivedOwnerGo.Grant available = providerGrant(
                insertProviderValidatedExport(), now.minusSeconds(30), now.plusSeconds(300));

        assertThat(store.registerProviderDerivedOwnerGo(future).status())
                .isEqualTo(J7ProviderDerivedOwnerGo.Status.NOT_YET_VALID);
        assertThat(store.registerProviderDerivedOwnerGo(expired).status())
                .isEqualTo(J7ProviderDerivedOwnerGo.Status.EXPIRED);
        store.registerProviderDerivedOwnerGo(available);
        assertThatThrownBy(() -> store.claimProviderDerived(
                new J7ProviderDerivedOwnerGo.Claim(
                        future, futureIdempotencyKey(future), now)))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.OWNER_GO_NOT_YET_VALID));
        assertThatThrownBy(() -> store.claimProviderDerived(
                new J7ProviderDerivedOwnerGo.Claim(
                        expired, futureIdempotencyKey(expired), now)))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.OWNER_GO_EXPIRED));

        String revocationSha = "c".repeat(64);
        var revoked = store.revokeProviderDerivedOwnerGo(
                available.reference(), revocationSha);
        assertThat(revoked.status()).isEqualTo(J7ProviderDerivedOwnerGo.Status.REVOKED);
        assertThat(revoked.revocationDecisionBlockSha256()).contains(revocationSha);
        assertThat(store.revokeProviderDerivedOwnerGo(
                available.reference(), revocationSha).status())
                .isEqualTo(J7ProviderDerivedOwnerGo.Status.REVOKED);
        assertThatThrownBy(() -> store.revokeProviderDerivedOwnerGo(
                available.reference(), "d".repeat(64)))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.OWNER_GO_REVOCATION_CONFLICT));
        assertThatThrownBy(() -> jdbc.update("""
                update j7_provider_delivery_owner_go_revocation
                set revoked_at = clock_timestamp()
                where revocation_decision_block_sha256 = ?
                """, revocationSha)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                delete from j7_provider_delivery_owner_go_grant where go_uuid = ?
                """, available.goId())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void rollsBackProviderAttemptAndConsumptionWhenClaimCannotReachInFlight() {
        ExportEvidence export = insertProviderValidatedExport();
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant(
                export, now.minusSeconds(30), now.plusSeconds(300));
        store.registerProviderDerivedOwnerGo(grant);
        jdbc.execute("""
                create function fail_test_provider_j7_delivery_claim()
                returns trigger
                language plpgsql
                as $$
                begin
                    if new.current_state = 'IN_FLIGHT' then
                        raise exception 'synthetic provider claim failure';
                    end if;
                    return new;
                end;
                $$
                """);
        jdbc.execute("""
                create trigger fail_test_provider_j7_delivery_claim
                before update on j7_delivery
                for each row execute function fail_test_provider_j7_delivery_claim()
                """);
        try {
            assertThatThrownBy(() -> store.claimProviderDerived(
                    new J7ProviderDerivedOwnerGo.Claim(
                            grant, export.idempotencyKey(), now)))
                    .isInstanceOfSatisfying(
                            J7DeliveryLedgerStore.LedgerException.class,
                            exception -> assertThat(exception.failure()).isEqualTo(
                                    J7DeliveryLedgerStore.LedgerFailure.STORAGE_UNAVAILABLE));
            assertThat(jdbc.queryForObject("""
                    select count(*) from j7_provider_delivery_owner_go_consumption
                    where grant_id = (
                        select id from j7_provider_delivery_owner_go_grant where go_uuid = ?
                    )
                    """, Long.class, grant.goId())).isZero();
            assertThat(store.findProviderDerivedOwnerGo(grant.reference()))
                    .hasValueSatisfying(snapshot -> assertThat(snapshot.status())
                            .isEqualTo(J7ProviderDerivedOwnerGo.Status.AVAILABLE));
            assertThat(store.find(export.exportId(), export.fileSha256())).isEmpty();
        }
        finally {
            jdbc.execute("drop trigger if exists fail_test_provider_j7_delivery_claim on j7_delivery");
            jdbc.execute("drop function if exists fail_test_provider_j7_delivery_claim()");
        }
    }

    @Test
    void rejectsDirectGrantInsertWhenTheCanonicalOwnerDecisionHashIsFalse() {
        ExportEvidence export = insertProviderValidatedExport();
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant(
                export, now.minusSeconds(30), now.plusSeconds(300));

        assertThatThrownBy(() -> insertProviderGrantDirect(
                grant, "f".repeat(64), Instant.parse("2000-01-01T00:00:00Z")))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("""
                select count(*) from j7_provider_delivery_owner_go_grant
                where go_uuid = ?
                """, Long.class, grant.goId())).isZero();
    }

    @Test
    void hostileSearchPathCannotShadowTheCanonicalOwnerDecisionHashFunction() {
        ExportEvidence export = insertProviderValidatedExport();
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant(
                export, now.minusSeconds(30), now.plusSeconds(300));
        String hostileSchema = "wo045_hostile_" + SEQUENCE.incrementAndGet();
        String hostileCanonicalBlock = "HOSTILE_CANONICAL_BLOCK";
        String hostileHash = jdbc.queryForObject("""
                select pg_catalog.encode(pg_catalog.sha256(
                    pg_catalog.convert_to(?, 'UTF8')), 'hex')
                """, String.class, hostileCanonicalBlock);

        jdbc.execute("create schema " + hostileSchema);
        try {
            jdbc.execute("""
                    create function %s.j7_provider_owner_go_canonical_block(
                        owner_go public.j7_provider_delivery_owner_go_grant)
                    returns text
                    language sql
                    immutable
                    as $hostile$
                        select 'HOSTILE_CANONICAL_BLOCK'::text
                    $hostile$
                    """.formatted(hostileSchema));

            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(ignored -> {
                jdbc.execute("set local search_path = "
                        + hostileSchema + ", public, pg_catalog");
                insertProviderGrantDirect(grant, hostileHash, now);
            })).isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("""
                    select count(*) from j7_provider_delivery_owner_go_grant
                    where go_uuid = ?
                    """, Long.class, grant.goId())).isZero();
        }
        finally {
            jdbc.execute("drop schema " + hostileSchema + " cascade");
        }
    }

    @Test
    void databaseOverwritesCallerSuppliedGrantAndRevocationAuditTimes() {
        ExportEvidence export = insertProviderValidatedExport();
        Instant before = databaseClock();
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant(
                export, before.minusSeconds(30), before.plusSeconds(300));
        Instant forgedPast = Instant.parse("2000-01-01T00:00:00Z");

        assertThat(insertProviderGrantDirect(
                grant, grant.ownerDecisionBlockSha256(), forgedPast)).isOne();
        Instant registeredAt = jdbc.queryForObject("""
                select registered_at from j7_provider_delivery_owner_go_grant
                where go_uuid = ?
                """, OffsetDateTime.class, grant.goId()).toInstant();
        assertThat(registeredAt).isBetween(before, databaseClock());
        long grantDatabaseId = jdbc.queryForObject("""
                select id from j7_provider_delivery_owner_go_grant where go_uuid = ?
                """, Long.class, grant.goId());
        assertThat(jdbc.update("""
                insert into j7_provider_delivery_owner_go_revocation (
                    grant_id, revocation_decision_block_sha256, revoked_at
                ) values (?, ?, ?)
                """, grantDatabaseId, sha(SEQUENCE.incrementAndGet()),
                OffsetDateTime.ofInstant(forgedPast, java.time.ZoneOffset.UTC))).isOne();
        Instant revokedAt = jdbc.queryForObject("""
                select revoked_at from j7_provider_delivery_owner_go_revocation
                where grant_id = ?
                """, OffsetDateTime.class, grantDatabaseId).toInstant();
        assertThat(revokedAt).isBetween(before, databaseClock());
    }

    @Test
    void rejectsAntidatedConsumptionAfterTheGrantWindowExpired() {
        ExportEvidence export = insertProviderValidatedExport();
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant expired = providerGrant(
                export, now.minusSeconds(180), now.minusSeconds(120));
        store.registerProviderDerivedOwnerGo(expired);
        UUID deliveryId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(ignored -> {
            DirectProviderAttempt attempt = insertDirectProviderAttempt(
                    export, deliveryId, now);
            long grantDatabaseId = providerGrantDatabaseId(expired);
            jdbc.update("""
                    insert into j7_provider_delivery_owner_go_consumption (
                        grant_id, attempt_id, consumed_at
                    ) values (?, ?, ?)
                    """, grantDatabaseId, attempt.attemptDatabaseId(),
                    OffsetDateTime.parse("2000-01-01T00:00:00Z"));
        })).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("""
                select count(*) from j7_provider_delivery_owner_go_consumption
                where grant_id = ?
                """, Long.class, providerGrantDatabaseId(expired))).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from j7_delivery where delivery_uuid = ?",
                Long.class, deliveryId)).isZero();
    }

    @Test
    void rollsBackDirectAttemptAndConsumptionWithoutAnInFlightTransition() {
        ExportEvidence export = insertProviderValidatedExport();
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant(
                export, now.minusSeconds(30), now.plusSeconds(300));
        store.registerProviderDerivedOwnerGo(grant);
        UUID deliveryId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(ignored -> {
            DirectProviderAttempt attempt = insertDirectProviderAttempt(
                    export, deliveryId, now);
            jdbc.update("""
                    insert into j7_provider_delivery_owner_go_consumption (
                        grant_id, attempt_id, consumed_at
                    ) values (?, ?, default)
                    """, providerGrantDatabaseId(grant), attempt.attemptDatabaseId());
        })).isInstanceOf(TransactionSystemException.class)
                .hasMessageContaining("JDBC commit failed")
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThat(jdbc.queryForObject("""
                select count(*) from j7_provider_delivery_owner_go_consumption
                where grant_id = ?
                """, Long.class, providerGrantDatabaseId(grant))).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from j7_delivery where delivery_uuid = ?",
                Long.class, deliveryId)).isZero();
    }

    @Test
    void serializesConcurrentClaimsForTheSameProviderGrant() throws Exception {
        ExportEvidence export = insertProviderValidatedExport();
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant(
                export, now.minusSeconds(30), now.plusSeconds(300));
        store.registerProviderDerivedOwnerGo(grant);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> concurrentProviderClaim(
                    grant, export.idempotencyKey(), now, ready, release));
            Future<Object> second = executor.submit(() -> concurrentProviderClaim(
                    grant, export.idempotencyKey(), now, ready, release));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            Object firstResult = first.get(20, TimeUnit.SECONDS);
            Object secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(firstResult, secondResult))
                    .filteredOn(J7DeliveryLedgerStore.ClaimReceipt.class::isInstance)
                    .hasSize(1);
            assertThat(List.of(firstResult, secondResult))
                    .filteredOn(result -> result ==
                            J7DeliveryLedgerStore.LedgerFailure.OWNER_GO_CONSUMED)
                    .hasSize(1);
            J7DeliveryLedgerStore.ClaimReceipt receipt = List.of(firstResult, secondResult)
                    .stream()
                    .filter(J7DeliveryLedgerStore.ClaimReceipt.class::isInstance)
                    .map(J7DeliveryLedgerStore.ClaimReceipt.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(jdbc.queryForObject("""
                    select count(*) from j7_provider_delivery_owner_go_consumption
                    where grant_id = (
                        select id from j7_provider_delivery_owner_go_grant where go_uuid = ?
                    )
                    """, Long.class, grant.goId())).isOne();
            assertThat(jdbc.queryForObject("""
                    select count(*) from j7_delivery_attempt attempt
                    join j7_delivery delivery on delivery.id = attempt.delivery_id
                    where delivery.delivery_uuid = ?
                    """, Long.class, receipt.deliveryId())).isOne();
            store.complete(
                    receipt.deliveryId(), 1,
                    J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                    OptionalInt.empty(), "CONCURRENT_CLAIM_QUALIFIED",
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    receipt.startedAt().plusSeconds(1));
        }
        finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void makesConcurrentRevocationAndClaimMutuallyExclusive() throws Exception {
        ExportEvidence export = insertProviderValidatedExport();
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant(
                export, now.minusSeconds(30), now.plusSeconds(300));
        store.registerProviderDerivedOwnerGo(grant);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        String revocationSha = sha(SEQUENCE.incrementAndGet());
        try {
            Future<Object> claim = executor.submit(() -> concurrentProviderClaim(
                    grant, export.idempotencyKey(), now, ready, release));
            Future<Object> revoke = executor.submit(() -> {
                ready.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent revocation release timed out");
                }
                try {
                    return store.revokeProviderDerivedOwnerGo(
                            grant.reference(), revocationSha);
                }
                catch (J7DeliveryLedgerStore.LedgerException exception) {
                    return exception.failure();
                }
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            Object claimResult = claim.get(20, TimeUnit.SECONDS);
            Object revokeResult = revoke.get(20, TimeUnit.SECONDS);
            var snapshot = store.findProviderDerivedOwnerGo(grant.reference()).orElseThrow();
            if (snapshot.status() == J7ProviderDerivedOwnerGo.Status.CONSUMED) {
                assertThat(claimResult).isInstanceOf(J7DeliveryLedgerStore.ClaimReceipt.class);
                assertThat(revokeResult)
                        .isEqualTo(J7DeliveryLedgerStore.LedgerFailure.OWNER_GO_CONSUMED);
                J7DeliveryLedgerStore.ClaimReceipt receipt =
                        (J7DeliveryLedgerStore.ClaimReceipt) claimResult;
                store.complete(
                        receipt.deliveryId(), 1,
                        J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                        OptionalInt.empty(), "CLAIM_WON_REVOCATION_RACE",
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        receipt.startedAt().plusSeconds(1));
            }
            else {
                assertThat(snapshot.status()).isEqualTo(J7ProviderDerivedOwnerGo.Status.REVOKED);
                assertThat(revokeResult).isInstanceOf(J7ProviderDerivedOwnerGo.Snapshot.class);
                assertThat(claimResult)
                        .isEqualTo(J7DeliveryLedgerStore.LedgerFailure.OWNER_GO_REVOKED);
            }
            long terminalEvidenceRows = jdbc.queryForObject("""
                    select
                        (select count(*) from j7_provider_delivery_owner_go_consumption
                         where grant_id = owner_go.id)
                        +
                        (select count(*) from j7_provider_delivery_owner_go_revocation
                         where grant_id = owner_go.id)
                    from j7_provider_delivery_owner_go_grant owner_go
                    where owner_go.go_uuid = ?
                    """, Long.class, grant.goId());
            assertThat(terminalEvidenceRows).isOne();
        }
        finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rejectsOrphanProviderAttemptAndAnyGrantIdentityDrift() {
        ExportEvidence export = insertProviderValidatedExport();
        Instant now = databaseClock();
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant(
                export, now.minusSeconds(30), now.plusSeconds(300));
        store.registerProviderDerivedOwnerGo(grant);
        J7ProviderDerivedOwnerGo.Reference wrongReference =
                new J7ProviderDerivedOwnerGo.Reference(grant.goId(), "e".repeat(64));
        assertThatThrownBy(() -> store.findProviderDerivedOwnerGo(wrongReference))
                .isInstanceOfSatisfying(
                        J7DeliveryLedgerStore.LedgerException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(
                                J7DeliveryLedgerStore.LedgerFailure.OWNER_GO_IDENTITY_MISMATCH));

        UUID deliveryId = UUID.randomUUID();
        assertThat(jdbc.update("""
                insert into j7_delivery (
                    delivery_uuid, export_manifest_id, export_uuid,
                    file_sha256, data_sha256, file_size_bytes,
                    idempotency_key, protocol_version,
                    current_state, created_at, state_changed_at
                ) select ?, id, export_uuid, content_sha256, data_sha256,
                         content_size_bytes, ?, '1.0', 'NOT_ATTEMPTED', ?, ?
                  from export_manifest where export_uuid = ?
                """, deliveryId, export.idempotencyKey(),
                OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                export.exportId())).isOne();
        long databaseDeliveryId = jdbc.queryForObject(
                "select id from j7_delivery where delivery_uuid = ?", Long.class, deliveryId);
        assertThatThrownBy(() -> jdbc.update("""
                insert into j7_delivery_attempt (
                    attempt_uuid, delivery_id, attempt_number, started_at, payload_class
                ) values (?, ?, 1, ?, 'PROVIDER_DERIVED')
                """, UUID.randomUUID(), databaseDeliveryId,
                OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC)))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject(
                "select count(*) from j7_delivery_attempt where delivery_id = ?",
                Long.class, databaseDeliveryId)).isZero();
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
                    attempt_uuid, delivery_id, attempt_number, started_at, payload_class
                ) values (?, ?, 1, ?, 'SYNTHETIC_ONLY')
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

    private static Object concurrentProviderClaim(
            J7ProviderDerivedOwnerGo.Grant grant,
            String idempotencyKey,
            Instant requestedAt,
            CountDownLatch ready,
            CountDownLatch release) throws InterruptedException {
        ready.countDown();
        if (!release.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent provider claim release timed out");
        }
        try {
            return store.claimProviderDerived(new J7ProviderDerivedOwnerGo.Claim(
                    grant, idempotencyKey, requestedAt));
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
        return validateExport(evidence);
    }

    private static ExportEvidence insertProviderValidatedExport() {
        long sequence = SEQUENCE.incrementAndGet();
        UUID canonicalEventId = UUID.nameUUIDFromBytes(("provider-event-" + sequence).getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        UUID exportId = UUID.nameUUIDFromBytes(("provider-export-" + sequence).getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        long providerEventId = sequence;
        String dataSha = sha(sequence * 10 + 1);
        String sourceSetSha = sha(sequence * 10 + 2);
        String candidateSha = sha(sequence * 10 + 3);
        String fileSha = sha(sequence * 10 + 4);
        jdbc.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?)
                """, canonicalEventId, providerEventId);
        Long snapshotId = jdbc.queryForObject("""
                insert into provider_snapshot (
                    provider, logical_endpoint, request_key,
                    requested_at, received_at, http_status, content_type,
                    latency_ms, payload_sha256, parser_version, schema_status
                ) values (
                    'SOFASCORE', 'EVENT_DETAILS', ?,
                    '2026-09-01T08:00:00Z', '2026-09-01T08:00:01Z',
                    200, 'application/json', 1000, ?, 'j9-test', 'PARSED'
                ) returning id
                """, Long.class, "provider-owner-go:" + sequence, sha(sequence * 10 + 5));
        if (snapshotId == null) {
            throw new IllegalStateException("provider snapshot insert failed");
        }
        String sources = """
                [
                  {"component":"EVENT_STATE","availability":"PRESENT","sourceKind":"PROVIDER_SNAPSHOT","snapshotId":%d},
                  {"component":"EVENT_DETAILS","availability":"PRESENT","sourceKind":"PROVIDER_SNAPSHOT","snapshotId":%d},
                  {"component":"EVENT_STATISTICS","availability":"UNAVAILABLE","sourceKind":"PROVIDER_SNAPSHOT","snapshotId":%d},
                  {"component":"EVENT_INCIDENTS","availability":"MISSING","sourceKind":null,"snapshotId":null},
                  {"component":"EVENT_LINEUPS","availability":"MISSING","sourceKind":null,"snapshotId":null}
                ]
                """.formatted(snapshotId, snapshotId, snapshotId);
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
                    array[?]::bigint[], '[]'::jsonb
                )
                """, exportId, canonicalEventId, dataSha, sourceSetSha, candidateSha,
                sources, candidatePath, candidateSha, snapshotId);
        return validateExport(new ExportEvidence(
                canonicalEventId,
                providerEventId,
                exportId,
                fileSha,
                dataSha,
                "j7:" + exportId + ":sha256:" + fileSha,
                candidateSha));
    }

    private static ExportEvidence validateExport(ExportEvidence evidence) {
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

    private static J7ProviderDerivedOwnerGo.Grant providerGrant(
            ExportEvidence export,
            Instant validFrom,
            Instant validUntil) {
        long sequence = SEQUENCE.incrementAndGet();
        J7ProviderDerivedOwnerGo.Grant draft = new J7ProviderDerivedOwnerGo.Grant(
                UUID.nameUUIDFromBytes(("provider-go-" + sequence).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)),
                sha(sequence * 20 + 1),
                "WO-SS-20260904-046-provider-derived-real-delivery",
                "docs/validation/J9-WO046-PROVIDER-DERIVED-MANIFEST-" + sequence + ".md",
                sha(sequence * 20 + 2),
                String.format("%040x", sequence * 20 + 3),
                String.format("%040x", sequence * 20 + 4),
                "docs/validation/J9-OFFICIAL-PERMISSION-EVIDENCE-" + sequence + ".md",
                sha(sequence * 20 + 5),
                "EVIDENCED_COMPATIBLE",
                "PASS",
                "PASS",
                "CODEX_LOCAL_UI",
                export.canonicalEventId(),
                export.providerEventId(),
                export.exportId(),
                export.fileSha256(),
                export.dataSha256(),
                2048,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_ID,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_VERSION,
                URI.create("https://127.0.0.1:8444"),
                sha(sequence * 20 + 6),
                1,
                1,
                validFrom,
                validUntil,
                "GRANT",
                "ONE_TIME",
                "PROVIDER_DERIVED",
                "HUMAN_VALIDATED",
                true,
                false,
                false,
                false,
                false,
                false);
        return draft.withOwnerDecisionBlockSha256(
                draft.computedOwnerDecisionBlockSha256());
    }

    private static int insertProviderGrantDirect(
            J7ProviderDerivedOwnerGo.Grant grant,
            String ownerDecisionBlockSha256,
            Instant suppliedRegisteredAt) {
        Long exportManifestId = jdbc.queryForObject("""
                select id from export_manifest where export_uuid = ?
                """, Long.class, grant.exportId());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("goId", grant.goId())
                .addValue("ownerDecisionBlockSha256", ownerDecisionBlockSha256)
                .addValue("workOrder", grant.workOrder())
                .addValue("campaignManifestReference", grant.campaignManifestReference())
                .addValue("campaignManifestSha256", grant.campaignManifestSha256())
                .addValue("localLabCommit", grant.localLabCommit())
                .addValue("receiverCommit", grant.receiverCommit())
                .addValue("officialPermissionEvidenceReference",
                        grant.officialPermissionEvidenceReference())
                .addValue("officialPermissionEvidenceSha256",
                        grant.officialPermissionEvidenceSha256())
                .addValue("officialPermissionStatus", grant.officialPermissionStatus())
                .addValue("receiverQualification", grant.receiverQualification())
                .addValue("senderQualification", grant.senderQualification())
                .addValue("executionActor", grant.executionActor())
                .addValue("exportManifestId", exportManifestId)
                .addValue("canonicalEventId", grant.canonicalEventId())
                .addValue("providerEventId", grant.providerEventId())
                .addValue("exportId", grant.exportId())
                .addValue("fileSha256", grant.fileSha256())
                .addValue("dataSha256", grant.dataSha256())
                .addValue("fileSizeBytes", grant.fileSizeBytes())
                .addValue("schemaId", grant.schemaId())
                .addValue("schemaVersion", grant.schemaVersion())
                .addValue("receiverOrigin", grant.receiverOrigin().toASCIIString())
                .addValue("clientCertificateSha256", grant.clientCertificateSha256())
                .addValue("expectedAttemptNumber", grant.expectedAttemptNumber())
                .addValue("maximumDirectImportCalls", grant.maximumDirectImportCalls())
                .addValue("validFrom", OffsetDateTime.ofInstant(
                        grant.validFrom(), java.time.ZoneOffset.UTC))
                .addValue("validUntil", OffsetDateTime.ofInstant(
                        grant.validUntil(), java.time.ZoneOffset.UTC))
                .addValue("ownerDecision", grant.ownerDecision())
                .addValue("goUse", grant.goUse())
                .addValue("payloadClass", grant.payloadClass())
                .addValue("validationStatus", grant.validationStatus())
                .addValue("providerDerivedRealPostAuthorized",
                        grant.providerDerivedRealPostAuthorized())
                .addValue("providerNetworkAuthorized", grant.providerNetworkAuthorized())
                .addValue("remoteReceiverNetworkAuthorized",
                        grant.remoteReceiverNetworkAuthorized())
                .addValue("vpsDeploymentAuthorized", grant.vpsDeploymentAuthorized())
                .addValue("productionAuthorized", grant.productionAuthorized())
                .addValue("automaticRetryAuthorized", grant.automaticRetryAuthorized())
                .addValue("registeredAt", OffsetDateTime.ofInstant(
                        suppliedRegisteredAt, java.time.ZoneOffset.UTC));
        return namedJdbc.update("""
                insert into j7_provider_delivery_owner_go_grant (
                    go_uuid, owner_decision_block_sha256, work_order,
                    campaign_manifest_reference, campaign_manifest_sha256,
                    local_lab_commit, receiver_commit,
                    official_permission_evidence_reference,
                    official_permission_evidence_sha256,
                    official_permission_status, receiver_qualification,
                    sender_qualification, execution_actor, export_manifest_id,
                    canonical_event_id, provider_event_id, export_uuid,
                    file_sha256, data_sha256, file_size_bytes,
                    schema_id, schema_version, receiver_origin,
                    client_certificate_sha256, expected_attempt_number,
                    maximum_direct_import_calls, valid_from, valid_until,
                    owner_decision, go_use, payload_class, validation_status,
                    provider_derived_real_post_authorized,
                    provider_network_authorized, remote_receiver_network_authorized,
                    vps_deployment_authorized, production_authorized,
                    automatic_retry_authorized, registered_at
                ) values (
                    :goId, :ownerDecisionBlockSha256, :workOrder,
                    :campaignManifestReference, :campaignManifestSha256,
                    :localLabCommit, :receiverCommit,
                    :officialPermissionEvidenceReference,
                    :officialPermissionEvidenceSha256,
                    :officialPermissionStatus, :receiverQualification,
                    :senderQualification, :executionActor, :exportManifestId,
                    :canonicalEventId, :providerEventId, :exportId,
                    :fileSha256, :dataSha256, :fileSizeBytes,
                    :schemaId, :schemaVersion, :receiverOrigin,
                    :clientCertificateSha256, :expectedAttemptNumber,
                    :maximumDirectImportCalls, :validFrom, :validUntil,
                    :ownerDecision, :goUse, :payloadClass, :validationStatus,
                    :providerDerivedRealPostAuthorized,
                    :providerNetworkAuthorized, :remoteReceiverNetworkAuthorized,
                    :vpsDeploymentAuthorized, :productionAuthorized,
                    :automaticRetryAuthorized, :registeredAt
                )
                """, parameters);
    }

    private static DirectProviderAttempt insertDirectProviderAttempt(
            ExportEvidence export,
            UUID deliveryId,
            Instant startedAt) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(
                startedAt, java.time.ZoneOffset.UTC);
        assertThat(jdbc.update("""
                insert into j7_delivery (
                    delivery_uuid, export_manifest_id, export_uuid,
                    file_sha256, data_sha256, file_size_bytes,
                    idempotency_key, protocol_version,
                    current_state, created_at, state_changed_at
                ) select ?, id, export_uuid, content_sha256, data_sha256,
                         content_size_bytes, ?, '1.0', 'NOT_ATTEMPTED', ?, ?
                  from export_manifest where export_uuid = ?
                """, deliveryId, export.idempotencyKey(), timestamp,
                timestamp, export.exportId())).isOne();
        long deliveryDatabaseId = jdbc.queryForObject(
                "select id from j7_delivery where delivery_uuid = ?",
                Long.class, deliveryId);
        UUID attemptId = UUID.randomUUID();
        assertThat(jdbc.update("""
                insert into j7_delivery_attempt (
                    attempt_uuid, delivery_id, attempt_number, started_at, payload_class
                ) values (?, ?, 1, ?, 'PROVIDER_DERIVED')
                """, attemptId, deliveryDatabaseId, timestamp)).isOne();
        long attemptDatabaseId = jdbc.queryForObject("""
                select id from j7_delivery_attempt where attempt_uuid = ?
                """, Long.class, attemptId);
        return new DirectProviderAttempt(deliveryDatabaseId, attemptDatabaseId);
    }

    private static long providerGrantDatabaseId(J7ProviderDerivedOwnerGo.Grant grant) {
        return jdbc.queryForObject("""
                select id from j7_provider_delivery_owner_go_grant where go_uuid = ?
                """, Long.class, grant.goId());
    }

    private static String futureIdempotencyKey(J7ProviderDerivedOwnerGo.Grant grant) {
        return "j7:" + grant.exportId() + ":sha256:" + grant.fileSha256();
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
                sequence,
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
                        ".*(payload(?!_class)|body|diagnostic|cookie|token|secret|private_key|certificate(?!_sha256)).*"));
    }

    private static String sha(long value) {
        return String.format("%064x", value);
    }

    private record ExportEvidence(
            UUID canonicalEventId,
            long providerEventId,
            UUID exportId,
            String fileSha256,
            String dataSha256,
            String idempotencyKey,
            String candidateSha256) {
    }

    private record DirectProviderAttempt(long deliveryDatabaseId, long attemptDatabaseId) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }
}
