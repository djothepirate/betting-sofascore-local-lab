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

class GroupedLiveAdmissionPolicyV7Test {
    private static GroupedAdmissionProfile profile() {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var family : new SofascoreEndpointType[]{EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS})
            costs.put(family, new EndpointEnvelope(Duration.ofMillis(500), Duration.ofMillis(100)));
        return new GroupedAdmissionProfile(costs, "d".repeat(64), "live-v7");
    }

    @Test void threeMatchesFitAllKickoffWindowsWithHourlyHeadroomAndFourFamiliesPerMinute() {
        assertThat(3 * (240 + 4 + 4)).isEqualTo(744).isLessThanOrEqualTo(900);
        assertThat(4 * (240 + 4 + 4)).isEqualTo(992).isGreaterThan(900);
        assertThat(GroupedLiveAdmissionSimulationV7.SCENARIOS).isEqualTo(16);
        assertThat(GroupedLiveAdmissionSimulationV7.fits(3, profile())).isTrue();
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV7(profile())).isEqualTo(3);
        assertThat(LiveAdmissionPolicy.estimatedLiveCallsPerMinuteV7(3)).isEqualTo(12);
        assertThat(profile().criticalInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile().lineupInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile().minimumRequestStartInterval()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test void oldEvidenceCannotAuthorizeV7OrGainItsPrematchException() {
        assertThatThrownBy(() -> LiveAdmissionPolicy.qualifiedCapacityV7(null)).hasMessage("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
        var v6 = new GroupedAdmissionProfile(profile().endpointEnvelopes(), "e".repeat(64), "live-v6");
        assertThatThrownBy(() -> LiveAdmissionPolicy.qualifiedCapacityV7(v6)).hasMessage("LIVE_GROUPED_POLICY_MISMATCH");
        assertThatThrownBy(() -> LiveAdmissionPolicy.qualifiedCapacityV6(profile())).hasMessage("LIVE_GROUPED_POLICY_MISMATCH");
        assertThat(v6.criticalInterval()).isEqualTo(Duration.ofSeconds(100));
        assertThat(v6.lineupInterval()).isEqualTo(Duration.ofSeconds(300));
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV6(v6)).isEqualTo(7);
        var properties = new LiveCampaignProperties();
        properties.getGroupedV6().setQualificationSha256("e".repeat(64));
        assertThatThrownBy(properties::groupedAdmissionProfileV7).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
    }

    @Test void fourthMatchAndOperatorCeilingAreRejectedBeforeStorageWhileByteCapRemainsMandatory() {
        var properties = new LiveCampaignProperties(); properties.setQualifiedMatchCapacity(20);
        var probes = new AtomicInteger();
        var admission = new LiveAdmissionPolicy(properties, () -> { probes.incrementAndGet(); return Long.MAX_VALUE; });
        assertThatThrownBy(() -> admission.admitV7(4, profile())).hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        assertThat(probes).hasValue(0);
        properties.setQualifiedMatchCapacity(2);
        assertThatThrownBy(() -> admission.admitV7(3, profile())).hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        assertThat(probes).hasValue(0);
        properties.setQualifiedMatchCapacity(3);
        long required = 2 * LiveAdmissionPolicy.V5_MAXIMUM_RAW_BYTES + properties.getDiskReserveBytes();
        new LiveAdmissionPolicy(properties, () -> required).admitV7(3, profile());
        assertThatThrownBy(() -> new LiveAdmissionPolicy(properties, () -> required - 1).admitV7(3, profile()))
                .hasMessage("LIVE_STORAGE_CAPACITY_REFUSED");
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(profile().endpointEnvelopes());
        costs.replaceAll((family, ignored) -> new EndpointEnvelope(Duration.ofSeconds(10), Duration.ofSeconds(10)));
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV7(new GroupedAdmissionProfile(costs, "d".repeat(64), "live-v7"))).isZero();
    }
}
