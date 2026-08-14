package com.bettingproject.sofascorelocal.domain.event;

import java.util.Objects;
import java.util.UUID;

public record EventObservationPersistenceResult(
        long observationId,
        UUID canonicalEventId,
        long observationCount,
        boolean inserted) {

    public EventObservationPersistenceResult {
        if (observationId < 1 || observationCount < 1) {
            throw new IllegalArgumentException("observation identifiers and count must be positive");
        }
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
    }
}
