package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class J5RealQualificationPolicyTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsTheDedicatedJ5Configuration() throws Exception {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN + "/");
        properties.setAllowedEndpoints(J5EventDataProviderRequest.ALLOWED_ENDPOINTS);

        var snapshot = configuredPolicy(properties, "j5-worker.jar").snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.providerOrigin().toString())
                .isEqualTo(EventDetailsProviderRequest.EXPECTED_ORIGIN + "/");
        assertThat(properties.isJ5EventDataQualificationConfigurationSafe()).isTrue();
    }

    @Test
    void blocksDisabledJ5AndAnyPartialJ5EndpointSelection() throws Exception {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        properties.setAllowedEndpoints(Set.of(SofascoreEndpointType.EVENT_DETAILS));

        var snapshot = configuredPolicy(properties, "disabled-worker.jar").snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.blockers()).containsExactly(
                "J5_EVENT_DATA_QUALIFICATION_DISABLED");

        properties.setJ4EventDetailsQualificationEnabled(false);
        properties.setJ4EventDetailsPhase2Enabled(false);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setAllowedEndpoints(Set.of(SofascoreEndpointType.EVENT_STATISTICS));
        assertThat(properties.isJ5EventDataQualificationConfigurationSafe()).isFalse();
    }

    @Test
    void acceptsJ5AlongsideJ4PhaseTwoWithTheExactEndpointUnion() throws Exception {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS));

        var snapshot = configuredPolicy(properties, "combined-j4-j5-worker.jar").snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(properties.isJ4EventDetailsQualificationConfigurationSafe()).isTrue();
        assertThat(properties.isJ5EventDataQualificationConfigurationSafe()).isTrue();
        assertThat(properties.isCombinedJ4J5SelectionSafe()).isTrue();
    }

    @Test
    void acceptsJ5InsideTheExactCombinedJ3J4PhaseTwoAndJ5Session() throws Exception {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS));

        var snapshot = configuredPolicy(properties, "combined-j3-j4-j5-worker.jar").snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(properties.isJ5EventDataQualificationConfigurationSafe()).isTrue();
        assertThat(properties.isCombinedJ3J4SelectionSafe()).isTrue();
    }

    @Test
    void blocksJ4PhaseOneFromSharingTheJ5Session() throws Exception {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS));

        assertThat(configuredPolicy(properties, "phase-one-worker.jar").snapshot().blockers())
                .contains("J4_PHASE_1_CANNOT_SHARE_J5_SESSION");
    }

    @Test
    void remainsBlockedUntilThePlaywrightRuntimeAndWorkerAreExplicitlyAvailable() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        properties.setAllowedEndpoints(J5EventDataProviderRequest.ALLOWED_ENDPOINTS);

        var snapshot = new J5RealQualificationPolicy(properties).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.blockers()).contains(
                "PLAYWRIGHT_RUNTIME_DISABLED",
                "PLAYWRIGHT_WORKER_ARTIFACT_INVALID");
    }

    private J5RealQualificationPolicy configuredPolicy(
            SofascoreProperties properties,
            String workerName) throws Exception {
        return new J5RealQualificationPolicy(
                properties,
                ProviderPlaywrightPolicyTestSupport.configured(
                        temporaryDirectory, workerName));
    }
}
