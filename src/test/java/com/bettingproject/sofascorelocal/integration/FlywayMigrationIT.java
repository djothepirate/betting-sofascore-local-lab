package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.application.network.J3QualificationCheckpointReparser;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspectionService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J3ScheduledEventsPageCache;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.J3QualificationCheckpointStore;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
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
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

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

    @Autowired
    J3ScheduledEventsPageCache scheduledEventsPageCache;

    @Autowired
    RawSnapshotInspectionStore snapshotInspectionStore;

    @Autowired
    RawSnapshotJsonInspectionService snapshotInspectionService;

    @Autowired
    CanonicalEventStore canonicalEventStore;

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
        assertThat(flywayVersion).isEqualTo("4");
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
    void readsAnExplicitRawSnapshotForInspectionWithoutMutatingPersistence() {
        byte[] rawPayload = "{\"events\":[],\"hasNextPage\":false}"
                .getBytes(StandardCharsets.UTF_8);
        var persisted = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-14|page=1",
                rawPayload));
        Long countBefore = jdbcTemplate.queryForObject(
                "select count(*) from provider_snapshot",
                Long.class);

        var summaries = snapshotInspectionStore.findRecent(50);
        var selected = snapshotInspectionStore.findById(persisted.snapshotId());

        assertThat(summaries)
                .extracting(summary -> summary.snapshotId())
                .contains(persisted.snapshotId());
        assertThat(selected).hasValueSatisfying(source -> {
            assertThat(source.summary().snapshotId()).isEqualTo(persisted.snapshotId());
            assertThat(source.summary().requestKey())
                    .isEqualTo("SCHEDULED_EVENTS|date=2026-08-14|page=1");
            assertThat(source.summary().payloadSha256())
                    .isEqualTo(persisted.payloadSha256());
            assertThat(source.payloadRaw()).isEqualTo(rawPayload);
        });
        assertThat(snapshotInspectionStore.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from provider_snapshot",
                Long.class)).isEqualTo(countBefore);
    }

    @Test
    void keepsAFreshParsedCacheCheckpointUnchangedAfterJsonInspection() {
        byte[] rawPayload = """
                {
                  "scheduled": [],
                  "hasNextPage": false
                }
                """.getBytes(StandardCharsets.UTF_8);
        String requestKey = "SCHEDULED_EVENTS|date=2026-08-14|page=1";
        var persisted = snapshotStore.save(snapshot(requestKey, rawPayload));
        var request = new ScheduledEventsProviderPageRequest(
                URI.create(ScheduledEventsProviderPageRequest.EXPECTED_ORIGIN),
                LocalDate.parse("2026-08-14"),
                1);
        var response = new ScheduledEventsTransportResponse(
                requestKey,
                Instant.parse("2026-08-14T09:31:22Z"),
                Instant.parse("2026-08-14T09:31:23Z"),
                200,
                "application/json; charset=utf-8",
                Duration.ofSeconds(1),
                RawPayloadEvidence.capture(rawPayload));
        scheduledEventsPageCache.recordParsed(
                request,
                response,
                persisted,
                ScheduledEventsV1Parser.PARSER_VERSION);
        Long snapshotsBefore = snapshotRowCount();
        Long checkpointsBefore = cacheCheckpointRowCount();
        Instant cachedAtBefore = cacheTimestamp(requestKey);

        var inspection = snapshotInspectionService.inspect(persisted.snapshotId());
        var cachedAfterInspection = scheduledEventsPageCache.findFreshParsed(
                request,
                Instant.parse("2026-08-14T09:36:23Z"),
                Duration.ofMinutes(10),
                ScheduledEventsV1Parser.PARSER_VERSION);

        assertThat(inspection.summary().snapshotId()).isEqualTo(persisted.snapshotId());
        assertThat(inspection.summary().schemaStatus())
                .isEqualTo(RawSnapshotSchemaStatus.PARSED);
        assertThat(inspection.formattedJson())
                .contains("\"hasNextPage\" : false");
        assertThat(cachedAfterInspection).hasValueSatisfying(cached -> {
            assertThat(cached.snapshotId()).isEqualTo(persisted.snapshotId());
            assertThat(cached.payload().bytes()).isEqualTo(rawPayload);
            assertThat(cached.cachedAt()).isEqualTo(cachedAtBefore);
        });
        assertThat(snapshotRowCount()).isEqualTo(snapshotsBefore);
        assertThat(cacheCheckpointRowCount()).isEqualTo(checkpointsBefore);
        assertThat(cacheTimestamp(requestKey)).isEqualTo(cachedAtBefore);
        assertThat(schemaStatus(persisted.snapshotId())).isEqualTo("PARSED");
    }

    @Test
    void keepsHistoricalIncompatibilityVisibleButIneligibleForCacheAfterInspection() {
        byte[] rawPayload = """
                {
                  "scheduled": [],
                  "hasNextPage": true
                }
                """.getBytes(StandardCharsets.UTF_8);
        String requestKey = "SCHEDULED_EVENTS|date=2026-08-13|page=1";
        var persisted = snapshotStore.save(snapshot(
                requestKey,
                rawPayload,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE"));
        var request = new ScheduledEventsProviderPageRequest(
                URI.create(ScheduledEventsProviderPageRequest.EXPECTED_ORIGIN),
                LocalDate.parse("2026-08-13"),
                1);
        Long snapshotsBefore = snapshotRowCount();
        Long checkpointsBefore = cacheCheckpointRowCount();

        var inspection = snapshotInspectionService.inspect(persisted.snapshotId());
        var cacheCandidate = scheduledEventsPageCache.findFreshParsed(
                request,
                Instant.parse("2026-08-13T13:30:00Z"),
                Duration.ofMinutes(10),
                ScheduledEventsV1Parser.PARSER_VERSION);

        assertThat(inspection.summary().schemaStatus())
                .isEqualTo(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE);
        assertThat(inspection.formattedJson())
                .contains("\"hasNextPage\" : true");
        assertThat(cacheCandidate).isEmpty();
        assertThat(snapshotRowCount()).isEqualTo(snapshotsBefore);
        assertThat(cacheCheckpointRowCount()).isEqualTo(checkpointsBefore);
        assertThat(schemaStatus(persisted.snapshotId()))
                .isEqualTo("SCHEMA_INCOMPATIBLE");
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
    void selectsOnlyAnExactFreshParsedPageAsDynamicCache() {
        byte[] rawPayload = """
                {
                  "scheduled": [],
                  "hasNextPage": false
                }
                """.getBytes(StandardCharsets.UTF_8);
        var persisted = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-13|page=1",
                rawPayload));
        var request = new ScheduledEventsProviderPageRequest(
                URI.create(ScheduledEventsProviderPageRequest.EXPECTED_ORIGIN),
                LocalDate.parse("2026-08-13"),
                1);
        var initialResponse = new ScheduledEventsTransportResponse(
                request.requestKey(),
                Instant.parse("2026-08-12T12:00:00Z"),
                Instant.parse("2026-08-12T12:00:00.275Z"),
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(275),
                RawPayloadEvidence.capture(rawPayload));
        scheduledEventsPageCache.recordParsed(
                request,
                initialResponse,
                persisted,
                ScheduledEventsV1Parser.PARSER_VERSION);

        var fresh = scheduledEventsPageCache.findFreshParsed(
                request,
                Instant.parse("2026-08-12T12:05:00Z"),
                Duration.ofMinutes(10),
                ScheduledEventsV1Parser.PARSER_VERSION);
        var expired = scheduledEventsPageCache.findFreshParsed(
                request,
                Instant.parse("2026-08-12T12:10:00.275Z"),
                Duration.ofMinutes(10),
                ScheduledEventsV1Parser.PARSER_VERSION);
        var otherParser = scheduledEventsPageCache.findFreshParsed(
                request,
                Instant.parse("2026-08-12T12:05:00Z"),
                Duration.ofMinutes(10),
                "scheduled-events-v2");

        assertThat(fresh).hasValueSatisfying(cached -> {
            assertThat(cached.snapshotId()).isEqualTo(persisted.snapshotId());
            assertThat(cached.requestKey()).isEqualTo(request.requestKey());
            assertThat(cached.payload().bytes()).isEqualTo(rawPayload);
            assertThat(cached.payload().sha256()).isEqualTo(persisted.payloadSha256());
        });
        assertThat(expired).isEmpty();
        assertThat(otherParser).isEmpty();

        var deduplicated = snapshotStore.save(snapshot(
                request.requestKey(),
                rawPayload));
        var refreshedResponse = new ScheduledEventsTransportResponse(
                request.requestKey(),
                Instant.parse("2026-08-12T12:20:00Z"),
                Instant.parse("2026-08-12T12:20:00.100Z"),
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(100),
                RawPayloadEvidence.capture(rawPayload));
        scheduledEventsPageCache.recordParsed(
                request,
                refreshedResponse,
                deduplicated,
                ScheduledEventsV1Parser.PARSER_VERSION);
        var refreshed = scheduledEventsPageCache.findFreshParsed(
                request,
                Instant.parse("2026-08-12T12:25:00Z"),
                Duration.ofMinutes(10),
                ScheduledEventsV1Parser.PARSER_VERSION);

        assertThat(deduplicated.outcome())
                .isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(refreshed).hasValueSatisfying(cached -> {
            assertThat(cached.snapshotId()).isEqualTo(persisted.snapshotId());
            assertThat(cached.receivedAt())
                    .isEqualTo(Instant.parse("2026-08-12T12:00:00.275Z"));
            assertThat(cached.cachedAt())
                    .isEqualTo(Instant.parse("2026-08-12T12:20:00.100Z"));
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

    @Test
    void persistsCanonicalIdentityAndAppendOnlyEventVersionsWithCompleteSnapshotTraceability() {
        byte[] firstPayload = "{\"events\":[{\"id\":9001}]}"
                .getBytes(StandardCharsets.UTF_8);
        var firstSnapshot = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-15|page=1",
                firstPayload));
        EventSourceTrace firstSource = EventSourceTrace.providerSnapshot(
                firstSnapshot.snapshotId(),
                firstSnapshot.payloadSha256(),
                ScheduledEventsV1Parser.PARSER_VERSION,
                Instant.parse("2026-08-12T12:00:00.275Z"));
        ScheduledEvent original = new ScheduledEvent(
                9001L,
                Instant.parse("2026-08-15T18:45:00Z"),
                new ScheduledTeam(101L, "Local FC"),
                new ScheduledTeam(202L, "Visitor FC"),
                new ScheduledEventStatus("scheduled", Optional.empty()),
                Optional.of(new ScheduledTournament(301L, "Local Cup")));

        var first = canonicalEventStore.save(CanonicalEventObservation.from(
                original,
                firstSource));
        var duplicate = canonicalEventStore.save(CanonicalEventObservation.from(
                original,
                firstSource));

        byte[] changedPayload = "{\"events\":[{\"id\":9001,\"changed\":true}]}"
                .getBytes(StandardCharsets.UTF_8);
        var changedSnapshot = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-15|page=1",
                changedPayload));
        EventSourceTrace changedSource = EventSourceTrace.providerSnapshot(
                changedSnapshot.snapshotId(),
                changedSnapshot.payloadSha256(),
                ScheduledEventsV1Parser.PARSER_VERSION,
                Instant.parse("2026-08-12T12:05:00Z"));
        ScheduledEvent postponed = new ScheduledEvent(
                original.providerEventId(),
                Instant.parse("2026-08-16T19:00:00Z"),
                new ScheduledTeam(101L, "Local United"),
                original.awayTeam(),
                new ScheduledEventStatus("postponed", Optional.of("Postponed")),
                original.tournament());
        var second = canonicalEventStore.save(CanonicalEventObservation.from(
                postponed,
                changedSource));

        assertThat(first.inserted()).isTrue();
        assertThat(duplicate.inserted()).isFalse();
        assertThat(duplicate.observationId()).isEqualTo(first.observationId());
        assertThat(second.canonicalEventId()).isEqualTo(first.canonicalEventId());
        assertThat(second.observationCount()).isEqualTo(2L);
        assertThat(canonicalEventStore.findHistory(first.canonicalEventId()))
                .hasSize(2)
                .extracting(view -> view.source().snapshotId().orElseThrow())
                .containsExactly(changedSnapshot.snapshotId(), firstSnapshot.snapshotId());
        assertThat(canonicalEventStore.findLatestStartingBetween(
                Instant.parse("2026-08-15T00:00:00Z"),
                Instant.parse("2026-08-16T00:00:00Z")))
                .isEmpty();
        assertThat(canonicalEventStore.findLatestStartingBetween(
                Instant.parse("2026-08-16T00:00:00Z"),
                Instant.parse("2026-08-17T00:00:00Z")))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.identity().value()).isEqualTo(first.canonicalEventId());
                    assertThat(view.homeTeam().name()).isEqualTo("Local United");
                    assertThat(view.status().type()).isEqualTo("postponed");
                    assertThat(view.observationCount()).isEqualTo(2L);
                    assertThat(view.source().snapshotId())
                            .hasValue(changedSnapshot.snapshotId());
                });

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update canonical_event_observation set status_type = ? where id = ?",
                "changed",
                second.observationId()))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining(
                        "canonical_event_observation is append-only");
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

    private Long snapshotRowCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from provider_snapshot",
                Long.class);
    }

    private Long cacheCheckpointRowCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from provider_response_cache",
                Long.class);
    }

    private Instant cacheTimestamp(String requestKey) {
        return jdbcTemplate.queryForObject(
                """
                select cached_at
                from provider_response_cache
                where provider = 'SOFASCORE'
                  and logical_endpoint = 'SCHEDULED_EVENTS'
                  and request_key = ?
                """,
                (resultSet, rowNumber) -> resultSet.getObject(
                        "cached_at",
                        java.time.OffsetDateTime.class).toInstant(),
                requestKey);
    }
}
