package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.Objects;
import java.util.UUID;

public record J5EventDataPersistenceResult(
        long observationId,
        UUID canonicalEventId,
        SofascoreEndpointType endpointType,
        boolean inserted) {

    public J5EventDataPersistenceResult {
        if (observationId < 1) {
            throw new IllegalArgumentException("observationId must be positive");
        }
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        endpointType = Objects.requireNonNull(endpointType, "endpointType");
    }
}
