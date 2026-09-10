package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.JdbcRawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.adapter.persistence.live.JdbcProviderResilienceStore;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.*;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessException;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Isolated PostgreSQL only. Logical time is supplied explicitly; there is no browser/provider startup. */
@Testcontainers
class ProviderResiliencePersistenceIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("provider_resilience_admin").withUsername("sofascore_lab").withPassword("integration-test-only");
    private static final AtomicInteger DATABASE=new AtomicInteger();
    private static final Instant T0=Instant.parse("2030-09-09T12:00:00Z");

    @Test
    void freshInstallAndUpgradeOfPopulatedV41ToV50LeaveOldEvidenceAndProviderGuardUntouched() {
        Fixture f=fixture("41");
        RawManualCallSnapshotStore raw=transactional(new JdbcRawManualCallSnapshotStore(new NamedParameterJdbcTemplate(f.jdbc)),
                RawManualCallSnapshotStore.class,f.transactions);
        RawSnapshotPersistenceResult saved=raw.save(new RawManualCallSnapshot(SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=58001",T0,T0.plusSeconds(1),200,"application/json",Duration.ofSeconds(1),
                RawPayloadEvidence.capture("{\"synthetic\":true}".getBytes(StandardCharsets.UTF_8)),
                "event-details-v2",RawSnapshotSchemaStatus.RAW_ONLY,null));
        List<Map<String,Object>> before=f.rows("provider_snapshot"),occurrences=f.rows("provider_snapshot_occurrence");
        List<Map<String,Object>> guard=f.rows("provider_campaign_guard");
        assertThat(saved.snapshotId()).isPositive();
        assertThat(f.migrate("42")).isEqualTo(1);
        assertThat(f.rows("provider_snapshot")).isEqualTo(before);
        assertThat(f.rows("provider_snapshot_occurrence")).isEqualTo(occurrences);
        assertThat(f.rows("provider_campaign_guard")).isEqualTo(guard);
        assertThat(f.jdbc.queryForObject("select state from provider_resilience_state where singleton_id=1",String.class)).isEqualTo("OPEN");
        assertThat(f.count("provider_resilience_event")).isZero();
        assertThat(f.count("provider_departure_reservation")).isZero();
        assertThat(f.migrate("42")).isZero();
        assertThat(f.migrate("48")).isEqualTo(6);
        assertThat(f.migrate("48")).isZero();
        assertThat(f.migrate("50")).isEqualTo(2);
        assertThat(f.count("provider_departure_accounting")).isZero();
        assertThat(f.store.snapshot().state()).isEqualTo(State.OPEN);
        assertThat(f.store.snapshot().version()).isZero();
        assertThat(f.store.snapshot().evidenceId()).isNull();
        assertThat(f.migrate("50")).isZero();
        Fixture fresh=fixture("50");
        assertThat(fresh.store.departureDecision(T0).allowed()).isTrue();
    }

    @Test
    void refusalSurvivesARepositoryRestartAndANewCampaignUntilExplicitVersionedRearm() {
        Fixture f=fixture("50"); UUID evidence=UUID.randomUUID(),manualCampaign=UUID.randomUUID();
        f.exchange(UUID.randomUUID(),T0);
        Snapshot suspended=f.store.suspend(evidence,manualCampaign,403,T0.plusSeconds(1),null);
        assertThat(suspended.state()).isEqualTo(State.SUSPENDED);
        assertThat(suspended.evidenceId()).isEqualTo(evidence);
        assertThat(suspended.campaignId()).isEqualTo(manualCampaign);
        ProviderResilienceStore restarted=f.newStore();
        assertThat(restarted.snapshot()).isEqualTo(suspended);
        assertThat(restarted.tryReserveDeparture(UUID.randomUUID(),T0.plus(Duration.ofDays(2))).reason())
                .isEqualTo(DepartureReason.PROVIDER_SUSPENDED);
        assertThat(f.count("provider_departure_reservation")).isEqualTo(1);
        assertThatThrownBy(()->restarted.rearm(suspended.version()-1,T0.plusSeconds(2)))
                .hasMessage("PROVIDER_REARM_STALE_VERSION");
        Snapshot rearmed=restarted.rearm(suspended.version(),T0.plusSeconds(2));
        assertThat(rearmed.state()).isEqualTo(State.OPEN);
        assertThat(rearmed.httpStatus()).isEqualTo(403);
        assertThat(rearmed.evidenceId()).isEqualTo(evidence);
        assertThat(rearmed.lastDepartureAt()).isEqualTo(T0);
        assertThat(restarted.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(2)).allowed()).isTrue();
        assertThat(f.jdbc.queryForList("select kind from provider_resilience_event order by state_version",String.class))
                .containsExactly("REFUSAL","MANUAL_REARM");
        assertThat(f.count("provider_departure_reservation")).isEqualTo(2);
        assertThatThrownBy(()->restarted.rearm(rearmed.version(),T0.plusSeconds(3)))
                .hasMessage("PROVIDER_REARM_NOT_SUSPENDED");
    }

    @Test
    void retryAfterIsAnEarliestManualRearmDateAndNeverAnAutomaticResume() {
        Fixture f=fixture("50"); Instant retry=T0.plusSeconds(3600);
        Snapshot suspended=f.store.suspend(UUID.randomUUID(),null,429,T0,retry);
        assertThatThrownBy(()->f.store.rearm(suspended.version(),retry.minusNanos(1000)))
                .hasMessage("PROVIDER_REARM_TOO_EARLY");
        assertThat(f.store.departureDecision(retry.plusSeconds(1)).reason()).isEqualTo(DepartureReason.PROVIDER_SUSPENDED);
        assertThat(f.store.snapshot()).isEqualTo(suspended);
        assertThat(f.store.rearm(suspended.version(),retry).state()).isEqualTo(State.OPEN);
        assertThat(f.count("provider_departure_reservation")).isZero();
    }

    @Test
    void repeatedRefusalIsIdempotentAndNewEvidenceInvalidatesAStaleRearmWithoutReplacingPrimaryCause() {
        Fixture f=fixture("50"); UUID evidence=UUID.randomUUID();
        Snapshot first=f.store.suspend(evidence,null,403,T0,null);
        assertThat(f.store.suspend(evidence,null,403,T0,null)).isEqualTo(first);
        assertThat(f.count("provider_resilience_event")).isEqualTo(1);
        assertThatThrownBy(()->f.store.suspend(evidence,null,429,T0,null))
                .hasMessage("PROVIDER_REFUSAL_EVIDENCE_COLLISION");
        Snapshot second=f.store.suspend(UUID.randomUUID(),UUID.randomUUID(),429,T0.plusSeconds(1),T0.plusSeconds(120));
        assertThat(second.httpStatus()).isEqualTo(403);
        assertThat(second.suspendedAt()).isEqualTo(T0);
        assertThat(second.evidenceId()).isEqualTo(evidence);
        assertThat(second.retryNotBefore()).isEqualTo(T0.plusSeconds(120));
        assertThatThrownBy(()->f.store.rearm(first.version(),T0.plusSeconds(120)))
                .hasMessage("PROVIDER_REARM_STALE_VERSION");
        Snapshot third=f.store.suspend(UUID.randomUUID(),null,429,T0.plusSeconds(2),T0.plusSeconds(30));
        assertThat(third.retryNotBefore()).isEqualTo(T0.plusSeconds(120));
        assertThat(f.count("provider_resilience_event")).isEqualTo(3);
    }

    @Test
    void aLateDuplicateOfTheSameRefusalDoesNotResuspendAnExplicitlyRearmedProvider() {
        Fixture f=fixture("50"); UUID evidence=UUID.randomUUID();
        Snapshot refused=f.store.suspend(evidence,null,403,T0,null);
        Snapshot rearmed=f.store.rearm(refused.version(),T0.plusSeconds(1));
        assertThat(f.store.suspend(evidence,null,403,T0,null)).isEqualTo(rearmed);
        assertThat(f.count("provider_resilience_event")).isEqualTo(2);
    }

    @Test
    void reservationIsSingleUseAndReadOnlyPreviewNeitherChargesNorGrantsADispatch() {
        Fixture f=fixture("50"); UUID dispatch=UUID.randomUUID();
        assertThat(f.store.departureDecision(T0).allowed()).isTrue();
        assertThat(f.store.departureDecision(T0).allowed()).isTrue();
        assertThat(f.count("provider_departure_reservation")).isZero();
        assertThat(f.store.tryReserveDeparture(dispatch,T0).allowed()).isTrue();
        f.store.markDepartureFinished(dispatch,T0);
        assertThat(f.store.tryReserveDeparture(dispatch,T0.plusSeconds(100)).reason())
                .isEqualTo(DepartureReason.DISPATCH_ALREADY_RESERVED);
        assertThat(f.store.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(1)).nextAllowedAt()).isEqualTo(T0.plusSeconds(2));
        assertThat(f.count("provider_departure_reservation")).isEqualTo(1);
        assertThat(f.newStore().tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(2)).allowed()).isTrue();
    }

    @Test
    void twentyInitialMatchGroupsCannotBurstBeyondTheRollingMinuteBudgetAndThereIsNoRefundOnRefusal() {
        Fixture f=fixture("50");
        for(int i=0;i<25;i++) assertThat(f.exchange(UUID.randomUUID(),T0.plusSeconds(i*2L)).allowed()).isTrue();
        DepartureDecision denied=f.store.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(50));
        assertThat(denied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(denied.nextAllowedAt()).isEqualTo(T0.plusSeconds(60));
        Snapshot refusal=f.store.suspend(UUID.randomUUID(),null,403,T0.plusSeconds(51),null);
        f.newStore().rearm(refusal.version(),T0.plusSeconds(52));
        assertThat(f.newStore().departureDecision(T0.plusSeconds(59)).reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(f.exchange(UUID.randomUUID(),T0.plusSeconds(60)).allowed()).isTrue();
        assertThat(f.store.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(62)).allowed()).isTrue();
        assertThat(f.count("provider_departure_reservation")).isEqualTo(27);
    }

    @Test
    void hourlyWindowSurvivesRepositoryRestartAndExpiresAtTheExactBoundary() {
        Fixture f=fixture("50");
        // Synthetic existing pressure: 1,000 calls spaced 3.5 s, all conform to the shorter limits.
        f.jdbc.update("""
            insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version)
                select gen_random_uuid(),?::timestamptz+n*interval '3.5 seconds','provider-resilience-v1'
                    from generate_series(0,999) n
            """,Timestamp.from(T0));
        f.jdbc.update("insert into provider_departure_completion(dispatch_id,finished_at) select dispatch_id,reserved_at from provider_departure_reservation");
        Instant last=T0.plusMillis(999*3500L);
        f.jdbc.update("update provider_resilience_state set last_departure_at=?,last_departure_finished_at=? where singleton_id=1",Timestamp.from(last),Timestamp.from(last));
        DepartureDecision denied=f.newStore().tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(3500));
        assertThat(denied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(denied.nextAllowedAt()).isEqualTo(T0.plusSeconds(3600));
        assertThat(f.exchange(UUID.randomUUID(),T0.plusSeconds(3600)).allowed()).isTrue();
        assertThat(f.store.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(3602)).nextAllowedAt()).isEqualTo(T0.plusMillis(3603500));
    }

    @Test
    void concurrentProcessesAtTheSameDepartureTimeReserveExactlyOneCall() throws Exception {
        Fixture f=fixture("50"); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<DepartureDecision>> results=new ArrayList<>();
            for(int i=0;i<8;i++) results.add(pool.submit(()->{start.await();return f.newStore().tryReserveDeparture(UUID.randomUUID(),T0);}));
            start.countDown();
            List<DepartureDecision> decisions=new ArrayList<>();
            for(var result:results) decisions.add(result.get(10,TimeUnit.SECONDS));
            assertThat(decisions.stream().filter(DepartureDecision::allowed).count()).isEqualTo(1);
            assertThat(decisions.stream().filter(d->!d.allowed())).allSatisfy(d->assertThat(d.reason()).isEqualTo(DepartureReason.DEPARTURE_UNRESOLVED));
        }
        assertThat(f.count("provider_departure_reservation")).isEqualTo(1);
    }

    @Test
    void committedRefusalWinsAgainstAnAdmissionBlockedOnTheSharedDatabaseLock() throws Exception {
        Fixture f=fixture("50"); CountDownLatch refusalWritten=new CountDownLatch(1),releaseRefusal=new CountDownLatch(1),admissionStarted=new CountDownLatch(1);
        AtomicInteger firstBackend=new AtomicInteger(),secondBackend=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            try {
                var refusal=pool.submit(()->new TransactionTemplate(f.transactions).execute(status->{
                    firstBackend.set(f.jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                    f.store.suspend(UUID.randomUUID(),null,403,T0,null);refusalWritten.countDown();
                    await(releaseRefusal);return true;
                }));
                assertThat(refusalWritten.await(5,TimeUnit.SECONDS)).isTrue();
                var departure=pool.submit(()->new TransactionTemplate(f.transactions).execute(status->{
                    secondBackend.set(f.jdbc.queryForObject("select pg_backend_pid()",Integer.class));admissionStarted.countDown();
                    return f.store.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(2));
                }));
                assertThat(admissionStarted.await(5,TimeUnit.SECONDS)).isTrue();
                boolean blocked=false;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(!blocked && System.nanoTime()<until) {
                    blocked=Boolean.TRUE.equals(f.jdbc.queryForObject("select ?=any(pg_blocking_pids(?))",Boolean.class,firstBackend.get(),secondBackend.get()));
                    if(!blocked) Thread.sleep(10);
                }
                assertThat(blocked).isTrue();releaseRefusal.countDown();
                assertThat(refusal.get(5,TimeUnit.SECONDS)).isTrue();
                assertThat(departure.get(5,TimeUnit.SECONDS).reason()).isEqualTo(DepartureReason.PROVIDER_SUSPENDED);
            } finally {releaseRefusal.countDown();}
        }
        assertThat(f.count("provider_departure_reservation")).isZero();
    }

    @Test
    void concurrentManualRearmsCannotBothConsumeTheSameVersion() throws Exception {
        Fixture f=fixture("50"); Snapshot refusal=f.store.suspend(UUID.randomUUID(),null,403,T0,null);
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->rearmOutcome(f,start,refusal.version()));
            var two=pool.submit(()->rearmOutcome(f,start,refusal.version()));start.countDown();
            assertThat(List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OPEN","PROVIDER_REARM_STALE_VERSION");
        }
        assertThat(f.count("provider_resilience_event")).isEqualTo(2);
    }

    @Test
    void transactionFailureRollsBackBothTheChargeAndTheRefusalAudit() {
        Fixture f=fixture("50");
        assertThatThrownBy(()->new TransactionTemplate(f.transactions).execute(status->{
            f.store.tryReserveDeparture(UUID.randomUUID(),T0);
            f.store.suspend(UUID.randomUUID(),null,403,T0.plusSeconds(1),null);
            throw new IllegalStateException("synthetic rollback");
        })).hasMessage("synthetic rollback");
        assertThat(f.store.snapshot().state()).isEqualTo(State.OPEN);
        assertThat(f.store.snapshot().lastDepartureAt()).isNull();
        assertThat(f.count("provider_departure_reservation")).isZero();
        assertThat(f.count("provider_resilience_event")).isZero();
    }

    @Test
    void clockRegressionAndMissingPersistentStateFailClosed() {
        Fixture f=fixture("50");f.exchange(UUID.randomUUID(),T0);
        assertThat(f.store.tryReserveDeparture(UUID.randomUUID(),T0.minusSeconds(1)).reason()).isEqualTo(DepartureReason.CLOCK_REGRESSION);
        Snapshot refused=f.store.suspend(UUID.randomUUID(),null,403,T0.plusSeconds(2),null);
        assertThatThrownBy(()->f.store.rearm(refused.version(),T0.plusSeconds(1))).hasMessage("PROVIDER_CLOCK_REGRESSION");
        f.jdbc.update("delete from provider_resilience_state where singleton_id=1");
        assertThatThrownBy(()->f.store.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(4))).isInstanceOf(DataAccessException.class);
        assertThat(f.count("provider_departure_reservation")).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(ints={200,404,500})
    void onlyConfirmedRefusalsCanSuspendAccess(int status) {
        Fixture f=fixture("50");
        assertThatThrownBy(()->f.store.suspend(UUID.randomUUID(),null,status,T0,null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(f.count("provider_resilience_event")).isZero();
        assertThat(f.store.snapshot().state()).isEqualTo(State.OPEN);
    }

    @Test
    void persistedEvidenceIsAppendOnlyAndRetryDatesAreBoundedWithoutRawHeaders() {
        Fixture f=fixture("50");
        assertThatThrownBy(()->f.store.suspend(UUID.randomUUID(),null,429,T0,T0.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->f.store.suspend(UUID.randomUUID(),null,429,T0,T0.plus(Duration.ofDays(366)))).isInstanceOf(IllegalArgumentException.class);
        f.exchange(UUID.randomUUID(),T0);
        f.store.suspend(UUID.randomUUID(),null,429,T0,T0.plus(Duration.ofDays(365)));
        for(String table:List.of("provider_resilience_event","provider_departure_reservation","provider_departure_completion",
                "provider_departure_accounting")) {
            assertThatThrownBy(()->f.jdbc.update("delete from "+table)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(()->f.jdbc.execute("truncate "+table)).isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(()->f.jdbc.update("update provider_resilience_event set http_status=403"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("update provider_departure_reservation set reserved_at=reserved_at+interval '1 day'"))
                .isInstanceOf(DataAccessException.class);
        assertThat(f.count("provider_resilience_event")).isEqualTo(1);
        assertThat(f.count("provider_departure_reservation")).isEqualTo(1);
    }

    @Test
    void anUnfinishedDepartureCannotAgeOutAcrossRestartAndItsVerifiedClosureChargesTheWholeDelay() {
        Fixture f=fixture("50"); UUID dispatch=UUID.randomUUID();
        assertThat(f.store.tryReserveDeparture(dispatch,T0).allowed()).isTrue();
        assertThat(f.newStore().tryReserveDeparture(UUID.randomUUID(),T0.plus(Duration.ofDays(1))).reason())
                .isEqualTo(DepartureReason.DEPARTURE_UNRESOLVED);
        assertThat(f.newStore().snapshot().unresolvedDispatchId()).isEqualTo(dispatch);
        Instant closed=T0.plus(Duration.ofDays(1));
        Snapshot completed=f.newStore().markDepartureFinished(dispatch,closed);
        assertThat(completed.unresolvedDispatchId()).isNull();
        assertThat(completed.lastDepartureFinishedAt()).isEqualTo(closed);
        assertThat(f.store.markDepartureFinished(dispatch,closed.plusSeconds(30))).isEqualTo(completed);
        assertThat(f.store.departureDecision(closed.plusSeconds(1)).nextAllowedAt()).isEqualTo(closed.plusSeconds(2));
        assertThat(f.store.tryReserveDeparture(UUID.randomUUID(),closed.plusSeconds(2)).allowed()).isTrue();
        assertThat(f.count("provider_departure_completion")).isEqualTo(1);
    }

    @Test
    void delayedExchangeCompletionKeepsTheMinuteChargeAfterItsOriginalReservationWouldHaveExpired() {
        Fixture f=fixture("50");
        for(int i=0;i<24;i++) assertThat(f.exchange(UUID.randomUUID(),T0.plusSeconds(i*2L)).allowed()).isTrue();
        UUID slow=UUID.randomUUID();assertThat(f.store.tryReserveDeparture(slow,T0.plusSeconds(48)).allowed()).isTrue();
        f.store.markDepartureFinished(slow,T0.plusSeconds(58));
        assertThat(f.store.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(59)).reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(f.exchange(UUID.randomUUID(),T0.plusSeconds(60)).allowed()).isTrue();
        // Charge the next 23 requests at 2-second intervals; the slow request expires at 118 s, not 108 s.
        for(int i=1;i<24;i++) assertThat(f.exchange(UUID.randomUUID(),T0.plusSeconds(60+i*2L)).allowed()).isTrue();
        assertThat(f.store.departureDecision(T0.plusSeconds(110)).nextAllowedAt()).isEqualTo(T0.plusSeconds(118));
    }

    @Test
    void v48AndV50UpgradeExistingV47DepartureHistoryAsLegacyAndBackfillConservativeAccounting() {
        Fixture f=fixture("47"); UUID historical=UUID.randomUUID();
        f.jdbc.update("insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version) values (?,?,?)",
                historical,Timestamp.from(T0),ProviderResilienceData.POLICY_VERSION);
        f.jdbc.update("insert into provider_departure_completion(dispatch_id,finished_at) values (?,?)",historical,Timestamp.from(T0));
        f.jdbc.update("update provider_resilience_state set last_departure_at=?,last_departure_finished_at=? where singleton_id=1",
                Timestamp.from(T0),Timestamp.from(T0));
        Map<String,Object> before=f.jdbc.queryForMap("select dispatch_id,reserved_at,policy_version from provider_departure_reservation where dispatch_id=?",historical);

        assertThat(f.migrate("48")).isOne();
        assertThat(f.jdbc.queryForMap("select dispatch_id,reserved_at,policy_version from provider_departure_reservation where dispatch_id=?",historical))
                .isEqualTo(before);
        assertThat(f.jdbc.queryForObject("select admission_profile from provider_departure_reservation where dispatch_id=?",String.class,historical))
                .isEqualTo(DepartureProfile.LEGACY_V1.persistenceValue());
        assertThat(f.jdbc.queryForObject("select last_departure_admission_profile from provider_resilience_state where singleton_id=1",String.class))
                .isNull();
        assertThat(f.migrate("50")).isEqualTo(2);
        assertThat(f.jdbc.queryForMap("select dispatch_id,departure_at,source from provider_departure_accounting where dispatch_id=?",historical))
                .containsEntry("dispatch_id",historical).containsEntry("departure_at",Timestamp.from(T0))
                .containsEntry("source","COMPLETION_FALLBACK");
        assertThat(f.newStore().departureDecision(DepartureProfile.LIVE_V8,T0.plusMillis(500)).nextAllowedAt())
                .isEqualTo(T0.plusSeconds(2));

        UUID v8=UUID.randomUUID();
        assertThat(f.newStore().tryReserveDeparture(v8,DepartureProfile.LIVE_V8,T0.plusSeconds(2)).allowed()).isTrue();
        f.newStore().markDepartureFinished(v8,T0.plusSeconds(2));
        assertThat(f.jdbc.queryForObject("select admission_profile from provider_departure_reservation where dispatch_id=?",String.class,v8))
                .isEqualTo(DepartureProfile.LIVE_V8.persistenceValue());
        assertThat(f.jdbc.queryForObject("select last_departure_admission_profile from provider_resilience_state where singleton_id=1",String.class))
                .isEqualTo(DepartureProfile.LIVE_V8.persistenceValue());
        assertThatThrownBy(()->f.jdbc.update("update provider_departure_reservation set admission_profile='legacy-v1' where dispatch_id=?",v8))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void v8ChargesThe46thDepartureFromAuthenticatedRequestedAtEvenWhenCompletionIsLater() {
        Fixture f=fixture("50"); Instant requested=T0.plusSeconds(10),finished=T0.plusSeconds(20),attempted=T0.plusMillis(20_500);
        f.jdbc.update("""
            insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version,admission_profile)
                select gen_random_uuid(),?,'provider-resilience-v1','live-v8' from generate_series(1,45)
            """,Timestamp.from(T0));
        f.jdbc.update("""
            insert into provider_departure_accounting(dispatch_id,departure_at,source)
                select dispatch_id,?,'AUTHENTICATED_WORKER_REQUEST' from provider_departure_reservation
            """,Timestamp.from(requested));
        f.jdbc.update("""
            insert into provider_departure_completion(dispatch_id,finished_at)
                select dispatch_id,? from provider_departure_reservation
            """,Timestamp.from(finished));
        f.jdbc.update("""
            update provider_resilience_state set last_departure_at=?,last_departure_finished_at=?,
                last_departure_admission_profile='live-v8' where singleton_id=1
            """,Timestamp.from(T0),Timestamp.from(finished));

        DepartureDecision denied=f.store.tryReserveDeparture(UUID.randomUUID(),DepartureProfile.LIVE_V8,attempted);

        assertThat(denied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        // The 45 durable charges expire from worker REQUEST_SENT (T0+10), not
        // from the deliberately later completion acknowledgement (T0+20).
        assertThat(denied.nextAllowedAt()).isEqualTo(requested.plusSeconds(60));
        assertThat(f.count("provider_departure_accounting")).isEqualTo(45);
        assertThat(f.count("provider_departure_completion")).isEqualTo(45);
    }

    @Test
    void v8RequestEvidenceFailsClosedWithoutReleasingTheUnresolvedReservationAndIsIdempotentWhenValid() {
        Fixture f=fixture("50"); UUID dispatch=UUID.randomUUID();
        assertThat(f.store.tryReserveDeparture(dispatch,DepartureProfile.LIVE_V8,T0).allowed()).isTrue();

        assertThatThrownBy(()->f.store.recordAuthenticatedV8Departure(dispatch,T0.minusNanos(1),T0))
                .hasMessage("PROVIDER_REQUESTED_BEFORE_RESERVATION");
        assertThatThrownBy(()->f.store.recordAuthenticatedV8Departure(dispatch,T0.plusSeconds(1),T0.plusMillis(999)))
                .hasMessage("PROVIDER_REQUESTED_TIMESTAMP_INCOHERENT");
        assertThatThrownBy(()->f.store.recordAuthenticatedV8Departure(dispatch,Instant.MAX,Instant.MAX))
                .hasMessage("PROVIDER_REQUESTED_TIMESTAMP_INVALID");
        assertThat(f.store.snapshot().unresolvedDispatchId()).isEqualTo(dispatch);
        assertThat(f.count("provider_departure_accounting")).isZero();

        Instant requested=T0.plusMillis(250),observed=T0.plusMillis(500);
        f.store.recordAuthenticatedV8Departure(dispatch,requested,observed);
        f.store.recordAuthenticatedV8Departure(dispatch,requested,observed);
        assertThatThrownBy(()->f.store.recordAuthenticatedV8Departure(dispatch,requested.plusMillis(1),observed.plusMillis(1)))
                .hasMessage("PROVIDER_REQUESTED_TIMESTAMP_CONFLICT");
        assertThat(f.count("provider_departure_accounting")).isOne();
        assertThat(f.jdbc.queryForMap("select departure_at,source from provider_departure_accounting where dispatch_id=?",dispatch))
                .containsEntry("departure_at",Timestamp.from(requested)).containsEntry("source","AUTHENTICATED_WORKER_REQUEST");
        assertThat(f.store.snapshot().unresolvedDispatchId()).isEqualTo(dispatch);

        f.store.markDepartureFinished(dispatch,T0.plusSeconds(1));
        assertThat(f.store.snapshot().unresolvedDispatchId()).isNull();
        assertThat(f.jdbc.queryForObject("select source from provider_departure_accounting where dispatch_id=?",String.class,dispatch))
                .isEqualTo("AUTHENTICATED_WORKER_REQUEST");
    }

    @Test
    void v8UsesTheConservativeCompletionFallbackWhenNoRequestSentEvidenceArrives() {
        Fixture f=fixture("50"); UUID dispatch=UUID.randomUUID(); Instant finished=T0.plusSeconds(30);
        assertThat(f.store.tryReserveDeparture(dispatch,DepartureProfile.LIVE_V8,T0).allowed()).isTrue();
        f.store.markDepartureFinished(dispatch,finished);

        assertThat(f.jdbc.queryForMap("select departure_at,source from provider_departure_accounting where dispatch_id=?",dispatch))
                .containsEntry("departure_at",Timestamp.from(finished)).containsEntry("source","COMPLETION_FALLBACK");
        DepartureDecision fence = f.store.departureDecision(DepartureProfile.LIVE_V8, finished.plusMillis(499));
        assertThat(fence.reason()).isEqualTo(DepartureReason.POST_EXCHANGE_FENCE);
        assertThat(fence.nextAllowedAt()).isEqualTo(finished.plusMillis(500));
    }

    @Test
    void v8UsesItsHalfSecondFenceButCrossPolicyTransitionsKeepTheLongerLegacyFence() {
        Fixture f=fixture("50");
        UUID legacy=UUID.randomUUID();
        assertThat(f.exchange(legacy,DepartureProfile.LEGACY_V1,T0).allowed()).isTrue();
        assertThat(f.store.departureDecision(DepartureProfile.LIVE_V8,T0.plusMillis(500)).nextAllowedAt())
                .isEqualTo(T0.plusSeconds(2));

        UUID v8=UUID.randomUUID();
        assertThat(f.exchange(v8,DepartureProfile.LIVE_V8,T0.plusSeconds(2)).allowed()).isTrue();
        DepartureDecision v8Fence = f.store.departureDecision(DepartureProfile.LIVE_V8,T0.plusSeconds(2).plusMillis(499));
        assertThat(v8Fence.reason()).isEqualTo(DepartureReason.POST_EXCHANGE_FENCE);
        assertThat(v8Fence.nextAllowedAt()).isEqualTo(T0.plusSeconds(2).plusMillis(500));
        assertThat(f.store.departureDecision(DepartureProfile.LIVE_V8,T0.plusSeconds(2).plusMillis(500)).allowed()).isTrue();
        assertThat(f.store.departureDecision(DepartureProfile.LEGACY_V1,T0.plusSeconds(2).plusMillis(500)).nextAllowedAt())
                .isEqualTo(T0.plusSeconds(4));
    }

    @Test
    void v8MinuteAndHourlyBudgetsAreDurableAndAllProfilesChargeTheSameWindows() {
        Fixture minute=fixture("50");
        for(int i=0;i<45;i++) assertThat(minute.exchange(UUID.randomUUID(),DepartureProfile.LIVE_V8,T0.plusMillis(i*500L)).allowed()).isTrue();
        DepartureDecision minuteDenied=minute.store.departureDecision(DepartureProfile.LIVE_V8,T0.plusMillis(22500));
        assertThat(minuteDenied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(minuteDenied.nextAllowedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(minute.store.tryReserveDeparture(UUID.randomUUID(),DepartureProfile.LIVE_V8,T0.plusSeconds(60)).allowed()).isTrue();

        Fixture mixed=fixture("50");
        for(int i=0;i<25;i++) assertThat(mixed.exchange(UUID.randomUUID(),DepartureProfile.LIVE_V8,T0.plusMillis(i*500L)).allowed()).isTrue();
        DepartureDecision legacyDenied=mixed.store.departureDecision(DepartureProfile.LEGACY_V1,T0.plusSeconds(14));
        assertThat(legacyDenied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(legacyDenied.nextAllowedAt()).isEqualTo(T0.plusSeconds(60));

        Fixture hour=fixture("50"); Instant now=T0.plusSeconds(3600),first=T0.plusSeconds(784),last=T0.plusSeconds(3539);
        hour.jdbc.update("""
            insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version,admission_profile)
                select gen_random_uuid(),?::timestamptz+n*interval '1 second','provider-resilience-v1','live-v8'
                    from generate_series(0,2755) n
            """,Timestamp.from(first));
        hour.jdbc.update("insert into provider_departure_completion(dispatch_id,finished_at) select dispatch_id,reserved_at from provider_departure_reservation");
        hour.jdbc.update("""
            update provider_resilience_state set last_departure_at=?,last_departure_finished_at=?,
                last_departure_admission_profile='live-v8' where singleton_id=1
            """,Timestamp.from(last),Timestamp.from(last));
        DepartureDecision hourDenied=hour.newStore().departureDecision(DepartureProfile.LIVE_V8,now);
        assertThat(hourDenied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(hourDenied.nextAllowedAt()).isEqualTo(first.plus(Duration.ofHours(1)));
        assertThat(hour.newStore().tryReserveDeparture(UUID.randomUUID(),DepartureProfile.LIVE_V8,first.plus(Duration.ofHours(1))).allowed()).isTrue();
    }

    @Test
    void v8InitialTenMatchWaveNeedsFortyFreeSharedDurableSlotsBeforeLaunch() {
        Fixture minute=fixture("50"); Instant now=T0.plusSeconds(60);
        // Five retained completions leave precisely 40 slots in the V8 minute
        // window. The sixth makes a 10 x 4 initial wave impossible until the
        // oldest durable departure expires.
        for(int i=0;i<5;i++) assertThat(minute.exchange(UUID.randomUUID(),DepartureProfile.LIVE_V8,
                now.minusSeconds(10).plusMillis(i*500L)).allowed()).isTrue();
        assertThat(minute.store.departureCapacityDecision(DepartureProfile.LIVE_V8,40,now))
                .extracting(DepartureDecision::allowed,DepartureDecision::reason)
                .containsExactly(true,DepartureReason.ALLOWED);
        assertThat(minute.exchange(UUID.randomUUID(),DepartureProfile.LIVE_V8,
                now.minusSeconds(10).plusMillis(2500)).allowed()).isTrue();
        DepartureDecision minuteDenied=minute.store.departureCapacityDecision(DepartureProfile.LIVE_V8,40,now);
        assertThat(minuteDenied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(minuteDenied.nextAllowedAt()).isEqualTo(now.plusSeconds(50));

        Fixture hour=fixture("50"); Instant hourNow=T0.plus(Duration.ofHours(1)),hourDeparture=hourNow.minus(Duration.ofMinutes(5));
        insertClosedV8Departures(hour,2_716,hourDeparture);
        assertThat(hour.store.departureCapacityDecision(DepartureProfile.LIVE_V8,40,hourNow).allowed()).isTrue();
        insertClosedV8Departures(hour,1,hourDeparture);
        DepartureDecision hourDenied=hour.newStore().departureCapacityDecision(DepartureProfile.LIVE_V8,40,hourNow);
        assertThat(hourDenied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(hourDenied.nextAllowedAt()).isEqualTo(hourDeparture.plus(Duration.ofHours(1)));
    }

    @Test
    void v8HourlyBoundaryAdmitsThe2756thDepartureRejectsThe2757thAndReopensOnlyAtTheExactExpiry() {
        Fixture f=fixture("50");
        Instant first=T0,admittedAt=T0.plusSeconds(3_599),expiry=first.plus(Duration.ofHours(1));
        // Seed already-completed V8 pressure outside the final minute so this test isolates
        // the durable rolling-hour boundary; the minute fence is proven independently above.
        f.jdbc.update("""
            insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version,admission_profile)
                select gen_random_uuid(),?,'provider-resilience-v1','live-v8'
                    from generate_series(1,2755)
            """,Timestamp.from(first));
        f.jdbc.update("insert into provider_departure_completion(dispatch_id,finished_at) select dispatch_id,reserved_at from provider_departure_reservation");
        f.jdbc.update("""
            update provider_resilience_state set last_departure_at=?,last_departure_finished_at=?,
                last_departure_admission_profile='live-v8' where singleton_id=1
            """,Timestamp.from(first),Timestamp.from(first));
        assertThat(f.count("provider_departure_completion")).isEqualTo(2_755);

        UUID departure2756=UUID.randomUUID();
        assertThat(f.store.tryReserveDeparture(departure2756,DepartureProfile.LIVE_V8,admittedAt))
                .extracting(DepartureDecision::allowed,DepartureDecision::reason)
                .containsExactly(true,DepartureReason.ALLOWED);
        f.store.markDepartureFinished(departure2756,admittedAt);
        assertThat(f.count("provider_departure_completion")).isEqualTo(2_756);

        DepartureDecision denied=f.newStore().tryReserveDeparture(UUID.randomUUID(),DepartureProfile.LIVE_V8,admittedAt.plusMillis(500));
        assertThat(denied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(denied.nextAllowedAt()).isEqualTo(expiry);
        assertThat(f.count("provider_departure_completion")).isEqualTo(2_756);
        DepartureDecision stillDenied=f.newStore().departureDecision(DepartureProfile.LIVE_V8,expiry.minusNanos(1));
        assertThat(stillDenied.reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(stillDenied.nextAllowedAt()).isEqualTo(expiry);
        assertThat(f.newStore().tryReserveDeparture(UUID.randomUUID(),DepartureProfile.LIVE_V8,expiry))
                .extracting(DepartureDecision::allowed,DepartureDecision::reason)
                .containsExactly(true,DepartureReason.ALLOWED);
    }

    @Test
    void v8RefusalStillSuspendsLegacyAndV8AfterRestart() {
        Fixture f=fixture("50"); UUID departure=UUID.randomUUID();
        assertThat(f.exchange(departure,DepartureProfile.LIVE_V8,T0).allowed()).isTrue();
        Snapshot suspension=f.store.suspend(UUID.randomUUID(),null,429,T0.plusSeconds(1),T0.plus(Duration.ofMinutes(1)));
        ProviderResilienceStore restarted=f.newStore();
        assertThat(restarted.tryReserveDeparture(UUID.randomUUID(),DepartureProfile.LIVE_V8,T0.plus(Duration.ofMinutes(2))).reason())
                .isEqualTo(DepartureReason.PROVIDER_SUSPENDED);
        assertThat(restarted.tryReserveDeparture(UUID.randomUUID(),DepartureProfile.LEGACY_V1,T0.plus(Duration.ofMinutes(2))).reason())
                .isEqualTo(DepartureReason.PROVIDER_SUSPENDED);
        assertThat(restarted.snapshot()).isEqualTo(suspension);
    }

    private static String rearmOutcome(Fixture f,CountDownLatch start,long version) throws InterruptedException {
        start.await();
        try {return f.newStore().rearm(version,T0.plusSeconds(1)).state().name();}
        catch(IllegalStateException rejected) {return rejected.getMessage();}
    }
    private static void insertClosedV8Departures(Fixture fixture,int count,Instant departureAt) {
        fixture.jdbc.update("""
            insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version,admission_profile)
                select gen_random_uuid(),?,'provider-resilience-v1','live-v8'
                    from generate_series(1,?)
            """,Timestamp.from(departureAt),count);
        fixture.jdbc.update("""
            insert into provider_departure_completion(dispatch_id,finished_at)
                select reservation.dispatch_id,reservation.reserved_at
                    from provider_departure_reservation reservation
                    where not exists (select 1 from provider_departure_completion completion
                        where completion.dispatch_id=reservation.dispatch_id)
            """);
        fixture.jdbc.update("""
            update provider_resilience_state set last_departure_at=?,last_departure_finished_at=?,
                last_departure_admission_profile='live-v8' where singleton_id=1
            """,Timestamp.from(departureAt),Timestamp.from(departureAt));
    }
    private static void await(CountDownLatch latch) {
        try {if(!latch.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("test lock timeout");}
        catch(InterruptedException failure) {Thread.currentThread().interrupt();throw new IllegalStateException(failure);}
    }
    private static Fixture fixture(String target) {
        String database="resilience_it_"+DATABASE.incrementAndGet();
        DriverManagerDataSource admin=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        new JdbcTemplate(admin).execute("create database "+database);
        String url=POSTGRES.getJdbcUrl().substring(0,POSTGRES.getJdbcUrl().lastIndexOf('/')+1)+database;
        Fixture fixture=new Fixture(new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword()));
        fixture.migrate(target);return fixture;
    }
    private static <T>T transactional(T object,Class<T> type,JdbcTransactionManager transactions) {
        ProxyFactory proxy=new ProxyFactory(object);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }
    private static final class Fixture {
        final DriverManagerDataSource ds;final JdbcTemplate jdbc;final JdbcTransactionManager transactions;final ProviderResilienceStore store;
        Fixture(DriverManagerDataSource ds) {
            this.ds=ds;jdbc=new JdbcTemplate(ds);transactions=new JdbcTransactionManager(ds);store=newStore();
        }
        ProviderResilienceStore newStore() {return transactional(new JdbcProviderResilienceStore(jdbc),ProviderResilienceStore.class,transactions);}
        DepartureDecision exchange(UUID dispatch,Instant at) {
            DepartureDecision decision=store.tryReserveDeparture(dispatch,at);
            if(decision.allowed()) store.markDepartureFinished(dispatch,at);
            return decision;
        }
        DepartureDecision exchange(UUID dispatch,DepartureProfile profile,Instant at) {
            DepartureDecision decision=store.tryReserveDeparture(dispatch,profile,at);
            if(decision.allowed()) store.markDepartureFinished(dispatch,at);
            return decision;
        }
        int migrate(String version) {return Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version)).load().migrate().migrationsExecuted;}
        long count(String table) {return jdbc.queryForObject("select count(*) from "+table,Long.class);}
        List<Map<String,Object>> rows(String table) {return jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text");}
    }
}
