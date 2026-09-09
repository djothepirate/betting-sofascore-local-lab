package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.*;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.*;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.*;
import com.bettingproject.sofascorelocal.domain.event.*;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.eventdetails.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.port.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL and synthetic snapshot bytes only; no application or provider network. */
@Testcontainers
class EventPeoplePersistenceIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("people_admin").withUsername("sofascore_lab").withPassword("integration-test-only");
    private static final AtomicInteger DATABASE = new AtomicInteger();
    private static final Instant RECEIVED = Instant.parse("2026-09-09T10:00:00.123456Z");
    private static final long EVENT = 46001;
    private static final CanonicalEventIdentity IDENTITY = CanonicalEventIdentity.sofascore(EVENT);
    private static final String DETAILS = """
        {"event":{"id":46001,"startTimestamp":1788948000,
        "homeTeam":{"id":1,"name":"Synthetic home","manager":{"name":"Home coach","country":{"name":"Brazil","alpha2":"BR"}}},
        "awayTeam":{"id":2,"name":"Synthetic away","manager":{"name":"Away coach","country":{"alpha2":"AR"}}},
        "status":{"type":"inprogress","description":"1st half"},"roundInfo":{"round":6,"name":"Quarter-finals"},
        "referee":{"name":"Synthetic referee","country":{"name":"France","alpha2":"FR"}}}}
        """;
    private static final String LINEUPS = """
        {"confirmed":true,"home":{"formation":"4-3-3","players":[
          {"player":{"id":101,"name":"Synthetic player","country":{"name":"Brazil","alpha2":"BR"}},
           "jerseyNumber":9,"position":"F","substitute":false,"captain":true,"statistics":{"goals":2,"goalAssist":1}}]},
         "away":{"formation":"4-4-2","players":[],"missingPlayers":[
           {"player":{"id":301,"name":"Synthetic unavailable","country":{"name":"France","alpha2":"FR"}},"description":"Knee Injury"},
           {"player":{"id":302,"name":"Unavailable without country"}}]}}
        """;

    @Test
    void upgradesPrefilledV45WithoutRewritingEvidenceAndReplaysBothParsersFromTheSameSnapshots() {
        Fixture f = fixture("45");
        Source details = f.source(DETAILS, SofascoreEndpointType.EVENT_DETAILS, "event-details-v3");
        Source lineups = f.source(LINEUPS, SofascoreEndpointType.EVENT_LINEUPS, "event-lineups-v2");
        var previousDetails = EventDetailObservation.from(IDENTITY,
                new EventDetailsV3Parser().parse(details.id, details.payload, RECEIVED).details().orElseThrow(), details.trace("event-details-v3"));
        var parsed = new EventLineupsV2Parser().parse(lineups.id, EVENT, lineups.payload, RECEIVED);
        var previousLineups = J5EventDataObservation.from(IDENTITY, parsed.data().orElseThrow(), lineups.trace("event-lineups-v2"), parsed.completeness().orElseThrow());
        long detailId = f.seedLegacyDetails(previousDetails);
        long lineupId = f.seedLegacyLineups(previousLineups);
        var before = f.evidence(true);
        var checksums = f.jdbc.queryForList("select version || ':' || checksum from flyway_schema_history order by installed_rank", String.class);
        assertThat(f.migrate("46")).isOne();
        assertThat(f.evidence(true)).isEqualTo(before);
        assertThat(f.jdbc.queryForList("select version || ':' || checksum from flyway_schema_history where version <> '46' order by installed_rank", String.class)).isEqualTo(checksums);
        assertThat(f.jdbc.queryForObject("select count(*) from event_detail_observation where home_manager_name is not null or away_manager_name is not null or referee_name is not null", Long.class)).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from j5_event_lineup_player where country_name is not null or country_alpha2 is not null", Long.class)).isZero();
        var oldDetail = f.details.findByObservationId(IDENTITY.value(), detailId).orElseThrow();
        var oldLineup = f.lineups.findByObservationId(IDENTITY.value(), SofascoreEndpointType.EVENT_LINEUPS, lineupId).orElseThrow();
        assertThat(oldDetail.details()).isEqualTo(previousDetails.details());
        assertThat(EventDetailObservation.from(IDENTITY, oldDetail.details(), oldDetail.source()).normalizedSha256()).isEqualTo(previousDetails.normalizedSha256());
        assertThat(oldLineup.data()).isEqualTo(previousLineups.data());
        assertThat(J5EventDataObservation.from(IDENTITY, oldLineup.data(), oldLineup.source(), oldLineup.completeness()).normalizedSha256()).isEqualTo(previousLineups.normalizedSha256());
        var currentDetails = f.detailsObservation(details);
        var currentLineups = f.lineupsObservation(lineups);
        assertThat(f.details.save(currentDetails).inserted()).isTrue();
        assertThat(f.details.save(currentDetails).inserted()).isFalse();
        assertThat(f.lineups.save(currentLineups).inserted()).isTrue();
        assertThat(f.lineups.save(currentLineups).inserted()).isFalse();
        f.assertLatest(currentDetails, currentLineups);
        assertThat(f.details.findHistory(IDENTITY.value())).hasSize(2);
        assertThat(f.lineups.findHistory(IDENTITY.value(), SofascoreEndpointType.EVENT_LINEUPS)).hasSize(2);
        assertThat(f.evidence(true).get("provider_snapshot")).isEqualTo(before.get("provider_snapshot"));
        assertThat(f.evidence(true).get("provider_snapshot_occurrence")).isEqualTo(before.get("provider_snapshot_occurrence"));
    }

    @Test
    void countryConstraintsProvenanceAndRollbackAreEnforcedForDirectSql() {
        Fixture f = fixture("46");
        var d = f.detailsObservation(f.source(DETAILS, SofascoreEndpointType.EVENT_DETAILS, "event-details-v4"));
        var l = f.lineupsObservation(f.source(LINEUPS, SofascoreEndpointType.EVENT_LINEUPS, "event-lineups-v4"));
        long detailId = f.details.save(d).observationId(), lineupId = f.lineups.save(l).observationId();
        for (String code : List.of("br", "1A", "", "B")) {
            assertThatThrownBy(() -> f.jdbc.update("""
                insert into j5_event_lineup_player(observation_id,side,player_order,player_provider_id,player_name,starter,country_alpha2)
                values (?,'HOME',900,900,'Synthetic invalid',false,?)
                """, lineupId, code)).hasMessageContaining("ck_j5_lineup_country_valid");
        }
        var legacy = new EventLineupsV3Parser().parse(100, EVENT, RawPayloadEvidence.capture(LINEUPS.getBytes(StandardCharsets.UTF_8)), RECEIVED);
        var legacySource = f.source(LINEUPS, SofascoreEndpointType.EVENT_LINEUPS, "event-lineups-v3");
        long legacyId = f.lineups.save(J5EventDataObservation.from(IDENTITY, legacy.data().orElseThrow(), legacySource.trace("event-lineups-v3"), legacy.completeness().orElseThrow())).observationId();
        assertThatThrownBy(() -> f.jdbc.update("""
            insert into j5_event_lineup_player(observation_id,side,player_order,player_provider_id,player_name,starter,country_alpha2)
            values (?,'HOME',900,900,'Synthetic invalid',false,'FR')
            """, legacyId)).hasMessageContaining("countries require an available event-lineups-v4");
        assertThatThrownBy(() -> f.jdbc.update("""
            insert into j5_event_lineup_side(observation_id,side,missing_players)
            select ?,'AWAY',missing_players from j5_event_lineup_side where observation_id=? and side='AWAY'
            """, legacyId, lineupId)).hasMessageContaining("countries require an available event-lineups-v4");
        for (String country : List.of("null", "{}", "{\"name\":null,\"alpha2\":null}",
                "{\"name\":\"France\",\"alpha2\":\"fr\"}", "{\"name\":\"France\",\"alpha2\":\"FR\",\"extra\":1}")) {
            assertThatThrownBy(() -> f.jdbc.update("""
                insert into j5_event_lineup_side(observation_id,side,missing_players)
                select observation_id,'AWAY',jsonb_set(missing_players,'{0,country}',?::jsonb)
                from j5_event_lineup_side where observation_id=? and side='AWAY'
                """, country, lineupId)).hasMessageContaining("ck_j5_lineup_missing_players");
        }
        assertThatThrownBy(() -> f.jdbc.update("""
            insert into event_detail_observation(canonical_event_id,source_kind,source_reference,source_snapshot_id,
              source_payload_sha256,parser_version,source_received_at,starts_at,home_team_provider_id,home_team_name,
              away_team_provider_id,away_team_name,status_type,status_description,normalized_sha256,referee_name)
            select canonical_event_id,source_kind,source_reference,source_snapshot_id,source_payload_sha256,'event-details-v3',
              source_received_at,starts_at,home_team_provider_id,home_team_name,away_team_provider_id,away_team_name,
              status_type,status_description,repeat('e',64),'Synthetic invalid' from event_detail_observation where id=?
            """, detailId)).hasMessageContaining("ck_event_detail_officials_provenance");
        assertThatThrownBy(() -> f.jdbc.update("update event_detail_observation set referee_name='Changed' where id=?", detailId)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var changed = f.lineupsObservation(f.source(LINEUPS.replace("Brazil", "Changed country"), SofascoreEndpointType.EVENT_LINEUPS, "event-lineups-v4"));
        var before = f.evidence(false);
        assertThatThrownBy(() -> new TransactionTemplate(f.transactions).executeWithoutResult(status -> {
            f.lineups.save(changed);
            throw new IllegalStateException("synthetic rollback");
        })).hasMessage("synthetic rollback");
        assertThat(f.evidence(false)).isEqualTo(before);
        new TransactionTemplate(f.transactions).executeWithoutResult(status -> {
            f.jdbc.execute("set local search_path = ''");
            assertThat(f.jdbc.queryForObject("select public.v46_valid_person('Coach','Brazil','BR')", Boolean.class)).isTrue();
            assertThat(f.jdbc.queryForObject("select public.v46_valid_person(null,'Brazil','BR')", Boolean.class)).isFalse();
            assertThat(f.jdbc.queryForObject("select public.j5_v46_valid_missing_players(missing_players) from public.j5_event_lineup_side where observation_id=? and side='AWAY'", Boolean.class, lineupId)).isTrue();
        });
    }

    @Test
    void concurrentReplayPublishesOneCompleteCountryObservation() throws Exception {
        Fixture f = fixture("46");
        var observation = f.lineupsObservation(f.source(LINEUPS, SofascoreEndpointType.EVENT_LINEUPS, "event-lineups-v4"));
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<J5EventDataPersistenceResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
                return f.lineups.save(observation);
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var a = futures.getFirst().get(15, TimeUnit.SECONDS);
            var b = futures.get(1).get(15, TimeUnit.SECONDS);
            assertThat(a.observationId()).isEqualTo(b.observationId());
            assertThat(a.inserted()).isNotEqualTo(b.inserted());
            assertThat(f.jdbc.queryForObject("select count(*) from j5_event_lineup_player", Long.class)).isOne();
            assertThat(f.lineups.findLatest(IDENTITY.value()).lineups().orElseThrow().data()).isEqualTo(observation.data());
        }
    }

    @Test
    void backupRestorePreservesBothEnrichmentsAndRecomputedHashes() throws Exception {
        Fixture source = fixture("46");
        var details = source.detailsObservation(source.source(DETAILS, SofascoreEndpointType.EVENT_DETAILS, "event-details-v4"));
        var lineups = source.lineupsObservation(source.source(LINEUPS, SofascoreEndpointType.EVENT_LINEUPS, "event-lineups-v4"));
        source.details.save(details); source.lineups.save(lineups);
        String restoredDb = "people_restore_" + DATABASE.incrementAndGet(), dump = "/tmp/people-" + UUID.randomUUID() + ".dump";
        try {
            command(POSTGRES.execInContainer("pg_dump", "-U", POSTGRES.getUsername(), "-d", source.database, "-Fc", "--no-owner", "--no-privileges", "-f", dump));
            command(POSTGRES.execInContainer("createdb", "-U", POSTGRES.getUsername(), restoredDb));
            command(POSTGRES.execInContainer("pg_restore", "-U", POSTGRES.getUsername(), "-d", restoredDb, "--exit-on-error", "--no-owner", "--no-privileges", dump));
            Fixture restored = new Fixture(restoredDb);
            assertThat(restored.evidence(false)).isEqualTo(source.evidence(false));
            assertThat(restored.migrate("46")).isZero();
            restored.assertLatest(details, lineups);
            String script = Files.readString(Path.of("scripts/Backup-Restore-J6.ps1"), StandardCharsets.UTF_8);
            String sql = script.split("\\$normalizedFingerprintSql = @'\\r?\\n", 2)[1].split("\\r?\\n'@", 2)[0];
            assertThat(restored.jdbc.queryForObject(sql, String.class)).isEqualTo(source.jdbc.queryForObject(sql, String.class));
        } finally {
            command(POSTGRES.execInContainer("dropdb", "-U", POSTGRES.getUsername(), "--if-exists", "--force", restoredDb));
            command(POSTGRES.execInContainer("rm", "-f", dump));
        }
    }

    private static void command(org.testcontainers.containers.Container.ExecResult result) {
        assertThat(result.getExitCode()).as("PostgreSQL fixture operation: %s", result.getStderr()).isZero();
    }
    private static Fixture fixture(String version) {
        String database = "people_" + DATABASE.incrementAndGet();
        new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())).execute("create database " + database);
        Fixture result = new Fixture(database); result.migrate(version);
        result.jdbc.update("insert into canonical_event(id,provider,provider_event_id) values (?,'SOFASCORE',?)", IDENTITY.value(), EVENT);
        return result;
    }
    private static <T> T transactional(T target, Class<T> type, JdbcTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }
    private record Source(long id, RawPayloadEvidence payload) {
        EventSourceTrace trace(String parser) { return EventSourceTrace.providerSnapshot(id, payload.sha256(), parser, RECEIVED); }
    }
    private static final class Fixture {
        final String database;
        final DriverManagerDataSource dataSource;
        final JdbcTemplate jdbc;
        final JdbcTransactionManager transactions;
        final RawManualCallSnapshotStore raw;
        final EventDetailsStore details;
        final J5EventDataStore lineups;
        Fixture(String database) {
            this.database = database;
            dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl().substring(0, POSTGRES.getJdbcUrl().lastIndexOf('/') + 1) + database, POSTGRES.getUsername(), POSTGRES.getPassword());
            jdbc = new JdbcTemplate(dataSource); transactions = new JdbcTransactionManager(dataSource);
            var named = new NamedParameterJdbcTemplate(dataSource);
            raw = transactional(new JdbcRawManualCallSnapshotStore(named), RawManualCallSnapshotStore.class, transactions);
            details = transactional(new JdbcEventDetailsStore(named), EventDetailsStore.class, transactions);
            lineups = transactional(new JdbcJ5EventDataStore(named), J5EventDataStore.class, transactions);
        }
        int migrate(String version) { return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(MigrationVersion.fromVersion(version)).load().migrate().migrationsExecuted; }
        Source source(String json, SofascoreEndpointType endpoint, String parser) {
            var payload = RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
            var saved = raw.save(new RawManualCallSnapshot(endpoint, endpoint.name() + "|eventId=" + EVENT, RECEIVED.minusMillis(100), RECEIVED,
                    200, "application/json", Duration.ofMillis(100), payload, parser, RawSnapshotSchemaStatus.PARSED, null));
            return new Source(saved.snapshotId(), payload);
        }
        EventDetailObservation detailsObservation(Source source) {
            return EventDetailObservation.from(IDENTITY, new EventDetailsV4Parser().parse(source.id, source.payload, RECEIVED).details().orElseThrow(), source.trace("event-details-v4"));
        }
        J5EventDataObservation lineupsObservation(Source source) {
            var parsed = new EventLineupsV4Parser().parse(source.id, EVENT, source.payload, RECEIVED);
            return J5EventDataObservation.from(IDENTITY, parsed.data().orElseThrow(), source.trace("event-lineups-v4"), parsed.completeness().orElseThrow());
        }
        void assertLatest(EventDetailObservation d, J5EventDataObservation l) {
            var actualD = details.findLatest(IDENTITY.value()).orElseThrow();
            var actualL = lineups.findLatest(IDENTITY.value()).lineups().orElseThrow();
            assertThat(actualD.details()).isEqualTo(d.details()); assertThat(actualD.source()).isEqualTo(d.source());
            assertThat(actualL.data()).isEqualTo(l.data()); assertThat(actualL.source()).isEqualTo(l.source());
            var missing = ((EventLineups) actualL.data()).away().missingPlayers().orElseThrow();
            assertThat(missing.getFirst().country().orElseThrow().alpha2()).contains("FR");
            assertThat(missing.get(1).country()).isEmpty();
            assertThat(EventDetailObservation.from(IDENTITY, actualD.details(), actualD.source()).normalizedSha256()).isEqualTo(d.normalizedSha256());
            assertThat(J5EventDataObservation.from(IDENTITY, actualL.data(), actualL.source(), actualL.completeness()).normalizedSha256()).isEqualTo(l.normalizedSha256());
        }
        long seedLegacyDetails(EventDetailObservation observation) {
            var d = observation.details(); var s = observation.source();
            return jdbc.queryForObject("""
                insert into event_detail_observation(canonical_event_id,source_kind,source_reference,source_snapshot_id,source_payload_sha256,
                    parser_version,source_received_at,starts_at,home_team_provider_id,home_team_name,away_team_provider_id,away_team_name,
                    status_type,status_description,event_round,normalized_sha256)
                values (?,'PROVIDER_SNAPSHOT',?,?,?,?,?,?,1,'Synthetic home',2,'Synthetic away','inprogress','1st half',?,?) returning id
                """, Long.class, IDENTITY.value(), s.sourceReference(), s.snapshotId().orElseThrow(), s.payloadSha256(), s.parserVersion(),
                    Timestamp.from(RECEIVED), Timestamp.from(d.startsAt()), d.round().orElse(null), observation.normalizedSha256());
        }
        long seedLegacyLineups(J5EventDataObservation observation) {
            return new TransactionTemplate(transactions).execute(status -> {
                var s = observation.source(); var c = observation.completeness();
                long id = jdbc.queryForObject("""
                    insert into j5_event_data_observation(canonical_event_id,endpoint_type,source_kind,source_reference,source_snapshot_id,
                        source_payload_sha256,parser_version,source_received_at,completeness_status,completeness_score,present_signals,
                        expected_signals,missing_paths_json,lineups_confirmed,normalized_sha256)
                    values (?,'EVENT_LINEUPS','PROVIDER_SNAPSHOT',?,?,?,?,?,?,?,?,?,?::jsonb,true,?) returning id
                    """, Long.class, IDENTITY.value(), s.sourceReference(), s.snapshotId().orElseThrow(), s.payloadSha256(), s.parserVersion(), Timestamp.from(RECEIVED),
                        c.status().name(), c.scorePercent(), c.presentSignals(), c.expectedSignals(), tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(c.missingPaths()), observation.normalizedSha256());
                jdbc.update("insert into j5_event_lineup_side(observation_id,side,formation) values (?,'HOME','4-3-3'),(?,'AWAY','4-4-2')", id, id);
                jdbc.update("insert into j5_event_lineup_player(observation_id,side,player_order,player_provider_id,player_name,shirt_number,position,starter) values (?,'HOME',0,101,'Synthetic player',9,'F',true)", id);
                return id;
            });
        }
        Map<String,List<String>> evidence(boolean omitV46) {
            var result = new LinkedHashMap<String,List<String>>();
            for (String table : List.of("provider_snapshot","provider_snapshot_occurrence","event_detail_observation","j5_event_data_observation","j5_event_lineup_side","j5_event_lineup_player")) {
                String expression = "to_jsonb(t)";
                if (omitV46 && table.equals("event_detail_observation")) for (String p : List.of("home_manager","away_manager","referee"))
                    expression += " - '" + p + "_name' - '" + p + "_country_name' - '" + p + "_country_alpha2'";
                if (omitV46 && table.equals("j5_event_lineup_player")) expression += " - 'country_name' - 'country_alpha2'";
                result.put(table, jdbc.queryForList("select (" + expression + ")::text from " + table + " t order by (" + expression + ")::text", String.class));
            }
            return result;
        }
    }
}
