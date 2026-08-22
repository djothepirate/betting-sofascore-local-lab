package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.application.network.J5RealEndpointResult;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J5OfflineBatchEventResult(
        UUID canonicalEventId,
        long providerEventId,
        String homeTeamName,
        String awayTeamName,
        List<J5RealEndpointResult> endpoints) {

    public J5OfflineBatchEventResult {
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        homeTeamName = Objects.requireNonNull(homeTeamName, "homeTeamName");
        awayTeamName = Objects.requireNonNull(awayTeamName, "awayTeamName");
        endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
        if (endpoints.size() != 3) {
            throw new IllegalArgumentException("a completed event requires three endpoint results");
        }
    }
}
