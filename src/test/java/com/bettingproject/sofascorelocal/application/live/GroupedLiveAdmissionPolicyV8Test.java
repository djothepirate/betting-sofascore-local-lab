package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_INCIDENTS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_LINEUPS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_STATISTICS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GroupedLiveAdmissionPolicyV8Test {
    private static GroupedAdmissionProfile profile() {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var family : new SofascoreEndpointType[]{EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS})
            costs.put(family, new EndpointEnvelope(Duration.ofMillis(500), Duration.ofMillis(100)));
        return new GroupedAdmissionProfile(costs, "8".repeat(64), "live-v8");
    }

    private static GroupedAdmissionProfile uniformExchange(Duration exchangeEnvelope) {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var family : new SofascoreEndpointType[]{EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS})
            costs.put(family, new EndpointEnvelope(exchangeEnvelope, Duration.ZERO));
        return new GroupedAdmissionProfile(costs, "8".repeat(64), "live-v8");
    }

    private static GroupedAdmissionProfile measuredV8Profile() {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        costs.put(EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(500)));
        costs.put(EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(400)));
        costs.put(EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(400)));
        costs.put(EVENT_LINEUPS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(450)));
        return new GroupedAdmissionProfile(costs, "8".repeat(64), "live-v8");
    }

    @Test
    void tenMatchesFitTheIsolatedMinuteAndHourlyV8PressurePolicy() {
        assertThat(10 * (240 + 4 + 4)).isEqualTo(2_480)
                .isEqualTo(LiveAdmissionPolicy.V8_MAXIMUM_CALLS_PER_HOUR * 9 / 10);
        assertThat(11 * (240 + 4 + 4)).isGreaterThan(LiveAdmissionPolicy.V8_MAXIMUM_CALLS_PER_HOUR * 9 / 10);
        assertThat(LiveAdmissionPolicy.estimatedLiveCallsPerMinuteV8(10)).isEqualTo(40.0d);
        assertThat(GroupedLiveAdmissionSimulationV8.SCENARIOS).isEqualTo(16);
        assertThat(GroupedLiveAdmissionSimulationV8.POST_EXCHANGE_FENCE).isEqualTo(Duration.ofMillis(500));
        assertThat(GroupedLiveScheduleV8.INTER_GROUP_SLOT_RESERVE).isEqualTo(Duration.ofSeconds(1));
        assertThat(GroupedLiveScheduleV8.PLAY_REQUEST_EMISSION_HEAD_START).isEqualTo(Duration.ofMillis(500));
        assertThat(GroupedLiveAdmissionSimulationV8.MAXIMUM_DEPARTURES_PER_MINUTE).isEqualTo(45);
        assertThat(GroupedLiveAdmissionSimulationV8.MAXIMUM_DEPARTURES_PER_HOUR).isEqualTo(2_756);
        assertThat(profile().criticalInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile().lineupInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile().minimumRequestStartInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(profile().interGroupDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(GroupedLiveAdmissionSimulationV8.hasStrictMinuteDepartureBudget(10, profile())).isTrue();
        assertThat(GroupedLiveAdmissionSimulationV8.fits(10, profile())).isTrue();
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV8(profile())).isEqualTo(10);
    }

    @Test
    void measuredAsymmetricProfileKeepsTenTargetsWithinTheStrictMinuteAcrossEveryReplayScenario() {
        var measured = measuredV8Profile();

        assertThat(LiveSchedule.v8StrictGroupReservation(measured)).isEqualTo(Duration.ofMillis(6_000));
        assertThat(GroupedLiveAdmissionSimulationV8.hasStrictMinuteDepartureBudget(10, measured)).isTrue();
        assertThat(GroupedLiveAdmissionSimulationV8.fits(10, measured)).isTrue();
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV8(measured)).isEqualTo(10);
    }

    @Test
    void admissionRefusesTenMatchesWhenTheTerminalFenceWouldPushTheNextWavePastOneMinute() {
        var tooSlow = uniformExchange(Duration.ofMillis(1_001));
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(10);
        var admission = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);

        assertThat(GroupedLiveAdmissionSimulationV8.hasStrictMinuteDepartureBudget(10, tooSlow)).isFalse();
        assertThat(GroupedLiveAdmissionSimulationV8.fits(10, tooSlow)).isFalse();
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV8(tooSlow)).isLessThan(10);
        assertThatIllegalArgumentException().isThrownBy(() -> admission.admitV8(10, tooSlow))
                .withMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
    }

    @Test
    void admissionRefusesTenMatchesWhenOnlyTheStaticWorkerStartSlotReserveExceedsTheMinute() {
        // Without the reserve, ten groups consume 56 seconds. The immutable
        // one-second slot reserve lifts each group to 6.6 seconds, so ten cannot
        // be admitted in the strict minute even though the terminal fences
        // alone would fit.
        var reserveOnlyOverflow = uniformExchange(Duration.ofMillis(900));
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(10);
        var admission = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);

        assertThat(GroupedLiveAdmissionSimulationV8.hasStrictMinuteDepartureBudget(10, reserveOnlyOverflow)).isFalse();
        assertThat(GroupedLiveAdmissionSimulationV8.fits(10, reserveOnlyOverflow)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> admission.admitV8(10, reserveOnlyOverflow))
                .withMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
    }

    @Test
    void eleventhMatchIsRejectedBeforeStorageWhenTheV8ProfileIsActive() {
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(10);
        var admission = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);

        admission.admitV8(10, profile());
        assertThatIllegalArgumentException().isThrownBy(() -> admission.admitV8(11, profile()))
                .withMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
    }
}
