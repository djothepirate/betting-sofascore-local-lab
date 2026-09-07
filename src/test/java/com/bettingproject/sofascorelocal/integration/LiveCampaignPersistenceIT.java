package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.*;
import com.bettingproject.sofascorelocal.adapter.persistence.live.*;
import com.bettingproject.sofascorelocal.adapter.sofascore.live.LivePayloadNormalizer;
import com.bettingproject.sofascorelocal.application.live.LiveProcessedResponse;
import com.bettingproject.sofascorelocal.application.live.LiveResponseProcessor;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.domain.event.*;
import com.bettingproject.sofascorelocal.domain.eventdetails.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.*;
import com.bettingproject.sofascorelocal.domain.retention.J6BackupEvidence;
import com.bettingproject.sofascorelocal.port.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL evidence only; no application startup, browser or provider network. */
@Testcontainers
class LiveCampaignPersistenceIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("live_test_admin").withUsername("sofascore_lab").withPassword("integration-test-only");
    private static final Instant T0=Instant.parse("2026-09-07T12:00:00Z");
    private static final AtomicInteger DATABASE=new AtomicInteger();
    private static final long EVENT=58_001;

    @Test
    void installsFreshSchemaAndKeepsV32PrepopulatedEvidenceUnchanged() {
        Fixture f=fixture("32"); Target target=f.seed(EVENT);
        f.jdbc.update("""
            insert into j8_benchmark_campaign(campaign_id,campaign_type,execution_mode,started_at,maximum_units)
            values (?,'J4_EVENT_DETAILS_PHASE2','GUARDED_PROVIDER',?,1)
            """,UUID.randomUUID(),java.sql.Timestamp.from(T0));
        Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();
        for(String table:List.of("provider_snapshot","provider_snapshot_occurrence","canonical_event","canonical_event_observation","event_detail_observation","j8_benchmark_campaign"))
            before.put(table,f.jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text"));
        assertThat(f.migrate("33").migrationsExecuted).isEqualTo(1);
        before.forEach((table,rows)->assertThat(f.jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text")).isEqualTo(rows));
        assertThat(f.guard.snapshot().state()).isEqualTo("FREE");
        assertThat(f.jdbc.queryForObject("select count(*) from live_campaign",Long.class)).isZero();
        Manifest manifest=f.manifest(target,100);
        assertThat(f.store.prepare(manifest)).isEqualTo(manifest);
        assertThat(f.migrate("33").migrationsExecuted).isZero();
    }

    @Test
    void launchIsIdempotentAndManifestSelectionAndEvidenceAreImmutable() {
        Fixture f=fixture("33"); Manifest m=f.manifest(f.seed(EVENT),100); f.store.prepare(m);
        Ownership own=f.acquire(m);
        assertThat(f.store.launch(m.campaignId(),m.manifestSha256(),own,T0.plusSeconds(1)).newlyLaunched()).isTrue();
        assertThat(f.store.launch(m.campaignId(),m.manifestSha256(),own,T0.plusSeconds(2)).newlyLaunched()).isFalse();
        assertThatThrownBy(()->f.store.launch(m.campaignId(),"b".repeat(64),own,T0.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->f.jdbc.update("update live_campaign set maximum_calls=99 where campaign_id=?",m.campaignId())).hasMessageContaining("immutable");
        assertThatThrownBy(()->f.jdbc.update("update live_event set provider_event_id=58002 where campaign_id=?",m.campaignId())).hasMessageContaining("immutable");
        ReservedAttempt a=f.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        f.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(10));
        assertThatThrownBy(()->f.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(11))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->f.jdbc.update("delete from live_call where attempt_id=?",a.attemptId())).hasMessageContaining("append-only");
        assertThatThrownBy(()->f.jdbc.execute("truncate live_transition")).hasMessageContaining("append-only");
    }

    @Test
    void identicalReceiptsAndAToBToAReturnToTheCorrectOldObservationWithNewFreshness() {
        Fixture f=fixture("33"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
        List<Long> snapshots=new ArrayList<>(); List<Long> occurrences=new ArrayList<>(); List<Long> observations=new ArrayList<>();
        List<UUID> attempts=new ArrayList<>();
        for(int cycle=0;cycle<4;cycle++) {
            String value=cycle==2?"B":"A";
            ReservedAttempt a=f.reserve(own,m.targets().getFirst(),cycle,SofascoreEndpointType.EVENT_DETAILS); attempts.add(a.attemptId());
            Instant at=T0.plusSeconds(10+cycle*60L); f.store.recordDispatch(own,a.attemptId(),at);
            RawManualCallSnapshot raw=f.raw(EVENT,value,at);
            RawSnapshotPersistenceResult receipt=f.store.saveReceipt(own,a.attemptId(),raw);
            assertThat(f.store.saveReceipt(own,a.attemptId(),raw)).isEqualTo(receipt);
            Result result=f.store.publishResult(own,a.attemptId(),f.success(at.plusSeconds(1),value),()->f.normalize(EVENT,value,raw,receipt));
            snapshots.add(receipt.snapshotId()); occurrences.add(receipt.occurrenceId().orElseThrow()); observations.add(result.normalized().detailObservationId());
        }
        assertThat(snapshots.get(0)).isEqualTo(snapshots.get(1)).isEqualTo(snapshots.get(3));
        assertThat(observations.get(0)).isEqualTo(observations.get(1)).isEqualTo(observations.get(3));
        assertThat(new HashSet<>(occurrences)).hasSize(4);
        FamilyCursor cursor=f.store.find(m.campaignId()).orElseThrow().events().getFirst().families().getFirst();
        assertThat(cursor.lastSuccessfulAttemptId()).isEqualTo(attempts.get(3));
        assertThat(cursor.lastChangedAttemptId()).isEqualTo(attempts.get(3));
        assertThat(cursor.normalized().detailObservationId()).isEqualTo(observations.get(0));
        assertThat(cursor.latestSuccessfulResult().publication().projectionJson()).contains("A");
        assertThat(cursor.lastSuccessfulAt()).isEqualTo(T0.plusSeconds(191));
        assertThat(f.details.findLatest(m.targets().getFirst().canonicalEventId()).orElseThrow().observationId()).isEqualTo(observations.get(2));
    }

    @Test
    void publicationRollbackKeepsRawReceiptAndDoesNotLeakNormalizedChildrenOrSuccessfulState() {
        Fixture f=fixture("33"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
        ReservedAttempt a=f.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        f.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(10));
        RawManualCallSnapshot raw=f.raw(EVENT,"rollback",T0.plusSeconds(10));
        RawSnapshotPersistenceResult receipt=f.store.saveReceipt(own,a.attemptId(),raw);
        Long before=f.jdbc.queryForObject("select count(*) from canonical_event_observation",Long.class);
        assertThatThrownBy(()->f.store.publishResult(own,a.attemptId(),f.success(T0.plusSeconds(11),"rollback"),()->{
            f.normalize(EVENT,"rollback",raw,receipt); throw new IllegalStateException("forced publication rollback");
        })).hasMessageContaining("forced publication rollback");
        assertThat(f.jdbc.queryForObject("select count(*) from canonical_event_observation",Long.class)).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("select count(*) from live_call_result where attempt_id=?",Long.class,a.attemptId())).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from live_call_receipt where attempt_id=?",Long.class,a.attemptId())).isEqualTo(1);
        assertThat(f.store.find(m.campaignId()).orElseThrow().events().getFirst().state()).isEqualTo("INITIAL_CHECK");
    }

    @Test
    void receiptCorrelationFailureRollsBackNewRawOccurrenceAndCounter() {
        Fixture f=fixture("33"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
        ReservedAttempt a=f.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        long before=f.jdbc.queryForObject("select count(*) from provider_snapshot_occurrence",Long.class);
        // No dispatch row: FK must reject receipt, including the inner raw save in the same transaction.
        assertThatThrownBy(()->f.store.saveReceipt(own,a.attemptId(),f.raw(EVENT,"no-dispatch",T0.plusSeconds(10)))).isInstanceOf(RuntimeException.class);
        assertThat(f.jdbc.queryForObject("select count(*) from provider_snapshot_occurrence",Long.class)).isEqualTo(before);
        assertThat(f.store.find(m.campaignId()).orElseThrow().receivedBytes()).isZero();
    }

    @Test
    void realConcurrentConnectionsCannotSpendLastOrdinaryBudgetTwice() throws Exception {
        Fixture f=fixture("33"); Manifest m=f.manifest(f.seed(EVENT),5); Ownership own=f.start(m);
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{ready.countDown();go.await();return f.store.reserveAttempt(f.request(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS));});
            var second=pool.submit(()->{ready.countDown();go.await();return f.store.reserveAttempt(f.request(own,m.targets().getFirst(),1,SofascoreEndpointType.EVENT_DETAILS));});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)).stream().filter(Optional::isPresent).count()).isEqualTo(1);
        }
        assertThat(f.store.find(m.campaignId()).orElseThrow().reservedCalls()).isEqualTo(1);
    }

    @Test
    void cleanupAndOldGenerationRemainFailClosedAndOrphanRecoveryNeverRearms() {
        Fixture f=fixture("33"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
        ReservedAttempt a=f.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        f.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(10));
        Owner other=new Owner(UUID.randomUUID(),1235,T0.minusSeconds(30));
        assertThat(f.guard.tryAcquire(UUID.randomUUID(),other,T0.plusSeconds(20))).isEmpty();
        assertThat(f.guard.isOwned(own)).isTrue();
        f.guard.requireCleanup(own,T0.plusSeconds(20));
        assertThat(f.guard.isOwned(own)).isFalse();
        f.store.interruptOrphan(own,T0.plusSeconds(21),"OWNER_PROCESS_EXITED");
        f.store.interruptOrphan(own,T0.plusSeconds(22),"OWNER_PROCESS_EXITED");
        assertThat(f.guard.snapshot().state()).isEqualTo("CLEANUP_REQUIRED");
        assertThat(f.guard.tryAcquire(UUID.randomUUID(),other,T0.plusSeconds(10000))).isEmpty();
        CampaignView view=f.store.find(m.campaignId()).orElseThrow();
        assertThat(view.state()).isEqualTo("INTERRUPTED"); assertThat(view.reservedCalls()).isEqualTo(1);
        assertThat(view.attempts().getFirst().result().publication().outcome()).isEqualTo("UNKNOWN");
        assertThatThrownBy(()->f.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(22))).isInstanceOf(IllegalStateException.class);
        // Synthetic proof of cleanup only; no real processes are involved in this integration test.
        f.guard.releaseAfterVerifiedCleanup(own,T0.plusSeconds(23));
        Guard newGuard=f.guard.tryAcquire(UUID.randomUUID(),other,T0.plusSeconds(24)).orElseThrow();
        assertThat(newGuard.generation()).isGreaterThan(own.generation());
        assertThatThrownBy(()->f.guard.releaseAfterVerifiedCleanup(own,T0.plusSeconds(25))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lateReceiptAfterIndividualStopPreservesEvidenceWithoutReactivatingEvent() {
        Fixture f=fixture("33"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
        ReservedAttempt a=f.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        f.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(10));
        f.store.transition(own,m.targets().getFirst().canonicalEventId(),"STOPPED_OPERATOR","OPERATOR_STOP",T0.plusSeconds(10),null);
        RawManualCallSnapshot raw=f.raw(EVENT,"late",T0.plusSeconds(10));
        RawSnapshotPersistenceResult receipt=f.store.saveReceipt(own,a.attemptId(),raw);
        f.store.publishResult(own,a.attemptId(),f.success(T0.plusSeconds(11),"late"),()->f.normalize(EVENT,"late",raw,receipt));
        EventView event=f.store.find(m.campaignId()).orElseThrow().events().getFirst();
        assertThat(event.state()).isEqualTo("STOPPED_OPERATOR");
        assertThat(event.families().getFirst().latestSuccessfulResult()).isNotNull();
        assertThat(f.store.reserveAttempt(f.request(own,m.targets().getFirst(),1,SofascoreEndpointType.EVENT_DETAILS))).isEmpty();
    }

    @Test
    void unavailableJ5FamilyRetainsLastGoodObservationAndItsOwnReceptionDate() {
        Fixture f=fixture("33");Manifest m=f.manifest(f.seed(EVENT),100);Ownership own=f.start(m);
        ReservedAttempt first=f.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_STATISTICS);
        Instant firstAt=T0.plusSeconds(10);f.store.recordDispatch(own,first.attemptId(),firstAt);
        RawManualCallSnapshot raw=new RawManualCallSnapshot(SofascoreEndpointType.EVENT_STATISTICS,"EVENT_STATISTICS|eventId="+EVENT,
                firstAt,firstAt.plusSeconds(1),200,"application/json",Duration.ofSeconds(1),RawPayloadEvidence.capture("{\"statistics\":[]}".getBytes(StandardCharsets.UTF_8)),
                "event-statistics-v2",RawSnapshotSchemaStatus.RAW_ONLY,null);
        RawSnapshotPersistenceResult receipt=f.store.saveReceipt(own,first.attemptId(),raw);
        Result good=f.store.publishResult(own,first.attemptId(),new Publication("PARSED","EVENT",null,raw.receivedAt(),"event-statistics-v2",true,"COLLECTING"),()->{
            Long observationId=f.jdbc.queryForObject("""
                insert into j5_event_data_observation(canonical_event_id,endpoint_type,source_kind,source_reference,source_snapshot_id,
                    source_payload_sha256,parser_version,source_received_at,completeness_status,completeness_score,present_signals,expected_signals,
                    missing_paths_json,normalized_sha256) values (?,'EVENT_STATISTICS','PROVIDER_SNAPSHOT',?,?,?,'event-statistics-v2',?,
                    'EMPTY_VALID',100,0,0,'[]'::jsonb,?) returning id
                """,Long.class,m.targets().getFirst().canonicalEventId(),"snapshot:"+receipt.snapshotId(),receipt.snapshotId(),receipt.payloadSha256(),
                    java.sql.Timestamp.from(raw.receivedAt()),"c".repeat(64));
            return new NormalizedReferences(null,null,observationId,"c".repeat(64));
        });
        ReservedAttempt second=f.reserve(own,m.targets().getFirst(),1,SofascoreEndpointType.EVENT_STATISTICS);
        Instant secondAt=T0.plusSeconds(70);f.store.recordDispatch(own,second.attemptId(),secondAt);
        f.store.saveReceipt(own,second.attemptId(),new RawManualCallSnapshot(SofascoreEndpointType.EVENT_STATISTICS,"EVENT_STATISTICS|eventId="+EVENT,
                secondAt,secondAt.plusSeconds(1),404,"application/json",Duration.ofSeconds(1),RawPayloadEvidence.capture("{}".getBytes(StandardCharsets.UTF_8)),
                "event-statistics-v2",RawSnapshotSchemaStatus.RAW_ONLY,null));
        f.store.publishResult(own,second.attemptId(),new Publication("ENDPOINT_UNAVAILABLE","EVENT",null,secondAt.plusSeconds(1),"event-statistics-v2",false,null),NormalizedReferences::none);
        FamilyCursor cursor=f.store.find(m.campaignId()).orElseThrow().events().getFirst().families().getFirst();
        assertThat(cursor.latestResult().publication().outcome()).isEqualTo("ENDPOINT_UNAVAILABLE");
        assertThat(cursor.lastReceivedAt()).isEqualTo(secondAt.plusSeconds(1));
        assertThat(cursor.lastSuccessfulAt()).isEqualTo(firstAt.plusSeconds(1));
        assertThat(cursor.normalized()).isEqualTo(good.normalized());
    }

    @Test
    void preparationNormalizesMicrosecondsAndScheduleMetricsOnlyReviseWhenChanged() {
        Fixture f=fixture("33");Target target=f.seed(EVENT);
        Manifest m=new Manifest(UUID.randomUUID(),"a".repeat(64),"live-v1",T0.plusNanos(123456789),T0.plusSeconds(300),
                Duration.ofHours(4),100,100,100_000_000L,1,List.of(target));
        assertThat(m.preparedAt().getNano()).isEqualTo(123456000);
        assertThat(f.store.prepare(m)).isEqualTo(m);
        Ownership own=f.acquire(m);f.store.launch(m.campaignId(),m.manifestSha256(),own,T0.plusSeconds(1));
        f.store.updateScheduleMetrics(own,target.canonicalEventId(),T0.plusSeconds(70),2,false,T0.plusSeconds(10));
        CampaignView first=f.store.find(m.campaignId()).orElseThrow();
        f.store.updateScheduleMetrics(own,target.canonicalEventId(),T0.plusSeconds(70),2,false,T0.plusSeconds(11));
        CampaignView second=f.store.find(m.campaignId()).orElseThrow();
        assertThat(second.revision()).isEqualTo(first.revision());
        assertThat(second.events().getFirst().missedCycles()).isEqualTo(2);
        assertThat(second.events().getFirst().finalComplete()).isFalse();
        assertThatThrownBy(()->f.store.updateScheduleMetrics(own,target.canonicalEventId(),null,1,false,T0.plusSeconds(12)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SofascoreEndpointType.class, names = {
            "EVENT_STATISTICS", "EVENT_INCIDENTS", "EVENT_LINEUPS"})
    void live404PublicationSurvivesDeduplicationAndRecoveryWithoutLosingLastGoodData(SofascoreEndpointType endpoint) {
        Fixture f = fixture("33"); Manifest m = f.manifest(f.seed(EVENT), 100); Ownership own = f.start(m);
        String available = switch (endpoint) {
            case EVENT_STATISTICS -> "{\"statistics\":[]}";
            case EVENT_INCIDENTS -> "{\"incidents\":[]}";
            case EVENT_LINEUPS -> "{\"confirmed\":false,\"home\":{\"players\":[]},\"away\":{\"players\":[]}}";
            default -> throw new AssertionError(endpoint);
        };
        List<Long> snapshots = new ArrayList<>(); Set<Long> occurrences = new HashSet<>();
        Result good = null;
        for (int cycle = 0; cycle < 4; cycle++) {
            boolean success = cycle == 2;
            ReservedAttempt attempt = f.reserve(own, m.targets().getFirst(), cycle, endpoint);
            Instant at = T0.plusSeconds(10 + 60L * cycle);
            f.store.recordDispatch(own, attempt.attemptId(), at);
            var response = new PlaywrightProviderResponse(at, at.plusMillis(100), success ? 200 : 404,
                    "application/json", Duration.ofMillis(100), RawPayloadEvidence.capture(
                            (success ? available : "{\"error\":\"unavailable\"}").getBytes(StandardCharsets.UTF_8)));
            var receipt = f.store.saveReceipt(own, attempt.attemptId(), new RawManualCallSnapshot(endpoint,
                    endpoint.name() + "|eventId=" + EVENT, at, response.receivedAt(), response.httpStatus(),
                    response.contentType(), Duration.ofMillis(100), response.payload(),
                    LivePayloadNormalizer.parserVersion(endpoint), RawSnapshotSchemaStatus.RAW_ONLY, null));
            var processed = f.processor.process(CanonicalEventIdentity.sofascore(EVENT), endpoint, response, receipt);
            assertThat(processed.scope()).isEqualTo(LiveProcessedResponse.FailureScope.NONE);
            var publication = new Publication(processed.outcome().name(), processed.scope().name(), processed.code(),
                    processed.receivedAt(), processed.parserVersion(), success, "COLLECTING", null,
                    processed.projectionJson(), processed.projectionVersion(),
                    processed.completeness().orElseThrow().status().name(),
                    processed.completeness().orElseThrow().scorePercent());
            Result result = f.store.publishResult(own, attempt.attemptId(), publication,
                    () -> f.processor.persistProcessed(processed));
            assertThat(result.normalized().j5ObservationId()).isPositive();
            assertThat(f.jdbc.queryForMap("select schema_status,error_code from provider_snapshot where id=?", receipt.snapshotId()))
                    .containsEntry("schema_status", success ? "PARSED" : "ENDPOINT_UNAVAILABLE").containsEntry("error_code", null);
            if (success) good = result;
            else {
                assertThat(result.publication().code()).isEqualTo("HTTP_404");
                assertThat(f.jdbc.queryForObject("select completeness_status from j5_event_data_observation where id=?",
                        String.class, result.normalized().j5ObservationId())).isEqualTo("UNAVAILABLE");
            }
            CampaignView state = f.store.find(m.campaignId()).orElseThrow();
            assertThat(state.state()).isEqualTo("RUNNING");
            FamilyCursor cursor = state.events().getFirst().families().getFirst();
            assertThat(cursor.lastReceivedAt()).isEqualTo(response.receivedAt());
            if (good == null) assertThat(cursor.latestSuccessfulResult()).isNull();
            else {
                assertThat(cursor.lastSuccessfulAttemptId()).isEqualTo(good.attemptId());
                assertThat(cursor.latestSuccessfulResult().normalized()).isEqualTo(good.normalized());
                assertThat(cursor.latestSuccessfulResult().publication().resolvedAt()).isEqualTo(good.publication().resolvedAt());
            }
            snapshots.add(receipt.snapshotId()); occurrences.add(receipt.occurrenceId().orElseThrow());
        }
        assertThat(snapshots.get(0)).isEqualTo(snapshots.get(1)).isEqualTo(snapshots.get(3));
        assertThat(occurrences).hasSize(4);
        FamilyCursor cursor = f.store.find(m.campaignId()).orElseThrow().events().getFirst().families().getFirst();
        assertThat(cursor.normalized()).isEqualTo(good.normalized());
        assertThat(cursor.lastSuccessfulAt()).isEqualTo(T0.plusSeconds(130).plusMillis(100));
    }

    @Test
    void j4UnavailablePublicationKeepsItsEventScopeInsteadOfBecomingAStorageFailure() {
        Fixture f = fixture("33"); Manifest m = f.manifest(f.seed(EVENT), 100); Ownership own = f.start(m);
        var attempt = f.reserve(own, m.targets().getFirst(), 0, SofascoreEndpointType.EVENT_DETAILS);
        Instant at = T0.plusSeconds(10); f.store.recordDispatch(own, attempt.attemptId(), at);
        var response = new PlaywrightProviderResponse(at, at.plusMillis(100), 404, "application/json",
                Duration.ofMillis(100), RawPayloadEvidence.capture("{}".getBytes(StandardCharsets.UTF_8)));
        var receipt = f.store.saveReceipt(own, attempt.attemptId(), new RawManualCallSnapshot(SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=" + EVENT, at, response.receivedAt(), 404, "application/json", Duration.ofMillis(100),
                response.payload(), "event-details-v2", RawSnapshotSchemaStatus.RAW_ONLY, null));
        var processed = f.processor.process(CanonicalEventIdentity.sofascore(EVENT), SofascoreEndpointType.EVENT_DETAILS, response, receipt);
        assertThat(processed.scope()).isEqualTo(LiveProcessedResponse.FailureScope.EVENT);
        Result result = f.store.publishResult(own, attempt.attemptId(), new Publication(processed.outcome().name(),
                processed.scope().name(), processed.code(), processed.receivedAt(), processed.parserVersion(), false,
                "STOPPED_REVIEW_REQUIRED"), () -> f.processor.persistProcessed(processed));
        assertThat(result.publication().code()).isEqualTo("HTTP_404");
        assertThat(result.normalized()).isEqualTo(NormalizedReferences.none());
        assertThat(f.jdbc.queryForObject("select schema_status from provider_snapshot where id=?", String.class, receipt.snapshotId()))
                .isEqualTo("ENDPOINT_UNAVAILABLE");
        assertThat(f.store.find(m.campaignId()).orElseThrow().state()).isEqualTo("RUNNING");
    }

    @Test
    void admissionProfileIsReadExactlyAndCannotChangeAfterPreparation() {
        Fixture f=fixture("33"); Target target=f.seed(EVENT);
        AdmissionProfile profile=new AdmissionProfile(Duration.ofSeconds(3).plusNanos(123),Duration.ofMillis(500).plusNanos(456),"d".repeat(64));
        Manifest m=new Manifest(UUID.randomUUID(),"a".repeat(64),"live-v1",T0,T0.plusSeconds(300),
                Duration.ofHours(4),100,100,100_000_000L,1,List.of(target),profile);
        assertThat(f.store.prepare(m)).isEqualTo(m);
        assertThat(f.store.find(m.campaignId()).orElseThrow().manifest().admissionProfile()).isEqualTo(profile);
        Manifest changed=new Manifest(m.campaignId(),m.manifestSha256(),m.policyVersion(),m.preparedAt(),m.expiresAt(),m.duration(),
                m.maximumCallsPerEvent(),m.maximumCalls(),m.maximumBytes(),m.qualifiedMatchCapacity(),m.targets(),AdmissionProfile.conservative());
        assertThatThrownBy(()->f.store.prepare(changed)).hasMessageContaining("idempotency collision");
        assertThatThrownBy(()->f.jdbc.update("update live_campaign set request_envelope_nanos=10000000000 where campaign_id=?",m.campaignId()))
                .hasMessageContaining("immutable");
        assertThatThrownBy(()->f.jdbc.update("update live_campaign set qualification_sha256=? where campaign_id=?","e".repeat(64),m.campaignId()))
                .hasMessageContaining("immutable");
        assertThat(f.store.find(m.campaignId()).orElseThrow().manifest()).isEqualTo(m);
    }

    @Test
    void preparationDoesNotHideRunningOrPreviouslyLaunchedEventData() {
        Fixture f=fixture("33"); Target target=f.seed(EVENT);
        Manifest first=f.manifest(target,100); Ownership own=f.start(first);
        Manifest prepared=new Manifest(UUID.randomUUID(),"b".repeat(64),"live-v1",T0.plusSeconds(20),T0.plusSeconds(320),
                Duration.ofHours(4),100,100,100_000_000L,1,List.of(target));
        f.store.prepare(prepared);
        assertThat(f.store.latestForEvent(target.canonicalEventId()).orElseThrow().manifest().campaignId()).isEqualTo(first.campaignId());
        f.store.transition(own,target.canonicalEventId(),"STOPPED_OPERATOR","OPERATOR_STOP",T0.plusSeconds(21),null);
        f.store.transition(own,null,"COMPLETED","CLEANUP_VERIFIED",T0.plusSeconds(22),null);
        f.guard.releaseAfterVerifiedCleanup(own,T0.plusSeconds(23));
        assertThat(f.store.latestForEvent(target.canonicalEventId()).orElseThrow().manifest().campaignId()).isEqualTo(first.campaignId());
        Ownership next=f.guard.tryAcquire(prepared.campaignId(),new Owner(UUID.randomUUID(),1235,T0),T0.plusSeconds(24)).orElseThrow().ownership();
        f.store.launch(prepared.campaignId(),prepared.manifestSha256(),next,T0.plusSeconds(24));
        assertThat(f.store.latestForEvent(target.canonicalEventId()).orElseThrow().manifest().campaignId()).isEqualTo(prepared.campaignId());
    }

    @Test
    void projectionHashCoversTheStoredJsonbRepresentationAndRejectsAnUnrelatedHash() {
        Fixture f=fixture("33");Manifest m=f.manifest(f.seed(EVENT),100);Ownership own=f.start(m);
        ReservedAttempt a=f.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        f.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(10));
        RawManualCallSnapshot raw=f.raw(EVENT,"hash",T0.plusSeconds(10));
        RawSnapshotPersistenceResult receipt=f.store.saveReceipt(own,a.attemptId(),raw);
        String projection="{\"z\":1,\"a\":\"é\"}";
        f.store.publishResult(own,a.attemptId(),new Publication("PARSED","EVENT",null,raw.receivedAt(),"event-details-v2",true,
                "COLLECTING","inprogress",projection,"live-phase-v1","COMPLETE",100),()->f.normalize(EVENT,"hash",raw,receipt));
        String stored=f.jdbc.queryForObject("select projection_json::text from live_call_result where attempt_id=?",String.class,a.attemptId());
        assertThat(stored).isNotEqualTo(projection);
        assertThat(f.jdbc.queryForObject("select projection_sha256 from live_call_result where attempt_id=?",String.class,a.attemptId()))
                .isEqualTo(com.bettingproject.sofascorelocal.security.Sha256.hex(stored.getBytes(StandardCharsets.UTF_8)));
        ReservedAttempt bad=f.reserve(own,m.targets().getFirst(),1,SofascoreEndpointType.EVENT_DETAILS);
        assertThatThrownBy(()->f.jdbc.update("""
            insert into live_call_result(attempt_id,outcome,scope,resolved_at,successful,projection_json,projection_version,projection_sha256)
            values (?,'FAILED','EVENT',?,false,cast(? as jsonb),'live-phase-v1',?)
            """,bad.attemptId(),java.sql.Timestamp.from(T0.plusSeconds(71)),projection,"0".repeat(64))).hasMessageContaining("check constraint");
    }

    @Test
    void retentionRefusesActiveProviderAndPreservesLiveReferencesAfterQualifiedTerminalPurge() throws Exception {
        Fixture f=fixture("33");Manifest m=f.manifest(f.seed(EVENT),100);Ownership own=f.start(m);
        ReservedAttempt a=f.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        f.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(10));
        RawManualCallSnapshot raw=f.raw(EVENT,"retention",T0.plusSeconds(10));
        RawSnapshotPersistenceResult receipt=f.store.saveReceipt(own,a.attemptId(),raw);
        f.store.publishResult(own,a.attemptId(),f.success(raw.receivedAt(),"retention"),()->f.normalize(EVENT,"retention",raw,receipt));
        Instant generated=T0.plus(Duration.ofDays(40)),cutoff=T0.plus(Duration.ofDays(10));
        assertThatThrownBy(()->f.retention.preview(30,generated,cutoff,500)).hasMessageContaining("PROVIDER_CAMPAIGN_ACTIVE");
        f.store.transition(own,m.targets().getFirst().canonicalEventId(),"STOPPED_OPERATOR","OPERATOR_STOP",T0.plusSeconds(12),null);
        f.store.transition(own,null,"COMPLETED","CLEANUP_VERIFIED",T0.plusSeconds(13),null);
        f.guard.releaseAfterVerifiedCleanup(own,T0.plusSeconds(14));
        String script=Files.readString(Path.of("scripts/Backup-Restore-J6.ps1"),StandardCharsets.UTF_8);
        String sql=script.split("\\$liveLedgerFingerprintSql = @'\\r?\\n",2)[1].split("\\r?\\n'@",2)[0];
        String before=f.jdbc.queryForObject(sql,String.class);
        var preview=f.retention.preview(30,generated,cutoff,500);
        assertThat(preview.candidates()).isNotEmpty();
        f.retention.purge(30,cutoff,500,preview.planSha256(),new J6BackupEvidence("a".repeat(64),"b".repeat(64),
                generated.minusSeconds(1),receipt.snapshotId(),generated,true),UUID.randomUUID(),generated.plusSeconds(1));
        assertThat(f.jdbc.queryForObject(sql,String.class)).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("select payload_raw is null from provider_snapshot where id=?",Boolean.class,receipt.snapshotId())).isTrue();
        assertThat(f.store.find(m.campaignId()).orElseThrow().events().getFirst().families().getFirst().normalized().detailObservationId()).isNotNull();
        assertThat(f.jdbc.queryForObject("select count(*) from live_call_receipt where snapshot_id=?",Long.class,receipt.snapshotId())).isEqualTo(1);
        Manifest again=f.manifest(m.targets().getFirst(),100);Ownership next=f.start(again);
        ReservedAttempt repeated=f.reserve(next,again.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        f.store.recordDispatch(next,repeated.attemptId(),T0.plusSeconds(10));
        Long occurrencesBefore=f.jdbc.queryForObject("select count(*) from provider_snapshot_occurrence",Long.class);
        assertThatThrownBy(()->f.store.saveReceipt(next,repeated.attemptId(),f.raw(EVENT,"retention",T0.plusSeconds(10))))
                .hasMessageContaining("LIVE_RAW_PREVIOUSLY_PURGED");
        assertThat(f.jdbc.queryForObject("select count(*) from provider_snapshot_occurrence",Long.class)).isEqualTo(occurrencesBefore);
        assertThat(f.jdbc.queryForObject("select count(*) from live_call_receipt where attempt_id=?",Long.class,repeated.attemptId())).isZero();
    }

    @Test
    void restoresAllLiveEvidenceAndFreeGuardWithoutRearmingExecution() throws Exception {
        Fixture source=fixture("33");Manifest m=source.manifest(source.seed(EVENT),100);Ownership own=source.start(m);
        ReservedAttempt a=source.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        source.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(10));
        RawManualCallSnapshot raw=source.raw(EVENT,"restore",T0.plusSeconds(10));
        RawSnapshotPersistenceResult receipt=source.store.saveReceipt(own,a.attemptId(),raw);
        source.store.publishResult(own,a.attemptId(),source.success(raw.receivedAt(),"restore"),()->source.normalize(EVENT,"restore",raw,receipt));
        source.store.transition(own,m.targets().getFirst().canonicalEventId(),"STOPPED_OPERATOR","OPERATOR_STOP",T0.plusSeconds(12),null);
        source.store.transition(own,null,"COMPLETED","CLEANUP_VERIFIED",T0.plusSeconds(13),null);
        source.guard.releaseAfterVerifiedCleanup(own,T0.plusSeconds(14));
        String script=Files.readString(Path.of("scripts/Backup-Restore-J6.ps1"),StandardCharsets.UTF_8);
        String sql=script.split("\\$liveLedgerFingerprintSql = @'\\r?\\n",2)[1].split("\\r?\\n'@",2)[0];
        String before=source.jdbc.queryForObject(sql,String.class);
        String sourceDatabase=source.ds.getUrl().substring(source.ds.getUrl().lastIndexOf('/')+1);
        String restoredDatabase="live_restored_"+DATABASE.incrementAndGet();
        String dump="/tmp/live-"+UUID.randomUUID()+".dump";
        try {
            assertThat(POSTGRES.execInContainer("pg_dump","--username",POSTGRES.getUsername(),"--dbname",sourceDatabase,
                    "--format=custom","--no-owner","--no-privileges","--file",dump).getExitCode()).isZero();
            assertThat(POSTGRES.execInContainer("createdb","--username",POSTGRES.getUsername(),restoredDatabase).getExitCode()).isZero();
            assertThat(POSTGRES.execInContainer("pg_restore","--username",POSTGRES.getUsername(),"--dbname",restoredDatabase,
                    "--exit-on-error","--no-owner","--no-privileges",dump).getExitCode()).isZero();
            String url=POSTGRES.getJdbcUrl().substring(0,POSTGRES.getJdbcUrl().lastIndexOf('/')+1)+restoredDatabase;
            Fixture restored=new Fixture(new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword()));
            assertThat(restored.jdbc.queryForObject(sql,String.class)).isEqualTo(before);
            assertThat(restored.migrate("33").migrationsExecuted).isZero();
            assertThat(restored.guard.snapshot().state()).isEqualTo("FREE");
            assertThat(restored.store.find(m.campaignId()).orElseThrow().state()).isEqualTo("COMPLETED");
            assertThat(restored.store.find(m.campaignId()).orElseThrow().attempts()).hasSize(1);
        } finally {
            POSTGRES.execInContainer("dropdb","--username",POSTGRES.getUsername(),"--if-exists","--force",restoredDatabase);
            POSTGRES.execInContainer("rm","-f",dump);
        }
    }

    @Test
    void concurrentProviderOwnersCannotAcquireTheSameDurableGuard() throws Exception {
        Fixture f=fixture("33");CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{ready.countDown();go.await();return f.guard.tryAcquire(UUID.randomUUID(),new Owner(UUID.randomUUID(),1234,T0),T0);});
            var second=pool.submit(()->{ready.countDown();go.await();return f.guard.tryAcquire(UUID.randomUUID(),new Owner(UUID.randomUUID(),1235,T0),T0);});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)).stream().filter(Optional::isPresent).count()).isEqualTo(1);
        }
        assertThat(f.guard.snapshot().generation()).isEqualTo(1);
    }

    private static Fixture fixture(String target) {
        String database="live_it_"+DATABASE.incrementAndGet();
        DriverManagerDataSource admin=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        new JdbcTemplate(admin).execute("create database "+database);
        String url=POSTGRES.getJdbcUrl().substring(0,POSTGRES.getJdbcUrl().lastIndexOf('/')+1)+database;
        DriverManagerDataSource ds=new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword());
        Fixture f=new Fixture(ds);f.migrate(target);return f;
    }
    private static <T>T transactional(T object,Class<T> type,JdbcTransactionManager manager) {
        ProxyFactory proxy=new ProxyFactory(object);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }
    private static final class Fixture {
        final DriverManagerDataSource ds; final JdbcTemplate jdbc; final RawManualCallSnapshotStore rawStore;
        final CanonicalEventStore canonical; final EventDetailsStore details; final LiveCampaignStore store; final ProviderCampaignGuardStore guard;
        final J6RawPayloadRetentionStore retention;
        final LiveResponseProcessor processor;
        Fixture(DriverManagerDataSource ds) {
            this.ds=ds;jdbc=new JdbcTemplate(ds);var named=new NamedParameterJdbcTemplate(ds);var tx=new JdbcTransactionManager(ds);
            rawStore=transactional(new JdbcRawManualCallSnapshotStore(named),RawManualCallSnapshotStore.class,tx);
            canonical=transactional(new JdbcCanonicalEventStore(named),CanonicalEventStore.class,tx);
            details=transactional(new JdbcEventDetailsStore(named),EventDetailsStore.class,tx);
            store=transactional(new JdbcLiveCampaignStore(jdbc,rawStore),LiveCampaignStore.class,tx);
            guard=transactional(new JdbcProviderCampaignGuardStore(jdbc),ProviderCampaignGuardStore.class,tx);
            retention=transactional(new JdbcJ6RawPayloadRetentionStore(named),J6RawPayloadRetentionStore.class,tx);
            J5EventDataStore data = transactional(new JdbcJ5EventDataStore(named), J5EventDataStore.class, tx);
            processor = transactional(new LiveResponseProcessor(canonical, details, data, rawStore), LiveResponseProcessor.class, tx);
        }
        org.flywaydb.core.api.output.MigrateResult migrate(String version) {
            return Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(MigrationVersion.fromVersion(version)).load().migrate();
        }
        Target seed(long event) {
            RawManualCallSnapshot raw=raw(event,"seed",T0.minusSeconds(100));RawSnapshotPersistenceResult p=rawStore.save(raw);
            NormalizedReferences normalized=normalize(event,"seed",raw,p);
            return new Target(CanonicalEventIdentity.sofascore(event).value(),event,normalized.canonicalObservationId(),p.snapshotId());
        }
        Manifest manifest(Target target,int budget) {
            return new Manifest(UUID.randomUUID(),"a".repeat(64),"live-v1",T0,T0.plusSeconds(300),Duration.ofHours(4),budget,budget,100_000_000L,1,List.of(target));
        }
        Ownership acquire(Manifest m) {return guard.tryAcquire(m.campaignId(),new Owner(UUID.randomUUID(),1234,T0.minusSeconds(50)),T0).orElseThrow().ownership();}
        Ownership start(Manifest m) {store.prepare(m);Ownership own=acquire(m);store.launch(m.campaignId(),m.manifestSha256(),own,T0.plusSeconds(1));return own;}
        AttemptRequest request(Ownership own,Target target,long cycle,SofascoreEndpointType endpoint) {
            Instant at=T0.plusSeconds(10+cycle*60);
            return new AttemptRequest(own,UUID.randomUUID(),target.canonicalEventId(),cycle,endpoint,"J4_WAIT",at,at,false);
        }
        ReservedAttempt reserve(Ownership own,Target target,long cycle,SofascoreEndpointType endpoint){return store.reserveAttempt(request(own,target,cycle,endpoint)).orElseThrow();}
        RawManualCallSnapshot raw(long event,String value,Instant at) {
            return new RawManualCallSnapshot(SofascoreEndpointType.EVENT_DETAILS,"EVENT_DETAILS|eventId="+event,at,at.plusSeconds(1),200,"application/json",
                    Duration.ofSeconds(1),RawPayloadEvidence.capture(("{\"value\":\""+value+"\"}").getBytes(StandardCharsets.UTF_8)),"event-details-v2",RawSnapshotSchemaStatus.RAW_ONLY,null);
        }
        Publication success(Instant at,String value) {
            return new Publication("PARSED","EVENT",null,at,"event-details-v2",true,"COLLECTING","inprogress","{\"value\":\""+value+"\"}","live-phase-v1","COMPLETE",100);
        }
        NormalizedReferences normalize(long event,String value,RawManualCallSnapshot raw,RawSnapshotPersistenceResult p) {
            EventDetails d=new EventDetails(event,T0,new ScheduledTeam(11,"Home"),new ScheduledTeam(22,"Away"),
                    new ScheduledEventStatus("inprogress",Optional.of(value)),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());
            EventSourceTrace source=EventSourceTrace.providerSnapshot(p.snapshotId(),p.payloadSha256(),"event-details-v2",raw.receivedAt());
            long canonicalId=canonical.save(CanonicalEventObservation.from(d.asScheduledEvent(),source)).observationId();
            EventDetailObservation observation=EventDetailObservation.from(CanonicalEventIdentity.sofascore(event),d,source);
            long detailId=details.save(observation).observationId();
            if(p.outcome()==RawSnapshotPersistenceOutcome.INSERTED) rawStore.classify(p.snapshotId(),RawSnapshotSchemaStatus.PARSED,null);
            return new NormalizedReferences(canonicalId,detailId,null,observation.normalizedSha256());
        }
    }
}
