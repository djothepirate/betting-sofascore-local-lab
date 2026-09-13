package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

class GroupedLiveAdmissionPolicyV6Test {
    static GroupedAdmissionProfile profile() {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var family : new SofascoreEndpointType[]{EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS})
            costs.put(family, new EndpointEnvelope(Duration.ofMillis(400), Duration.ofMillis(100)));
        // Test-only envelope and digest: actual activation requires its separate local qualification.
        return new GroupedAdmissionProfile(costs, "d".repeat(64), "live-v6");
    }

    @Test void sevenFitsTheCompletionSpacedReplayAndIncludesInitialAndFinalHourlyHeadroom() {
        assertThat(7 * (120 + 4 + 4)).isEqualTo(896).isLessThanOrEqualTo(900);
        assertThat(8 * (120 + 4 + 4)).isGreaterThan(1000);
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV6(profile())).isEqualTo(7);
        assertThat(GroupedLiveAdmissionSimulation.fits(7, profile())).isTrue();
        assertThat(LiveAdmissionPolicy.estimatedLiveCallsPerMinuteV6(7)).isEqualTo(14);
    }

    @Test void refusalOfAnEighthEventOrConfiguredCeilingHappensBeforeAnyStorageProbe() {
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(20);
        AtomicInteger probes = new AtomicInteger();
        var admission = new LiveAdmissionPolicy(properties, () -> { probes.incrementAndGet(); return Long.MAX_VALUE; });
        assertThatThrownBy(() -> admission.admitV6(8, profile())).hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        assertThat(probes).hasValue(0);
        properties.setQualifiedMatchCapacity(3);
        assertThatThrownBy(() -> admission.admitV6(4, profile())).hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        assertThat(probes).hasValue(0);
        admission.admitV6(3, profile());
        assertThat(probes).hasValue(1);
    }

    @Test void aNewProofIsRequiredAndHistoricalProfilesNeverGainTheV6Policy() {
        assertThatThrownBy(() -> LiveAdmissionPolicy.qualifiedCapacityV6(null)).hasMessage("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
        assertThatThrownBy(() -> LiveAdmissionPolicy.qualifiedCapacityV6(
                new GroupedAdmissionProfile(profile().endpointEnvelopes(), "a".repeat(64), "live-v5")))
                .hasMessage("LIVE_GROUPED_POLICY_MISMATCH");
        assertThatThrownBy(() -> LiveAdmissionPolicy.qualifiedCapacityV5(profile())).hasMessage("LIVE_GROUPED_POLICY_MISMATCH");
        assertThat(profile().minimumRequestStartInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(profile().intraGroupDelay()).isZero();
        assertThat(profile().interGroupDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(new GroupedAdmissionProfile(profile().endpointEnvelopes(), "a".repeat(64), "live-v5")
                .minimumRequestStartInterval()).isZero();
    }

    @Test void slowerLocalWorkCanFurtherReduceCapacityAndByteHeadroomRemainsMandatory() {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(profile().endpointEnvelopes());
        costs.put(EVENT_LINEUPS, new EndpointEnvelope(Duration.ofSeconds(10), Duration.ofSeconds(10)));
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV6(new GroupedAdmissionProfile(costs, "d".repeat(64), "live-v6")))
                .isLessThan(7);
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(7);
        long required = 2 * LiveAdmissionPolicy.V5_MAXIMUM_RAW_BYTES + properties.getDiskReserveBytes();
        new LiveAdmissionPolicy(properties, () -> required).admitV6(7, profile());
        assertThatThrownBy(() -> new LiveAdmissionPolicy(properties, () -> required - 1).admitV6(7, profile()))
                .hasMessage("LIVE_STORAGE_CAPACITY_REFUSED");
    }
}
