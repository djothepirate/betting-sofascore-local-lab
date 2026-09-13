package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.*;
import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.application.network.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.*;
import com.bettingproject.sofascorelocal.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static com.bettingproject.sofascorelocal.integration.J3CollectionPersistenceIT.*;

/** Tests feed synthetic responses directly to the executor. No Playwright or provider HTTP. */
@Testcontainers
class J3CollectionExecutionIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("j3_execution_admin").withUsername("sofascore_lab").withPassword("integration-test-only");
    private static final URI ORIGIN=ScheduledEventsProviderPageRequest.parseExactProviderOrigin("https://www.sofascore.com");
    private static Fixture fixture() {return J3CollectionPersistenceIT.fixture(POSTGRES,"56");}
    private static Owner owner() {return new Owner(UUID.randomUUID(),999_060,Instant.now().minusSeconds(10));}
    private static J3AutomationStore orders(Fixture f) {
        return proxy(new JdbcJ3AutomationStore(new NamedParameterJdbcTemplate(f.ds),f.store),J3AutomationStore.class,new JdbcTransactionManager(f.ds));
    }
    private static J3CollectionExecutor executor(Fixture f,J3AutomationStore orders,J3ScheduledEventsPageCache cache) {
        var completion=proxy(new J3CollectionCompletionService(f.store,orders),J3CollectionCompletionService.class,new JdbcTransactionManager(f.ds));
        return new J3CollectionExecutor(f.raw,cache,f.store,f.projector,completion,f.audit,new SofascoreEndpointCatalog());
    }
    private static Order manual(J3AutomationStore store,Owner owner,Trigger trigger,List<RawPayloadEvidence> pages) {
        UUID id=UUID.randomUUID();Instant now=Instant.now();
        store.manual(id,DATE,trigger,pages==null?null:J3LocalBatchValidator.fingerprint(pages),owner,now);
        return store.claim(owner,now).orElseThrow();
    }
    private static RawPayloadEvidence payload(boolean next) {
        return RawPayloadEvidence.capture(("{\"scheduled\":[],\"hasNextPage\":"+next+"}").getBytes(StandardCharsets.UTF_8));
    }
    private static ScheduledEventsTransportResponse response(ScheduledEventsProviderPageRequest request,boolean next) {
        Instant now=Instant.now();return new ScheduledEventsTransportResponse(request.requestKey(),now,now,200,"application/json",Duration.ZERO,payload(next));
    }
    @Test void directImportPublishesCompleteProofAndOrderWithoutTouchingProviderCache() {
        var f=fixture();var orders=orders(f);var owner=owner();var cache=mock(J3ScheduledEventsPageCache.class);
        var pages=List.of(payload(true),payload(false));var order=manual(orders,owner,Trigger.MANUAL_IMPORT,pages);
        var result=executor(f,orders,cache).execute(order,null,pages,null,()->null);
        assertThat(result.proof().successful()).isTrue();assertThat(result.safeToResumeLive()).isTrue();
        assertThat(f.store.latest(DATE)).get().satisfies(c->{
            assertThat(c.proof().pages()).hasSize(2).allSatisfy(p->{
                assertThat(p.resolutionSource()).isEqualTo(J3PageResolutionSource.LOCAL_JSON_IMPORT);
                assertThat(p.providerRequestExecuted()).isFalse();assertThat(p.occurrenceId()).isNotNull();
            });assertThat(c.catalog().options()).isEmpty();
        });
        assertThat(orders.find(order.id())).get().extracting(Order::state).isEqualTo(OrderState.COMPLETED);
        assertThat(f.jdbc.queryForObject("select terminal_state from j8_benchmark_campaign_result where campaign_id=?",String.class,order.id())).isEqualTo("COMPLETED");
        verifyNoInteractions(cache);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={27,35})
    void completeLargeImportsPublishAllPagesAndNeverTouchProviderCache(int count) {
        var f=fixture();var orders=orders(f);var cache=mock(J3ScheduledEventsPageCache.class);
        var pages=java.util.stream.IntStream.range(0,count).mapToObj(i->payload(i<count-1)).toList();
        var order=manual(orders,owner(),Trigger.MANUAL_IMPORT,pages);
        var result=executor(f,orders,cache).execute(order,null,pages,null,()->null);
        assertThat(result.proof().successful()).isTrue();
        assertThat(f.store.latest(DATE).orElseThrow().proof().pages()).hasSize(count);
        assertThat(orders.find(order.id()).orElseThrow().state()).isEqualTo(OrderState.COMPLETED);
        verifyNoInteractions(cache);
    }
    @Test void lostSuccessfulCommitResponseIsReconciledWithoutRepeatingTheCollection() {
        var f=fixture();var orders=orders(f);var cache=mock(J3ScheduledEventsPageCache.class);
        var completion=proxy(new J3CollectionCompletionService(f.store,orders),J3CollectionCompletionService.class,new JdbcTransactionManager(f.ds));
        var uncertain=mock(J3CollectionCompletionService.class,delegatesTo(completion));
        AtomicInteger publications=new AtomicInteger();
        doAnswer(i->{
            completion.publish(i.getArgument(0),i.getArgument(1),i.getArgument(2),i.getArgument(3));
            publications.incrementAndGet();throw new IllegalStateException("simulated response lost after commit");
        }).when(uncertain).publish(any(),any(),any(),any());
        var pages=List.of(payload(false));var order=manual(orders,owner(),Trigger.MANUAL_IMPORT,pages);
        var executor=new J3CollectionExecutor(f.raw,cache,f.store,f.projector,uncertain,f.audit,new SofascoreEndpointCatalog());
        var result=executor.execute(order,null,pages,null,()->null);
        assertThat(result.proof().successful()).isTrue();assertThat(result.safeToResumeLive()).isTrue();
        assertThat(publications.get()).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select count(*) from j8_benchmark_campaign_result where campaign_id=?",Long.class,order.id())).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select count(*) from provider_snapshot_occurrence",Long.class)).isEqualTo(1);
        assertThat(orders.find(order.id()).orElseThrow().state()).isEqualTo(OrderState.COMPLETED);
        verifyNoInteractions(cache);
    }
    @Test void page35WithMoreDataFailsWithoutDispatchingPage36() {
        var f=fixture();var orders=orders(f);var order=manual(orders,owner(),Trigger.MANUAL_PROVIDER,null);
        var cache=mock(J3ScheduledEventsPageCache.class);when(cache.findFreshParsed(any(),any(),any(),any())).thenReturn(Optional.empty());
        AtomicInteger calls=new AtomicInteger(),closes=new AtomicInteger();
        var access=new J3CollectionExecutor.ProviderAccess() {
            public ScheduledEventsTransportResponse execute(ScheduledEventsProviderPageRequest request,Runnable dispatch) {
                dispatch.run();assertThat(request.page()).isEqualTo(calls.incrementAndGet());return response(request,true);
            }
            public void close(){closes.incrementAndGet();}
        };
        var result=executor(f,orders,cache).execute(order,ORIGIN,null,access,()->null);
        assertThat(result.proof().state()).isEqualTo(State.FAILED);
        assertThat(result.proof().terminalCode()).isEqualTo("PAGINATION_LIMIT_REACHED");
        assertThat(calls.get()).isEqualTo(35);assertThat(closes.get()).isEqualTo(1);assertThat(f.store.latest(DATE)).isEmpty();
        assertThat(f.jdbc.queryForObject("select count(*) from provider_snapshot_occurrence",Long.class)).isEqualTo(35);
    }
    @Test void disablingAutomaticCollectionFinishesCurrentPageThenCancelsFollowingPages() {
        var f=fixture();var orders=orders(f);var owner=owner();orders.lead(owner,ignored->false);Instant now=Instant.now();
        orders.tick(owner,now,now,false,null);var order=orders.claim(owner,now).orElseThrow();
        var cache=mock(J3ScheduledEventsPageCache.class);when(cache.findFreshParsed(any(),any(),any(),any())).thenReturn(Optional.empty());
        AtomicInteger calls=new AtomicInteger();
        var access=new J3CollectionExecutor.ProviderAccess() {
            public ScheduledEventsTransportResponse execute(ScheduledEventsProviderPageRequest request,Runnable dispatch) {
                dispatch.run();calls.incrementAndGet();orders.configure(1,false,Mode.STARTUP_OR_DAY_CHANGE,null,Instant.now());return response(request,true);
            }
            public void close(){ }
        };
        var result=executor(f,orders,cache).execute(order,ORIGIN,null,access,()->orders.settings().enabled()?null:"AUTOMATION_DISABLED");
        assertThat(result.proof().state()).isEqualTo(State.CANCELLED);assertThat(calls.get()).isEqualTo(1);
        assertThat(result.proof().pages()).hasSize(1);assertThat(result.safeToResumeLive()).isTrue();
    }
    @Test void cacheOnlySuccessReusesSourcesAndNeverDispatchesProviderRequest() {
        var f=fixture();var orders=orders(f);var named=new NamedParameterJdbcTemplate(f.ds);
        var cache=proxy(new JdbcJ3ScheduledEventsPageCache(named),J3ScheduledEventsPageCache.class,new JdbcTransactionManager(f.ds));
        var first=manual(orders,owner(),Trigger.MANUAL_PROVIDER,null);
        var access=new J3CollectionExecutor.ProviderAccess() {
            public ScheduledEventsTransportResponse execute(ScheduledEventsProviderPageRequest request,Runnable dispatch) {dispatch.run();return response(request,false);}
            public void close(){ }
        };
        assertThat(executor(f,orders,cache).execute(first,ORIGIN,null,access,()->null).proof().successful()).isTrue();
        var repeated=manual(orders,owner(),Trigger.MANUAL_PROVIDER,null);var unused=mock(J3CollectionExecutor.ProviderAccess.class);
        var result=executor(f,orders,cache).execute(repeated,ORIGIN,null,unused,()->null);
        assertThat(result.proof().successful()).isTrue();
        assertThat(result.proof().pages()).singleElement().satisfies(p->assertThat(p.resolutionSource()).isEqualTo(J3PageResolutionSource.CACHE));
        verify(unused).close();verifyNoMoreInteractions(unused);
        assertThat(f.jdbc.queryForObject("select count(*) from provider_snapshot_occurrence",Long.class)).isEqualTo(1);
    }
    @Test void terminalPublicationFailureRollsBackCatalogueAndJ8SuccessTogether() {
        var f=fixture();var orders=orders(f);var cache=mock(J3ScheduledEventsPageCache.class);var pages=List.of(payload(false));
        var previous=f.publish(DATE,List.of(f.page(DATE,1,false,1,T0)),T0);
        var order=manual(orders,owner(),Trigger.MANUAL_IMPORT,pages);
        J3AutomationStore failing=mock(J3AutomationStore.class,delegatesTo(orders));AtomicInteger calls=new AtomicInteger();
        doAnswer(inv->{orders.finish(inv.getArgument(0),inv.getArgument(1),inv.getArgument(2),inv.getArgument(3),inv.getArgument(4));
            if(calls.incrementAndGet()==1)throw new IllegalStateException("simulated final commit failure");return null;})
                .when(failing).finish(any(),any(),any(),any(),any());
        var result=executor(f,failing,cache).execute(order,null,pages,null,()->null);
        assertThat(result.proof().state()).isEqualTo(State.FAILED);assertThat(result.safeToResumeLive()).isFalse();
        assertThat(f.store.latest(DATE)).contains(previous);
        assertThat(f.jdbc.queryForObject("select count(*) from j8_benchmark_campaign_result where campaign_id=?",Long.class,order.id())).isZero();
        assertThat(orders.find(order.id())).get().extracting(Order::state).isEqualTo(OrderState.FAILED);
    }
}
