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
    void freshInstallAndUpgradeOfPopulatedV41LeaveOldEvidenceAndProviderGuardUntouched() {
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
        assertThat(f.store.snapshot().state()).isEqualTo(State.OPEN);
        assertThat(f.store.snapshot().version()).isZero();
        assertThat(f.store.snapshot().evidenceId()).isNull();
        assertThat(f.count("provider_resilience_event")).isZero();
        assertThat(f.count("provider_departure_reservation")).isZero();
        assertThat(f.migrate("42")).isZero();
        Fixture fresh=fixture("42");
        assertThat(fresh.store.departureDecision(T0).allowed()).isTrue();
    }

    @Test
    void refusalSurvivesARepositoryRestartAndANewCampaignUntilExplicitVersionedRearm() {
        Fixture f=fixture("42"); UUID evidence=UUID.randomUUID(),manualCampaign=UUID.randomUUID();
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
        Fixture f=fixture("42"); Instant retry=T0.plusSeconds(3600);
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
        Fixture f=fixture("42"); UUID evidence=UUID.randomUUID();
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
        Fixture f=fixture("42"); UUID evidence=UUID.randomUUID();
        Snapshot refused=f.store.suspend(evidence,null,403,T0,null);
        Snapshot rearmed=f.store.rearm(refused.version(),T0.plusSeconds(1));
        assertThat(f.store.suspend(evidence,null,403,T0,null)).isEqualTo(rearmed);
        assertThat(f.count("provider_resilience_event")).isEqualTo(2);
    }

    @Test
    void reservationIsSingleUseAndReadOnlyPreviewNeitherChargesNorGrantsADispatch() {
        Fixture f=fixture("42"); UUID dispatch=UUID.randomUUID();
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
        Fixture f=fixture("42");
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
        Fixture f=fixture("42");
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
        Fixture f=fixture("42"); var start=new CountDownLatch(1);
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
        Fixture f=fixture("42"); CountDownLatch refusalWritten=new CountDownLatch(1),releaseRefusal=new CountDownLatch(1),admissionStarted=new CountDownLatch(1);
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
        Fixture f=fixture("42"); Snapshot refusal=f.store.suspend(UUID.randomUUID(),null,403,T0,null);
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
        Fixture f=fixture("42");
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
        Fixture f=fixture("42");f.exchange(UUID.randomUUID(),T0);
        assertThat(f.store.tryReserveDeparture(UUID.randomUUID(),T0.minusSeconds(1)).reason()).isEqualTo(DepartureReason.CLOCK_REGRESSION);
        Snapshot refused=f.store.suspend(UUID.randomUUID(),null,403,T0.plusSeconds(2),null);
        assertThatThrownBy(()->f.store.rearm(refused.version(),T0.plusSeconds(1))).hasMessage("PROVIDER_CLOCK_REGRESSION");
        f.jdbc.update("delete from provider_resilience_state where singleton_id=1");
        assertThatThrownBy(()->f.store.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(4))).isInstanceOf(DataAccessException.class);
        assertThat(f.count("provider_departure_reservation")).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(ints={200,404,500})
    void onlyConfirmedRefusalsCanSuspendAccess(int status) {
        Fixture f=fixture("42");
        assertThatThrownBy(()->f.store.suspend(UUID.randomUUID(),null,status,T0,null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(f.count("provider_resilience_event")).isZero();
        assertThat(f.store.snapshot().state()).isEqualTo(State.OPEN);
    }

    @Test
    void persistedEvidenceIsAppendOnlyAndRetryDatesAreBoundedWithoutRawHeaders() {
        Fixture f=fixture("42");
        assertThatThrownBy(()->f.store.suspend(UUID.randomUUID(),null,429,T0,T0.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->f.store.suspend(UUID.randomUUID(),null,429,T0,T0.plus(Duration.ofDays(366)))).isInstanceOf(IllegalArgumentException.class);
        f.exchange(UUID.randomUUID(),T0);
        f.store.suspend(UUID.randomUUID(),null,429,T0,T0.plus(Duration.ofDays(365)));
        for(String table:List.of("provider_resilience_event","provider_departure_reservation","provider_departure_completion")) {
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
        Fixture f=fixture("42"); UUID dispatch=UUID.randomUUID();
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
        Fixture f=fixture("42");
        for(int i=0;i<24;i++) assertThat(f.exchange(UUID.randomUUID(),T0.plusSeconds(i*2L)).allowed()).isTrue();
        UUID slow=UUID.randomUUID();assertThat(f.store.tryReserveDeparture(slow,T0.plusSeconds(48)).allowed()).isTrue();
        f.store.markDepartureFinished(slow,T0.plusSeconds(58));
        assertThat(f.store.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(59)).reason()).isEqualTo(DepartureReason.RATE_LIMITED);
        assertThat(f.exchange(UUID.randomUUID(),T0.plusSeconds(60)).allowed()).isTrue();
        // Charge the next 23 requests at 2-second intervals; the slow request expires at 118 s, not 108 s.
        for(int i=1;i<24;i++) assertThat(f.exchange(UUID.randomUUID(),T0.plusSeconds(60+i*2L)).allowed()).isTrue();
        assertThat(f.store.departureDecision(T0.plusSeconds(110)).nextAllowedAt()).isEqualTo(T0.plusSeconds(118));
    }

    private static String rearmOutcome(Fixture f,CountDownLatch start,long version) throws InterruptedException {
        start.await();
        try {return f.newStore().rearm(version,T0.plusSeconds(1)).state().name();}
        catch(IllegalStateException rejected) {return rejected.getMessage();}
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
        int migrate(String version) {return Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version)).load().migrate().migrationsExecuted;}
        long count(String table) {return jdbc.queryForObject("select count(*) from "+table,Long.class);}
        List<Map<String,Object>> rows(String table) {return jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text");}
    }
}
