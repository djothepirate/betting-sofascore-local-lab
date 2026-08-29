package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class J4EventDetailsPhase2QualificationPolicyTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void phaseTwoRequiresItsDistinctOptInAndBlocksPhaseOneWhenSelected()
            throws Exception {
        SofascoreProperties properties = safeJ4Properties();
        var playwright = ProviderPlaywrightPolicyTestSupport.configured(
                temporaryDirectory, "j4-worker.jar");
        var phase1 = new J4EventDetailsQualificationPolicy(properties, playwright);
        var phase2 = new J4EventDetailsPhase2QualificationPolicy(properties, playwright);

        assertThat(phase1.snapshot().available()).isTrue();
        assertThat(phase2.snapshot().available()).isFalse();
        assertThat(phase2.snapshot().blockers())
                .contains("J4_EVENT_DETAILS_PHASE_2_DISABLED");

        properties.setJ4EventDetailsPhase2Enabled(true);

        assertThat(phase1.snapshot().available()).isFalse();
        assertThat(phase1.snapshot().blockers()).contains("J4_PHASE_2_MUST_BE_DISABLED");
        assertThat(phase2.snapshot().available()).isTrue();
        assertThat(phase2.snapshot().providerOrigin().toString())
                .isEqualTo(EventDetailsProviderRequest.EXPECTED_ORIGIN);
    }

    @Test
    void phaseTwoRemainsAvailableInTheExactCombinedJ3J4J5Session() throws Exception {
        SofascoreProperties properties = safeJ4Properties();
        properties.setJ3QualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS));

        var snapshot = new J4EventDetailsPhase2QualificationPolicy(
                properties,
                ProviderPlaywrightPolicyTestSupport.configured(
                        temporaryDirectory, "combined-worker.jar"))
                .snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(properties.isJ4EventDetailsQualificationConfigurationSafe()).isTrue();
    }

    @Test
    void remainsBlockedUntilThePlaywrightRuntimeAndWorkerAreExplicitlyAvailable() {
        SofascoreProperties properties = safeJ4Properties();

        var snapshot = new J4EventDetailsQualificationPolicy(properties).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.blockers()).contains(
                "PLAYWRIGHT_RUNTIME_DISABLED",
                "PLAYWRIGHT_WORKER_ARTIFACT_INVALID");
    }

    private static SofascoreProperties safeJ4Properties() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        properties.setAllowedEndpoints(Set.of(SofascoreEndpointType.EVENT_DETAILS));
        return properties;
    }
}
