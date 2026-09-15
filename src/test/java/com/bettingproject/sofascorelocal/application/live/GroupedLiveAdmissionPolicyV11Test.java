package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** New V11 admission replay; V10's historical class bindings remain untouched. */
class GroupedLiveAdmissionPolicyV11Test {
    @Test void committedV11ProfileBindsTheActualClassesAndItsNativeProof() throws Exception {
        var mapper=new tools.jackson.databind.json.JsonMapper();
        var profile=mapper.readTree(java.nio.file.Files.readString(java.nio.file.Path.of("docs/validation/WO060-LIVE-V11-PROFILE-20260914.json")));
        assertThat(profile.path("policyVersion").asString()).isEqualTo("live-v11");
        for(var entry:profile.path("productionClassSha256").properties()) {
            try(var bytes=Class.forName(entry.getKey()).getResourceAsStream("/"+entry.getKey().replace('.','/')+".class")) {
                assertThat(bytes).isNotNull();
                assertThat(com.bettingproject.sofascorelocal.security.Sha256.hex(bytes.readAllBytes())).isEqualTo(entry.getValue().asString());
            }
        }
        var nativeProof=mapper.readTree(java.nio.file.Files.readString(java.nio.file.Path.of("docs/validation/WO060-J3-LIVE-WORKER-20260914.json")));
        assertThat(nativeProof.path("status").asString()).isEqualTo("PASS");
        assertThat(nativeProof.path("realProviderCalls").intValue()).isZero();
        assertThat(nativeProof.path("workerCount").intValue()).isEqualTo(1);
    }
    static GroupedAdmissionProfile profile() {
        var envelopes=new EnumMap<SofascoreEndpointType,EndpointEnvelope>(SofascoreEndpointType.class);
        envelopes.put(SofascoreEndpointType.EVENT_DETAILS,new EndpointEnvelope(Duration.ofMillis(300),Duration.ofMillis(500)));
        envelopes.put(SofascoreEndpointType.EVENT_INCIDENTS,new EndpointEnvelope(Duration.ofMillis(300),Duration.ofMillis(400)));
        envelopes.put(SofascoreEndpointType.EVENT_STATISTICS,new EndpointEnvelope(Duration.ofMillis(350),Duration.ofMillis(400)));
        envelopes.put(SofascoreEndpointType.EVENT_LINEUPS,new EndpointEnvelope(Duration.ofMillis(300),Duration.ofMillis(450)));
        return new GroupedAdmissionProfile(envelopes,"6".repeat(64),"live-v9");
    }
    @Test void sixteenScenariosReplayTheV11SchedulerWithItsOwnMinuteAndHourlyLimits() {
        var profile=profile();
        assertThat(GroupedLiveAdmissionSimulationV11.SCENARIOS).isEqualTo(16);
        assertThat(GroupedLiveAdmissionSimulationV11.MAXIMUM_DEPARTURES_PER_MINUTE).isEqualTo(35);
        assertThat(GroupedLiveAdmissionSimulationV11.MAXIMUM_DEPARTURES_PER_HOUR).isEqualTo(2100);
        assertThat(GroupedLiveAdmissionSimulationV11.fits(8,profile)).isTrue();
        assertThat(GroupedLiveAdmissionSimulationV11.fits(9,profile)).isFalse();
        assertThat(LiveAdmissionPolicyV11.qualifiedCapacity(profile)).isEqualTo(8);
        var p=new LiveCampaignProperties();p.setQualifiedMatchCapacity(8);
        var admission=new LiveAdmissionPolicyV11(p,new LiveAdmissionPolicy(p,()->Long.MAX_VALUE));
        assertThatCode(()->admission.admit(8,profile)).doesNotThrowAnyException();
        assertThatThrownBy(()->admission.admit(9,profile)).hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={1,8})
    void eightMatchResumePhasesCannotReplayAbandonedJ5OrExtendWindow(int matches) {
        Instant start=GroupedLiveScheduleV11Test.START,end=start.plusSeconds(14400);
        var ids=java.util.stream.LongStream.rangeClosed(1,matches).mapToObj(id->new UUID(60,id)).toList();
        var schedule=new LiveSessionSchedule(ids,start,end,Duration.ofMinutes(1),"live-v11",UUID.randomUUID(),profile());
        var before=schedule.next(start).orElseThrow();schedule.started(before,start);schedule.departed(before,start);
        schedule.completed(before,"inprogress",false,Map.of(),start.plusMillis(800),start,null,GroupedLiveScheduleV11Test.controls(),"PARSED");
        schedule.pauseForJ3(start.plusSeconds(1));
        assertThat(schedule.next(start.plusSeconds(600))).isEmpty();
        var missed=schedule.resumeAfterJ3(start.plusSeconds(1200));
        assertThat(missed).isNotEmpty();
        var times=schedule.states().stream().map(LiveSchedule.EventState::nextDueAt).toList();
        assertThat(times).hasSize(matches).allSatisfy(at->assertThat(at).isAfter(start.plusSeconds(1200)).isBefore(end));
        for(int i=1;i<times.size();i++) assertThat(Duration.between(times.get(i-1),times.get(i))).isGreaterThanOrEqualTo(Duration.ofSeconds(6));
        for(var id:ids) assertThat(schedule.familySchedules(id).stream().filter(f->f.endpoint()!=SofascoreEndpointType.EVENT_DETAILS))
                .allSatisfy(f->assertThat(f.nextDueAt()).isNull());
        var resumed=schedule.next(start.plusSeconds(1203)).orElseThrow();
        assertThat(resumed.endpoint()).isEqualTo(SofascoreEndpointType.EVENT_DETAILS);
        assertThat(resumed.groupId()).isNotEqualTo(before.groupId());
    }
}
