package com.bettingproject.sofascorelocal.application.event;

import java.util.List;
import java.util.Objects;

public record TournamentCanonicalizationResult(
        int insertedObservations,
        int deduplicatedObservations,
        List<TournamentDiscoveredEventView> events) {

    public TournamentCanonicalizationResult {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (insertedObservations < 0
                || deduplicatedObservations < 0
                || insertedObservations + deduplicatedObservations != events.size()) {
            throw new IllegalArgumentException("canonicalization counts are inconsistent");
        }
    }
}
