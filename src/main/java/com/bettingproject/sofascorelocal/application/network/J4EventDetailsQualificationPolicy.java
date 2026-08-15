package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J4EventDetailsQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class J4EventDetailsQualificationPolicy {

    private final SofascoreProperties properties;

    public J4EventDetailsQualificationPolicy(SofascoreProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
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
        if (properties.isJ3QualificationEnabled()) {
            blockers.add("J3_QUALIFICATION_MUST_BE_DISABLED");
        }
        if (!properties.getAllowedEndpoints().equals(Set.of(SofascoreEndpointType.EVENT_DETAILS))) {
            blockers.add("EVENT_DETAILS_NOT_EXCLUSIVELY_ALLOWED");
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
