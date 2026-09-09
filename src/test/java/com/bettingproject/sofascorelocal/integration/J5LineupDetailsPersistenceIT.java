package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.JdbcJ5EventDataStore;
import com.bettingproject.sofascorelocal.adapter.persistence.JdbcRawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV3Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** Isolated PostgreSQL migration, replay and restore; no application, browser or provider request. */
@Testcontainers
class J5LineupDetailsPersistenceIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("lineup_details_admin").withUsername("sofascore_lab").withPassword("integration-test-only");
    private static final AtomicInteger DATABASE = new AtomicInteger();
    private static final long EVENT = 90_041;
    private static final CanonicalEventIdentity IDENTITY = CanonicalEventIdentity.sofascore(EVENT);
    private static final Instant RECEIVED = Instant.parse("2026-09-09T01:00:00.123456Z");
    private static final String PAYLOAD = """
        {"confirmed":true,
         "home":{"formation":"4-3-3","players":[
           {"player":{"id":101,"name":"Synthetic captain"},"jerseyNumber":1,"position":"G","substitute":false,
            "captain":true,"statistics":{"rating":7.100000000000000000000000000001,
                "expectedGoals":0.00000000000000000000000000000001,"minutesPlayed":0,
                "ratingVersions":{"original":7.010000000000000000000000000009}}},
           {"player":{"id":102,"name":"Synthetic substitute"},"jerseyNumber":12,"position":"M","substitute":true,
            "captain":false,"statistics":{}},
           {"player":{"id":103,"name":"Synthetic player"},"jerseyNumber":3,"position":"D","substitute":false}],
           "missingPlayers":[
             {"player":{"id":203,"name":"Second identifier first"},"jerseyNumber":8,"position":"M",
              "type":"missing","reason":0,"description":"Synthetic absence","externalType":-1,
              "expectedEndDate":"2026-10-09T14:15:16.123456789+05:30"},
             {"player":{"id":201,"name":"First identifier second"},"reason":-2147483648,"externalType":2147483647}]},
         "away":{"formation":"4-4-2","players":[],"missingPlayers":[]}}
        """;

    @Test
    void upgradesV40WithoutBackfillAndReplaysTheSameSnapshotWithoutRewritingItsEarlierInterpretation() {
        Fixture f = fixture("40");
        Source source = f.source(PAYLOAD, EventLineupsV2Parser.PARSER_VERSION);
        var parsed = new EventLineupsV2Parser().parse(source.snapshotId(), EVENT, source.payload(), RECEIVED);
        assertThat(parsed.data()).isPresent();
        J5EventDataObservation previous = J5EventDataObservation.from(IDENTITY, parsed.data().orElseThrow(),
                source.trace(EventLineupsV2Parser.PARSER_VERSION), parsed.completeness().orElseThrow());
        long previousId = f.insertLegacy(previous);
        List<String> checksums = f.jdbc.queryForList(
                "select version || ':' || checksum from flyway_schema_history where version is not null order by installed_rank", String.class);
        Map<String, List<String>> before = f.evidence(false);

        assertThat(f.migrate("41")).isEqualTo(1);
        assertThat(f.evidence(true)).isEqualTo(before);
        assertThat(f.jdbc.queryForList("select version || ':' || checksum from flyway_schema_history where version <> '41' order by installed_rank", String.class))
                .isEqualTo(checksums);
        assertThat(f.jdbc.queryForObject("select count(*) from j5_event_lineup_player where captain is not null or statistics is not null", Long.class)).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from j5_event_lineup_side where missing_players is not null", Long.class)).isZero();
        J5EventDataObservationView reread = f.store.findByObservationId(IDENTITY.value(), SofascoreEndpointType.EVENT_LINEUPS, previousId).orElseThrow();
        assertThat(reread.data()).isEqualTo(previous.data());
        assertThat(reread.source()).isEqualTo(previous.source());
        assertThat(recomputed(reread)).isEqualTo(previous.normalizedSha256());
        assertThatThrownBy(() -> f.jdbc.update("""
                insert into j5_event_lineup_player(observation_id,side,player_order,player_provider_id,player_name,starter,captain)
                values (?,'HOME',900,900,'Invalid V2 captain',false,false)
                """, previousId)).hasMessageContaining("require an available event-lineups-v3");
        assertThatThrownBy(() -> f.jdbc.update("""
                insert into j5_event_lineup_player(observation_id,side,player_order,player_provider_id,player_name,starter,statistics)
                values (?,'HOME',900,900,'Invalid V2 statistics',false,'{"values":{},"ratingVersions":{}}')
                """, previousId)).hasMessageContaining("require an available event-lineups-v3");
        assertThatThrownBy(() -> f.jdbc.update("""
                insert into j5_event_lineup_side(observation_id,side,missing_players) values (?,'HOME','[]')
                """, previousId)).hasMessageContaining("require an available event-lineups-v3");

        J5EventDataObservation current = f.observation(source);
        assertThat(current.normalizedSha256()).isNotEqualTo(previous.normalizedSha256());
        var inserted = f.store.save(current);
        var repeated = f.store.save(current);
        assertThat(inserted.inserted()).isTrue();
        assertThat(repeated.inserted()).isFalse();
        assertThat(repeated.observationId()).isEqualTo(inserted.observationId());
        assertThat(f.store.findHistory(IDENTITY.value(), SofascoreEndpointType.EVENT_LINEUPS)).hasSize(2);
        assertThat(f.store.findByObservationId(IDENTITY.value(), SofascoreEndpointType.EVENT_LINEUPS, previousId)).contains(reread);
        assertThat(f.jdbc.queryForObject("select parser_version from provider_snapshot where id=?", String.class, source.snapshotId()))
                .isEqualTo("event-lineups-v2");
        assertThat(f.evidence(true).get("provider_snapshot")).isEqualTo(before.get("provider_snapshot"));
        assertThat(f.evidence(true).get("provider_snapshot_occurrence")).isEqualTo(before.get("provider_snapshot_occurrence"));
        assertEnriched(f.store.findByObservationId(IDENTITY.value(), SofascoreEndpointType.EVENT_LINEUPS,
                inserted.observationId()).orElseThrow(), current);
        assertThat(f.migrate("41")).isZero();
    }

    @Test
    void preservesUnknownAndExplicitEmptyValuesAndExactDecimalAndOffsetDateValues() {
        Fixture f = fixture("41");
        var enriched = f.observation(f.source(PAYLOAD, "event-lineups-v3"));
        var inserted = f.store.save(enriched);
        assertEnriched(f.store.findLatest(IDENTITY.value()).lineups().orElseThrow(), enriched);
        assertThat(f.jdbc.queryForList("""
                select player_provider_id, captain, statistics is null as statistics_absent,
                       statistics -> 'values' as values
                from j5_event_lineup_player where observation_id=? order by player_order
                """, inserted.observationId())).satisfies(rows -> {
                    assertThat(rows.get(0)).containsEntry("captain", true).containsEntry("statistics_absent", false);
                    assertThat(rows.get(1)).containsEntry("captain", false).containsEntry("statistics_absent", false);
                    assertThat(rows.get(2)).containsEntry("captain", null).containsEntry("statistics_absent", true);
                });
        Source absent = f.source("""
                {"confirmed":false,"home":{"players":[]},"away":{"players":[]}}
                """, "event-lineups-v3");
        Source empty = f.source("""
                {"confirmed":false,"home":{"players":[],"missingPlayers":[]},"away":{"players":[]}}
                """, "event-lineups-v3");
        var absentObservation = f.observation(absent);
        var emptyObservation = f.observation(empty);
        assertThat(absentObservation.normalizedSha256()).isNotEqualTo(emptyObservation.normalizedSha256());
        var absentId = f.store.save(absentObservation).observationId();
        var emptyId = f.store.save(emptyObservation).observationId();
        var absentRead = (EventLineups) f.store.findByObservationId(IDENTITY.value(), SofascoreEndpointType.EVENT_LINEUPS, absentId).orElseThrow().data();
        var emptyRead = (EventLineups) f.store.findByObservationId(IDENTITY.value(), SofascoreEndpointType.EVENT_LINEUPS, emptyId).orElseThrow().data();
        assertThat(absentRead.home().missingPlayers()).isEmpty();
        assertThat(emptyRead.home().missingPlayers()).contains(List.of());
        assertThat(emptyRead.away().missingPlayers()).isEmpty();
    }

    @Test
    void concurrentDuplicateReservationsPublishOneCompleteObservationAndRollbackLeavesNoChildren() throws Exception {
        Fixture f = fixture("41");
        var observation = f.observation(f.source(PAYLOAD, "event-lineups-v3"));
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<java.util.concurrent.Future<J5EventDataPersistenceResult>>();
            for (int i = 0; i < 2; i++) tasks.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start not released");
                return f.store.save(observation);
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = tasks.get(0).get(15, TimeUnit.SECONDS);
            var second = tasks.get(1).get(15, TimeUnit.SECONDS);
            assertThat(first.observationId()).isEqualTo(second.observationId());
            assertThat(first.inserted()).isNotEqualTo(second.inserted());
            assertThat(f.jdbc.queryForObject("select count(*) from j5_event_lineup_player", Long.class)).isEqualTo(3);
            assertEnriched(f.store.findLatest(IDENTITY.value()).lineups().orElseThrow(), observation);
        }
        Map<String, List<String>> committed = f.evidence(false);
        var changed = f.observation(f.source(PAYLOAD.replace("Synthetic captain", "Changed captain"), "event-lineups-v3"));
        // Snapshot reception is a separate durable action; only normalization belongs to this rollback.
        Map<String, List<String>> before = f.evidence(false);
        assertThatThrownBy(() -> new TransactionTemplate(f.transactions).executeWithoutResult(status -> {
            f.store.save(changed);
            throw new IllegalStateException("synthetic rollback");
        })).hasMessage("synthetic rollback");
        assertThat(f.evidence(false)).isEqualTo(before);
        assertThat(f.evidence(false).get("j5_event_lineup_player")).isEqualTo(committed.get("j5_event_lineup_player"));
    }

    @Test
    void constraintsBoundJsonAndTheExistingAppendOnlyAndJ7LocksRemainInForce() throws Exception {
        Fixture f = fixture("41");
        var inserted = f.store.save(f.observation(f.source(PAYLOAD, "event-lineups-v3")));
        long id = inserted.observationId();
        for (String invalid : List.of("[]", "null", "{}", "{\"values\":{},\"ratingVersions\":{},\"raw\":{}}",
                "{\"values\":{\"rating\":\"7.1\"},\"ratingVersions\":{}}",
                "{\"values\":{\"rating\":1e-33},\"ratingVersions\":{}}",
                "{\"values\":{\"rating\":1e33},\"ratingVersions\":{}}",
                "{\"values\":{\"unsafe key\":1},\"ratingVersions\":{}}")) {
            assertThatThrownBy(() -> f.jdbc.update("""
                    insert into j5_event_lineup_player(observation_id,side,player_order,player_provider_id,player_name,starter,statistics)
                    values (?,'HOME',900,900,'Invalid synthetic player',false,?::jsonb)
                    """, id, invalid)).hasMessageContaining("ck_j5_lineup_player_statistics");
        }
        for (String invalid : List.of("{}", "null", "[{}]", "[true]")) {
            assertThat(f.jdbc.queryForObject("select j5_v41_valid_missing_players(?::jsonb)", Boolean.class, invalid)).isFalse();
            assertThatThrownBy(() -> f.jdbc.update("insert into j5_event_lineup_side(observation_id,side,missing_players) values (?,'HOME',?::jsonb)", id, invalid))
                    .hasMessageContaining("ck_j5_lineup_missing_players");
        }
        for (String invalidDate : List.of("2026-99-99T14:00Z", "2026-02-29T14:00Z", "2024-02-30T14:00Z",
                "2026-01-01T24:00Z", "2026-01-01T14:00+18:01", "2026-01-01T14:00")) {
            assertThat(f.jdbc.queryForObject("select j5_v41_valid_offset_datetime(to_jsonb(?::text))", Boolean.class, invalidDate)).isFalse();
        }
        for (String validDate : List.of("2024-02-29T14:00Z", "2026-01-01T14:00:00.123456789+05:30",
                "+999999999-12-31T23:59:59-18:00", "-0004-02-29T00:00Z")) {
            assertThat(f.jdbc.queryForObject("select j5_v41_valid_offset_datetime(to_jsonb(?::text))", Boolean.class, validDate)).isTrue();
        }
        assertThat(f.jdbc.queryForObject("select j5_v41_valid_decimal_map(jsonb_object_agg('field' || n, 0),128) from generate_series(1,129) n", Boolean.class)).isFalse();
        assertThat(f.jdbc.queryForObject("select j5_v41_valid_decimal_map('{\"n\":100000000000000000000000000000000}'::jsonb,128)", Boolean.class)).isTrue();
        new TransactionTemplate(f.transactions).executeWithoutResult(status -> {
            f.jdbc.execute("set local search_path = ''");
            assertThat(f.jdbc.queryForObject("""
                    select public.j5_v41_valid_player_statistics(statistics)
                    from public.j5_event_lineup_player where observation_id=? and player_order=0 and side='HOME'
                    """, Boolean.class, id)).isTrue();
            assertThat(f.jdbc.queryForObject("""
                    select public.j5_v41_valid_missing_players(missing_players)
                    from public.j5_event_lineup_side where observation_id=? and side='HOME'
                    """, Boolean.class, id)).isTrue();
        });
        assertThatThrownBy(() -> f.jdbc.update("update j5_event_lineup_player set captain=false where observation_id=?", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> f.jdbc.update("delete from j5_event_lineup_side where observation_id=?", id))
                .hasMessageContaining("append-only");
        assertThat(f.jdbc.queryForList("select tgname from pg_trigger where tgrelid='j5_event_lineup_player'::regclass and not tgisinternal", String.class))
                .contains("j5_event_lineup_player_append_only", "j5_event_lineup_player_j7_source_lock");
        assertThat(f.jdbc.queryForList("select tgname from pg_trigger where tgrelid='j5_event_lineup_side'::regclass and not tgisinternal", String.class))
                .contains("j5_event_lineup_side_append_only", "j5_event_lineup_side_j7_source_lock");
    }

    @Test
    void backupRestoreRetainsNewPlayerDataSourceEvidenceAndExactRecomputedHashes() throws Exception {
        Fixture source = fixture("41");
        var observation = source.observation(source.source(PAYLOAD, "event-lineups-v3"));
        source.store.save(observation);
        String restoreDatabase = "lineup_restore_" + DATABASE.incrementAndGet();
        String dump = "/tmp/lineup-details-" + UUID.randomUUID() + ".dump";
        Map<String, List<String>> before = source.evidence(false);
        try {
            assertContainerCommandSucceeded("pg_dump", POSTGRES.execInContainer("pg_dump", "--username", POSTGRES.getUsername(), "--dbname", source.database,
                    "--format=custom", "--no-owner", "--no-privileges", "--file", dump));
            assertContainerCommandSucceeded("createdb", POSTGRES.execInContainer("createdb", "--username", POSTGRES.getUsername(), restoreDatabase));
            assertContainerCommandSucceeded("pg_restore", POSTGRES.execInContainer("pg_restore", "--username", POSTGRES.getUsername(), "--dbname", restoreDatabase,
                    "--exit-on-error", "--no-owner", "--no-privileges", dump));
            Fixture restored = new Fixture(restoreDatabase);
            assertThat(restored.evidence(false)).isEqualTo(before);
            assertThat(restored.migrate("41")).isZero();
            assertEnriched(restored.store.findLatest(IDENTITY.value()).lineups().orElseThrow(), observation);
            String script = Files.readString(Path.of("scripts/Backup-Restore-J6.ps1"), StandardCharsets.UTF_8);
            String sql = script.split("\\$normalizedFingerprintSql = @'\\r?\\n", 2)[1].split("\\r?\\n'@", 2)[0];
            assertThat(restored.jdbc.queryForObject(sql, String.class)).isEqualTo(source.jdbc.queryForObject(sql, String.class));
        }
        finally {
            assertContainerCommandSucceeded("dropdb", POSTGRES.execInContainer("dropdb", "--username", POSTGRES.getUsername(), "--if-exists", "--force", restoreDatabase));
            assertContainerCommandSucceeded("remove temporary dump", POSTGRES.execInContainer("rm", "-f", dump));
        }
    }

    private static void assertContainerCommandSucceeded(String operation,
            org.testcontainers.containers.Container.ExecResult result) {
        assertThat(result.getExitCode())
                .as("%s; stderr: %s; stdout: %s", operation, result.getStderr(), result.getStdout())
                .isZero();
    }

    private static void assertEnriched(J5EventDataObservationView actual, J5EventDataObservation expected) {
        assertThat(actual.source()).isEqualTo(expected.source());
        assertThat(actual.completeness()).isEqualTo(expected.completeness());
        assertThat(actual.normalizedSha256()).isEqualTo(expected.normalizedSha256());
        assertThat(recomputed(actual)).isEqualTo(expected.normalizedSha256());
        assertThat(actual.data()).isEqualTo(expected.data());
        EventLineups lineup = (EventLineups) actual.data();
        assertThat(lineup.home().players()).extracting(EventLineupPlayer::captain)
                .containsExactly(Optional.of(true), Optional.of(false), Optional.empty());
        PlayerMatchStatistics stats = lineup.home().players().getFirst().statistics().orElseThrow();
        assertThat(stats.values().get("rating")).isEqualByComparingTo("7.100000000000000000000000000001");
        assertThat(stats.values().get("expectedGoals")).isEqualByComparingTo("0.00000000000000000000000000000001");
        assertThat(stats.values().get("minutesPlayed")).isEqualTo(BigDecimal.ZERO);
        assertThat(stats.ratingVersions().get("original")).isEqualByComparingTo("7.010000000000000000000000000009");
        assertThat(lineup.home().players().get(1).statistics()).contains(new PlayerMatchStatistics(Map.of(), Map.of()));
        assertThat(lineup.home().players().get(2).statistics()).isEmpty();
        assertThat(lineup.home().missingPlayers().orElseThrow()).extracting(MissingLineupPlayer::providerPlayerId).containsExactly(203L, 201L);
        assertThat(lineup.home().missingPlayers().orElseThrow().getFirst().expectedEndDate())
                .contains(OffsetDateTime.parse("2026-10-09T14:15:16.123456789+05:30"));
        assertThat(lineup.away().missingPlayers()).contains(List.of());
    }

    private static String recomputed(J5EventDataObservationView value) {
        return J5EventDataObservation.from(value.identity(), value.data(), value.source(), value.completeness()).normalizedSha256();
    }

    private static Fixture fixture(String version) {
        String database = "lineup_details_" + DATABASE.incrementAndGet();
        new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
                .execute("create database " + database);
        Fixture f = new Fixture(database);
        f.migrate(version);
        f.jdbc.update("insert into canonical_event(id,provider,provider_event_id) values (?,'SOFASCORE',?)", IDENTITY.value(), EVENT);
        return f;
    }

    private static <T> T transactional(T object, Class<T> type, JdbcTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(object);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }

    private record Source(long snapshotId, RawPayloadEvidence payload) {
        EventSourceTrace trace(String parser) { return EventSourceTrace.providerSnapshot(snapshotId, payload.sha256(), parser, RECEIVED); }
    }

    private static final class Fixture {
        final String database;
        final DriverManagerDataSource dataSource;
        final JdbcTemplate jdbc;
        final JdbcTransactionManager transactions;
        final RawManualCallSnapshotStore raw;
        final J5EventDataStore store;
        Fixture(String database) {
            this.database = database;
            String url = POSTGRES.getJdbcUrl().substring(0, POSTGRES.getJdbcUrl().lastIndexOf('/') + 1) + database;
            dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
            jdbc = new JdbcTemplate(dataSource);
            transactions = new JdbcTransactionManager(dataSource);
            var named = new NamedParameterJdbcTemplate(dataSource);
            raw = transactional(new JdbcRawManualCallSnapshotStore(named), RawManualCallSnapshotStore.class, transactions);
            store = transactional(new JdbcJ5EventDataStore(named), J5EventDataStore.class, transactions);
        }
        int migrate(String version) {
            return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion(version)).load().migrate().migrationsExecuted;
        }
        Source source(String json, String parser) {
            RawPayloadEvidence payload = RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
            var snapshot = raw.save(new RawManualCallSnapshot(SofascoreEndpointType.EVENT_LINEUPS,
                    "EVENT_LINEUPS|eventId=" + EVENT, RECEIVED.minusMillis(100), RECEIVED, 200,
                    "application/json", Duration.ofMillis(100), payload, parser, RawSnapshotSchemaStatus.PARSED, null));
            return new Source(snapshot.snapshotId(), payload);
        }
        J5EventDataObservation observation(Source source) {
            var parsed = new EventLineupsV3Parser().parse(source.snapshotId(), EVENT, source.payload(), RECEIVED);
            assertThat(parsed.data()).as("synthetic lineups fixture parses without provider network").isPresent();
            return J5EventDataObservation.from(IDENTITY, parsed.data().orElseThrow(), source.trace("event-lineups-v3"),
                    parsed.completeness().orElseThrow());
        }
        long insertLegacy(J5EventDataObservation observation) {
            return new TransactionTemplate(transactions).execute(status -> {
                EventLineups lineups = (EventLineups) observation.data();
                var source = observation.source();
                var completeness = observation.completeness();
                long id = jdbc.queryForObject("""
                    insert into j5_event_data_observation(canonical_event_id,endpoint_type,source_kind,source_reference,
                        source_snapshot_id,source_payload_sha256,parser_version,source_received_at,completeness_status,
                        completeness_score,present_signals,expected_signals,missing_paths_json,lineups_confirmed,normalized_sha256)
                    values (?,'EVENT_LINEUPS','PROVIDER_SNAPSHOT',?,?,?,?,?,
                        ?,?,?,?,?::jsonb,?,?) returning id
                    """, Long.class, IDENTITY.value(), source.sourceReference(), source.snapshotId().orElseThrow(),
                    source.payloadSha256(), source.parserVersion(), Timestamp.from(source.receivedAt()),
                    completeness.status().name(), completeness.scorePercent(), completeness.presentSignals(),
                    completeness.expectedSignals(), tools.jackson.databind.json.JsonMapper.builder().build()
                            .writeValueAsString(completeness.missingPaths()),
                    lineups.confirmed(), observation.normalizedSha256());
                for (TeamLineup side : List.of(lineups.home(), lineups.away())) {
                    jdbc.update("insert into j5_event_lineup_side(observation_id,side,formation) values (?,?,?)",
                            id, side.side().name(), side.formation().orElse(null));
                    for (int i = 0; i < side.players().size(); i++) {
                        var player = side.players().get(i);
                        jdbc.update("""
                            insert into j5_event_lineup_player(observation_id,side,player_order,player_provider_id,
                                player_name,shirt_number,position,starter) values (?,?,?,?,?,?,?,?)
                            """, id, side.side().name(), i, player.providerPlayerId(), player.name(),
                                player.shirtNumber().orElse(null), player.position().orElse(null), player.starter());
                    }
                }
                return id;
            });
        }
        Map<String, List<String>> evidence(boolean ignoreNewFields) {
            Map<String, List<String>> evidence = new LinkedHashMap<>();
            for (String table : List.of("provider_snapshot", "provider_snapshot_occurrence", "j5_event_data_observation",
                    "j5_event_lineup_side", "j5_event_lineup_player")) {
                String expression = "to_jsonb(t)";
                if (ignoreNewFields && table.equals("j5_event_lineup_player")) expression += " - 'captain' - 'statistics'";
                if (ignoreNewFields && table.equals("j5_event_lineup_side")) expression += " - 'missing_players'";
                evidence.put(table, jdbc.queryForList("select (" + expression + ")::text from " + table + " t order by (" + expression + ")::text", String.class));
            }
            return evidence;
        }
    }
}
