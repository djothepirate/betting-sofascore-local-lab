package com.bettingproject.sofascorelocal.domain.eventdetails;

import java.util.Objects;
import java.util.UUID;

public record EventDetailPersistenceResult(
        long observationId,
        UUID canonicalEventId,
        boolean inserted) {

    public EventDetailPersistenceResult {
        if (observationId < 1) {
            throw new IllegalArgumentException("observationId must be positive");
        }
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
    }
}
