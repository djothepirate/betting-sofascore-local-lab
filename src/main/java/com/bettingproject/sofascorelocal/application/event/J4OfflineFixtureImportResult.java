package com.bettingproject.sofascorelocal.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record J4OfflineFixtureImportResult(
        UUID canonicalEventId,
        Instant startsAt,
        long canonicalObservationCount,
        boolean scheduledObservationInserted,
        boolean detailEventObservationInserted,
        boolean detailInserted) {

    public J4OfflineFixtureImportResult {
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
        if (canonicalObservationCount < 1) {
            throw new IllegalArgumentException("canonicalObservationCount must be positive");
        }
    }
}
