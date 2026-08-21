package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryQualificationSnapshot;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class TournamentEventDiscoveryQualificationPolicy {

    private final SofascoreProperties properties;

    public TournamentEventDiscoveryQualificationPolicy(SofascoreProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public TournamentEventDiscoveryQualificationSnapshot snapshot() {
        List<String> blockers = new ArrayList<>();
        if (!properties.isTournamentEventDiscoveryEnabled()) {
            blockers.add("TOURNAMENT_EVENT_DISCOVERY_DISABLED");
        }
        if (!properties.isJ3QualificationEnabled()) {
            blockers.add("J3_QUALIFICATION_DISABLED");
        }
        if (!properties.isEnabled()) {
            blockers.add("CONNECTOR_DISABLED");
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
                ? TournamentEventDiscoveryQualificationSnapshot.available(origin)
                : TournamentEventDiscoveryQualificationSnapshot.blocked(List.copyOf(blockers));
    }
}
