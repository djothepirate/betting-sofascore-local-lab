package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.application.network.J3QualificationCheckpointReparser;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.J3QualificationCheckpointStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class FlywayMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("sofascore_local_lab")
            .withUsername("sofascore_lab")
            .withPassword("integration-test-only");

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("sofascore.export-directory", () -> "target/integration-test-exports");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RawManualCallSnapshotStore snapshotStore;

    @Autowired
    J3QualificationCheckpointStore checkpointStore;

    @Autowired
    J3QualificationCheckpointReparser checkpointReparser;

    @Test
    void createsTheJ3RawSnapshotSchemaAndKeepsNetworkDisabled() {
        String snapshotTable = jdbcTemplate.queryForObject(
                "select to_regclass('public.provider_snapshot')", String.class);
        String exportTable = jdbcTemplate.queryForObject(
                "select to_regclass('public.export_manifest')", String.class);
        Boolean networkEnabled = jdbcTemplate.queryForObject(
                "select network_enabled from connector_control where singleton_id = 1",
                Boolean.class);
        String flywayVersion = jdbcTemplate.queryForObject(
                """
                select version
                from flyway_schema_history
                where success = true
                order by installed_rank desc
                limit 1
                """,
                String.class);
        String rawColumn = jdbcTemplate.queryForObject(
                """
                select data_type
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'provider_snapshot'
                  and column_name = 'payload_raw'
                """,
                String.class);

        assertThat(snapshotTable).isEqualTo("provider_snapshot");
        assertThat(exportTable).isEqualTo("export_manifest");
        assertThat(networkEnabled).isFalse();
        assertThat(flywayVersion).isEqualTo("2");
        assertThat(rawColumn).isEqualTo("bytea");
    }

    @Test
    void persistsExactRawBytesMetadataAndDirectLocalProvenance() {
        byte[] rawPayload = "{\"events\":[],\"hasNextPage\":false}"
                .getBytes(StandardCharsets.UTF_8);
        RawManualCallSnapshot snapshot = snapshot(
                "SCHEDULED_EVENTS|date=2026-08-12",
                rawPayload);

        var result = snapshotStore.save(snapshot);

        assertThat(result.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.INSERTED);
        assertThat(result.payloadSizeBytes()).isEqualTo(rawPayload.length);
        assertThat(jdbcTemplate.queryForObject(
                "select payload_raw from provider_snapshot where id = ?",
                byte[].class,
                result.snapshotId())).isEqualTo(rawPayload);
        assertThat(jdbcTemplate.queryForObject(
                "select acquisition_mode from provider_snapshot where id = ?",
                String.class,
                result.snapshotId())).isEqualTo("DIRECT_LOCAL_ENDPOINT");
        assertThat(jdbcTemplate.queryForObject(
                "select payload_size_bytes from provider_snapshot where id = ?",
                Long.class,
                result.snapshotId())).isEqualTo((long) rawPayload.length);
        assertThat(jdbcTemplate.queryForObject(
                "select payload_sha256 from provider_snapshot where id = ?",
                String.class,
                result.snapshotId())).isEqualTo(result.payloadSha256());
        assertThat(jdbcTemplate.queryForObject(
                "select payload_jsonb is null from provider_snapshot where id = ?",
                Boolean.class,
                result.snapshotId())).isTrue();
    }

    @Test
    void deduplicatesTheSameRequestAndExactPayloadButKeepsChangedPayloads() {
        String requestKey = "SCHEDULED_EVENTS|date=2026-08-13";
        RawManualCallSnapshot first = snapshot(
                requestKey,
                "{\"events\":[]}".getBytes(StandardCharsets.UTF_8));
        RawManualCallSnapshot changed = snapshot(
                requestKey,
                "{\"events\":[{\"id\":1}]}".getBytes(StandardCharsets.UTF_8));

        var inserted = snapshotStore.save(first);
        var duplicate = snapshotStore.save(first);
        var secondVersion = snapshotStore.save(changed);

        assertThat(inserted.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.INSERTED);
        assertThat(duplicate.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(duplicate.snapshotId()).isEqualTo(inserted.snapshotId());
        assertThat(secondVersion.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.INSERTED);
        assertThat(secondVersion.snapshotId()).isNotEqualTo(inserted.snapshotId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from provider_snapshot where request_key = ?",
                Long.class,
                requestKey)).isEqualTo(2L);
    }

    @Test
    void persistsRawOnlyBeforeApplyingAParserClassificationIdempotently() {
        byte[] rawPayload = "{\"events\":[],\"hasNextPage\":false}"
                .getBytes(StandardCharsets.UTF_8);
        RawManualCallSnapshot rawOnly = snapshot(
                "SCHEDULED_EVENTS|date=2026-08-15",
                rawPayload,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null);

        var result = snapshotStore.save(rawOnly);
        assertThat(schemaStatus(result.snapshotId())).isEqualTo("RAW_ONLY");

        snapshotStore.classify(
                result.snapshotId(),
                RawSnapshotSchemaStatus.PARSED,
                null);
        snapshotStore.classify(
                result.snapshotId(),
                RawSnapshotSchemaStatus.PARSED,
                null);

        assertThat(schemaStatus(result.snapshotId())).isEqualTo("PARSED");
        assertThat(jdbcTemplate.queryForObject(
                "select payload_raw from provider_snapshot where id = ?",
                byte[].class,
                result.snapshotId())).isEqualTo(rawPayload);
        assertThatThrownBy(() -> snapshotStore.classify(
                result.snapshotId(),
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE"))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage(
                        "raw snapshot classification requires one RAW_ONLY or identical row");
    }

    @Test
    void reloadsTheExactRawPageOneAsALocalResumeCheckpoint() {
        byte[] rawPayload = """
                {
                  "scheduled": [],
                  "hasNextPage": true
                }
                """.getBytes(StandardCharsets.UTF_8);
        var persisted = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-13|page=1",
                rawPayload));

        var pages = checkpointStore.findStoredPages(LocalDate.parse("2026-08-13"));

        assertThat(pages).singleElement().satisfies(page -> {
            assertThat(page.snapshotId()).isEqualTo(persisted.snapshotId());
            assertThat(page.page()).isEqualTo(1);
            assertThat(page.requestKey())
                    .isEqualTo("SCHEDULED_EVENTS|date=2026-08-13|page=1");
            assertThat(page.payload().bytes()).isEqualTo(rawPayload);
            assertThat(page.payload().sha256()).isEqualTo(persisted.payloadSha256());
            assertThat(page.historicalSchemaStatus())
                    .isEqualTo(RawSnapshotSchemaStatus.PARSED);
        });
    }

    @Test
    void reparsesTwoHistoricalIncompatibilitiesWithoutMutatingTheirStatuses() throws Exception {
        byte[] pageOnePayload = Files.readAllBytes(Path.of(
                "fixtures/scheduled-events/qualified-provider-shape.json"));
        byte[] pageTwoPayload = Files.readAllBytes(Path.of(
                "fixtures/scheduled-events/qualified-page-two-shape.json"));
        var pageOne = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-13|page=1",
                pageOnePayload,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE"));
        var pageTwo = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-13|page=2",
                pageTwoPayload,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE"));

        var results = checkpointReparser.reparseStoredPages(LocalDate.parse("2026-08-13"));

        assertThat(results).hasSize(2).allSatisfy(result -> {
            assertThat(result.historicalSchemaStatus())
                    .isEqualTo(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE);
            assertThat(result.currentParseStatus()).isEqualTo(ScheduledEventsParseStatus.PARSED);
            assertThat(result.hasNextPage()).isTrue();
        });
        assertThat(schemaStatus(pageOne.snapshotId())).isEqualTo("SCHEMA_INCOMPATIBLE");
        assertThat(schemaStatus(pageTwo.snapshotId())).isEqualTo("SCHEMA_INCOMPATIBLE");
    }

    @Test
    void databaseRejectsAnInconsistentRawPayloadSize() {
        byte[] rawPayload = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into provider_snapshot (
                    provider,
                    acquisition_mode,
                    logical_endpoint,
                    request_key,
                    requested_at,
                    received_at,
                    http_status,
                    content_type,
                    latency_ms,
                    payload_raw,
                    payload_size_bytes,
                    payload_sha256,
                    parser_version,
                    schema_status
                ) values (?, ?, ?, ?, current_timestamp, current_timestamp, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "SOFASCORE",
                "DIRECT_LOCAL_ENDPOINT",
                "SCHEDULED_EVENTS",
                "SCHEDULED_EVENTS|date=2026-08-14",
                200,
                "application/json",
                10L,
                rawPayload,
                99L,
                "a".repeat(64),
                "scheduled-events-v1",
                "RAW_ONLY"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static RawManualCallSnapshot snapshot(String requestKey, byte[] rawPayload) {
        return snapshot(
                requestKey,
                rawPayload,
                RawSnapshotSchemaStatus.PARSED,
                null);
    }

    private static RawManualCallSnapshot snapshot(
            String requestKey,
            byte[] rawPayload,
            RawSnapshotSchemaStatus schemaStatus,
            String errorCode) {
        Instant requestedAt = Instant.parse("2026-08-12T12:00:00Z");
        return new RawManualCallSnapshot(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                requestKey,
                requestedAt,
                requestedAt.plusMillis(275),
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(275),
                RawPayloadEvidence.capture(rawPayload),
                "scheduled-events-v1",
                schemaStatus,
                errorCode);
    }

    private String schemaStatus(long snapshotId) {
        return jdbcTemplate.queryForObject(
                "select schema_status from provider_snapshot where id = ?",
                String.class,
                snapshotId);
    }
}
