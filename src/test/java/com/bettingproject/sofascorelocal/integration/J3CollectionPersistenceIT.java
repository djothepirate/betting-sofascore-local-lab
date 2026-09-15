package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.*;
import com.bettingproject.sofascorelocal.application.network.*;
import com.bettingproject.sofascorelocal.domain.benchmark.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.*;
import com.bettingproject.sofascorelocal.port.*;
import org.flywaydb.core.Flyway;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Actual PostgreSQL, isolated databases, synthetic payloads; no provider transport. */
@Testcontainers
class J3CollectionPersistenceIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("j3_admin").withUsername("sofascore_lab").withPassword("integration-test-only");
    static final AtomicInteger DATABASE=new AtomicInteger();
    static final Instant T0=Instant.parse("2026-09-13T08:00:00Z");
    static final LocalDate DATE=LocalDate.parse("2026-09-13");

    @Test void migrationPreservesPopulatedV54AndReappliesWithoutChange() {
        var f=fixture("54"); var page=f.page(DATE,1,false,1,T0);
        var before=f.jdbc.queryForList("select to_jsonb(s)::text from provider_snapshot s");
        var occurrences=f.jdbc.queryForList("select to_jsonb(o)::text from provider_snapshot_occurrence o");
        assertThat(f.migrate("55")).isEqualTo(1);
        assertThat(f.jdbc.queryForList("select to_jsonb(s)::text from provider_snapshot s")).isEqualTo(before);
        assertThat(f.jdbc.queryForList("select to_jsonb(o)::text from provider_snapshot_occurrence o")).isEqualTo(occurrences);
        assertThat(f.store.hasSuccess(DATE)).isFalse();
        assertThat(f.migrate("55")).isZero();
        assertThat(f.publish(DATE,List.of(page),T0).catalog().options()).hasSize(1);
    }

    @Test void lastSuccessfulCatalogueSurvivesServiceRestartAndCollectingAnotherDate() {
        var f=fixture("55"); var first=f.publish(DATE,List.of(f.page(DATE,1,false,1,T0)),T0);
        var other=f.publish(DATE.plusDays(1),List.of(f.page(DATE.plusDays(1),1,false,2,T0.plusSeconds(2))),T0.plusSeconds(2));
        var restarted=new Fixture(f.ds);
        assertThat(restarted.store.latest(DATE)).contains(first);
        assertThat(restarted.store.latest(DATE.plusDays(1))).contains(other);
        var refreshed=f.publish(DATE,List.of(f.page(DATE,1,false,3,T0.plusSeconds(4))),T0.plusSeconds(4));
        assertThat(restarted.store.latest(DATE)).contains(refreshed);
        assertThat(restarted.store.find(first.id())).contains(first);
        assertThat(restarted.store.page(first.id(),DATE,1,25)).get().satisfies(p->{
            assertThat(p.total()).isEqualTo(1);assertThat(p.pageCount()).isEqualTo(1);
            assertThat(p.entries()).extracting(Entry::tournamentId).containsExactly(1L);
        });
        assertThat(restarted.store.page(first.id(),DATE.plusDays(1),1,25)).isEmpty();
        assertThatThrownBy(()->restarted.store.page(first.id(),DATE,2,25)).hasMessage("J3_PAGE_OUT_OF_RANGE");
        assertThatThrownBy(()->restarted.store.page(first.id(),DATE,1,7)).hasMessage("J3_PAGINATION_INVALID");
        assertThat(restarted.store.dates(100)).extracting(DateSummary::date).containsExactly(DATE.plusDays(1),DATE);
    }

    @Test void failedAndRolledBackPublicationCannotEraseEarlierSuccess() {
        var f=fixture("55"); var first=f.publish(DATE,List.of(f.page(DATE,1,false,1,T0)),T0);
        UUID failed=UUID.randomUUID(); f.store.begin(failed,DATE,Trigger.MANUAL_PROVIDER,T0.plusSeconds(3));
        f.store.publish(new Proof(failed,DATE,Trigger.MANUAL_PROVIDER,T0.plusSeconds(3),T0.plusSeconds(4),
                State.FAILED,"HTTP_FORBIDDEN",List.of()),List.of());
        var page=f.page(DATE,1,false,2,T0.plusSeconds(6)); UUID id=UUID.randomUUID();
        f.store.begin(id,DATE,Trigger.MANUAL_PROVIDER,T0.plusSeconds(6));
        var proof=new Proof(id,DATE,Trigger.MANUAL_PROVIDER,T0.plusSeconds(6),T0.plusSeconds(8),State.COMPLETED,"NONE",List.of(page));
        assertThatThrownBy(()->new TransactionTemplate(new JdbcTransactionManager(f.ds)).execute(status->{
            f.store.publish(proof,f.projector.project(DATE,List.of(page)).entries());
            throw new IllegalStateException("simulated audit commit failure");
        })).hasMessage("simulated audit commit failure");
        assertThat(f.store.latest(DATE)).contains(first);
        assertThat(f.store.find(id)).isEmpty();
        assertThat(f.jdbc.queryForObject("select count(*) from j3_collection_page where run_id=?",Long.class,id)).isZero();
        assertThat(f.store.hasSuccess(DATE)).isTrue();
    }

    @Test void exactDeduplicatedOccurrenceIsRequiredAndTerminalPublicationIsIdempotent() {
        var f=fixture("55"); var original=f.page(DATE,1,false,1,T0);
        var duplicate=f.page(DATE,1,false,1,T0.plusSeconds(5));
        assertThat(duplicate.snapshotId()).isEqualTo(original.snapshotId());
        assertThat(duplicate.occurrenceId()).isNotEqualTo(original.occurrenceId());
        assertThat(duplicate.persistenceOutcome()).isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(f.projector.project(DATE,List.of(duplicate)).catalog().available()).isTrue();
        assertThat(f.projector.project(DATE,List.of(duplicate.withOccurrence(original.occurrenceId()))).catalog().available()).isFalse();
        var saved=f.publish(DATE,List.of(duplicate),T0.plusSeconds(5));
        f.store.publish(saved.proof(),saved.entries());
        assertThat(f.jdbc.queryForObject("select revision from j3_last_success where collection_date=?",Long.class,DATE)).isEqualTo(1);
        assertThatThrownBy(()->f.jdbc.update("delete from j3_collection_run where run_id=?",saved.id())).hasMessageContaining("immutable");
        assertThatThrownBy(()->f.jdbc.update("update j3_collection_page set evidence=evidence where run_id=?",saved.id())).hasMessageContaining("append-only");
    }

    @Test void lastSuccessRawSourcesAreProtectedFromRetention() {
        var f=fixture("55"); var page=f.page(DATE,1,false,1,T0); f.publish(DATE,List.of(page),T0);
        assertThatThrownBy(()->f.jdbc.update("update provider_snapshot set payload_raw=null where id=?",page.snapshotId()))
                .hasMessageContaining("last-success raw evidence is protected");
        assertThat(f.snapshots.findById(page.snapshotId())).isPresent();
    }

    @Test void retentionWaitsForConcurrentJ3PublicationAndPreservesItsSources() throws Exception {
        var f=fixture("57");var page=f.page(DATE,1,false,1,T0);
        var retention=proxy(new JdbcJ6RawPayloadRetentionStore(new NamedParameterJdbcTemplate(f.ds)),
                J6RawPayloadRetentionStore.class,new JdbcTransactionManager(f.ds));
        Instant now=T0.plus(Duration.ofDays(40)),cutoff=now.minus(Duration.ofDays(30));
        var preview=retention.preview(30,now,cutoff,500);
        // The existing J6 scope requires normalized event references. A tournament page alone
        // is not eligible even before publication; still prove that J6 waits for J3's commit.
        assertThat(preview.candidates()).isEmpty();
        var published=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var publisher=pool.submit(()->new TransactionTemplate(new JdbcTransactionManager(f.ds)).execute(status->{
                var result=f.publish(DATE,List.of(page),T0);
                published.countDown();
                try { if(!release.await(8,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                return result;
            }));
            try {
                assertThat(published.await(8,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var purge=pool.submit(()->retention.purge(30,cutoff,500,preview.planSha256(),
                        new com.bettingproject.sofascorelocal.domain.retention.J6BackupEvidence(
                            "a".repeat(64),"b".repeat(64),now.minusSeconds(1),page.snapshotId(),now,true),
                        UUID.randomUUID(),now));
                long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                boolean waiting=false;
                while(!waiting && System.nanoTime()<until) {
                    waiting=Boolean.TRUE.equals(f.jdbc.queryForObject("""
                        select exists(select 1 from pg_stat_activity where datname=current_database()
                            and wait_event_type='Lock' and wait_event='advisory'
                            and query like '%pg_advisory_xact_lock(-6060)%')
                        """,Boolean.class));
                    if(!waiting) Thread.sleep(20);
                }
                assertThat(waiting).isTrue();assertThat(purge).isNotDone();
                release.countDown();
                var saved=publisher.get(8,java.util.concurrent.TimeUnit.SECONDS);
                assertThatThrownBy(()->purge.get(8,java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .cause().isInstanceOfAny(com.bettingproject.sofascorelocal.application.retention.J6RetentionException.class,
                            org.springframework.dao.TransientDataAccessException.class);
                assertThat(f.store.latest(DATE)).contains(saved);
                assertThat(f.jdbc.queryForObject("select payload_raw is not null from provider_snapshot where id=?",
                        Boolean.class,page.snapshotId())).isTrue();
                assertThat(f.jdbc.queryForObject("select count(*) from j6_raw_payload_purge_audit",Long.class)).isZero();
            } finally {release.countDown();}
        }
    }

    @Test void legacyCacheRecoveryPreservesMissingCacheTimeAndIsRepeatable() {
        var f=fixture("54"); var page=f.page(DATE,1,false,1,T0);
        UUID id=f.legacyCacheSuccess(page);
        f.migrate("55");
        var recovery=new J3LegacyRecoveryService(f.store,f.snapshots,f.projector);
        assertThat(recovery.recover()).isEqualTo(1);
        assertThat(recovery.recover()).isZero();
        var recovered=f.store.latest(DATE).orElseThrow();
        assertThat(recovered.id()).isEqualTo(id);
        assertThat(recovered.proof().trigger()).isEqualTo(Trigger.LEGACY);
        assertThat(recovered.proof().pages()).singleElement().satisfies(p->{
            assertThat(p.resolutionSource()).isEqualTo(J3PageResolutionSource.CACHE);
            assertThat(p.cacheStoredAt()).isNull();
            assertThat(p.historicalCacheTimestampAbsent()).isTrue();
            assertThat(p.occurrenceId()).isNull();
        });
        assertThat(recovered.catalog().options()).hasSize(1);
    }

    @Test void historicalSuccessWithUnavailableBytesStillPreventsAutomaticDuplicate() {
        var f=fixture("54"); var page=f.page(DATE,1,false,1,T0); f.legacyCacheSuccess(page);
        // Deliberately damaged historical backup, confined to this disposable test database.
        f.jdbc.execute("alter table provider_snapshot disable trigger provider_snapshot_j6_guard");
        f.jdbc.update("update provider_snapshot set payload_raw=null,payload_purged_at=current_timestamp where id=?",page.snapshotId());
        f.jdbc.execute("alter table provider_snapshot enable trigger provider_snapshot_j6_guard");
        f.migrate("55");
        assertThat(new J3LegacyRecoveryService(f.store,f.snapshots,f.projector).recover()).isEqualTo(1);
        assertThat(f.store.hasSuccess(DATE)).isTrue();
        assertThat(f.store.latest(DATE)).isEmpty();
        assertThat(f.store.dates(100)).singleElement().satisfies(d->
                assertThat(d.recoveryStatus()).isEqualTo("SUCCESS_PROVEN_CONTENT_UNAVAILABLE"));
    }

    static Fixture fixture(String version) {
        return fixture(POSTGRES,version);
    }
    static Fixture fixture(PostgreSQLContainer postgres,String version) {
        var admin=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        String name="j3_it_"+DATABASE.incrementAndGet();new JdbcTemplate(admin).execute("create database "+name);
        String url=postgres.getJdbcUrl().substring(0,postgres.getJdbcUrl().lastIndexOf('/')+1)+name;
        var f=new Fixture(new DriverManagerDataSource(url,postgres.getUsername(),postgres.getPassword()));f.migrate(version);return f;
    }
    static <T>T proxy(T value,Class<T> type,JdbcTransactionManager tx) {
        var p=new ProxyFactory(value);p.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        return type.cast(p.getProxy());
    }
    static class Fixture {
        final DriverManagerDataSource ds;final JdbcTemplate jdbc;final J3CollectionStore store;
        final RawManualCallSnapshotStore raw;final RawSnapshotInspectionStore snapshots;final J3CatalogProjector projector;
        final J8BenchmarkAuditService audit;
        Fixture(DriverManagerDataSource ds) {
            this.ds=ds;jdbc=new JdbcTemplate(ds);var named=new NamedParameterJdbcTemplate(ds);var tx=new JdbcTransactionManager(ds);
            store=proxy(new JdbcJ3CollectionStore(named),J3CollectionStore.class,tx);
            raw=proxy(new JdbcRawManualCallSnapshotStore(named),RawManualCallSnapshotStore.class,tx);
            snapshots=new JdbcRawSnapshotInspectionStore(named);projector=new J3CatalogProjector(snapshots);
            audit=new J8BenchmarkAuditService(proxy(new JdbcJ8BenchmarkEvidenceStore(named),J8BenchmarkEvidenceStore.class,tx));
        }
        int migrate(String v) {return Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(v).load().migrate().migrationsExecuted;}
        J3MinimizedPageEvidence page(LocalDate date,int number,boolean next,long tournament,Instant at) {
            String body="""
                {"scheduled":[{"tournament":{"id":%d,"name":"Tournament %d","category":{"id":1,"name":"Europe"},
                "uniqueTournament":{"id":7,"name":"League"}},"timezoneEventCount":{"7200":2}}],"hasNextPage":%s}
                """.formatted(tournament,tournament,next);
            var response=new ScheduledEventsTransportResponse("SCHEDULED_EVENTS|date="+date+"|page="+number,
                    at,at.plusSeconds(1),200,"application/json",Duration.ofSeconds(1),RawPayloadEvidence.capture(body.getBytes(StandardCharsets.UTF_8)));
            var saved=raw.save(new RawManualCallSnapshot(SofascoreEndpointType.SCHEDULED_EVENTS,response.requestKey(),at,at.plusSeconds(1),
                    200,"application/json",Duration.ofSeconds(1),response.payload(),"scheduled-events-v1",RawSnapshotSchemaStatus.RAW_ONLY,null));
            if(saved.outcome()==RawSnapshotPersistenceOutcome.INSERTED) raw.classify(saved.snapshotId(),RawSnapshotSchemaStatus.PARSED,null);
            return J3MinimizedPageEvidence.recorded(number,response,saved,RawSnapshotSchemaStatus.PARSED,next,null)
                    .withOccurrence(saved.occurrenceId().orElseThrow());
        }
        com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Collection publish(LocalDate date,List<J3MinimizedPageEvidence> pages,Instant start) {
            UUID id=UUID.randomUUID();store.begin(id,date,Trigger.MANUAL_PROVIDER,start);
            var projection=projector.project(date,pages);assertThat(projection.catalog().available()).isTrue();
            store.publish(new Proof(id,date,Trigger.MANUAL_PROVIDER,start,start.plusSeconds(2),State.COMPLETED,"NONE",pages),projection.entries());
            return store.find(id).orElseThrow();
        }
        UUID legacyCacheSuccess(J3MinimizedPageEvidence page) {
            UUID id=UUID.randomUUID();var session=audit.start(id,J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                    J8BenchmarkExecutionMode.GUARDED_PROVIDER,35,Optional.of(DATE));
            var unit=session.declare(1,SofascoreEndpointType.SCHEDULED_EVENTS,"SCHEDULED_EVENTS|date="+DATE+"|page=1",Optional.empty(),OptionalLong.empty());
            session.captureSnapshot(unit,new RawSnapshotPersistenceResult(page.snapshotId(),RawSnapshotPersistenceOutcome.CACHE_HIT,
                    page.payloadSha256(),page.payloadSizeBytes(),OptionalLong.empty()),"scheduled-events-v1");
            session.resolve(unit,J8BenchmarkResolutionSource.CACHE,J8BenchmarkOutcomeType.PARSED,Optional.of(RawSnapshotSchemaStatus.PARSED),
                    0,Optional.empty(),OptionalInt.empty(),Optional.empty());
            session.finish(J8BenchmarkCampaignTerminalState.COMPLETED,Optional.empty());return id;
        }
    }
}
