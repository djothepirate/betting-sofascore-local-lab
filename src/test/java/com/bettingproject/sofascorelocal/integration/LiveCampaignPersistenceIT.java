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
import org.junit.jupiter.params.provider.ValueSource;
import com.bettingproject.sofascorelocal.domain.live.LiveCadence;
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

    @ParameterizedTest @ValueSource(strings={"live-v1","live-v2","live-v3","live-v4"})
    void cancellationKeepsExpiredPreparationEvidenceAndIsIdempotentWithoutTakingTheProviderGuard(String policy) {
        Fixture f=fixture("39"); Target target=f.seed(EVENT);
        Manifest manifest="live-v4".equals(policy) ? groupedManifest(List.of(target))
                : new Manifest(UUID.randomUUID(),"a".repeat(64),policy,T0,T0.plusSeconds(300),Duration.ofHours(4),
                    100,100,100_000_000,1,List.of(target));
        f.store.prepare(manifest);
        // Another campaign can own the provider: cancelling a local preparation must leave its lease alone.
        Manifest other=f.manifest(f.seed(EVENT+1),100); Ownership otherOwner=f.start(other);
        Guard guardBefore=f.guard.snapshot();
        Map<String,List<Map<String,Object>>> evidence=new LinkedHashMap<>();
        for(String table:List.of("provider_snapshot","provider_snapshot_occurrence","canonical_event_observation","event_detail_observation"))
            evidence.put(table,f.jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text"));
        Instant cancelledAt=T0.plusSeconds(600);
        f.store.cancelPreparation(manifest.campaignId(),manifest.manifestSha256(),cancelledAt);
        CampaignView cancelled=f.store.find(manifest.campaignId()).orElseThrow();
        assertThat(cancelled.manifest()).isEqualTo(manifest);
        assertThat(cancelled.state()).isEqualTo("STOPPED_OPERATOR");
        assertThat(cancelled.reason()).isEqualTo("PREPARATION_CANCELLED");
        assertThat(cancelled.startedAt()).isNull(); assertThat(cancelled.endsAt()).isNull();
        assertThat(cancelled.ownership()).isNull(); assertThat(cancelled.attempts()).isEmpty();
        assertThat(cancelled.reservedCalls()).isZero(); assertThat(cancelled.receivedBytes()).isZero();
        assertThat(cancelled.events()).singleElement().satisfies(event->{
            assertThat(event.target()).isEqualTo(target); assertThat(event.state()).isEqualTo("STOPPED_OPERATOR");
            assertThat(event.reason()).isEqualTo("PREPARATION_CANCELLED"); assertThat(event.nextDueAt()).isNull();
            assertThat(event.reservedCalls()).isZero(); assertThat(event.receivedBytes()).isZero();
        });
        assertThat(cancelled.transitions()).hasSize(2).allSatisfy(transition->{
            assertThat(transition.state()).isEqualTo("STOPPED_OPERATOR");
            assertThat(transition.reason()).isEqualTo("PREPARATION_CANCELLED");
            assertThat(transition.changedAt()).isEqualTo(cancelledAt); assertThat(transition.attemptId()).isNull();
        });
        f.store.cancelPreparation(manifest.campaignId(),manifest.manifestSha256(),cancelledAt.plusSeconds(1));
        assertThat(f.store.find(manifest.campaignId())).contains(cancelled);
        assertThat(f.guard.snapshot()).isEqualTo(guardBefore);
        assertThat(f.guard.isOwned(otherOwner)).isTrue();
        evidence.forEach((table,rows)->assertThat(f.jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text")).isEqualTo(rows));
        assertThat(f.jdbc.queryForObject("select count(*) from live_call_group where campaign_id=?",Long.class,manifest.campaignId())).isZero();
        assertThatThrownBy(()->f.store.cancelPreparation(manifest.campaignId(),"f".repeat(64),cancelledAt))
                .hasMessage("LIVE_MANIFEST_MISMATCH");
        assertThat(f.store.find(manifest.campaignId())).contains(cancelled);
    }

    @Test
    void cancellationDoesNotStopARunningCampaignOrAcceptAnUnknownManifest() {
        Fixture f=fixture("39"); Manifest manifest=groupedManifest(List.of(f.seed(EVENT))); f.start(manifest);
        CampaignView before=f.store.find(manifest.campaignId()).orElseThrow(); Guard guard=f.guard.snapshot();
        assertThatThrownBy(()->f.store.cancelPreparation(manifest.campaignId(),manifest.manifestSha256(),T0.plusSeconds(2)))
                .hasMessage("LIVE_PREPARATION_ALREADY_LAUNCHED");
        assertThatThrownBy(()->f.store.cancelPreparation(UUID.randomUUID(),manifest.manifestSha256(),T0.plusSeconds(2)))
                .isInstanceOf(NoSuchElementException.class).hasMessage("LIVE_CAMPAIGN_NOT_FOUND");
        assertThat(f.store.find(manifest.campaignId())).contains(before);
        assertThat(f.guard.snapshot()).isEqualTo(guard);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void launchAndCancellationSerializeOnThePreparationWithExactlyOneOutcome(boolean cancellationFirst) throws Exception {
        Fixture f=fixture("39"); Manifest manifest=groupedManifest(List.of(f.seed(EVENT)));
        f.store.prepare(manifest); Ownership owner=f.acquire(manifest);
        CountDownLatch firstWritten=new CountDownLatch(1),releaseFirst=new CountDownLatch(1),secondStarted=new CountDownLatch(1);
        AtomicInteger firstBackend=new AtomicInteger(),secondBackend=new AtomicInteger();
        JdbcTransactionManager transactions=new JdbcTransactionManager(f.ds);
        try(var pool=Executors.newFixedThreadPool(2)) {
            try {
                var first=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                    firstBackend.set(f.jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                    if(cancellationFirst) f.store.cancelPreparation(manifest.campaignId(),manifest.manifestSha256(),T0.plusSeconds(1));
                    else f.store.launch(manifest.campaignId(),manifest.manifestSha256(),owner,T0.plusSeconds(1));
                    firstWritten.countDown();
                    try { if(!releaseFirst.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("first transaction timeout"); }
                    catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    return true;
                }));
                assertThat(firstWritten.await(5,TimeUnit.SECONDS)).isTrue();
                var second=pool.submit(()->{
                    try {
                        return new TransactionTemplate(transactions).execute(status->{
                            secondBackend.set(f.jdbc.queryForObject("select pg_backend_pid()",Integer.class));secondStarted.countDown();
                            if(cancellationFirst) f.store.launch(manifest.campaignId(),manifest.manifestSha256(),owner,T0.plusSeconds(2));
                            else f.store.cancelPreparation(manifest.campaignId(),manifest.manifestSha256(),T0.plusSeconds(2));
                            return "unexpected success";
                        });
                    } catch(IllegalStateException rejected) {return rejected.getMessage();}
                });
                assertThat(secondStarted.await(5,TimeUnit.SECONDS)).isTrue();
                boolean blocked=false;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(!blocked && System.nanoTime()<until) {
                    blocked=Boolean.TRUE.equals(f.jdbc.queryForObject("select ? = any(pg_blocking_pids(?))",Boolean.class,firstBackend.get(),secondBackend.get()));
                    if(!blocked) Thread.sleep(10);
                }
                assertThat(blocked).as("the second command waits for the first preparation transaction").isTrue();
                assertThat(second.isDone()).isFalse();releaseFirst.countDown();
                assertThat(first.get(5,TimeUnit.SECONDS)).isTrue();
                assertThat(second.get(5,TimeUnit.SECONDS)).isEqualTo(cancellationFirst
                        ? "live preparation is expired or closed" : "LIVE_PREPARATION_ALREADY_LAUNCHED");
                CampaignView saved=f.store.find(manifest.campaignId()).orElseThrow();
                assertThat(saved.state()).isEqualTo(cancellationFirst ? "STOPPED_OPERATOR" : "RUNNING");
                assertThat(saved.events()).extracting(EventView::state).containsExactly(cancellationFirst ? "STOPPED_OPERATOR" : "INITIAL_CHECK");
                assertThat(saved.startedAt()).isEqualTo(cancellationFirst ? null : T0.plusSeconds(1));
                assertThat(saved.ownership()).isEqualTo(cancellationFirst ? null : owner);
                assertThat(saved.attempts()).isEmpty();assertThat(saved.reservedCalls()).isZero();
                assertThat(saved.transitions()).hasSize(cancellationFirst ? 2 : 1);
            } finally {releaseFirst.countDown();}
        }
    }

    @Test
    void upgradingV38RetainsHistoricalEvidenceAndRequiresExplicitGroupedPolicyForV4() {
        Fixture f=fixture("38"); Target target=f.seed(EVENT); Manifest old=f.manifest(target,100);
        Ownership own=f.start(old); ReservedAttempt historical=f.reserve(own,target,0,SofascoreEndpointType.EVENT_DETAILS);
        Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();
        for(String table:List.of("live_campaign","live_event","provider_snapshot","canonical_event_observation","event_detail_observation"))
            before.put(table,f.jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text"));
        String oldCall=f.jdbc.queryForObject("select to_jsonb(t)::text from live_call t",String.class);
        assertThat(f.migrate("39").migrationsExecuted).isEqualTo(1);
        before.forEach((table,rows)->assertThat(f.jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text")).isEqualTo(rows));
        assertThat(f.jdbc.queryForObject("select (to_jsonb(t)-'group_id'-'group_sequence'-'group_ordinal')::text from live_call t",String.class)).isEqualTo(oldCall);
        assertThat(f.store.find(old.campaignId()).orElseThrow().manifest()).isEqualTo(old);
        assertThat(f.store.find(old.campaignId()).orElseThrow().attempts().getFirst().attempt()).isEqualTo(historical);
        assertThat(f.jdbc.queryForObject("select count(*) from live_grouped_policy",Long.class)).isZero();
        assertThat(f.migrate("39").migrationsExecuted).isZero();
        assertThatThrownBy(()->f.jdbc.update("""
            insert into live_campaign(campaign_id,manifest_sha256,policy_version,prepared_at,expires_at,duration_seconds,
                maximum_calls_per_event,maximum_calls,maximum_bytes,qualified_match_capacity,target_count,
                request_envelope_nanos,processing_envelope_nanos,qualification_sha256,cycle_interval_seconds)
            values (?,?,'live-v4',?,?,14400,100,100,100000000,1,1,500000000,100000000,'',60)
            """,UUID.randomUUID(),"f".repeat(64),java.sql.Timestamp.from(T0),java.sql.Timestamp.from(T0.plusSeconds(300))))
                .hasMessageContaining("immutable qualified grouped profile");
    }

    @Test
    void groupedPolicyAttemptsAndFamilySchedulesRoundTripWithOwnershipAndImmutableEvidence() {
        Fixture f=fixture("39"); Target target=f.seed(EVENT); Manifest manifest=groupedManifest(List.of(target));
        Ownership own=f.start(manifest); UUID group=UUID.randomUUID();
        assertThat(f.store.find(manifest.campaignId()).orElseThrow().manifest()).isEqualTo(manifest);
        assertThat(f.store.prepare(manifest)).isEqualTo(manifest);
        AttemptRequest first=groupedRequest(own,target,0,SofascoreEndpointType.EVENT_DETAILS,group,0,0);
        ReservedAttempt reserved=f.store.reserveAttempt(first).orElseThrow();
        assertThat(f.store.reserveAttempt(first)).contains(reserved);
        assertThat(reserved.groupId()).isEqualTo(group);
        f.store.reserveAttempt(groupedRequest(own,target,0,SofascoreEndpointType.EVENT_INCIDENTS,group,0,1)).orElseThrow();
        assertThat(f.store.dispatchBudget(own,target.canonicalEventId())).isEqualTo(new DispatchBudget(2,0,2,"INITIAL_CHECK"));
        assertThatThrownBy(()->f.store.dispatchBudget(new Ownership(own.campaignId(),own.instanceId(),own.generation()+1),target.canonicalEventId()))
                .hasMessageContaining("ownership is stale");
        FamilySchedule schedule=new FamilySchedule(SofascoreEndpointType.EVENT_STATISTICS,T0.plusSeconds(70),60,2);
        f.store.updateFamilySchedule(own,target.canonicalEventId(),schedule,T0.plusSeconds(11));
        f.store.updateFamilySchedule(own,target.canonicalEventId(),schedule,T0.plusSeconds(12));
        assertThat(f.jdbc.queryForObject("select count(*) from live_family_schedule_revision",Long.class)).isEqualTo(1);
        assertThat(f.store.find(manifest.campaignId()).orElseThrow().events().getFirst().families())
                .anySatisfy(cursor->{assertThat(cursor.endpoint()).isEqualTo(SofascoreEndpointType.EVENT_STATISTICS);
                    assertThat(cursor.lastAttemptId()).isNull();assertThat(cursor.schedule()).isEqualTo(schedule);});
        assertThatThrownBy(()->f.store.updateFamilySchedule(new Ownership(own.campaignId(),UUID.randomUUID(),own.generation()),
                target.canonicalEventId(),schedule,T0.plusSeconds(13))).hasMessageContaining("ownership is stale");
        assertThatThrownBy(()->f.store.updateFamilySchedule(own,target.canonicalEventId(),
                new FamilySchedule(schedule.endpoint(),T0.plusSeconds(80),60,1),T0.plusSeconds(13))).hasMessageContaining("backwards");
        assertThatThrownBy(()->f.jdbc.update("update live_grouped_policy set qualification_sha256=? where campaign_id=?","b".repeat(64),manifest.campaignId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(()->f.jdbc.update("delete from live_call_group where group_id=?",group)).hasMessageContaining("append-only");
        assertThatThrownBy(()->f.jdbc.update("delete from live_family_schedule_revision where campaign_id=?",manifest.campaignId())).hasMessageContaining("append-only");
        f.store.transition(own,target.canonicalEventId(),"STOPPED_POSTPONED","POSTPONED",T0.plusSeconds(14),null);
        f.store.updateFamilySchedule(own,target.canonicalEventId(),schedule,T0.plusSeconds(15));
        assertThat(f.store.find(manifest.campaignId()).orElseThrow().events().getFirst().families())
                .filteredOn(cursor->cursor.endpoint()==schedule.endpoint()).singleElement()
                .satisfies(cursor->assertThat(cursor.schedule().nextDueAt()).isNull());
    }

    @Test
    void groupedReservationRejectsCrossEventOrReusedGroupsAndConcurrentDuplicateOrdinals() throws Exception {
        Fixture f=fixture("39"); Target one=f.seed(EVENT),two=f.seed(EVENT+1);
        Manifest manifest=groupedManifest(List.of(one,two));Ownership own=f.start(manifest);UUID group=UUID.randomUUID();
        f.store.reserveAttempt(groupedRequest(own,one,0,SofascoreEndpointType.EVENT_DETAILS,group,0,0)).orElseThrow();
        assertThatThrownBy(()->f.store.reserveAttempt(groupedRequest(own,two,0,SofascoreEndpointType.EVENT_INCIDENTS,group,0,1)))
                .hasMessageContaining("live_call_group_reference_fk");
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            var a=pool.submit(()->{ready.countDown();go.await();try{return f.store.reserveAttempt(groupedRequest(own,one,0,SofascoreEndpointType.EVENT_INCIDENTS,group,0,1)).isPresent();}catch(RuntimeException rejected){return false;}});
            var b=pool.submit(()->{ready.countDown();go.await();try{return f.store.reserveAttempt(groupedRequest(own,one,0,SofascoreEndpointType.EVENT_STATISTICS,group,0,1)).isPresent();}catch(RuntimeException rejected){return false;}});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(f.jdbc.queryForObject("select count(*) from live_call where group_id=?",Long.class,group)).isEqualTo(2);
        assertThat(f.store.find(manifest.campaignId()).orElseThrow().reservedCalls()).isEqualTo(2);
        f.store.reserveAttempt(groupedRequest(own,two,0,SofascoreEndpointType.EVENT_DETAILS,UUID.randomUUID(),1,0)).orElseThrow();
        assertThatThrownBy(()->f.store.reserveAttempt(groupedRequest(own,one,1,SofascoreEndpointType.EVENT_LINEUPS,group,0,2)))
                .hasMessageContaining("cannot be resumed");
        assertThatThrownBy(()->f.store.reserveAttempt(f.request(own,one,2,SofascoreEndpointType.EVENT_DETAILS)))
                .hasMessageContaining("requires a group");
    }

    private static Map<SofascoreEndpointType,EndpointEnvelope> syntheticGroupedEnvelopes() {
        Map<SofascoreEndpointType,EndpointEnvelope> envelopes=new EnumMap<>(SofascoreEndpointType.class);
        for(SofascoreEndpointType endpoint:List.of(SofascoreEndpointType.EVENT_DETAILS,SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS,SofascoreEndpointType.EVENT_LINEUPS))
            envelopes.put(endpoint,new EndpointEnvelope(Duration.ofMillis(500),Duration.ofMillis(100)));
        return envelopes;
    }
    private static Manifest groupedManifest(List<Target> targets) {
        return new Manifest(UUID.randomUUID(),"d".repeat(64),"live-v4",T0,T0.plusSeconds(300),Duration.ofHours(4),
                1000,3000,3000L*5*1024*1024,10,targets,
                new AdmissionProfile(Duration.ofSeconds(1),Duration.ofSeconds(1),"b".repeat(64),new GroupedAdmissionProfile(syntheticGroupedEnvelopes(),"e".repeat(64))),Duration.ofSeconds(60));
    }
    private static AttemptRequest groupedRequest(Ownership own,Target target,long cycle,SofascoreEndpointType endpoint,UUID group,long sequence,int ordinal) {
        Instant at=T0.plusSeconds(10+cycle*60);
        return new AttemptRequest(own,UUID.randomUUID(),target.canonicalEventId(),cycle,endpoint,"GROUPED",at,at,false,group,sequence,ordinal);
    }

    private static Manifest v5Manifest(List<Target> targets) {
        return new Manifest(UUID.randomUUID(),"f".repeat(64),"live-v5",T0,T0.plusSeconds(300),Duration.ofHours(4),
                2500,20000,15_728_640_000L,20,targets,
                new AdmissionProfile(Duration.ofSeconds(1),Duration.ofSeconds(1),"b".repeat(64),
                        new GroupedAdmissionProfile(syntheticGroupedEnvelopes(),"f".repeat(64),"live-v5")),Duration.ofSeconds(100));
    }

    @Test
    void upgradeV39ToV40KeepsAllHistoricalPoliciesAndEvidenceByteForByte() {
        Fixture f=fixture("39"); int eventIndex=0;
        for(String policy:List.of("live-v1","live-v2","live-v3","live-v4")) {
            Target target=f.seed(EVENT+eventIndex++);
            Manifest manifest="live-v4".equals(policy)?groupedManifest(List.of(target))
                    :new Manifest(UUID.randomUUID(),"a".repeat(64),policy,T0,T0.plusSeconds(300),Duration.ofHours(4),
                        100,100,100_000_000,1,List.of(target));
            Ownership own=f.start(manifest);
            ReservedAttempt call=f.store.reserveAttempt("live-v4".equals(policy)
                    ?groupedRequest(own,target,0,SofascoreEndpointType.EVENT_DETAILS,UUID.randomUUID(),0,0)
                    :f.request(own,target,0,SofascoreEndpointType.EVENT_DETAILS)).orElseThrow();
            f.store.recordDispatch(own,call.attemptId(),T0.plusSeconds(10));
            RawManualCallSnapshot raw=f.raw(target.providerEventId(),"before-v40",T0.plusSeconds(10));
            RawSnapshotPersistenceResult receipt=f.store.saveReceipt(own,call.attemptId(),raw);
            f.store.publishResult(own,call.attemptId(),f.success(raw.receivedAt(),policy),()->f.normalize(target.providerEventId(),policy,raw,receipt));
            if("live-v4".equals(policy)) f.store.updateFamilySchedule(own,target.canonicalEventId(),
                    new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,T0.plusSeconds(70),60,1),T0.plusSeconds(12));
            f.store.interruptOrphan(own,T0.plusSeconds(20),"OWNER_PROCESS_ABSENT");
            f.store.completeOrphanCleanup(f.guard.snapshot(),T0.plusSeconds(21));
        }
        Map<String,List<String>> before=new LinkedHashMap<>();
        for(String table:List.of("provider_campaign_guard","live_campaign","live_event","live_transition","live_call","live_call_dispatch",
                "live_call_receipt","live_call_result","live_grouped_policy","live_call_group","live_family_schedule","live_family_schedule_revision",
                "provider_snapshot","provider_snapshot_occurrence","canonical_event_observation","event_detail_observation"))
            before.put(table,f.jdbc.queryForList("select to_jsonb(t)::text from "+table+" t order by to_jsonb(t)::text",String.class));
        List<CampaignView> campaigns=f.store.findRecent(20);
        List<String> oldChecksums=f.jdbc.queryForList("select version || ':' || checksum::text from flyway_schema_history where version is not null order by installed_rank",String.class);
        assertThat(f.migrate("40").migrationsExecuted).isEqualTo(1);
        before.forEach((table,rows)->assertThat(f.jdbc.queryForList("select to_jsonb(t)::text from "+table+" t order by to_jsonb(t)::text",String.class)).isEqualTo(rows));
        assertThat(f.store.findRecent(20)).isEqualTo(campaigns);
        assertThat(f.jdbc.queryForList("select version || ':' || checksum::text from flyway_schema_history where version is not null and version<>'40' order by installed_rank",String.class)).isEqualTo(oldChecksums);
        assertThat(f.migrate("40").migrationsExecuted).isZero();
    }

    @Test
    void v5PersistsTwentyTargetsWithItsIndependentRawBudgetAndEnforcesVersionedBounds() {
        Fixture f=fixture("40"); List<Target> targets=new ArrayList<>();
        for(int i=0;i<20;i++) targets.add(f.seed(EVENT+i));
        Manifest v5=v5Manifest(targets); f.store.prepare(v5);
        assertThat(f.store.find(v5.campaignId()).orElseThrow().manifest()).isEqualTo(v5);
        assertThat(v5.maximumBytes()).isLessThan((long)v5.maximumCalls()*RawPayloadEvidence.MAXIMUM_BYTES);
        assertThat(f.jdbc.queryForMap("select critical_interval_seconds,lineup_interval_seconds,intra_group_delay_nanos,inter_group_delay_nanos from live_grouped_policy where campaign_id=?",v5.campaignId()))
                .containsEntry("critical_interval_seconds",100).containsEntry("lineup_interval_seconds",300)
                .containsEntry("intra_group_delay_nanos",0L).containsEntry("inter_group_delay_nanos",1_000_000_000L);
        // Isolated constraint probes: no claim of 2,500 real provider calls or cadence qualification.
        UUID eventId=targets.getFirst().canonicalEventId();
        f.jdbc.update("update live_event set reserved_calls=2500 where campaign_id=? and canonical_event_id=?",v5.campaignId(),eventId);
        assertThatThrownBy(()->f.jdbc.update("update live_event set reserved_calls=2501 where campaign_id=? and canonical_event_id=?",v5.campaignId(),eventId))
                .hasMessageContaining("policy budget");
        f.jdbc.update("update live_campaign set reserved_calls=20000 where campaign_id=?",v5.campaignId());
        assertThatThrownBy(()->f.jdbc.update("update live_campaign set reserved_calls=20001 where campaign_id=?",v5.campaignId()))
                .hasMessageContaining("check constraint");
        for(String policy:List.of("live-v1","live-v2","live-v3","live-v4")) {
            assertThatThrownBy(()->insertPolicyManifestRow(f,UUID.randomUUID(),policy,1001,3000,15_728_640_000L,20,60))
                    .hasMessageContaining("live_campaign_maximum_calls_per_event_check");
            assertThatThrownBy(()->insertPolicyManifestRow(f,UUID.randomUUID(),policy,1000,3001,15_728_640_000L,20,60))
                    .hasMessageContaining("live_campaign_maximum_calls_check");
        }
        Manifest old=groupedManifest(List.of(targets.getFirst())); f.store.prepare(old);
        assertThatThrownBy(()->f.jdbc.update("update live_event set reserved_calls=1001 where campaign_id=?",old.campaignId()))
                .hasMessageContaining("policy budget");
        assertThatThrownBy(()->insertPolicyManifestRow(f,UUID.randomUUID(),"live-v5",2501,20000,15_728_640_000L,20,100))
                .hasMessageContaining("live_campaign_maximum_calls_per_event_check");
        assertThatThrownBy(()->insertPolicyManifestRow(f,UUID.randomUUID(),"live-v5",2500,20001,15_728_640_000L,20,100))
                .hasMessageContaining("live_campaign_maximum_calls_check");
        for(long[] invalid:List.of(new long[]{15_728_640_001L,20,100},new long[]{15_728_640_000L,21,100},
                new long[]{15_728_640_000L,20,60},new long[]{15_728_640_000L,20,75}))
            assertThatThrownBy(()->insertPolicyManifestRow(f,UUID.randomUUID(),"live-v5",2500,20000,invalid[0],(int)invalid[1],(int)invalid[2]))
                    .hasMessageContaining("live_campaign_v5_policy_bounds_check");
        assertThatThrownBy(()->insertPolicyManifestRow(f,UUID.randomUUID(),"live-v5",2500,20000,15_728_640_000L,20,100))
                .hasMessageContaining("live-v5 requires its immutable qualified grouped profile");
    }

    @ParameterizedTest @ValueSource(strings={"live-v4","live-v5"})
    void groupedProfileCannotBorrowTheOtherVersionsCadenceOrPause(String policy) {
        Fixture f=fixture("40"); UUID campaignId=UUID.randomUUID(); boolean v5="live-v5".equals(policy);
        String envelopes="{\"EVENT_DETAILS\":{\"requestNanos\":500000000,\"processingNanos\":100000000},"
                +"\"EVENT_INCIDENTS\":{\"requestNanos\":500000000,\"processingNanos\":100000000},"
                +"\"EVENT_STATISTICS\":{\"requestNanos\":500000000,\"processingNanos\":100000000},"
                +"\"EVENT_LINEUPS\":{\"requestNanos\":500000000,\"processingNanos\":100000000}}";
        assertThatThrownBy(()->new TransactionTemplate(new JdbcTransactionManager(f.ds)).executeWithoutResult(status->{
            insertPolicyManifestRow(f,campaignId,policy,1000,3000,15_728_640_000L,20,v5?100:60);
            f.jdbc.update("""
                insert into live_grouped_policy(campaign_id,critical_interval_seconds,lineup_interval_seconds,intra_group_delay_nanos,
                    inter_group_delay_nanos,maximum_utilization_percent,qualification_sha256,endpoint_envelopes)
                values (?,?,300,0,?,90,?,cast(? as jsonb))
                """,campaignId,v5?60:100,v5?3_000_000_000L:1_000_000_000L,"a".repeat(64),envelopes);
        })).hasMessageContaining("profile cadence and delay must match");
        assertThat(f.store.find(campaignId)).isEmpty();
    }

    private static void insertPolicyManifestRow(Fixture f,UUID campaignId,String policy,int perEvent,int total,long bytes,int capacity,int interval) {
        f.jdbc.update("""
            insert into live_campaign(campaign_id,manifest_sha256,policy_version,prepared_at,expires_at,duration_seconds,
                maximum_calls_per_event,maximum_calls,maximum_bytes,qualified_match_capacity,target_count,
                request_envelope_nanos,processing_envelope_nanos,qualification_sha256,cycle_interval_seconds)
            values (?,?,?,?,?,14400,?,?,?,?,1,500000000,100000000,'',?)
            """,campaignId,"b".repeat(64),policy,java.sql.Timestamp.from(T0),java.sql.Timestamp.from(T0.plusSeconds(300)),
                perEvent,total,bytes,capacity,interval);
    }

    @Test
    void installsFreshSchemaAndKeepsV32PrepopulatedEvidenceUnchanged() {
        Fixture f=fixture("32"); Target target=f.seedHistorical(EVENT);
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
        assertThat(f.migrate("34").migrationsExecuted).isEqualTo(1);
        Manifest manifest=f.manifest(target,100);
        assertThat(f.store.prepare(manifest)).isEqualTo(manifest);
        assertThat(f.migrate("34").migrationsExecuted).isZero();
    }

    @Test
    void upgradingAPrepopulatedV33KeepsTheOldManifestAndSourceEvidenceWithItsSixtySecondInterval() {
        Fixture f = fixture("33"); Target target = f.seedHistorical(EVENT);
        Manifest old = f.manifest(target, 100);
        f.jdbc.update("""
            insert into live_campaign(campaign_id,manifest_sha256,policy_version,prepared_at,expires_at,
                duration_seconds,maximum_calls_per_event,maximum_calls,maximum_bytes,qualified_match_capacity,target_count,
                request_envelope_nanos,processing_envelope_nanos,qualification_sha256)
            values (?,?,'live-v1',?,?,14400,100,100,100000000,1,1,10000000000,1000000000,'')
            """, old.campaignId(), old.manifestSha256(), java.sql.Timestamp.from(T0), java.sql.Timestamp.from(T0.plusSeconds(300)));
        f.jdbc.update("""
            insert into live_event(campaign_id,canonical_event_id,provider_event_id,target_order,source_observation_id,source_snapshot_id)
            values (?,?,?,0,?,?)
            """, old.campaignId(), target.canonicalEventId(), target.providerEventId(), target.sourceObservationId(), target.sourceSnapshotId());
        String oldRow = f.jdbc.queryForObject("select to_jsonb(t)::text from live_campaign t", String.class);
        Map<String,List<Map<String,Object>>> before = new LinkedHashMap<>();
        for (String table : List.of("live_event", "provider_snapshot", "provider_snapshot_occurrence", "canonical_event_observation"))
            before.put(table, f.jdbc.queryForList("select to_jsonb(t)::text as row from " + table + " t order by to_jsonb(t)::text"));

        assertThat(f.migrate("34").migrationsExecuted).isEqualTo(1);

        assertThat(f.jdbc.queryForObject("select (to_jsonb(t) - 'cycle_interval_seconds')::text from live_campaign t", String.class)).isEqualTo(oldRow);
        before.forEach((table, rows) -> assertThat(f.jdbc.queryForList("select to_jsonb(t)::text as row from " + table + " t order by to_jsonb(t)::text")).isEqualTo(rows));
        assertThat(f.store.find(old.campaignId()).orElseThrow().manifest()).isEqualTo(old);
        assertThat(f.store.prepare(old)).isEqualTo(old);
        assertThat(f.migrate("34").migrationsExecuted).isZero();
    }

    @ParameterizedTest @ValueSource(ints = {4, 5, 10, 25})
    void configuredCeilingAndAdaptiveIntervalRoundTripAndEveryRetainedTargetCanDispatch(int count) {
        Fixture f = fixture("34");
        List<Target> targets = java.util.stream.IntStream.range(0, count).mapToObj(i -> f.seedHistorical(EVENT + i)).toList();
        Manifest m = new Manifest(UUID.randomUUID(), "c".repeat(64), "live-v2", T0, T0.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, 3000L * 5 * 1024 * 1024, 25, targets,
                new AdmissionProfile(Duration.ofSeconds(1), Duration.ofSeconds(1), "b".repeat(64)), LiveCadence.forMatches(count));
        Ownership own = f.start(m);
        assertThat(f.store.find(m.campaignId()).orElseThrow().manifest()).isEqualTo(m);
        assertThat(f.store.prepare(m)).isEqualTo(m);
        for (Target target : targets) assertThat(f.reserve(own, target, 0, SofascoreEndpointType.EVENT_DETAILS).canonicalEventId())
                .isEqualTo(target.canonicalEventId());
        assertThat(f.store.find(m.campaignId()).orElseThrow().reservedCalls()).isEqualTo(count);
        assertThatThrownBy(() -> f.jdbc.update("update live_campaign set cycle_interval_seconds=cycle_interval_seconds+30 where campaign_id=?", m.campaignId()))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> f.jdbc.update("update live_campaign set qualified_match_capacity=26 where campaign_id=?", m.campaignId()))
                .hasMessageContaining("immutable");
    }

    @Test
    void launchIsIdempotentAndManifestSelectionAndEvidenceAreImmutable() {
        Fixture f=fixture("38"); Manifest m=f.manifest(f.seed(EVENT),100); f.store.prepare(m);
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
    void v35PreservesPopulatedV2EvidenceAndEnforcesV3Cadence() {
        Fixture f=fixture("34"); Target target=f.seedHistorical(EVENT);
        Manifest legacy=new Manifest(UUID.randomUUID(),"a".repeat(64),"live-v2",T0,T0.plusSeconds(300),
                Duration.ofHours(4),100,100,100000000,1,List.of(target));
        Ownership own=f.start(legacy);
        ReservedAttempt a=f.reserve(own,target,0,SofascoreEndpointType.EVENT_DETAILS);
        f.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(10));
        RawManualCallSnapshot raw=f.raw(EVENT,"before-v35",T0.plusSeconds(10));
        var receipt=f.store.saveReceipt(own,a.attemptId(),raw);
        f.store.publishResult(own,a.attemptId(),f.success(raw.receivedAt(),"before-v35"),
                ()->f.normalizeHistorical(EVENT,"before-v35",raw,receipt));
        Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();
        for(String table:List.of("live_campaign","live_event","live_call","live_call_dispatch","live_call_receipt",
                "live_call_result","live_transition","provider_campaign_guard","provider_snapshot","provider_snapshot_occurrence"))
            before.put(table,f.jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text"));
        assertThat(f.migrate("35").migrationsExecuted).isEqualTo(1);
        before.forEach((table,rows)->assertThat(f.jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text")).isEqualTo(rows));
        assertThat(f.store.find(legacy.campaignId()).orElseThrow().manifest()).isEqualTo(legacy);
        Manifest current=new Manifest(UUID.randomUUID(),"c".repeat(64),"live-v3",T0,T0.plusSeconds(300),
                Duration.ofHours(4),100,100,100000000,1,List.of(target));
        f.store.prepare(current);
        assertThat(f.store.find(current.campaignId()).orElseThrow().manifest()).isEqualTo(current);
        assertThatThrownBy(()->f.jdbc.update("""
            insert into live_campaign select (jsonb_populate_record(null::live_campaign,
                to_jsonb(c) || jsonb_build_object('campaign_id',?::text,'cycle_interval_seconds',90))).*
            from live_campaign c where campaign_id=?
            """,UUID.randomUUID().toString(),current.campaignId()))
                .hasMessageContaining("live_campaign_v3_adaptive_cadence_check");
        assertThat(f.migrate("35").migrationsExecuted).isZero();
    }

    @Test
    void v3PrematchLineupsPersistExactCyclesAndProvenanceWhileRepeatedReceiptsRefreshTheCursor() {
        // Historical live policy, current versioned normalizer and current persistence schema.
        Fixture f = fixture("41");
        String detailsJson = """
                {"event":{"id":%d,"startTimestamp":%d,
                "homeTeam":{"id":11,"name":"Home"},"awayTeam":{"id":22,"name":"Away"},
                "status":{"type":"notstarted"}}}
                """.formatted(EVENT, T0.plusSeconds(3600).getEpochSecond());
        Instant seedAt = T0.minusSeconds(100);
        var seedResponse = new PlaywrightProviderResponse(seedAt, seedAt.plusMillis(100), 200,
                "application/json", Duration.ofMillis(100), RawPayloadEvidence.capture(detailsJson.getBytes(StandardCharsets.UTF_8)));
        var seedReceipt = f.rawStore.save(new RawManualCallSnapshot(SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=" + EVENT, seedAt, seedResponse.receivedAt(), 200, "application/json",
                seedResponse.latency(), seedResponse.payload(), "event-details-v2", RawSnapshotSchemaStatus.RAW_ONLY, null));
        var initialEvent = new ScheduledEvent(EVENT, T0.plusSeconds(3600), new ScheduledTeam(11, "Home"),
                new ScheduledTeam(22, "Away"), new ScheduledEventStatus("notstarted", Optional.empty()), Optional.empty());
        var seed = f.canonical.save(CanonicalEventObservation.from(initialEvent, EventSourceTrace.providerSnapshot(
                seedReceipt.snapshotId(), seedReceipt.payloadSha256(), "event-details-v2", seedResponse.receivedAt())));
        f.rawStore.classify(seedReceipt.snapshotId(), RawSnapshotSchemaStatus.PARSED, null);
        Target target = new Target(CanonicalEventIdentity.sofascore(EVENT).value(), EVENT,
                seed.observationId(), seedReceipt.snapshotId());
        Manifest m = new Manifest(UUID.randomUUID(), "e".repeat(64), "live-v3", T0, T0.plusSeconds(300),
                Duration.ofHours(4), 100, 100, 100_000_000L, 1, List.of(target));
        Ownership own = f.start(m);
        List<AttemptView> lineups = new ArrayList<>();
        for (int cycle = 0; cycle < 4; cycle++) {
            boolean prematch = cycle % 2 == 1;
            SofascoreEndpointType endpoint = prematch ? SofascoreEndpointType.EVENT_LINEUPS : SofascoreEndpointType.EVENT_DETAILS;
            String kind = prematch ? "J5_PREMATCH_LINEUPS" : cycle == 0 ? "J4_INITIAL" : "J4_WAIT";
            Instant at = T0.plusSeconds(10 + 60L * (cycle / 2) + (prematch ? 3 : 0));
            Instant due = cycle == 0 ? T0.plusSeconds(1) : cycle == 1 ? T0.plusSeconds(10).plusMillis(100) : at;
            AttemptRequest request = new AttemptRequest(own, UUID.randomUUID(), target.canonicalEventId(),
                    cycle, endpoint, kind, due, at, false);
            ReservedAttempt attempt = f.store.reserveAttempt(request).orElseThrow();
            assertThat(f.store.reserveAttempt(request)).contains(attempt);
            f.store.recordDispatch(own, attempt.attemptId(), at);
            String body = prematch ? "{\"confirmed\":false,\"home\":{\"players\":[]},\"away\":{\"players\":[]}}" : detailsJson;
            var response = new PlaywrightProviderResponse(at, at.plusMillis(100), 200,
                    "application/json", Duration.ofMillis(100), RawPayloadEvidence.capture(body.getBytes(StandardCharsets.UTF_8)));
            var raw = new RawManualCallSnapshot(endpoint, endpoint.name() + "|eventId=" + EVENT, at,
                    response.receivedAt(), 200, "application/json", Duration.ofMillis(100), response.payload(),
                    LivePayloadNormalizer.parserVersion(endpoint), RawSnapshotSchemaStatus.RAW_ONLY, null);
            var receipt = f.store.saveReceipt(own, attempt.attemptId(), raw);
            assertThat(f.store.saveReceipt(own, attempt.attemptId(), raw)).isEqualTo(receipt);
            var processed = f.processor.process(CanonicalEventIdentity.sofascore(EVENT), endpoint, response, receipt);
            assertThat(processed.scope()).isEqualTo(LiveProcessedResponse.FailureScope.NONE);
            var completeness = processed.completeness().orElse(null);
            var publication = new Publication(processed.outcome().name(), processed.scope().name(), processed.code(),
                    processed.receivedAt(), processed.parserVersion(), true, "WAITING_START", prematch ? null : "notstarted",
                    processed.projectionJson(), processed.projectionVersion(),
                    completeness == null ? null : completeness.status().name(), completeness == null ? null : completeness.scorePercent());
            Result result = f.store.publishResult(own, attempt.attemptId(), publication, () -> f.processor.persistProcessed(processed));
            CampaignView state = f.store.find(m.campaignId()).orElseThrow();
            AttemptView persisted = state.attempts().stream().filter(a -> a.attempt().attemptId().equals(attempt.attemptId())).findFirst().orElseThrow();
            assertThat(persisted.attempt()).isEqualTo(attempt);
            assertThat(persisted.dispatchAuthorizedAt()).isEqualTo(at);
            assertThat(persisted.snapshotId()).isEqualTo(receipt.snapshotId());
            assertThat(persisted.occurrenceId()).isEqualTo(receipt.occurrenceId().orElseThrow());
            assertThat(persisted.receivedAt()).isEqualTo(response.receivedAt());
            assertThat(persisted.result().normalized()).isEqualTo(result.normalized());
            assertThat(state.state()).isEqualTo("RUNNING");
            assertThat(state.events().getFirst().state()).isEqualTo("WAITING_START");
            assertThat(state.reservedCalls()).isEqualTo(cycle + 1);
            assertThat(state.events().getFirst().reservedCalls()).isEqualTo(cycle + 1);
            if (prematch) {
                lineups.add(persisted);
                assertThat(f.jdbc.queryForMap("""
                        select canonical_event_id,endpoint_type,source_kind,source_reference,source_snapshot_id,
                               source_payload_sha256,parser_version,normalized_sha256,
                               source_received_at = ? as original_reception
                        from j5_event_data_observation where id=?
                        """, java.sql.Timestamp.from(T0.plusSeconds(13).plusMillis(100)), result.normalized().j5ObservationId()))
                        .containsEntry("canonical_event_id", target.canonicalEventId()).containsEntry("endpoint_type", endpoint.name())
                        .containsEntry("source_kind", "PROVIDER_SNAPSHOT").containsEntry("source_reference", "snapshot:" + receipt.snapshotId())
                        .containsEntry("source_snapshot_id", receipt.snapshotId()).containsEntry("source_payload_sha256", receipt.payloadSha256())
                        .containsEntry("parser_version", LivePayloadNormalizer.parserVersion(endpoint))
                        .containsEntry("normalized_sha256", result.normalized().normalizedSha256()).containsEntry("original_reception", true);
            }
        }
        assertThat(lineups).hasSize(2);
        assertThat(lineups).extracting(a -> a.attempt().cycleNumber()).containsExactly(1L, 3L);
        assertThat(lineups).extracting(a -> a.attempt().kind()).containsOnly("J5_PREMATCH_LINEUPS");
        assertThat(lineups).allSatisfy(a -> assertThat(a.attempt().finalCycle()).isFalse());
        assertThat(lineups.get(1).snapshotId()).isEqualTo(lineups.get(0).snapshotId());
        assertThat(lineups.get(1).occurrenceId()).isNotEqualTo(lineups.get(0).occurrenceId());
        assertThat(lineups.get(1).result().normalized()).isEqualTo(lineups.get(0).result().normalized());
        CampaignView state = f.store.find(m.campaignId()).orElseThrow();
        FamilyCursor cursor = state.events().getFirst().families().stream()
                .filter(c -> c.endpoint() == SofascoreEndpointType.EVENT_LINEUPS).findFirst().orElseThrow();
        assertThat(cursor.lastSuccessfulAttemptId()).isEqualTo(lineups.get(1).attempt().attemptId());
        assertThat(cursor.lastChangedAttemptId()).isEqualTo(lineups.get(0).attempt().attemptId());
        assertThat(cursor.lastReceivedAt()).isEqualTo(T0.plusSeconds(73).plusMillis(100));
        assertThat(cursor.lastSuccessfulAt()).isEqualTo(cursor.lastReceivedAt());
        assertThat(cursor.lastChangedAt()).isEqualTo(T0.plusSeconds(13).plusMillis(100));
        assertThat(state.events().getFirst().families()).extracting(FamilyCursor::endpoint)
                .containsExactly(SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(state.events().getFirst().finalComplete()).isFalse();
    }

    @Test
    void identicalReceiptsAndAToBToAReturnToTheCorrectOldObservationWithNewFreshness() {
        Fixture f=fixture("38"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
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
        Fixture f=fixture("38"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
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
        Fixture f=fixture("38"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
        ReservedAttempt a=f.reserve(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS);
        long before=f.jdbc.queryForObject("select count(*) from provider_snapshot_occurrence",Long.class);
        // No dispatch row: FK must reject receipt, including the inner raw save in the same transaction.
        assertThatThrownBy(()->f.store.saveReceipt(own,a.attemptId(),f.raw(EVENT,"no-dispatch",T0.plusSeconds(10)))).isInstanceOf(RuntimeException.class);
        assertThat(f.jdbc.queryForObject("select count(*) from provider_snapshot_occurrence",Long.class)).isEqualTo(before);
        assertThat(f.store.find(m.campaignId()).orElseThrow().receivedBytes()).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"live-v1","live-v5"})
    void realConcurrentConnectionsCannotSpendLastOrdinaryBudgetTwice(String policy) throws Exception {
        boolean v5="live-v5".equals(policy); Fixture f=fixture(v5?"40":"38"); Target target=f.seed(EVENT);
        Manifest base=v5?v5Manifest(List.of(target)):f.manifest(target,5);
        Manifest m=v5?new Manifest(base.campaignId(),base.manifestSha256(),policy,base.preparedAt(),base.expiresAt(),base.duration(),
                5,5,base.maximumBytes(),base.qualifiedMatchCapacity(),base.targets(),base.admissionProfile(),base.cycleInterval()):base;
        Ownership own=f.start(m);
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{ready.countDown();go.await();return f.store.reserveAttempt(v5
                    ?groupedRequest(own,target,0,SofascoreEndpointType.EVENT_DETAILS,UUID.randomUUID(),0,0)
                    :f.request(own,target,0,SofascoreEndpointType.EVENT_DETAILS));});
            var second=pool.submit(()->{ready.countDown();go.await();return f.store.reserveAttempt(v5
                    ?groupedRequest(own,target,1,SofascoreEndpointType.EVENT_DETAILS,UUID.randomUUID(),1,0)
                    :f.request(own,target,1,SofascoreEndpointType.EVENT_DETAILS));});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)).stream().filter(Optional::isPresent).count()).isEqualTo(1);
        }
        assertThat(f.store.find(m.campaignId()).orElseThrow().reservedCalls()).isEqualTo(1);
        if(v5) {
            UUID finalGroup=UUID.randomUUID(); int ordinal=0;
            for(SofascoreEndpointType endpoint:List.of(SofascoreEndpointType.EVENT_DETAILS,SofascoreEndpointType.EVENT_INCIDENTS,
                    SofascoreEndpointType.EVENT_STATISTICS,SofascoreEndpointType.EVENT_LINEUPS)) {
                Instant at=T0.plusSeconds(160+ordinal);
                assertThat(f.store.reserveAttempt(new AttemptRequest(own,UUID.randomUUID(),target.canonicalEventId(),2,
                        endpoint,"FINAL",at,at,true,finalGroup,2,ordinal++))).isPresent();
            }
            assertThat(f.store.find(m.campaignId()).orElseThrow().reservedCalls()).isEqualTo(5);
            assertThat(f.store.reserveAttempt(groupedRequest(own,target,3,SofascoreEndpointType.EVENT_DETAILS,UUID.randomUUID(),3,0))).isEmpty();
            assertThat(f.jdbc.queryForObject("select count(*) from live_call where campaign_id=? and final_cycle",Long.class,m.campaignId())).isEqualTo(4);
        }
    }

    @Test
    void cleanupAndOldGenerationRemainFailClosedAndOrphanRecoveryNeverRearms() {
        Fixture f=fixture("38"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
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
    void orphanedV4CancelsOnlyPendingFamilySchedulesAndPreservesTheirRevisionHistory() {
        Fixture f=fixture("39"); Target target=f.seed(EVENT); Manifest manifest=groupedManifest(List.of(target));
        Ownership own=f.start(manifest);
        for(SofascoreEndpointType endpoint:List.of(SofascoreEndpointType.EVENT_DETAILS,SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS,SofascoreEndpointType.EVENT_LINEUPS)) {
            boolean lineups=endpoint==SofascoreEndpointType.EVENT_LINEUPS;
            f.store.updateFamilySchedule(own,target.canonicalEventId(),
                    new FamilySchedule(endpoint,lineups?null:T0.plusSeconds(60),lineups?300:60,2),T0.plusSeconds(10));
        }
        List<String> prior=f.jdbc.queryForList("select to_jsonb(r)::text from live_family_schedule_revision r order by revision",String.class);
        assertThat(prior).hasSize(4);
        f.store.interruptOrphan(own,T0.plusSeconds(20),"OWNER_PROCESS_ABSENT");
        CampaignView interrupted=f.store.find(manifest.campaignId()).orElseThrow();
        assertThat(interrupted.state()).isEqualTo("INTERRUPTED");
        assertThat(interrupted.manifest()).isEqualTo(manifest);
        assertThat(interrupted.events().getFirst().families()).hasSize(4).allSatisfy(family->{
            assertThat(family.schedule().nextDueAt()).isNull();
            assertThat(family.schedule().missedCycles()).isEqualTo(2);
            assertThat(family.schedule().intervalSeconds()).isEqualTo(family.endpoint()==SofascoreEndpointType.EVENT_LINEUPS?300:60);
        });
        List<String> revisions=f.jdbc.queryForList("select to_jsonb(r)::text from live_family_schedule_revision r order by revision",String.class);
        assertThat(revisions).hasSize(7).startsWith(prior.toArray(String[]::new));
        assertThat(f.jdbc.queryForObject("select count(*) from live_family_schedule_revision where next_due_at is null",Long.class)).isEqualTo(4);
        assertThat(f.jdbc.queryForObject("select count(*) from live_transition where state='FAMILY_SCHEDULED' and changed_at=?",
                Long.class,java.sql.Timestamp.from(T0.plusSeconds(20)))).isEqualTo(3);
        assertThat(f.guard.snapshot().state()).isEqualTo("CLEANUP_REQUIRED");
        f.store.interruptOrphan(own,T0.plusSeconds(21),"OWNER_PROCESS_ABSENT");
        assertThat(f.jdbc.queryForList("select to_jsonb(r)::text from live_family_schedule_revision r order by revision",String.class)).isEqualTo(revisions);
    }

    @ParameterizedTest @ValueSource(strings={"live-v1","live-v2","live-v3","live-v4"})
    void verifiedOrphanCleanupPreservesReceiptsUnknownAttemptsAndHistoryWithAnIdempotentAudit(String policy) {
        Fixture f=fixture("39"); Target target=f.seed(EVENT);
        Manifest manifest="live-v4".equals(policy)?groupedManifest(List.of(target))
                :new Manifest(UUID.randomUUID(),"a".repeat(64),policy,T0,T0.plusSeconds(300),Duration.ofHours(4),
                    100,100,100_000_000,1,List.of(target));
        Ownership own=f.start(manifest);
        for(int cycle=0;cycle<3;cycle++) {
            ReservedAttempt attempt=f.store.reserveAttempt("live-v4".equals(policy)
                    ?groupedRequest(own,target,cycle,SofascoreEndpointType.EVENT_DETAILS,UUID.randomUUID(),cycle,0)
                    :f.request(own,target,cycle,SofascoreEndpointType.EVENT_DETAILS)).orElseThrow();
            if(cycle==2) continue; // A reserved call with no dispatch must remain evidence too.
            Instant at=attempt.reservedAt(); f.store.recordDispatch(own,attempt.attemptId(),at);
            RawManualCallSnapshot raw=f.raw(EVENT,"orphan-"+cycle,at);
            RawSnapshotPersistenceResult receipt=f.store.saveReceipt(own,attempt.attemptId(),raw);
            if(cycle==0) f.store.publishResult(own,attempt.attemptId(),f.success(raw.receivedAt(),"parsed"),
                    ()->f.normalize(EVENT,"parsed",raw,receipt));
            // The second receipt has no publication when the owner exits.
        }
        if("live-v4".equals(policy)) f.store.updateFamilySchedule(own,target.canonicalEventId(),
                new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,T0.plusSeconds(190),60,2),T0.plusSeconds(140));
        f.store.interruptOrphan(own,T0.plusSeconds(150),"OWNER_PROCESS_ABSENT");
        Guard expected=f.guard.snapshot(); CampaignView before=f.store.find(manifest.campaignId()).orElseThrow();
        Map<String,List<String>> evidence=new LinkedHashMap<>();
        for(String table:List.of("provider_snapshot","provider_snapshot_occurrence","canonical_event_observation","event_detail_observation",
                "live_event","live_call","live_call_dispatch","live_call_receipt","live_call_result","live_call_group",
                "live_grouped_policy","live_family_schedule","live_family_schedule_revision"))
            evidence.put(table,f.jdbc.queryForList("select to_jsonb(t)::text from "+table+" t order by to_jsonb(t)::text",String.class));
        // Synthetic process proof belongs to the service tests; this test verifies only its atomic SQL boundary.
        f.store.completeOrphanCleanup(expected,T0.plusSeconds(160));
        CampaignView after=f.store.find(manifest.campaignId()).orElseThrow();
        assertThat(after).usingRecursiveComparison().ignoringFields("revision","transitions").isEqualTo(before);
        assertThat(after.state()).isEqualTo("INTERRUPTED"); assertThat(after.reason()).isEqualTo("OWNER_PROCESS_ABSENT");
        assertThat(after.reservedCalls()).isEqualTo(3); assertThat(after.receivedBytes()).isPositive();
        assertThat(after.attempts()).extracting(a->a.result().publication().outcome()).containsExactly("PARSED","UNKNOWN","UNKNOWN");
        assertThat(after.attempts().get(1).snapshotId()).isNotNull(); assertThat(after.attempts().get(2).dispatchAuthorizedAt()).isNull();
        assertThat(after.transitions()).hasSize(before.transitions().size()+1).startsWith(before.transitions().toArray(Transition[]::new));
        Transition cleanup=after.transitions().getLast();
        assertThat(cleanup.state()).isEqualTo("LOCAL_CLEANUP_VERIFIED");
        assertThat(cleanup.reason()).matches("GUARD_[0-9a-f]{64}");
        assertThat(cleanup.changedAt()).isEqualTo(T0.plusSeconds(160));
        assertThat(cleanup.canonicalEventId()).isNull(); assertThat(cleanup.attemptId()).isNull();
        Guard free=f.guard.snapshot(); assertThat(free.state()).isEqualTo("FREE"); assertThat(free.generation()).isEqualTo(expected.generation());
        assertThat(free.owner()).isNull(); assertThat(free.campaignId()).isNull();
        f.store.completeOrphanCleanup(expected,T0.plusSeconds(161));
        assertThat(f.store.find(manifest.campaignId())).contains(after); assertThat(f.guard.snapshot()).isEqualTo(free);
        evidence.forEach((table,rows)->assertThat(f.jdbc.queryForList("select to_jsonb(t)::text from "+table+" t order by to_jsonb(t)::text",String.class)).isEqualTo(rows));
        Guard altered=new Guard(expected.state(),expected.campaignId(),
                new Owner(expected.owner().instanceId(),expected.owner().processId()+1,expected.owner().processStartedAt()),
                expected.generation(),expected.changedAt());
        assertThatThrownBy(()->f.store.completeOrphanCleanup(altered,T0.plusSeconds(162)))
                .hasMessage("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
        Guard newOwner=f.guard.tryAcquire(UUID.randomUUID(),new Owner(UUID.randomUUID(),4321,T0.plusSeconds(160)),T0.plusSeconds(163)).orElseThrow();
        assertThatThrownBy(()->f.store.completeOrphanCleanup(expected,T0.plusSeconds(164)))
                .hasMessage("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
        assertThat(f.guard.snapshot()).isEqualTo(newOwner);
        assertThatThrownBy(()->f.store.reserveAttempt(f.request(own,target,3,SofascoreEndpointType.EVENT_DETAILS)))
                .hasMessageContaining("ownership is stale or closed");
    }

    @Test
    void orphanCleanupChecksEveryGuardIdentityFieldAndCannotClaimAnUnauditedFreeGuard() {
        Fixture f=fixture("39"); Manifest manifest=f.manifest(f.seed(EVENT),100); Ownership own=f.start(manifest);
        f.store.interruptOrphan(own,T0.plusSeconds(20),"OWNER_PROCESS_ABSENT");
        Guard expected=f.guard.snapshot(); Owner owner=expected.owner(); CampaignView before=f.store.find(manifest.campaignId()).orElseThrow();
        List<Guard> collisions=List.of(
                new Guard(expected.state(),UUID.randomUUID(),owner,expected.generation(),expected.changedAt()),
                new Guard(expected.state(),expected.campaignId(),new Owner(UUID.randomUUID(),owner.processId(),owner.processStartedAt()),expected.generation(),expected.changedAt()),
                new Guard(expected.state(),expected.campaignId(),new Owner(owner.instanceId(),owner.processId()+1,owner.processStartedAt()),expected.generation(),expected.changedAt()),
                new Guard(expected.state(),expected.campaignId(),new Owner(owner.instanceId(),owner.processId(),owner.processStartedAt().plusSeconds(1)),expected.generation(),expected.changedAt()),
                new Guard(expected.state(),expected.campaignId(),owner,expected.generation()+1,expected.changedAt()),
                new Guard(expected.state(),expected.campaignId(),owner,expected.generation(),expected.changedAt().plusSeconds(1)));
        for(Guard collision:collisions) assertThatThrownBy(()->f.store.completeOrphanCleanup(collision,T0.plusSeconds(30)))
                .hasMessage("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
        assertThat(f.guard.snapshot()).isEqualTo(expected); assertThat(f.store.find(manifest.campaignId())).contains(before);
        f.guard.releaseAfterVerifiedCleanup(own,T0.plusSeconds(31));
        Guard free=f.guard.snapshot();
        assertThatThrownBy(()->f.store.completeOrphanCleanup(expected,T0.plusSeconds(32)))
                .hasMessage("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
        assertThat(f.guard.snapshot()).isEqualTo(free); assertThat(f.store.find(manifest.campaignId())).contains(before);
    }

    @Test
    void orphanCleanupRefusesUnfinishedRuntimeEvidenceAndPendingFamilySchedules() {
        Fixture f=fixture("39"); Target target=f.seed(EVENT); Manifest manifest=groupedManifest(List.of(target)); Ownership own=f.start(manifest);
        ReservedAttempt call=f.store.reserveAttempt(groupedRequest(own,target,0,SofascoreEndpointType.EVENT_DETAILS,UUID.randomUUID(),0,0)).orElseThrow();
        f.store.updateFamilySchedule(own,target.canonicalEventId(),new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,T0.plusSeconds(70),60,0),T0.plusSeconds(10));
        f.guard.requireCleanup(own,T0.plusSeconds(11));
        Guard expected=f.guard.snapshot();
        assertThatThrownBy(()->f.store.completeOrphanCleanup(expected,T0.plusSeconds(12))).hasMessage("LIVE_ORPHAN_CLEANUP_INCOMPLETE");
        f.store.transition(own,null,"INTERRUPTED","OWNER_PROCESS_ABSENT",T0.plusSeconds(13),null);
        assertThatThrownBy(()->f.store.completeOrphanCleanup(expected,T0.plusSeconds(14))).hasMessage("LIVE_ORPHAN_CLEANUP_INCOMPLETE");
        f.store.transition(own,target.canonicalEventId(),"INTERRUPTED","OWNER_PROCESS_ABSENT",T0.plusSeconds(15),null);
        assertThatThrownBy(()->f.store.completeOrphanCleanup(expected,T0.plusSeconds(16))).hasMessage("LIVE_ORPHAN_CLEANUP_INCOMPLETE");
        f.store.publishResult(own,call.attemptId(),new Publication("UNKNOWN","CAMPAIGN","OWNER_PROCESS_ABSENT",T0.plusSeconds(17),null,false,null),NormalizedReferences::none);
        assertThatThrownBy(()->f.store.completeOrphanCleanup(expected,T0.plusSeconds(18))).hasMessage("LIVE_ORPHAN_CLEANUP_INCOMPLETE");
        assertThat(f.guard.snapshot()).isEqualTo(expected);
        assertThat(f.store.find(manifest.campaignId()).orElseThrow().transitions()).noneMatch(t->"LOCAL_CLEANUP_VERIFIED".equals(t.state()));
        f.store.updateFamilySchedule(own,target.canonicalEventId(),new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,null,60,0),T0.plusSeconds(19));
        f.store.completeOrphanCleanup(expected,T0.plusSeconds(20));
        assertThat(f.guard.snapshot().state()).isEqualTo("FREE");
    }

    @Test
    void orphanCleanupRollsBackItsAuditIfTheGuardReleaseFails() {
        Fixture f=fixture("39"); Manifest manifest=f.manifest(f.seed(EVENT),100); Ownership own=f.start(manifest);
        f.store.interruptOrphan(own,T0.plusSeconds(20),"OWNER_PROCESS_ABSENT");
        Guard expected=f.guard.snapshot(); CampaignView before=f.store.find(manifest.campaignId()).orElseThrow();
        f.jdbc.execute("""
            create function reject_test_orphan_release() returns trigger language plpgsql as $$
            begin if new.state='FREE' then raise exception 'synthetic guard release failure'; end if; return new; end $$
            """);
        f.jdbc.execute("create trigger reject_test_orphan_release before update on provider_campaign_guard for each row execute function reject_test_orphan_release()");
        assertThatThrownBy(()->f.store.completeOrphanCleanup(expected,T0.plusSeconds(21)))
                .hasMessageContaining("synthetic guard release failure");
        assertThat(f.guard.snapshot()).isEqualTo(expected); assertThat(f.store.find(manifest.campaignId())).contains(before);
        f.jdbc.execute("drop trigger reject_test_orphan_release on provider_campaign_guard");
        f.store.completeOrphanCleanup(expected,T0.plusSeconds(22));
        assertThat(f.guard.snapshot().state()).isEqualTo("FREE");
        assertThat(f.store.find(manifest.campaignId()).orElseThrow().transitions()).filteredOn(t->"LOCAL_CLEANUP_VERIFIED".equals(t.state())).hasSize(1);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void orphanCleanupSerializesWithARepeatedCleanupOrANewProviderAcquisition(boolean acquireNext) throws Exception {
        Fixture f=fixture("39"); Manifest manifest=f.manifest(f.seed(EVENT),100); Ownership own=f.start(manifest);
        f.store.interruptOrphan(own,T0.plusSeconds(20),"OWNER_PROCESS_ABSENT"); Guard expected=f.guard.snapshot();
        CountDownLatch firstWritten=new CountDownLatch(1),releaseFirst=new CountDownLatch(1),secondStarted=new CountDownLatch(1);
        AtomicInteger firstBackend=new AtomicInteger(),secondBackend=new AtomicInteger();
        JdbcTransactionManager transactions=new JdbcTransactionManager(f.ds);
        try(var pool=Executors.newFixedThreadPool(2)) {
            try {
                var first=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                    firstBackend.set(f.jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                    f.store.completeOrphanCleanup(expected,T0.plusSeconds(21)); firstWritten.countDown();
                    try {if(!releaseFirst.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("cleanup transaction timeout");}
                    catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    return true;
                }));
                assertThat(firstWritten.await(5,TimeUnit.SECONDS)).isTrue();
                var second=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                    secondBackend.set(f.jdbc.queryForObject("select pg_backend_pid()",Integer.class)); secondStarted.countDown();
                    if(acquireNext) return f.guard.tryAcquire(UUID.randomUUID(),new Owner(UUID.randomUUID(),4321,T0.plusSeconds(20)),T0.plusSeconds(22)).isPresent();
                    f.store.completeOrphanCleanup(expected,T0.plusSeconds(22)); return true;
                }));
                assertThat(secondStarted.await(5,TimeUnit.SECONDS)).isTrue();
                boolean blocked=false; long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(!blocked && System.nanoTime()<until) {
                    blocked=Boolean.TRUE.equals(f.jdbc.queryForObject("select ? = any(pg_blocking_pids(?))",Boolean.class,firstBackend.get(),secondBackend.get()));
                    if(!blocked) Thread.sleep(10);
                }
                assertThat(blocked).as("cleanup and acquisition use the same durable exclusion").isTrue();
                assertThat(second.isDone()).isFalse(); releaseFirst.countDown();
                assertThat(first.get(5,TimeUnit.SECONDS)).isTrue(); assertThat(second.get(5,TimeUnit.SECONDS)).isTrue();
                Guard saved=f.guard.snapshot(); assertThat(saved.state()).isEqualTo(acquireNext?"OWNED":"FREE");
                assertThat(saved.generation()).isEqualTo(expected.generation()+(acquireNext?1:0));
                CampaignView after=f.store.find(manifest.campaignId()).orElseThrow();
                assertThat(after.state()).isEqualTo("INTERRUPTED"); assertThat(after.reason()).isEqualTo("OWNER_PROCESS_ABSENT");
                assertThat(after.transitions()).filteredOn(t->"LOCAL_CLEANUP_VERIFIED".equals(t.state())).hasSize(1);
                if(acquireNext) {
                    assertThatThrownBy(()->f.store.completeOrphanCleanup(expected,T0.plusSeconds(23))).hasMessage("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
                    assertThat(f.guard.snapshot()).isEqualTo(saved);
                }
            } finally {releaseFirst.countDown();}
        }
    }

    @Test
    void lateReceiptAfterIndividualStopPreservesEvidenceWithoutReactivatingEvent() {
        Fixture f=fixture("38"); Manifest m=f.manifest(f.seed(EVENT),100); Ownership own=f.start(m);
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
        Fixture f=fixture("38");Manifest m=f.manifest(f.seed(EVENT),100);Ownership own=f.start(m);
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
        Fixture f=fixture("38");Target target=f.seed(EVENT);
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
        Fixture f = fixture("41"); Manifest m = f.manifest(f.seed(EVENT), 100); Ownership own = f.start(m);
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
        Fixture f = fixture("38"); Manifest m = f.manifest(f.seed(EVENT), 100); Ownership own = f.start(m);
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
        Fixture f=fixture("38"); Target target=f.seed(EVENT);
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
        Fixture f=fixture("38"); Target target=f.seed(EVENT);
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
        Fixture f=fixture("38");Manifest m=f.manifest(f.seed(EVENT),100);Ownership own=f.start(m);
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
        Fixture f=fixture("39");Manifest m=f.manifest(f.seed(EVENT),100);Ownership own=f.start(m);
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
        // Exercise the historical ledger first, then upgrade it for the current J6 tooling.
        var campaignBeforeUpgrade=f.store.find(m.campaignId()).orElseThrow();
        var guardBeforeUpgrade=f.guard.snapshot();
        assertThat(f.migrate("45").migrationsExecuted).isEqualTo(6);
        assertThat(f.store.find(m.campaignId())).contains(campaignBeforeUpgrade);
        assertThat(f.guard.snapshot()).isEqualTo(guardBeforeUpgrade);
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

    @ParameterizedTest @ValueSource(strings={"live-v4","live-v5"})
    void restoresAllLiveEvidenceAndFreeGuardWithoutRearmingExecution(String policy) throws Exception {
        String schema="live-v5".equals(policy)?"40":"39";
        Fixture source=fixture(schema); Target target=source.seed(EVENT);
        Manifest m="live-v5".equals(policy)?v5Manifest(List.of(target)):groupedManifest(List.of(target)); Ownership own=source.start(m);
        long interval=m.cycleInterval().toSeconds();
        ReservedAttempt a=source.store.reserveAttempt(groupedRequest(own,m.targets().getFirst(),0,SofascoreEndpointType.EVENT_DETAILS,UUID.randomUUID(),0,0)).orElseThrow();
        source.store.recordDispatch(own,a.attemptId(),T0.plusSeconds(10));
        RawManualCallSnapshot raw=source.raw(EVENT,"restore",T0.plusSeconds(10));
        RawSnapshotPersistenceResult receipt=source.store.saveReceipt(own,a.attemptId(),raw);
        source.store.publishResult(own,a.attemptId(),source.success(raw.receivedAt(),"restore"),()->source.normalize(EVENT,"restore",raw,receipt));
        source.store.updateFamilySchedule(own,m.targets().getFirst().canonicalEventId(),
                new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,T0.plusSeconds(10+interval),interval,0),T0.plusSeconds(11));
        source.store.transition(own,m.targets().getFirst().canonicalEventId(),"STOPPED_OPERATOR","OPERATOR_STOP",T0.plusSeconds(12),null);
        source.store.updateFamilySchedule(own,m.targets().getFirst().canonicalEventId(),
                new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,null,interval,0),T0.plusSeconds(12));
        source.store.transition(own,null,"COMPLETED","CLEANUP_VERIFIED",T0.plusSeconds(13),null);
        source.guard.releaseAfterVerifiedCleanup(own,T0.plusSeconds(14));
        // Keep v4/v5 execution evidence on its original schema, then qualify today's backup on V45.
        var campaignBeforeUpgrade=source.store.find(m.campaignId()).orElseThrow();
        var guardBeforeUpgrade=source.guard.snapshot();
        assertThat(source.migrate("45").migrationsExecuted).isEqualTo(45-Integer.parseInt(schema));
        assertThat(source.store.find(m.campaignId())).contains(campaignBeforeUpgrade);
        assertThat(source.guard.snapshot()).isEqualTo(guardBeforeUpgrade);
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
            assertThat(restored.migrate("45").migrationsExecuted).isZero();
            assertThat(restored.guard.snapshot().state()).isEqualTo("FREE");
            assertThat(restored.store.find(m.campaignId()).orElseThrow().state()).isEqualTo("COMPLETED");
            assertThat(restored.store.find(m.campaignId()).orElseThrow().attempts()).hasSize(1);
            assertThat(restored.store.find(m.campaignId()).orElseThrow().manifest()).isEqualTo(m);
            assertThat(restored.store.find(m.campaignId()).orElseThrow().attempts().getFirst().attempt().groupId()).isEqualTo(a.groupId());
            assertThat(restored.jdbc.queryForObject("select count(*) from live_family_schedule_revision",Long.class)).isEqualTo(2);
        } finally {
            POSTGRES.execInContainer("dropdb","--username",POSTGRES.getUsername(),"--if-exists","--force",restoredDatabase);
            POSTGRES.execInContainer("rm","-f",dump);
        }
    }

    @Test
    void cleanupBarrierWaitsForAnUnresolvedLaunchCommitBeforeAcceptingPreparationEvidence() throws Exception {
        Fixture f = fixture("38");
        Manifest manifest = f.manifest(f.seed(EVENT), 100);
        f.store.prepare(manifest);
        Ownership ownership = f.acquire(manifest);
        CountDownLatch launchWritten = new CountDownLatch(1), allowCommit = new CountDownLatch(1);
        CountDownLatch barrierStarted = new CountDownLatch(1);
        AtomicInteger launchBackend = new AtomicInteger(), barrierBackend = new AtomicInteger();

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var launch = pool.submit(() -> new TransactionTemplate(new JdbcTransactionManager(f.ds)).execute(status -> {
                    launchBackend.set(f.jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    Launch result = f.store.launch(manifest.campaignId(), manifest.manifestSha256(), ownership, T0.plusSeconds(1));
                    launchWritten.countDown();
                    try {
                        if (!allowCommit.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("launch commit was not released");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("launch commit wait interrupted", interrupted);
                    }
                    return result;
                }));
                assertThat(launchWritten.await(5, TimeUnit.SECONDS)).isTrue();

                // A normal MVCC read can still report PREPARED while launch owns the guard lock.
                CampaignView stalePreparation = f.store.find(manifest.campaignId()).orElseThrow();
                assertThat(stalePreparation.state()).isEqualTo("PREPARED");
                assertThat(stalePreparation.ownership()).isNull();
                assertThat(stalePreparation.startedAt()).isNull();
                assertThat(stalePreparation.attempts()).isEmpty();

                var barrier = pool.submit(() -> {
                    new TransactionTemplate(new JdbcTransactionManager(f.ds)).executeWithoutResult(status -> {
                        barrierBackend.set(f.jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                        barrierStarted.countDown();
                        f.guard.requireCleanup(ownership, T0.plusSeconds(2));
                    });
                    return f.store.find(manifest.campaignId()).orElseThrow();
                });
                assertThat(barrierStarted.await(5, TimeUnit.SECONDS)).isTrue();
                boolean blockedByLaunch = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!blockedByLaunch && System.nanoTime() < deadline) {
                    blockedByLaunch = Boolean.TRUE.equals(f.jdbc.queryForObject(
                            "select ? = any(pg_blocking_pids(?))", Boolean.class, launchBackend.get(), barrierBackend.get()));
                    if (!blockedByLaunch) Thread.sleep(10);
                }
                assertThat(blockedByLaunch).as("cleanup must wait on the unresolved launch transaction").isTrue();
                assertThat(barrier.isDone()).isFalse();

                allowCommit.countDown();
                assertThat(launch.get(5, TimeUnit.SECONDS).newlyLaunched()).isTrue();
                CampaignView committedLaunch = barrier.get(5, TimeUnit.SECONDS);
                assertThat(committedLaunch.state()).isEqualTo("RUNNING");
                assertThat(committedLaunch.ownership()).isEqualTo(ownership);
                assertThat(committedLaunch.startedAt()).isEqualTo(T0.plusSeconds(1));
                assertThat(committedLaunch.events()).singleElement().satisfies(event -> {
                    assertThat(event.state()).isEqualTo("INITIAL_CHECK");
                    assertThat(event.nextDueAt()).isEqualTo(T0.plusSeconds(1));
                });
                assertThat(f.guard.snapshot().state()).isEqualTo("CLEANUP_REQUIRED");
                assertThat(f.guard.isOwned(ownership)).isFalse();
                assertThatThrownBy(() -> f.reserve(ownership, manifest.targets().getFirst(), 0, SofascoreEndpointType.EVENT_DETAILS))
                        .hasMessageContaining("ownership is stale or closed");
                assertThat(f.store.find(manifest.campaignId()).orElseThrow().reservedCalls()).isZero();
            } finally {
                allowCommit.countDown();
            }
        }
    }

    @Test
    void concurrentProviderOwnersCannotAcquireTheSameDurableGuard() throws Exception {
        Fixture f=fixture("38");CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
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
        Target seedHistorical(long event) {
            RawManualCallSnapshot raw=raw(event,"seed",T0.minusSeconds(100));RawSnapshotPersistenceResult p=rawStore.save(raw);
            NormalizedReferences normalized=normalizeHistorical(event,"seed",raw,p);
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
            return normalize(event,value,raw,p,false);
        }
        NormalizedReferences normalizeHistorical(long event,String value,RawManualCallSnapshot raw,RawSnapshotPersistenceResult p) {
            return normalize(event,value,raw,p,true);
        }
        private NormalizedReferences normalize(long event,String value,RawManualCallSnapshot raw,RawSnapshotPersistenceResult p,
                                               boolean historicalSchema) {
            EventDetails d=new EventDetails(event,T0,new ScheduledTeam(11,"Home"),new ScheduledTeam(22,"Away"),
                    new ScheduledEventStatus("inprogress",Optional.of(value)),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());
            EventSourceTrace source=EventSourceTrace.providerSnapshot(p.snapshotId(),p.payloadSha256(),"event-details-v2",raw.receivedAt());
            long canonicalId=canonical.save(CanonicalEventObservation.from(d.asScheduledEvent(),source)).observationId();
            EventDetailObservation observation=EventDetailObservation.from(CanonicalEventIdentity.sofascore(event),d,source);
            long detailId=historicalSchema ? saveHistoricalDetails(observation) : details.save(observation).observationId();
            if(p.outcome()==RawSnapshotPersistenceOutcome.INSERTED) rawStore.classify(p.snapshotId(),RawSnapshotSchemaStatus.PARSED,null);
            return new NormalizedReferences(canonicalId,detailId,null,observation.normalizedSha256());
        }
        /** Populate the actual pre-V38 schema, without using a store that now names V38 columns. */
        private long saveHistoricalDetails(EventDetailObservation observation) {
            EventDetails d=observation.details();EventSourceTrace source=observation.source();
            assertThat(source.parserVersion()).isEqualTo("event-details-v2");
            assertThat(d.isAwarded()).isEmpty();
            assertThat(d.homeDisplayScore()).isEmpty();
            assertThat(d.awayDisplayScore()).isEmpty();
            assertThat(d.tournament()).isEmpty();
            assertThat(d.venue()).isEmpty();
            assertThat(d.season()).isEmpty();
            assertThat(d.round()).isEmpty();
            jdbc.update("""
                insert into event_detail_observation(canonical_event_id,source_kind,source_reference,source_snapshot_id,
                    source_payload_sha256,parser_version,source_received_at,starts_at,home_team_provider_id,home_team_name,
                    away_team_provider_id,away_team_name,status_type,status_description,normalized_sha256)
                values (?,'PROVIDER_SNAPSHOT',?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict(canonical_event_id,source_kind,source_reference,normalized_sha256) do nothing
                """,observation.identity().value(),source.sourceReference(),source.snapshotId().orElseThrow(),
                    source.payloadSha256(),source.parserVersion(),java.sql.Timestamp.from(source.receivedAt()),
                    java.sql.Timestamp.from(d.startsAt()),d.homeTeam().providerTeamId(),d.homeTeam().name(),
                    d.awayTeam().providerTeamId(),d.awayTeam().name(),d.status().type(),
                    d.status().description().orElse(null),observation.normalizedSha256());
            return jdbc.queryForObject("""
                select id from event_detail_observation where canonical_event_id=? and source_kind='PROVIDER_SNAPSHOT'
                    and source_reference=? and normalized_sha256=?
                """,Long.class,observation.identity().value(),source.sourceReference(),observation.normalizedSha256());
        }
    }
}
