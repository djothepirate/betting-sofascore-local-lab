package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

class GroupedLiveAdmissionPolicyV5Test {
    static GroupedAdmissionProfile candidateProfile() {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        costs.put(EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(400), Duration.ofMillis(600)));
        costs.put(EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(400), Duration.ofMillis(500)));
        costs.put(EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(500)));
        costs.put(EVENT_LINEUPS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(400)));
        // Synthetic test input, never a substitute for the separate V5 native qualification.
        return new GroupedAdmissionProfile(costs, "b".repeat(64), "live-v5");
    }

    private static GroupedAdmissionProfile uniform(long exchangeNanos) {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var endpoint : new SofascoreEndpointType[]{EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS})
            costs.put(endpoint, new EndpointEnvelope(Duration.ofNanos(exchangeNanos), Duration.ZERO));
        return new GroupedAdmissionProfile(costs, "b".repeat(64), "live-v5");
    }

    @Test void twentyMatchesFitTheCandidateThreeRoundBoundAndAllPhaseReplays() {
        // 20 * (3 * (1 + 1.00 + 0.90 + 0.85) + 0.70) = 239 s / 270 usable.
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV5(candidateProfile())).isEqualTo(20);
        assertThat(GroupedLiveAdmissionSimulation.fits(20, candidateProfile())).isTrue();
    }

    @Test void exactNanosecondBoundaryDoesNotHideTheFirstOverrun() {
        // The production phase replay is tighter than the mean-load boundary: keep both controls.
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV5(uniform(1_042_553_191L))).isEqualTo(20);
        assertThat(GroupedLiveAdmissionSimulation.fits(20, uniform(1_042_553_191L))).isTrue();
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV5(uniform(1_042_553_192L))).isEqualTo(19);
        assertThat(GroupedLiveAdmissionSimulation.fits(20, uniform(1_042_553_192L))).isFalse();
        // 20 * (three pauses + ten exchanges of 1.05 s) = 270 s, but replay already refuses twenty.
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV5(uniform(1_050_000_000L))).isEqualTo(19);
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV5(uniform(1))).isEqualTo(20);
    }

    @Test void startupWaveCanReduceTheWeightedSteadyCapacity() {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(uniform(200_000_000L).endpointEnvelopes());
        costs.put(EVENT_LINEUPS, new EndpointEnvelope(Duration.ofSeconds(8), Duration.ZERO));
        // The steady bound alone allows twenty; twenty initial lineups cannot fit the nominal deadlines.
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV5(new GroupedAdmissionProfile(costs, "b".repeat(64), "live-v5")))
                .isLessThan(20);
    }

    @Test void historicalEvidenceNeverQualifiesTheNewPolicyOrViceVersa() {
        var old = new GroupedAdmissionProfile(candidateProfile().endpointEnvelopes(), "a".repeat(64));
        assertThatThrownBy(() -> LiveAdmissionPolicy.qualifiedCapacityV5(old)).hasMessage("LIVE_GROUPED_POLICY_MISMATCH");
        assertThatThrownBy(() -> LiveAdmissionPolicy.qualifiedCapacityV4(candidateProfile())).hasMessage("LIVE_GROUPED_POLICY_MISMATCH");
    }

    @Test void configuredCeilingAndTwentyMatchLimitRemainHardBounds() {
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(15);
        var admission = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        admission.admitV5(15, candidateProfile());
        assertThatThrownBy(() -> admission.admitV5(16, candidateProfile())).hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        properties.setQualifiedMatchCapacity(100);
        assertThatThrownBy(() -> admission.admitV5(21, candidateProfile())).hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
    }

    @Test void byteAllowanceRemainsIndependentOfTheTwentyThousandCallBudget() {
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(20);
        long required = 2 * LiveAdmissionPolicy.V5_MAXIMUM_RAW_BYTES + properties.getDiskReserveBytes();
        var admission = new LiveAdmissionPolicy(properties, () -> required);
        assertThat(admission.maximumBytesV5(1)).isEqualTo(15_728_640_000L);
        assertThat(admission.maximumBytesV5(20)).isEqualTo(15_728_640_000L);
        admission.admitV5(20, candidateProfile());
        assertThatThrownBy(() -> new LiveAdmissionPolicy(properties, () -> required - 1).admitV5(20, candidateProfile()))
                .hasMessage("LIVE_STORAGE_CAPACITY_REFUSED");
        assertThat(admission.maximumBytes(1)).isEqualTo(5_242_880_000L);
    }

    @Test void reportedRateUsesTheV5PeriodWithoutChangingV4() {
        assertThat(LiveAdmissionPolicy.estimatedLiveCallsPerMinuteV5(15)).isEqualTo(30);
        assertThat(LiveAdmissionPolicy.estimatedLiveCallsPerMinuteV5(20)).isEqualTo(40);
        assertThat(LiveAdmissionPolicy.estimatedLiveCallsPerMinuteV4(10)).isEqualTo(32);
        assertThatThrownBy(() -> LiveAdmissionPolicy.estimatedLiveCallsPerMinuteV5(21)).isInstanceOf(IllegalArgumentException.class);
    }
}
