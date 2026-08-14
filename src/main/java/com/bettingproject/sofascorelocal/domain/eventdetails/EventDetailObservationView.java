package com.bettingproject.sofascorelocal.domain.eventdetails;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;

import java.util.Objects;

public record EventDetailObservationView(
        long observationId,
        CanonicalEventIdentity identity,
        EventDetails details,
        EventSourceTrace source,
        String normalizedSha256) {

    public EventDetailObservationView {
        if (observationId < 1) {
            throw new IllegalArgumentException("observationId must be positive");
        }
        identity = Objects.requireNonNull(identity, "identity");
        details = Objects.requireNonNull(details, "details");
        source = Objects.requireNonNull(source, "source");
        normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
    }
}
