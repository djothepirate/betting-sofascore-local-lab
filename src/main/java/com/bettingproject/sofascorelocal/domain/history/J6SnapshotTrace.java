package com.bettingproject.sofascorelocal.domain.history;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record J6SnapshotTrace(
        long snapshotId,
        int occurrenceCount,
        int deduplicatedOccurrenceCount,
        J6SnapshotOccurrenceOutcome latestOutcome,
        Optional<Instant> latestReceivedAt,
        J6RawPayloadState rawPayloadState) {

    public J6SnapshotTrace {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        if (occurrenceCount < 1
                || deduplicatedOccurrenceCount < 0
                || deduplicatedOccurrenceCount > occurrenceCount) {
            throw new IllegalArgumentException("snapshot occurrence counts are inconsistent");
        }
        latestOutcome = Objects.requireNonNull(latestOutcome, "latestOutcome");
        latestReceivedAt = Objects.requireNonNull(latestReceivedAt, "latestReceivedAt");
        rawPayloadState = Objects.requireNonNull(rawPayloadState, "rawPayloadState");
    }

    public boolean hasTechnicalDuplicates() {
        return deduplicatedOccurrenceCount > 0;
    }
}
