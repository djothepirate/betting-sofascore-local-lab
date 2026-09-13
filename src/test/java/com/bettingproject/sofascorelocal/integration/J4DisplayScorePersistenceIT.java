package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.JdbcCanonicalEventStore;
import com.bettingproject.sofascorelocal.adapter.persistence.JdbcEventDetailsStore;
import com.bettingproject.sofascorelocal.adapter.persistence.JdbcRawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV3Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic bytes in isolated PostgreSQL databases; no application, browser or provider call. */
@Testcontainers
class J4DisplayScorePersistenceIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("j4_display_admin").withUsername("sofascore_lab")
            .withPassword("integration-test-only");
    private static final Instant RECEIVED_AT = Instant.parse("2026-09-08T09:00:00Z");
    private static final long EVENT_ID = 38001;

    @Test
    void upgradesV37WithoutChangingHistoricalObservationsAndReplaysV3WithExactSnapshotProvenance() {
        Fixture f = fixture();
        assertThat(f.migrate("37").migrationsExecuted).isEqualTo(37);
        RawPayloadEvidence payload = payload();
        var raw = f.raw.save(new RawManualCallSnapshot(SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=" + EVENT_ID, RECEIVED_AT.minusMillis(100), RECEIVED_AT,
                200, "application/json", Duration.ofMillis(100), payload,
                EventDetailsV2Parser.PARSER_VERSION, RawSnapshotSchemaStatus.RAW_ONLY, null));
        var parsedV2 = new EventDetailsV2Parser().parse(raw.snapshotId(), payload, RECEIVED_AT);
        var detailsV2 = parsedV2.details().orElseThrow();
        var sourceV2 = EventSourceTrace.providerSnapshot(raw.snapshotId(), raw.payloadSha256(),
                EventDetailsV2Parser.PARSER_VERSION, RECEIVED_AT);
        var identity = CanonicalEventIdentity.sofascore(EVENT_ID);
        f.canonical.save(CanonicalEventObservation.from(detailsV2.asScheduledEvent(), sourceV2));
        var observationV2 = EventDetailObservation.from(identity, detailsV2, sourceV2);
        // Seed through the actual V37 columns, never through the current V38 JDBC writer.
        Long historicalId = f.jdbc.queryForObject("""
                insert into event_detail_observation (
                    canonical_event_id, source_kind, source_reference, source_snapshot_id,
                    source_payload_sha256, parser_version, source_received_at, starts_at,
                    home_team_provider_id, home_team_name, away_team_provider_id, away_team_name,
                    status_type, status_description, normalized_sha256
                ) values (?, 'PROVIDER_SNAPSHOT', ?, ?, ?, 'event-details-v2', ?, ?,
                          1, 'Synthetic Home', 2, 'Synthetic Away', 'postponed', 'Postponed', ?)
                returning id
                """, Long.class, identity.value(), sourceV2.sourceReference(), raw.snapshotId(),
                raw.payloadSha256(), Timestamp.from(RECEIVED_AT), Timestamp.from(detailsV2.startsAt()),
                observationV2.normalizedSha256());
        f.raw.classify(raw.snapshotId(), RawSnapshotSchemaStatus.PARSED, null);
        Map<String, Object> historicalBefore = f.jdbc.queryForMap(
                "select * from event_detail_observation where id = ?", historicalId);
        Map<String, List<Map<String, Object>>> provenanceBefore = new LinkedHashMap<>();
        for (String table : List.of("provider_snapshot", "provider_snapshot_occurrence",
                "canonical_event", "canonical_event_observation")) {
            provenanceBefore.put(table, f.rows(table));
        }
        assertThat(f.parserConstraint()).doesNotContain("event-details-v3");

        assertThat(f.migrate("38").migrationsExecuted).isOne();
        Map<String, Object> expectedHistorical = new LinkedHashMap<>(historicalBefore);
        expectedHistorical.put("is_awarded", null);
        expectedHistorical.put("home_display_score", null);
        expectedHistorical.put("away_display_score", null);
        assertThat(f.jdbc.queryForMap("select * from event_detail_observation where id = ?", historicalId))
                .isEqualTo(expectedHistorical);
        provenanceBefore.forEach((table, rows) -> assertThat(f.rows(table)).isEqualTo(rows));
        assertThat(f.parserConstraint()).contains("event-details-v1", "event-details-v2", "event-details-v3");
        assertRejected(f, historicalId, "event-details-v4", null, null, null);
        // Keep the V38 upgrade proof above; use today's schema for today's JDBC reader.
        assertThat(f.migrate("46").migrationsExecuted).isEqualTo(8);
        for (String person : List.of("home_manager", "away_manager", "referee")) {
            expectedHistorical.put(person + "_name", null);
            expectedHistorical.put(person + "_country_name", null);
            expectedHistorical.put(person + "_country_alpha2", null);
        }
        assertThat(f.details.findByObservationId(identity.value(), historicalId)).hasValueSatisfying(view -> {
            assertThat(view.details()).isEqualTo(detailsV2);
            assertThat(view.source()).isEqualTo(sourceV2);
            assertThat(view.normalizedSha256()).isEqualTo(observationV2.normalizedSha256());
        });

        var parsedV3 = new EventDetailsV3Parser().parse(raw.snapshotId(), payload, RECEIVED_AT);
        var sourceV3 = EventSourceTrace.providerSnapshot(raw.snapshotId(), raw.payloadSha256(),
                EventDetailsV3Parser.PARSER_VERSION, RECEIVED_AT);
        var observationV3 = EventDetailObservation.from(identity, parsedV3.details().orElseThrow(), sourceV3);
        var first = f.details.save(observationV3);
        var repeated = f.details.save(observationV3);
        assertThat(first.inserted()).isTrue();
        assertThat(repeated.inserted()).isFalse();
        assertThat(repeated.observationId()).isEqualTo(first.observationId()).isNotEqualTo(historicalId);
        assertThat(observationV3.normalizedSha256()).isNotEqualTo(observationV2.normalizedSha256());
        assertThat(f.details.findLatest(identity.value())).hasValueSatisfying(view -> {
            assertThat(view.observationId()).isEqualTo(first.observationId());
            assertThat(view.details()).isEqualTo(observationV3.details());
            assertThat(view.details().isAwarded()).contains(false);
            assertThat(view.details().homeDisplayScore()).contains(0);
            assertThat(view.details().awayDisplayScore()).contains(2);
            assertThat(view.source()).isEqualTo(sourceV3);
            assertThat(view.normalizedSha256()).isEqualTo(observationV3.normalizedSha256());
        });
        assertThat(f.details.findHistory(identity.value())).extracting(view -> view.observationId())
                .containsExactly(first.observationId(), historicalId);
        assertThat(f.details.findByObservationId(identity.value(), first.observationId()))
                .isEqualTo(f.details.findLatest(identity.value()));
        provenanceBefore.forEach((table, rows) -> assertThat(f.rows(table)).isEqualTo(rows));
        assertThat(f.jdbc.queryForMap("select * from event_detail_observation where id = ?", historicalId))
                .isEqualTo(expectedHistorical);
        assertThat(f.migrate("46").migrationsExecuted).isZero();

        // Invalid direct SQL cannot bypass the same version and value bounds as the domain/parser.
        assertRejected(f, first.observationId(), "event-details-v5", null, null, null);
        assertRejected(f, first.observationId(), "event-details-v2", false, null, null);
        assertRejected(f, first.observationId(), "event-details-v1", null, 0, null);
        assertRejected(f, first.observationId(), "event-details-v2", null, null, 0);
        assertRejected(f, first.observationId(), "event-details-v3", true, -1, 2);
        assertRejected(f, first.observationId(), "event-details-v3", true, 0, 1000);
        assertThatThrownBy(() -> f.jdbc.update(
                "update event_detail_observation set is_awarded = true where id = ?", first.observationId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> f.jdbc.update(
                "delete from event_detail_observation where id = ?", historicalId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(f.jdbc.queryForObject("select count(*) from event_detail_observation", Long.class)).isEqualTo(2);
    }

    @Test
    void installsV38FromAnEmptyIsolatedDatabaseWithoutBackfilledObservations() {
        Fixture f = fixture();
        assertThat(f.migrate("38").migrationsExecuted).isEqualTo(38);
        assertThat(f.jdbc.queryForObject("select count(*) from event_detail_observation", Long.class)).isZero();
        assertThat(f.jdbc.queryForList("""
                select column_name, is_nullable, column_default
                from information_schema.columns
                where table_schema = 'public' and table_name = 'event_detail_observation'
                  and column_name in ('is_awarded', 'home_display_score', 'away_display_score')
                """)).hasSize(3).allSatisfy(row -> {
                    assertThat(row.get("is_nullable")).isEqualTo("YES");
                    assertThat(row.get("column_default")).isNull();
                });
        assertThat(f.migrate("38").migrationsExecuted).isZero();
    }

    private static void assertRejected(Fixture f, long sourceId, String parser, Boolean awarded,
            Integer home, Integer away) {
        assertThatThrownBy(() -> f.jdbc.update("""
                insert into event_detail_observation (
                    canonical_event_id, source_kind, source_reference, source_snapshot_id,
                    source_payload_sha256, parser_version, source_received_at, starts_at,
                    home_team_provider_id, home_team_name, away_team_provider_id, away_team_name,
                    status_type, status_description, normalized_sha256,
                    is_awarded, home_display_score, away_display_score
                ) select canonical_event_id, source_kind, source_reference, source_snapshot_id,
                         source_payload_sha256, ?, source_received_at, starts_at,
                         home_team_provider_id, home_team_name, away_team_provider_id, away_team_name,
                         status_type, status_description, repeat('c', 64), ?, ?, ?
                  from event_detail_observation where id = ?
                """, parser, awarded, home, away, sourceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static RawPayloadEvidence payload() {
        return RawPayloadEvidence.capture("""
                {"event":{"id":38001,"startTimestamp":1786793400,
                "homeTeam":{"id":1,"name":"Synthetic Home"},
                "awayTeam":{"id":2,"name":"Synthetic Away"},
                "status":{"type":"postponed","description":"Postponed"},
                "isAwarded":false,"homeScore":{"display":0},"awayScore":{"display":2}}}
                """.getBytes(StandardCharsets.UTF_8));
    }

    private static Fixture fixture() {
        // V31 has explicitly public functions: a separate schema in the same DB is insufficient.
        String database = "j4_display_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new JdbcTemplate(admin).execute("create database " + database);
        String url = POSTGRES.getJdbcUrl().substring(0, POSTGRES.getJdbcUrl().lastIndexOf('/') + 1) + database;
        Fixture fixture = new Fixture(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertThat(fixture.jdbc.queryForObject("select current_database()", String.class)).isEqualTo(database);
        return fixture;
    }

    private static <T> T transactional(T object, Class<T> type, JdbcTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(object);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }

    private static final class Fixture {
        final DriverManagerDataSource dataSource;
        final JdbcTemplate jdbc;
        final RawManualCallSnapshotStore raw;
        final CanonicalEventStore canonical;
        final EventDetailsStore details;

        Fixture(DriverManagerDataSource dataSource) {
            this.dataSource = dataSource;
            this.jdbc = new JdbcTemplate(dataSource);
            var named = new NamedParameterJdbcTemplate(dataSource);
            var transactionManager = new JdbcTransactionManager(dataSource);
            raw = transactional(new JdbcRawManualCallSnapshotStore(named), RawManualCallSnapshotStore.class, transactionManager);
            canonical = transactional(new JdbcCanonicalEventStore(named), CanonicalEventStore.class, transactionManager);
            details = transactional(new JdbcEventDetailsStore(named), EventDetailsStore.class, transactionManager);
        }

        org.flywaydb.core.api.output.MigrateResult migrate(String version) {
            return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion(version)).load().migrate();
        }

        List<Map<String, Object>> rows(String table) {
            return jdbc.queryForList("select to_jsonb(t)::text as row from " + table + " t order by to_jsonb(t)::text");
        }

        String parserConstraint() {
            return jdbc.queryForObject("""
                    select pg_get_constraintdef(oid) from pg_constraint
                    where conrelid = 'event_detail_observation'::regclass
                      and conname = 'ck_event_detail_observation_parser'
                    """, String.class);
        }
    }
}
