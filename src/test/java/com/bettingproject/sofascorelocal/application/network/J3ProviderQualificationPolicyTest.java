package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class J3ProviderQualificationPolicyTest {

    @Test
    void staysBlockedByDefaultWithNoProviderOriginExposed() {
        var snapshot = new J3ProviderQualificationPolicy(
                new SofascoreProperties()).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.providerOrigin()).isNull();
        assertThat(snapshot.blockers()).contains(
                "J3_QUALIFICATION_DISABLED",
                "CONNECTOR_DISABLED",
                "PROVIDER_ORIGIN_NOT_EXACT");
    }

    @Test
    void opensOnlyForTheExactFourPartOptIn() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));

        var snapshot = new J3ProviderQualificationPolicy(properties).snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.providerOrigin()).hasToString("https://www.sofascore.com");
        assertThat(snapshot.blockers()).isEmpty();
    }

    @Test
    void rejectsAPathPortOrAdditionalLogicalFamily() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com/api");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS));

        var snapshot = new J3ProviderQualificationPolicy(properties).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.blockers()).containsExactly(
                "QUALIFICATION_ENDPOINTS_NOT_EXACTLY_ALLOWED",
                "PROVIDER_ORIGIN_NOT_EXACT");
    }

    @Test
    void acceptsJ3InsideTheExactJ3J4PhaseTwoAndJ5EndpointUnion() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS));

        var snapshot = new J3ProviderQualificationPolicy(properties).snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(properties.isJ3QualificationConfigurationSafe()).isTrue();
        assertThat(properties.isCombinedJ3J4SelectionSafe()).isTrue();
    }

    @Test
    void blocksJ4PhaseOneFromSharingTheJ3SessionEvenWithAnExactUnion() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS));

        assertThat(new J3ProviderQualificationPolicy(properties).snapshot().blockers())
                .contains("J4_PHASE_1_CANNOT_SHARE_J3_SESSION");
    }
}
