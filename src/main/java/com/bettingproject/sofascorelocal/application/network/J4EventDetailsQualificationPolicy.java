package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J4EventDetailsQualificationSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class J4EventDetailsQualificationPolicy {

    private final SofascoreProperties properties;
    private final ProviderPlaywrightProperties playwrightProperties;

    @Autowired
    public J4EventDetailsQualificationPolicy(
            SofascoreProperties properties,
            ObjectProvider<ProviderPlaywrightProperties> playwrightProperties) {
        this(properties, playwrightProperties.getIfAvailable(ProviderPlaywrightProperties::new));
    }

    public J4EventDetailsQualificationPolicy(
            SofascoreProperties properties,
            ProviderPlaywrightProperties playwrightProperties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.playwrightProperties = Objects.requireNonNull(
                playwrightProperties, "playwrightProperties");
    }

    public J4EventDetailsQualificationPolicy(SofascoreProperties properties) {
        this(properties, new ProviderPlaywrightProperties());
    }

    public J4EventDetailsQualificationSnapshot snapshot() {
        List<String> blockers = new ArrayList<>();
        if (!properties.isJ4EventDetailsQualificationEnabled()) {
            blockers.add("J4_EVENT_DETAILS_QUALIFICATION_DISABLED");
        }
        if (properties.isJ4EventDetailsPhase2Enabled()) {
            blockers.add("J4_PHASE_2_MUST_BE_DISABLED");
        }
        if (!properties.isEnabled()) {
            blockers.add("CONNECTOR_DISABLED");
        }
        ProviderPlaywrightQualificationGuard.appendBlockers(
                playwrightProperties, blockers);
        if (properties.isJ3QualificationEnabled()) {
            blockers.add("J3_QUALIFICATION_MUST_BE_DISABLED");
        }
        if (properties.isJ5EventDataQualificationEnabled()) {
            blockers.add("J5_MUST_BE_DISABLED_FOR_J4_PHASE_1");
        }
        if (!properties.hasExactActiveQualificationEndpoints()) {
            blockers.add("QUALIFICATION_ENDPOINTS_NOT_EXACTLY_ALLOWED");
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
            providerOrigin = EventDetailsProviderRequest.parseExactProviderOrigin(
                    properties.getBaseUrl());
        }
        catch (IllegalArgumentException | NullPointerException exception) {
            blockers.add("PROVIDER_ORIGIN_NOT_EXACT");
        }

        return blockers.isEmpty()
                ? J4EventDetailsQualificationSnapshot.available(providerOrigin)
                : J4EventDetailsQualificationSnapshot.blocked(List.copyOf(blockers));
    }
}
