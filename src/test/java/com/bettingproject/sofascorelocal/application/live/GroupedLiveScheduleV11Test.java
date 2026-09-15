package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

class GroupedLiveScheduleV11Test {
    static final Instant START=Instant.parse("2026-09-13T08:00:00Z");
    static final UUID A=UUID.randomUUID(),B=UUID.randomUUID();
    static GroupedAdmissionProfile profile() {
        var envelopes=new EnumMap<SofascoreEndpointType,EndpointEnvelope>(SofascoreEndpointType.class);
        for(var family:List.of(EVENT_DETAILS,EVENT_INCIDENTS,EVENT_STATISTICS,EVENT_LINEUPS))
            envelopes.put(family,new EndpointEnvelope(Duration.ofMillis(500),Duration.ofMillis(100)));
        return new GroupedAdmissionProfile(envelopes,"b".repeat(64),"live-v9");
    }
    static LiveSessionSchedule schedule(List<UUID> ids) {
        return new LiveSessionSchedule(ids,START,START.plusSeconds(3600),Duration.ofSeconds(60),"live-v11",UUID.randomUUID(),profile());
    }
    static LiveJ4ControlFacts controls() {
        return new LiveJ4ControlFacts(BooleanFact.FALSE,DetailIdFact.ONE,BooleanFact.TRUE,BooleanFact.TRUE,StatusDescription.OTHER,TextFact.absent());
    }
    @Test void pauseAbandonsPartialGroupAndResumesOnFutureJ4WithoutReactivatingStoppedEvent() {
        var s=schedule(List.of(A,B));var first=s.next(START).orElseThrow();
        s.started(first,START);s.departed(first,START);
        s.completed(first,"inprogress",false,Map.of(),START.plusMillis(100),START,null,controls(),"PARSED");
        var pending=s.next(START.plusMillis(1200)).orElseThrow();
        assertThat(pending.endpoint()).isEqualTo(EVENT_INCIDENTS);
        s.pauseForJ3(START.plusMillis(1200));
        assertThat(s.states()).allSatisfy(state->assertThat(state.state()).isEqualTo("PAUSED_J3"));
        assertThat(s.next(START.plusSeconds(120))).isEmpty();
        assertThat(s.mayDispatch(pending,START.plusSeconds(120))).isFalse();
        s.stopEvent(B,"STOPPED_OPERATOR");
        var missed=s.resumeAfterJ3(START.plusSeconds(125));
        assertThat(missed).anySatisfy(slot-> {
            assertThat(slot.eventId()).isEqualTo(A);assertThat(slot.endpoint()).isEqualTo("EVENT_INCIDENTS");
            assertThat(slot.count()).isGreaterThanOrEqualTo(2);
        });
        assertThat(s.next(START.plusSeconds(125))).isEmpty();
        var resumed=s.next(START.plusSeconds(128)).orElseThrow();
        assertThat(resumed.eventId()).isEqualTo(A);assertThat(resumed.endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(resumed.kind()).isEqualTo("J4_J3_RESUME_RECHECK");
        assertThat(resumed.groupId()).isNotEqualTo(first.groupId());
        assertThat(resumed.groupSequence()).isGreaterThan(first.groupSequence());
        assertThat(s.states()).filteredOn(state->state.eventId().equals(B)).singleElement()
                .satisfies(state->assertThat(state.state()).isEqualTo("STOPPED_OPERATOR"));
        assertThat(s.familySchedules(A)).filteredOn(f->f.endpoint()!=EVENT_DETAILS)
                .allSatisfy(f->assertThat(f.nextDueAt()).isNull());
    }
    @Test void anUnpublishedExchangeCannotBePausedAndDeadlineIsNeverExtended() {
        var s=schedule(List.of(A));var first=s.next(START).orElseThrow();s.started(first,START);
        assertThatThrownBy(()->s.pauseForJ3(START.plusMillis(1))).hasMessage("J3_LIVE_NOT_QUIESCENT");
        s.completed(first,"inprogress",false,Map.of(),START.plusMillis(100),START,null,controls(),"PARSED");
        s.pauseForJ3(START.plusSeconds(1));s.resumeAfterJ3(START.plusSeconds(3600));
        assertThat(s.terminal()).isTrue();assertThat(s.next(START.plusSeconds(3601))).isEmpty();
    }
    @Test void historicalV9DoesNotAcquirePauseCapability() {
        var s=new LiveSessionSchedule(List.of(A),START,START.plusSeconds(3600),Duration.ofSeconds(60),
                "live-v9",UUID.randomUUID(),profile());
        assertThatThrownBy(()->s.pauseForJ3(START)).hasMessage("J3_LIVE_POLICY_UNSUPPORTED");
        assertThat(s.next(START)).isPresent();
    }
}
