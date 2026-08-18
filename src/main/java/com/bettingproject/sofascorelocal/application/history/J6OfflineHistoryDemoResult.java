package com.bettingproject.sofascorelocal.application.history;

import java.util.Objects;
import java.util.UUID;

public record J6OfflineHistoryDemoResult(
        UUID canonicalEventId,
        int attemptedObservations,
        int insertedObservations,
        int deduplicatedObservations) {

    public J6OfflineHistoryDemoResult {
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        if (attemptedObservations < 1
                || insertedObservations < 0
                || deduplicatedObservations < 0
                || insertedObservations + deduplicatedObservations
                        != attemptedObservations) {
            throw new IllegalArgumentException("J6 demo counters are inconsistent");
        }
    }
}
