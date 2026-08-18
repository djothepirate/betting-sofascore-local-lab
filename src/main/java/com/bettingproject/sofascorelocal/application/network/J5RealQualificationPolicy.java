package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5RealQualificationSnapshot;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class J5RealQualificationPolicy {

    private final SofascoreProperties properties;

    public J5RealQualificationPolicy(SofascoreProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public J5RealQualificationSnapshot snapshot() {
        List<String> blockers = new ArrayList<>();
        if (!properties.isJ5EventDataQualificationEnabled()) {
            blockers.add("J5_EVENT_DATA_QUALIFICATION_DISABLED");
        }
        if (!properties.isEnabled()) {
            blockers.add("CONNECTOR_DISABLED");
        }
        if (properties.isJ3QualificationEnabled()) {
            blockers.add("J3_QUALIFICATION_MUST_BE_DISABLED");
        }
        if (properties.isJ4EventDetailsQualificationEnabled()
                && !properties.isJ4EventDetailsPhase2Enabled()) {
            blockers.add("J4_PHASE_1_CANNOT_SHARE_J5_SESSION");
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
        URI origin = null;
        try {
            origin = EventDetailsProviderRequest.parseExactProviderOrigin(
                    properties.getBaseUrl());
        }
        catch (IllegalArgumentException | NullPointerException exception) {
            blockers.add("PROVIDER_ORIGIN_NOT_EXACT");
        }
        return blockers.isEmpty()
                ? J5RealQualificationSnapshot.available(origin)
                : J5RealQualificationSnapshot.blocked(List.copyOf(blockers));
    }
}
