package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class J5OfflineBatchPolicyTest {

    @Test
    void allowsTheCombinedProviderConfigurationRegardlessOfConnectorState() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setTournamentEventDiscoveryEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS));
        J5OfflineBatchPolicy policy = new J5OfflineBatchPolicy(properties);

        assertThat(policy.snapshot().available()).isTrue();
        assertThat(policy.snapshot().blockers()).isEmpty();

        properties.setEnabled(false);

        assertThat(policy.snapshot().available()).isTrue();
        assertThat(policy.snapshot().blockers()).isEmpty();
    }

    @Test
    void keepsTheGlobalAutomationAndRawStorageGuards() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setTournamentEventDiscoveryEnabled(true);
        properties.setAutomaticRefreshEnabled(true);
        properties.setLivePollingEnabled(true);
        properties.setStoreRawPayloads(false);

        assertThat(new J5OfflineBatchPolicy(properties).snapshot().blockers())
                .containsExactly(
                        "AUTOMATIC_REFRESH_MUST_BE_FALSE",
                        "LIVE_POLLING_MUST_BE_FALSE",
                        "RAW_STORAGE_MUST_BE_TRUE");
    }
}
