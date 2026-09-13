package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.*;
import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.application.network.*;
import com.bettingproject.sofascorelocal.application.network.playwright.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger;
import com.bettingproject.sofascorelocal.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static com.bettingproject.sofascorelocal.integration.J3CollectionPersistenceIT.*;

/** Runtime threads and real PostgreSQL; every provider response is an in-process synthetic fake. */
@Testcontainers
class J3RuntimeIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("j3_runtime_admin").withUsername("sofascore_lab").withPassword("integration-test-only");
    static final URI ORIGIN=URI.create("https://www.sofascore.com");

    @Test void firstStartupCollectsOnceAndRestartWithOtherDatesKeepsEachLastSuccess() throws Exception {
        var f=fixture(POSTGRES,"57");
        try(Harness h=new Harness(f)) {
            h.runtime.start();
            await(()->f.store.latest(LocalDate.now(ZONE)).isPresent());
            var first=f.store.latest(LocalDate.now(ZONE)).orElseThrow();
            assertThat(h.calls).hasValue(1);
            assertThat(first.proof().trigger()).isEqualTo(Trigger.DAILY);
            await(()->h.closes.get()==1);
            h.runtime.stop();
            try(Harness restarted=new Harness(new Fixture(f.ds))) {
                restarted.runtime.start();
                await(()->restarted.orders.recent(100).size()==1);
                LocalDate other=LocalDate.now(ZONE).minusDays(2);
                UUID imported=UUID.randomUUID();
                restarted.runtime.manual(imported,other,List.of(payload(7)));
                await(()->restarted.orders.find(imported).orElseThrow().terminal());
                assertThat(f.store.latest(other)).get().satisfies(c->assertThat(c.catalog().options()).hasSize(1));
                assertThat(f.store.latest(first.date())).contains(first);
                assertThat(restarted.orders.find(imported)).get().extracting(Order::state).isEqualTo(OrderState.COMPLETED);
                assertThat(restarted.calls).hasValue(0);
                assertThat(restarted.runtime.settings().enabled()).isTrue();
                assertThat(restarted.runtime.manual(imported,other,List.of(payload(7)))).isEqualTo(restarted.orders.find(imported).orElseThrow());
                assertThat(restarted.orders.recent(100)).hasSize(2);
            }
        }
    }

    @Test void manualImportAndPreferenceSurviveRestartWithNetworkUnqualified() throws Exception {
        var f=fixture(POSTGRES,"57");
        try(Harness h=new Harness(f)) {
            when(h.qualification.snapshot()).thenReturn(J3ProviderQualificationSnapshot.blocked(List.of("J3_QUALIFICATION_DISABLED")));
            h.runtime.start();
            UUID imported=UUID.randomUUID();
            h.runtime.manual(imported,DATE,List.of(payload(8)));
            await(()->h.orders.find(imported).orElseThrow().terminal());
            h.runtime.configure(1,false,Mode.DAILY_AT,LocalTime.of(8,30));
            assertThatThrownBy(()->h.runtime.manual(UUID.randomUUID(),DATE,List.of(payload(true))))
                    .isInstanceOf(RuntimeException.class);
            assertThat(h.orders.recent(100).stream().filter(o->o.trigger()==Trigger.MANUAL_IMPORT)).hasSize(1);
            verifyNoInteractions(h.factory,h.coordinator);
            h.runtime.stop();
            try(Harness restart=new Harness(new Fixture(f.ds))) {
                restart.runtime.start();
                assertThat(restart.runtime.settings().enabled()).isFalse();
                assertThat(restart.runtime.settings().dailyTime()).isEqualTo(LocalTime.of(8,30));
                assertThat(f.store.latest(DATE)).get().satisfies(c->assertThat(c.catalog().options()).hasSize(1));
            }
        }
    }

    @Test void maintenanceContextCannotTakeLeadershipOrCollectEvenWithAllProviderFlagsEnabled() {
        var f=fixture(POSTGRES,"57");
        try(Harness h=new Harness(f);
            var context=new org.springframework.context.support.GenericApplicationContext()) {
            var event=new org.springframework.boot.context.event.ApplicationReadyEvent(
                    new org.springframework.boot.SpringApplication(),new String[0],context,Duration.ZERO);
            h.runtime.onApplicationReady(event);
            assertThat(h.runtime.runtimeReason()).isEqualTo("J3_WEB_APPLICATION_REQUIRED");
            assertThat(h.orders.recent(100)).isEmpty();
            assertThat(f.jdbc.queryForObject("select owner_id from j3_automation_settings",UUID.class)).isNull();
            verifyNoInteractions(h.factory,h.coordinator);
        }
    }

    private static RawPayloadEvidence payload(long id) {
        return RawPayloadEvidence.capture(("""
            {"scheduled":[{"tournament":{"id":%d,"name":"Synthetic tournament","category":{"id":1,"name":"Local"},
            "uniqueTournament":{"id":7,"name":"League"}},
            "timezoneEventCount":{"7200":1}}],"hasNextPage":false}
            """).formatted(id).getBytes(StandardCharsets.UTF_8));
    }
    private static RawPayloadEvidence payload(boolean next) {
        return RawPayloadEvidence.capture(("{\"scheduled\":[],\"hasNextPage\":"+next+"}").getBytes(StandardCharsets.UTF_8));
    }
    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!condition.getAsBoolean() && System.nanoTime()<end) Thread.sleep(30);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    static final java.time.ZoneId ZONE=com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.ZONE;
    static final class Harness implements AutoCloseable {
        final J3AutomationStore orders;
        final J3ProviderQualificationPolicy qualification=mock(J3ProviderQualificationPolicy.class);
        final ManualProviderRequestCoordinator coordinator=mock(ManualProviderRequestCoordinator.class);
        final PlaywrightProviderCampaignFactory factory=mock(PlaywrightProviderCampaignFactory.class);
        final AtomicInteger calls=new AtomicInteger(),closes=new AtomicInteger();
        final J3RuntimeService runtime;
        Harness(Fixture f) {
            var tx=new JdbcTransactionManager(f.ds);
            orders=proxy(new JdbcJ3AutomationStore(new NamedParameterJdbcTemplate(f.ds),f.store),J3AutomationStore.class,tx);
            var completion=proxy(new J3CollectionCompletionService(f.store,orders),J3CollectionCompletionService.class,tx);
            var cache=mock(J3ScheduledEventsPageCache.class);
            when(cache.findFreshParsed(any(),any(),any(),any())).thenReturn(Optional.empty());
            var engine=new J3CollectionExecutor(f.raw,cache,f.store,f.projector,completion,f.audit,new SofascoreEndpointCatalog());
            var guard=mock(ProviderCampaignGuardStore.class);
            var supervisor=mock(PlaywrightProviderSupervisor.class);
            var resilience=mock(ProviderResilienceStore.class);
            var live=mock(LiveCampaignService.class);
            when(qualification.snapshot()).thenReturn(J3ProviderQualificationSnapshot.available(ORIGIN));
            when(resilience.snapshot()).thenReturn(new ProviderResilienceData.Snapshot(ProviderResilienceData.State.OPEN,0,Instant.now(),
                    null,null,null,null,null,null,null,null));
            var lease=mock(ManualProviderRequestCoordinator.CampaignLease.class);
            var ownership=new LiveCampaignData.Ownership(UUID.randomUUID(),UUID.randomUUID(),1);
            when(lease.ownership()).thenReturn(ownership);when(guard.isOwned(ownership)).thenReturn(true);
            when(coordinator.tryAcquireJ3Campaign(any())).thenReturn(lease);
            doAnswer(i->{closes.incrementAndGet();return null;}).when(lease).close();
            when(live.submitJ3(any(),any())).thenReturn(Optional.empty());
            var campaign=mock(PlaywrightProviderCampaign.class);
            when(factory.open(any(),any())).thenReturn(campaign);
            when(campaign.execute(any(),any())).thenAnswer(i->{
                var admission=(PlaywrightDispatchAdmission)i.getArgument(1);
                admission.check();try(var permit=admission.acquireDispatchPermit()) {calls.incrementAndGet();}
                Instant now=Instant.now();
                return new PlaywrightProviderResponse(now,now,200,"application/json",Duration.ZERO,payload(1));
            });
            var env=new MockEnvironment();env.setActiveProfiles("local");env.setProperty("server.address","127.0.0.1");
            runtime=new J3RuntimeService(orders,new J3LegacyRecoveryService(f.store,f.snapshots,f.projector),completion,engine,
                    qualification,guard,resilience,coordinator,factory,supervisor,live,env,true);
        }
        public void close() {runtime.stop();}
    }
}
