package com.bettingproject.sofascorelocal.application.event;

import java.util.Objects;
import java.util.UUID;

public record J4ParsedEventDetailsPersistenceResult(
        UUID canonicalEventId,
        long eventObservationId,
        long detailObservationId,
        boolean eventObservationInserted,
        boolean detailObservationInserted) {

    public J4ParsedEventDetailsPersistenceResult {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        if (eventObservationId < 1 || detailObservationId < 1) {
            throw new IllegalArgumentException("observation identifiers must be positive");
        }
    }
}
