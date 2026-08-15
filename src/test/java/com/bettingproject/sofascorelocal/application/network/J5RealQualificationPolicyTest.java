package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class J5RealQualificationPolicyTest {

    @Test
    void acceptsOnlyTheDedicatedFutureJ5Configuration() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN + "/");
        properties.setAllowedEndpoints(J5EventDataProviderRequest.ALLOWED_ENDPOINTS);

        var snapshot = new J5RealQualificationPolicy(properties).snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.providerOrigin().toString())
                .isEqualTo(EventDetailsProviderRequest.EXPECTED_ORIGIN + "/");
        assertThat(properties.isJ5EventDataQualificationConfigurationSafe()).isTrue();
    }

    @Test
    void blocksThePriorJ4ConfigurationAndAnyPartialJ5EndpointSelection() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        properties.setAllowedEndpoints(Set.of(SofascoreEndpointType.EVENT_DETAILS));

        var snapshot = new J5RealQualificationPolicy(properties).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.blockers()).contains(
                "J5_EVENT_DATA_QUALIFICATION_DISABLED",
                "J4_EVENT_DETAILS_QUALIFICATION_MUST_BE_DISABLED",
                "J4_EVENT_DETAILS_PHASE_2_MUST_BE_DISABLED",
                "J5_ENDPOINTS_NOT_EXACTLY_ALLOWED");

        properties.setJ4EventDetailsQualificationEnabled(false);
        properties.setJ4EventDetailsPhase2Enabled(false);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setAllowedEndpoints(Set.of(SofascoreEndpointType.EVENT_STATISTICS));
        assertThat(properties.isJ5EventDataQualificationConfigurationSafe()).isFalse();
    }
}
