package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TournamentEventDiscoveryQualificationPolicyTest {

    @Test
    void acceptsTheExactDedicatedConfiguration() {
        SofascoreProperties properties = configured();

        var snapshot = new TournamentEventDiscoveryQualificationPolicy(properties).snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.providerOrigin().toString())
                .isEqualTo(EventDetailsProviderRequest.EXPECTED_ORIGIN + "/");
        assertThat(properties.isTournamentEventDiscoveryConfigurationSafe()).isTrue();
    }

    @Test
    void blocksTheDefaultAndAnExpandedEndpointUnion() {
        SofascoreProperties defaults = new SofascoreProperties();

        assertThat(new TournamentEventDiscoveryQualificationPolicy(defaults)
                .snapshot().blockers())
                .contains(
                        "TOURNAMENT_EVENT_DISCOVERY_DISABLED",
                        "J3_QUALIFICATION_DISABLED",
                        "CONNECTOR_DISABLED");

        SofascoreProperties expanded = configured();
        expanded.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS));
        assertThat(new TournamentEventDiscoveryQualificationPolicy(expanded)
                .snapshot().blockers())
                .contains("QUALIFICATION_ENDPOINTS_NOT_EXACTLY_ALLOWED");
    }

    private static SofascoreProperties configured() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setTournamentEventDiscoveryEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN + "/");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS));
        return properties;
    }
}
