package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV4Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV5Parser;
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
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J3ScheduledEventsPageCache;
import com.bettingproject.sofascorelocal.port.J4EventDetailsCache;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
        assertThat(flywayVersion).isEqualTo("12");
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
        j5EventDataStore.save(J5EventDataObservation.from(
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
                                assertThat(incident.minute()).isEqualTo(58);
                                assertThat(incident.incidentClass()).contains("yellow");
                                assertThat(incident.reason()).contains("Argument");
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
