package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.JdbcRawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.adapter.persistence.delivery.JdbcJ7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsParseEvidence;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV4Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV5Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV6Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV7Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV8Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV9Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV10Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV11Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV12Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV13Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV14Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV15Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV16Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV17Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchControlService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchError;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchException;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchPlan;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchUpload;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchUploadService;
import com.bettingproject.sofascorelocal.application.event.J5EventDataQueryService;
import com.bettingproject.sofascorelocal.application.event.TournamentCanonicalEventPersistenceService;
import com.bettingproject.sofascorelocal.application.network.TournamentScheduledEventsProjectionService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import com.bettingproject.sofascorelocal.application.network.J3TournamentCatalogService;
import com.bettingproject.sofascorelocal.application.network.J3QualificationCheckpointReparser;
import com.bettingproject.sofascorelocal.application.network.J5LocalUnavailableEvidence;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspectionService;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkReadEvidence;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkReport;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkService;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkWindow;
import com.bettingproject.sofascorelocal.application.benchmark.J8DirectObservationCohorts;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaign;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnit;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnitResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8ProviderCallAttempt;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J4ScheduledEventsSnapshotNormalizationService;
import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventSeason;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventVenue;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5UnavailableFamily;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryStream;
import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedCollectionEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.retention.J6BackupEvidence;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogStatus;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J3ScheduledEventsPageCache;
import com.bettingproject.sofascorelocal.port.J4EventDetailsCache;
import com.bettingproject.sofascorelocal.port.TournamentScheduledEventsCache;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.J6SnapshotHistoryStore;
import com.bettingproject.sofascorelocal.port.J6RawPayloadRetentionStore;
import com.bettingproject.sofascorelocal.port.J7ExportManifestStore;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.port.J8BenchmarkEvidenceStore;
import com.bettingproject.sofascorelocal.port.J8BenchmarkReadStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.J3QualificationCheckpointStore;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        registry.add("sofascore.enabled", () -> false);
        registry.add("sofascore.j3-qualification-enabled", () -> false);
        registry.add("sofascore.j4-event-details-qualification-enabled", () -> false);
        registry.add("sofascore.j4-event-details-phase2-enabled", () -> false);
        registry.add("sofascore.j5-event-data-qualification-enabled", () -> false);
        registry.add("sofascore.tournament-event-discovery-enabled", () -> false);
        registry.add("sofascore.base-url", () -> "");
        registry.add("sofascore.allowed-endpoints", () -> "");
        registry.add("sofascore.automatic-refresh-enabled", () -> false);
        registry.add("sofascore.live-polling-enabled", () -> false);
        registry.add("sofascore.store-raw-payloads", () -> true);
        registry.add("sofascore.export-directory", () -> "target/integration-test-exports");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    @Autowired
    RawManualCallSnapshotStore snapshotStore;

    @Autowired
    J3QualificationCheckpointStore checkpointStore;

    @Autowired
    J3QualificationCheckpointReparser checkpointReparser;

    @Autowired
    J3ScheduledEventsPageCache scheduledEventsPageCache;

    @Autowired
    J4EventDetailsCache eventDetailsCache;

    @Autowired
    TournamentScheduledEventsCache tournamentScheduledEventsCache;

    @Autowired
    RawSnapshotInspectionStore snapshotInspectionStore;

    @Autowired
    RawSnapshotJsonInspectionService snapshotInspectionService;

    @Autowired
    CanonicalEventStore canonicalEventStore;

    @Autowired
    EventDetailsStore eventDetailsStore;

    @Autowired
    J4OfflineFixtureImportService j4OfflineFixtureImportService;

    @Autowired
    J4ScheduledEventsSnapshotNormalizationService j4SnapshotNormalizationService;

    @Autowired
    J4EventQueryService j4EventQueryService;

    @Autowired
    J4ParsedEventDetailsPersistenceService j4ParsedEventDetailsPersistenceService;

    @Autowired
    J5OfflineFixtureImportService j5OfflineFixtureImportService;

    @Autowired
    J5OfflineBatchControlService j5OfflineBatchControlService;

    @Autowired
    J5OfflineBatchImportService j5OfflineBatchImportService;

    @Autowired
    J5OfflineBatchUploadService j5OfflineBatchUploadService;

    @Autowired
    J5EventDataQueryService j5EventDataQueryService;

    @Autowired
    TournamentCanonicalEventPersistenceService tournamentCanonicalPersistenceService;

    @Autowired
    TournamentScheduledEventsProjectionService tournamentProjectionService;

    @Autowired
    J5EventDataStore j5EventDataStore;

    @Autowired
    J6SnapshotHistoryStore j6SnapshotHistoryStore;

    @Autowired
    J6RawPayloadRetentionStore j6RawPayloadRetentionStore;

    @Autowired
    J7CanonicalExportService j7CanonicalExportService;

    @Autowired
    J7ExportManifestStore j7ExportManifestStore;

    @Autowired
    J7DeliveryLedgerStore j7DeliveryLedgerStore;

    @Autowired
    J8BenchmarkEvidenceStore j8BenchmarkEvidenceStore;

    @Autowired
    J8BenchmarkReadStore j8BenchmarkReadStore;

    @Autowired
    J8BenchmarkService j8BenchmarkService;

    @Test
    void createsTheJ3RawSnapshotSchemaAndKeepsNetworkDisabled() {
        String snapshotTable = jdbcTemplate.queryForObject(
                "select to_regclass('public.provider_snapshot')", String.class);
        String exportTable = jdbcTemplate.queryForObject(
                "select to_regclass('public.export_manifest')", String.class);
        String deliveryTable = jdbcTemplate.queryForObject(
                "select to_regclass('public.j7_delivery')", String.class);
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
        assertThat(deliveryTable).isEqualTo("j7_delivery");
        assertThat(networkEnabled).isFalse();
        assertThat(flywayVersion).isEqualTo("38");
        assertThat(rawColumn).isEqualTo("bytea");
    }

    @Test
    void startsTheJ7DeliveryLedgerStoreWithTransactionalAdvice() {
        assertThat(AopUtils.isAopProxy(j7DeliveryLedgerStore)).isTrue();
        assertThat(AopUtils.getTargetClass(j7DeliveryLedgerStore))
                .isEqualTo(JdbcJ7DeliveryLedgerStore.class);
        assertThat(j7DeliveryLedgerStore).isInstanceOf(Advised.class);
        assertThat(((Advised) j7DeliveryLedgerStore).getAdvisors())
                .anySatisfy(advisor -> assertThat(advisor.getAdvice())
                        .isInstanceOf(TransactionInterceptor.class));
    }

    @Test
    void extendsOnlyTheParsedResponseCacheScopeForTournamentDiscovery() {
        String definition = jdbcTemplate.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conrelid = 'provider_response_cache'::regclass
                  and conname = 'ck_provider_response_cache_scope'
                """,
                String.class);

        assertThat(definition)
                .contains("SCHEDULED_EVENTS")
                .contains("EVENT_DETAILS")
                .contains("TOURNAMENT_SCHEDULED_EVENTS");
    }

    @Test
    void upgradesV23ToV24WithoutLosingAnExistingJ3CacheCheckpoint() {
        String schema = "upgrade_v23_to_v24_tournament_cache";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var upgradeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway toV23 = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("23"))
                .load();
        assertThat(toV23.migrate().migrationsExecuted).isEqualTo(23);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        byte[] bytes = "{\"scheduled\":[],\"hasNextPage\":false}"
                .getBytes(StandardCharsets.UTF_8);
        String sha256 = com.bettingproject.sofascorelocal.security.Sha256.hex(bytes);
        Long snapshotId = upgradeJdbc.queryForObject(
                """
                insert into provider_snapshot (
                    provider, logical_endpoint, request_key,
                    requested_at, received_at, http_status, content_type, latency_ms,
                    payload_raw, payload_size_bytes, payload_sha256,
                    parser_version, schema_status
                ) values (
                    'SOFASCORE', 'SCHEDULED_EVENTS',
                    'SCHEDULED_EVENTS|date=2026-08-18|page=1',
                    ?, ?, 200, 'application/json', 25,
                    ?, ?, ?, 'scheduled-events-v1', 'PARSED'
                )
                returning id
                """,
                Long.class,
                Timestamp.from(Instant.parse("2026-08-18T08:00:00Z")),
                Timestamp.from(Instant.parse("2026-08-18T08:00:00.025Z")),
                bytes,
                bytes.length,
                sha256);
        upgradeJdbc.update(
                """
                insert into provider_response_cache (
                    provider, logical_endpoint, request_key,
                    snapshot_id, cached_at, parser_version
                ) values (
                    'SOFASCORE', 'SCHEDULED_EVENTS',
                    'SCHEDULED_EVENTS|date=2026-08-18|page=1',
                    ?, ?, 'scheduled-events-v1'
                )
                """,
                snapshotId,
                Timestamp.from(Instant.parse("2026-08-18T08:00:00.025Z")));

        Flyway toV24 = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("24"))
                .load();
        assertThat(toV24.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(toV24.info().current().getVersion().getVersion()).isEqualTo("24");
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from provider_response_cache",
                Long.class)).isEqualTo(1L);
        assertThat(upgradeJdbc.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conrelid = 'provider_response_cache'::regclass
                  and conname = 'ck_provider_response_cache_scope'
                """,
                String.class)).contains("TOURNAMENT_SCHEDULED_EVENTS");
    }

    @Test
    void upgradesV24ToV25AndSeparatesDirectResponsesFromManualJsonImports() {
        String schema = "upgrade_v24_to_v25_local_import";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var upgradeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway toV24 = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("24"))
                .load();
        assertThat(toV24.migrate().migrationsExecuted).isEqualTo(24);

        Flyway latest = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("25"))
                .load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("25");

        JdbcRawManualCallSnapshotStore upgradeStore =
                new JdbcRawManualCallSnapshotStore(
                        new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(
                                upgradeDataSource));
        Instant importedAt = Instant.parse("2026-08-20T18:00:00Z");
        byte[] bytes = "{\"events\":[]}".getBytes(StandardCharsets.UTF_8);
        RawPayloadEvidence payload = RawPayloadEvidence.capture(bytes);
        String requestKey =
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-20|uniqueTournamentId=8";
        RawManualCallSnapshot direct = new RawManualCallSnapshot(
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                requestKey,
                importedAt.minusMillis(20),
                importedAt,
                200,
                "application/json",
                Duration.ofMillis(20),
                payload,
                TournamentScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null);
        RawManualCallSnapshot imported = new RawManualCallSnapshot(
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT,
                requestKey,
                importedAt,
                importedAt,
                200,
                "application/json",
                Duration.ZERO,
                payload,
                TournamentScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null);

        var directFirst = upgradeStore.save(direct);
        var directSecond = upgradeStore.save(direct);
        var importFirst = upgradeStore.save(imported);
        var importSecond = upgradeStore.save(imported);

        assertThat(directFirst.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.INSERTED);
        assertThat(directSecond.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(importFirst.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.INSERTED);
        assertThat(importSecond.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(directSecond.snapshotId()).isEqualTo(directFirst.snapshotId());
        assertThat(importSecond.snapshotId()).isEqualTo(importFirst.snapshotId());
        assertThat(importFirst.snapshotId()).isNotEqualTo(directFirst.snapshotId());

        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from provider_snapshot where request_key = ?",
                Long.class,
                requestKey)).isEqualTo(2L);
        assertThat(upgradeJdbc.queryForList(
                "select acquisition_mode from provider_snapshot where request_key = ? "
                        + "order by acquisition_mode",
                String.class,
                requestKey)).containsExactly(
                        "DIRECT_LOCAL_ENDPOINT",
                        "MANUAL_LOCAL_JSON_IMPORT");
    }

    @Test
    void upgradesV25ForTheLiveExtraTimePeriodWithoutRewritingV13History() {
        String schema = "upgrade_v25_to_v26_live_extra_time";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV25 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("25"))
                .load();

        assertThat(flywayV25.migrate().migrationsExecuted).isEqualTo(25);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id, provider, provider_event_id, created_at
                ) values (
                    '26262626-2626-2626-2626-262626262626',
                    'SOFASCORE',
                    16717086,
                    '2026-08-27T10:00:00Z'
                )
                """);
        Long historicalObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '26262626-2626-2626-2626-262626262626', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'historical-v13-card', 'historical-v13-card',
                    repeat('a', 64), ?, '2026-08-27T10:00:01Z', 'COMPLETE', 100,
                    3, 3, '[]'::jsonb, repeat('b', 64)
                )
                returning id
                """, Long.class, EventIncidentsV13Parser.PARSER_VERSION);
        Map<String, Object> historicalBefore = upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId);

        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV13Parser.PARSER_VERSION)
                .doesNotContain(EventIncidentsV14Parser.PARSER_VERSION);
        assertThatThrownBy(() -> upgradeJdbc.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '26262626-2626-2626-2626-262626262626', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v14-before-migration', 'v14-before-migration',
                    repeat('c', 64), ?, '2026-08-27T10:00:02Z', 'COMPLETE', 100,
                    3, 3, '[]'::jsonb, repeat('d', 64)
                )
                """, EventIncidentsV14Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway flywayV26 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("26"))
                .load();

        assertThat(flywayV26.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV26.info().current().getVersion().getVersion()).isEqualTo("26");
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV13Parser.PARSER_VERSION)
                .contains(EventIncidentsV14Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalBefore);

        Long v14ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '26262626-2626-2626-2626-262626262626', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v14-live-extra-time', 'v14-live-extra-time',
                    repeat('e', 64), ?, '2026-08-27T10:00:03Z', 'COMPLETE', 100,
                    3, 3, '[]'::jsonb, repeat('f', 64)
                )
                returning id
                """, Long.class, EventIncidentsV14Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    home_score, away_score, period_text
                ) values (?, 0, 'period', 120, 1, 1, 'Extra time')
                """, v14ObservationId);
        assertThat(upgradeJdbc.queryForMap("""
                select parser_version, incident_type, minute,
                       home_score, away_score, period_text
                from j5_event_data_observation observation
                join j5_event_incident incident on incident.observation_id = observation.id
                where observation.id = ?
                """, v14ObservationId))
                .containsEntry("parser_version", EventIncidentsV14Parser.PARSER_VERSION)
                .containsEntry("incident_type", "period")
                .containsEntry("minute", 120)
                .containsEntry("home_score", 1)
                .containsEntry("away_score", 1)
                .containsEntry("period_text", "Extra time");
    }

    @Test
    void upgradesV26ToV27WithoutBackfillingOrRewritingHistoricalEvidence() {
        String schema = "upgrade_v26_to_v27_j8_evidence";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV26 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("26"))
                .load();

        assertThat(flywayV26.migrate().migrationsExecuted).isEqualTo(26);
        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        Long snapshotId = upgradeJdbc.queryForObject("""
                insert into provider_snapshot (
                    provider, acquisition_mode, logical_endpoint, request_key,
                    requested_at, received_at, http_status, content_type, latency_ms,
                    payload_raw, payload_size_bytes, payload_sha256,
                    parser_version, schema_status
                ) values (
                    'SOFASCORE', 'DIRECT_LOCAL_ENDPOINT', 'EVENT_DETAILS',
                    'EVENT_DETAILS|eventId=27000001',
                    '2026-08-28T10:00:00Z', '2026-08-28T10:00:00.025Z',
                    200, 'application/json', 25,
                    decode('7b7d', 'hex'), 2, repeat('a', 64),
                    'event-details-v2', 'PARSED'
                )
                returning id
                """, Long.class);
        Long occurrenceId = upgradeJdbc.queryForObject("""
                insert into provider_snapshot_occurrence (
                    snapshot_id, requested_at, received_at, http_status,
                    content_type, latency_ms, parser_version, persistence_outcome
                ) values (
                    ?, '2026-08-28T10:00:00Z', '2026-08-28T10:00:00.025Z',
                    200, 'application/json', 25, 'event-details-v2', 'INSERTED'
                )
                returning id
                """, Long.class, snapshotId);
        Map<String, Object> snapshotBefore = upgradeJdbc.queryForMap("""
                select id, provider, acquisition_mode, logical_endpoint, request_key,
                       requested_at, received_at, http_status, content_type, latency_ms,
                       payload_size_bytes, payload_sha256, parser_version, schema_status,
                       error_code, payload_purged_at, created_at
                from provider_snapshot
                where id = ?
                """, snapshotId);
        Map<String, Object> occurrenceBefore = upgradeJdbc.queryForMap("""
                select id, snapshot_id, requested_at, received_at, http_status,
                       content_type, latency_ms, parser_version, persistence_outcome,
                       created_at
                from provider_snapshot_occurrence
                where id = ?
                """, occurrenceId);

        Flyway flywayV27 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("27"))
                .load();

        assertThat(flywayV27.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV27.info().current().getVersion().getVersion()).isEqualTo("27");
        assertThat(upgradeJdbc.queryForMap("""
                select id, provider, acquisition_mode, logical_endpoint, request_key,
                       requested_at, received_at, http_status, content_type, latency_ms,
                       payload_size_bytes, payload_sha256, parser_version, schema_status,
                       error_code, payload_purged_at, created_at
                from provider_snapshot
                where id = ?
                """, snapshotId)).containsAllEntriesOf(snapshotBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select id, snapshot_id, requested_at, received_at, http_status,
                       content_type, latency_ms, parser_version, persistence_outcome,
                       created_at
                from provider_snapshot_occurrence
                where id = ?
                """, occurrenceId)).containsAllEntriesOf(occurrenceBefore);
        assertThat(upgradeJdbc.queryForList("""
                select to_regclass(table_name)::text
                from (values
                    ('j8_benchmark_campaign'),
                    ('j8_benchmark_unit'),
                    ('j8_provider_call_attempt'),
                    ('j8_benchmark_unit_result'),
                    ('j8_benchmark_campaign_result')
                ) tables(table_name)
                order by table_name
                """, String.class)).containsExactly(
                        "j8_benchmark_campaign",
                        "j8_benchmark_campaign_result",
                        "j8_benchmark_unit",
                        "j8_benchmark_unit_result",
                        "j8_provider_call_attempt");
        assertThat(upgradeJdbc.queryForObject(
                "select to_regclass('ix_provider_snapshot_occurrence_j8_requested')::text",
                String.class))
                .isEqualTo("ix_provider_snapshot_occurrence_j8_requested");
        for (String table : List.of(
                "j8_benchmark_campaign",
                "j8_benchmark_unit",
                "j8_provider_call_attempt",
                "j8_benchmark_unit_result",
                "j8_benchmark_campaign_result")) {
            assertThat(upgradeJdbc.queryForObject(
                    "select count(*) from " + table,
                    Long.class)).isZero();
        }
    }

    @Test
    void upgradesV27ToV28WithoutRewritingV14OrJ8Evidence() {
        String schema = "upgrade_v27_to_v28_incidents_v15";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var upgradeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV27 = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("27"))
                .load();

        assertThat(flywayV27.migrate().migrationsExecuted).isEqualTo(27);
        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        UUID canonicalEventId = UUID.fromString(
                "28282828-2828-2828-2828-282828282828");
        upgradeJdbc.update("""
                insert into canonical_event (id, provider, provider_event_id, created_at)
                values (?, 'SOFASCORE', 16691018, '2026-08-30T04:30:00Z')
                """, canonicalEventId);
        Long v14ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    ?, 'EVENT_INCIDENTS', 'SYNTHETIC_FIXTURE',
                    'historical-v14-unminuted-shootout', 'historical-v14-unminuted-shootout',
                    repeat('a', 64), ?, '2026-08-30T04:30:01Z', 'PARTIAL', 83,
                    5, 6, '["$.incidents[0].time"]'::jsonb, repeat('b', 64)
                ) returning id
                """, Long.class, canonicalEventId, EventIncidentsV14Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, home_score, away_score, shootout_sequence
                ) values (?, 0, 'penaltyShootout', null, true, 2, 1, 1)
                """, v14ObservationId);
        Map<String, Object> v14ObservationBefore = upgradeJdbc.queryForMap(
                "select * from j5_event_data_observation where id = ?",
                v14ObservationId);
        Map<String, Object> v14IncidentBefore = upgradeJdbc.queryForMap(
                "select * from j5_event_incident where observation_id = ?",
                v14ObservationId);

        UUID campaignId = UUID.fromString("28282828-0000-0000-0000-000000000001");
        Long unitId = upgradeJdbc.queryForObject("""
                insert into j8_benchmark_campaign (
                    campaign_id, campaign_type, execution_mode, started_at,
                    maximum_units, collection_date
                ) values (?, 'J3_SCHEDULED_EVENTS', 'GUARDED_PROVIDER',
                          '2026-08-30T04:31:00Z', 25, '2026-08-30')
                returning 1
                """, Long.class, campaignId);
        assertThat(unitId).isOne();
        unitId = upgradeJdbc.queryForObject("""
                insert into j8_benchmark_unit (
                    campaign_id, unit_ordinal, logical_endpoint, request_key, declared_at
                ) values (?, 1, 'SCHEDULED_EVENTS',
                          'SCHEDULED_EVENTS|date=2026-08-30|page=1',
                          '2026-08-30T04:31:01Z')
                returning id
                """, Long.class, campaignId);
        Long attemptId = upgradeJdbc.queryForObject("""
                insert into j8_provider_call_attempt (unit_id, started_at)
                values (?, '2026-08-30T04:31:02Z')
                returning id
                """, Long.class, unitId);
        upgradeJdbc.update("""
                insert into j8_benchmark_unit_result (
                    unit_id, attempt_id, resolved_at, resolution_source, outcome_type,
                    response_received, parser_warning_count, terminal_code
                ) values (?, ?, '2026-08-30T04:31:03Z', 'PROVIDER',
                          'TRANSPORT_FAILURE', false, 0, 'V28_MIGRATION_SENTINEL')
                """, unitId, attemptId);
        upgradeJdbc.update("""
                insert into j8_benchmark_campaign_result (
                    campaign_id, finished_at, terminal_state, terminal_code,
                    completed_units
                ) values (?, '2026-08-30T04:31:04Z', 'FAILED',
                          'V28_MIGRATION_SENTINEL', 1)
                """, campaignId);
        List<String> j8Tables = List.of(
                "j8_benchmark_campaign",
                "j8_benchmark_unit",
                "j8_provider_call_attempt",
                "j8_benchmark_unit_result",
                "j8_benchmark_campaign_result");
        List<String> j8EvidenceBefore = j8Tables.stream()
                .map(table -> upgradeJdbc.queryForObject(
                        "select to_jsonb(evidence)::text from " + table + " evidence",
                        String.class))
                .toList();

        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV14Parser.PARSER_VERSION)
                .doesNotContain(EventIncidentsV15Parser.PARSER_VERSION);
        assertThatThrownBy(() -> upgradeJdbc.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    ?, 'EVENT_INCIDENTS', 'SYNTHETIC_FIXTURE',
                    'v15-before-v28', 'v15-before-v28', repeat('c', 64), ?,
                    '2026-08-30T04:32:00Z', 'PARTIAL', 90,
                    10, 11, '["$.incidents[0].time"]'::jsonb, repeat('d', 64)
                )
                """, canonicalEventId, EventIncidentsV15Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway flywayV28 = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("28"))
                .load();

        assertThat(flywayV28.migrate().migrationsExecuted).isOne();
        assertThat(flywayV28.info().current().getVersion().getVersion()).isEqualTo("28");
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV14Parser.PARSER_VERSION)
                .contains(EventIncidentsV15Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForMap(
                "select * from j5_event_data_observation where id = ?",
                v14ObservationId)).containsAllEntriesOf(v14ObservationBefore);
        assertThat(upgradeJdbc.queryForMap(
                "select * from j5_event_incident where observation_id = ?",
                v14ObservationId)).containsAllEntriesOf(v14IncidentBefore);
        assertThat(j8Tables.stream()
                .map(table -> upgradeJdbc.queryForObject(
                        "select to_jsonb(evidence)::text from " + table + " evidence",
                        String.class))
                .toList()).containsExactlyElementsOf(j8EvidenceBefore);

        Long v15ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    ?, 'EVENT_INCIDENTS', 'SYNTHETIC_FIXTURE',
                    'v15-after-v28', 'v15-after-v28', repeat('c', 64), ?,
                    '2026-08-30T04:32:01Z', 'PARTIAL', 90,
                    10, 11, '["$.incidents[0].time"]'::jsonb, repeat('d', 64)
                ) returning id
                """, Long.class, canonicalEventId, EventIncidentsV15Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, home_score, away_score, shootout_sequence
                ) values (?, 0, 'penaltyShootout', null, true, 2, 1, 1)
                """, v15ObservationId);
        assertThat(upgradeJdbc.queryForMap("""
                select observation.parser_version, incident.minute,
                       incident.incident_type, incident.shootout_sequence
                from j5_event_data_observation observation
                join j5_event_incident incident on incident.observation_id = observation.id
                where observation.id = ?
                """, v15ObservationId))
                .containsEntry("parser_version", EventIncidentsV15Parser.PARSER_VERSION)
                .containsEntry("minute", null)
                .containsEntry("incident_type", "penaltyShootout")
                .containsEntry("shootout_sequence", 1);
    }

    @Test
    void upgradesV35ToV36WithoutRewritingHistoricalIncidentsOrTheirProvenance() {
        // V31 defines functions explicitly in public, so this upgrade needs a separate database.
        String database = "upgrade_v35_v36_" + UUID.randomUUID().toString().replace("-", "");
        var adminDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new JdbcTemplate(adminDataSource).execute("create database " + database);
        String databaseUrl = POSTGRES.getJdbcUrl().substring(0, POSTGRES.getJdbcUrl().lastIndexOf('/') + 1)
                + database;
        String schema = "public";
        var dataSource = new DriverManagerDataSource(
                databaseUrl,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway before = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .createSchemas(true).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("35")).load();
        assertThat(before.migrate().migrationsExecuted).isEqualTo(35);
        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        assertThat(upgradeJdbc.queryForObject("select current_database()", String.class)).isEqualTo(database);
        UUID eventId = UUID.fromString("36363636-3636-3636-3636-363636363636");
        upgradeJdbc.update("""
                insert into canonical_event (id, provider, provider_event_id, created_at)
                values (?, 'SOFASCORE', 900036, '2026-09-07T20:30:00Z')
                """, eventId);
        String insertObservation = """
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version, source_received_at,
                    completeness_status, completeness_score, present_signals, expected_signals,
                    missing_paths_json, normalized_sha256
                ) values (?, ?, 'SYNTHETIC_FIXTURE', ?, ?, repeat('a', 64), ?,
                          '2026-09-07T20:31:00Z', 'COMPLETE', 100, 2, 2, '[]'::jsonb, repeat('b', 64))
                returning id
                """;
        Long historicalId = upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_INCIDENTS", "v15-before-v36", "v15-before-v36", EventIncidentsV15Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute, is_home,
                    player_provider_id, player_name, incident_class, reason, description
                ) values (?, 0, 'inGamePenalty', 74, false, 9001, 'Synthetic taker',
                          'missed', 'goalkeeperSave', 'Goalkeeper save')
                """, historicalId);
        Map<String, Object> observationBefore = upgradeJdbc.queryForMap(
                "select * from j5_event_data_observation where id = ?", historicalId);
        Map<String, Object> incidentBefore = upgradeJdbc.queryForMap(
                "select * from j5_event_incident where observation_id = ?", historicalId);
        assertThat(parserConstraint(upgradeJdbc, schema)).doesNotContain(EventIncidentsV16Parser.PARSER_VERSION);
        assertThatThrownBy(() -> upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_INCIDENTS", "v16-before-v36", "v16-before-v36", EventIncidentsV16Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway after = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("36")).load();
        assertThat(after.migrate().migrationsExecuted).isOne();
        assertThat(after.info().current().getVersion().getVersion()).isEqualTo("36");
        assertThat(upgradeJdbc.queryForMap("select * from j5_event_data_observation where id = ?", historicalId))
                .isEqualTo(observationBefore);
        assertThat(upgradeJdbc.queryForMap("select * from j5_event_incident where observation_id = ?", historicalId))
                .isEqualTo(incidentBefore);
        String constraint = parserConstraint(upgradeJdbc, schema);
        for (int version = 1; version <= 16; version++) assertThat(constraint).contains("'event-incidents-v" + version + "'");
        assertThat(constraint).contains("event-incidents-unavailable-v1", "event-statistics-v1",
                "event-statistics-v2", "event-statistics-unavailable-v1", "event-lineups-v1",
                "event-lineups-v2", "event-lineups-unavailable-v1");
        Long awardedId = upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_INCIDENTS", "v16-after-v36", "v16-after-v36", EventIncidentsV16Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute, is_home, incident_class
                ) values (?, 0, 'inGamePenalty', 83, true, 'awarded')
                """, awardedId);
        assertThat(upgradeJdbc.queryForMap("""
                select incident_class, player_name, home_score, away_score, reason, description
                from j5_event_incident where observation_id = ?
                """, awardedId)).containsEntry("incident_class", "awarded")
                .containsEntry("player_name", null).containsEntry("home_score", null)
                .containsEntry("away_score", null).containsEntry("reason", null).containsEntry("description", null);
        assertThatThrownBy(() -> upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_STATISTICS", "v16-wrong-family", "v16-wrong-family", EventIncidentsV16Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_INCIDENTS", "unknown-parser", "unknown-parser", "event-incidents-v17"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(after.migrate().migrationsExecuted).isZero();
        assertThat(upgradeJdbc.queryForObject("select count(*) from j5_event_data_observation", Long.class)).isEqualTo(2);
    }

    @Test
    void upgradesV36ToV37WithoutRewritingHistoricalIncidentsOrTheirProvenance() {
        // V31 defines functions explicitly in public, so this upgrade needs a separate database.
        String database = "upgrade_v36_v37_" + UUID.randomUUID().toString().replace("-", "");
        var adminDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new JdbcTemplate(adminDataSource).execute("create database " + database);
        String databaseUrl = POSTGRES.getJdbcUrl().substring(0, POSTGRES.getJdbcUrl().lastIndexOf('/') + 1)
                + database;
        String schema = "public";
        var dataSource = new DriverManagerDataSource(
                databaseUrl,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway before = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .createSchemas(true).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("36")).load();
        assertThat(before.migrate().migrationsExecuted).isEqualTo(36);
        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        assertThat(upgradeJdbc.queryForObject("select current_database()", String.class)).isEqualTo(database);
        UUID eventId = UUID.fromString("36363636-3636-3636-3636-363636363636");
        upgradeJdbc.update("""
                insert into canonical_event (id, provider, provider_event_id, created_at)
                values (?, 'SOFASCORE', 900036, '2026-09-07T20:30:00Z')
                """, eventId);
        String insertObservation = """
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version, source_received_at,
                    completeness_status, completeness_score, present_signals, expected_signals,
                    missing_paths_json, normalized_sha256
                ) values (?, ?, 'SYNTHETIC_FIXTURE', ?, ?, repeat('a', 64), ?,
                          '2026-09-07T20:31:00Z', 'COMPLETE', 100, 2, 2, '[]'::jsonb, repeat('b', 64))
                returning id
                """;
        Long historicalId = upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_INCIDENTS", "v16-before-v37", "v16-before-v37", EventIncidentsV16Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute, is_home,
                    player_provider_id, player_name, incident_class, reason, description
                ) values (?, 0, 'inGamePenalty', 74, false, 9001, 'Synthetic taker',
                          'missed', 'goalkeeperSave', 'Goalkeeper save')
                """, historicalId);
        Map<String, Object> observationBefore = upgradeJdbc.queryForMap(
                "select * from j5_event_data_observation where id = ?", historicalId);
        Map<String, Object> incidentBefore = upgradeJdbc.queryForMap(
                "select * from j5_event_incident where observation_id = ?", historicalId);
        assertThat(parserConstraint(upgradeJdbc, schema)).doesNotContain(EventIncidentsV17Parser.PARSER_VERSION);
        assertThatThrownBy(() -> upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_INCIDENTS", "v17-before-v37", "v17-before-v37", EventIncidentsV17Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway after = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("37")).load();
        assertThat(after.migrate().migrationsExecuted).isOne();
        assertThat(after.info().current().getVersion().getVersion()).isEqualTo("37");
        assertThat(upgradeJdbc.queryForMap("select * from j5_event_data_observation where id = ?", historicalId))
                .isEqualTo(observationBefore);
        assertThat(upgradeJdbc.queryForMap("select * from j5_event_incident where observation_id = ?", historicalId))
                .isEqualTo(incidentBefore);
        String constraint = parserConstraint(upgradeJdbc, schema);
        for (int version = 1; version <= 17; version++) assertThat(constraint).contains("'event-incidents-v" + version + "'");
        assertThat(constraint).contains("event-incidents-unavailable-v1", "event-statistics-v1",
                "event-statistics-v2", "event-statistics-unavailable-v1", "event-lineups-v1",
                "event-lineups-v2", "event-lineups-unavailable-v1");
        Long handballId = upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_INCIDENTS", "v17-after-v37", "v17-after-v37", EventIncidentsV17Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute, is_home, incident_class, reason
                ) values (?, 0, 'card', 64, false, 'red', 'Professional handball')
                """, handballId);
        assertThat(upgradeJdbc.queryForMap("""
                select incident_class, player_name, home_score, away_score, reason, description
                from j5_event_incident where observation_id = ?
                """, handballId)).containsEntry("incident_class", "red")
                .containsEntry("player_name", null).containsEntry("home_score", null)
                .containsEntry("away_score", null).containsEntry("reason", "Professional handball").containsEntry("description", null);
        assertThatThrownBy(() -> upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_STATISTICS", "v17-wrong-family", "v17-wrong-family", EventIncidentsV17Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> upgradeJdbc.queryForObject(insertObservation, Long.class, eventId,
                "EVENT_INCIDENTS", "unknown-parser", "unknown-parser", "event-incidents-v18"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(after.migrate().migrationsExecuted).isZero();
        assertThat(upgradeJdbc.queryForObject("select count(*) from j5_event_data_observation", Long.class)).isEqualTo(2);
    }

    @Test
    void persistsAndReplaysV16AwardedPenaltyWithExactSnapshotProvenanceAndNoInventedOutcome() {
        var j4Import = j4OfflineFixtureImportService.importNominalCorpus();
        Instant requestedAt = Instant.parse("2026-09-07T20:31:00Z");
        Instant receivedAt = requestedAt.plusMillis(100);
        RawPayloadEvidence payload = RawPayloadEvidence.capture("""
                {"incidents":[{"incidentType":"inGamePenalty","incidentClass":"awarded",
                               "time":83,"isHome":true}]}
                """.getBytes(StandardCharsets.UTF_8));
        var raw = snapshotStore.save(new RawManualCallSnapshot(SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=900001|rules=v16-awarded-penalty", requestedAt, receivedAt,
                200, "application/json", Duration.ofMillis(100), payload,
                EventIncidentsV16Parser.PARSER_VERSION, RawSnapshotSchemaStatus.RAW_ONLY, null));
        var parsed = new EventIncidentsV16Parser().parse(raw.snapshotId(), 900001L, payload, receivedAt);
        var identity = canonicalEventStore.findLatestByCanonicalId(j4Import.canonicalEventId()).orElseThrow().identity();
        var observation = J5EventDataObservation.from(identity, parsed.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(raw.snapshotId(), raw.payloadSha256(),
                        EventIncidentsV16Parser.PARSER_VERSION, receivedAt), parsed.completeness().orElseThrow());

        var persisted = j5EventDataStore.save(observation);
        var repeated = j5EventDataStore.save(observation);

        assertThat(persisted.inserted()).isTrue();
        assertThat(repeated.inserted()).isFalse();
        assertThat(repeated.observationId()).isEqualTo(persisted.observationId());
        assertThat(jdbcTemplate.queryForMap("""
                select source_snapshot_id, source_payload_sha256, parser_version,
                       source_received_at, normalized_sha256, completeness_status, completeness_score
                from j5_event_data_observation where id = ?
                """, persisted.observationId()))
                .containsEntry("source_snapshot_id", raw.snapshotId())
                .containsEntry("source_payload_sha256", payload.sha256())
                .containsEntry("parser_version", EventIncidentsV16Parser.PARSER_VERSION)
                .containsEntry("source_received_at", Timestamp.from(receivedAt))
                .containsEntry("normalized_sha256", observation.normalizedSha256())
                .containsEntry("completeness_status", "COMPLETE")
                .containsEntry("completeness_score", 100);
        assertThat(j5EventDataStore.findByObservationId(identity.value(), SofascoreEndpointType.EVENT_INCIDENTS,
                persisted.observationId())).hasValueSatisfying(read -> {
            assertThat(read.source()).isEqualTo(observation.source());
            assertThat(read.normalizedSha256()).isEqualTo(observation.normalizedSha256());
            assertThat(read.completeness()).isEqualTo(observation.completeness());
            assertThat(read.data()).isEqualTo(observation.data());
        });
        assertThat(jdbcTemplate.queryForList("""
                select incident_class, player_name, minute, is_home, home_score, away_score, reason, description
                from j5_event_incident where observation_id = ?
                """, persisted.observationId())).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("incident_class", "awarded").containsEntry("minute", 83).containsEntry("is_home", true)
                .containsEntry("player_name", null).containsEntry("home_score", null).containsEntry("away_score", null)
                .containsEntry("reason", null).containsEntry("description", null));
    }

    @Test
    void persistsAndReplaysV17ProfessionalHandballWithExactSnapshotProvenance() {
        var j4Import = j4OfflineFixtureImportService.importNominalCorpus();
        Instant requestedAt = Instant.parse("2026-09-07T20:31:00Z");
        Instant receivedAt = requestedAt.plusMillis(100);
        RawPayloadEvidence payload = RawPayloadEvidence.capture("""
                {"incidents":[{"incidentType":"card","incidentClass":"red",
                               "time":64,"isHome":false,"reason":"Professional handball",
                               "player":{"id":9001,"name":"Synthetic defender"},"rescinded":false}]}
                """.getBytes(StandardCharsets.UTF_8));
        var raw = snapshotStore.save(new RawManualCallSnapshot(SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=900001|rules=v17-professional-handball", requestedAt, receivedAt,
                200, "application/json", Duration.ofMillis(100), payload,
                EventIncidentsV17Parser.PARSER_VERSION, RawSnapshotSchemaStatus.RAW_ONLY, null));
        var parsed = new EventIncidentsV17Parser().parse(raw.snapshotId(), 900001L, payload, receivedAt);
        var identity = canonicalEventStore.findLatestByCanonicalId(j4Import.canonicalEventId()).orElseThrow().identity();
        var observation = J5EventDataObservation.from(identity, parsed.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(raw.snapshotId(), raw.payloadSha256(),
                        EventIncidentsV17Parser.PARSER_VERSION, receivedAt), parsed.completeness().orElseThrow());

        var persisted = j5EventDataStore.save(observation);
        var repeated = j5EventDataStore.save(observation);

        assertThat(persisted.inserted()).isTrue();
        assertThat(repeated.inserted()).isFalse();
        assertThat(repeated.observationId()).isEqualTo(persisted.observationId());
        assertThat(jdbcTemplate.queryForMap("""
                select source_snapshot_id, source_payload_sha256, parser_version,
                       source_received_at, normalized_sha256, completeness_status, completeness_score
                from j5_event_data_observation where id = ?
                """, persisted.observationId()))
                .containsEntry("source_snapshot_id", raw.snapshotId())
                .containsEntry("source_payload_sha256", payload.sha256())
                .containsEntry("parser_version", EventIncidentsV17Parser.PARSER_VERSION)
                .containsEntry("source_received_at", Timestamp.from(receivedAt))
                .containsEntry("normalized_sha256", observation.normalizedSha256())
                .containsEntry("completeness_status", "COMPLETE")
                .containsEntry("completeness_score", 100);
        assertThat(j5EventDataStore.findByObservationId(identity.value(), SofascoreEndpointType.EVENT_INCIDENTS,
                persisted.observationId())).hasValueSatisfying(read -> {
            assertThat(read.source()).isEqualTo(observation.source());
            assertThat(read.normalizedSha256()).isEqualTo(observation.normalizedSha256());
            assertThat(read.completeness()).isEqualTo(observation.completeness());
            assertThat(read.data()).isEqualTo(observation.data());
        });
        assertThat(jdbcTemplate.queryForList("""
                select incident_class, player_name, minute, is_home, home_score, away_score, reason, description
                from j5_event_incident where observation_id = ?
                """, persisted.observationId())).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("incident_class", "red").containsEntry("minute", 64).containsEntry("is_home", false)
                .containsEntry("player_name", "Synthetic defender").containsEntry("home_score", null).containsEntry("away_score", null)
                .containsEntry("reason", "Professional handball").containsEntry("description", null));
    }

    @Test
    void upgradesV29ToV30WithoutRewritingTheLedgerAndRemovesOnlyCrossClockOrdering() {
        String schema = "upgrade_v29_to_v30_ack_receipt_time";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var upgradeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV29 = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("29"))
                .load();

        assertThat(flywayV29.migrate().migrationsExecuted).isEqualTo(29);
        assertThat(flywayV29.info().current().getVersion().getVersion()).isEqualTo("29");
        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        Map<String, Object> v29HistoryBefore = upgradeJdbc.queryForMap("""
                select installed_rank, version, description, type, script, checksum, success
                from flyway_schema_history
                where version = '29'
                """);
        assertThat(v29HistoryBefore.get("checksum")).isNotNull();
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from flyway_schema_history where version is not null",
                Long.class)).isEqualTo(29L);

        String v29GuardDefinition = upgradeJdbc.queryForObject("""
                select pg_get_functiondef(routine.oid)
                from pg_proc routine
                join pg_namespace namespace on namespace.oid = routine.pronamespace
                where namespace.nspname = ?
                  and routine.proname = 'guard_j7_delivery_attempt_result_insert'
                """, String.class, schema);
        assertThat(v29GuardDefinition)
                .contains("new.completed_at < attempt_record.started_at")
                .contains("new.acknowledgement_received_at < attempt_record.started_at")
                .contains("new.acknowledgement_received_at > new.completed_at");

        UUID canonicalEventId = UUID.fromString(
                "30000000-0000-4000-8000-000000000001");
        UUID exportId = UUID.fromString(
                "30000000-0000-4000-8000-000000000002");
        UUID deliveryId = UUID.fromString(
                "30000000-0000-4000-8000-000000000003");
        UUID attemptId = UUID.fromString(
                "30000000-0000-4000-8000-000000000004");
        UUID remoteImportId = UUID.fromString(
                "30000000-0000-4000-8000-000000000005");
        String dataSha256 = "a".repeat(64);
        String sourceSetSha256 = "b".repeat(64);
        String fileSha256 = "c".repeat(64);
        String idempotencyKey = "j7:" + exportId + ":sha256:" + fileSha256;
        Instant validatedAt = Instant.parse("2026-09-03T11:59:00Z");
        Instant deliveryCreatedAt = Instant.parse("2026-09-03T12:00:00Z");
        Instant attemptStartedAt = deliveryCreatedAt.plusSeconds(1);
        String validatedPath = "j7-" + canonicalEventId + "-" + exportId
                + ".validated.json";

        upgradeJdbc.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', 19999930)
                """, canonicalEventId);
        insertJ7Candidate(
                upgradeJdbc,
                canonicalEventId,
                exportId,
                dataSha256,
                sourceSetSha256,
                fileSha256);
        recordJ7DecisionIntent(
                upgradeJdbc,
                exportId,
                J7ExportStatus.HUMAN_VALIDATED,
                validatedAt,
                null,
                validatedPath,
                fileSha256,
                1024);
        assertThat(upgradeJdbc.update("""
                update export_manifest
                set export_path = ?,
                    content_sha256 = ?,
                    content_size_bytes = 1024,
                    validation_status = 'HUMAN_VALIDATED',
                    decided_at = ?,
                    decision_reason = null
                where export_uuid = ?
                """,
                validatedPath,
                fileSha256,
                Timestamp.from(validatedAt),
                exportId)).isOne();
        Long exportManifestId = upgradeJdbc.queryForObject(
                "select id from export_manifest where export_uuid = ?",
                Long.class,
                exportId);
        Long deliveryDatabaseId = upgradeJdbc.queryForObject("""
                insert into j7_delivery (
                    delivery_uuid, export_manifest_id, export_uuid,
                    file_sha256, data_sha256, file_size_bytes,
                    idempotency_key, protocol_version, current_state,
                    created_at, state_changed_at
                ) values (?, ?, ?, ?, ?, 1024, ?, '1.0', 'NOT_ATTEMPTED', ?, ?)
                returning id
                """,
                Long.class,
                deliveryId,
                exportManifestId,
                exportId,
                fileSha256,
                dataSha256,
                idempotencyKey,
                Timestamp.from(deliveryCreatedAt),
                Timestamp.from(deliveryCreatedAt));
        Long attemptDatabaseId = upgradeJdbc.queryForObject("""
                insert into j7_delivery_attempt (
                    attempt_uuid, delivery_id, attempt_number, started_at
                ) values (?, ?, 1, ?)
                returning id
                """,
                Long.class,
                attemptId,
                deliveryDatabaseId,
                Timestamp.from(attemptStartedAt));

        Map<String, Object> ledgerCountsBefore = upgradeJdbc.queryForMap("""
                select
                    (select count(*) from j7_delivery) as delivery_count,
                    (select count(*) from j7_delivery_attempt) as attempt_count,
                    (select count(*) from j7_delivery_attempt_result) as result_count
                """);
        String ledgerFingerprintSql = """
                select coalesce(string_agg(value, E'\\n' order by value), '')
                from (
                    select 'DELIVERY|' || to_jsonb(delivery)::text as value
                    from j7_delivery delivery
                    union all
                    select 'ATTEMPT|' || to_jsonb(attempt)::text as value
                    from j7_delivery_attempt attempt
                    union all
                    select 'RESULT|' || to_jsonb(result)::text as value
                    from j7_delivery_attempt_result result
                ) evidence
                """;
        String ledgerFingerprintBefore = upgradeJdbc.queryForObject(
                ledgerFingerprintSql, String.class);

        Flyway flywayV30 = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("30"))
                .load();

        assertThat(flywayV30.migrate().migrationsExecuted).isOne();
        assertThat(flywayV30.info().current().getVersion().getVersion()).isEqualTo("30");
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from flyway_schema_history where version is not null",
                Long.class)).isEqualTo(30L);
        assertThat(upgradeJdbc.queryForMap("""
                select installed_rank, version, description, type, script, checksum, success
                from flyway_schema_history
                where version = '29'
                """)).containsExactlyInAnyOrderEntriesOf(v29HistoryBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select
                    (select count(*) from j7_delivery) as delivery_count,
                    (select count(*) from j7_delivery_attempt) as attempt_count,
                    (select count(*) from j7_delivery_attempt_result) as result_count
                """)).containsExactlyInAnyOrderEntriesOf(ledgerCountsBefore);
        assertThat(upgradeJdbc.queryForObject(ledgerFingerprintSql, String.class))
                .isEqualTo(ledgerFingerprintBefore);

        String v30GuardDefinition = upgradeJdbc.queryForObject("""
                select pg_get_functiondef(routine.oid)
                from pg_proc routine
                join pg_namespace namespace on namespace.oid = routine.pronamespace
                where namespace.nspname = ?
                  and routine.proname = 'guard_j7_delivery_attempt_result_insert'
                """, String.class, schema);
        assertThat(v30GuardDefinition)
                .contains("delivery_state <> 'IN_FLIGHT'")
                .contains("latest_attempt_id is distinct from new.attempt_id")
                .contains("new.completed_at < attempt_record.started_at")
                .contains("not isfinite(new.acknowledgement_received_at)")
                .contains("timestamptz '0001-01-01 00:00:00+00'")
                .contains("timestamptz '9999-12-31 23:59:59.999999+00'")
                .doesNotContain(
                        "new.acknowledgement_received_at < attempt_record.started_at",
                        "new.acknowledgement_received_at > new.completed_at");

        String insertResultSql = """
                insert into j7_delivery_attempt_result (
                    attempt_id, terminal_state, http_status, safe_result_code,
                    acknowledgement_sha256, remote_import_id,
                    acknowledgement_received_at, completed_at
                ) values (?, 'DUPLICATE_CONFIRMED', 200, 'V30_REMOTE_CLOCK',
                          repeat('d', 64), ?, ?, ?)
                """;
        Instant remoteReceivedAt = attemptStartedAt.minusSeconds(86_400);
        Instant completedAt = attemptStartedAt.plusSeconds(1);

        assertThatThrownBy(() -> upgradeJdbc.update(
                insertResultSql,
                attemptDatabaseId,
                remoteImportId,
                Timestamp.from(remoteReceivedAt),
                Timestamp.from(completedAt)))
                .isInstanceOf(DataAccessException.class);
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from j7_delivery_attempt_result",
                Long.class)).isZero();

        assertThat(upgradeJdbc.update("""
                update j7_delivery
                set current_state = 'IN_FLIGHT', state_changed_at = ?
                where id = ?
                """, Timestamp.from(attemptStartedAt), deliveryDatabaseId)).isOne();
        assertThatThrownBy(() -> upgradeJdbc.update(
                insertResultSql,
                attemptDatabaseId,
                remoteImportId,
                Timestamp.from(remoteReceivedAt),
                Timestamp.from(attemptStartedAt.minusMillis(1))))
                .isInstanceOf(DataAccessException.class);
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from j7_delivery_attempt_result",
                Long.class)).isZero();

        for (String nonFinite : new String[] {"infinity", "-infinity"}) {
            assertThatThrownBy(() -> upgradeJdbc.update(
                    """
                    insert into j7_delivery_attempt_result (
                        attempt_id, terminal_state, http_status, safe_result_code,
                        acknowledgement_sha256, remote_import_id,
                        acknowledgement_received_at, completed_at
                    ) values (?, 'DUPLICATE_CONFIRMED', 200, 'V30_NON_FINITE_CLOCK',
                              repeat('d', 64), ?, ?::timestamptz, ?)
                    """,
                    attemptDatabaseId,
                    remoteImportId,
                    nonFinite,
                    Timestamp.from(completedAt)))
                    .isInstanceOf(DataAccessException.class)
                    .rootCause()
                    .hasMessageContaining("J7 delivery attempt result is not current");
            assertThat(upgradeJdbc.queryForObject(
                    "select count(*) from j7_delivery_attempt_result",
                    Long.class)).isZero();
        }
        for (String outsideCanonicalRange : new String[] {
                "0001-01-01 00:00:00 BC",
                "10000-01-01T00:00:00Z"
        }) {
            assertThatThrownBy(() -> upgradeJdbc.update(
                    """
                    insert into j7_delivery_attempt_result (
                        attempt_id, terminal_state, http_status, safe_result_code,
                        acknowledgement_sha256, remote_import_id,
                        acknowledgement_received_at, completed_at
                    ) values (?, 'DUPLICATE_CONFIRMED', 200, 'V30_OUT_OF_RANGE_CLOCK',
                              repeat('d', 64), ?, ?::timestamptz, ?)
                    """,
                    attemptDatabaseId,
                    remoteImportId,
                    outsideCanonicalRange,
                    Timestamp.from(completedAt)))
                    .isInstanceOf(DataAccessException.class)
                    .rootCause()
                    .hasMessageContaining("J7 delivery attempt result is not current");
            assertThat(upgradeJdbc.queryForObject(
                    "select count(*) from j7_delivery_attempt_result",
                    Long.class)).isZero();
        }

        assertThat(upgradeJdbc.update(
                insertResultSql,
                attemptDatabaseId,
                remoteImportId,
                Timestamp.from(remoteReceivedAt),
                Timestamp.from(completedAt))).isOne();
        assertThat(upgradeJdbc.update("""
                update j7_delivery
                set current_state = 'DUPLICATE_CONFIRMED', state_changed_at = ?
                where id = ?
                """, Timestamp.from(completedAt), deliveryDatabaseId)).isOne();
        assertThat(upgradeJdbc.queryForMap("""
                select delivery.current_state, result.terminal_state
                from j7_delivery delivery
                join j7_delivery_attempt attempt on attempt.delivery_id = delivery.id
                join j7_delivery_attempt_result result on result.attempt_id = attempt.id
                where delivery.id = ?
                """, deliveryDatabaseId))
                .containsEntry("current_state", "DUPLICATE_CONFIRMED")
                .containsEntry("terminal_state", "DUPLICATE_CONFIRMED");
        assertThat(upgradeJdbc.queryForObject("""
                select result.acknowledgement_received_at
                from j7_delivery_attempt_result result
                where result.attempt_id = ?
                """, java.time.OffsetDateTime.class, attemptDatabaseId).toInstant())
                .isEqualTo(remoteReceivedAt);

        assertThatThrownBy(() -> upgradeJdbc.update("""
                update j7_delivery_attempt_result
                set safe_result_code = 'FORBIDDEN_MUTATION'
                where attempt_id = ?
                """, attemptDatabaseId))
                .isInstanceOf(DataAccessException.class);
        assertThat(upgradeJdbc.queryForObject("""
                select safe_result_code
                from j7_delivery_attempt_result
                where attempt_id = ?
                """, String.class, attemptDatabaseId)).isEqualTo("V30_REMOTE_CLOCK");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistsExactJ8AttemptsOccurrencesResultsAndAppendOnlyEvidence() {
        UUID campaignId = UUID.randomUUID();
        UUID canonicalEventId = UUID.randomUUID();
        long providerEventId = 27000002L;
        Instant startedAt = Instant.parse("2026-08-29T09:00:00Z");
        jdbcTemplate.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?)
                """, canonicalEventId, providerEventId);
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                campaignId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                startedAt,
                3,
                Optional.empty()));

        String statisticsKey = "EVENT_STATISTICS|eventId=" + providerEventId;
        long statisticsUnitId = j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.EVENT_STATISTICS,
                statisticsKey,
                Optional.of(canonicalEventId),
                OptionalLong.of(providerEventId),
                startedAt.plusSeconds(1)));
        long incidentsUnitId = j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                campaignId,
                2,
                SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=" + providerEventId,
                Optional.of(canonicalEventId),
                OptionalLong.of(providerEventId),
                startedAt.plusSeconds(1)));
        long lineupsUnitId = j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                campaignId,
                3,
                SofascoreEndpointType.EVENT_LINEUPS,
                "EVENT_LINEUPS|eventId=" + providerEventId,
                Optional.of(canonicalEventId),
                OptionalLong.of(providerEventId),
                startedAt.plusSeconds(1)));

        long attemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(statisticsUnitId, startedAt.plusSeconds(2)));
        assertThatThrownBy(() -> j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(statisticsUnitId, startedAt.plusSeconds(2))))
                .isInstanceOf(DataIntegrityViolationException.class);

        RawSnapshotPersistenceResult persisted = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_STATISTICS,
                RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                statisticsKey,
                startedAt.plusSeconds(2),
                startedAt.plusSeconds(2).plusMillis(25),
                200,
                "application/json",
                Duration.ofMillis(25),
                RawPayloadEvidence.capture("{}".getBytes(StandardCharsets.UTF_8)),
                EventStatisticsV2Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                null));
        assertThat(persisted.occurrenceId()).isPresent();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into j8_benchmark_unit_result (
                    unit_id, attempt_id, resolved_at, resolution_source, outcome_type,
                    response_received, http_status, latency_ms, snapshot_id,
                    parser_version, schema_status, parser_warning_count,
                    completeness_status, completeness_score
                ) values (
                    ?, ?, ?, 'PROVIDER', 'PARSED', true, 200, 25, ?,
                    ?, 'PARSED', 1, 'COMPLETE', 100
                )
                """,
                statisticsUnitId,
                attemptId,
                Timestamp.from(startedAt.plusSeconds(3)),
                persisted.snapshotId(),
                EventStatisticsV2Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        var parsedResult = new J8BenchmarkUnitResult(
                statisticsUnitId,
                OptionalLong.of(attemptId),
                startedAt.plusSeconds(3),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                true,
                OptionalInt.of(200),
                OptionalLong.of(25L),
                OptionalLong.of(persisted.snapshotId()),
                persisted.occurrenceId(),
                Optional.of(EventStatisticsV2Parser.PARSER_VERSION),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                1,
                Optional.of(J5CompletenessStatus.COMPLETE),
                OptionalInt.of(100),
                Optional.empty());
        j8BenchmarkEvidenceStore.recordUnitResult(parsedResult);
        assertThatThrownBy(() -> j8BenchmarkEvidenceStore.recordUnitResult(parsedResult))
                .isInstanceOf(DataIntegrityViolationException.class);

        String incidentsKey = "EVENT_INCIDENTS|eventId=" + providerEventId;
        RawSnapshotPersistenceResult cachedSnapshot = snapshotStore.save(
                new RawManualCallSnapshot(
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                        incidentsKey,
                        startedAt.plusSeconds(2),
                        startedAt.plusSeconds(2).plusMillis(30),
                        200,
                        "application/json",
                        Duration.ofMillis(30),
                        RawPayloadEvidence.capture("{}".getBytes(StandardCharsets.UTF_8)),
                        EventIncidentsV14Parser.PARSER_VERSION,
                        RawSnapshotSchemaStatus.PARSED,
                        null));
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into j8_benchmark_unit_result (
                    unit_id, resolved_at, resolution_source, outcome_type,
                    response_received, snapshot_id, snapshot_occurrence_id,
                    parser_version, schema_status, parser_warning_count,
                    completeness_status, completeness_score
                ) values (
                    ?, ?, 'CACHE', 'PARSED', false, ?, ?, ?, 'PARSED', 0,
                    'EMPTY_VALID', 100
                )
                """,
                incidentsUnitId,
                Timestamp.from(startedAt.plusSeconds(3)),
                cachedSnapshot.snapshotId(),
                cachedSnapshot.occurrenceId().orElseThrow(),
                EventIncidentsV14Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        j8BenchmarkEvidenceStore.recordUnitResult(blockedJ8Result(
                incidentsUnitId, startedAt.plusSeconds(3)));
        j8BenchmarkEvidenceStore.recordUnitResult(blockedJ8Result(
                lineupsUnitId, startedAt.plusSeconds(3)));
        j8BenchmarkEvidenceStore.finishCampaign(new J8BenchmarkCampaignResult(
                campaignId,
                startedAt.plusSeconds(4),
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("SCHEMA_STOP"),
                3));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_provider_call_attempt attempt
                join j8_benchmark_unit unit on unit.id = attempt.unit_id
                where unit.campaign_id = ?
                """, Long.class, campaignId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForMap("""
                select result.resolution_source, result.outcome_type,
                       result.response_received, result.http_status, result.latency_ms,
                       result.snapshot_id, result.snapshot_occurrence_id,
                       result.schema_status, result.completeness_status,
                       result.completeness_score
                from j8_benchmark_unit_result result
                where result.unit_id = ?
                """, statisticsUnitId))
                .containsEntry("resolution_source", "PROVIDER")
                .containsEntry("outcome_type", "PARSED")
                .containsEntry("response_received", true)
                .containsEntry("http_status", 200)
                .containsEntry("latency_ms", 25L)
                .containsEntry("snapshot_id", persisted.snapshotId())
                .containsEntry(
                        "snapshot_occurrence_id",
                        persisted.occurrenceId().orElseThrow())
                .containsEntry("schema_status", "PARSED")
                .containsEntry("completeness_status", "COMPLETE")
                .containsEntry("completeness_score", 100);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_benchmark_unit_result result
                join j8_benchmark_unit unit on unit.id = result.unit_id
                where unit.campaign_id = ?
                  and result.resolution_source = 'BLOCKED'
                  and result.outcome_type = 'NOT_REACHED_AFTER_TERMINAL_FAILURE'
                """, Long.class, campaignId)).isEqualTo(2L);

        assertJ8AppendOnly("j8_benchmark_campaign", "campaign_id", campaignId);
        assertJ8AppendOnly("j8_benchmark_unit", "id", statisticsUnitId);
        assertJ8AppendOnly("j8_provider_call_attempt", "id", attemptId);
        assertJ8AppendOnly("j8_benchmark_unit_result", "unit_id", statisticsUnitId);
        assertJ8AppendOnly("j8_benchmark_campaign_result", "campaign_id", campaignId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void recordsTournamentDiscoveryManualImportWithoutAProviderAttempt() {
        UUID campaignId = UUID.randomUUID();
        LocalDate collectionDate = LocalDate.of(2026, 8, 29);
        Instant startedAt = Instant.parse("2026-08-29T10:00:00Z");
        String requestKey = "TOURNAMENT_SCHEDULED_EVENTS|date="
                + collectionDate + "|uniqueTournamentId=7";
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                campaignId,
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY,
                J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT,
                startedAt,
                1,
                Optional.of(collectionDate)));
        long unitId = j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                requestKey,
                Optional.empty(),
                OptionalLong.empty(),
                startedAt.plusSeconds(1)));
        RawSnapshotPersistenceResult imported = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT,
                requestKey,
                startedAt.plusSeconds(2),
                startedAt.plusSeconds(2),
                200,
                "application/json",
                Duration.ZERO,
                RawPayloadEvidence.capture("{}".getBytes(StandardCharsets.UTF_8)),
                TournamentScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                null));
        j8BenchmarkEvidenceStore.recordUnitResult(new J8BenchmarkUnitResult(
                unitId,
                OptionalLong.empty(),
                startedAt.plusSeconds(3),
                J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT,
                J8BenchmarkOutcomeType.PARSED,
                false,
                OptionalInt.empty(),
                OptionalLong.empty(),
                OptionalLong.of(imported.snapshotId()),
                imported.occurrenceId(),
                Optional.of(TournamentScheduledEventsV1Parser.PARSER_VERSION),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty()));
        j8BenchmarkEvidenceStore.finishCampaign(new J8BenchmarkCampaignResult(
                campaignId,
                startedAt.plusSeconds(4),
                J8BenchmarkCampaignTerminalState.COMPLETED,
                Optional.empty(),
                1));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_provider_call_attempt attempt
                join j8_benchmark_unit unit on unit.id = attempt.unit_id
                where unit.campaign_id = ?
                """, Long.class, campaignId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_benchmark_unit_result result
                join j8_benchmark_unit unit on unit.id = result.unit_id
                where unit.campaign_id = ?
                  and result.resolution_source = 'MANUAL_LOCAL_JSON_IMPORT'
                  and result.snapshot_occurrence_id is not null
                """, Long.class, campaignId)).isEqualTo(1L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void enforcesCanonicalJ8CampaignTargetsAtTheDatabaseBoundary() {
        Instant startedAt = Instant.parse("2033-03-19T09:00:00Z");
        LocalDate collectionDate = LocalDate.of(2033, 3, 19);

        UUID scheduledCampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                scheduledCampaignId,
                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                startedAt,
                25,
                Optional.of(collectionDate)));
        assertThatThrownBy(() -> insertJ8Unit(
                scheduledCampaignId,
                1,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                "SCHEDULED_EVENTS|date=2033-03-18|page=1",
                null,
                null,
                startedAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("must match campaign date and ordinal");
        assertThatThrownBy(() -> insertJ8Unit(
                scheduledCampaignId,
                1,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                "SCHEDULED_EVENTS|date=2033-03-19|page=2",
                null,
                null,
                startedAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("must match campaign date and ordinal");

        UUID tournamentCampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                tournamentCampaignId,
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                startedAt,
                1,
                Optional.of(collectionDate)));
        assertThatThrownBy(() -> insertJ8Unit(
                tournamentCampaignId,
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                "TOURNAMENT_SCHEDULED_EVENTS|date=2033-03-18|uniqueTournamentId=17",
                null,
                null,
                startedAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("must match campaign date and numeric id");
        assertThatThrownBy(() -> insertJ8Unit(
                tournamentCampaignId,
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                "TOURNAMENT_SCHEDULED_EVENTS|date=2033-03-19|uniqueTournamentId=x",
                null,
                null,
                startedAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("must match campaign date and numeric id");
        assertThatThrownBy(() -> insertJ8Unit(
                tournamentCampaignId,
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                "TOURNAMENT_SCHEDULED_EVENTS|date=2033-03-19"
                        + "|uniqueTournamentId=9223372036854775808",
                null,
                null,
                startedAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("exceeds signed bigint");

        UUID phase2CampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                phase2CampaignId,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                startedAt,
                1,
                Optional.empty()));
        assertThatThrownBy(() -> insertJ8Unit(
                phase2CampaignId,
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "event:27000001:details",
                null,
                27_000_001L,
                startedAt.plusSeconds(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertJ8Unit(
                phase2CampaignId,
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=1000000000",
                null,
                1_000_000_000L,
                startedAt.plusSeconds(1)))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID phase1CampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                phase1CampaignId,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                startedAt,
                2,
                Optional.empty()));
        assertThatThrownBy(() -> insertJ8Unit(
                phase1CampaignId,
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=16691018",
                null,
                16_691_018L,
                startedAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("outside its fixed allowlist");
        insertJ8Unit(
                phase1CampaignId,
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=16386245",
                null,
                16_386_245L,
                startedAt.plusSeconds(1));
        insertJ8Unit(
                phase1CampaignId,
                2,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=16421052",
                null,
                16_421_052L,
                startedAt.plusSeconds(1));
        assertThat(jdbcTemplate.queryForList("""
                select provider_event_id
                from j8_benchmark_unit
                where campaign_id = ?
                order by unit_ordinal
                """, Long.class, phase1CampaignId)).containsExactly(
                        16_386_245L, 16_421_052L);

        long j5EventId = 27_000_011L;
        long otherEventId = 27_000_012L;
        UUID canonicalEventId = UUID.randomUUID();
        UUID otherCanonicalEventId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?), (?, 'SOFASCORE', ?)
                """,
                canonicalEventId,
                j5EventId,
                otherCanonicalEventId,
                otherEventId);
        UUID j5CampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                j5CampaignId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                startedAt,
                3,
                Optional.empty()));
        insertJ8Unit(
                j5CampaignId,
                1,
                SofascoreEndpointType.EVENT_STATISTICS,
                "EVENT_STATISTICS|eventId=" + j5EventId,
                canonicalEventId,
                j5EventId,
                startedAt.plusSeconds(1));
        assertThatThrownBy(() -> insertJ8Unit(
                j5CampaignId,
                2,
                SofascoreEndpointType.EVENT_LINEUPS,
                "EVENT_LINEUPS|eventId=" + j5EventId,
                canonicalEventId,
                j5EventId,
                startedAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("fixed endpoint families");
        assertThatThrownBy(() -> insertJ8Unit(
                j5CampaignId,
                2,
                SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=" + otherEventId,
                otherCanonicalEventId,
                otherEventId,
                startedAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("same canonical event");
        assertThatThrownBy(() -> insertJ8Unit(
                j5CampaignId,
                2,
                SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=" + j5EventId,
                null,
                j5EventId,
                startedAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("same canonical event");
        insertJ8Unit(
                j5CampaignId,
                2,
                SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=" + j5EventId,
                canonicalEventId,
                j5EventId,
                startedAt.plusSeconds(1));
        insertJ8Unit(
                j5CampaignId,
                3,
                SofascoreEndpointType.EVENT_LINEUPS,
                "EVENT_LINEUPS|eventId=" + j5EventId,
                canonicalEventId,
                j5EventId,
                startedAt.plusSeconds(1));
        assertThat(jdbcTemplate.queryForList("""
                select unit_ordinal::text || ':' || logical_endpoint
                from j8_benchmark_unit
                where campaign_id = ?
                order by unit_ordinal
                """, String.class, j5CampaignId)).containsExactly(
                        "1:EVENT_STATISTICS",
                        "2:EVENT_INCIDENTS",
                        "3:EVENT_LINEUPS");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void enforcesExactJ8OccurrenceChronologyAndTemporalCampaignCompletion() {
        UUID campaignId = UUID.randomUUID();
        LocalDate collectionDate = LocalDate.of(2034, 4, 20);
        Instant startedAt = Instant.parse("2034-04-20T09:00:00Z");
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                campaignId,
                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                startedAt,
                25,
                Optional.of(collectionDate)));

        List<Long> unitIds = new ArrayList<>();
        for (int ordinal = 1; ordinal <= 3; ordinal++) {
            unitIds.add(j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                    campaignId,
                    ordinal,
                    SofascoreEndpointType.SCHEDULED_EVENTS,
                    "SCHEDULED_EVENTS|date=" + collectionDate + "|page=" + ordinal,
                    Optional.empty(),
                    OptionalLong.empty(),
                    startedAt.plusSeconds(1))));
        }

        long firstUnitId = unitIds.get(0);
        long firstAttemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(firstUnitId, startedAt.plusSeconds(2)));
        RawSnapshotPersistenceResult first = snapshotStore.save(j8ScheduledSnapshot(
                "SCHEDULED_EVENTS|date=" + collectionDate + "|page=1",
                startedAt.plusSeconds(2),
                "{\"events\":[]}".getBytes(StandardCharsets.UTF_8)));
        long firstOccurrenceId = first.occurrenceId().orElseThrow();

        assertThatThrownBy(() -> insertJ8Result(
                firstUnitId,
                firstAttemptId,
                startedAt.plusSeconds(4),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PROCESSING_FAILURE,
                false,
                null,
                null,
                first.snapshotId(),
                firstOccurrenceId,
                ScheduledEventsV1Parser.PARSER_VERSION,
                null,
                0))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("provider snapshots require a received response");
        assertThatThrownBy(() -> insertJ8Result(
                firstUnitId,
                firstAttemptId,
                startedAt.plusSeconds(4),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                true,
                200,
                25L,
                first.snapshotId(),
                firstOccurrenceId,
                "scheduled-events-v999",
                RawSnapshotSchemaStatus.PARSED,
                0))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("parser must match its occurrence");
        assertThatThrownBy(() -> insertJ8Result(
                firstUnitId,
                firstAttemptId,
                startedAt.plusSeconds(4),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                true,
                201,
                25L,
                first.snapshotId(),
                firstOccurrenceId,
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                0))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("match occurrence transport evidence");
        assertThatThrownBy(() -> insertJ8Result(
                firstUnitId,
                firstAttemptId,
                startedAt.plusSeconds(4),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                true,
                200,
                26L,
                first.snapshotId(),
                firstOccurrenceId,
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                0))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("match occurrence transport evidence");
        assertThatThrownBy(() -> insertJ8Result(
                firstUnitId,
                firstAttemptId,
                startedAt.plusSeconds(2).plusMillis(10),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                true,
                200,
                25L,
                first.snapshotId(),
                firstOccurrenceId,
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                0))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("occurrence chronology is inconsistent");
        assertThatThrownBy(() -> insertJ8Result(
                firstUnitId,
                firstAttemptId,
                startedAt.plusSeconds(4),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.TRANSPORT_FAILURE,
                false,
                null,
                null,
                null,
                null,
                ScheduledEventsV1Parser.PARSER_VERSION,
                null,
                0))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long baselineOccurrenceId = jdbcTemplate.queryForObject("""
                insert into provider_snapshot_occurrence (
                    snapshot_id, requested_at, received_at, http_status,
                    content_type, latency_ms, parser_version, persistence_outcome
                ) values (?, ?, ?, 200, 'application/json', 25, ?, 'BASELINE')
                returning id
                """,
                Long.class,
                first.snapshotId(),
                Timestamp.from(startedAt.plusSeconds(2)),
                Timestamp.from(startedAt.plusSeconds(2).plusMillis(25)),
                ScheduledEventsV1Parser.PARSER_VERSION);
        assertThat(baselineOccurrenceId).isNotNull();
        assertThatThrownBy(() -> insertJ8Result(
                firstUnitId,
                firstAttemptId,
                startedAt.plusSeconds(4),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                true,
                200,
                25L,
                first.snapshotId(),
                baselineOccurrenceId,
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                0))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("requires a prospective occurrence");

        Long earlyOccurrenceId = jdbcTemplate.queryForObject("""
                insert into provider_snapshot_occurrence (
                    snapshot_id, requested_at, received_at, http_status,
                    content_type, latency_ms, parser_version, persistence_outcome
                ) values (?, ?, ?, 200, 'application/json', 25, ?, 'DEDUPLICATED')
                returning id
                """,
                Long.class,
                first.snapshotId(),
                Timestamp.from(startedAt.plusSeconds(1).plusMillis(500)),
                Timestamp.from(startedAt.plusSeconds(1).plusMillis(525)),
                ScheduledEventsV1Parser.PARSER_VERSION);
        assertThat(earlyOccurrenceId).isNotNull();
        assertThatThrownBy(() -> insertJ8Result(
                firstUnitId,
                firstAttemptId,
                startedAt.plusSeconds(4),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                true,
                200,
                25L,
                first.snapshotId(),
                earlyOccurrenceId,
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                0))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("cannot predate its attempt");

        j8BenchmarkEvidenceStore.recordUnitResult(providerJ8Result(
                firstUnitId,
                firstAttemptId,
                startedAt.plusSeconds(5),
                J8BenchmarkOutcomeType.PARSED,
                OptionalInt.of(200),
                OptionalLong.of(25L),
                OptionalLong.of(first.snapshotId()),
                OptionalLong.of(firstOccurrenceId),
                Optional.of(ScheduledEventsV1Parser.PARSER_VERSION),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                null));

        long secondUnitId = unitIds.get(1);
        long secondAttemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(secondUnitId, startedAt.plusSeconds(3)));
        String secondRequestKey = "SCHEDULED_EVENTS|date=" + collectionDate + "|page=2";
        RawSnapshotPersistenceResult second = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                secondRequestKey,
                startedAt.plusSeconds(3),
                startedAt.plusSeconds(3).plusMillis(35),
                500,
                "application/json",
                Duration.ofMillis(35),
                RawPayloadEvidence.capture(
                        "{\"error\":true}".getBytes(StandardCharsets.UTF_8)),
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE"));
        long secondOccurrenceId = second.occurrenceId().orElseThrow();
        assertThatThrownBy(() -> insertJ8Result(
                secondUnitId,
                secondAttemptId,
                startedAt.plusSeconds(6),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE,
                true,
                500,
                35L,
                second.snapshotId(),
                secondOccurrenceId,
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                0))
                .isInstanceOf(DataIntegrityViolationException.class);
        j8BenchmarkEvidenceStore.recordUnitResult(providerJ8Result(
                secondUnitId,
                secondAttemptId,
                startedAt.plusSeconds(6),
                J8BenchmarkOutcomeType.HTTP_ERROR,
                OptionalInt.of(500),
                OptionalLong.of(35L),
                OptionalLong.of(second.snapshotId()),
                OptionalLong.of(secondOccurrenceId),
                Optional.of(ScheduledEventsV1Parser.PARSER_VERSION),
                Optional.empty(),
                "HTTP_500"));

        long thirdUnitId = unitIds.get(2);
        String thirdRequestKey = "SCHEDULED_EVENTS|date=" + collectionDate + "|page=3";
        RawSnapshotPersistenceResult cacheEvidence = snapshotStore.save(j8ScheduledSnapshot(
                thirdRequestKey,
                startedAt.minusSeconds(30),
                "{\"events\":[]}".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> insertJ8Result(
                thirdUnitId,
                null,
                startedAt.plusSeconds(7),
                J8BenchmarkResolutionSource.CACHE,
                J8BenchmarkOutcomeType.PROCESSING_FAILURE,
                false,
                null,
                null,
                cacheEvidence.snapshotId(),
                null,
                null,
                null,
                1))
                .isInstanceOf(DataIntegrityViolationException.class);
        j8BenchmarkEvidenceStore.recordUnitResult(blockedJ8Result(
                thirdUnitId, startedAt.plusSeconds(7)));

        assertThatThrownBy(() -> j8BenchmarkEvidenceStore.finishCampaign(
                new J8BenchmarkCampaignResult(
                        campaignId,
                        startedAt.plusSeconds(6).plusMillis(500),
                        J8BenchmarkCampaignTerminalState.FAILED,
                        Optional.of("EARLY_FINISH"),
                        3)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("cannot finish before its latest unit result");
        j8BenchmarkEvidenceStore.finishCampaign(new J8BenchmarkCampaignResult(
                campaignId,
                startedAt.plusSeconds(8),
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("EVIDENCE_COMPLETE"),
                3));

        J8BenchmarkWindow window = J8BenchmarkWindow.between(
                startedAt.minusSeconds(1),
                startedAt.plusSeconds(10));
        J8BenchmarkReadEvidence beforeFinish = j8BenchmarkReadStore.readEvidence(
                window, startedAt.plusSeconds(7).plusMillis(500));
        assertThat(beforeFinish.campaigns()).filteredOn(campaign ->
                campaign.campaignId().equals(campaignId))
                .singleElement()
                .satisfies(campaign -> {
                    assertThat(campaign.finishedAt()).isEmpty();
                    assertThat(campaign.terminalState()).isEmpty();
                });
        J8BenchmarkReadEvidence afterFinish = j8BenchmarkReadStore.readEvidence(
                window, startedAt.plusSeconds(9));
        assertThat(afterFinish.campaigns()).filteredOn(campaign ->
                campaign.campaignId().equals(campaignId))
                .singleElement()
                .satisfies(campaign -> assertThat(campaign.finishedAt())
                        .contains(startedAt.plusSeconds(8)));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistsJ8TerminalOutcomeMatrixAndCacheWithoutOrphanAttempts() {
        record TerminalCase(
                OptionalInt httpStatus,
                J8BenchmarkOutcomeType outcome,
                Optional<RawSnapshotSchemaStatus> schemaStatus,
                boolean persistsSnapshot,
                String terminalCode) {
        }

        UUID campaignId = UUID.randomUUID();
        LocalDate collectionDate = LocalDate.of(2031, 1, 17);
        Instant startedAt = Instant.parse("2031-01-17T09:00:00Z");
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                campaignId,
                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                startedAt,
                25,
                Optional.of(collectionDate)));

        List<Long> unitIds = new ArrayList<>();
        List<String> requestKeys = new ArrayList<>();
        for (int ordinal = 1; ordinal <= 10; ordinal++) {
            String requestKey = "SCHEDULED_EVENTS|date=" + collectionDate
                    + "|page=" + ordinal;
            requestKeys.add(requestKey);
            unitIds.add(j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                    campaignId,
                    ordinal,
                    SofascoreEndpointType.SCHEDULED_EVENTS,
                    requestKey,
                    Optional.empty(),
                    OptionalLong.empty(),
                    startedAt.plusSeconds(1))));
        }

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into j8_provider_call_attempt (unit_id, started_at)
                values (?, ?)
                """, Long.MAX_VALUE, Timestamp.from(startedAt.plusSeconds(2))))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("J8 benchmark unit does not exist");

        List<TerminalCase> terminalCases = List.of(
                new TerminalCase(
                        OptionalInt.of(401), J8BenchmarkOutcomeType.HTTP_REFUSED,
                        Optional.empty(), false, "HTTP_401"),
                new TerminalCase(
                        OptionalInt.of(403), J8BenchmarkOutcomeType.HTTP_REFUSED,
                        Optional.empty(), false, "HTTP_403"),
                new TerminalCase(
                        OptionalInt.of(429), J8BenchmarkOutcomeType.HTTP_REFUSED,
                        Optional.empty(), false, "HTTP_429"),
                new TerminalCase(
                        OptionalInt.of(404), J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE,
                        Optional.of(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE),
                        true, "HTTP_404"),
                new TerminalCase(
                        OptionalInt.of(503), J8BenchmarkOutcomeType.HTTP_ERROR,
                        Optional.empty(), false, "HTTP_503"),
                new TerminalCase(
                        OptionalInt.of(200), J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE,
                        Optional.of(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE),
                        true, "SCHEMA_INCOMPATIBLE"),
                new TerminalCase(
                        OptionalInt.of(200), J8BenchmarkOutcomeType.UNEXPECTED_CONTENT,
                        Optional.of(RawSnapshotSchemaStatus.UNEXPECTED_CONTENT),
                        true, "UNEXPECTED_CONTENT"),
                new TerminalCase(
                        OptionalInt.empty(), J8BenchmarkOutcomeType.TRANSPORT_FAILURE,
                        Optional.empty(), false, "TRANSPORT_FAILURE"),
                new TerminalCase(
                        OptionalInt.of(200), J8BenchmarkOutcomeType.PERSISTENCE_FAILURE,
                        Optional.empty(), false, "RAW_SNAPSHOT_PERSISTENCE_FAILURE"));

        long[] attemptIds = new long[terminalCases.size()];
        for (int index = 0; index < terminalCases.size(); index++) {
            long unitId = unitIds.get(index);
            Instant attemptAt = startedAt.plusSeconds(2L + index);
            if (index == 1) {
                long foreignAttemptId = attemptIds[0];
                assertThatThrownBy(() -> j8BenchmarkEvidenceStore.recordUnitResult(
                        providerJ8Result(
                                unitId,
                                foreignAttemptId,
                                attemptAt,
                                J8BenchmarkOutcomeType.TRANSPORT_FAILURE,
                                OptionalInt.empty(),
                                OptionalLong.empty(),
                                OptionalLong.empty(),
                                OptionalLong.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                "ORPHAN_ATTEMPT")))
                        .isInstanceOf(RuntimeException.class)
                        .hasStackTraceContaining(
                                "J8 benchmark result attempt does not belong to its unit");
            }
            attemptIds[index] = j8BenchmarkEvidenceStore.startProviderAttempt(
                    new J8ProviderCallAttempt(unitId, attemptAt));

            TerminalCase terminal = terminalCases.get(index);
            OptionalLong snapshotId = OptionalLong.empty();
            OptionalLong occurrenceId = OptionalLong.empty();
            Optional<String> parserVersion = Optional.empty();
            if (terminal.persistsSnapshot()) {
                RawSnapshotPersistenceResult raw = snapshotStore.save(
                        new RawManualCallSnapshot(
                                SofascoreEndpointType.SCHEDULED_EVENTS,
                                requestKeys.get(index),
                                attemptAt,
                                attemptAt.plusMillis(10L + index),
                                terminal.httpStatus().orElseThrow(),
                                "application/json",
                                Duration.ofMillis(10L + index),
                                RawPayloadEvidence.capture(
                                        ("{\"terminalCase\":" + index + "}")
                                                .getBytes(StandardCharsets.UTF_8)),
                                ScheduledEventsV1Parser.PARSER_VERSION,
                                terminal.schemaStatus().orElseThrow(),
                                terminal.outcome() == J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE
                                        ? null
                                        : terminal.terminalCode()));
                snapshotId = OptionalLong.of(raw.snapshotId());
                occurrenceId = raw.occurrenceId();
                parserVersion = Optional.of(ScheduledEventsV1Parser.PARSER_VERSION);
            }
            boolean responseReceived = terminal.httpStatus().isPresent();
            j8BenchmarkEvidenceStore.recordUnitResult(providerJ8Result(
                    unitId,
                    attemptIds[index],
                    attemptAt.plusSeconds(1),
                    terminal.outcome(),
                    terminal.httpStatus(),
                    responseReceived
                            ? OptionalLong.of(10L + index)
                            : OptionalLong.empty(),
                    snapshotId,
                    occurrenceId,
                    parserVersion,
                    terminal.schemaStatus(),
                    terminal.terminalCode()));
        }

        long cacheUnitId = unitIds.get(9);
        String cacheRequestKey = requestKeys.get(9);
        RawSnapshotPersistenceResult cached = snapshotStore.save(
                new RawManualCallSnapshot(
                        SofascoreEndpointType.SCHEDULED_EVENTS,
                        cacheRequestKey,
                        startedAt.minusSeconds(30),
                        startedAt.minusSeconds(30).plusMillis(5),
                        200,
                        "application/json",
                        Duration.ofMillis(5),
                        RawPayloadEvidence.capture(
                                "{\"events\":[]}".getBytes(StandardCharsets.UTF_8)),
                        ScheduledEventsV1Parser.PARSER_VERSION,
                        RawSnapshotSchemaStatus.PARSED,
                        null));
        j8BenchmarkEvidenceStore.recordUnitResult(new J8BenchmarkUnitResult(
                cacheUnitId,
                OptionalLong.empty(),
                startedAt.plusSeconds(20),
                J8BenchmarkResolutionSource.CACHE,
                J8BenchmarkOutcomeType.PARSED,
                false,
                OptionalInt.empty(),
                OptionalLong.empty(),
                OptionalLong.of(cached.snapshotId()),
                OptionalLong.empty(),
                Optional.of(ScheduledEventsV1Parser.PARSER_VERSION),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty()));
        j8BenchmarkEvidenceStore.finishCampaign(new J8BenchmarkCampaignResult(
                campaignId,
                startedAt.plusSeconds(30),
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("TERMINAL_MATRIX"),
                10));

        assertThat(jdbcTemplate.queryForList("""
                select coalesce(result.http_status::text, 'NONE')
                       || ':' || result.outcome_type
                from j8_benchmark_unit_result result
                join j8_benchmark_unit unit on unit.id = result.unit_id
                where unit.campaign_id = ?
                order by unit.unit_ordinal
                """, String.class, campaignId)).containsExactly(
                        "401:HTTP_REFUSED",
                        "403:HTTP_REFUSED",
                        "429:HTTP_REFUSED",
                        "404:ENDPOINT_UNAVAILABLE",
                        "503:HTTP_ERROR",
                        "200:SCHEMA_INCOMPATIBLE",
                        "200:UNEXPECTED_CONTENT",
                        "NONE:TRANSPORT_FAILURE",
                        "200:PERSISTENCE_FAILURE",
                        "NONE:PARSED");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_provider_call_attempt attempt
                join j8_benchmark_unit unit on unit.id = attempt.unit_id
                where unit.campaign_id = ?
                """, Long.class, campaignId)).isEqualTo(9L);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_provider_call_attempt attempt
                join j8_benchmark_unit unit on unit.id = attempt.unit_id
                left join j8_benchmark_unit_result result
                  on result.attempt_id = attempt.id
                 and result.unit_id = attempt.unit_id
                where unit.campaign_id = ?
                  and result.unit_id is null
                """, Long.class, campaignId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_benchmark_unit_result
                where unit_id = ?
                  and attempt_id is null
                  and resolution_source = 'CACHE'
                  and snapshot_id is not null
                  and snapshot_occurrence_id is null
                """, Long.class, cacheUnitId)).isEqualTo(1L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void keepsAnUnfinishedJ8AttemptVisibleWithoutInventingAResult() {
        UUID campaignId = UUID.randomUUID();
        LocalDate collectionDate = LocalDate.of(2032, 2, 18);
        Instant startedAt = Instant.parse("2032-02-18T10:00:00Z");
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                campaignId,
                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                startedAt,
                25,
                Optional.of(collectionDate)));
        long unitId = j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                "SCHEDULED_EVENTS|date=" + collectionDate + "|page=1",
                Optional.empty(),
                OptionalLong.empty(),
                startedAt.plusSeconds(1)));
        Instant attemptStartedAt = startedAt.plusSeconds(2);
        long attemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(unitId, attemptStartedAt));

        assertThatThrownBy(() -> j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(unitId, attemptStartedAt.plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);

        J8BenchmarkReadEvidence evidence = j8BenchmarkReadStore.readEvidence(
                J8BenchmarkWindow.between(
                        startedAt.minusSeconds(1),
                        startedAt.plusSeconds(10)),
                Instant.parse("2099-01-01T00:00:00Z"));
        J8BenchmarkReadEvidence.CampaignEvidence campaign = evidence.campaigns().stream()
                .filter(value -> value.campaignId().equals(campaignId))
                .findFirst()
                .orElseThrow();
        assertThat(campaign.finishedAt()).isEmpty();
        assertThat(campaign.terminalState()).isEmpty();
        assertThat(campaign.completedUnits()).isEmpty();

        J8BenchmarkReadEvidence.UnitEvidence unit = evidence.units().stream()
                .filter(value -> value.unitId() == unitId)
                .findFirst()
                .orElseThrow();
        assertThat(unit.attemptId()).isEqualTo(OptionalLong.of(attemptId));
        assertThat(unit.attemptStartedAt()).contains(attemptStartedAt);
        assertThat(unit.resolvedAt()).isEmpty();
        assertThat(unit.resolutionSource()).isEmpty();
        assertThat(unit.outcomeType()).isEmpty();
        assertThat(unit.responseReceived()).isFalse();
        assertThat(unit.snapshotId()).isEmpty();
        assertThat(unit.snapshotOccurrenceId()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_benchmark_unit_result
                where unit_id = ?
                """, Long.class, unitId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_benchmark_campaign_result
                where campaign_id = ?
                """, Long.class, campaignId)).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void correlatesDeduplicatedJ8ResponsesAndReadsEvidenceAfterRawPurge()
            throws Exception {
        LocalDate collectionDate = LocalDate.of(1800, 1, 1);
        Instant firstStartedAt = Instant.parse("1800-01-01T00:00:00Z");
        Instant secondStartedAt = firstStartedAt.plusSeconds(3600);
        String requestKey = "SCHEDULED_EVENTS|date=" + collectionDate + "|page=1";
        byte[] payload = Files.readString(
                Path.of("fixtures/scheduled-events/nominal.json"),
                StandardCharsets.UTF_8)
                .replace("\"id\": 900001", "\"id\": 88880701")
                .replace(
                        "\"startTimestamp\": 1786543200",
                        "\"startTimestamp\": 4070908800")
                .getBytes(StandardCharsets.UTF_8);
        UUID firstCampaignId = UUID.randomUUID();
        UUID secondCampaignId = UUID.randomUUID();

        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                firstCampaignId,
                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                firstStartedAt,
                25,
                Optional.of(collectionDate)));
        long firstUnitId = j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                firstCampaignId,
                1,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                requestKey,
                Optional.empty(),
                OptionalLong.empty(),
                firstStartedAt.plusSeconds(1)));
        long firstAttemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(firstUnitId, firstStartedAt.plusSeconds(2)));
        RawSnapshotPersistenceResult first = snapshotStore.save(j8ScheduledSnapshot(
                requestKey,
                firstStartedAt.plusSeconds(2),
                payload));

        RawSnapshotPersistenceResult mismatchedOccurrence = snapshotStore.save(
                j8ScheduledSnapshot(
                        "SCHEDULED_EVENTS|date=" + collectionDate + "|page=99",
                        firstStartedAt.plusSeconds(120),
                        "{\"events\":[]}".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> j8BenchmarkEvidenceStore.recordUnitResult(
                providerJ8Result(
                        firstUnitId,
                        firstAttemptId,
                        firstStartedAt.plusSeconds(3),
                        J8BenchmarkOutcomeType.PARSED,
                        OptionalInt.of(200),
                        OptionalLong.of(25),
                        OptionalLong.of(first.snapshotId()),
                        mismatchedOccurrence.occurrenceId(),
                        Optional.of(ScheduledEventsV1Parser.PARSER_VERSION),
                        Optional.of(RawSnapshotSchemaStatus.PARSED),
                        "OCCURRENCE_SNAPSHOT_MISMATCH")))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("occurrence does not belong to its snapshot");
        j8BenchmarkEvidenceStore.recordUnitResult(providerJ8Result(
                firstUnitId,
                firstAttemptId,
                firstStartedAt.plusSeconds(3),
                J8BenchmarkOutcomeType.PARSED,
                OptionalInt.of(200),
                OptionalLong.of(25),
                OptionalLong.of(first.snapshotId()),
                first.occurrenceId(),
                Optional.of(ScheduledEventsV1Parser.PARSER_VERSION),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                null));
        j8BenchmarkEvidenceStore.finishCampaign(new J8BenchmarkCampaignResult(
                firstCampaignId,
                firstStartedAt.plusSeconds(4),
                J8BenchmarkCampaignTerminalState.COMPLETED,
                Optional.empty(),
                1));
        j4SnapshotNormalizationService.normalize(first.snapshotId());

        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                secondCampaignId,
                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                secondStartedAt,
                25,
                Optional.of(collectionDate)));
        long secondUnitId = j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                secondCampaignId,
                1,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                requestKey,
                Optional.empty(),
                OptionalLong.empty(),
                secondStartedAt.plusSeconds(1)));
        long secondAttemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(secondUnitId, secondStartedAt.plusSeconds(2)));
        RawSnapshotPersistenceResult second = snapshotStore.save(j8ScheduledSnapshot(
                requestKey,
                secondStartedAt.plusSeconds(2),
                payload));
        j8BenchmarkEvidenceStore.recordUnitResult(providerJ8Result(
                secondUnitId,
                secondAttemptId,
                secondStartedAt.plusSeconds(3),
                J8BenchmarkOutcomeType.PARSED,
                OptionalInt.of(200),
                OptionalLong.of(25),
                OptionalLong.of(second.snapshotId()),
                second.occurrenceId(),
                Optional.of(ScheduledEventsV1Parser.PARSER_VERSION),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                null));
        j8BenchmarkEvidenceStore.finishCampaign(new J8BenchmarkCampaignResult(
                secondCampaignId,
                secondStartedAt.plusSeconds(4),
                J8BenchmarkCampaignTerminalState.COMPLETED,
                Optional.empty(),
                1));

        assertThat(first.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.INSERTED);
        assertThat(second.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(second.snapshotId()).isEqualTo(first.snapshotId());
        assertThat(second.occurrenceId().orElseThrow())
                .isNotEqualTo(first.occurrenceId().orElseThrow());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from provider_snapshot
                where request_key = ?
                  and payload_sha256 = ?
                """, Long.class, requestKey, first.payloadSha256())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from provider_snapshot_occurrence
                where snapshot_id = ?
                """, Long.class, first.snapshotId())).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_provider_call_attempt attempt
                join j8_benchmark_unit unit on unit.id = attempt.unit_id
                where unit.campaign_id in (?, ?)
                """, Long.class, firstCampaignId, secondCampaignId)).isEqualTo(2L);

        List<Map<String, Object>> versionLinks = jdbcTemplate.queryForList("""
                select result.snapshot_id,
                       occurrence.snapshot_id as occurrence_snapshot_id,
                       result.parser_version as result_parser_version,
                       occurrence.parser_version as occurrence_parser_version,
                       snapshot.parser_version as snapshot_parser_version,
                       occurrence.persistence_outcome
                from j8_benchmark_unit_result result
                join j8_benchmark_unit unit on unit.id = result.unit_id
                join provider_snapshot_occurrence occurrence
                  on occurrence.id = result.snapshot_occurrence_id
                join provider_snapshot snapshot on snapshot.id = result.snapshot_id
                where unit.campaign_id in (?, ?)
                order by unit.declared_at
                """, firstCampaignId, secondCampaignId);
        assertThat(versionLinks).hasSize(2).allSatisfy(link -> {
            assertThat(link)
                    .containsEntry("snapshot_id", first.snapshotId())
                    .containsEntry("occurrence_snapshot_id", first.snapshotId())
                    .containsEntry(
                            "result_parser_version",
                            ScheduledEventsV1Parser.PARSER_VERSION)
                    .containsEntry(
                            "occurrence_parser_version",
                            ScheduledEventsV1Parser.PARSER_VERSION)
                    .containsEntry(
                            "snapshot_parser_version",
                            ScheduledEventsV1Parser.PARSER_VERSION);
        });
        assertThat(versionLinks)
                .extracting(link -> link.get("persistence_outcome"))
                .containsExactly("INSERTED", "DEDUPLICATED");

        J8BenchmarkWindow window = J8BenchmarkWindow.between(
                firstStartedAt.minusSeconds(1),
                secondStartedAt.plusSeconds(5));
        Instant asOf = Instant.parse("2099-01-01T00:00:00Z");
        J8BenchmarkReadEvidence beforePurge = j8BenchmarkReadStore.readEvidence(window, asOf);
        List<J8BenchmarkReadEvidence.UnitEvidence> beforeUnits = beforePurge.units().stream()
                .filter(unit -> unit.campaignId().equals(firstCampaignId)
                        || unit.campaignId().equals(secondCampaignId))
                .toList();
        assertThat(beforeUnits).hasSize(2);
        assertThat(beforeUnits)
                .extracting(J8BenchmarkReadEvidence.UnitEvidence::deduplicatedResponse)
                .containsExactly(false, true);

        Instant retentionPreviewAt = Instant.parse("2098-01-01T00:00:00Z");
        Instant retentionCutoffAt = Instant.parse("1800-01-02T00:00:00Z");
        var preview = j6RawPayloadRetentionStore.preview(
                30,
                retentionPreviewAt,
                retentionCutoffAt,
                1);
        assertThat(preview.candidates())
                .extracting(candidate -> candidate.snapshotId())
                .containsExactly(first.snapshotId());
        J6BackupEvidence backup = new J6BackupEvidence(
                "d".repeat(64),
                "e".repeat(64),
                retentionPreviewAt,
                Math.max(first.snapshotId(), mismatchedOccurrence.snapshotId()),
                retentionPreviewAt,
                true);
        var purge = j6RawPayloadRetentionStore.purge(
                30,
                retentionCutoffAt,
                1,
                preview.planSha256(),
                backup,
                UUID.randomUUID(),
                retentionPreviewAt.plusSeconds(60));
        assertThat(purge.purgedPayloadCount()).isEqualTo(1);
        Map<String, Object> purgedSnapshot = jdbcTemplate.queryForMap("""
                select payload_raw, payload_sha256, parser_version, schema_status,
                       payload_purged_at
                from provider_snapshot
                where id = ?
                """, first.snapshotId());
        assertThat(purgedSnapshot)
                .containsEntry("payload_raw", null)
                .containsEntry("payload_sha256", first.payloadSha256())
                .containsEntry(
                        "parser_version", ScheduledEventsV1Parser.PARSER_VERSION)
                .containsEntry("schema_status", "PARSED");
        assertThat(purgedSnapshot.get("payload_purged_at")).isNotNull();

        J8BenchmarkReadEvidence afterPurge = j8BenchmarkReadStore.readEvidence(window, asOf);
        assertThat(afterPurge).isEqualTo(beforePurge);
    }

    @Test
    void excludesOccurrencesReceivedAfterAsOfFromEveryJ8ReadPopulation() {
        Instant requestedAt = Instant.parse("2199-01-01T00:00:00Z");
        Instant receivedAt = Instant.parse("2200-01-01T00:00:00Z");
        Instant asOf = Instant.parse("2199-06-01T00:00:00Z");
        Instant afterReceipt = Instant.parse("2201-01-01T00:00:00Z");
        J8BenchmarkWindow window = J8BenchmarkWindow.between(
                Instant.parse("2198-01-01T00:00:00Z"),
                afterReceipt.plusSeconds(1));

        RawSnapshotPersistenceResult direct = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_STATISTICS,
                RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                "EVENT_STATISTICS|eventId=88880999",
                requestedAt,
                receivedAt,
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(25),
                RawPayloadEvidence.capture("{}".getBytes(StandardCharsets.UTF_8)),
                EventStatisticsV2Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                null));
        RawSnapshotPersistenceResult imported = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT,
                "SCHEDULED_EVENTS|date=2199-01-01|page=1",
                requestedAt.plusSeconds(1),
                receivedAt.plusSeconds(1),
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(25),
                RawPayloadEvidence.capture("{\"events\":[]}".getBytes(
                        StandardCharsets.UTF_8)),
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                null));

        long directOccurrenceId = direct.occurrenceId().orElseThrow();
        long importedOccurrenceId = imported.occurrenceId().orElseThrow();
        J8BenchmarkReadEvidence frozenEvidence = j8BenchmarkReadStore.readEvidence(
                window, asOf);
        J8DirectObservationCohorts frozenCohorts =
                j8BenchmarkReadStore.readDirectObservationCohorts(window, asOf);

        assertThat(frozenEvidence.legacyResponses())
                .noneMatch(row -> row.occurrenceId() == directOccurrenceId);
        assertThat(frozenEvidence.manualImportOccurrenceCount()).isZero();
        assertThat(frozenCohorts.historicalObservations())
                .noneMatch(row -> row.occurrenceId() == directOccurrenceId);

        J8BenchmarkReadEvidence visibleEvidence = j8BenchmarkReadStore.readEvidence(
                window, afterReceipt);
        J8DirectObservationCohorts visibleCohorts =
                j8BenchmarkReadStore.readDirectObservationCohorts(window, afterReceipt);
        assertThat(visibleEvidence.legacyResponses())
                .anyMatch(row -> row.occurrenceId() == directOccurrenceId);
        assertThat(visibleEvidence.manualImportOccurrenceCount()).isEqualTo(1);
        assertThat(visibleCohorts.historicalObservations())
                .anyMatch(row -> row.occurrenceId() == directOccurrenceId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from provider_snapshot_occurrence where id in (?, ?)",
                Long.class,
                directOccurrenceId,
                importedOccurrenceId)).isEqualTo(2L);
    }

    @Test
    void doesNotReuseAnOlderTerminalStateAfterANewerDirectNonTerminalState() {
        UUID canonicalEventId = UUID.randomUUID();
        long providerEventId = 88_880_991L;
        Instant snapshotAt = Instant.parse("2300-01-01T00:00:00Z");
        Instant terminalAt = snapshotAt.plusSeconds(10);
        Instant firstDetailAt = snapshotAt.plusSeconds(20);
        Instant reopenedAt = snapshotAt.plusSeconds(30);
        Instant changedDetailAt = snapshotAt.plusSeconds(40);
        Instant asOf = snapshotAt.plusSeconds(50);
        String requestKey = "EVENT_DETAILS|eventId=" + providerEventId;
        RawSnapshotPersistenceResult snapshot = snapshotStore.save(
                new RawManualCallSnapshot(
                        SofascoreEndpointType.EVENT_DETAILS,
                        RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                        requestKey,
                        snapshotAt.minusMillis(10),
                        snapshotAt,
                        200,
                        "application/json; charset=utf-8",
                        Duration.ofMillis(10),
                        RawPayloadEvidence.capture(("{\"event\":{\"id\":"
                                + providerEventId
                                + ",\"marker\":\"terminal-reset-regression\"}}")
                                .getBytes(StandardCharsets.UTF_8)),
                        EventDetailsV2Parser.PARSER_VERSION,
                        RawSnapshotSchemaStatus.PARSED,
                        null));
        jdbcTemplate.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?)
                """, canonicalEventId, providerEventId);
        jdbcTemplate.update("""
                insert into canonical_event_observation (
                    canonical_event_id, source_kind, source_reference,
                    source_snapshot_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    starts_at, home_team_provider_id, home_team_name,
                    away_team_provider_id, away_team_name, status_type,
                    status_description, tournament_provider_id,
                    tournament_name, normalized_sha256
                ) values
                (
                    ?, 'PROVIDER_SNAPSHOT', 'snapshot:' || ?::text, ?, null,
                    ?, ?, ?, '2300-01-02T18:00:00Z',
                    88880911, 'Terminal Home', 88880912, 'Terminal Away',
                    'finished', 'Finished', 88880913, 'Terminal League',
                    repeat('1', 64)
                ),
                (
                    ?, 'PROVIDER_SNAPSHOT', 'snapshot:' || ?::text, ?, null,
                    ?, ?, ?, '2300-01-02T18:00:00Z',
                    88880911, 'Terminal Home', 88880912, 'Terminal Away',
                    'notstarted', 'Not started', 88880913, 'Terminal League',
                    repeat('2', 64)
                )
                """,
                canonicalEventId,
                snapshot.snapshotId(),
                snapshot.snapshotId(),
                snapshot.payloadSha256(),
                EventDetailsV2Parser.PARSER_VERSION,
                Timestamp.from(terminalAt),
                canonicalEventId,
                snapshot.snapshotId(),
                snapshot.snapshotId(),
                snapshot.payloadSha256(),
                EventDetailsV2Parser.PARSER_VERSION,
                Timestamp.from(reopenedAt));
        jdbcTemplate.update("""
                insert into event_detail_observation (
                    canonical_event_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    starts_at, home_team_provider_id, home_team_name,
                    away_team_provider_id, away_team_name, status_type,
                    status_description, tournament_provider_id,
                    tournament_name, venue_provider_id, venue_name, venue_city,
                    season_provider_id, season_name, event_round,
                    normalized_sha256, source_kind, source_reference,
                    source_snapshot_id
                ) values
                (
                    ?, null, ?, ?, ?, '2300-01-02T18:00:00Z',
                    88880911, 'Terminal Home', 88880912, 'Terminal Away',
                    'finished', 'Finished', 88880913, 'Terminal League',
                    88880914, 'Terminal Stadium', 'Paris',
                    88880915, '2299/2300', '1', repeat('3', 64),
                    'PROVIDER_SNAPSHOT', 'snapshot:' || ?::text, ?
                ),
                (
                    ?, null, ?, ?, ?, '2300-01-02T18:00:00Z',
                    88880911, 'Terminal Home corrected',
                    88880912, 'Terminal Away',
                    'notstarted', 'Not started', 88880913, 'Terminal League',
                    88880914, 'Terminal Stadium', 'Paris',
                    88880915, '2299/2300', '1', repeat('4', 64),
                    'PROVIDER_SNAPSHOT', 'snapshot:' || ?::text, ?
                )
                """,
                canonicalEventId,
                snapshot.payloadSha256(),
                EventDetailsV2Parser.PARSER_VERSION,
                Timestamp.from(firstDetailAt),
                snapshot.snapshotId(),
                snapshot.snapshotId(),
                canonicalEventId,
                snapshot.payloadSha256(),
                EventDetailsV2Parser.PARSER_VERSION,
                Timestamp.from(changedDetailAt),
                snapshot.snapshotId(),
                snapshot.snapshotId());
        jdbcTemplate.update("""
                insert into event_detail_observation (
                    canonical_event_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    starts_at, home_team_provider_id, home_team_name,
                    away_team_provider_id, away_team_name, status_type,
                    status_description, tournament_provider_id,
                    tournament_name, venue_provider_id, venue_name, venue_city,
                    season_provider_id, season_name, event_round,
                    normalized_sha256, source_kind, source_reference,
                    source_snapshot_id
                ) values (
                    ?, 'j8-interleaved-fixture', repeat('5', 64),
                    'event-details-v1', ?, '2300-01-02T18:00:00Z',
                    88880911, 'Fixture Home', 88880912, 'Fixture Away',
                    'notstarted', 'Not started', 88880913, 'Terminal League',
                    88880914, 'Terminal Stadium', 'Paris',
                    88880915, '2299/2300', '1', repeat('6', 64),
                    'SYNTHETIC_FIXTURE', 'j8-interleaved-fixture', null
                )
                """, canonicalEventId, Timestamp.from(firstDetailAt.plusSeconds(5)));
        Long firstDirectDetailId = jdbcTemplate.queryForObject("""
                select id
                from event_detail_observation
                where canonical_event_id = ?
                  and source_received_at = ?
                """, Long.class, canonicalEventId, Timestamp.from(firstDetailAt));
        Long changedDetailId = jdbcTemplate.queryForObject("""
                select id
                from event_detail_observation
                where canonical_event_id = ?
                  and source_received_at = ?
                """, Long.class, canonicalEventId, Timestamp.from(changedDetailAt));
        Long reopenedStateId = jdbcTemplate.queryForObject("""
                select id
                from canonical_event_observation
                where canonical_event_id = ?
                  and source_received_at = ?
                """, Long.class, canonicalEventId, Timestamp.from(reopenedAt));
        J8BenchmarkWindow window = J8BenchmarkWindow.between(
                reopenedAt.plusMillis(1), asOf);

        assertThat(j8BenchmarkReadStore.readLateChanges(window, asOf))
                .filteredOn(change -> change.canonicalEventId().equals(canonicalEventId))
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.stream())
                            .isEqualTo(J6HistoryStream.EVENT_DETAILS);
                    assertThat(change.observationId()).isEqualTo(changedDetailId);
                    assertThat(change.previousObservationId())
                            .isEqualTo(firstDirectDetailId);
                    assertThat(change.previousDirectStateObservationId())
                            .hasValue(reopenedStateId);
                    assertThat(change.previousDirectStateStatus())
                            .contains("notstarted");
                    assertThat(change.previousDirectStateAt()).contains(reopenedAt);
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void latestDeduplicatedIncompatibleJ8OccurrenceInvalidatesCurrentDossierEvidence() {
        UUID canonicalEventId = UUID.randomUUID();
        long providerEventId = 88_880_801L;
        Instant detailStartedAt = Instant.parse("1812-01-01T00:00:00Z");
        Instant firstStatisticsStartedAt = detailStartedAt.plusSeconds(100);
        Instant incompatibleStatisticsStartedAt = detailStartedAt.plusSeconds(200);
        Instant windowEnd = detailStartedAt.plusSeconds(300);
        String detailKey = "EVENT_DETAILS|eventId=" + providerEventId;
        String statisticsKey = "EVENT_STATISTICS|eventId=" + providerEventId;
        jdbcTemplate.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?)
                """, canonicalEventId, providerEventId);

        UUID detailCampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                detailCampaignId,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                detailStartedAt,
                1,
                Optional.empty()));
        long detailUnitId = j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                detailCampaignId,
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                detailKey,
                Optional.of(canonicalEventId),
                OptionalLong.of(providerEventId),
                detailStartedAt.plusSeconds(1)));
        long detailAttemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(detailUnitId, detailStartedAt.plusSeconds(2)));
        Instant detailReceivedAt = detailStartedAt.plusSeconds(2).plusMillis(20);
        RawSnapshotPersistenceResult detailSnapshot = snapshotStore.save(
                new RawManualCallSnapshot(
                        SofascoreEndpointType.EVENT_DETAILS,
                        RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                        detailKey,
                        detailStartedAt.plusSeconds(2),
                        detailReceivedAt,
                        200,
                        "application/json",
                        Duration.ofMillis(20),
                        RawPayloadEvidence.capture(
                                ("{\"event\":{\"id\":" + providerEventId + "}}")
                                        .getBytes(StandardCharsets.UTF_8)),
                        EventDetailsV2Parser.PARSER_VERSION,
                        RawSnapshotSchemaStatus.PARSED,
                        null));
        j8BenchmarkEvidenceStore.recordUnitResult(providerJ8Result(
                detailUnitId,
                detailAttemptId,
                detailStartedAt.plusSeconds(3),
                J8BenchmarkOutcomeType.PARSED,
                OptionalInt.of(200),
                OptionalLong.of(20),
                OptionalLong.of(detailSnapshot.snapshotId()),
                detailSnapshot.occurrenceId(),
                Optional.of(EventDetailsV2Parser.PARSER_VERSION),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                null));
        j8BenchmarkEvidenceStore.finishCampaign(new J8BenchmarkCampaignResult(
                detailCampaignId,
                detailStartedAt.plusSeconds(4),
                J8BenchmarkCampaignTerminalState.COMPLETED,
                Optional.empty(),
                1));
        jdbcTemplate.update("""
                insert into canonical_event_observation (
                    canonical_event_id, source_kind, source_reference,
                    source_snapshot_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    starts_at, home_team_provider_id, home_team_name,
                    away_team_provider_id, away_team_name, status_type,
                    status_description, tournament_provider_id,
                    tournament_name, normalized_sha256
                ) values (
                    ?, 'PROVIDER_SNAPSHOT', 'snapshot:' || ?::text, ?, null,
                    ?, ?, ?, '1812-01-02T18:00:00Z',
                    88880101, 'J8 Current Home', 88880102, 'J8 Current Away',
                    'ended', 'Ended', 88880103, 'J8 Current League', repeat('a', 64)
                )
                """,
                canonicalEventId,
                detailSnapshot.snapshotId(),
                detailSnapshot.snapshotId(),
                detailSnapshot.payloadSha256(),
                EventDetailsV2Parser.PARSER_VERSION,
                Timestamp.from(detailReceivedAt));
        jdbcTemplate.update("""
                insert into event_detail_observation (
                    canonical_event_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    starts_at, home_team_provider_id, home_team_name,
                    away_team_provider_id, away_team_name, status_type,
                    status_description, tournament_provider_id,
                    tournament_name, venue_provider_id, venue_name, venue_city,
                    season_provider_id, season_name, event_round,
                    normalized_sha256, source_kind, source_reference,
                    source_snapshot_id
                ) values (
                    ?, null, ?, ?, ?, '1812-01-02T18:00:00Z',
                    88880101, 'J8 Current Home', 88880102, 'J8 Current Away',
                    'ended', 'Ended', 88880103, 'J8 Current League',
                    88880104, 'J8 Current Stadium', 'Paris',
                    88880105, '1811/1812', '1', repeat('b', 64),
                    'PROVIDER_SNAPSHOT', 'snapshot:' || ?::text, ?
                )
                """,
                canonicalEventId,
                detailSnapshot.payloadSha256(),
                EventDetailsV2Parser.PARSER_VERSION,
                Timestamp.from(detailReceivedAt),
                detailSnapshot.snapshotId(),
                detailSnapshot.snapshotId());

        byte[] statisticsPayload = "{\"statistics\":[]}".getBytes(StandardCharsets.UTF_8);
        UUID firstStatisticsCampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                firstStatisticsCampaignId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                firstStatisticsStartedAt,
                3,
                Optional.empty()));
        long firstStatisticsUnitId = j8BenchmarkEvidenceStore.declareUnit(
                new J8BenchmarkUnit(
                        firstStatisticsCampaignId,
                        1,
                        SofascoreEndpointType.EVENT_STATISTICS,
                        statisticsKey,
                        Optional.of(canonicalEventId),
                        OptionalLong.of(providerEventId),
                        firstStatisticsStartedAt.plusSeconds(1)));
        long firstStatisticsAttemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(
                        firstStatisticsUnitId,
                        firstStatisticsStartedAt.plusSeconds(2)));
        Instant firstStatisticsReceivedAt = firstStatisticsStartedAt
                .plusSeconds(2).plusMillis(25);
        RawSnapshotPersistenceResult firstStatisticsSnapshot = snapshotStore.save(
                new RawManualCallSnapshot(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                        statisticsKey,
                        firstStatisticsStartedAt.plusSeconds(2),
                        firstStatisticsReceivedAt,
                        200,
                        "application/json",
                        Duration.ofMillis(25),
                        RawPayloadEvidence.capture(statisticsPayload),
                        EventStatisticsV2Parser.PARSER_VERSION,
                        RawSnapshotSchemaStatus.PARSED,
                        null));
        j8BenchmarkEvidenceStore.recordUnitResult(new J8BenchmarkUnitResult(
                firstStatisticsUnitId,
                OptionalLong.of(firstStatisticsAttemptId),
                firstStatisticsStartedAt.plusSeconds(3),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                true,
                OptionalInt.of(200),
                OptionalLong.of(25),
                OptionalLong.of(firstStatisticsSnapshot.snapshotId()),
                firstStatisticsSnapshot.occurrenceId(),
                Optional.of(EventStatisticsV2Parser.PARSER_VERSION),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                0,
                Optional.of(J5CompletenessStatus.COMPLETE),
                OptionalInt.of(100),
                Optional.empty()));
        j8BenchmarkEvidenceStore.finishCampaign(new J8BenchmarkCampaignResult(
                firstStatisticsCampaignId,
                firstStatisticsStartedAt.plusSeconds(4),
                J8BenchmarkCampaignTerminalState.COMPLETED,
                Optional.empty(),
                1));
        jdbcTemplate.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind,
                    source_reference, source_snapshot_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    completeness_status, completeness_score, present_signals,
                    expected_signals, missing_paths_json, lineups_confirmed,
                    normalized_sha256
                ) values (
                    ?, 'EVENT_STATISTICS', 'PROVIDER_SNAPSHOT',
                    'snapshot:' || ?::text, ?, null, ?, ?, ?,
                    'COMPLETE', 100, 2, 2, '[]'::jsonb, null, repeat('c', 64)
                )
                """,
                canonicalEventId,
                firstStatisticsSnapshot.snapshotId(),
                firstStatisticsSnapshot.snapshotId(),
                firstStatisticsSnapshot.payloadSha256(),
                EventStatisticsV2Parser.PARSER_VERSION,
                Timestamp.from(firstStatisticsReceivedAt));

        UUID incompatibleCampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                incompatibleCampaignId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                incompatibleStatisticsStartedAt,
                3,
                Optional.empty()));
        long incompatibleUnitId = j8BenchmarkEvidenceStore.declareUnit(
                new J8BenchmarkUnit(
                        incompatibleCampaignId,
                        1,
                        SofascoreEndpointType.EVENT_STATISTICS,
                        statisticsKey,
                        Optional.of(canonicalEventId),
                        OptionalLong.of(providerEventId),
                        incompatibleStatisticsStartedAt.plusSeconds(1)));
        long incompatibleAttemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(
                        incompatibleUnitId,
                        incompatibleStatisticsStartedAt.plusSeconds(2)));
        RawSnapshotPersistenceResult incompatibleSnapshot = snapshotStore.save(
                new RawManualCallSnapshot(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                        statisticsKey,
                        incompatibleStatisticsStartedAt.plusSeconds(2),
                        incompatibleStatisticsStartedAt.plusSeconds(2).plusMillis(25),
                        200,
                        "application/json",
                        Duration.ofMillis(25),
                        RawPayloadEvidence.capture(statisticsPayload),
                        EventStatisticsV2Parser.PARSER_VERSION,
                        RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                        "SCHEMA_INCOMPATIBLE"));
        assertThat(incompatibleSnapshot.outcome())
                .isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(incompatibleSnapshot.snapshotId())
                .isEqualTo(firstStatisticsSnapshot.snapshotId());
        j8BenchmarkEvidenceStore.recordUnitResult(providerJ8Result(
                incompatibleUnitId,
                incompatibleAttemptId,
                incompatibleStatisticsStartedAt.plusSeconds(3),
                J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE,
                OptionalInt.of(200),
                OptionalLong.of(25),
                OptionalLong.of(incompatibleSnapshot.snapshotId()),
                incompatibleSnapshot.occurrenceId(),
                Optional.of(EventStatisticsV2Parser.PARSER_VERSION),
                Optional.of(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE),
                "SCHEMA_INCOMPATIBLE"));
        j8BenchmarkEvidenceStore.finishCampaign(new J8BenchmarkCampaignResult(
                incompatibleCampaignId,
                incompatibleStatisticsStartedAt.plusSeconds(4),
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("SCHEMA_INCOMPATIBLE"),
                1));

        UUID orphanCanonicalEventId = UUID.randomUUID();
        long orphanProviderEventId = providerEventId + 1;
        Instant orphanStartedAt = detailStartedAt.plusSeconds(250);
        String orphanStatisticsKey =
                "EVENT_STATISTICS|eventId=" + orphanProviderEventId;
        jdbcTemplate.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?)
                """, orphanCanonicalEventId, orphanProviderEventId);
        UUID orphanCampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                orphanCampaignId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                orphanStartedAt,
                3,
                Optional.empty()));
        long orphanUnitId = j8BenchmarkEvidenceStore.declareUnit(new J8BenchmarkUnit(
                orphanCampaignId,
                1,
                SofascoreEndpointType.EVENT_STATISTICS,
                orphanStatisticsKey,
                Optional.of(orphanCanonicalEventId),
                OptionalLong.of(orphanProviderEventId),
                orphanStartedAt.plusSeconds(1)));
        long orphanAttemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(orphanUnitId, orphanStartedAt.plusSeconds(2)));
        RawSnapshotPersistenceResult orphanSnapshot = snapshotStore.save(
                new RawManualCallSnapshot(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                        orphanStatisticsKey,
                        orphanStartedAt.plusSeconds(2),
                        orphanStartedAt.plusSeconds(2).plusMillis(15),
                        200,
                        "application/json",
                        Duration.ofMillis(15),
                        RawPayloadEvidence.capture(
                                "{\"statistics\":[]}".getBytes(StandardCharsets.UTF_8)),
                        EventStatisticsV2Parser.PARSER_VERSION,
                        RawSnapshotSchemaStatus.RAW_ONLY,
                        null));
        long orphanOccurrenceId = orphanSnapshot.occurrenceId().orElseThrow();
        jdbcTemplate.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind,
                    source_reference, source_snapshot_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    completeness_status, completeness_score, present_signals,
                    expected_signals, missing_paths_json, lineups_confirmed,
                    normalized_sha256
                ) values (
                    ?, 'EVENT_STATISTICS', 'PROVIDER_SNAPSHOT',
                    'snapshot:' || ?::text, ?, null, ?, ?, ?,
                    'COMPLETE', 100, 2, 2, '[]'::jsonb, null, repeat('d', 64)
                )
                """,
                orphanCanonicalEventId,
                orphanSnapshot.snapshotId(),
                orphanSnapshot.snapshotId(),
                orphanSnapshot.payloadSha256(),
                EventStatisticsV2Parser.PARSER_VERSION,
                Timestamp.from(orphanStartedAt.plusSeconds(2).plusMillis(15)));

        J8BenchmarkWindow window = J8BenchmarkWindow.between(
                detailStartedAt.minusSeconds(1), windowEnd);
        Instant asOf = Instant.now();
        J8BenchmarkReadEvidence readEvidence =
                j8BenchmarkReadStore.readEvidence(window, asOf);
        assertThat(readEvidence.units()).filteredOn(unit -> unit.unitId() == orphanUnitId)
                .singleElement()
                .satisfies(unit -> {
                    assertThat(unit.attemptId()).hasValue(orphanAttemptId);
                    assertThat(unit.outcomeType()).isEmpty();
                    assertThat(unit.snapshotOccurrenceId()).isEmpty();
                });
        assertThat(readEvidence.legacyResponses())
                .noneMatch(response -> response.occurrenceId() == orphanOccurrenceId);
        J8DirectObservationCohorts cohorts =
                j8BenchmarkReadStore.readDirectObservationCohorts(window, asOf);
        assertThat(cohorts.historicalObservations())
                .noneMatch(observation -> observation.occurrenceId() == orphanOccurrenceId);
        assertThat(cohorts.observations())
                .filteredOn(cohort -> cohort.unitId() == orphanUnitId)
                .singleElement()
                .satisfies(cohort -> assertThat(cohort.currentStatistics())
                        .isEqualTo(
                                J8DirectObservationCohorts.DirectComponentState.INCOMPATIBLE));
        List<J8DirectObservationCohorts.ObservationCohort> eventCohorts =
                cohorts.observations().stream()
                        .filter(cohort -> cohort.providerEventId() == providerEventId)
                        .toList();
        assertThat(eventCohorts).hasSize(3);
        assertThat(eventCohorts)
                .allSatisfy(cohort -> assertThat(cohort.currentStatistics())
                        .isEqualTo(
                                J8DirectObservationCohorts.DirectComponentState.INCOMPATIBLE));
        assertThat(eventCohorts.stream()
                .filter(cohort -> cohort.unitId() == detailUnitId)
                .findFirst().orElseThrow().currentEventDetails())
                .isEqualTo(J8DirectObservationCohorts.DirectComponentState.AVAILABLE);
        assertThat(eventCohorts.stream()
                .filter(cohort -> cohort.unitId() == incompatibleUnitId)
                .findFirst().orElseThrow().normalizedObservationPresent()).isFalse();
        assertThat(j8BenchmarkReadStore.readLateChanges(window, asOf))
                .noneSatisfy(change -> assertThat(change.canonicalEventId())
                        .isEqualTo(canonicalEventId));

        J8BenchmarkReport firstReport = j8BenchmarkService.load(window, asOf);
        J8BenchmarkReport secondReport = j8BenchmarkService.load(window, asOf);
        assertThat(firstReport.populationHash()).isEqualTo(secondReport.populationHash());
        assertThat(firstReport.callSummary().incompleteAttemptCount()).isOne();
        assertThat(firstReport.dossierEfficiency().exploitableEventCount()).isZero();
        assertThat(secondReport.dossierEfficiency()).isEqualTo(firstReport.dossierEfficiency());

        UUID duplicateEvidenceCampaignId = UUID.randomUUID();
        j8BenchmarkEvidenceStore.startCampaign(new J8BenchmarkCampaign(
                duplicateEvidenceCampaignId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                incompatibleStatisticsStartedAt,
                3,
                Optional.empty()));
        long duplicateEvidenceUnitId = j8BenchmarkEvidenceStore.declareUnit(
                new J8BenchmarkUnit(
                        duplicateEvidenceCampaignId,
                        1,
                        SofascoreEndpointType.EVENT_STATISTICS,
                        statisticsKey,
                        Optional.of(canonicalEventId),
                        OptionalLong.of(providerEventId),
                        incompatibleStatisticsStartedAt.plusSeconds(1)));
        long duplicateEvidenceAttemptId = j8BenchmarkEvidenceStore.startProviderAttempt(
                new J8ProviderCallAttempt(
                        duplicateEvidenceUnitId,
                        incompatibleStatisticsStartedAt.plusSeconds(2)));
        assertThatThrownBy(() -> j8BenchmarkEvidenceStore.recordUnitResult(
                providerJ8Result(
                        duplicateEvidenceUnitId,
                        duplicateEvidenceAttemptId,
                        incompatibleStatisticsStartedAt.plusSeconds(3),
                        J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE,
                        OptionalInt.of(200),
                        OptionalLong.of(25),
                        OptionalLong.of(incompatibleSnapshot.snapshotId()),
                        incompatibleSnapshot.occurrenceId(),
                        Optional.of(EventStatisticsV2Parser.PARSER_VERSION),
                        Optional.of(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE),
                        "DUPLICATE_OCCURRENCE")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(
                        "uq_j8_benchmark_unit_result_snapshot_occurrence");
    }

    @Test
    void persistsTournamentDiscoveryThenOpensJ5WithoutAnyEventDetailsObservation() {
        LocalDate date = LocalDate.of(2026, 8, 18);
        Instant requestedAt = Instant.parse("2026-08-20T08:00:00Z");
        Instant receivedAt = requestedAt.plusMillis(25);
        TournamentScheduledEventsProviderRequest request =
                new TournamentScheduledEventsProviderRequest(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                        date,
                        7);
        RawPayloadEvidence payload = RawPayloadEvidence.capture("""
                {
                  "events": [{
                    "id": 981001,
                    "startTimestamp": 1787079600,
                    "homeTeam": {"id": 181, "name": "J3 Home"},
                    "awayTeam": {"id": 182, "name": "J3 Away"},
                    "status": {"type": "notstarted"},
                    "tournament": {
                      "id": 119880,
                      "name": "UEFA Champions League, Playoff Round",
                      "uniqueTournament": {
                        "id": 7,
                        "name": "UEFA Champions League"
                      }
                    }
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8));
        TournamentScheduledEventsTransportResponse response =
                new TournamentScheduledEventsTransportResponse(
                        request.requestKey(),
                        requestedAt,
                        receivedAt,
                        200,
                        "application/json",
                        Duration.ofMillis(25),
                        payload);
        RawSnapshotPersistenceResult raw = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                request.requestKey(),
                requestedAt,
                receivedAt,
                200,
                "application/json",
                Duration.ofMillis(25),
                payload,
                TournamentScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        snapshotStore.classify(
                raw.snapshotId(),
                RawSnapshotSchemaStatus.PARSED,
                null);
        tournamentScheduledEventsCache.recordParsed(
                request,
                response,
                raw,
                TournamentScheduledEventsV1Parser.PARSER_VERSION);

        var cached = tournamentScheduledEventsCache.findFreshParsed(
                request,
                receivedAt.plusSeconds(1),
                Duration.ofMinutes(10),
                TournamentScheduledEventsV1Parser.PARSER_VERSION).orElseThrow();
        var parsed = new TournamentScheduledEventsV1Parser().parse(
                cached.payload().bytes(),
                cached.contentType(),
                new TournamentScheduledEventsParseEvidence(
                        "snapshot:" + cached.snapshotId(),
                        cached.payload().sha256(),
                        Optional.empty(),
                        cached.receivedAt(),
                        TournamentScheduledEventsV1Parser.PARSER_VERSION));
        var option = new J3TournamentCatalogOption(
                119_880,
                "UEFA Champions League, Playoff Round",
                "Europe",
                7,
                "UEFA Champions League",
                Map.of(7200, 1),
                List.of(41L));
        var projection = tournamentProjectionService.project(
                date,
                option,
                parsed.candidates().orElseThrow());
        var persisted = tournamentCanonicalPersistenceService.persist(
                projection,
                cached.snapshotId(),
                cached.payload().sha256(),
                cached.receivedAt());
        UUID canonicalEventId = persisted.events().getFirst().canonicalEventId();

        assertThat(persisted.insertedObservations()).isEqualTo(1);
        assertThat(eventDetailsStore.findLatest(canonicalEventId)).isEmpty();
        assertThat(j5EventDataQueryService.find(canonicalEventId, "Europe/Paris"))
                .isPresent()
                .get()
                .satisfies(page -> {
                    assertThat(page.current().event().identity().providerEventId())
                            .isEqualTo(981001);
                    assertThat(page.data().statistics()).isEmpty();
                    assertThat(page.data().incidents()).isEmpty();
                    assertThat(page.data().lineups()).isEmpty();
                });
    }

    @Test
    void upgradesAStoredJ5Http404FromTransportErrorToEndpointUnavailable() {
        String schema = "upgrade_v8_to_v9";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV8 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8"))
                .load();

        assertThat(flywayV8.migrate().migrationsExecuted).isEqualTo(8);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into provider_snapshot (
                    provider,
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
                    schema_status,
                    error_code
                ) values (
                    'SOFASCORE',
                    'EVENT_STATISTICS',
                    'EVENT_STATISTICS|eventId=16412917',
                    '2026-08-15T19:50:46Z',
                    '2026-08-15T19:50:47Z',
                    404,
                    'application/json',
                    1000,
                    decode('7b7d', 'hex'),
                    2,
                    repeat('a', 64),
                    'event-statistics-v2',
                    'TRANSPORT_ERROR',
                    'HTTP_STATUS_404'
                )
                """);
        Map<String, Object> evidenceBeforeMigration = upgradeJdbc.queryForMap("""
                select
                    id,
                    encode(payload_raw, 'hex') as payload_hex,
                    payload_sha256,
                    received_at
                from provider_snapshot
                where request_key = 'EVENT_STATISTICS|eventId=16412917'
                """);

        Flyway flywayV9 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("9"))
                .load();

        assertThat(flywayV9.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV9.info().current().getVersion().getVersion()).isEqualTo("9");
        Map<String, Object> evidenceAfterMigration = upgradeJdbc.queryForMap("""
                select
                    id,
                    schema_status,
                    error_code,
                    encode(payload_raw, 'hex') as payload_hex,
                    payload_sha256,
                    received_at
                from provider_snapshot
                where request_key = 'EVENT_STATISTICS|eventId=16412917'
                """);
        assertThat(evidenceAfterMigration)
                .containsEntry("schema_status", "ENDPOINT_UNAVAILABLE")
                .containsEntry("error_code", null);
        assertThat(evidenceAfterMigration)
                .containsAllEntriesOf(evidenceBeforeMigration);
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from j5_event_data_observation",
                Long.class)).isZero();
    }

    @Test
    void upgradesV9ToTheVersionedIncidentPeriodMarkerParserWithoutRewritingData() {
        String schema = "upgrade_v9_to_v10";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV9 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("9"))
                .load();

        assertThat(flywayV9.migrate().migrationsExecuted).isEqualTo(9);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        String constraintBefore = upgradeJdbc.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where connamespace = ?::regnamespace
                  and conname = 'ck_j5_event_data_parser'
                """,
                String.class,
                schema);
        assertThat(constraintBefore)
                .contains("event-incidents-v2")
                .doesNotContain("event-incidents-v3");

        Flyway flywayV10 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("10"))
                .load();

        assertThat(flywayV10.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV10.info().current().getVersion().getVersion()).isEqualTo("10");
        String constraintAfter = upgradeJdbc.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where connamespace = ?::regnamespace
                  and conname = 'ck_j5_event_data_parser'
                """,
                String.class,
                schema);
        assertThat(constraintAfter)
                .contains("event-incidents-v1")
                .contains("event-incidents-v2")
                .contains("event-incidents-v3")
                .contains("event-incidents-unavailable-v1");
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from j5_event_data_observation",
                Long.class)).isZero();
    }

    @Test
    void upgradesV10WithVersionedSubstitutionPlayersAndNoHistoricalRewrite() {
        String schema = "upgrade_v10_to_v11";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV10 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("10"))
                .load();

        assertThat(flywayV10.migrate().migrationsExecuted).isEqualTo(10);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        String constraintBefore = upgradeJdbc.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where connamespace = ?::regnamespace
                  and conname = 'ck_j5_event_data_parser'
                """,
                String.class,
                schema);
        assertThat(constraintBefore)
                .contains("event-incidents-v3")
                .doesNotContain("event-incidents-v4");

        Flyway flywayV11 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("11"))
                .load();

        assertThat(flywayV11.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV11.info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(upgradeJdbc.queryForList(
                """
                select column_name
                from information_schema.columns
                where table_schema = ?
                  and table_name = 'j5_event_incident'
                  and column_name in (
                      'player_in_provider_id',
                      'player_in_name',
                      'player_out_provider_id',
                      'player_out_name'
                  )
                order by column_name
                """,
                String.class,
                schema)).containsExactly(
                        "player_in_name",
                        "player_in_provider_id",
                        "player_out_name",
                        "player_out_provider_id");
        String constraintAfter = upgradeJdbc.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where connamespace = ?::regnamespace
                  and conname = 'ck_j5_event_data_parser'
                """,
                String.class,
                schema);
        assertThat(constraintAfter)
                .contains("event-incidents-v3")
                .contains("event-incidents-v4");
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from j5_event_data_observation",
                Long.class)).isZero();
    }

    @Test
    void upgradesV11WithBenchIncidentDetailsAndNoHistoricalRewrite() {
        String schema = "upgrade_v11_to_v12";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV11 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("11"))
                .load();

        assertThat(flywayV11.migrate().migrationsExecuted).isEqualTo(11);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        String parserConstraintBefore = upgradeJdbc.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where connamespace = ?::regnamespace
                  and conname = 'ck_j5_event_data_parser'
                """,
                String.class,
                schema);
        assertThat(upgradeJdbc.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = ?
                  and table_name = 'j5_event_incident'
                  and column_name in ('incident_class', 'reason')
                """,
                Long.class,
                schema)).isZero();
        assertThat(parserConstraintBefore)
                .contains("event-incidents-v4")
                .doesNotContain("event-incidents-v5");

        Flyway flywayV12 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("12"))
                .load();

        assertThat(flywayV12.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV12.info().current().getVersion().getVersion()).isEqualTo("12");
        assertThat(upgradeJdbc.queryForList(
                """
                select column_name
                from information_schema.columns
                where table_schema = ?
                  and table_name = 'j5_event_incident'
                  and column_name in ('incident_class', 'reason')
                order by column_name
                """,
                String.class,
                schema)).containsExactly("incident_class", "reason");
        String parserConstraintAfter = upgradeJdbc.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where connamespace = ?::regnamespace
                  and conname = 'ck_j5_event_data_parser'
                """,
                String.class,
                schema);
        assertThat(parserConstraintAfter)
                .contains("event-incidents-v4")
                .contains("event-incidents-v5");
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from j5_event_data_observation",
                Long.class)).isZero();
    }

    @Test
    void upgradesV12WithReviewedFootballIncidentRulesWithoutRewritingHistory() {
        String schema = "upgrade_v12_to_v13";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV12 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("12"))
                .load();

        assertThat(flywayV12.migrate().migrationsExecuted).isEqualTo(12);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id,
                    provider,
                    provider_event_id,
                    created_at
                ) values (
                    '13131313-1313-1313-1313-131313131313',
                    'SOFASCORE',
                    16483632,
                    '2026-08-16T09:16:58Z'
                )
                """);
        Long historicalObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id,
                    endpoint_type,
                    source_kind,
                    source_reference,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    completeness_status,
                    completeness_score,
                    present_signals,
                    expected_signals,
                    missing_paths_json,
                    normalized_sha256
                ) values (
                    '13131313-1313-1313-1313-131313131313',
                    'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE',
                    'historical-v5-incident',
                    'historical-v5-incident',
                    repeat('a', 64),
                    'event-incidents-v5',
                    '2026-08-16T09:17:00Z',
                    'COMPLETE',
                    100,
                    1,
                    1,
                    '[]'::jsonb,
                    repeat('b', 64)
                )
                returning id
                """, Long.class);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id,
                    incident_order,
                    incident_type,
                    minute,
                    is_home,
                    player_name,
                    incident_class,
                    reason
                ) values (?, 0, 'card', 58, false, 'Kerem Aktürkoğlu', 'yellow', 'Argument')
                """, historicalObservationId);
        Map<String, Object> evidenceBeforeMigration = upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId);
        String parserConstraintBefore = upgradeJdbc.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where connamespace = ?::regnamespace
                  and conname = 'ck_j5_event_data_parser'
                """,
                String.class,
                schema);
        assertThat(parserConstraintBefore)
                .contains("event-incidents-v5")
                .doesNotContain("event-incidents-v6");

        Flyway flywayV13 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("13"))
                .load();

        assertThat(flywayV13.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV13.info().current().getVersion().getVersion()).isEqualTo("13");
        assertThat(upgradeJdbc.queryForList(
                """
                select column_name
                from information_schema.columns
                where table_schema = ?
                  and table_name = 'j5_event_incident'
                  and column_name in (
                      'period_text',
                      'injury',
                      'assist_provider_id',
                      'assist_name',
                      'goal_origin',
                      'injury_time_length',
                      'var_confirmed',
                      'rescinded',
                      'description',
                      'shootout_sequence'
                  )
                order by column_name
                """,
                String.class,
                schema)).containsExactly(
                        "assist_name",
                        "assist_provider_id",
                        "description",
                        "goal_origin",
                        "injury",
                        "injury_time_length",
                        "period_text",
                        "rescinded",
                        "shootout_sequence",
                        "var_confirmed");
        String parserConstraintAfter = upgradeJdbc.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where connamespace = ?::regnamespace
                  and conname = 'ck_j5_event_data_parser'
                """,
                String.class,
                schema);
        assertThat(parserConstraintAfter)
                .contains("event-incidents-v5")
                .contains("event-incidents-v6");
        assertThat(upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId)).containsAllEntriesOf(evidenceBeforeMigration);
        assertThat(upgradeJdbc.queryForMap("""
                select period_text, injury, assist_provider_id, assist_name,
                       goal_origin, injury_time_length, var_confirmed, rescinded,
                       description, shootout_sequence
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId).values()).containsOnlyNulls();
    }

    @Test
    void upgradesV13WithTheInjurySubstitutionClassWithoutRewritingV6History() {
        String schema = "upgrade_v13_to_v14";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV13 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("13"))
                .load();

        assertThat(flywayV13.migrate().migrationsExecuted).isEqualTo(13);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id,
                    provider,
                    provider_event_id,
                    created_at
                ) values (
                    '14141414-1414-1414-1414-141414141414',
                    'SOFASCORE',
                    16248427,
                    '2026-08-17T07:28:21Z'
                )
                """);
        Long historicalObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id,
                    endpoint_type,
                    source_kind,
                    source_reference,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    completeness_status,
                    completeness_score,
                    present_signals,
                    expected_signals,
                    missing_paths_json,
                    normalized_sha256
                ) values (
                    '14141414-1414-1414-1414-141414141414',
                    'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE',
                    'historical-v6-regular-substitution',
                    'historical-v6-regular-substitution',
                    repeat('c', 64),
                    'event-incidents-v6',
                    '2026-08-17T07:28:22Z',
                    'COMPLETE',
                    100,
                    4,
                    4,
                    '[]'::jsonb,
                    repeat('d', 64)
                )
                returning id
                """, Long.class);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id,
                    incident_order,
                    incident_type,
                    minute,
                    is_home,
                    player_in_name,
                    player_out_name,
                    incident_class,
                    injury
                ) values (?, 0, 'substitution', 85, true,
                          'Incoming Player', 'Outgoing Player', 'regular', false)
                """, historicalObservationId);
        Map<String, Object> historicalObservationBefore = upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId);
        Map<String, Object> historicalIncidentBefore = upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       is_home, player_in_name, player_out_name, incident_class, injury
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId);
        String parserConstraintBefore = parserConstraint(upgradeJdbc, schema);
        assertThat(parserConstraintBefore)
                .contains(EventIncidentsV6Parser.PARSER_VERSION)
                .doesNotContain(EventIncidentsV7Parser.PARSER_VERSION);

        Flyway flywayV14 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("14"))
                .load();

        assertThat(flywayV14.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV14.info().current().getVersion().getVersion()).isEqualTo("14");
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV6Parser.PARSER_VERSION)
                .contains(EventIncidentsV7Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalObservationBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       is_home, player_in_name, player_out_name, incident_class, injury
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalIncidentBefore);

        Long v7ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id,
                    endpoint_type,
                    source_kind,
                    source_reference,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    completeness_status,
                    completeness_score,
                    present_signals,
                    expected_signals,
                    missing_paths_json,
                    normalized_sha256
                ) values (
                    '14141414-1414-1414-1414-141414141414',
                    'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE',
                    'v7-injury-substitution',
                    'v7-injury-substitution',
                    repeat('e', 64),
                    'event-incidents-v7',
                    '2026-08-17T07:28:23Z',
                    'COMPLETE',
                    100,
                    4,
                    4,
                    '[]'::jsonb,
                    repeat('f', 64)
                )
                returning id
                """, Long.class);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id,
                    incident_order,
                    incident_type,
                    minute,
                    is_home,
                    player_in_name,
                    player_out_name,
                    incident_class,
                    injury
                ) values (?, 0, 'substitution', 46, false,
                          'Jack Grealish', 'Jérémy Doku', 'injury', true)
                """, v7ObservationId);
        assertThat(upgradeJdbc.queryForMap("""
                select parser_version, incident_class, injury,
                       player_in_name, player_out_name
                from j5_event_data_observation observation
                join j5_event_incident incident on incident.observation_id = observation.id
                where observation.id = ?
                """, v7ObservationId))
                .containsEntry("parser_version", EventIncidentsV7Parser.PARSER_VERSION)
                .containsEntry("incident_class", "injury")
                .containsEntry("injury", true)
                .containsEntry("player_in_name", "Jack Grealish")
                .containsEntry("player_out_name", "Jérémy Doku");
    }

    @Test
    void upgradesV14WithPenaltyVariantsWithoutRewritingV7History() {
        String schema = "upgrade_v14_to_v15";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV14 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("14"))
                .load();

        assertThat(flywayV14.migrate().migrationsExecuted).isEqualTo(14);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id,
                    provider,
                    provider_event_id,
                    created_at
                ) values (
                    '15151515-1515-1515-1515-151515151515',
                    'SOFASCORE',
                    14037091,
                    '2026-08-18T07:59:59Z'
                )
                """);
        Long historicalObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id,
                    endpoint_type,
                    source_kind,
                    source_reference,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    completeness_status,
                    completeness_score,
                    present_signals,
                    expected_signals,
                    missing_paths_json,
                    normalized_sha256
                ) values (
                    '15151515-1515-1515-1515-151515151515',
                    'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE',
                    'historical-v7-injury-substitution',
                    'historical-v7-injury-substitution',
                    repeat('a', 64),
                    'event-incidents-v7',
                    '2026-08-18T08:00:00Z',
                    'COMPLETE',
                    100,
                    4,
                    4,
                    '[]'::jsonb,
                    repeat('b', 64)
                )
                returning id
                """, Long.class);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id,
                    incident_order,
                    incident_type,
                    minute,
                    is_home,
                    player_in_name,
                    player_out_name,
                    incident_class,
                    injury
                ) values (?, 0, 'substitution', 46, false,
                          'Incoming Player', 'Outgoing Player', 'injury', true)
                """, historicalObservationId);
        Map<String, Object> historicalObservationBefore = upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId);
        Map<String, Object> historicalIncidentBefore = upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       is_home, player_in_name, player_out_name, incident_class, injury
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId);
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV7Parser.PARSER_VERSION)
                .doesNotContain(EventIncidentsV8Parser.PARSER_VERSION);

        Flyway flywayV15 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("15"))
                .load();

        assertThat(flywayV15.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV15.info().current().getVersion().getVersion()).isEqualTo("15");
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV7Parser.PARSER_VERSION)
                .contains(EventIncidentsV8Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalObservationBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       is_home, player_in_name, player_out_name, incident_class, injury
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalIncidentBefore);

        Long v8ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id,
                    endpoint_type,
                    source_kind,
                    source_reference,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    completeness_status,
                    completeness_score,
                    present_signals,
                    expected_signals,
                    missing_paths_json,
                    normalized_sha256
                ) values (
                    '15151515-1515-1515-1515-151515151515',
                    'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE',
                    'v8-penalty-variants',
                    'v8-penalty-variants',
                    repeat('c', 64),
                    ?,
                    '2026-08-18T08:00:01Z',
                    'COMPLETE',
                    100,
                    8,
                    8,
                    '[]'::jsonb,
                    repeat('d', 64)
                )
                returning id
                """, Long.class, EventIncidentsV8Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id,
                    incident_order,
                    incident_type,
                    minute,
                    is_home,
                    player_name,
                    home_score,
                    away_score,
                    incident_class,
                    reason,
                    period_text,
                    description,
                    shootout_sequence
                ) values
                    (?, 0, 'period', 146, null, null, 3, 2,
                     null, null, 'PEN', null, null),
                    (?, 1, 'penaltyShootout', 146, false, 'Shootout Taker', 3, 2,
                     'missed', 'woodwork', null, 'Woodwork', 9),
                    (?, 2, 'card', 56, false, 'Booked Player', null, null,
                     'yellowRed', null, null, null, null)
                """, v8ObservationId, v8ObservationId, v8ObservationId);
        assertThat(upgradeJdbc.queryForMap("""
                select parser_version, minute, period_text
                from j5_event_data_observation observation
                join j5_event_incident incident on incident.observation_id = observation.id
                where observation.id = ? and incident.incident_order = 0
                """, v8ObservationId))
                .containsEntry("parser_version", EventIncidentsV8Parser.PARSER_VERSION)
                .containsEntry("minute", 146)
                .containsEntry("period_text", "PEN");
        assertThat(upgradeJdbc.queryForMap("""
                select incident_class, reason, description, shootout_sequence
                from j5_event_incident
                where observation_id = ? and incident_order = 1
                """, v8ObservationId))
                .containsEntry("incident_class", "missed")
                .containsEntry("reason", "woodwork")
                .containsEntry("description", "Woodwork")
                .containsEntry("shootout_sequence", 9);
        assertThat(upgradeJdbc.queryForMap("""
                select incident_class, reason
                from j5_event_incident
                where observation_id = ? and incident_order = 2
                """, v8ObservationId))
                .containsEntry("incident_class", "yellowRed")
                .containsEntry("reason", null);
    }

    @Test
    void upgradesV15WithTheObservedBenchCardVariantWithoutRewritingV8History() {
        String schema = "upgrade_v15_to_v16";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV15 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("15"))
                .load();

        assertThat(flywayV15.migrate().migrationsExecuted).isEqualTo(15);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id,
                    provider,
                    provider_event_id,
                    created_at
                ) values (
                    '16161616-1616-1616-1616-161616161616',
                    'SOFASCORE',
                    16391145,
                    '2026-08-18T09:33:43Z'
                )
                """);
        Long historicalObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id,
                    endpoint_type,
                    source_kind,
                    source_reference,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    completeness_status,
                    completeness_score,
                    present_signals,
                    expected_signals,
                    missing_paths_json,
                    normalized_sha256
                ) values (
                    '16161616-1616-1616-1616-161616161616',
                    'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE',
                    'historical-v8-card',
                    'historical-v8-card',
                    repeat('a', 64),
                    ?,
                    '2026-08-18T09:33:44Z',
                    'COMPLETE',
                    100,
                    2,
                    2,
                    '[]'::jsonb,
                    repeat('b', 64)
                )
                returning id
                """, Long.class, EventIncidentsV8Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id,
                    incident_order,
                    incident_type,
                    minute,
                    is_home,
                    player_name,
                    incident_class,
                    reason
                ) values (?, 0, 'card', 56, false, 'Booked Player', 'yellow', 'Foul')
                """, historicalObservationId);
        Map<String, Object> historicalObservationBefore = upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId);
        Map<String, Object> historicalIncidentBefore = upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId);
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV8Parser.PARSER_VERSION)
                .doesNotContain(EventIncidentsV9Parser.PARSER_VERSION);

        assertThatThrownBy(() -> upgradeJdbc.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json, normalized_sha256
                ) values (
                    '16161616-1616-1616-1616-161616161616', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v9-before-migration', 'v9-before-migration',
                    repeat('c', 64), ?, '2026-08-18T09:33:45Z', 'COMPLETE', 100,
                    2, 2, '[]'::jsonb, repeat('d', 64)
                )
                """, EventIncidentsV9Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway flywayV16 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("16"))
                .load();

        assertThat(flywayV16.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV16.info().current().getVersion().getVersion()).isEqualTo("16");
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV8Parser.PARSER_VERSION)
                .contains(EventIncidentsV9Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalObservationBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalIncidentBefore);

        Long v9ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id,
                    endpoint_type,
                    source_kind,
                    source_reference,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    completeness_status,
                    completeness_score,
                    present_signals,
                    expected_signals,
                    missing_paths_json,
                    normalized_sha256
                ) values (
                    '16161616-1616-1616-1616-161616161616',
                    'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE',
                    'v9-bench-card',
                    'v9-bench-card',
                    repeat('e', 64),
                    ?,
                    '2026-08-18T09:33:46Z',
                    'COMPLETE',
                    100,
                    2,
                    2,
                    '[]'::jsonb,
                    repeat('f', 64)
                )
                returning id
                """, Long.class, EventIncidentsV9Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id,
                    incident_order,
                    incident_type,
                    minute,
                    added_time,
                    is_home,
                    player_name,
                    incident_class,
                    reason
                ) values (?, 0, 'card', 90, 9, true,
                          'Home Manager', 'yellow', 'Other reason')
                """, v9ObservationId);
        assertThat(upgradeJdbc.queryForMap("""
                select parser_version, minute, added_time, player_name, incident_class, reason
                from j5_event_data_observation observation
                join j5_event_incident incident on incident.observation_id = observation.id
                where observation.id = ?
                """, v9ObservationId))
                .containsEntry("parser_version", EventIncidentsV9Parser.PARSER_VERSION)
                .containsEntry("minute", 90)
                .containsEntry("added_time", 9)
                .containsEntry("player_name", "Home Manager")
                .containsEntry("incident_class", "yellow")
                .containsEntry("reason", "Other reason");
    }

    @Test
    void upgradesV16WithTheObservedRegularGoalOriginWithoutRewritingV9History() {
        String schema = "upgrade_v16_to_v17";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV16 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("16"))
                .load();

        assertThat(flywayV16.migrate().migrationsExecuted).isEqualTo(16);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id, provider, provider_event_id, created_at
                ) values (
                    '17171717-1717-1717-1717-171717171717',
                    'SOFASCORE',
                    16251993,
                    '2026-08-18T10:09:38Z'
                )
                """);
        Long historicalObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '17171717-1717-1717-1717-171717171717', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'historical-v9-card', 'historical-v9-card',
                    repeat('a', 64), ?, '2026-08-18T10:09:39Z', 'COMPLETE', 100,
                    2, 2, '[]'::jsonb, repeat('b', 64)
                )
                returning id
                """, Long.class, EventIncidentsV9Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute, added_time,
                    is_home, player_name, incident_class, reason
                ) values (?, 0, 'card', 90, 9, true,
                          'Home Manager', 'yellow', 'Other reason')
                """, historicalObservationId);
        Map<String, Object> historicalObservationBefore = upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId);
        Map<String, Object> historicalIncidentBefore = upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId);

        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV9Parser.PARSER_VERSION)
                .doesNotContain(EventIncidentsV10Parser.PARSER_VERSION);
        assertThatThrownBy(() -> upgradeJdbc.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json, normalized_sha256
                ) values (
                    '17171717-1717-1717-1717-171717171717', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v10-before-migration', 'v10-before-migration',
                    repeat('c', 64), ?, '2026-08-18T10:09:40Z', 'COMPLETE', 100,
                    4, 4, '[]'::jsonb, repeat('d', 64)
                )
                """, EventIncidentsV10Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway flywayV17 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("17"))
                .load();

        assertThat(flywayV17.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV17.info().current().getVersion().getVersion()).isEqualTo("17");
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV9Parser.PARSER_VERSION)
                .contains(EventIncidentsV10Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalObservationBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalIncidentBefore);

        Long v10ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '17171717-1717-1717-1717-171717171717', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v10-regular-goal', 'v10-regular-goal',
                    repeat('e', 64), ?, '2026-08-18T10:09:41Z', 'COMPLETE', 100,
                    4, 4, '[]'::jsonb, repeat('f', 64)
                )
                returning id
                """, Long.class, EventIncidentsV10Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute, is_home,
                    player_name, home_score, away_score, incident_class, goal_origin
                ) values (?, 0, 'goal', 16, false,
                          'Goal Scorer', 0, 1, 'regular', null)
                """, v10ObservationId);
        assertThat(upgradeJdbc.queryForMap("""
                select parser_version, incident_type, minute, player_name,
                       incident_class, goal_origin
                from j5_event_data_observation observation
                join j5_event_incident incident on incident.observation_id = observation.id
                where observation.id = ?
                """, v10ObservationId))
                .containsEntry("parser_version", EventIncidentsV10Parser.PARSER_VERSION)
                .containsEntry("incident_type", "goal")
                .containsEntry("minute", 16)
                .containsEntry("player_name", "Goal Scorer")
                .containsEntry("incident_class", "regular")
                .containsEntry("goal_origin", null);
    }

    @Test
    void upgradesV17WithTheObservedOffTheBallReasonWithoutRewritingV10History() {
        String schema = "upgrade_v17_to_v18";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV17 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("17"))
                .load();

        assertThat(flywayV17.migrate().migrationsExecuted).isEqualTo(17);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id, provider, provider_event_id, created_at
                ) values (
                    '18181818-1818-1818-1818-181818181818',
                    'SOFASCORE',
                    16251993,
                    '2026-08-18T10:29:59Z'
                )
                """);
        Long historicalObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '18181818-1818-1818-1818-181818181818', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'historical-v10-card', 'historical-v10-card',
                    repeat('a', 64), ?, '2026-08-18T10:30:00Z', 'COMPLETE', 100,
                    2, 2, '[]'::jsonb, repeat('b', 64)
                )
                returning id
                """, Long.class, EventIncidentsV10Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, player_name, incident_class, reason
                ) values (?, 0, 'card', 54, false,
                          'Historical Player', 'yellow', 'Foul')
                """, historicalObservationId);
        Map<String, Object> historicalObservationBefore = upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId);
        Map<String, Object> historicalIncidentBefore = upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId);

        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV10Parser.PARSER_VERSION)
                .doesNotContain(EventIncidentsV11Parser.PARSER_VERSION);
        assertThatThrownBy(() -> upgradeJdbc.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '18181818-1818-1818-1818-181818181818', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v11-before-migration', 'v11-before-migration',
                    repeat('c', 64), ?, '2026-08-18T10:30:01Z', 'COMPLETE', 100,
                    2, 2, '[]'::jsonb, repeat('d', 64)
                )
                """, EventIncidentsV11Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway flywayV18 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("18"))
                .load();

        assertThat(flywayV18.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV18.info().current().getVersion().getVersion()).isEqualTo("18");
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV10Parser.PARSER_VERSION)
                .contains(EventIncidentsV11Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalObservationBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalIncidentBefore);

        Long v11ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '18181818-1818-1818-1818-181818181818', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v11-off-ball-card', 'v11-off-ball-card',
                    repeat('e', 64), ?, '2026-08-18T10:30:02Z', 'COMPLETE', 100,
                    2, 2, '[]'::jsonb, repeat('f', 64)
                )
                returning id
                """, Long.class, EventIncidentsV11Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, player_provider_id, player_name, incident_class, reason
                ) values (?, 0, 'card', 77, false, 877400,
                          'Facundo Mallo', 'yellow', 'Off the ball foul')
                """, v11ObservationId);
        assertThat(upgradeJdbc.queryForMap("""
                select parser_version, incident_type, minute, is_home,
                       player_provider_id, player_name, incident_class, reason
                from j5_event_data_observation observation
                join j5_event_incident incident on incident.observation_id = observation.id
                where observation.id = ?
                """, v11ObservationId))
                .containsEntry("parser_version", EventIncidentsV11Parser.PARSER_VERSION)
                .containsEntry("incident_type", "card")
                .containsEntry("minute", 77)
                .containsEntry("is_home", false)
                .containsEntry("player_provider_id", 877400L)
                .containsEntry("player_name", "Facundo Mallo")
                .containsEntry("incident_class", "yellow")
                .containsEntry("reason", "Off the ball foul");
    }

    @Test
    void upgradesV18ForUnminutedTerminalShootoutsWithoutRewritingV11History() {
        String schema = "upgrade_v18_to_v19";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV18 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("18"))
                .load();

        assertThat(flywayV18.migrate().migrationsExecuted).isEqualTo(18);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id, provider, provider_event_id, created_at
                ) values (
                    '19191919-1919-1919-1919-191919191919',
                    'SOFASCORE',
                    16691018,
                    '2026-08-18T12:28:20Z'
                )
                """);
        Long historicalObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '19191919-1919-1919-1919-191919191919', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'historical-v11-card', 'historical-v11-card',
                    repeat('a', 64), ?, '2026-08-18T12:28:21Z', 'COMPLETE', 100,
                    2, 2, '[]'::jsonb, repeat('b', 64)
                )
                returning id
                """, Long.class, EventIncidentsV11Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, player_name, incident_class, reason
                ) values (?, 0, 'card', 77, false,
                          'Historical Player', 'yellow', 'Off the ball foul')
                """, historicalObservationId);
        Map<String, Object> historicalObservationBefore = upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId);
        Map<String, Object> historicalIncidentBefore = upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId);

        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV11Parser.PARSER_VERSION)
                .doesNotContain(EventIncidentsV12Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForObject("""
                select is_nullable
                from information_schema.columns
                where table_schema = ?
                  and table_name = 'j5_event_incident'
                  and column_name = 'minute'
                """, String.class, schema)).isEqualTo("NO");
        assertThatThrownBy(() -> upgradeJdbc.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '19191919-1919-1919-1919-191919191919', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v12-before-migration', 'v12-before-migration',
                    repeat('c', 64), ?, '2026-08-18T12:28:22Z', 'PARTIAL', 50,
                    1, 2, '["$.incidents[0].time"]'::jsonb, repeat('d', 64)
                )
                """, EventIncidentsV12Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway flywayV19 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("19"))
                .load();

        assertThat(flywayV19.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV19.info().current().getVersion().getVersion()).isEqualTo("19");
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV11Parser.PARSER_VERSION)
                .contains(EventIncidentsV12Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForObject("""
                select is_nullable
                from information_schema.columns
                where table_schema = ?
                  and table_name = 'j5_event_incident'
                  and column_name = 'minute'
                """, String.class, schema)).isEqualTo("YES");
        assertThat(upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalObservationBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, player_name, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalIncidentBefore);

        Long v12ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '19191919-1919-1919-1919-191919191919', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v12-unminuted-shootout', 'v12-unminuted-shootout',
                    repeat('e', 64), ?, '2026-08-18T12:28:26Z', 'PARTIAL', 50,
                    1, 2, '["$.incidents[0].time"]'::jsonb, repeat('f', 64)
                )
                returning id
                """, Long.class, EventIncidentsV12Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    home_score, away_score, period_text
                ) values (?, 0, 'period', null, 2, 1, 'PEN')
                """, v12ObservationId);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, home_score, away_score, incident_class, shootout_sequence
                ) values (?, 1, 'penaltyShootout', null, false, 1, 1, 'missed', 1)
                """, v12ObservationId);
        assertThat(upgradeJdbc.queryForList("""
                select incident_type, minute, added_time
                from j5_event_incident
                where observation_id = ?
                order by incident_order
                """, v12ObservationId))
                .allSatisfy(row -> {
                    assertThat(row.get("minute")).isNull();
                    assertThat(row.get("added_time")).isNull();
                });
        assertThatThrownBy(() -> upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, incident_class
                ) values (?, 2, 'inGamePenalty', null, true, 'missed')
                """, v12ObservationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute, added_time,
                    is_home, incident_class, shootout_sequence
                ) values (?, 2, 'penaltyShootout', null, 1, true, 'missed', 2)
                """, v12ObservationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void upgradesV19ForLeavingFieldCardsWithoutRewritingV12History() {
        String schema = "upgrade_v19_to_v20";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV19 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("19"))
                .load();

        assertThat(flywayV19.migrate().migrationsExecuted).isEqualTo(19);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id, provider, provider_event_id, created_at
                ) values (
                    '20202020-2020-2020-2020-202020202020',
                    'SOFASCORE',
                    16691018,
                    '2026-08-18T12:28:20Z'
                )
                """);
        Long historicalObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '20202020-2020-2020-2020-202020202020', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'historical-v12-shootout',
                    'historical-v12-shootout', repeat('a', 64), ?,
                    '2026-08-18T12:28:26Z', 'PARTIAL', 50,
                    1, 2, '["$.incidents[0].time"]'::jsonb, repeat('b', 64)
                )
                returning id
                """, Long.class, EventIncidentsV12Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, home_score, away_score, incident_class, shootout_sequence
                ) values (?, 0, 'penaltyShootout', null,
                          false, 0, 0, 'missed', 1)
                """, historicalObservationId);
        Map<String, Object> historicalObservationBefore = upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId);
        Map<String, Object> historicalIncidentBefore = upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, home_score, away_score,
                       incident_class, shootout_sequence
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId);

        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV12Parser.PARSER_VERSION)
                .doesNotContain(EventIncidentsV13Parser.PARSER_VERSION);
        assertThatThrownBy(() -> upgradeJdbc.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '20202020-2020-2020-2020-202020202020', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v13-before-migration', 'v13-before-migration',
                    repeat('c', 64), ?, '2026-08-18T13:00:00Z', 'COMPLETE', 100,
                    3, 3, '[]'::jsonb, repeat('d', 64)
                )
                """, EventIncidentsV13Parser.PARSER_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway flywayV20 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("20"))
                .load();

        assertThat(flywayV20.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV20.info().current().getVersion().getVersion()).isEqualTo("20");
        assertThat(parserConstraint(upgradeJdbc, schema))
                .contains(EventIncidentsV12Parser.PARSER_VERSION)
                .contains(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(upgradeJdbc.queryForMap("""
                select id, canonical_event_id, endpoint_type, source_kind, source_reference,
                       source_fixture_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status, completeness_score,
                       present_signals, expected_signals, missing_paths_json,
                       normalized_sha256
                from j5_event_data_observation
                where id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalObservationBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select id, observation_id, incident_order, incident_type, minute,
                       added_time, is_home, home_score, away_score,
                       incident_class, shootout_sequence
                from j5_event_incident
                where observation_id = ?
                """, historicalObservationId)).containsAllEntriesOf(historicalIncidentBefore);

        Long v13ObservationId = upgradeJdbc.queryForObject("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind, source_reference,
                    source_fixture_id, source_payload_sha256, parser_version,
                    source_received_at, completeness_status, completeness_score,
                    present_signals, expected_signals, missing_paths_json,
                    normalized_sha256
                ) values (
                    '20202020-2020-2020-2020-202020202020', 'EVENT_INCIDENTS',
                    'SYNTHETIC_FIXTURE', 'v13-leaving-field-card',
                    'v13-leaving-field-card', repeat('e', 64), ?,
                    '2026-08-18T13:00:01Z', 'COMPLETE', 100,
                    3, 3, '[]'::jsonb, repeat('f', 64)
                )
                returning id
                """, Long.class, EventIncidentsV13Parser.PARSER_VERSION);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, player_provider_id, player_name, incident_class, reason
                ) values (?, 0, 'card', 74, false, 817886,
                          'Yongjing Cao', 'yellow', 'Leaving field')
                """, v13ObservationId);
        assertThat(upgradeJdbc.queryForMap("""
                select parser_version, incident_type, minute, is_home,
                       player_provider_id, player_name, incident_class, reason
                from j5_event_data_observation observation
                join j5_event_incident incident on incident.observation_id = observation.id
                where observation.id = ?
                """, v13ObservationId))
                .containsEntry("parser_version", EventIncidentsV13Parser.PARSER_VERSION)
                .containsEntry("incident_type", "card")
                .containsEntry("minute", 74)
                .containsEntry("is_home", false)
                .containsEntry("player_provider_id", 817886L)
                .containsEntry("player_name", "Yongjing Cao")
                .containsEntry("incident_class", "yellow")
                .containsEntry("reason", "Leaving field");
    }

    @Test
    void upgradesV20WithOneImmutableBaselineOccurrencePerHistoricalSnapshot() {
        String schema = "upgrade_v20_to_v21";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV20 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("20"))
                .load();

        assertThat(flywayV20.migrate().migrationsExecuted).isEqualTo(20);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        Long snapshotId = upgradeJdbc.queryForObject("""
                insert into provider_snapshot (
                    provider, acquisition_mode, logical_endpoint, request_key,
                    requested_at, received_at, http_status, content_type, latency_ms,
                    payload_raw, payload_size_bytes, payload_sha256,
                    parser_version, schema_status
                ) values (
                    'SOFASCORE', 'DIRECT_LOCAL_ENDPOINT', 'EVENT_DETAILS',
                    'EVENT_DETAILS|eventId=16671566',
                    '2026-08-18T12:00:00Z', '2026-08-18T12:00:01Z',
                    200, 'application/json', 1000,
                    decode('7b7d', 'hex'), 2, repeat('a', 64),
                    'event-details-v2', 'PARSED'
                )
                returning id
                """, Long.class);
        Map<String, Object> snapshotBefore = upgradeJdbc.queryForMap("""
                select id, request_key, requested_at, received_at, http_status,
                       content_type, latency_ms, payload_size_bytes, payload_sha256,
                       parser_version, schema_status
                from provider_snapshot
                where id = ?
                """, snapshotId);

        Flyway flywayV21 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("21"))
                .load();

        assertThat(flywayV21.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV21.info().current().getVersion().getVersion()).isEqualTo("21");
        assertThat(upgradeJdbc.queryForMap("""
                select id, request_key, requested_at, received_at, http_status,
                       content_type, latency_ms, payload_size_bytes, payload_sha256,
                       parser_version, schema_status
                from provider_snapshot
                where id = ?
                """, snapshotId)).containsAllEntriesOf(snapshotBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select snapshot_id, requested_at, received_at, http_status,
                       content_type, latency_ms, parser_version, persistence_outcome
                from provider_snapshot_occurrence
                where snapshot_id = ?
                """, snapshotId))
                .containsEntry("snapshot_id", snapshotId)
                .containsEntry("http_status", 200)
                .containsEntry("content_type", "application/json")
                .containsEntry("latency_ms", 1000L)
                .containsEntry("parser_version", "event-details-v2")
                .containsEntry("persistence_outcome", "BASELINE");
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from provider_snapshot_occurrence where snapshot_id = ?",
                Long.class,
                snapshotId)).isEqualTo(1L);
        assertThatThrownBy(() -> upgradeJdbc.update("""
                update provider_snapshot_occurrence
                set persistence_outcome = 'INSERTED'
                where snapshot_id = ?
                """, snapshotId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("provider_snapshot_occurrence is append-only");
        assertThatThrownBy(() -> upgradeJdbc.update(
                "delete from provider_snapshot_occurrence where snapshot_id = ?",
                snapshotId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("provider_snapshot_occurrence is append-only");
    }

    @Test
    void upgradesV21WithGuardedPayloadRetentionWithoutRewritingSnapshots() {
        String schema = "upgrade_v21_to_v22";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV21 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("21"))
                .load();

        assertThat(flywayV21.migrate().migrationsExecuted).isEqualTo(21);
        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        Long snapshotId = upgradeJdbc.queryForObject("""
                insert into provider_snapshot (
                    provider, acquisition_mode, logical_endpoint, request_key,
                    requested_at, received_at, http_status, content_type, latency_ms,
                    payload_raw, payload_size_bytes, payload_sha256,
                    parser_version, schema_status
                ) values (
                    'SOFASCORE', 'DIRECT_LOCAL_ENDPOINT', 'EVENT_DETAILS',
                    'EVENT_DETAILS|eventId=16671566|j6-retention',
                    '2026-06-01T12:00:00Z', '2026-06-01T12:00:01Z',
                    200, 'application/json', 1000,
                    decode('7b7d', 'hex'), 2, repeat('a', 64),
                    'event-details-v2', 'PARSED'
                )
                returning id
                """, Long.class);
        Map<String, Object> before = upgradeJdbc.queryForMap("""
                select id, request_key, payload_size_bytes, payload_sha256,
                       encode(payload_raw, 'hex') as payload_hex, schema_status
                from provider_snapshot
                where id = ?
                """, snapshotId);

        Flyway flywayV22 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("22"))
                .load();

        assertThat(flywayV22.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV22.info().current().getVersion().getVersion()).isEqualTo("22");
        assertThat(upgradeJdbc.queryForMap("""
                select id, request_key, payload_size_bytes, payload_sha256,
                       encode(payload_raw, 'hex') as payload_hex, schema_status
                from provider_snapshot
                where id = ?
                """, snapshotId)).containsAllEntriesOf(before);
        assertThat(upgradeJdbc.queryForObject(
                "select payload_purged_at is null from provider_snapshot where id = ?",
                Boolean.class,
                snapshotId)).isTrue();
        assertThat(upgradeJdbc.queryForObject(
                "select to_regclass(? || '.j6_raw_payload_purge_audit')",
                String.class,
                schema)).isEqualTo("j6_raw_payload_purge_audit");

        Long rawOnlyId = upgradeJdbc.queryForObject("""
                insert into provider_snapshot (
                    provider, acquisition_mode, logical_endpoint, request_key,
                    requested_at, received_at, http_status, content_type, latency_ms,
                    payload_raw, payload_size_bytes, payload_sha256,
                    parser_version, schema_status
                ) values (
                    'SOFASCORE', 'DIRECT_LOCAL_ENDPOINT', 'EVENT_DETAILS',
                    'EVENT_DETAILS|eventId=16671567|j6-classification',
                    '2026-06-01T12:00:00Z', '2026-06-01T12:00:01Z',
                    200, 'application/json', 1000,
                    decode('7b7d', 'hex'), 2, repeat('b', 64),
                    'event-details-v2', 'RAW_ONLY'
                )
                returning id
                """, Long.class);
        assertThat(upgradeJdbc.update(
                "update provider_snapshot set schema_status = 'PARSED' where id = ?",
                rawOnlyId)).isEqualTo(1);
        assertThatThrownBy(() -> upgradeJdbc.update(
                "update provider_snapshot set request_key = request_key || '-changed' where id = ?",
                snapshotId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("provider_snapshot is immutable");
        assertThatThrownBy(() -> upgradeJdbc.update(
                "update provider_snapshot set payload_raw = null where id = ?",
                snapshotId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("audited J6 payload purge");
        assertThatThrownBy(() -> upgradeJdbc.update(
                "delete from provider_snapshot where id = ?",
                snapshotId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("provider_snapshot deletion is forbidden");
    }

    @Test
    void upgradesV22WithGuardedJ7ExportsWithoutChangingLegacyRows() {
        String schema = "upgrade_v22_to_v23";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV22 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("22"))
                .load();

        assertThat(flywayV22.migrate().migrationsExecuted).isEqualTo(22);
        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into export_manifest (
                    schema_version, export_path, content_sha256,
                    validation_status, warnings
                ) values (
                    'legacy-v1', 'legacy-export.json', repeat('a', 64),
                    'RAW_ONLY', '["legacy"]'::jsonb
                )
                """);
        Map<String, Object> legacyBefore = upgradeJdbc.queryForMap("""
                select schema_version, export_path, content_sha256,
                       validation_status, source_snapshot_ids::text as source_snapshot_ids,
                       warnings::text as warnings
                from export_manifest
                where export_path = 'legacy-export.json'
                """);
        UUID canonicalEventId = UUID.fromString(
                "71000000-0000-0000-0000-000000000007");
        upgradeJdbc.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', 17000007)
                """, canonicalEventId);
        upgradeJdbc.update("""
                insert into provider_snapshot (
                    provider, logical_endpoint, request_key,
                    requested_at, received_at, http_status, content_type,
                    latency_ms, payload_jsonb, payload_sha256, parser_version,
                    schema_status, acquisition_mode, payload_raw,
                    payload_size_bytes
                ) values
                (
                    'SOFASCORE', 'EVENT_DETAILS',
                    'J7_UPGRADE|EVENT_DETAILS|eventId=17000007',
                    '2026-08-18T07:59:59Z', '2026-08-18T08:00:00Z',
                    200, 'application/json', 1000,
                    '{"event":{"id":17000007}}'::jsonb, repeat('1', 64),
                    'event-details-v2', 'PARSED', 'DIRECT_LOCAL_ENDPOINT',
                    convert_to('{"event":{"id":17000007}}', 'UTF8'),
                    octet_length(convert_to('{"event":{"id":17000007}}', 'UTF8'))
                ),
                (
                    'SOFASCORE', 'EVENT_STATISTICS',
                    'J7_UPGRADE|EVENT_STATISTICS|eventId=17000007',
                    '2026-08-18T08:00:59Z', '2026-08-18T08:01:00Z',
                    200, 'application/json', 1000,
                    '{"statistics":[{"period":"ALL"}]}'::jsonb, repeat('2', 64),
                    'event-statistics-v2', 'PARSED', 'DIRECT_LOCAL_ENDPOINT',
                    convert_to('{"statistics":[{"period":"ALL"}]}', 'UTF8'),
                    octet_length(convert_to(
                        '{"statistics":[{"period":"ALL"}]}', 'UTF8'))
                ),
                (
                    'SOFASCORE', 'EVENT_INCIDENTS',
                    'J7_UPGRADE|EVENT_INCIDENTS|eventId=17000007',
                    '2026-08-18T08:01:59Z', '2026-08-18T08:02:00Z',
                    200, 'application/json', 1000,
                    '{"incidents":[{"incidentType":"goal","time":24}]}'::jsonb,
                    repeat('3', 64), 'event-incidents-v4', 'PARSED',
                    'DIRECT_LOCAL_ENDPOINT',
                    convert_to(
                        '{"incidents":[{"incidentType":"goal","time":24}]}',
                        'UTF8'),
                    octet_length(convert_to(
                        '{"incidents":[{"incidentType":"goal","time":24}]}',
                        'UTF8'))
                ),
                (
                    'SOFASCORE', 'EVENT_LINEUPS',
                    'J7_UPGRADE|EVENT_LINEUPS|eventId=17000007',
                    '2026-08-18T08:02:59Z', '2026-08-18T08:03:00Z',
                    200, 'application/json', 1000,
                    ('{"confirmed":true,"home":{"formation":"4-3-3"},'
                        || '"away":{"formation":"4-4-2"}}')::jsonb,
                    repeat('4', 64), 'event-lineups-v2', 'PARSED',
                    'DIRECT_LOCAL_ENDPOINT',
                    convert_to(
                        '{"confirmed":true,"home":{"formation":"4-3-3"},'
                            || '"away":{"formation":"4-4-2"}}',
                        'UTF8'),
                    octet_length(convert_to(
                        '{"confirmed":true,"home":{"formation":"4-3-3"},'
                            || '"away":{"formation":"4-4-2"}}',
                        'UTF8'))
                )
                """);
        Long detailsSnapshotId = upgradeJdbc.queryForObject("""
                select id from provider_snapshot
                where request_key = 'J7_UPGRADE|EVENT_DETAILS|eventId=17000007'
                """, Long.class);
        Long statisticsSnapshotId = upgradeJdbc.queryForObject("""
                select id from provider_snapshot
                where request_key = 'J7_UPGRADE|EVENT_STATISTICS|eventId=17000007'
                """, Long.class);
        Long incidentsSnapshotId = upgradeJdbc.queryForObject("""
                select id from provider_snapshot
                where request_key = 'J7_UPGRADE|EVENT_INCIDENTS|eventId=17000007'
                """, Long.class);
        Long lineupsSnapshotId = upgradeJdbc.queryForObject("""
                select id from provider_snapshot
                where request_key = 'J7_UPGRADE|EVENT_LINEUPS|eventId=17000007'
                """, Long.class);
        upgradeJdbc.update("""
                insert into provider_snapshot_occurrence (
                    snapshot_id, requested_at, received_at, http_status,
                    content_type, latency_ms, parser_version,
                    persistence_outcome
                )
                select id, requested_at, received_at, http_status,
                       content_type, latency_ms, parser_version, 'INSERTED'
                from provider_snapshot
                where request_key like 'J7_UPGRADE|%'
                """);
        upgradeJdbc.update("""
                insert into canonical_event_observation (
                    canonical_event_id, source_kind, source_reference,
                    source_snapshot_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    starts_at, home_team_provider_id, home_team_name,
                    away_team_provider_id, away_team_name, status_type,
                    status_description, tournament_provider_id,
                    tournament_name, normalized_sha256
                ) values (
                    ?, 'PROVIDER_SNAPSHOT', 'snapshot:' || ?::text, ?, null,
                    repeat('1', 64), 'event-details-v2',
                    '2026-08-18T08:00:00Z', '2026-08-20T19:00:00Z',
                    17000101, 'J7 Upgrade Home', 17000102, 'J7 Upgrade Away',
                    'scheduled', 'Not started', 17000103,
                    'J7 Upgrade League', repeat('5', 64)
                )
                """, canonicalEventId, detailsSnapshotId, detailsSnapshotId);
        upgradeJdbc.update("""
                insert into event_detail_observation (
                    canonical_event_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    starts_at, home_team_provider_id, home_team_name,
                    away_team_provider_id, away_team_name, status_type,
                    status_description, tournament_provider_id,
                    tournament_name, venue_provider_id, venue_name, venue_city,
                    season_provider_id, season_name, event_round,
                    normalized_sha256, source_kind, source_reference,
                    source_snapshot_id
                ) values (
                    ?, null, repeat('1', 64), 'event-details-v2',
                    '2026-08-18T08:00:00Z', '2026-08-20T19:00:00Z',
                    17000101, 'J7 Upgrade Home', 17000102, 'J7 Upgrade Away',
                    'scheduled', 'Not started', 17000103,
                    'J7 Upgrade League', 17000104, 'J7 Upgrade Stadium',
                    'Paris', 17000105, '2026/2027', '3', repeat('6', 64),
                    'PROVIDER_SNAPSHOT', 'snapshot:' || ?::text, ?
                )
                """, canonicalEventId, detailsSnapshotId, detailsSnapshotId);
        upgradeJdbc.update("""
                insert into j5_event_data_observation (
                    canonical_event_id, endpoint_type, source_kind,
                    source_reference, source_snapshot_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    completeness_status, completeness_score, present_signals,
                    expected_signals, missing_paths_json, lineups_confirmed,
                    normalized_sha256
                ) values
                (?, 'EVENT_STATISTICS', 'PROVIDER_SNAPSHOT',
                    'snapshot:' || ?::text, ?, null, repeat('2', 64),
                    'event-statistics-v2', '2026-08-18T08:01:00Z',
                    'COMPLETE', 100, 2, 2, '[]'::jsonb, null, repeat('7', 64)),
                (?, 'EVENT_INCIDENTS', 'PROVIDER_SNAPSHOT',
                    'snapshot:' || ?::text, ?, null, repeat('3', 64),
                    'event-incidents-v4', '2026-08-18T08:02:00Z',
                    'COMPLETE', 100, 1, 1, '[]'::jsonb, null, repeat('8', 64)),
                (?, 'EVENT_LINEUPS', 'PROVIDER_SNAPSHOT',
                    'snapshot:' || ?::text, ?, null, repeat('4', 64),
                    'event-lineups-v2', '2026-08-18T08:03:00Z',
                    'COMPLETE', 100, 4, 4, '[]'::jsonb, true, repeat('9', 64))
                """,
                canonicalEventId,
                statisticsSnapshotId,
                statisticsSnapshotId,
                canonicalEventId,
                incidentsSnapshotId,
                incidentsSnapshotId,
                canonicalEventId,
                lineupsSnapshotId,
                lineupsSnapshotId);
        Long statisticsObservationId = upgradeJdbc.queryForObject("""
                select id from j5_event_data_observation
                where canonical_event_id = ? and endpoint_type = 'EVENT_STATISTICS'
                """, Long.class, canonicalEventId);
        Long incidentsObservationId = upgradeJdbc.queryForObject("""
                select id from j5_event_data_observation
                where canonical_event_id = ? and endpoint_type = 'EVENT_INCIDENTS'
                """, Long.class, canonicalEventId);
        Long lineupsObservationId = upgradeJdbc.queryForObject("""
                select id from j5_event_data_observation
                where canonical_event_id = ? and endpoint_type = 'EVENT_LINEUPS'
                """, Long.class, canonicalEventId);
        upgradeJdbc.update("""
                insert into j5_event_metric (
                    observation_id, metric_order, period, group_name,
                    metric_code, metric_name, home_value, away_value
                ) values (?, 0, 'ALL', 'Match overview', 'ballPossession',
                    'Ball possession', '54%', '46%')
                """, statisticsObservationId);
        upgradeJdbc.update("""
                insert into j5_event_incident (
                    observation_id, incident_order, incident_type, minute,
                    is_home, participant_provider_id, player_provider_id,
                    player_name, home_score, away_score
                ) values (?, 0, 'goal', 24, true, 17000101, 17000301,
                    'J7 Upgrade Scorer', 1, 0)
                """, incidentsObservationId);
        upgradeJdbc.update("""
                insert into j5_event_lineup_side (
                    observation_id, side, formation
                ) values (?, 'HOME', '4-3-3'), (?, 'AWAY', '4-4-2')
                """, lineupsObservationId, lineupsObservationId);
        upgradeJdbc.update("""
                insert into j5_event_lineup_player (
                    observation_id, side, player_order, player_provider_id,
                    player_name, shirt_number, position, starter
                ) values
                (?, 'HOME', 0, 17000401, 'J7 Upgrade Home Keeper', 1, 'G', true),
                (?, 'AWAY', 0, 17000402, 'J7 Upgrade Away Keeper', 1, 'G', true)
                """, lineupsObservationId, lineupsObservationId);

        List<Map<String, Object>> snapshotsBeforeV23 = upgradeJdbc.queryForList("""
                select snapshot.id, snapshot.logical_endpoint, snapshot.request_key,
                       snapshot.received_at, snapshot.payload_jsonb::text as payload_jsonb,
                       snapshot.payload_sha256, snapshot.parser_version,
                       snapshot.schema_status,
                       convert_from(snapshot.payload_raw, 'UTF8') as payload_raw,
                       snapshot.payload_size_bytes, snapshot.payload_purged_at,
                       occurrence.persistence_outcome,
                       occurrence.received_at as occurrence_received_at
                from provider_snapshot snapshot
                join provider_snapshot_occurrence occurrence
                  on occurrence.snapshot_id = snapshot.id
                where snapshot.request_key like 'J7_UPGRADE|%'
                order by snapshot.logical_endpoint
                """);
        List<Map<String, Object>> eventStateBeforeV23 = upgradeJdbc.queryForList("""
                select event.provider, event.provider_event_id,
                       observation.source_kind, observation.source_reference,
                       observation.source_snapshot_id,
                       observation.source_payload_sha256,
                       observation.parser_version, observation.source_received_at,
                       observation.home_team_name, observation.away_team_name,
                       observation.status_type, observation.tournament_name,
                       observation.normalized_sha256
                from canonical_event event
                join canonical_event_observation observation
                  on observation.canonical_event_id = event.id
                where event.id = ?
                """, canonicalEventId);
        List<Map<String, Object>> detailsBeforeV23 = upgradeJdbc.queryForList("""
                select source_kind, source_reference, source_snapshot_id,
                       source_payload_sha256, parser_version, source_received_at,
                       venue_name, venue_city, season_name, event_round,
                       normalized_sha256
                from event_detail_observation
                where canonical_event_id = ?
                """, canonicalEventId);
        List<Map<String, Object>> j5BeforeV23 = upgradeJdbc.queryForList("""
                select endpoint_type, source_kind, source_reference,
                       source_snapshot_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status,
                       completeness_score, present_signals, expected_signals,
                       missing_paths_json::text as missing_paths_json,
                       lineups_confirmed, normalized_sha256
                from j5_event_data_observation
                where canonical_event_id = ?
                order by endpoint_type
                """, canonicalEventId);
        List<Map<String, Object>> metricsBeforeV23 = upgradeJdbc.queryForList("""
                select metric_order, period, group_name, metric_code,
                       metric_name, home_value, away_value
                from j5_event_metric where observation_id = ?
                order by metric_order
                """, statisticsObservationId);
        List<Map<String, Object>> incidentsBeforeV23 = upgradeJdbc.queryForList("""
                select incident_order, incident_type, minute, is_home,
                       participant_provider_id, player_provider_id, player_name,
                       home_score, away_score
                from j5_event_incident where observation_id = ?
                order by incident_order
                """, incidentsObservationId);
        List<Map<String, Object>> lineupsBeforeV23 = upgradeJdbc.queryForList("""
                select side.side, side.formation, player.player_order,
                       player.player_provider_id, player.player_name,
                       player.shirt_number, player.position, player.starter
                from j5_event_lineup_side side
                join j5_event_lineup_player player
                  on player.observation_id = side.observation_id
                 and player.side = side.side
                where side.observation_id = ?
                order by side.side, player.player_order
                """, lineupsObservationId);

        Flyway flywayV23 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("23"))
                .load();

        assertThat(flywayV23.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV23.info().current().getVersion().getVersion()).isEqualTo("23");
        assertThat(snapshotsBeforeV23).hasSize(4);
        assertThat(eventStateBeforeV23).hasSize(1);
        assertThat(detailsBeforeV23).hasSize(1);
        assertThat(j5BeforeV23)
                .extracting(row -> row.get("completeness_status"))
                .containsExactly("COMPLETE", "COMPLETE", "COMPLETE");
        assertThat(metricsBeforeV23).singleElement().satisfies(metric ->
                assertThat(metric)
                        .containsEntry("metric_code", "ballPossession")
                        .containsEntry("home_value", "54%")
                        .containsEntry("away_value", "46%"));
        assertThat(incidentsBeforeV23).singleElement().satisfies(incident ->
                assertThat(incident)
                        .containsEntry("incident_type", "goal")
                        .containsEntry("player_name", "J7 Upgrade Scorer"));
        assertThat(lineupsBeforeV23)
                .extracting(row -> row.get("side"), row -> row.get("formation"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AWAY", "4-4-2"),
                        org.assertj.core.groups.Tuple.tuple("HOME", "4-3-3"));
        assertThat(upgradeJdbc.queryForList("""
                select snapshot.id, snapshot.logical_endpoint, snapshot.request_key,
                       snapshot.received_at, snapshot.payload_jsonb::text as payload_jsonb,
                       snapshot.payload_sha256, snapshot.parser_version,
                       snapshot.schema_status,
                       convert_from(snapshot.payload_raw, 'UTF8') as payload_raw,
                       snapshot.payload_size_bytes, snapshot.payload_purged_at,
                       occurrence.persistence_outcome,
                       occurrence.received_at as occurrence_received_at
                from provider_snapshot snapshot
                join provider_snapshot_occurrence occurrence
                  on occurrence.snapshot_id = snapshot.id
                where snapshot.request_key like 'J7_UPGRADE|%'
                order by snapshot.logical_endpoint
                """)).containsExactlyElementsOf(snapshotsBeforeV23);
        assertThat(upgradeJdbc.queryForList("""
                select event.provider, event.provider_event_id,
                       observation.source_kind, observation.source_reference,
                       observation.source_snapshot_id,
                       observation.source_payload_sha256,
                       observation.parser_version, observation.source_received_at,
                       observation.home_team_name, observation.away_team_name,
                       observation.status_type, observation.tournament_name,
                       observation.normalized_sha256
                from canonical_event event
                join canonical_event_observation observation
                  on observation.canonical_event_id = event.id
                where event.id = ?
                """, canonicalEventId)).containsExactlyElementsOf(eventStateBeforeV23);
        assertThat(upgradeJdbc.queryForList("""
                select source_kind, source_reference, source_snapshot_id,
                       source_payload_sha256, parser_version, source_received_at,
                       venue_name, venue_city, season_name, event_round,
                       normalized_sha256
                from event_detail_observation
                where canonical_event_id = ?
                """, canonicalEventId)).containsExactlyElementsOf(detailsBeforeV23);
        assertThat(upgradeJdbc.queryForList("""
                select endpoint_type, source_kind, source_reference,
                       source_snapshot_id, source_payload_sha256, parser_version,
                       source_received_at, completeness_status,
                       completeness_score, present_signals, expected_signals,
                       missing_paths_json::text as missing_paths_json,
                       lineups_confirmed, normalized_sha256
                from j5_event_data_observation
                where canonical_event_id = ?
                order by endpoint_type
                """, canonicalEventId)).containsExactlyElementsOf(j5BeforeV23);
        assertThat(upgradeJdbc.queryForList("""
                select metric_order, period, group_name, metric_code,
                       metric_name, home_value, away_value
                from j5_event_metric where observation_id = ?
                order by metric_order
                """, statisticsObservationId))
                .containsExactlyElementsOf(metricsBeforeV23);
        assertThat(upgradeJdbc.queryForList("""
                select incident_order, incident_type, minute, is_home,
                       participant_provider_id, player_provider_id, player_name,
                       home_score, away_score
                from j5_event_incident where observation_id = ?
                order by incident_order
                """, incidentsObservationId))
                .containsExactlyElementsOf(incidentsBeforeV23);
        assertThat(upgradeJdbc.queryForList("""
                select side.side, side.formation, player.player_order,
                       player.player_provider_id, player.player_name,
                       player.shirt_number, player.position, player.starter
                from j5_event_lineup_side side
                join j5_event_lineup_player player
                  on player.observation_id = side.observation_id
                 and player.side = side.side
                where side.observation_id = ?
                order by side.side, player.player_order
                """, lineupsObservationId))
                .containsExactlyElementsOf(lineupsBeforeV23);
        assertThat(upgradeJdbc.queryForMap("""
                select schema_version, export_path, content_sha256,
                       validation_status, source_snapshot_ids::text as source_snapshot_ids,
                       warnings::text as warnings
                from export_manifest
                where export_path = 'legacy-export.json'
                """)).containsAllEntriesOf(legacyBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select export_kind, export_uuid, canonical_event_id, schema_id,
                       generated_at, data_sha256, source_set_sha256,
                       candidate_content_sha256, content_size_bytes,
                       source_observations, decided_at, decision_reason,
                       decision_intent_status, decision_intent_at,
                       decision_intent_reason, decision_intent_path,
                       decision_intent_content_sha256,
                       decision_intent_content_size_bytes
                from export_manifest
                where export_path = 'legacy-export.json'
                """)).allSatisfy((column, value) -> assertThat(value).isNull());
        assertThat(upgradeJdbc.update("""
                update export_manifest
                set warnings = '["legacy", "still-generic"]'::jsonb
                where export_path = 'legacy-export.json'
                """)).isEqualTo(1);

        UUID firstExportId = UUID.fromString(
                "72000000-0000-0000-0000-000000000007");
        insertJ7Candidate(
                upgradeJdbc,
                canonicalEventId,
                firstExportId,
                "b".repeat(64),
                "c".repeat(64),
                "d".repeat(64));
        Map<String, Object> candidate = upgradeJdbc.queryForMap("""
                select export_kind, schema_id, schema_version, validation_status,
                       data_sha256, source_set_sha256, candidate_content_sha256,
                       content_sha256, content_size_bytes, export_path,
                       source_observations::text as source_observations,
                       source_snapshot_ids, warnings::text as warnings
                from export_manifest
                where export_uuid = ?
                """, firstExportId);
        assertThat(candidate)
                .containsEntry("export_kind", "J7_CANONICAL_EVENT")
                .containsEntry(
                        "schema_id",
                        "urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1")
                .containsEntry("schema_version", "1.0.0")
                .containsEntry("validation_status", "COHERENCE_CHECKED")
                .containsEntry("data_sha256", "b".repeat(64))
                .containsEntry("source_set_sha256", "c".repeat(64))
                .containsEntry("candidate_content_sha256", "d".repeat(64))
                .containsEntry("content_sha256", "d".repeat(64))
                .containsEntry("content_size_bytes", 1024L)
                .containsEntry(
                        "export_path",
                        "j7-" + canonicalEventId + "-" + firstExportId + ".candidate.json")
                .containsEntry("warnings", "[]");

        assertThatThrownBy(() -> insertJ7Candidate(
                upgradeJdbc,
                canonicalEventId,
                UUID.fromString("73000000-0000-0000-0000-000000000007"),
                "e".repeat(64),
                "f".repeat(64),
                "1".repeat(64)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("uq_export_manifest_j7_pending");

        String validatedPath =
                "j7-" + canonicalEventId + "-" + firstExportId + ".validated.json";
        recordJ7DecisionIntent(
                upgradeJdbc,
                firstExportId,
                J7ExportStatus.HUMAN_VALIDATED,
                Instant.parse("2026-08-19T10:01:00Z"),
                null,
                validatedPath,
                "e".repeat(64),
                1100);
        assertThat(upgradeJdbc.update("""
                update export_manifest
                set export_path = ?,
                    content_sha256 = ?,
                    content_size_bytes = 1100,
                    validation_status = 'HUMAN_VALIDATED',
                    decided_at = '2026-08-19T10:01:00Z',
                    decision_reason = null
                where export_uuid = ?
                """, validatedPath, "e".repeat(64), firstExportId)).isEqualTo(1);
        assertThat(upgradeJdbc.queryForMap("""
                select validation_status, export_path, content_sha256,
                       candidate_content_sha256, content_size_bytes, decided_at,
                       decision_reason
                from export_manifest
                where export_uuid = ?
                """, firstExportId))
                .containsEntry("validation_status", "HUMAN_VALIDATED")
                .containsEntry("export_path", validatedPath)
                .containsEntry("content_sha256", "e".repeat(64))
                .containsEntry("candidate_content_sha256", "d".repeat(64))
                .containsEntry("content_size_bytes", 1100L)
                .containsEntry("decision_reason", null);

        assertThatThrownBy(() -> upgradeJdbc.update("""
                update export_manifest
                set validation_status = 'REJECTED',
                    export_path = ?,
                    content_sha256 = ?,
                    decided_at = '2026-08-19T10:02:00Z',
                    decision_reason = 'second terminal decision'
                where export_uuid = ?
                """,
                "j7-" + canonicalEventId + "-" + firstExportId + ".rejected.json",
                "f".repeat(64),
                firstExportId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("J7 export terminal transition is not allowed");
        assertThatThrownBy(() -> upgradeJdbc.update(
                "delete from export_manifest where export_uuid = ?",
                firstExportId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("J7 export manifests cannot be deleted");

        UUID duplicateDataExportId = UUID.fromString(
                "74000000-0000-0000-0000-000000000007");
        insertJ7Candidate(
                upgradeJdbc,
                canonicalEventId,
                duplicateDataExportId,
                "b".repeat(64),
                "2".repeat(64),
                "3".repeat(64));
        String duplicateValidatedPath =
                "j7-" + canonicalEventId + "-" + duplicateDataExportId + ".validated.json";
        recordJ7DecisionIntent(
                upgradeJdbc,
                duplicateDataExportId,
                J7ExportStatus.HUMAN_VALIDATED,
                Instant.parse("2026-08-19T10:03:00Z"),
                null,
                duplicateValidatedPath,
                "4".repeat(64),
                1200);
        assertThatThrownBy(() -> upgradeJdbc.update("""
                update export_manifest
                set export_path = ?,
                    content_sha256 = ?,
                    content_size_bytes = 1200,
                    validation_status = 'HUMAN_VALIDATED',
                    decided_at = '2026-08-19T10:03:00Z'
                where export_uuid = ?
                """,
                duplicateValidatedPath,
                "4".repeat(64),
                duplicateDataExportId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("uq_export_manifest_j7_validated_data");
        assertThatThrownBy(() -> upgradeJdbc.update("""
                update export_manifest
                set data_sha256 = ?,
                    export_path = ?,
                    content_sha256 = ?,
                    content_size_bytes = 1200,
                    validation_status = 'REJECTED',
                    decided_at = '2026-08-19T10:03:00Z',
                    decision_reason = 'immutable evidence mutation'
                where export_uuid = ?
                """,
                "5".repeat(64),
                "j7-" + canonicalEventId + "-" + duplicateDataExportId + ".rejected.json",
                "6".repeat(64),
                duplicateDataExportId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("J7 export immutable evidence cannot be changed");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistsAndReloadsTheCompleteJ7CandidateRejectAndValidationLifecycle()
            throws Exception {
        long providerEventId = 17670022L;
        var startsAt = Instant.parse("2026-08-20T16:00:00Z");
        var homeTeam = new ScheduledTeam(111L, "J7 Synthetic Home");
        var awayTeam = new ScheduledTeam(112L, "J7 Synthetic Away");
        var status = new ScheduledEventStatus("scheduled", Optional.empty());
        var tournament = Optional.of(new ScheduledTournament(
                113L,
                "J7 Synthetic League"));
        var details = new EventDetails(
                providerEventId,
                startsAt,
                homeTeam,
                awayTeam,
                status,
                tournament,
                Optional.of(new EventVenue(
                        114L,
                        "J7 Synthetic Stadium",
                        Optional.of("Local City"))),
                Optional.of(new EventSeason(115L, "2026/2027")),
                Optional.of("8"));
        var stateSource = EventSourceTrace.syntheticFixture(
                "j7-lifecycle-state",
                "1".repeat(64),
                "scheduled-events-v1",
                Instant.parse("2026-08-19T10:00:00Z"));
        var eventPersistence = canonicalEventStore.save(CanonicalEventObservation.from(
                details.asScheduledEvent(),
                stateSource));
        UUID canonicalEventId = eventPersistence.canonicalEventId();
        var identity = canonicalEventStore.findLatestByCanonicalId(canonicalEventId)
                .orElseThrow()
                .identity();
        eventDetailsStore.save(EventDetailObservation.from(
                identity,
                details,
                EventSourceTrace.syntheticFixture(
                        "j7-lifecycle-details",
                        "2".repeat(64),
                        "event-details-v1",
                        Instant.parse("2026-08-19T10:01:00Z"))));
        j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                new EventStatistics(providerEventId, List.of()),
                EventSourceTrace.syntheticFixture(
                        "j7-lifecycle-statistics",
                        "3".repeat(64),
                        "event-statistics-v1",
                        Instant.parse("2026-08-19T10:02:00Z")),
                J5CompletenessReport.emptyValid()));
        j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                new EventIncidents(providerEventId, List.of()),
                EventSourceTrace.syntheticFixture(
                        "j7-lifecycle-incidents",
                        "4".repeat(64),
                        "event-incidents-v1",
                        Instant.parse("2026-08-19T10:03:00Z")),
                J5CompletenessReport.emptyValid()));
        j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                new EventLineups(
                        providerEventId,
                        false,
                        new TeamLineup(LineupSide.HOME, Optional.empty(), List.of()),
                        new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of())),
                EventSourceTrace.syntheticFixture(
                        "j7-lifecycle-lineups",
                        "5".repeat(64),
                        "event-lineups-v1",
                        Instant.parse("2026-08-19T10:04:00Z")),
                J5CompletenessReport.emptyValid()));
        Path exportDirectory = Path.of("target/integration-test-exports")
                .toAbsolutePath()
                .normalize();

        var firstCandidate = j7CanonicalExportService.createCandidate(
                canonicalEventId);
        Path firstCandidatePath = exportDirectory.resolve(firstCandidate.relativePath());
        assertThat(firstCandidate.status()).isEqualTo(J7ExportStatus.COHERENCE_CHECKED);
        assertThat(Files.isRegularFile(firstCandidatePath)).isTrue();
        assertThat(j7CanonicalExportService.preview(
                canonicalEventId,
                firstCandidate.exportId()).prettyJson())
                .contains("\"EVENT_STATE\"")
                .contains("\"SYNTHETIC_SOURCE\"")
                .doesNotContain("payload_raw", "requestUri", "sessionId", ".env");

        String rejectionReason = "Rejet local de qualification J7";
        var rejected = j7CanonicalExportService.reject(
                canonicalEventId,
                firstCandidate.exportId(),
                "REJETER EXPORT J7 " + firstCandidate.exportId(),
                rejectionReason);
        Path rejectedPath = exportDirectory.resolve(rejected.relativePath());
        assertThat(rejected.status()).isEqualTo(J7ExportStatus.REJECTED);
        assertThat(rejected.decisionReason()).contains(rejectionReason);
        assertThat(rejected.candidateContentSha256())
                .isEqualTo(firstCandidate.candidateContentSha256());
        assertThat(rejected.dataSha256()).isEqualTo(firstCandidate.dataSha256());
        assertThat(Files.exists(firstCandidatePath)).isFalse();
        assertThat(Files.isRegularFile(rejectedPath)).isTrue();
        assertThatThrownBy(() -> j7CanonicalExportService.download(
                canonicalEventId,
                rejected.exportId()))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.NOT_DOWNLOADABLE));

        var secondCandidate = j7CanonicalExportService.createCandidate(
                canonicalEventId);
        Path secondCandidatePath = exportDirectory.resolve(secondCandidate.relativePath());
        var validated = j7CanonicalExportService.validate(
                canonicalEventId,
                secondCandidate.exportId(),
                "VALIDER EXPORT J7 " + secondCandidate.exportId()
                        + " " + secondCandidate.dataSha256());
        Path validatedPath = exportDirectory.resolve(validated.relativePath());
        var download = j7CanonicalExportService.download(
                canonicalEventId,
                validated.exportId());

        assertThat(validated.status()).isEqualTo(J7ExportStatus.HUMAN_VALIDATED);
        assertThat(validated.dataSha256()).isEqualTo(secondCandidate.dataSha256());
        assertThat(validated.candidateContentSha256())
                .isEqualTo(secondCandidate.candidateContentSha256());
        assertThat(validated.currentContentSha256())
                .isNotEqualTo(validated.candidateContentSha256());
        assertThat(Files.exists(secondCandidatePath)).isFalse();
        assertThat(Files.isRegularFile(validatedPath)).isTrue();
        assertThat(download.fileName()).isEqualTo(validated.relativePath());
        assertThat(download.sha256()).isEqualTo(validated.currentContentSha256());
        assertThat(com.bettingproject.sofascorelocal.security.Sha256.hex(download.content()))
                .isEqualTo(download.sha256());
        assertThat(j7ExportManifestStore.findByExportId(validated.exportId()))
                .contains(validated);
        assertThat(j7ExportManifestStore.findByCanonicalEventId(canonicalEventId))
                .extracting(item -> item.status())
                .containsExactly(
                        J7ExportStatus.HUMAN_VALIDATED,
                        J7ExportStatus.REJECTED);

        Files.deleteIfExists(rejectedPath);
        Files.deleteIfExists(validatedPath);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void j7DecisionLockBlocksEverySourceWriteUntilTheDecisionCommits()
            throws Exception {
        UUID canonicalEventId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?)
                """, canonicalEventId, 17670023L);

        assertSourceInsertWaitsForJ7Decision(
                canonicalEventId,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            insert into canonical_event_observation (
                                canonical_event_id,
                                source_kind,
                                source_reference,
                                source_snapshot_id,
                                source_fixture_id,
                                source_payload_sha256,
                                parser_version,
                                source_received_at,
                                starts_at,
                                home_team_provider_id,
                                home_team_name,
                                away_team_provider_id,
                                away_team_name,
                                status_type,
                                status_description,
                                tournament_provider_id,
                                tournament_name,
                                normalized_sha256
                            ) values (
                                ?, 'SYNTHETIC_FIXTURE', 'j7-lock-j4', null,
                                'j7-lock-j4', ?, 'scheduled-events-v1',
                                '2026-08-19T12:00:01Z', '2026-08-20T18:00:00Z',
                                101, 'J7 Lock Home', 102, 'J7 Lock Away',
                                'scheduled', null, 103, 'J7 Lock League', ?
                            )
                            """)) {
                        statement.setObject(1, canonicalEventId);
                        statement.setString(2, "a".repeat(64));
                        statement.setString(3, "b".repeat(64));
                        return statement.executeUpdate();
                    }
                },
                "canonical_event_observation");

        assertSourceInsertWaitsForJ7Decision(
                canonicalEventId,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            insert into event_detail_observation (
                                canonical_event_id,
                                source_fixture_id,
                                source_payload_sha256,
                                parser_version,
                                source_received_at,
                                starts_at,
                                home_team_provider_id,
                                home_team_name,
                                away_team_provider_id,
                                away_team_name,
                                status_type,
                                status_description,
                                tournament_provider_id,
                                tournament_name,
                                venue_provider_id,
                                venue_name,
                                venue_city,
                                season_provider_id,
                                season_name,
                                event_round,
                                normalized_sha256,
                                source_kind,
                                source_reference,
                                source_snapshot_id
                            ) values (
                                ?, 'j7-lock-detail', ?, 'event-details-v1',
                                '2026-08-19T12:00:02Z',
                                '2026-08-20T18:00:00Z',
                                101, 'J7 Lock Home', 102, 'J7 Lock Away',
                                'scheduled', null, 103, 'J7 Lock League',
                                104, 'J7 Lock Stadium', 'Paris',
                                105, '2026/2027', '1', ?,
                                'SYNTHETIC_FIXTURE', 'j7-lock-detail', null
                            )
                            """)) {
                        statement.setObject(1, canonicalEventId);
                        statement.setString(2, "e".repeat(64));
                        statement.setString(3, "f".repeat(64));
                        return statement.executeUpdate();
                    }
                },
                "event_detail_observation");

        assertSourceInsertWaitsForJ7Decision(
                canonicalEventId,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            insert into j5_event_data_observation (
                                canonical_event_id,
                                endpoint_type,
                                source_kind,
                                source_reference,
                                source_snapshot_id,
                                source_fixture_id,
                                source_payload_sha256,
                                parser_version,
                                source_received_at,
                                completeness_status,
                                completeness_score,
                                present_signals,
                                expected_signals,
                                missing_paths_json,
                                lineups_confirmed,
                                normalized_sha256
                            ) values (
                                ?, 'EVENT_STATISTICS', 'SYNTHETIC_FIXTURE',
                                'j7-lock-j5', null, 'j7-lock-j5', ?,
                                'event-statistics-v1', '2026-08-19T12:00:02Z',
                                'COMPLETE', 100, 2, 2, '[]'::jsonb, null, ?
                            )
                            """)) {
                        statement.setObject(1, canonicalEventId);
                        statement.setString(2, "c".repeat(64));
                        statement.setString(3, "d".repeat(64));
                        return statement.executeUpdate();
                    }
                },
                "j5_event_data_observation");

        Long statisticsObservationId = jdbcTemplate.queryForObject("""
                select id
                from j5_event_data_observation
                where canonical_event_id = ?
                  and endpoint_type = 'EVENT_STATISTICS'
                  and source_fixture_id = 'j7-lock-j5'
                """, Long.class, canonicalEventId);
        jdbcTemplate.update("""
                insert into j5_event_data_observation (
                    canonical_event_id,
                    endpoint_type,
                    source_kind,
                    source_reference,
                    source_snapshot_id,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    completeness_status,
                    completeness_score,
                    present_signals,
                    expected_signals,
                    missing_paths_json,
                    lineups_confirmed,
                    normalized_sha256
                ) values
                (
                    ?, 'EVENT_INCIDENTS', 'SYNTHETIC_FIXTURE',
                    'j7-lock-j5-incidents', null, 'j7-lock-j5-incidents',
                    ?, 'event-incidents-v4', '2026-08-19T12:00:03Z',
                    'COMPLETE', 100, 1, 1, '[]'::jsonb, null, ?
                ),
                (
                    ?, 'EVENT_LINEUPS', 'SYNTHETIC_FIXTURE',
                    'j7-lock-j5-lineups', null, 'j7-lock-j5-lineups',
                    ?, 'event-lineups-v2', '2026-08-19T12:00:04Z',
                    'COMPLETE', 100, 1, 1, '[]'::jsonb, true, ?
                )
                """,
                canonicalEventId,
                "6".repeat(64),
                "7".repeat(64),
                canonicalEventId,
                "8".repeat(64),
                "0".repeat(64));
        Long incidentsObservationId = jdbcTemplate.queryForObject("""
                select id
                from j5_event_data_observation
                where canonical_event_id = ?
                  and endpoint_type = 'EVENT_INCIDENTS'
                  and source_fixture_id = 'j7-lock-j5-incidents'
                """, Long.class, canonicalEventId);
        Long lineupsObservationId = jdbcTemplate.queryForObject("""
                select id
                from j5_event_data_observation
                where canonical_event_id = ?
                  and endpoint_type = 'EVENT_LINEUPS'
                  and source_fixture_id = 'j7-lock-j5-lineups'
                """, Long.class, canonicalEventId);

        assertJ5ChildInsertWaitsForJ7Decision(
                canonicalEventId,
                statisticsObservationId,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            insert into j5_event_metric (
                                observation_id, metric_order, period, group_name,
                                metric_code, metric_name, home_value, away_value
                            ) values (
                                ?, 7007, 'ALL', 'J7 lock group',
                                'j7LockMetric', 'J7 lock metric', '1', '0'
                            )
                            """)) {
                        statement.setLong(1, statisticsObservationId);
                        return statement.executeUpdate();
                    }
                },
                "j5_event_metric");
        assertJ5ChildInsertWaitsForJ7Decision(
                canonicalEventId,
                incidentsObservationId,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            insert into j5_event_incident (
                                observation_id, incident_order, incident_type,
                                minute, is_home, participant_provider_id,
                                player_provider_id, player_name,
                                home_score, away_score
                            ) values (
                                ?, 7007, 'goal', 70, true, 17670101,
                                17670301, 'J7 Lock Scorer', 1, 0
                            )
                            """)) {
                        statement.setLong(1, incidentsObservationId);
                        return statement.executeUpdate();
                    }
                },
                "j5_event_incident");
        assertJ5ChildInsertWaitsForJ7Decision(
                canonicalEventId,
                lineupsObservationId,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            insert into j5_event_lineup_side (
                                observation_id, side, formation
                            ) values (?, 'HOME', '4-3-3')
                            """)) {
                        statement.setLong(1, lineupsObservationId);
                        return statement.executeUpdate();
                    }
                },
                "j5_event_lineup_side");
        assertJ5ChildInsertWaitsForJ7Decision(
                canonicalEventId,
                lineupsObservationId,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            insert into j5_event_lineup_player (
                                observation_id, side, player_order,
                                player_provider_id, player_name, shirt_number,
                                position, starter
                            ) values (
                                ?, 'HOME', 7007, 17670401,
                                'J7 Lock Keeper', 1, 'G', true
                            )
                            """)) {
                        statement.setLong(1, lineupsObservationId);
                        return statement.executeUpdate();
                    }
                },
                "j5_event_lineup_player");
        assertInvisibleJ5ParentRejectsChildInsert(canonicalEventId);

        var mutableSourceSnapshot = saveProviderSnapshot(
                SofascoreEndpointType.EVENT_DETAILS,
                17670023L,
                EventDetailsV2Parser.PARSER_VERSION,
                Instant.parse("2026-08-19T12:00:04Z"),
                "{\"event\":{\"id\":17670023},\"lockQualification\":true}");
        jdbcTemplate.update("""
                insert into canonical_event_observation (
                    canonical_event_id, source_kind, source_reference,
                    source_snapshot_id, source_fixture_id,
                    source_payload_sha256, parser_version, source_received_at,
                    starts_at, home_team_provider_id, home_team_name,
                    away_team_provider_id, away_team_name, status_type,
                    status_description, tournament_provider_id,
                    tournament_name, normalized_sha256
                ) values (
                    ?, 'PROVIDER_SNAPSHOT', 'snapshot:' || ?::text, ?, null,
                    ?, 'event-details-v2', '2026-08-19T12:00:04Z',
                    '2026-08-20T18:00:00Z',
                    101, 'J7 Lock Home', 102, 'J7 Lock Away',
                    'scheduled', null, 103, 'J7 Lock League', ?
                )
                """,
                canonicalEventId,
                mutableSourceSnapshot.snapshotId(),
                mutableSourceSnapshot.snapshotId(),
                mutableSourceSnapshot.payloadSha256(),
                "9".repeat(64));
        assertProviderSnapshotMutationWaitsForJ7Decision(
                canonicalEventId,
                mutableSourceSnapshot.snapshotId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistsAndReloadsExactProviderSourcesWithoutReadingRawPayloads()
            throws Exception {
        long providerEventId = 17670024L;
        Instant detailsReceivedAt = Instant.parse("2026-08-19T11:00:00.100Z");
        var detailsSnapshot = saveProviderSnapshot(
                SofascoreEndpointType.EVENT_DETAILS,
                providerEventId,
                EventDetailsV2Parser.PARSER_VERSION,
                detailsReceivedAt,
                "{\"event\":{\"id\":17670024},"
                        + "\"privateMarker\":\"PROVIDER_RAW_MUST_NOT_LEAK_J7\"}");
        EventDetails details = new EventDetails(
                providerEventId,
                Instant.parse("2026-08-20T18:00:00Z"),
                new ScheduledTeam(201L, "Provider Home"),
                new ScheduledTeam(202L, "Provider Away"),
                new ScheduledEventStatus("scheduled", Optional.empty()),
                Optional.of(new ScheduledTournament(203L, "Provider League")),
                Optional.of(new EventVenue(
                        204L,
                        "Provider Stadium",
                        Optional.of("Paris"))),
                Optional.of(new EventSeason(205L, "2026/2027")),
                Optional.of("7"));
        EventSourceTrace detailsSource = EventSourceTrace.providerSnapshot(
                detailsSnapshot.snapshotId(),
                detailsSnapshot.payloadSha256(),
                EventDetailsV2Parser.PARSER_VERSION,
                detailsReceivedAt);
        var eventPersistence = canonicalEventStore.save(CanonicalEventObservation.from(
                details.asScheduledEvent(),
                detailsSource));
        var identity = canonicalEventStore.findLatestByCanonicalId(
                        eventPersistence.canonicalEventId())
                .orElseThrow()
                .identity();
        var detailPersistence = eventDetailsStore.save(EventDetailObservation.from(
                identity,
                details,
                detailsSource));
        snapshotStore.classify(
                detailsSnapshot.snapshotId(),
                RawSnapshotSchemaStatus.PARSED,
                null);

        Instant statisticsReceivedAt = Instant.parse("2026-08-19T11:01:00.100Z");
        String statisticsRaw = """
                {
                  "statistics":[{
                    "period":"ALL",
                    "groups":[{
                      "groupName":"Match overview",
                      "statisticsItems":[{
                        "key":"ballPossession",
                        "name":"Ball possession",
                        "home":"54%",
                        "away":"46%"
                      }]
                    }]
                  }],
                  "privateMarker":"PROVIDER_RAW_MUST_NOT_LEAK_J7"
                }
                """;
        RawPayloadEvidence statisticsPayload = RawPayloadEvidence.capture(
                statisticsRaw.getBytes(StandardCharsets.UTF_8));
        var statisticsSnapshot = saveProviderSnapshot(
                SofascoreEndpointType.EVENT_STATISTICS,
                providerEventId,
                EventStatisticsV2Parser.PARSER_VERSION,
                statisticsReceivedAt,
                statisticsRaw);
        var parsedStatistics = new EventStatisticsV2Parser().parse(
                statisticsSnapshot.snapshotId(),
                providerEventId,
                statisticsPayload,
                statisticsReceivedAt);
        var statisticsPersistence = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                parsedStatistics.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(
                        statisticsSnapshot.snapshotId(),
                        statisticsSnapshot.payloadSha256(),
                        EventStatisticsV2Parser.PARSER_VERSION,
                        statisticsReceivedAt),
                parsedStatistics.completeness().orElseThrow()));
        snapshotStore.classify(
                statisticsSnapshot.snapshotId(),
                RawSnapshotSchemaStatus.PARSED,
                null);

        Instant incidentsReceivedAt = Instant.parse("2026-08-19T11:02:00.100Z");
        String incidentsRaw = """
                {
                  "incidents":[{
                    "incidentType":"goal",
                    "time":32,
                    "isHome":true,
                    "teamId":201,
                    "player":{"id":301,"name":"Provider Scorer"},
                    "homeScore":1,
                    "awayScore":0
                  }],
                  "privateMarker":"PROVIDER_RAW_MUST_NOT_LEAK_J7"
                }
                """;
        RawPayloadEvidence incidentsPayload = RawPayloadEvidence.capture(
                incidentsRaw.getBytes(StandardCharsets.UTF_8));
        var incidentsSnapshot = saveProviderSnapshot(
                SofascoreEndpointType.EVENT_INCIDENTS,
                providerEventId,
                EventIncidentsV4Parser.PARSER_VERSION,
                incidentsReceivedAt,
                incidentsRaw);
        var parsedIncidents = new EventIncidentsV4Parser().parse(
                incidentsSnapshot.snapshotId(),
                providerEventId,
                incidentsPayload,
                incidentsReceivedAt);
        var incidentsPersistence = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                parsedIncidents.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(
                        incidentsSnapshot.snapshotId(),
                        incidentsSnapshot.payloadSha256(),
                        EventIncidentsV4Parser.PARSER_VERSION,
                        incidentsReceivedAt),
                parsedIncidents.completeness().orElseThrow()));
        snapshotStore.classify(
                incidentsSnapshot.snapshotId(),
                RawSnapshotSchemaStatus.PARSED,
                null);

        Instant lineupsReceivedAt = Instant.parse("2026-08-19T11:03:00.100Z");
        String lineupsRaw = """
                {
                  "confirmed":true,
                  "home":{
                    "formation":"4-3-3",
                    "players":[{
                      "player":{"id":401,"name":"Provider Home Keeper"},
                      "shirtNumber":1,
                      "position":"G",
                      "substitute":false
                    }]
                  },
                  "away":{
                    "formation":"4-4-2",
                    "players":[{
                      "player":{"id":402,"name":"Provider Away Keeper"},
                      "shirtNumber":1,
                      "position":"G",
                      "substitute":false
                    }]
                  },
                  "privateMarker":"PROVIDER_RAW_MUST_NOT_LEAK_J7"
                }
                """;
        RawPayloadEvidence lineupsPayload = RawPayloadEvidence.capture(
                lineupsRaw.getBytes(StandardCharsets.UTF_8));
        var lineupsSnapshot = saveProviderSnapshot(
                SofascoreEndpointType.EVENT_LINEUPS,
                providerEventId,
                EventLineupsV2Parser.PARSER_VERSION,
                lineupsReceivedAt,
                lineupsRaw);
        var parsedLineups = new EventLineupsV2Parser().parse(
                lineupsSnapshot.snapshotId(),
                providerEventId,
                lineupsPayload,
                lineupsReceivedAt);
        var lineupsPersistence = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                parsedLineups.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(
                        lineupsSnapshot.snapshotId(),
                        lineupsSnapshot.payloadSha256(),
                        EventLineupsV2Parser.PARSER_VERSION,
                        lineupsReceivedAt),
                parsedLineups.completeness().orElseThrow()));
        snapshotStore.classify(
                lineupsSnapshot.snapshotId(),
                RawSnapshotSchemaStatus.PARSED,
                null);

        var currentEvent = canonicalEventStore.findLatestByCanonicalId(
                        eventPersistence.canonicalEventId())
                .orElseThrow();
        var currentDetails = eventDetailsStore.findLatest(
                        eventPersistence.canonicalEventId())
                .orElseThrow();
        var currentJ5 = j5EventDataStore.findLatest(eventPersistence.canonicalEventId());
        assertThat(parsedStatistics.completeness().orElseThrow().status())
                .isEqualTo(J5CompletenessStatus.COMPLETE);
        assertThat(parsedIncidents.completeness().orElseThrow().status())
                .isEqualTo(J5CompletenessStatus.COMPLETE);
        assertThat(parsedLineups.completeness().orElseThrow().status())
                .isEqualTo(J5CompletenessStatus.COMPLETE);
        assertThat(currentJ5.statistics()).hasValueSatisfying(observation -> {
            assertThat(observation.completeness().status())
                    .isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(observation.data()).isInstanceOfSatisfying(
                    EventStatistics.class,
                    statistics -> assertThat(statistics.metrics()).singleElement()
                            .satisfies(metric -> {
                                assertThat(metric.metricCode())
                                        .isEqualTo("ballPossession");
                                assertThat(metric.homeValue()).contains("54%");
                                assertThat(metric.awayValue()).contains("46%");
                            }));
        });
        assertThat(currentJ5.incidents()).hasValueSatisfying(observation -> {
            assertThat(observation.completeness().status())
                    .isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(observation.data()).isInstanceOfSatisfying(
                    EventIncidents.class,
                    incidents -> assertThat(incidents.incidents()).singleElement()
                            .satisfies(incident -> {
                                assertThat(incident.incidentType()).isEqualTo("goal");
                                assertThat(incident.minute()).contains(32);
                                assertThat(incident.playerName())
                                        .contains("Provider Scorer");
                            }));
        });
        assertThat(currentJ5.lineups()).hasValueSatisfying(observation -> {
            assertThat(observation.completeness().status())
                    .isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(observation.data()).isInstanceOfSatisfying(
                    EventLineups.class,
                    lineups -> {
                        assertThat(lineups.confirmed()).isTrue();
                        assertThat(lineups.home().formation()).contains("4-3-3");
                        assertThat(lineups.home().players()).singleElement()
                                .satisfies(player -> assertThat(player.name())
                                        .isEqualTo("Provider Home Keeper"));
                        assertThat(lineups.away().formation()).contains("4-4-2");
                        assertThat(lineups.away().players()).singleElement()
                                .satisfies(player -> assertThat(player.name())
                                        .isEqualTo("Provider Away Keeper"));
                    });
        });
        var candidate = j7CanonicalExportService.createCandidate(
                eventPersistence.canonicalEventId());
        var reloaded = j7ExportManifestStore.findByExportId(candidate.exportId())
                .orElseThrow();

        List<Long> expectedSnapshotIds = List.of(
                        detailsSnapshot.snapshotId(),
                        statisticsSnapshot.snapshotId(),
                        incidentsSnapshot.snapshotId(),
                        lineupsSnapshot.snapshotId())
                .stream()
                .sorted()
                .toList();
        assertThat(candidate.sourceSnapshotIds()).containsExactlyElementsOf(expectedSnapshotIds);
        assertThat(reloaded).isEqualTo(candidate);
        assertThat(reloaded.sourceSnapshotIds()).containsExactlyElementsOf(expectedSnapshotIds);
        assertThat(reloaded.warningsJson()).isEqualTo("[]");

        JsonNode sources = JsonMapper.builder().build()
                .readTree(reloaded.sourceObservationsJson());
        assertThat(sources.valueStream()
                .map(source -> source.get("component").stringValue())
                .toList())
                .containsExactly(
                        "EVENT_STATE",
                        "EVENT_DETAILS",
                        "EVENT_STATISTICS",
                        "EVENT_INCIDENTS",
                        "EVENT_LINEUPS");
        assertProviderSource(
                sources.get(0),
                "EVENT_STATE",
                eventPersistence.observationId(),
                detailsSnapshot.snapshotId(),
                detailsSnapshot.payloadSha256(),
                currentEvent.normalizedSha256(),
                EventDetailsV2Parser.PARSER_VERSION,
                detailsReceivedAt,
                "PRESENT",
                Optional.empty());
        assertProviderSource(
                sources.get(1),
                "EVENT_DETAILS",
                detailPersistence.observationId(),
                detailsSnapshot.snapshotId(),
                detailsSnapshot.payloadSha256(),
                currentDetails.normalizedSha256(),
                EventDetailsV2Parser.PARSER_VERSION,
                detailsReceivedAt,
                "PRESENT",
                Optional.empty());
        assertProviderSource(
                sources.get(2),
                "EVENT_STATISTICS",
                statisticsPersistence.observationId(),
                statisticsSnapshot.snapshotId(),
                statisticsSnapshot.payloadSha256(),
                currentJ5.statistics().orElseThrow().normalizedSha256(),
                EventStatisticsV2Parser.PARSER_VERSION,
                statisticsReceivedAt,
                "PRESENT",
                parsedStatistics.completeness());
        assertProviderSource(
                sources.get(3),
                "EVENT_INCIDENTS",
                incidentsPersistence.observationId(),
                incidentsSnapshot.snapshotId(),
                incidentsSnapshot.payloadSha256(),
                currentJ5.incidents().orElseThrow().normalizedSha256(),
                EventIncidentsV4Parser.PARSER_VERSION,
                incidentsReceivedAt,
                "PRESENT",
                parsedIncidents.completeness());
        assertProviderSource(
                sources.get(4),
                "EVENT_LINEUPS",
                lineupsPersistence.observationId(),
                lineupsSnapshot.snapshotId(),
                lineupsSnapshot.payloadSha256(),
                currentJ5.lineups().orElseThrow().normalizedSha256(),
                EventLineupsV2Parser.PARSER_VERSION,
                lineupsReceivedAt,
                "PRESENT",
                parsedLineups.completeness());

        Map<Long, String> expectedSourceHashes = Map.of(
                detailsSnapshot.snapshotId(), detailsSnapshot.payloadSha256(),
                statisticsSnapshot.snapshotId(), statisticsSnapshot.payloadSha256(),
                incidentsSnapshot.snapshotId(), incidentsSnapshot.payloadSha256(),
                lineupsSnapshot.snapshotId(), lineupsSnapshot.payloadSha256());
        assertThat(j6SnapshotHistoryStore.findTraces(Set.copyOf(expectedSnapshotIds)))
                .hasSize(4)
                .allSatisfy((snapshotId, trace) -> {
                    assertThat(trace.payloadSha256())
                            .isEqualTo(expectedSourceHashes.get(snapshotId));
                    assertThat(trace.rawPayloadState().name()).isEqualTo("RETAINED");
                    assertThat(trace.occurrenceCount()).isEqualTo(1);
                });

        String preview = j7CanonicalExportService.preview(
                eventPersistence.canonicalEventId(),
                candidate.exportId()).prettyJson();
        JsonNode exported = JsonMapper.builder().build().readTree(preview);
        JsonNode exportedData = exported.get("data");
        assertThat(exportedData.get("statistics").get("availability").stringValue())
                .isEqualTo("PRESENT");
        assertThat(exportedData.get("statistics").get("completeness")
                .get("status").stringValue()).isEqualTo("COMPLETE");
        assertThat(exportedData.get("statistics").get("metrics").get(0)
                .get("metricCode").stringValue()).isEqualTo("ballPossession");
        assertThat(exportedData.get("statistics").get("metrics").get(0)
                .get("homeValue").stringValue()).isEqualTo("54%");
        assertThat(exportedData.get("incidents").get("availability").stringValue())
                .isEqualTo("PRESENT");
        assertThat(exportedData.get("incidents").get("completeness")
                .get("status").stringValue()).isEqualTo("COMPLETE");
        assertThat(exportedData.get("incidents").get("incidents").get(0)
                .get("playerName").stringValue()).isEqualTo("Provider Scorer");
        assertThat(exportedData.get("lineups").get("availability").stringValue())
                .isEqualTo("PRESENT");
        assertThat(exportedData.get("lineups").get("completeness")
                .get("status").stringValue()).isEqualTo("COMPLETE");
        assertThat(exportedData.get("lineups").get("lineups").get("home")
                .get("players").get(0).get("name").stringValue())
                .isEqualTo("Provider Home Keeper");
        assertThat(exportedData.get("lineups").get("lineups").get("away")
                .get("formation").stringValue()).isEqualTo("4-4-2");
        assertThat(preview)
                .contains("\"sourceKind\" : \"PROVIDER_SNAPSHOT\"")
                .doesNotContain("PROVIDER_RAW_MUST_NOT_LEAK_J7", "payload_raw");

        var rejected = j7CanonicalExportService.reject(
                eventPersistence.canonicalEventId(),
                candidate.exportId(),
                "REJETER EXPORT J7 " + candidate.exportId(),
                "Fin du test PostgreSQL fournisseur J7");
        Files.deleteIfExists(Path.of("target/integration-test-exports")
                .toAbsolutePath()
                .normalize()
                .resolve(rejected.relativePath()));
    }

    @Test
    void executesJ6BackupFingerprintQueriesAgainstTheMigratedSchema() throws Exception {
        String script = Files.readString(
                Path.of("scripts", "Backup-Restore-J6.ps1"),
                StandardCharsets.UTF_8);

        assertThat(jdbcTemplate.queryForObject(
                powerShellHereString(script, "$flywaySql"),
                String.class)).isEqualTo("38");
        assertThat(jdbcTemplate.queryForObject(
                powerShellHereString(script, "$snapshotFingerprintSql"),
                String.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                powerShellHereString(script, "$occurrenceFingerprintSql"),
                String.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                powerShellHereString(script, "$normalizedFingerprintSql"),
                String.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                powerShellHereString(script, "$j8BenchmarkFingerprintSql"),
                String.class)).isNotNull();
        String deliveryLedgerFingerprintSql =
                powerShellHereString(script, "$j7DeliveryLedgerFingerprintSql");
        assertThat(jdbcTemplate.queryForObject(
                deliveryLedgerFingerprintSql,
                String.class)).isNotNull();
        assertThat(deliveryLedgerFingerprintSql)
                .contains("from j7_delivery delivery")
                .contains("from j7_delivery_attempt attempt")
                .contains("from j7_delivery_attempt_result attempt_result")
                .contains("from j7_provider_delivery_owner_go_grant owner_go")
                .contains("from j7_provider_delivery_owner_go_revocation revocation")
                .contains("from j7_provider_delivery_owner_go_consumption consumption")
                .doesNotContain("export_manifest", "provider_snapshot", "payload_raw");
        assertThat(script)
                .contains("j8CampaignCount")
                .contains("j8UnitCount")
                .contains("j8ProviderAttemptCount")
                .contains("j8UnitResultCount")
                .contains("j8CampaignResultCount")
                .contains("j7DeliveryCount")
                .contains("j7DeliveryAttemptCount")
                .contains("j7DeliveryAttemptResultCount")
                .contains("j7ProviderOwnerGoGrantCount")
                .contains("j7ProviderOwnerGoRevocationCount")
                .contains("j7ProviderOwnerGoConsumptionCount")
                .contains("j7DeliveryLedgerSha256")
                .contains("$sourceFlywayVersion -cne '38'");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void restoresJ8AndJ7DeliveryEvidenceWithIdenticalFingerprints() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sourceDatabase = "j8_backup_src_" + suffix;
        String restoreDatabase = "j8_backup_dst_" + suffix;
        String dumpPath = "/tmp/j8-backup-" + suffix + ".dump";
        String script = Files.readString(
                Path.of("scripts", "Backup-Restore-J6.ps1"),
                StandardCharsets.UTF_8);
        String j8FingerprintSql =
                powerShellHereString(script, "$j8BenchmarkFingerprintSql");
        String j7DeliveryFingerprintSql =
                powerShellHereString(script, "$j7DeliveryLedgerFingerprintSql");

        assertContainerCommandSucceeded(POSTGRES.execInContainer(
                "createdb",
                "--username", POSTGRES.getUsername(),
                sourceDatabase));
        try {
            DataSource sourceDataSource = databaseDataSource(sourceDatabase);
            Flyway sourceFlyway = Flyway.configure()
                    .dataSource(sourceDataSource)
                    .locations("classpath:db/migration")
                    .load();
            assertThat(sourceFlyway.migrate().migrationsExecuted).isEqualTo(38);
            assertThat(sourceFlyway.info().current().getVersion().getVersion()).isEqualTo("38");

            JdbcTemplate sourceJdbc = new JdbcTemplate(sourceDataSource);
            UUID campaignId = UUID.randomUUID();
            Instant startedAt = Instant.parse("2026-08-29T12:00:00Z");
            sourceJdbc.update("""
                    insert into j8_benchmark_campaign (
                        campaign_id, campaign_type, execution_mode, started_at,
                        maximum_units, collection_date
                    ) values (?, 'J3_SCHEDULED_EVENTS', 'GUARDED_PROVIDER', ?, 25, ?)
                    """, campaignId, Timestamp.from(startedAt), LocalDate.of(2026, 8, 29));
            Long unitId = sourceJdbc.queryForObject("""
                    insert into j8_benchmark_unit (
                        campaign_id, unit_ordinal, logical_endpoint, request_key,
                        declared_at
                    ) values (?, 1, 'SCHEDULED_EVENTS',
                              'SCHEDULED_EVENTS|date=2026-08-29|page=1', ?)
                    returning id
                    """, Long.class, campaignId, Timestamp.from(startedAt.plusSeconds(1)));
            Long attemptId = sourceJdbc.queryForObject("""
                    insert into j8_provider_call_attempt (unit_id, started_at)
                    values (?, ?)
                    returning id
                    """, Long.class, unitId, Timestamp.from(startedAt.plusSeconds(2)));
            sourceJdbc.update("""
                    insert into j8_benchmark_unit_result (
                        unit_id, attempt_id, resolved_at, resolution_source, outcome_type,
                        response_received, parser_warning_count, terminal_code
                    ) values (?, ?, ?, 'PROVIDER', 'TRANSPORT_FAILURE', false, 0,
                              'BACKUP_RESTORE_TEST')
                    """, unitId, attemptId, Timestamp.from(startedAt.plusSeconds(3)));
            sourceJdbc.update("""
                    insert into j8_benchmark_campaign_result (
                        campaign_id, finished_at, terminal_state, terminal_code,
                        completed_units
                    ) values (?, ?, 'FAILED', 'BACKUP_RESTORE_TEST', 1)
                    """, campaignId, Timestamp.from(startedAt.plusSeconds(4)));

            UUID canonicalEventId = UUID.randomUUID();
            UUID exportId = UUID.randomUUID();
            String dataSha256 = "a".repeat(64);
            String sourceSetSha256 = "b".repeat(64);
            String candidateContentSha256 = "c".repeat(64);
            String validatedContentSha256 = "d".repeat(64);
            sourceJdbc.update("""
                    insert into canonical_event (id, provider, provider_event_id)
                    values (?, 'SOFASCORE', 19999999)
                    """, canonicalEventId);
            Long detailObservationId = sourceJdbc.queryForObject("""
                    insert into event_detail_observation (
                        canonical_event_id, source_kind, source_reference, source_fixture_id,
                        source_payload_sha256, parser_version, source_received_at,
                        starts_at, home_team_provider_id, home_team_name,
                        away_team_provider_id, away_team_name, status_type,
                        normalized_sha256, is_awarded, home_display_score, away_display_score
                    ) values (
                        ?, 'SYNTHETIC_FIXTURE', 'j6-v38-display-roundtrip', 'j6-v38-display-roundtrip',
                        repeat('e', 64), 'event-details-v3', ?, ?,
                        19999991, 'Synthetic Home', 19999992, 'Synthetic Away', 'finished',
                        repeat('f', 64), true, 0, 3
                    ) returning id
                    """, Long.class, canonicalEventId, Timestamp.from(startedAt), Timestamp.from(startedAt));
            // The manifest fingerprint remains provenance/hash based. Compare the three V38
            // business values separately so a restored zero cannot silently become null.
            String j4DisplayEvidenceSql = """
                    select id, canonical_event_id, source_kind, source_reference,
                        source_fixture_id, source_snapshot_id, source_payload_sha256,
                        parser_version, source_received_at, normalized_sha256,
                        is_awarded, home_display_score, away_display_score
                    from event_detail_observation where id = ?
                    """;
            Map<String, Object> sourceJ4DisplayEvidence =
                    sourceJdbc.queryForMap(j4DisplayEvidenceSql, detailObservationId);
            assertThat(sourceJ4DisplayEvidence)
                    .containsEntry("parser_version", "event-details-v3")
                    .containsEntry("is_awarded", true)
                    .containsEntry("home_display_score", 0)
                    .containsEntry("away_display_score", 3);
            insertJ7Candidate(
                    sourceJdbc,
                    canonicalEventId,
                    exportId,
                    dataSha256,
                    sourceSetSha256,
                    candidateContentSha256,
                    """
                    [
                      {"component":"EVENT_STATE","availability":"PRESENT","sourceKind":"PROVIDER_SNAPSHOT","snapshotId":null},
                      {"component":"EVENT_DETAILS","availability":"PRESENT","sourceKind":"PROVIDER_SNAPSHOT","snapshotId":null},
                      {"component":"EVENT_STATISTICS","availability":"UNAVAILABLE","sourceKind":"PROVIDER_SNAPSHOT","snapshotId":null},
                      {"component":"EVENT_INCIDENTS","availability":"MISSING","sourceKind":null,"snapshotId":null},
                      {"component":"EVENT_LINEUPS","availability":"MISSING","sourceKind":null,"snapshotId":null}
                    ]
                    """);
            Instant validatedAt = startedAt.plusSeconds(5);
            String validatedPath =
                    "j7-" + canonicalEventId + "-" + exportId + ".validated.json";
            recordJ7DecisionIntent(
                    sourceJdbc,
                    exportId,
                    J7ExportStatus.HUMAN_VALIDATED,
                    validatedAt,
                    null,
                    validatedPath,
                    validatedContentSha256,
                    2048);
            assertThat(sourceJdbc.update("""
                    update export_manifest
                    set export_path = ?,
                        content_sha256 = ?,
                        content_size_bytes = 2048,
                        validation_status = 'HUMAN_VALIDATED',
                        decided_at = ?,
                        decision_reason = null
                    where export_uuid = ?
                    """,
                    validatedPath,
                    validatedContentSha256,
                    Timestamp.from(validatedAt),
                    exportId)).isOne();
            String idempotencyKey = "j7:" + exportId
                    + ":sha256:" + validatedContentSha256;
            JdbcJ7DeliveryLedgerStore sourceStore = new JdbcJ7DeliveryLedgerStore(
                    new NamedParameterJdbcTemplate(sourceDataSource));
            TransactionTemplate sourceTransaction = new TransactionTemplate(
                    new JdbcTransactionManager(sourceDataSource));
            Instant sourceNow = sourceJdbc.queryForObject(
                    "select clock_timestamp()", java.time.OffsetDateTime.class).toInstant();
            J7ProviderDerivedOwnerGo.Grant consumedGrant = backupProviderOwnerGoGrant(
                    canonicalEventId,
                    19_999_999L,
                    exportId,
                    validatedContentSha256,
                    dataSha256,
                    sourceNow,
                    1);
            J7DeliveryLedgerStore.ClaimReceipt providerClaim = sourceTransaction.execute(status -> {
                sourceStore.registerProviderDerivedOwnerGo(consumedGrant);
                return sourceStore.claimProviderDerived(new J7ProviderDerivedOwnerGo.Claim(
                        consumedGrant, idempotencyKey, sourceNow));
            });
            assertThat(providerClaim).isNotNull();
            sourceTransaction.executeWithoutResult(status -> sourceStore.complete(
                    providerClaim.deliveryId(),
                    1,
                    J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                    OptionalInt.of(422),
                    "BACKUP_RESTORE_TEST",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    providerClaim.startedAt().plusSeconds(1)));
            J7ProviderDerivedOwnerGo.Grant revokedGrant = backupProviderOwnerGoGrant(
                    canonicalEventId,
                    19_999_999L,
                    exportId,
                    validatedContentSha256,
                    dataSha256,
                    sourceNow,
                    2);
            sourceTransaction.executeWithoutResult(status -> {
                sourceStore.registerProviderDerivedOwnerGo(revokedGrant);
                sourceStore.revokeProviderDerivedOwnerGo(
                        revokedGrant.reference(), "f".repeat(64));
            });

            String sourceJ8Fingerprint =
                    sourceJdbc.queryForObject(j8FingerprintSql, String.class);
            String sourceJ7DeliveryFingerprint =
                    sourceJdbc.queryForObject(j7DeliveryFingerprintSql, String.class);
            assertThat(sourceJ8Fingerprint).isNotBlank();
            assertThat(sourceJ7DeliveryFingerprint).isNotBlank();
            for (String table : List.of(
                    "j8_benchmark_campaign",
                    "j8_benchmark_unit",
                    "j8_provider_call_attempt",
                    "j8_benchmark_unit_result",
                    "j8_benchmark_campaign_result")) {
                assertThat(sourceJdbc.queryForObject(
                        "select count(*) from " + table,
                        Long.class)).isOne();
            }
            for (String table : List.of(
                    "j7_delivery",
                    "j7_delivery_attempt",
                    "j7_delivery_attempt_result")) {
                assertThat(sourceJdbc.queryForObject(
                        "select count(*) from " + table,
                        Long.class)).isOne();
            }
            assertThat(sourceJdbc.queryForObject(
                    "select count(*) from j7_provider_delivery_owner_go_grant",
                    Long.class)).isEqualTo(2L);
            assertThat(sourceJdbc.queryForObject(
                    "select count(*) from j7_provider_delivery_owner_go_revocation",
                    Long.class)).isOne();
            assertThat(sourceJdbc.queryForObject(
                    "select count(*) from j7_provider_delivery_owner_go_consumption",
                    Long.class)).isOne();
            assertThat(sourceJdbc.queryForObject("""
                    select count(*)
                    from information_schema.columns
                    where table_schema = 'public'
                      and table_name in (
                          'j7_delivery',
                          'j7_delivery_attempt',
                          'j7_delivery_attempt_result'
                      )
                      and (
                          data_type in ('bytea', 'json', 'jsonb', 'text')
                          or (
                              column_name ~
                                  '(payload|body|content|cookie|token|secret|private_key|certificate|diagnostic)'
                              and column_name <> 'payload_class'
                          )
                      )
                    """, Long.class)).isZero();

            assertContainerCommandSucceeded(POSTGRES.execInContainer(
                    "pg_dump",
                    "--username", POSTGRES.getUsername(),
                    "--dbname", sourceDatabase,
                    "--format=custom",
                    "--no-owner",
                    "--no-privileges",
                    "--file", dumpPath));
            assertContainerCommandSucceeded(POSTGRES.execInContainer(
                    "createdb",
                    "--username", POSTGRES.getUsername(),
                    restoreDatabase));
            assertContainerCommandSucceeded(POSTGRES.execInContainer(
                    "pg_restore",
                    "--username", POSTGRES.getUsername(),
                    "--dbname", restoreDatabase,
                    "--exit-on-error",
                    "--no-owner",
                    "--no-privileges",
                    dumpPath));

            DataSource restoreDataSource = databaseDataSource(restoreDatabase);
            JdbcTemplate restoreJdbc = new JdbcTemplate(restoreDataSource);
            assertThat(restoreJdbc.queryForObject(
                    """
                    select version
                    from flyway_schema_history
                    where success and version is not null
                    order by installed_rank desc
                    limit 1
                    """,
                    String.class)).isEqualTo("38");
            assertThat(restoreJdbc.queryForObject(j8FingerprintSql, String.class))
                    .isEqualTo(sourceJ8Fingerprint);
            assertThat(restoreJdbc.queryForObject(
                    j7DeliveryFingerprintSql,
                    String.class)).isEqualTo(sourceJ7DeliveryFingerprint);
            assertThat(restoreJdbc.queryForMap(j4DisplayEvidenceSql, detailObservationId))
                    .isEqualTo(sourceJ4DisplayEvidence);
            for (String table : List.of(
                    "j8_benchmark_campaign",
                    "j8_benchmark_unit",
                    "j8_provider_call_attempt",
                    "j8_benchmark_unit_result",
                    "j8_benchmark_campaign_result")) {
                assertThat(restoreJdbc.queryForObject(
                        "select count(*) from " + table,
                        Long.class)).isOne();
            }
            for (String table : List.of(
                    "j7_delivery",
                    "j7_delivery_attempt",
                    "j7_delivery_attempt_result")) {
                assertThat(restoreJdbc.queryForObject(
                        "select count(*) from " + table,
                        Long.class)).isOne();
            }
            assertThat(restoreJdbc.queryForObject(
                    "select count(*) from j7_provider_delivery_owner_go_grant",
                    Long.class)).isEqualTo(2L);
            assertThat(restoreJdbc.queryForObject(
                    "select count(*) from j7_provider_delivery_owner_go_revocation",
                    Long.class)).isOne();
            assertThat(restoreJdbc.queryForObject(
                    "select count(*) from j7_provider_delivery_owner_go_consumption",
                    Long.class)).isOne();
        }
        finally {
            assertContainerCommandSucceeded(POSTGRES.execInContainer(
                    "dropdb",
                    "--username", POSTGRES.getUsername(),
                    "--if-exists",
                    "--force",
                    restoreDatabase));
            assertContainerCommandSucceeded(POSTGRES.execInContainer(
                    "dropdb",
                    "--username", POSTGRES.getUsername(),
                    "--if-exists",
                    "--force",
                    sourceDatabase));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void keepsJ8PopulationStableInsideAConcurrentPostgresqlRepeatableReadSnapshot()
            throws Exception {
        UUID campaignId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-08-29T12:30:00Z");

        try (Connection reader = dataSource.getConnection();
             Connection writer = dataSource.getConnection()) {
            reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            reader.setReadOnly(true);
            reader.setAutoCommit(false);
            assertThat(reader.getTransactionIsolation())
                    .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
            assertThat(countJ8Campaign(reader, campaignId)).isZero();

            writer.setAutoCommit(false);
            try (PreparedStatement insert = writer.prepareStatement("""
                    insert into j8_benchmark_campaign (
                        campaign_id, campaign_type, execution_mode, started_at,
                        maximum_units, collection_date
                    ) values (?, 'J3_SCHEDULED_EVENTS', 'GUARDED_PROVIDER', ?, 25, ?)
                    """)) {
                insert.setObject(1, campaignId);
                insert.setTimestamp(2, Timestamp.from(startedAt));
                insert.setObject(3, LocalDate.of(2026, 8, 29));
                assertThat(insert.executeUpdate()).isOne();
            }
            writer.commit();

            assertThat(countJ8Campaign(reader, campaignId)).isZero();
            reader.commit();
        }

        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j8_benchmark_campaign
                where campaign_id = ?
                """, Long.class, campaignId)).isOne();
    }

    @Test
    void j6RetentionLauncherRequiresCurrentFlywayAndDisablesTournamentDiscovery() throws Exception {
        String script = Files.readString(
                Path.of("scripts", "Invoke-J6Retention.ps1"),
                StandardCharsets.UTF_8);

        assertThat(script)
                .contains("$manifest.source.flywayVersion.ToString() -cne '38'")
                .contains("valid Flyway V38 raw-payload, J8, J7 and quiescent live ledger restore");

        String qualificationFields = powerShellArray(script, "$qualificationFields");
        assertThat(qualificationFields)
                .contains("'j7DeliveryCount'")
                .contains("'j7DeliveryAttemptCount'")
                .contains("'j7DeliveryAttemptResultCount'")
                .contains("'j7ProviderOwnerGoGrantCount'")
                .contains("'j7ProviderOwnerGoRevocationCount'")
                .contains("'j7ProviderOwnerGoConsumptionCount'")
                .contains("'j7DeliveryLedgerSha256'");

        String environmentNames = powerShellArray(script, "$environmentNames");
        assertThat(environmentNames)
                .contains("'SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED'");

        Pattern forcedFalseNames = Pattern.compile(
                "foreach\\s*\\(\\$name\\s+in\\s+@\\((.*?)\\)\\)\\s*\\{\\R"
                        + "\\s*Set-ProcessEnvironment\\s+-Name\\s+\\$name"
                        + "\\s+-Value\\s+'false'",
                Pattern.DOTALL);
        Matcher forcedFalseMatcher = forcedFalseNames.matcher(script);
        assertThat(forcedFalseMatcher.find()).isTrue();
        assertThat(forcedFalseMatcher.group(1))
                .contains("'SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED'");
    }

    private static String powerShellHereString(String script, String variableName) {
        Pattern assignment = Pattern.compile(
                "^" + Pattern.quote(variableName) + "\\s*=\\s*@'\\R(.*?)\\R'@$",
                Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = assignment.matcher(script);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "PowerShell here-string not found: " + variableName);
        }
        return matcher.group(1);
    }

    private static String powerShellArray(String script, String variableName) {
        Pattern assignment = Pattern.compile(
                "^\\s*" + Pattern.quote(variableName)
                        + "\\s*=\\s*@\\(\\R(.*?)^\\s*\\)$",
                Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = assignment.matcher(script);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "PowerShell array not found: " + variableName);
        }
        return matcher.group(1);
    }

    private static DataSource databaseDataSource(String databaseName) {
        String sourceSegment = "/" + POSTGRES.getDatabaseName();
        String targetSegment = "/" + databaseName;
        String jdbcUrl = POSTGRES.getJdbcUrl();
        assertThat(jdbcUrl).contains(sourceSegment);
        return new DriverManagerDataSource(
                jdbcUrl.replace(sourceSegment, targetSegment),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private static void assertContainerCommandSucceeded(
            org.testcontainers.containers.Container.ExecResult result) {
        assertThat(result.getExitCode())
                .as("container command stderr: %s; stdout: %s",
                        result.getStderr(),
                        result.getStdout())
                .isZero();
    }

    private static long countJ8Campaign(Connection connection, UUID campaignId)
            throws Exception {
        try (PreparedStatement query = connection.prepareStatement("""
                select count(*)
                from j8_benchmark_campaign
                where campaign_id = ?
                """)) {
            query.setObject(1, campaignId);
            try (var result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private static String parserConstraint(JdbcTemplate jdbcTemplate, String schema) {
        return jdbcTemplate.queryForObject(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where connamespace = ?::regnamespace
                  and conname = 'ck_j5_event_data_parser'
                """,
                String.class,
                schema);
    }

    @Test
    void upgradesAPrepopulatedV5EventDetailToV6WithoutRewritingHistory() {
        String schema = "upgrade_v5_to_v6";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flywayV5 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("5"))
                .load();

        assertThat(flywayV5.migrate().migrationsExecuted).isEqualTo(5);

        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                insert into canonical_event (
                    id,
                    provider,
                    provider_event_id,
                    created_at
                ) values (
                    '11111111-1111-1111-1111-111111111111',
                    'SOFASCORE',
                    900001,
                    '2026-08-12T12:00:00Z'
                )
                """);
        upgradeJdbc.update("""
                insert into canonical_event_observation (
                    canonical_event_id,
                    source_kind,
                    source_reference,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    starts_at,
                    home_team_provider_id,
                    home_team_name,
                    away_team_provider_id,
                    away_team_name,
                    status_type,
                    status_description,
                    tournament_provider_id,
                    tournament_name,
                    normalized_sha256,
                    created_at
                ) values (
                    '11111111-1111-1111-1111-111111111111',
                    'SYNTHETIC_FIXTURE',
                    'scheduled-events-nominal',
                    'scheduled-events-nominal',
                    repeat('a', 64),
                    'scheduled-events-v1',
                    '2026-08-12T12:00:01Z',
                    '2026-08-12T14:00:00Z',
                    101,
                    'Synthetic Home FC',
                    202,
                    'Synthetic Away FC',
                    'notstarted',
                    'Not started',
                    301,
                    'Synthetic League',
                    repeat('b', 64),
                    '2026-08-12T12:00:02Z'
                )
                """);
        upgradeJdbc.update("""
                insert into event_detail_observation (
                    canonical_event_id,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    starts_at,
                    home_team_provider_id,
                    home_team_name,
                    away_team_provider_id,
                    away_team_name,
                    status_type,
                    status_description,
                    tournament_provider_id,
                    tournament_name,
                    venue_provider_id,
                    venue_name,
                    venue_city,
                    season_provider_id,
                    season_name,
                    event_round,
                    normalized_sha256,
                    created_at
                ) values (
                    '11111111-1111-1111-1111-111111111111',
                    'event-details-nominal',
                    repeat('c', 64),
                    'event-details-v1',
                    '2026-08-12T12:00:03Z',
                    '2026-08-12T14:00:00Z',
                    101,
                    'Synthetic Home FC',
                    202,
                    'Synthetic Away FC',
                    'notstarted',
                    'Not started',
                    301,
                    'Synthetic League',
                    401,
                    'Synthetic Park',
                    'Local City',
                    501,
                    '2026',
                    '1',
                    repeat('d', 64),
                    '2026-08-12T12:00:04Z'
                )
                """);

        Map<String, Object> canonicalBefore = upgradeJdbc.queryForMap(
                "select * from canonical_event_observation");
        Map<String, Object> detailBefore = upgradeJdbc.queryForMap("""
                select
                    id,
                    canonical_event_id,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    starts_at,
                    home_team_provider_id,
                    home_team_name,
                    away_team_provider_id,
                    away_team_name,
                    status_type,
                    status_description,
                    tournament_provider_id,
                    tournament_name,
                    venue_provider_id,
                    venue_name,
                    venue_city,
                    season_provider_id,
                    season_name,
                    event_round,
                    normalized_sha256,
                    created_at
                from event_detail_observation
                """);

        Flyway flywayV6 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("6"))
                .load();

        assertThat(flywayV6.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flywayV6.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from canonical_event", Long.class)).isEqualTo(1L);
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from canonical_event_observation", Long.class)).isEqualTo(1L);
        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from event_detail_observation", Long.class)).isEqualTo(1L);
        assertThat(upgradeJdbc.queryForMap(
                "select * from canonical_event_observation"))
                .isEqualTo(canonicalBefore);
        assertThat(upgradeJdbc.queryForMap("""
                select
                    id,
                    canonical_event_id,
                    source_fixture_id,
                    source_payload_sha256,
                    parser_version,
                    source_received_at,
                    starts_at,
                    home_team_provider_id,
                    home_team_name,
                    away_team_provider_id,
                    away_team_name,
                    status_type,
                    status_description,
                    tournament_provider_id,
                    tournament_name,
                    venue_provider_id,
                    venue_name,
                    venue_city,
                    season_provider_id,
                    season_name,
                    event_round,
                    normalized_sha256,
                    created_at
                from event_detail_observation
                """))
                .isEqualTo(detailBefore);
        assertThat(upgradeJdbc.queryForObject(
                "select source_kind from event_detail_observation", String.class))
                .isEqualTo("SYNTHETIC_FIXTURE");
        assertThat(upgradeJdbc.queryForObject(
                "select source_reference from event_detail_observation", String.class))
                .isEqualTo("event-details-nominal");
        assertThat(upgradeJdbc.queryForObject(
                "select source_snapshot_id is null from event_detail_observation", Boolean.class))
                .isTrue();
        assertThat(upgradeJdbc.queryForObject("""
                select tgenabled::text
                from pg_trigger
                where tgrelid = 'event_detail_observation'::regclass
                  and tgname = 'event_detail_observation_append_only'
                """, String.class))
                .isEqualTo("O");
        assertThatThrownBy(() -> upgradeJdbc.update(
                "update event_detail_observation set status_type = 'changed'"))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("canonical_event_observation is append-only");
        assertThatThrownBy(() -> upgradeJdbc.update(
                "delete from event_detail_observation"))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("canonical_event_observation is append-only");
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
    void rebuildsJ3TournamentCatalogAfterPostgresRoundsReceivedAtToMicroseconds() {
        LocalDate collectionDate = LocalDate.parse("2026-08-20");
        String requestKey = "SCHEDULED_EVENTS|date=" + collectionDate + "|page=1";
        Instant receivedAt = Instant.parse("2026-08-20T11:18:19.049495700Z");
        Instant requestedAt = receivedAt.minusMillis(20);
        byte[] rawPayload = """
                {
                  "scheduled": [
                    {
                      "tournament": {
                        "id": 119880,
                        "name": "UEFA Champions League, Playoff Round",
                        "category": {
                          "name": "Europe"
                        },
                        "uniqueTournament": {
                          "id": 7,
                          "name": "UEFA Champions League"
                        }
                      },
                      "timezoneEventCount": {"7200": 1}
                    }
                  ],
                  "hasNextPage": false
                }
                """.getBytes(StandardCharsets.UTF_8);
        RawPayloadEvidence payload = RawPayloadEvidence.capture(rawPayload);
        ScheduledEventsTransportResponse response = new ScheduledEventsTransportResponse(
                requestKey,
                requestedAt,
                receivedAt,
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(20),
                payload);
        RawSnapshotPersistenceResult persisted = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                requestKey,
                requestedAt,
                receivedAt,
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(20),
                payload,
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                null));
        J3MinimizedPageEvidence pageEvidence = J3MinimizedPageEvidence.recorded(
                1,
                response,
                persisted,
                RawSnapshotSchemaStatus.PARSED,
                false,
                null);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        evidenceService.publish(new J3MinimizedCollectionEvidence(
                collectionDate,
                J3ManualCallIntentState.COMPLETED,
                0,
                1,
                null,
                "NONE",
                receivedAt.plusSeconds(1),
                true,
                J3CircuitState.LOCKED,
                J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK,
                Duration.ofMinutes(10),
                List.of(pageEvidence)));
        J3TournamentCatalogService catalogService = new J3TournamentCatalogService(
                evidenceService,
                snapshotInspectionStore);

        Instant persistedReceivedAt = snapshotInspectionStore
                .findById(persisted.snapshotId())
                .orElseThrow()
                .summary()
                .receivedAt();
        var catalog = catalogService.latest();

        assertThat(persistedReceivedAt).isNotEqualTo(receivedAt);
        assertThat(Duration.between(persistedReceivedAt, receivedAt).abs())
                .isLessThan(Duration.ofNanos(1_000));
        assertThat(catalog.status()).isEqualTo(J3TournamentCatalogStatus.AVAILABLE);
        assertThat(catalog.options()).singleElement().satisfies(option -> {
            assertThat(option.tournamentId()).isEqualTo(119880L);
            assertThat(option.tournamentCategoryName()).isEqualTo("Europe");
            assertThat(option.displayLabel())
                    .isEqualTo("UEFA Champions League, Playoff Round - Europe");
            assertThat(option.uniqueTournamentId()).isEqualTo(7L);
            assertThat(option.sourceSnapshotIds()).containsExactly(persisted.snapshotId());
        });
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
        assertThat(inserted.occurrenceId()).isPresent();
        assertThat(duplicate.occurrenceId()).isPresent();
        assertThat(duplicate.occurrenceId().orElseThrow())
                .isNotEqualTo(inserted.occurrenceId().orElseThrow());
        assertThat(duplicate.snapshotId()).isEqualTo(inserted.snapshotId());
        assertThat(secondVersion.outcome()).isEqualTo(RawSnapshotPersistenceOutcome.INSERTED);
        assertThat(secondVersion.snapshotId()).isNotEqualTo(inserted.snapshotId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from provider_snapshot where request_key = ?",
                Long.class,
                requestKey)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForList("""
                select persistence_outcome
                from provider_snapshot_occurrence occurrence
                join provider_snapshot snapshot on snapshot.id = occurrence.snapshot_id
                where snapshot.request_key = ?
                order by occurrence.id
                """, String.class, requestKey))
                .containsExactly("INSERTED", "DEDUPLICATED", "INSERTED");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from provider_snapshot_occurrence
                where snapshot_id = ?
                """, Long.class, inserted.snapshotId())).isEqualTo(2L);
        assertThat(j6SnapshotHistoryStore.findTraces(Set.of(
                inserted.snapshotId(),
                secondVersion.snapshotId())))
                .hasEntrySatisfying(inserted.snapshotId(), trace -> {
                    assertThat(trace.payloadSha256()).isEqualTo(inserted.payloadSha256());
                    assertThat(trace.occurrenceCount()).isEqualTo(2);
                    assertThat(trace.deduplicatedOccurrenceCount()).isEqualTo(1);
                    assertThat(trace.latestOutcome().name()).isEqualTo("DEDUPLICATED");
                    assertThat(trace.rawPayloadState().name()).isEqualTo("RETAINED");
                })
                .hasEntrySatisfying(secondVersion.snapshotId(), trace -> {
                    assertThat(trace.payloadSha256()).isEqualTo(secondVersion.payloadSha256());
                    assertThat(trace.occurrenceCount()).isEqualTo(1);
                });
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
        assertThat(canonicalEventStore.findByObservationId(
                first.canonicalEventId(),
                first.observationId()))
                .hasValueSatisfying(view ->
                        assertThat(view.homeTeam().name()).isEqualTo("Local FC"));
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

    @Test
    void importsOneOfflineEventDetailIdempotentlyWithoutCreatingProviderSnapshots() {
        Long snapshotsBefore = snapshotRowCount();

        var first = j4OfflineFixtureImportService.importNominalCorpus();
        var repeated = j4OfflineFixtureImportService.importNominalCorpus();

        assertThat(first.scheduledObservationInserted()).isTrue();
        assertThat(first.detailEventObservationInserted()).isTrue();
        assertThat(first.detailInserted()).isTrue();
        assertThat(first.canonicalObservationCount()).isEqualTo(2L);
        assertThat(repeated.scheduledObservationInserted()).isFalse();
        assertThat(repeated.detailEventObservationInserted()).isFalse();
        assertThat(repeated.detailInserted()).isFalse();
        assertThat(repeated.canonicalObservationCount()).isEqualTo(2L);
        assertThat(snapshotRowCount()).isEqualTo(snapshotsBefore);

        assertThat(canonicalEventStore.findHistory(first.canonicalEventId()))
                .hasSize(2)
                .extracting(view -> view.source().fixtureId().orElseThrow())
                .containsExactly("event-details-nominal", "scheduled-events-nominal");
        assertThat(eventDetailsStore.findLatest(first.canonicalEventId()))
                .hasValueSatisfying(detail -> {
                    assertThat(detail.details().providerEventId()).isEqualTo(900001L);
                    assertThat(detail.details().venue()).hasValueSatisfying(venue ->
                            assertThat(venue.name()).isEqualTo("Synthetic Park"));
                    assertThat(detail.details().season()).hasValueSatisfying(season ->
                            assertThat(season.name()).isEqualTo("2026"));
                    assertThat(detail.details().round()).contains("1");
                    assertThat(detail.source().fixtureId())
                            .contains("event-details-nominal");
                    assertThat(detail.source().payloadSha256()).hasSize(64);
                });
        assertThat(eventDetailsStore.findHistory(first.canonicalEventId()))
                .singleElement()
                .satisfies(detail -> assertThat(eventDetailsStore.findByObservationId(
                        first.canonicalEventId(),
                        detail.observationId())).contains(detail));
        assertThat(j4EventQueryService.search(
                LocalDate.parse("2026-08-12"),
                "Europe/Paris").events())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.event().identity().value())
                            .isEqualTo(first.canonicalEventId());
                    assertThat(item.startsAtInZone().toString())
                            .isEqualTo("2026-08-12T16:00+02:00[Europe/Paris]");
                    assertThat(item.event().observationCount()).isEqualTo(2L);
                });
        assertThat(j4EventQueryService.findDetail(
                first.canonicalEventId(),
                "Europe/Paris"))
                .hasValueSatisfying(detail -> {
                    assertThat(detail.history()).hasSize(2);
                    assertThat(detail.offlineDetail()).isPresent();
                });

        Long detailObservationId = jdbcTemplate.queryForObject(
                "select id from event_detail_observation where canonical_event_id = ?",
                Long.class,
                first.canonicalEventId());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from event_detail_observation where id = ?",
                detailObservationId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("canonical_event_observation is append-only");
    }

    @Test
    void persistsJ5FamiliesIdempotentlyAndSelectsTheLatestCompletenessEvidence() {
        Long snapshotsBefore = snapshotRowCount();
        var j4Import = j4OfflineFixtureImportService.importNominalCorpus();

        var first = j5OfflineFixtureImportService.importNominalCorpus(
                j4Import.canonicalEventId());
        var repeated = j5OfflineFixtureImportService.importNominalCorpus(
                j4Import.canonicalEventId());

        assertThat(first.statisticsInserted()).isTrue();
        assertThat(first.incidentsInserted()).isTrue();
        assertThat(first.lineupsInserted()).isTrue();
        assertThat(repeated.statisticsInserted()).isFalse();
        assertThat(repeated.incidentsInserted()).isFalse();
        assertThat(repeated.lineupsInserted()).isFalse();
        assertThat(first.statisticsCompleteness()).isEqualTo(J5CompletenessStatus.COMPLETE);
        assertThat(first.incidentsCompleteness()).isEqualTo(J5CompletenessStatus.COMPLETE);
        assertThat(first.lineupsCompleteness()).isEqualTo(J5CompletenessStatus.COMPLETE);
        assertThat(snapshotRowCount()).isEqualTo(snapshotsBefore);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_data_observation
                where canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_metric metric
                join j5_event_data_observation observation
                  on observation.id = metric.observation_id
                where observation.canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_incident incident
                join j5_event_data_observation observation
                  on observation.id = incident.observation_id
                where observation.canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_lineup_side lineup_side
                join j5_event_data_observation observation
                  on observation.id = lineup_side.observation_id
                where observation.canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_lineup_player player
                join j5_event_data_observation observation
                  on observation.id = player.observation_id
                where observation.canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(4L);

        var nominalBundle = j5EventDataStore.findLatest(j4Import.canonicalEventId());
        assertThat(nominalBundle.statistics()).hasValueSatisfying(observation -> {
            assertThat(observation.completeness().status())
                    .isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(observation.source().fixtureId())
                    .contains("event-statistics-nominal");
            assertThat(observation.data()).isInstanceOfSatisfying(
                    EventStatistics.class,
                    statistics -> assertThat(statistics.metrics()).hasSize(3));
        });
        assertThat(nominalBundle.incidents()).hasValueSatisfying(observation ->
                assertThat(observation.data()).isInstanceOfSatisfying(
                        EventIncidents.class,
                        incidents -> assertThat(incidents.incidents()).hasSize(3)));
        assertThat(nominalBundle.lineups()).hasValueSatisfying(observation ->
                assertThat(observation.data()).isInstanceOfSatisfying(
                        EventLineups.class,
                        lineups -> {
                            assertThat(lineups.confirmed()).isTrue();
                            assertThat(lineups.home().players()).hasSize(2);
                            assertThat(lineups.away().players()).hasSize(2);
                        }));

        var identity = canonicalEventStore.findLatestByCanonicalId(
                        j4Import.canonicalEventId())
                .orElseThrow()
                .identity();
        var fixtureLoader = new ClasspathFixtureLoader();
        LoadedFixture partialStatisticsFixture = fixtureLoader.load(
                "fixtures/event-statistics/partial-missing-away.manifest.json");
        var partialStatistics = new EventStatisticsV1Parser().parse(
                partialStatisticsFixture);
        var partialStatisticsPersistence = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                partialStatistics.data().orElseThrow(),
                fixtureSource(partialStatisticsFixture),
                partialStatistics.completeness().orElseThrow()));

        LoadedFixture emptyIncidentsFixture = fixtureLoader.load(
                "fixtures/event-incidents/empty.manifest.json");
        var emptyIncidents = new EventIncidentsV1Parser().parse(emptyIncidentsFixture);
        j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                emptyIncidents.data().orElseThrow(),
                fixtureSource(emptyIncidentsFixture),
                emptyIncidents.completeness().orElseThrow()));

        LoadedFixture partialLineupsFixture = fixtureLoader.load(
                "fixtures/event-lineups/partial-unconfirmed.manifest.json");
        var partialLineups = new EventLineupsV1Parser().parse(partialLineupsFixture);
        j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                partialLineups.data().orElseThrow(),
                fixtureSource(partialLineupsFixture),
                partialLineups.completeness().orElseThrow()));

        var latestBundle = j5EventDataStore.findLatest(j4Import.canonicalEventId());
        assertThat(latestBundle.statistics()).hasValueSatisfying(observation -> {
            assertThat(observation.completeness().status())
                    .isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(observation.completeness().scorePercent()).isEqualTo(75);
            assertThat(observation.completeness().missingPaths()).isNotEmpty();
            assertThat(observation.data()).isInstanceOfSatisfying(
                    EventStatistics.class,
                    statistics -> assertThat(statistics.metrics()).hasSize(2));
        });
        assertThat(latestBundle.incidents()).hasValueSatisfying(observation -> {
            assertThat(observation.completeness().status())
                    .isEqualTo(J5CompletenessStatus.EMPTY_VALID);
            assertThat(observation.data()).isInstanceOfSatisfying(
                    EventIncidents.class,
                    incidents -> assertThat(incidents.incidents()).isEmpty());
        });
        assertThat(latestBundle.lineups()).hasValueSatisfying(observation -> {
            assertThat(observation.completeness().status())
                    .isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(observation.data()).isInstanceOfSatisfying(
                    EventLineups.class,
                    lineups -> {
                        assertThat(lineups.confirmed()).isFalse();
                        assertThat(lineups.home().players()).hasSize(1);
                        assertThat(lineups.away().players()).isEmpty();
                    });
        });
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_data_observation
                where canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(6L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_metric metric
                join j5_event_data_observation observation
                  on observation.id = metric.observation_id
                where observation.canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_incident incident
                join j5_event_data_observation observation
                  on observation.id = incident.observation_id
                where observation.canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_lineup_side lineup_side
                join j5_event_data_observation observation
                  on observation.id = lineup_side.observation_id
                where observation.canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from j5_event_lineup_player player
                join j5_event_data_observation observation
                  on observation.id = player.observation_id
                where observation.canonical_event_id = ?
                """,
                Long.class,
                j4Import.canonicalEventId())).isEqualTo(5L);
        assertThat(j5EventDataStore.findHistory(
                j4Import.canonicalEventId(),
                SofascoreEndpointType.EVENT_STATISTICS))
                .hasSize(2)
                .extracting(view -> view.observationId())
                .containsExactly(
                        partialStatisticsPersistence.observationId(),
                        first.statisticsObservationId());
        assertThat(j5EventDataStore.findByObservationId(
                j4Import.canonicalEventId(),
                SofascoreEndpointType.EVENT_STATISTICS,
                partialStatisticsPersistence.observationId()))
                .hasValueSatisfying(view -> assertThat(view.completeness().status())
                        .isEqualTo(J5CompletenessStatus.PARTIAL));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update j5_event_data_observation set completeness_score = 99 where id = ?",
                first.statisticsObservationId()))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("J5 normalized event data is append-only");
    }

    @Test
    void persistsAProviderJ5ObservationWithV2ParserAndSnapshotProvenance() {
        var j4Import = j4OfflineFixtureImportService.importNominalCorpus();
        long eventId = 900001L;
        Instant requestedAt = Instant.parse("2026-08-15T14:00:00Z");
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{\"statistics\":[]}".getBytes(StandardCharsets.UTF_8));
        var raw = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_STATISTICS,
                "EVENT_STATISTICS|eventId=900001",
                requestedAt,
                requestedAt.plusMillis(100),
                200,
                "application/json",
                Duration.ofMillis(100),
                payload,
                EventStatisticsV2Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        var parsed = new EventStatisticsV2Parser().parse(
                raw.snapshotId(), eventId, payload, requestedAt.plusMillis(100));
        var identity = canonicalEventStore.findLatestByCanonicalId(
                        j4Import.canonicalEventId())
                .orElseThrow()
                .identity();

        var persisted = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                parsed.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(
                        raw.snapshotId(),
                        raw.payloadSha256(),
                        EventStatisticsV2Parser.PARSER_VERSION,
                        requestedAt.plusMillis(100)),
                parsed.completeness().orElseThrow()));
        snapshotStore.classify(raw.snapshotId(), RawSnapshotSchemaStatus.PARSED, null);

        assertThat(persisted.inserted()).isTrue();
        assertThat(schemaStatus(raw.snapshotId())).isEqualTo("PARSED");
        assertThat(j5EventDataStore.findLatest(j4Import.canonicalEventId()).statistics())
                .hasValueSatisfying(observation -> {
                    assertThat(observation.source().snapshotId()).hasValue(raw.snapshotId());
                    assertThat(observation.source().parserVersion())
                            .isEqualTo(EventStatisticsV2Parser.PARSER_VERSION);
                    assertThat(observation.completeness().status())
                            .isEqualTo(J5CompletenessStatus.EMPTY_VALID);
                });
    }

    @Test
    void reparsesDeduplicatedHistoricalIncidentsWithSubstitutionPlayers()
            throws Exception {
        var j4Import = j4OfflineFixtureImportService.importNominalCorpus();
        long eventId = 900001L;
        Instant requestedAt = Instant.parse("2026-08-15T23:29:59Z");
        RawPayloadEvidence payload = RawPayloadEvidence.capture("""
                {"incidents":[{
                  "incidentType":"substitution",
                  "time":83,
                  "isHome":true,
                  "playerIn":{"id":2443678,"name":"Synthetic Incoming Player"},
                  "playerOut":{"id":2119385,"name":"Synthetic Outgoing Player"}
                }]}
                """.getBytes(StandardCharsets.UTF_8));
        String requestKey = "EVENT_INCIDENTS|eventId=900001";
        var historical = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_INCIDENTS,
                requestKey,
                requestedAt.minusSeconds(60),
                requestedAt.minusSeconds(60).plusMillis(100),
                200,
                "application/json",
                Duration.ofMillis(100),
                payload,
                EventIncidentsV2Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE"));
        var deduplicated = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_INCIDENTS,
                requestKey,
                requestedAt,
                requestedAt.plusMillis(100),
                200,
                "application/json",
                Duration.ofMillis(100),
                payload,
                EventIncidentsV4Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        var parsed = new EventIncidentsV4Parser().parse(
                deduplicated.snapshotId(), eventId, payload, requestedAt.plusMillis(100));
        var identity = canonicalEventStore.findLatestByCanonicalId(
                        j4Import.canonicalEventId())
                .orElseThrow()
                .identity();

        var persisted = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                parsed.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(
                        deduplicated.snapshotId(),
                        deduplicated.payloadSha256(),
                        EventIncidentsV4Parser.PARSER_VERSION,
                        requestedAt.plusMillis(100)),
                parsed.completeness().orElseThrow()));

        assertThat(deduplicated.outcome())
                .isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(deduplicated.snapshotId()).isEqualTo(historical.snapshotId());
        assertThat(persisted.inserted()).isTrue();
        assertThat(schemaStatus(historical.snapshotId()))
                .isEqualTo("SCHEMA_INCOMPATIBLE");
        assertThat(jdbcTemplate.queryForMap(
                """
                select
                    player_in_provider_id,
                    player_in_name,
                    player_out_provider_id,
                    player_out_name
                from j5_event_incident
                where observation_id = ?
                """,
                persisted.observationId()))
                .containsEntry("player_in_provider_id", 2443678L)
                .containsEntry("player_in_name", "Synthetic Incoming Player")
                .containsEntry("player_out_provider_id", 2119385L)
                .containsEntry("player_out_name", "Synthetic Outgoing Player");
        assertThat(j5EventDataStore.findLatest(j4Import.canonicalEventId()).incidents())
                .hasValueSatisfying(observation -> {
                    assertThat(observation.source().parserVersion())
                            .isEqualTo(EventIncidentsV4Parser.PARSER_VERSION);
                    assertThat(observation.completeness().status())
                            .isEqualTo(J5CompletenessStatus.COMPLETE);
                    assertThat(observation.data()).isInstanceOfSatisfying(
                            EventIncidents.class,
                            incidents -> {
                                var substitution = incidents.incidents().getFirst();
                                assertThat(substitution.playerInName())
                                        .contains("Synthetic Incoming Player");
                                assertThat(substitution.playerOutName())
                                        .contains("Synthetic Outgoing Player");
                            });
                });
    }

    @Test
    void reparsesADeduplicatedBenchIncidentWithProviderMinuteAndReason() {
        var j4Import = j4OfflineFixtureImportService.importNominalCorpus();
        long eventId = 900001L;
        Instant requestedAt = Instant.parse("2026-08-16T09:16:58Z");
        RawPayloadEvidence payload = RawPayloadEvidence.capture("""
                {"incidents":[{
                  "incidentType":"card",
                  "incidentClass":"yellow",
                  "time":-5,
                  "benchTime":58,
                  "reversedPeriodTime":6,
                  "isHome":false,
                  "reason":"Argument",
                  "player":{"id":1053241,"name":"Provider Player"}
                }]}
                """.getBytes(StandardCharsets.UTF_8));
        String requestKey = "EVENT_INCIDENTS|eventId=900001";
        var historical = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_INCIDENTS,
                requestKey,
                requestedAt.minusSeconds(60),
                requestedAt.minusSeconds(60).plusMillis(100),
                200,
                "application/json",
                Duration.ofMillis(100),
                payload,
                EventIncidentsV4Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE"));
        var deduplicated = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_INCIDENTS,
                requestKey,
                requestedAt,
                requestedAt.plusMillis(100),
                200,
                "application/json",
                Duration.ofMillis(100),
                payload,
                EventIncidentsV5Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        var parsed = new EventIncidentsV5Parser().parse(
                deduplicated.snapshotId(), eventId, payload, requestedAt.plusMillis(100));
        var identity = canonicalEventStore.findLatestByCanonicalId(
                        j4Import.canonicalEventId())
                .orElseThrow()
                .identity();

        var persisted = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                parsed.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(
                        deduplicated.snapshotId(),
                        deduplicated.payloadSha256(),
                        EventIncidentsV5Parser.PARSER_VERSION,
                        requestedAt.plusMillis(100)),
                parsed.completeness().orElseThrow()));

        assertThat(deduplicated.outcome())
                .isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(deduplicated.snapshotId()).isEqualTo(historical.snapshotId());
        assertThat(persisted.inserted()).isTrue();
        assertThat(schemaStatus(historical.snapshotId()))
                .isEqualTo("SCHEMA_INCOMPATIBLE");
        assertThat(jdbcTemplate.queryForMap(
                """
                select minute, incident_class, reason
                from j5_event_incident
                where observation_id = ?
                """,
                persisted.observationId()))
                .containsEntry("minute", 58)
                .containsEntry("incident_class", "yellow")
                .containsEntry("reason", "Argument");
        assertThat(j5EventDataStore.findLatest(j4Import.canonicalEventId()).incidents())
                .hasValueSatisfying(observation -> {
                    assertThat(observation.source().parserVersion())
                            .isEqualTo(EventIncidentsV5Parser.PARSER_VERSION);
                    assertThat(observation.completeness().status())
                            .isEqualTo(J5CompletenessStatus.COMPLETE);
                    assertThat(observation.data()).isInstanceOfSatisfying(
                            EventIncidents.class,
                            incidents -> {
                                var incident = incidents.incidents().getFirst();
                                assertThat(incident.minute()).contains(58);
                                assertThat(incident.incidentClass()).contains("yellow");
                                assertThat(incident.reason()).contains("Argument");
                            });
                });
    }

    @Test
    void persistsAndReadsBackTheReviewedV6FootballIncidentSignals() {
        var j4Import = j4OfflineFixtureImportService.importNominalCorpus();
        long eventId = 900001L;
        Instant requestedAt = Instant.parse("2026-08-17T12:00:00Z");
        RawPayloadEvidence payload = RawPayloadEvidence.capture("""
                {"incidents":[
                  {
                    "incidentType":"period",
                    "time":90,
                    "addedTime":999,
                    "text":"FT",
                    "homeScore":2,
                    "awayScore":1
                  },
                  {
                    "incidentType":"substitution",
                    "incidentClass":"regular",
                    "time":83,
                    "isHome":true,
                    "injury":true,
                    "playerIn":{"name":"Incoming Player"},
                    "playerOut":{"id":2119385,"name":"Outgoing Player"}
                  },
                  {
                    "incidentType":"goal",
                    "incidentClass":"penalty",
                    "from":"penalty",
                    "time":71,
                    "isHome":false,
                    "player":{"id":101,"name":"Goal Scorer"},
                    "assist1":{"id":102,"name":"Goal Assistant"},
                    "homeScore":1,
                    "awayScore":1
                  },
                  {
                    "incidentType":"injuryTime",
                    "time":45,
                    "addedTime":2,
                    "length":3
                  },
                  {
                    "incidentType":"varDecision",
                    "incidentClass":"goalAwarded",
                    "time":52,
                    "isHome":true,
                    "player":{"name":"Reviewed Player"},
                    "confirmed":true
                  },
                  {
                    "incidentType":"card",
                    "incidentClass":"yellow",
                    "time":58,
                    "isHome":false,
                    "playerName":"Carded Player",
                    "reason":"Argument",
                    "rescinded":true
                  },
                  {
                    "incidentType":"penaltyShootout",
                    "incidentClass":"scored",
                    "isHome":false,
                    "player":{"id":301,"name":"Shootout Taker"},
                    "homeScore":4,
                    "awayScore":5,
                    "reason":"scored",
                    "description":"Scored",
                    "sequence":9,
                    "footballPassingNetworkAction":[{"time":98}]
                  }
                ]}
                """.getBytes(StandardCharsets.UTF_8));
        var raw = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=900001|rules=v6",
                requestedAt,
                requestedAt.plusMillis(100),
                200,
                "application/json",
                Duration.ofMillis(100),
                payload,
                EventIncidentsV6Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        var parsed = new EventIncidentsV6Parser().parse(
                raw.snapshotId(), eventId, payload, requestedAt.plusMillis(100));
        var identity = canonicalEventStore.findLatestByCanonicalId(
                        j4Import.canonicalEventId())
                .orElseThrow()
                .identity();

        var persisted = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                parsed.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(
                        raw.snapshotId(),
                        raw.payloadSha256(),
                        EventIncidentsV6Parser.PARSER_VERSION,
                        requestedAt.plusMillis(100)),
                parsed.completeness().orElseThrow()));

        assertThat(persisted.inserted()).isTrue();
        assertThat(jdbcTemplate.queryForMap(
                """
                select period_text, added_time
                from j5_event_incident
                where observation_id = ? and incident_order = 0
                """,
                persisted.observationId()))
                .containsEntry("period_text", "FT")
                .containsEntry("added_time", null);
        assertThat(jdbcTemplate.queryForMap(
                """
                select injury, player_in_provider_id, player_in_name,
                       player_out_provider_id, player_out_name
                from j5_event_incident
                where observation_id = ? and incident_order = 1
                """,
                persisted.observationId()))
                .containsEntry("injury", true)
                .containsEntry("player_in_provider_id", null)
                .containsEntry("player_in_name", "Incoming Player")
                .containsEntry("player_out_provider_id", 2119385L)
                .containsEntry("player_out_name", "Outgoing Player");
        assertThat(jdbcTemplate.queryForMap(
                """
                select assist_provider_id, assist_name, goal_origin
                from j5_event_incident
                where observation_id = ? and incident_order = 2
                """,
                persisted.observationId()))
                .containsEntry("assist_provider_id", 102L)
                .containsEntry("assist_name", "Goal Assistant")
                .containsEntry("goal_origin", "penalty");
        assertThat(jdbcTemplate.queryForMap(
                """
                select injury_time_length, var_confirmed, rescinded,
                       description, shootout_sequence
                from j5_event_incident
                where observation_id = ?
                order by incident_order
                limit 1 offset 6
                """,
                persisted.observationId()))
                .containsEntry("injury_time_length", null)
                .containsEntry("var_confirmed", null)
                .containsEntry("rescinded", null)
                .containsEntry("description", "Scored")
                .containsEntry("shootout_sequence", 9);
        assertThat(j5EventDataStore.findLatest(j4Import.canonicalEventId()).incidents())
                .hasValueSatisfying(observation -> {
                    assertThat(observation.source().parserVersion())
                            .isEqualTo(EventIncidentsV6Parser.PARSER_VERSION);
                    assertThat(observation.completeness().status())
                            .isEqualTo(J5CompletenessStatus.COMPLETE);
                    assertThat(observation.data()).isInstanceOfSatisfying(
                            EventIncidents.class,
                            incidents -> {
                                assertThat(incidents.incidents()).hasSize(7);
                                assertThat(incidents.incidents().get(1).injury()).contains(true);
                                assertThat(incidents.incidents().get(2).assistName())
                                        .contains("Goal Assistant");
                                assertThat(incidents.incidents().get(3).injuryTimeLength())
                                        .contains(3);
                                assertThat(incidents.incidents().get(4).varConfirmed())
                                        .contains(true);
                                assertThat(incidents.incidents().get(5).rescinded())
                                        .contains(true);
                                assertThat(incidents.incidents().get(6).description())
                                        .contains("Scored");
                                assertThat(incidents.incidents().get(6).shootoutSequence())
                                        .contains(9);
                            });
                });
    }

    @Test
    void persistsAndReadsBackV12TerminalShootoutMinutesAsAbsent() {
        var j4Import = j4OfflineFixtureImportService.importNominalCorpus();
        long eventId = 900001L;
        Instant requestedAt = Instant.parse("2026-08-18T12:28:26Z");
        RawPayloadEvidence payload = RawPayloadEvidence.capture("""
                {"incidents":[
                  {
                    "text":"PEN","homeScore":2,"awayScore":1,"isLive":false,
                    "period":"penalties","time":999,"addedTime":999,
                    "incidentType":"period"
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"scored",
                    "isHome":true,"player":{"id":302,"name":"Scoring Taker"},
                    "homeScore":2,"awayScore":1,"sequence":2,
                    "description":"Scored","reason":"scored"
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"missed",
                    "isHome":false,"player":{"id":301,"name":"Missing Taker"},
                    "homeScore":1,"awayScore":1,"sequence":1
                  },
                  {
                    "text":"FT","homeScore":1,"awayScore":1,"isLive":false,
                    "time":90,"addedTime":999,"incidentType":"period"
                  }
                ]}
                """.getBytes(StandardCharsets.UTF_8));
        var raw = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=900001|rules=v12-unminuted-shootout",
                requestedAt,
                requestedAt.plusMillis(100),
                200,
                "application/json",
                Duration.ofMillis(100),
                payload,
                EventIncidentsV12Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        var parsed = new EventIncidentsV12Parser().parse(
                raw.snapshotId(), eventId, payload, requestedAt.plusMillis(100));
        var identity = canonicalEventStore.findLatestByCanonicalId(
                        j4Import.canonicalEventId())
                .orElseThrow()
                .identity();

        var persisted = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                parsed.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(
                        raw.snapshotId(),
                        raw.payloadSha256(),
                        EventIncidentsV12Parser.PARSER_VERSION,
                        requestedAt.plusMillis(100)),
                parsed.completeness().orElseThrow()));

        assertThat(parsed.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths())
                    .contains(
                            "$.incidents[0].time",
                            "$.incidents[1].time",
                            "$.incidents[2].time",
                            "$.incidents[2].reason",
                            "$.incidents[2].description");
        });
        assertThat(persisted.inserted()).isTrue();
        assertThat(jdbcTemplate.queryForList("""
                select incident_order, incident_type, minute, added_time
                from j5_event_incident
                where observation_id = ?
                order by incident_order
                """, persisted.observationId()))
                .satisfiesExactly(
                        marker -> {
                            assertThat(marker).containsEntry("incident_type", "period");
                            assertThat(marker.get("minute")).isNull();
                            assertThat(marker.get("added_time")).isNull();
                        },
                        scored -> {
                            assertThat(scored).containsEntry("incident_type", "penaltyShootout");
                            assertThat(scored.get("minute")).isNull();
                        },
                        missed -> {
                            assertThat(missed).containsEntry("incident_type", "penaltyShootout");
                            assertThat(missed.get("minute")).isNull();
                        },
                        fullTime -> {
                            assertThat(fullTime)
                                    .containsEntry("incident_type", "period")
                                    .containsEntry("minute", 90);
                        });
        assertThat(j5EventDataStore.findLatest(j4Import.canonicalEventId()).incidents())
                .hasValueSatisfying(observation -> {
                    assertThat(observation.source().parserVersion())
                            .isEqualTo(EventIncidentsV12Parser.PARSER_VERSION);
                    assertThat(observation.completeness().status())
                            .isEqualTo(J5CompletenessStatus.PARTIAL);
                    assertThat(observation.data()).isInstanceOfSatisfying(
                            EventIncidents.class,
                            incidents -> {
                                assertThat(incidents.incidents()).hasSize(4);
                                assertThat(incidents.incidents().get(0).minute()).isEmpty();
                                assertThat(incidents.incidents().get(1).minute()).isEmpty();
                                assertThat(incidents.incidents().get(2).minute()).isEmpty();
                                assertThat(incidents.incidents().get(3).minute()).contains(90);
                                assertThat(incidents.incidents().get(2).reason()).isEmpty();
                                assertThat(incidents.incidents().get(2).description()).isEmpty();
                            });
                });
    }

    @Test
    void persistsAProvider404AsAnUnavailableJ5Observation() {
        var j4Import = j4OfflineFixtureImportService.importNominalCorpus();
        long eventId = 900001L;
        Instant requestedAt = Instant.parse("2026-08-15T14:30:00Z");
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{\"error\":\"statistics unavailable\"}"
                        .getBytes(StandardCharsets.UTF_8));
        var raw = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_STATISTICS,
                "EVENT_STATISTICS|eventId=900001",
                requestedAt,
                requestedAt.plusMillis(100),
                404,
                "application/json",
                Duration.ofMillis(100),
                payload,
                EventStatisticsV2Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        var identity = canonicalEventStore.findLatestByCanonicalId(
                        j4Import.canonicalEventId())
                .orElseThrow()
                .identity();

        var persisted = j5EventDataStore.save(J5EventDataObservation.from(
                identity,
                new EventStatistics(eventId, java.util.List.of()),
                EventSourceTrace.providerSnapshot(
                        raw.snapshotId(),
                        raw.payloadSha256(),
                        J5UnavailableFamily.normalizerVersion(
                                SofascoreEndpointType.EVENT_STATISTICS),
                        requestedAt.plusMillis(100)),
                J5CompletenessReport.unavailable()));
        snapshotStore.classify(
                raw.snapshotId(), RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null);

        assertThat(persisted.inserted()).isTrue();
        assertThat(schemaStatus(raw.snapshotId())).isEqualTo("ENDPOINT_UNAVAILABLE");
        assertThat(j5EventDataStore.findLatest(j4Import.canonicalEventId()).statistics())
                .hasValueSatisfying(observation -> {
                    assertThat(observation.completeness().status())
                            .isEqualTo(J5CompletenessStatus.UNAVAILABLE);
                    assertThat(observation.completeness().scorePercent()).isZero();
                    assertThat(observation.data()).isInstanceOfSatisfying(
                            EventStatistics.class,
                            statistics -> assertThat(statistics.metrics()).isEmpty());
                });
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from j5_event_metric where observation_id = ?",
                Long.class,
                persisted.observationId())).isZero();
    }

    @Test
    void persistsProviderEventDetailsWithSnapshotProvenanceAndParsedCache() {
        var request = EventDetailsProviderRequest.phase1(
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                16386245L);
        byte[] rawBytes = "{\"event\":{\"id\":16386245}}"
                .getBytes(StandardCharsets.UTF_8);
        RawPayloadEvidence payload = RawPayloadEvidence.capture(rawBytes);
        Instant requestedAt = Instant.parse("2026-08-15T10:00:00Z");
        var response = new EventDetailsTransportResponse(
                request.requestKey(),
                requestedAt,
                requestedAt.plusMillis(275),
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(275),
                payload);
        var rawPersistence = snapshotStore.save(new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_DETAILS,
                response.requestKey(),
                response.requestedAt(),
                response.receivedAt(),
                response.httpStatus(),
                response.contentType(),
                response.latency(),
                response.payload(),
                com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV3Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        EventDetails details = new EventDetails(
                16386245L,
                Instant.parse("2026-08-14T18:00:00Z"),
                new ScheduledTeam(101L, "Saint-Etienne"),
                new ScheduledTeam(102L, "Clermont Foot"),
                new ScheduledEventStatus("finished", Optional.of("Ended")),
                Optional.of(new ScheduledTournament(103L, "Ligue 2")),
                Optional.of(new EventVenue(
                        104L,
                        "Stade local",
                        Optional.of("Saint-Etienne"))),
                Optional.of(new EventSeason(105L, "2026")),
                Optional.of("1"));

        var stored = j4ParsedEventDetailsPersistenceService.persistParsed(
                request,
                response,
                rawPersistence,
                details);

        assertThat(schemaStatus(rawPersistence.snapshotId())).isEqualTo("PARSED");
        assertThat(eventDetailsStore.findLatest(stored.canonicalEventId()))
                .hasValueSatisfying(detail -> {
                    assertThat(detail.source().snapshotId())
                            .hasValue(rawPersistence.snapshotId());
                    assertThat(detail.source().fixtureId()).isEmpty();
                    assertThat(detail.source().parserVersion())
                            .isEqualTo(com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV3Parser.PARSER_VERSION);
                    assertThat(detail.details().homeTeam().name())
                            .isEqualTo("Saint-Etienne");
                });
        assertThat(eventDetailsCache.findFreshParsed(
                request,
                response.receivedAt(),
                Duration.ofMinutes(15),
                com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV3Parser.PARSER_VERSION))
                .hasValueSatisfying(candidate -> {
                    assertThat(candidate.snapshotId())
                            .isEqualTo(rawPersistence.snapshotId());
                    assertThat(candidate.payload().sha256()).isEqualTo(payload.sha256());
                });
    }

    @Test
    void normalizesAnExistingSnapshotWithoutChangingItsHistoricalClassification()
            throws Exception {
        byte[] nominalPayload = Files.readAllBytes(Path.of(
                "fixtures/scheduled-events/nominal.json"));
        var snapshot = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-12|page=1",
                nominalPayload,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE"));

        var first = j4SnapshotNormalizationService.normalize(snapshot.snapshotId());
        var repeated = j4SnapshotNormalizationService.normalize(snapshot.snapshotId());

        assertThat(first.historicalSchemaStatus())
                .isEqualTo(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE);
        assertThat(first.currentParseStatus()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(first.payloadShape()).isEqualTo("EVENT_LIST");
        assertThat(first.parsedEventCount()).isEqualTo(1);
        assertThat(first.insertedObservationCount()).isEqualTo(1);
        assertThat(repeated.insertedObservationCount()).isZero();
        assertThat(repeated.deduplicatedObservationCount()).isEqualTo(1);
        assertThat(schemaStatus(snapshot.snapshotId()))
                .isEqualTo("SCHEMA_INCOMPATIBLE");
        assertThat(canonicalEventStore.findLatestByCanonicalId(
                first.canonicalEventIds().getFirst()))
                .hasValueSatisfying(event -> {
                    assertThat(event.source().snapshotId())
                            .hasValue(snapshot.snapshotId());
                    assertThat(event.source().payloadSha256())
                            .isEqualTo(snapshot.payloadSha256());
                    assertThat(event.source().parserVersion())
                            .isEqualTo("scheduled-events-v1");
                });
    }

    @Test
    void previewsAndExecutesOnlyAnAuditedBackupCoveredRetentionPlan()
            throws Exception {
        byte[] nominalPayload = Files.readAllBytes(Path.of(
                "fixtures/scheduled-events/nominal.json"));
        var snapshot = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-12|page=1",
                nominalPayload));
        j4SnapshotNormalizationService.normalize(snapshot.snapshotId());
        var unnormalized = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-12|page=2",
                nominalPayload));
        var excludedStatus = snapshotStore.save(snapshot(
                "SCHEDULED_EVENTS|date=2026-08-12|page=3",
                nominalPayload,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE"));
        j4SnapshotNormalizationService.normalize(excludedStatus.snapshotId());

        Instant generatedAt = Instant.parse("2026-09-20T12:00:00Z");
        Instant cutoffAt = Instant.parse("2026-08-21T12:00:00Z");
        var preview = j6RawPayloadRetentionStore.preview(
                30,
                generatedAt,
                cutoffAt,
                500);

        assertThat(preview.totalEligibleCount()).isEqualTo(1);
        assertThat(preview.candidates())
                .extracting(candidate -> candidate.snapshotId())
                .containsExactly(snapshot.snapshotId());
        assertThat(preview.candidates())
                .extracting(candidate -> candidate.snapshotId())
                .doesNotContain(unnormalized.snapshotId(), excludedStatus.snapshotId());
        assertThat(preview.confirmationPhrase())
                .startsWith("PURGER 1 PAYLOADS J6 ");

        Instant executedAt = generatedAt.plusSeconds(60);
        J6BackupEvidence backup = new J6BackupEvidence(
                "b".repeat(64),
                "c".repeat(64),
                generatedAt,
                excludedStatus.snapshotId(),
                generatedAt,
                true);
        UUID batchId = UUID.fromString("70000000-0000-0000-0000-000000000007");
        var execution = j6RawPayloadRetentionStore.purge(
                30,
                cutoffAt,
                500,
                preview.planSha256(),
                backup,
                batchId,
                executedAt);

        assertThat(execution.purgedPayloadCount()).isEqualTo(1);
        assertThat(execution.purgedPayloadBytes()).isEqualTo(nominalPayload.length);
        assertThat(jdbcTemplate.queryForMap("""
                select payload_raw, payload_size_bytes, payload_sha256, payload_purged_at
                from provider_snapshot
                where id = ?
                """, snapshot.snapshotId()))
                .containsEntry("payload_raw", null)
                .containsEntry("payload_size_bytes", (long) nominalPayload.length)
                .containsEntry("payload_sha256", snapshot.payloadSha256());
        Map<String, Object> auditEvidence = jdbcTemplate.queryForMap("""
                select batch_id, snapshot_id, snapshot_received_at_before,
                       payload_size_bytes_before,
                       payload_sha256_before, plan_sha256,
                       backup_manifest_sha256, backup_cipher_sha256
                from j6_raw_payload_purge_audit
                where snapshot_id = ?
                """, snapshot.snapshotId());
        assertThat(auditEvidence)
                .containsEntry("batch_id", batchId)
                .containsEntry("snapshot_id", snapshot.snapshotId())
                .containsEntry("payload_size_bytes_before", (long) nominalPayload.length)
                .containsEntry("payload_sha256_before", snapshot.payloadSha256())
                .containsEntry("plan_sha256", preview.planSha256())
                .containsEntry("backup_manifest_sha256", "b".repeat(64))
                .containsEntry("backup_cipher_sha256", "c".repeat(64));
        assertThat(((Timestamp) auditEvidence.get("snapshot_received_at_before")).toInstant())
                .isEqualTo(Instant.parse("2026-08-12T12:00:00.275Z"));
        assertThat(snapshotInspectionStore.findById(snapshot.snapshotId())).isEmpty();
        assertThat(j6SnapshotHistoryStore.findTraces(Set.of(snapshot.snapshotId())))
                .hasEntrySatisfying(snapshot.snapshotId(), trace -> {
                    assertThat(trace.payloadSha256()).isEqualTo(snapshot.payloadSha256());
                    assertThat(trace.rawPayloadState().name())
                            .isEqualTo("PAYLOAD_PURGED");
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void importsAndReimportsAnOfflineMultiMatchBatchWithAppendOnlyOccurrences()
            throws Exception {
        long firstProviderEventId = 1_000_000_001L;
        long secondProviderEventId = 1_000_000_002L;
        LocalDate date = LocalDate.parse("2026-10-10");
        UUID firstCanonicalEventId = persistOfflineBatchEvent(
                firstProviderEventId, Instant.parse("2026-10-10T14:00:00Z"));
        UUID secondCanonicalEventId = persistOfflineBatchEvent(
                secondProviderEventId, Instant.parse("2026-10-10T18:00:00Z"));
        byte[] statistics = Files.readAllBytes(Path.of(
                "src/test/resources/fixtures/provider-j5/statistics-nominal.json"));
        byte[] incidents = Files.readAllBytes(Path.of(
                "src/test/resources/fixtures/provider-j5/"
                        + "incidents-provider-live-extra-time.json"));
        byte[] lineups = Files.readAllBytes(Path.of(
                "src/test/resources/fixtures/provider-j5/lineups-nominal.json"));
        Instant seededAt = Instant.parse("2026-10-10T13:55:00Z");
        RawSnapshotPersistenceResult seededRawOnly = snapshotStore.save(
                new RawManualCallSnapshot(
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT,
                        "EVENT_INCIDENTS|eventId=" + firstProviderEventId,
                        seededAt,
                        seededAt,
                        200,
                        "application/json",
                        Duration.ZERO,
                        RawPayloadEvidence.capture(incidents),
                        EventIncidentsV14Parser.PARSER_VERSION,
                        RawSnapshotSchemaStatus.RAW_ONLY,
                        null));
        assertThat(seededRawOnly.outcome())
                .isEqualTo(RawSnapshotPersistenceOutcome.INSERTED);
        assertThat(jdbcTemplate.queryForObject(
                "select schema_status from provider_snapshot where id = ?",
                String.class,
                seededRawOnly.snapshotId())).isEqualTo("RAW_ONLY");

        J5OfflineBatchPlan firstPlan = j5OfflineBatchControlService.prepare(
                date,
                "Europe/Paris",
                List.of(secondCanonicalEventId, firstCanonicalEventId)).plan();
        List<J5OfflineBatchUpload> firstUploads = new ArrayList<>(offlineBatchUploads(
                firstPlan, statistics, incidents, lineups));
        firstUploads.set(0, j5OfflineBatchUploadService.declareUnavailable404(
                firstPlan.events().getFirst().expectedFileNames().getFirst()));
        var first = j5OfflineBatchImportService.execute(
                firstPlan.requestId(),
                firstPlan.confirmationPhrase(),
                true,
                firstUploads);

        assertThat(first.completed()).isTrue();
        assertThat(first.localJsonImports()).isEqualTo(6);
        assertThat(first.events())
                .extracting(event -> event.providerEventId())
                .containsExactly(firstProviderEventId, secondProviderEventId);
        assertThat(first.events()).allSatisfy(event ->
                assertThat(event.endpoints()).allSatisfy(endpoint ->
                        assertThat(endpoint.observationInserted()).isTrue()));
        assertThat(first.events().getFirst().endpoints().getFirst().completenessStatus())
                .isEqualTo(J5CompletenessStatus.UNAVAILABLE);
        Map<String, Object> unavailableSnapshot = jdbcTemplate.queryForMap("""
                select http_status, schema_status, acquisition_mode, payload_raw
                from provider_snapshot
                where request_key = ?
                  and acquisition_mode = 'MANUAL_LOCAL_JSON_IMPORT'
                """, "EVENT_STATISTICS|eventId=" + firstProviderEventId);
        assertThat(unavailableSnapshot)
                .containsEntry("http_status", 404)
                .containsEntry("schema_status", "ENDPOINT_UNAVAILABLE")
                .containsEntry("acquisition_mode", "MANUAL_LOCAL_JSON_IMPORT");
        assertThat(new String(
                (byte[]) unavailableSnapshot.get("payload_raw"),
                StandardCharsets.UTF_8))
                .contains(J5LocalUnavailableEvidence.OPERATOR_MARKER);
        assertThat(offlineBatchSnapshotCount(
                firstProviderEventId, secondProviderEventId)).isEqualTo(6L);
        assertThat(offlineBatchOccurrenceCount(
                firstProviderEventId, secondProviderEventId)).isEqualTo(7L);
        assertThat(offlineBatchObservationCount(
                firstCanonicalEventId, secondCanonicalEventId)).isEqualTo(6L);
        assertThat(jdbcTemplate.queryForObject(
                "select schema_status from provider_snapshot where id = ?",
                String.class,
                seededRawOnly.snapshotId())).isEqualTo("PARSED");
        assertThat(jdbcTemplate.queryForList("""
                select observation.parser_version, incident.period_text, incident.minute
                from j5_event_data_observation observation
                join j5_event_incident incident
                  on incident.observation_id = observation.id
                where observation.canonical_event_id in (?, ?)
                  and observation.endpoint_type = 'EVENT_INCIDENTS'
                  and incident.incident_type = 'period'
                  and incident.period_text = 'Extra time'
                order by observation.canonical_event_id
                """, firstCanonicalEventId, secondCanonicalEventId))
                .hasSize(2)
                .allSatisfy(row -> assertThat(row)
                        .containsEntry(
                                "parser_version", EventIncidentsV17Parser.PARSER_VERSION)
                        .containsEntry("period_text", "Extra time")
                        .containsEntry("minute", 120));

        J5OfflineBatchPlan secondPlan = j5OfflineBatchControlService.prepare(
                date,
                "Europe/Paris",
                List.of(firstCanonicalEventId, secondCanonicalEventId)).plan();
        assertThat(secondPlan.planSha256()).isEqualTo(firstPlan.planSha256());
        List<J5OfflineBatchUpload> repeatedUploads = new ArrayList<>(offlineBatchUploads(
                secondPlan, statistics, incidents, lineups));
        repeatedUploads.set(0, j5OfflineBatchUploadService.declareUnavailable404(
                secondPlan.events().getFirst().expectedFileNames().getFirst()));
        var repeated = j5OfflineBatchImportService.execute(
                secondPlan.requestId(),
                secondPlan.confirmationPhrase(),
                true,
                repeatedUploads);

        assertThat(repeated.completed()).isTrue();
        assertThat(repeated.localJsonImports()).isEqualTo(6);
        assertThat(repeated.events()).allSatisfy(event ->
                assertThat(event.endpoints()).allSatisfy(endpoint ->
                        assertThat(endpoint.observationInserted()).isFalse()));
        assertThat(offlineBatchSnapshotCount(
                firstProviderEventId, secondProviderEventId)).isEqualTo(6L);
        assertThat(offlineBatchOccurrenceCount(
                firstProviderEventId, secondProviderEventId)).isEqualTo(13L);
        assertThat(offlineBatchObservationCount(
                firstCanonicalEventId, secondCanonicalEventId)).isEqualTo(6L);
        assertThat(jdbcTemplate.queryForObject("""
                select schema_status
                from provider_snapshot
                where request_key = ?
                  and acquisition_mode = 'MANUAL_LOCAL_JSON_IMPORT'
                """, String.class, "EVENT_INCIDENTS|eventId=" + firstProviderEventId))
                .isEqualTo("PARSED");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectsTheWholeOfflineBatchDuringPrevalidationWithoutPersistence()
            throws Exception {
        long firstProviderEventId = 9_710_001L;
        long secondProviderEventId = 9_710_002L;
        LocalDate date = LocalDate.parse("2026-10-09");
        UUID firstCanonicalEventId = persistOfflineBatchEvent(
                firstProviderEventId, Instant.parse("2026-10-09T14:00:00Z"));
        UUID secondCanonicalEventId = persistOfflineBatchEvent(
                secondProviderEventId, Instant.parse("2026-10-09T18:00:00Z"));
        byte[] statistics = Files.readAllBytes(Path.of(
                "src/test/resources/fixtures/provider-j5/statistics-nominal.json"));
        byte[] incidents = Files.readAllBytes(Path.of(
                "src/test/resources/fixtures/provider-j5/incidents-nominal.json"));
        byte[] lineups = Files.readAllBytes(Path.of(
                "src/test/resources/fixtures/provider-j5/lineups-nominal.json"));

        J5OfflineBatchPlan plan = j5OfflineBatchControlService.prepare(
                date,
                "Europe/Paris",
                List.of(firstCanonicalEventId, secondCanonicalEventId)).plan();
        List<J5OfflineBatchUpload> uploads = new ArrayList<>(offlineBatchUploads(
                plan, statistics, incidents, lineups));
        String lastLineupsFileName = plan.events().getLast().expectedFileNames().get(2);
        uploads.set(uploads.size() - 1, j5OfflineBatchUploadService.capture(
                lastLineupsFileName,
                "{\"confirmed\":true}".getBytes(StandardCharsets.UTF_8)));

        long snapshotsBefore = offlineBatchSnapshotCount(
                firstProviderEventId, secondProviderEventId);
        long occurrencesBefore = offlineBatchOccurrenceCount(
                firstProviderEventId, secondProviderEventId);
        long observationsBefore = offlineBatchObservationCount(
                firstCanonicalEventId, secondCanonicalEventId);

        assertThatThrownBy(() -> j5OfflineBatchImportService.execute(
                plan.requestId(),
                plan.confirmationPhrase(),
                true,
                uploads))
                .isInstanceOf(J5OfflineBatchException.class)
                .extracting(exception -> ((J5OfflineBatchException) exception).error())
                .isEqualTo(J5OfflineBatchError.LINEUPS_PAYLOAD_INCOMPATIBLE);

        assertThat(offlineBatchSnapshotCount(
                firstProviderEventId, secondProviderEventId)).isEqualTo(snapshotsBefore);
        assertThat(offlineBatchOccurrenceCount(
                firstProviderEventId, secondProviderEventId)).isEqualTo(occurrencesBefore);
        assertThat(offlineBatchObservationCount(
                firstCanonicalEventId, secondCanonicalEventId)).isEqualTo(observationsBefore);
        assertThat(j5OfflineBatchControlService.snapshot().awaitingConfirmation()).isTrue();
        j5OfflineBatchControlService.stop(plan.requestId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackTheWholeOfflineBatchWhenTheLastFamilyOfTheLastEventFails()
            throws Exception {
        long firstProviderEventId = 9_720_001L;
        long secondProviderEventId = 9_720_002L;
        LocalDate date = LocalDate.parse("2026-10-11");
        UUID firstCanonicalEventId = persistOfflineBatchEvent(
                firstProviderEventId, Instant.parse("2026-10-11T14:00:00Z"));
        UUID secondCanonicalEventId = persistOfflineBatchEvent(
                secondProviderEventId, Instant.parse("2026-10-11T18:00:00Z"));
        byte[] statistics = Files.readAllBytes(Path.of(
                "src/test/resources/fixtures/provider-j5/statistics-nominal.json"));
        byte[] incidents = Files.readAllBytes(Path.of(
                "src/test/resources/fixtures/provider-j5/incidents-nominal.json"));
        byte[] lineups = Files.readAllBytes(Path.of(
                "src/test/resources/fixtures/provider-j5/lineups-nominal.json"));
        String triggerName = "trg_fail_j5_offline_batch_it";
        String functionName = "fail_j5_offline_batch_it";
        jdbcTemplate.execute("""
                create or replace function fail_j5_offline_batch_it()
                returns trigger
                language plpgsql
                as $function$
                begin
                    raise exception 'forced offline batch rollback';
                end;
                $function$
                """);
        jdbcTemplate.execute("""
                create trigger trg_fail_j5_offline_batch_it
                before insert on j5_event_data_observation
                for each row
                when (
                    new.canonical_event_id = '%s'::uuid
                    and new.endpoint_type = 'EVENT_LINEUPS'
                )
                execute function fail_j5_offline_batch_it()
                """.formatted(secondCanonicalEventId));
        try {
            J5OfflineBatchPlan plan = j5OfflineBatchControlService.prepare(
                    date,
                    "Europe/Paris",
                    List.of(firstCanonicalEventId, secondCanonicalEventId)).plan();
            List<J5OfflineBatchUpload> uploads = offlineBatchUploads(
                    plan, statistics, incidents, lineups);

            assertThatThrownBy(() -> j5OfflineBatchImportService.execute(
                    plan.requestId(),
                    plan.confirmationPhrase(),
                    true,
                    uploads))
                    .isInstanceOf(J5OfflineBatchException.class)
                    .extracting(exception -> ((J5OfflineBatchException) exception).error())
                    .isEqualTo(J5OfflineBatchError.STORAGE_UNAVAILABLE);

            assertThat(offlineBatchSnapshotCount(
                    firstProviderEventId, secondProviderEventId)).isZero();
            assertThat(offlineBatchOccurrenceCount(
                    firstProviderEventId, secondProviderEventId)).isZero();
            assertThat(offlineBatchObservationCount(
                    firstCanonicalEventId, secondCanonicalEventId)).isZero();
            assertThat(j5OfflineBatchControlService.snapshot().result().localJsonImports())
                    .isZero();
        }
        finally {
            jdbcTemplate.execute("drop trigger if exists " + triggerName
                    + " on j5_event_data_observation");
            jdbcTemplate.execute("drop function if exists " + functionName + "()");
        }
    }

    private UUID persistOfflineBatchEvent(long providerEventId, Instant startsAt) {
        ScheduledEvent event = new ScheduledEvent(
                providerEventId,
                startsAt,
                new ScheduledTeam(providerEventId * 10, "Batch Home " + providerEventId),
                new ScheduledTeam(providerEventId * 10 + 1, "Batch Away " + providerEventId),
                new ScheduledEventStatus("notstarted", Optional.of("Not started")),
                Optional.of(new ScheduledTournament(97_000L, "Batch League")));
        EventSourceTrace source = EventSourceTrace.syntheticFixture(
                "j5-offline-batch-it-" + providerEventId,
                "%064x".formatted(providerEventId),
                ScheduledEventsV1Parser.PARSER_VERSION,
                Instant.parse("2026-08-22T10:00:00Z"));
        return canonicalEventStore.save(CanonicalEventObservation.from(event, source))
                .canonicalEventId();
    }

    private List<J5OfflineBatchUpload> offlineBatchUploads(
            J5OfflineBatchPlan plan,
            byte[] statistics,
            byte[] incidents,
            byte[] lineups) {
        return plan.events().stream()
                .flatMap(event -> java.util.stream.Stream.of(
                        j5OfflineBatchUploadService.capture(
                                event.expectedFileNames().get(0), statistics),
                        j5OfflineBatchUploadService.capture(
                                event.expectedFileNames().get(1), incidents),
                        j5OfflineBatchUploadService.capture(
                                event.expectedFileNames().get(2), lineups)))
                .toList();
    }

    private long offlineBatchSnapshotCount(long... providerEventIds) {
        long count = 0;
        for (long providerEventId : providerEventIds) {
            count += jdbcTemplate.queryForObject("""
                    select count(*)
                    from provider_snapshot
                    where acquisition_mode = 'MANUAL_LOCAL_JSON_IMPORT'
                      and request_key in (?, ?, ?)
                    """,
                    Long.class,
                    "EVENT_STATISTICS|eventId=" + providerEventId,
                    "EVENT_INCIDENTS|eventId=" + providerEventId,
                    "EVENT_LINEUPS|eventId=" + providerEventId);
        }
        return count;
    }

    private long offlineBatchOccurrenceCount(long... providerEventIds) {
        long count = 0;
        for (long providerEventId : providerEventIds) {
            count += jdbcTemplate.queryForObject("""
                    select count(*)
                    from provider_snapshot_occurrence occurrence
                    join provider_snapshot snapshot on snapshot.id = occurrence.snapshot_id
                    where snapshot.acquisition_mode = 'MANUAL_LOCAL_JSON_IMPORT'
                      and snapshot.request_key in (?, ?, ?)
                    """,
                    Long.class,
                    "EVENT_STATISTICS|eventId=" + providerEventId,
                    "EVENT_INCIDENTS|eventId=" + providerEventId,
                    "EVENT_LINEUPS|eventId=" + providerEventId);
        }
        return count;
    }

    private long offlineBatchObservationCount(UUID... canonicalEventIds) {
        long count = 0;
        for (UUID canonicalEventId : canonicalEventIds) {
            count += jdbcTemplate.queryForObject("""
                    select count(*)
                    from j5_event_data_observation
                    where canonical_event_id = ?
                      and source_kind = 'PROVIDER_SNAPSHOT'
                    """, Long.class, canonicalEventId);
        }
        return count;
    }

    private static RawManualCallSnapshot snapshot(String requestKey, byte[] rawPayload) {
        return snapshot(
                requestKey,
                rawPayload,
                RawSnapshotSchemaStatus.PARSED,
                null);
    }

    private static EventSourceTrace fixtureSource(LoadedFixture fixture) {
        return EventSourceTrace.syntheticFixture(
                fixture.manifest().fixtureId(),
                fixture.rawSha256(),
                fixture.manifest().parserVersion(),
                fixture.manifest().recordedAt());
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

    private RawSnapshotPersistenceResult saveProviderSnapshot(
            SofascoreEndpointType endpointType,
            long providerEventId,
            String parserVersion,
            Instant receivedAt,
            String rawJson) {
        Instant requestedAt = receivedAt.minusMillis(100);
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                rawJson.getBytes(StandardCharsets.UTF_8));
        return snapshotStore.save(new RawManualCallSnapshot(
                endpointType,
                endpointType.name() + "|eventId=" + providerEventId,
                requestedAt,
                receivedAt,
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(100),
                payload,
                parserVersion,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
    }

    private void assertSourceInsertWaitsForJ7Decision(
            UUID canonicalEventId,
            ConnectionInsert sourceInsert,
            String observationTable) throws Exception {
        assertWriteWaitsForJ7Decision(
                canonicalEventId,
                sourceInsert,
                () -> assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from " + observationTable
                                + " where canonical_event_id = ?",
                        Long.class,
                        canonicalEventId)).isZero(),
                () -> assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from " + observationTable
                                + " where canonical_event_id = ?",
                        Long.class,
                        canonicalEventId)).isEqualTo(1L));
    }

    private void assertProviderSnapshotMutationWaitsForJ7Decision(
            UUID canonicalEventId,
            long snapshotId) throws Exception {
        assertWriteWaitsForJ7Decision(
                canonicalEventId,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            update provider_snapshot
                            set schema_status = 'PARSED'
                            where id = ?
                            """)) {
                        statement.setLong(1, snapshotId);
                        return statement.executeUpdate();
                    }
                },
                () -> assertThat(schemaStatus(snapshotId)).isEqualTo("RAW_ONLY"),
                () -> assertThat(schemaStatus(snapshotId)).isEqualTo("PARSED"));
    }

    private void assertJ5ChildInsertWaitsForJ7Decision(
            UUID canonicalEventId,
            long observationId,
            ConnectionInsert childInsert,
            String childTable) throws Exception {
        assertWriteWaitsForJ7Decision(
                canonicalEventId,
                childInsert,
                () -> assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from " + childTable
                                + " where observation_id = ?",
                        Long.class,
                        observationId)).isZero(),
                () -> assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from " + childTable
                                + " where observation_id = ?",
                        Long.class,
                        observationId)).isEqualTo(1L));
    }

    private void assertInvisibleJ5ParentRejectsChildInsert(UUID canonicalEventId)
            throws Exception {
        long parentObservationId;
        try (Connection parentConnection = dataSource.getConnection();
                Connection childConnection = dataSource.getConnection()) {
            parentConnection.setAutoCommit(false);
            childConnection.setAutoCommit(false);
            try {
                try (PreparedStatement parentInsert = parentConnection.prepareStatement("""
                        insert into j5_event_data_observation (
                            canonical_event_id,
                            endpoint_type,
                            source_kind,
                            source_reference,
                            source_snapshot_id,
                            source_fixture_id,
                            source_payload_sha256,
                            parser_version,
                            source_received_at,
                            completeness_status,
                            completeness_score,
                            present_signals,
                            expected_signals,
                            missing_paths_json,
                            lineups_confirmed,
                            normalized_sha256
                        ) values (
                            ?, 'EVENT_STATISTICS', 'SYNTHETIC_FIXTURE',
                            'j7-lock-j5-uncommitted-parent', null,
                            'j7-lock-j5-uncommitted-parent', repeat('a', 64),
                            'event-statistics-v1', '2026-08-19T12:00:05Z',
                            'COMPLETE', 100, 2, 2, '[]'::jsonb, null,
                            repeat('b', 64)
                        )
                        returning id
                        """)) {
                    parentInsert.setObject(1, canonicalEventId);
                    try (var resultSet = parentInsert.executeQuery()) {
                        assertThat(resultSet.next()).isTrue();
                        parentObservationId = resultSet.getLong(1);
                    }
                }
                assertThat(jdbcTemplate.queryForObject("""
                        select count(*)
                        from j5_event_data_observation
                        where id = ?
                        """, Long.class, parentObservationId)).isZero();

                try (PreparedStatement timeout = childConnection.prepareStatement(
                        "set local statement_timeout = '2s'")) {
                    timeout.execute();
                }
                try (PreparedStatement childInsert = childConnection.prepareStatement("""
                        insert into j5_event_metric (
                            observation_id, metric_order, period, group_name,
                            metric_code, metric_name, home_value, away_value
                        ) values (
                            ?, 7017, 'ALL', 'J7 invisible parent group',
                            'j7InvisibleParent', 'J7 invisible parent', '1', '0'
                        )
                        """)) {
                    childInsert.setLong(1, parentObservationId);
                    assertThatThrownBy(childInsert::executeUpdate)
                            .isInstanceOf(java.sql.SQLException.class)
                            .hasStackTraceContaining(
                                    "J5 child source parent observation must be visible");
                }
                childConnection.rollback();
                assertThat(jdbcTemplate.queryForObject("""
                        select count(*)
                        from j5_event_metric
                        where observation_id = ?
                        """, Long.class, parentObservationId)).isZero();
            }
            finally {
                childConnection.rollback();
                parentConnection.rollback();
            }
        }
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j5_event_data_observation
                where source_fixture_id = 'j7-lock-j5-uncommitted-parent'
                """, Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from j5_event_metric metric
                join j5_event_data_observation observation
                  on observation.id = metric.observation_id
                where observation.source_fixture_id =
                    'j7-lock-j5-uncommitted-parent'
                """, Long.class)).isZero();
    }

    private void assertWriteWaitsForJ7Decision(
            UUID canonicalEventId,
            ConnectionInsert sourceWrite,
            Runnable assertBeforeCommit,
            Runnable assertAfterCommit) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection decisionConnection = dataSource.getConnection();
                Connection writerConnection = dataSource.getConnection()) {
            decisionConnection.setAutoCommit(false);
            writerConnection.setAutoCommit(false);
            try (PreparedStatement timeout = writerConnection.prepareStatement(
                    "set local statement_timeout = '10s'")) {
                timeout.execute();
            }
            int writerPid;
            try (PreparedStatement pidStatement = writerConnection.prepareStatement(
                    "select pg_backend_pid()")) {
                try (var resultSet = pidStatement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    writerPid = resultSet.getInt(1);
                }
            }
            try (PreparedStatement lockStatement = decisionConnection.prepareStatement("""
                    select pg_advisory_xact_lock(hashtextextended(id::text, 7007))
                    from canonical_event
                    where id = ?
                    for update
                    """)) {
                lockStatement.setObject(1, canonicalEventId);
                try (var resultSet = lockStatement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                }
            }

            CountDownLatch writeStarted = new CountDownLatch(1);
            Future<Integer> writer = executor.submit(() -> {
                writeStarted.countDown();
                int rows = sourceWrite.execute(writerConnection);
                writerConnection.commit();
                return rows;
            });
            try {
                assertThat(writeStarted.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(awaitAdvisoryLockWait(writerPid, Duration.ofSeconds(5))).isTrue();
                assertThat(writer.isDone()).isFalse();
                assertBeforeCommit.run();

                decisionConnection.commit();

                assertThat(writer.get(5, TimeUnit.SECONDS)).isEqualTo(1);
                assertAfterCommit.run();
            }
            finally {
                if (!decisionConnection.getAutoCommit()) {
                    decisionConnection.rollback();
                }
                if (!writer.isDone()) {
                    writer.get(5, TimeUnit.SECONDS);
                }
            }
        }
        finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean awaitAdvisoryLockWait(int writerPid, Duration maximumWait)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(maximumWait);
        while (Instant.now().isBefore(deadline)) {
            Boolean waiting = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1
                        from pg_stat_activity
                        where pid = ?
                          and wait_event_type = 'Lock'
                          and wait_event = 'advisory'
                    )
                    """, Boolean.class, writerPid);
            if (Boolean.TRUE.equals(waiting)) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static RawManualCallSnapshot j8ScheduledSnapshot(
            String requestKey,
            Instant requestedAt,
            byte[] payload) {
        return new RawManualCallSnapshot(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                requestKey,
                requestedAt,
                requestedAt.plusMillis(25),
                200,
                "application/json; charset=utf-8",
                Duration.ofMillis(25),
                RawPayloadEvidence.capture(payload),
                ScheduledEventsV1Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.PARSED,
                null);
    }

    private static J8BenchmarkUnitResult providerJ8Result(
            long unitId,
            long attemptId,
            Instant resolvedAt,
            J8BenchmarkOutcomeType outcome,
            OptionalInt httpStatus,
            OptionalLong latencyMillis,
            OptionalLong snapshotId,
            OptionalLong snapshotOccurrenceId,
            Optional<String> parserVersion,
            Optional<RawSnapshotSchemaStatus> schemaStatus,
            String terminalCode) {
        return new J8BenchmarkUnitResult(
                unitId,
                OptionalLong.of(attemptId),
                resolvedAt,
                J8BenchmarkResolutionSource.PROVIDER,
                outcome,
                httpStatus.isPresent(),
                httpStatus,
                latencyMillis,
                snapshotId,
                snapshotOccurrenceId,
                parserVersion,
                schemaStatus,
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.ofNullable(terminalCode));
    }

    private static J8BenchmarkUnitResult blockedJ8Result(long unitId, Instant resolvedAt) {
        return new J8BenchmarkUnitResult(
                unitId,
                OptionalLong.empty(),
                resolvedAt,
                J8BenchmarkResolutionSource.BLOCKED,
                J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE,
                false,
                OptionalInt.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.of("PRIOR_UNIT_FAILED"));
    }

    private void insertJ8Unit(
            UUID campaignId,
            int ordinal,
            SofascoreEndpointType endpoint,
            String requestKey,
            UUID canonicalEventId,
            Long providerEventId,
            Instant declaredAt) {
        jdbcTemplate.update("""
                insert into j8_benchmark_unit (
                    campaign_id, unit_ordinal, logical_endpoint, request_key,
                    canonical_event_id, provider_event_id, declared_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                campaignId,
                ordinal,
                endpoint.name(),
                requestKey,
                canonicalEventId,
                providerEventId,
                Timestamp.from(declaredAt));
    }

    private void insertJ8Result(
            long unitId,
            Long attemptId,
            Instant resolvedAt,
            J8BenchmarkResolutionSource source,
            J8BenchmarkOutcomeType outcome,
            boolean responseReceived,
            Integer httpStatus,
            Long latencyMillis,
            Long snapshotId,
            Long occurrenceId,
            String parserVersion,
            RawSnapshotSchemaStatus schemaStatus,
            int warningCount) {
        jdbcTemplate.update("""
                insert into j8_benchmark_unit_result (
                    unit_id, attempt_id, resolved_at, resolution_source, outcome_type,
                    response_received, http_status, latency_ms, snapshot_id,
                    snapshot_occurrence_id, parser_version, schema_status,
                    parser_warning_count, terminal_code
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'TEST_EVIDENCE')
                """,
                unitId,
                attemptId,
                Timestamp.from(resolvedAt),
                source.name(),
                outcome.name(),
                responseReceived,
                httpStatus,
                latencyMillis,
                snapshotId,
                occurrenceId,
                parserVersion,
                schemaStatus == null ? null : schemaStatus.name(),
                warningCount);
    }

    private void assertJ8AppendOnly(String table, String keyColumn, Object key) {
        assertThat(table).matches("j8_[a-z_]+");
        assertThat(keyColumn).matches("[a-z_]+");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update " + table + " set created_at = created_at where "
                        + keyColumn + " = ?",
                key))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining(table + " is append-only");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from " + table + " where " + keyColumn + " = ?",
                key))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining(table + " is append-only");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from pg_trigger trigger_definition
                join pg_class evidence_table
                  on evidence_table.oid = trigger_definition.tgrelid
                join pg_namespace evidence_schema
                  on evidence_schema.oid = evidence_table.relnamespace
                where evidence_table.relname = ?
                  and evidence_schema.nspname = current_schema()
                  and not trigger_definition.tgisinternal
                  and pg_get_triggerdef(trigger_definition.oid) like '%BEFORE TRUNCATE%'
                """, Long.class, table)).isOne();
    }

    private static void assertProviderSource(
            JsonNode source,
            String component,
            long observationId,
            long snapshotId,
            String sourceSha256,
            String normalizedSha256,
            String parserVersion,
            Instant receivedAt,
            String availability,
            Optional<J5CompletenessReport> expectedCompleteness) {
        assertThat(source.get("component").stringValue()).isEqualTo(component);
        assertThat(source.get("availability").stringValue()).isEqualTo(availability);
        assertThat(source.get("observationId").longValue()).isEqualTo(observationId);
        assertThat(source.get("sourceKind").stringValue())
                .isEqualTo("PROVIDER_SNAPSHOT");
        assertThat(source.get("sourceReference").stringValue())
                .isEqualTo("snapshot:" + snapshotId);
        assertThat(source.get("snapshotId").longValue()).isEqualTo(snapshotId);
        assertThat(source.get("fixtureId").isNull()).isTrue();
        assertThat(source.get("sourceSha256").stringValue()).isEqualTo(sourceSha256);
        assertThat(source.get("normalizedSha256").stringValue())
                .isEqualTo(normalizedSha256);
        assertThat(source.get("parserVersion").stringValue()).isEqualTo(parserVersion);
        assertThat(Instant.parse(source.get("receivedAt").stringValue()))
                .isEqualTo(receivedAt);
        assertThat(source.get("rawPayloadState").stringValue()).isEqualTo("RETAINED");
        if (expectedCompleteness.isPresent()) {
            J5CompletenessReport expected = expectedCompleteness.orElseThrow();
            JsonNode completeness = source.get("completeness");
            assertThat(completeness.get("status").stringValue())
                    .isEqualTo(expected.status().name());
            assertThat(completeness.get("scorePercent").intValue())
                    .isEqualTo(expected.scorePercent());
            assertThat(completeness.get("presentSignals").intValue())
                    .isEqualTo(expected.presentSignals());
            assertThat(completeness.get("expectedSignals").intValue())
                    .isEqualTo(expected.expectedSignals());
            assertThat(completeness.get("missingPaths").valueStream()
                    .map(JsonNode::stringValue)
                    .toList()).containsExactlyElementsOf(expected.missingPaths());
        }
        else {
            assertThat(source.get("completeness").isNull()).isTrue();
        }
    }

    @FunctionalInterface
    private interface ConnectionInsert {

        int execute(Connection connection) throws Exception;
    }

    private static void insertJ7Candidate(
            JdbcTemplate jdbcTemplate,
            UUID canonicalEventId,
            UUID exportId,
            String dataSha256,
            String sourceSetSha256,
            String contentSha256) {
        insertJ7Candidate(
                jdbcTemplate,
                canonicalEventId,
                exportId,
                dataSha256,
                sourceSetSha256,
                contentSha256,
                """
                        [
                          {"component":"EVENT_STATE","snapshotId":null},
                          {"component":"EVENT_DETAILS","snapshotId":null},
                          {"component":"EVENT_STATISTICS","snapshotId":null},
                          {"component":"EVENT_INCIDENTS","snapshotId":null},
                          {"component":"EVENT_LINEUPS","snapshotId":null}
                        ]
                        """);
    }

    private static void insertJ7Candidate(
            JdbcTemplate jdbcTemplate,
            UUID canonicalEventId,
            UUID exportId,
            String dataSha256,
            String sourceSetSha256,
            String contentSha256,
            String sourceObservations) {
        String relativePath =
                "j7-" + canonicalEventId + "-" + exportId + ".candidate.json";
        jdbcTemplate.update("""
                insert into export_manifest (
                    export_kind,
                    export_uuid,
                    canonical_event_id,
                    schema_id,
                    schema_version,
                    generated_at,
                    data_sha256,
                    source_set_sha256,
                    candidate_content_sha256,
                    content_size_bytes,
                    source_observations,
                    export_path,
                    content_sha256,
                    validation_status,
                    source_snapshot_ids,
                    warnings
                ) values (
                    'J7_CANONICAL_EVENT',
                    ?,
                    ?,
                    'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1',
                    '1.0.0',
                    '2026-08-19T10:00:00Z',
                    ?,
                    ?,
                    ?,
                    1024,
                    cast(? as jsonb),
                    ?,
                    ?,
                    'COHERENCE_CHECKED',
                    '{}'::bigint[],
                    '[]'::jsonb
                )
                """,
                exportId,
                canonicalEventId,
                dataSha256,
                sourceSetSha256,
                contentSha256,
                sourceObservations,
                relativePath,
                contentSha256);
    }

    private static J7ProviderDerivedOwnerGo.Grant backupProviderOwnerGoGrant(
            UUID canonicalEventId,
            long providerEventId,
            UUID exportId,
            String fileSha256,
            String dataSha256,
            Instant now,
            int sequence) {
        J7ProviderDerivedOwnerGo.Grant draft = new J7ProviderDerivedOwnerGo.Grant(
                UUID.nameUUIDFromBytes(
                        ("j6-backup-owner-go-" + sequence)
                                .getBytes(StandardCharsets.UTF_8)),
                "0".repeat(64),
                "WO-SS-20260904-046-j6-backup-restore-evidence",
                "docs/validation/J9-WO046-J6-BACKUP-OWNER-GO-"
                        + sequence + ".md",
                Integer.toString(sequence).repeat(64),
                "3".repeat(40),
                "4".repeat(40),
                "docs/validation/J9-OFFICIAL-PERMISSION-EVIDENCE.md",
                "5".repeat(64),
                "EVIDENCED_COMPATIBLE",
                "PASS",
                "PASS",
                "CODEX_LOCAL_UI",
                canonicalEventId,
                providerEventId,
                exportId,
                fileSha256,
                dataSha256,
                2048,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_ID,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_VERSION,
                J7ProviderDerivedOwnerGo.EXPECTED_RECEIVER_ORIGIN,
                (sequence == 1 ? "6" : "7").repeat(64),
                1,
                1,
                now.minusSeconds(30),
                now.plusSeconds(300),
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

    private static void recordJ7DecisionIntent(
            JdbcTemplate jdbcTemplate,
            UUID exportId,
            J7ExportStatus status,
            Instant decidedAt,
            String reason,
            String relativePath,
            String contentSha256,
            long contentSizeBytes) {
        assertThat(jdbcTemplate.update("""
                update export_manifest
                set decision_intent_status = ?,
                    decision_intent_at = ?,
                    decision_intent_reason = ?,
                    decision_intent_path = ?,
                    decision_intent_content_sha256 = ?,
                    decision_intent_content_size_bytes = ?
                where export_uuid = ?
                  and validation_status = 'COHERENCE_CHECKED'
                """,
                status.name(),
                java.sql.Timestamp.from(decidedAt),
                reason,
                relativePath,
                contentSha256,
                contentSizeBytes,
                exportId)).isEqualTo(1);
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
