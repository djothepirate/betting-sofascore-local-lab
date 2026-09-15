package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.JdbcJ3AutomationStore;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger;
import com.bettingproject.sofascorelocal.port.J3AutomationStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.bettingproject.sofascorelocal.integration.J3CollectionPersistenceIT.*;

@Testcontainers
class J3AutomationPersistenceIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("j3_automation_admin").withUsername("sofascore_lab").withPassword("integration-test-only");
    private static Fixture fixture(String version) {return J3CollectionPersistenceIT.fixture(POSTGRES,version);}
    private static Owner owner() {return new Owner(UUID.randomUUID(),999_060,T0.minusSeconds(60));}
    private static J3AutomationStore store(Fixture f) {
        return proxy(new JdbcJ3AutomationStore(new NamedParameterJdbcTemplate(f.ds),f.store),J3AutomationStore.class,new JdbcTransactionManager(f.ds));
    }
    @Test void firstStartEnabledAndFailedDailyOpportunityIsNeverRetriedOnSameDate() {
        var f=fixture("56");var a=store(f);var owner=owner();
        assertThat(a.settings().enabled()).isTrue();assertThat(a.settings().mode()).isEqualTo(Mode.STARTUP_OR_DAY_CHANGE);
        assertThat(a.lead(owner,ignored->false)).isTrue();
        a.tick(owner,T0,T0,false,null);var order=a.claim(owner,T0).orElseThrow();
        a.finish(order.id(),owner,OrderState.FAILED,"HTTP_FORBIDDEN",T0.plusSeconds(1));
        var restart=store(f);var nextOwner=owner();assertThat(restart.lead(nextOwner,ignored->false)).isTrue();
        restart.tick(nextOwner,T0.plusSeconds(10),T0.plusSeconds(10),false,null);
        assertThat(restart.claim(nextOwner,T0.plusSeconds(10))).isEmpty();
        assertThat(restart.recent(100)).hasSize(1);
    }
    @Test void fixedDailyDatesMissedDuringSeveralDaysOffAreRecordedWithoutReplay() {
        var f=fixture("56");var a=store(f);var owner=owner();a.lead(owner,ignored->false);
        a.configure(1,true,Mode.DAILY_AT,LocalTime.of(7,0),T0);
        Instant restarted=T0.plus(Duration.ofDays(3));
        a.tick(owner,restarted,restarted,false,null);
        assertThat(a.recent(100)).hasSize(4).allSatisfy(order->{
            assertThat(order.trigger()).isEqualTo(Trigger.DAILY_AT);
            assertThat(order.state()).isEqualTo(OrderState.MISSED);
            assertThat(order.reason()).isEqualTo("LAB_NOT_IN_SERVICE");
        });
        assertThat(a.claim(owner,restarted)).isEmpty();
        a.tick(owner,restarted,restarted.plusMillis(500),true,null);
        assertThat(a.recent(100)).hasSize(4);
    }
    @Test void successAfterDailyQueueingIsRecheckedWhileExplicitScheduleStillRuns() {
        var f=fixture("56");var a=store(f);var owner=owner();a.lead(owner,ignored->false);
        a.tick(owner,T0,T0,false,null);
        f.publish(DATE,List.of(f.page(DATE,1,false,1,T0)),T0);
        assertThat(a.claim(owner,T0.plusSeconds(3))).isEmpty();
        var scheduled=a.schedule(UUID.randomUUID(),1,DATE,T0.plusSeconds(5),T0);
        a.tick(owner,T0.plusSeconds(4),T0.plusSeconds(5),true,null);
        assertThat(a.claim(owner,T0.plusSeconds(5))).get().extracting(Order::id).isEqualTo(scheduled.id());
        assertThat(a.recent(100)).filteredOn(o->o.trigger()==Trigger.DAILY).singleElement()
                .satisfies(o->assertThat(o.reason()).isEqualTo("DATE_ALREADY_SUCCESSFUL"));
    }
    @Test void scheduledTimesDuringStopOrDisableAreMissedAndNeverCaughtUp() {
        var f=fixture("56");var a=store(f);var owner=owner();a.lead(owner,ignored->false);
        a.configure(1,true,Mode.DAILY_AT,LocalTime.of(10,1),T0);
        var off=a.schedule(UUID.randomUUID(),1,DATE,T0.plusSeconds(5),T0);
        a.tick(owner,T0,T0.plusSeconds(10),false,null);
        assertThat(a.find(off.id())).get().extracting(Order::state).isEqualTo(OrderState.MISSED);
        var disabled=a.schedule(UUID.randomUUID(),1,DATE,T0.plusSeconds(15),T0.plusSeconds(10));
        a.configure(2,false,Mode.DAILY_AT,LocalTime.of(10,1),T0.plusSeconds(11));
        a.tick(owner,T0.plusSeconds(14),T0.plusSeconds(15),true,null);
        assertThat(a.find(disabled.id())).get().extracting(Order::reason).isEqualTo("AUTOMATION_DISABLED");
        a.configure(3,true,Mode.DAILY_AT,LocalTime.of(10,1),T0.plusSeconds(16));
        a.tick(owner,T0.plusSeconds(16),T0.plusSeconds(17),true,null);
        assertThat(a.claim(owner,T0.plusSeconds(17))).isEmpty();
    }
    @Test void disabledAutomationLeavesManualAvailableAndDoubleClickReusesItsIdentity() {
        var f=fixture("56");var a=store(f);var owner=owner();a.lead(owner,ignored->false);
        a.configure(1,false,Mode.STARTUP_OR_DAY_CHANGE,null,T0);
        UUID id=UUID.randomUUID();var manual=a.manual(id,DATE,Trigger.MANUAL_IMPORT,"a".repeat(64),owner,T0);
        assertThat(a.manual(id,DATE,Trigger.MANUAL_IMPORT,"a".repeat(64),owner,T0.plusSeconds(1))).isEqualTo(manual);
        assertThatThrownBy(()->a.manual(id,DATE.plusDays(1),Trigger.MANUAL_IMPORT,"a".repeat(64),owner,T0))
                .hasMessage("J3_MANUAL_IDENTITY_CONFLICT");
        assertThat(a.claim(owner,T0.plusSeconds(2))).get().extracting(Order::id).isEqualTo(id);
    }
    @Test void unavailableRuntimeDoesNotConsumeDailyAttemptAndSameInstantExplicitOrderHasPriority() {
        var f=fixture("56");var a=store(f);var owner=owner();a.lead(owner,ignored->false);
        a.tick(owner,T0,T0,false,"RUNTIME_UNAVAILABLE");assertThat(a.recent(100)).isEmpty();
        var planned=a.schedule(UUID.randomUUID(),1,DATE,T0.plusSeconds(1),T0);
        a.tick(owner,T0,T0.plusSeconds(1),true,null);
        assertThat(a.claim(owner,T0.plusSeconds(1))).get().extracting(Order::id).isEqualTo(planned.id());
        a.finish(planned.id(),owner,OrderState.FAILED,"HTTP_FORBIDDEN",T0.plusSeconds(2));
        assertThat(a.claim(owner,T0.plusSeconds(2))).isEmpty();
        assertThat(a.recent(100)).filteredOn(o->o.trigger()==Trigger.DAILY).singleElement()
                .satisfies(o->assertThat(o.reason()).isEqualTo("EXPLICIT_OCCURRENCE_HAS_PRIORITY"));
    }
    @Test void revisionCancelsOldPlanButCannotChangeAnAdmittedOccurrence() {
        var f=fixture("56");var a=store(f);var owner=owner();a.lead(owner,ignored->false);
        UUID rule=UUID.randomUUID();var first=a.schedule(rule,1,DATE,T0.plusSeconds(10),T0);
        var revised=a.schedule(rule,2,DATE,T0.plusSeconds(20),T0);
        assertThat(a.find(first.id())).get().extracting(Order::reason).isEqualTo("PLAN_REVISED");
        a.tick(owner,T0.plusSeconds(19),T0.plusSeconds(20),true,null);
        assertThatThrownBy(()->a.schedule(rule,3,DATE,T0.plusSeconds(30),T0.plusSeconds(21))).hasMessage("J3_PLAN_ALREADY_ADMITTED");
        assertThat(a.find(revised.id())).get().extracting(Order::state).isEqualTo(OrderState.QUEUED);
    }
    @Test void concurrentClaimsCannotAdmitTwoJ3Runs() throws Exception {
        var f=fixture("56");var a=store(f);var owner=owner();
        a.manual(UUID.randomUUID(),DATE,Trigger.MANUAL_PROVIDER,null,owner,T0);
        a.manual(UUID.randomUUID(),DATE.plusDays(1),Trigger.MANUAL_PROVIDER,null,owner,T0);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<Optional<Order>> task=()->{barrier.await(5,TimeUnit.SECONDS);return a.claim(owner,T0);};
            var one=pool.submit(task);var two=pool.submit(task);
            assertThat(List.of(one.get(5,TimeUnit.SECONDS),two.get(5,TimeUnit.SECONDS)).stream().filter(Optional::isPresent)).hasSize(1);
        }
    }
    @Test void liveProcessCannotLoseLeadershipToSecondInstance() {
        var f=fixture("56");var a=store(f);var first=owner();var second=owner();
        assertThat(a.lead(first,ignored->false)).isTrue();
        assertThat(a.lead(second,ignored->true)).isFalse();
        assertThatThrownBy(()->a.tick(second,T0,T0,false,null)).hasMessage("J3_SCHEDULER_NOT_OWNER");
        a.release(first);assertThat(a.lead(second,ignored->true)).isTrue();
    }
}
