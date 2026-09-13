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

class GroupedLiveAdmissionPolicyV4Test {
    private GroupedAdmissionProfile profile(Duration exchange) {
        var map = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var endpoint : new SofascoreEndpointType[] {EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS})
            map.put(endpoint, new EndpointEnvelope(exchange, Duration.ZERO));
        return new GroupedAdmissionProfile(map, "a".repeat(64));
    }

    @Test void theExistingTwoSecondEndpointCostDoesNotQualifyTenMatches() {
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(10);
        var policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        // The weighted steady bound alone would allow five. Variable endpoint durations
        // can still move statistics receipts from 60 to 66 seconds, above P95 65.
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV4(profile(Duration.ofSeconds(2)))).isZero();
        assertThatThrownBy(() -> policy.admitV4(10, profile(Duration.ofSeconds(2))))
                .hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        assertThatThrownBy(() -> policy.admitV4(1, profile(Duration.ofSeconds(2))))
                .hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
    }

    @Test void tenPercentSteadyHeadroomUsesExactNanosecondsAndKeepsTheWaveReplay() {
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV4(profile(Duration.ofMillis(750)))).isEqualTo(10);
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV4(profile(Duration.ofMillis(750).plusNanos(1)))).isEqualTo(9);
        // The weighted bound would allow ten, but the full initial wave must still
        // deliver each family within its separately checked nominal delay limit.
        var maps = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(profile(Duration.ofMillis(200)).endpointEnvelopes());
        maps.put(EVENT_LINEUPS, new EndpointEnvelope(Duration.ofSeconds(8), Duration.ZERO));
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV4(new GroupedAdmissionProfile(maps, "a".repeat(64)))).isEqualTo(5);
    }

    @Test void sevenHundredMillisecondsQualifiesTenWithoutChargingEveryMinuteForTheInitialWave() {
        // Steady: 32 calls * 0.7 s + 10 pauses * 3 s = 52.4 s, below 54 s.
        // Initial: 40 calls * 0.7 s + 10 pauses * 3 s = 58 s. This one-off wave
        // fits the six-second phases and must not be charged to every live minute.
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV4(profile(Duration.ofMillis(700)))).isEqualTo(10);
    }

    @Test void measuredCapacityDoesNotOverrideTheConfiguredMaximumOrStorage() {
        var properties = new LiveCampaignProperties();
        var policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        assertThatThrownBy(() -> policy.admitV4(2, profile(Duration.ofMillis(100))))
                .hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        properties.setQualifiedMatchCapacity(10);
        assertThatThrownBy(() -> new LiveAdmissionPolicy(properties, () -> 0).admitV4(10, profile(Duration.ofMillis(100))))
                .hasMessage("LIVE_STORAGE_CAPACITY_REFUSED");
        assertThat(LiveAdmissionPolicy.estimatedLiveCallsPerMinuteV4(10)).isEqualTo(32);
    }
}
