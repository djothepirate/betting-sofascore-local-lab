package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.J3ProviderQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class J3ProviderQualificationPolicy {

    private final SofascoreProperties properties;
    private final ProviderPlaywrightProperties playwrightProperties;

    @Autowired
    public J3ProviderQualificationPolicy(
            SofascoreProperties properties,
            ObjectProvider<ProviderPlaywrightProperties> playwrightProperties) {
        this(properties, playwrightProperties.getIfAvailable(ProviderPlaywrightProperties::new));
    }

    public J3ProviderQualificationPolicy(
            SofascoreProperties properties,
            ProviderPlaywrightProperties playwrightProperties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.playwrightProperties = Objects.requireNonNull(
                playwrightProperties, "playwrightProperties");
    }

    public J3ProviderQualificationPolicy(SofascoreProperties properties) {
        this(properties, new ProviderPlaywrightProperties());
    }

    public J3ProviderQualificationSnapshot snapshot() {
        return snapshot(true);
    }

    J3ProviderQualificationSnapshot localImportSnapshot() {
        return snapshot(false);
    }

    private J3ProviderQualificationSnapshot snapshot(boolean playwrightRequired) {
        List<String> blockers = new ArrayList<>();
        if (!properties.isJ3QualificationEnabled()) {
            blockers.add("J3_QUALIFICATION_DISABLED");
        }
        if (!properties.isEnabled()) {
            blockers.add("CONNECTOR_DISABLED");
        }
        if (playwrightRequired) {
            ProviderPlaywrightQualificationGuard.appendBlockers(
                    playwrightProperties, blockers);
        }
        if (!properties.hasExactActiveQualificationEndpoints()) {
            blockers.add("QUALIFICATION_ENDPOINTS_NOT_EXACTLY_ALLOWED");
        }
        if (properties.isJ4EventDetailsQualificationEnabled()
                && !properties.isJ4EventDetailsPhase2Enabled()) {
            blockers.add("J4_PHASE_1_CANNOT_SHARE_J3_SESSION");
        }
        if (!properties.isStoreRawPayloads()) {
            blockers.add("RAW_SNAPSHOT_STORAGE_DISABLED");
        }
        if (properties.getMaximumConcurrency() != 1) {
            blockers.add("MAXIMUM_CONCURRENCY_NOT_ONE");
        }
        if (properties.isAutomaticRefreshEnabled() || properties.isLivePollingEnabled()) {
            blockers.add("AUTOMATIC_NETWORK_ACTIVITY_ENABLED");
        }

        URI providerOrigin = null;
        try {
            providerOrigin = ScheduledEventsProviderPageRequest.parseExactProviderOrigin(
                    properties.getBaseUrl());
        }
        catch (IllegalArgumentException | NullPointerException exception) {
            blockers.add("PROVIDER_ORIGIN_NOT_EXACT");
        }

        return blockers.isEmpty()
                ? J3ProviderQualificationSnapshot.available(providerOrigin)
                : J3ProviderQualificationSnapshot.blocked(List.copyOf(blockers));
    }
}
