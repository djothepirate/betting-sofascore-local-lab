package com.bettingproject.sofascorelocal.domain.history;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record J6SnapshotTrace(
        long snapshotId,
        String payloadSha256,
        int occurrenceCount,
        int deduplicatedOccurrenceCount,
        J6SnapshotOccurrenceOutcome latestOutcome,
        Optional<Instant> latestReceivedAt,
        J6RawPayloadState rawPayloadState) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public J6SnapshotTrace {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256");
        if (!SHA_256_PATTERN.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be a lower-case SHA-256");
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
