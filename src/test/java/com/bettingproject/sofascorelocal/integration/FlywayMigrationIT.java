package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
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
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.network.J3QualificationCheckpointReparser;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspectionService;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J4ScheduledEventsSnapshotNormalizationService;
import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
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
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.retention.J6BackupEvidence;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J3ScheduledEventsPageCache;
import com.bettingproject.sofascorelocal.port.J4EventDetailsCache;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.J6SnapshotHistoryStore;
import com.bettingproject.sofascorelocal.port.J6RawPayloadRetentionStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.J3QualificationCheckpointStore;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    J4EventDetailsCache eventDetailsCache;

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
    J5EventDataStore j5EventDataStore;

    @Autowired
    J6SnapshotHistoryStore j6SnapshotHistoryStore;

    @Autowired
    J6RawPayloadRetentionStore j6RawPayloadRetentionStore;

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
        assertThat(flywayVersion).isEqualTo("22");
        assertThat(rawColumn).isEqualTo("bytea");
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
                    assertThat(trace.occurrenceCount()).isEqualTo(2);
                    assertThat(trace.deduplicatedOccurrenceCount()).isEqualTo(1);
                    assertThat(trace.latestOutcome().name()).isEqualTo("DEDUPLICATED");
                    assertThat(trace.rawPayloadState().name()).isEqualTo("RETAINED");
                })
                .hasEntrySatisfying(secondVersion.snapshotId(), trace ->
                        assertThat(trace.occurrenceCount()).isEqualTo(1));
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
                "select count(*) from j5_event_data_observation",
                Long.class)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from j5_event_metric",
                Long.class)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from j5_event_incident",
                Long.class)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from j5_event_lineup_side",
                Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from j5_event_lineup_player",
                Long.class)).isEqualTo(4L);

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
                "select count(*) from j5_event_data_observation",
                Long.class)).isEqualTo(6L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from j5_event_metric",
                Long.class)).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from j5_event_incident",
                Long.class)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from j5_event_lineup_side",
                Long.class)).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from j5_event_lineup_player",
                Long.class)).isEqualTo(5L);
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
                EventDetailsV2Parser.PARSER_VERSION,
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
                            .isEqualTo(EventDetailsV2Parser.PARSER_VERSION);
                    assertThat(detail.details().homeTeam().name())
                            .isEqualTo("Saint-Etienne");
                });
        assertThat(eventDetailsCache.findFreshParsed(
                request,
                response.receivedAt(),
                Duration.ofMinutes(15),
                EventDetailsV2Parser.PARSER_VERSION))
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
                .hasEntrySatisfying(snapshot.snapshotId(), trace ->
                        assertThat(trace.rawPayloadState().name())
                                .isEqualTo("PAYLOAD_PURGED"));
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
