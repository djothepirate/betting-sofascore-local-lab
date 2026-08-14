package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.J3ProviderQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class J3ProviderQualificationPolicy {

    private final SofascoreProperties properties;

    public J3ProviderQualificationPolicy(SofascoreProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public J3ProviderQualificationSnapshot snapshot() {
        List<String> blockers = new ArrayList<>();
        if (!properties.isJ3QualificationEnabled()) {
            blockers.add("J3_QUALIFICATION_DISABLED");
        }
        if (!properties.isEnabled()) {
            blockers.add("CONNECTOR_DISABLED");
        }
        if (!properties.getAllowedEndpoints().equals(
                java.util.Set.of(SofascoreEndpointType.SCHEDULED_EVENTS))) {
            blockers.add("SCHEDULED_EVENTS_NOT_EXCLUSIVELY_ALLOWED");
        }
        if (!properties.isStoreRawPayloads()) {
            blockers.add("RAW_SNAPSHOT_STORAGE_DISABLED");
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
