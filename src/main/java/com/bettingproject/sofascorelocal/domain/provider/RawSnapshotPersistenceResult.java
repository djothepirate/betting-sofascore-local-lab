package com.bettingproject.sofascorelocal.domain.provider;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;

public record RawSnapshotPersistenceResult(
        long snapshotId,
        RawSnapshotPersistenceOutcome outcome,
        String payloadSha256,
        int payloadSizeBytes,
        OptionalLong occurrenceId) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public RawSnapshotPersistenceResult {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(payloadSha256, "payloadSha256");
        if (!SHA256_PATTERN.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be lowercase SHA-256");
        }
        if (payloadSizeBytes < 0 || payloadSizeBytes > RawPayloadEvidence.MAXIMUM_BYTES) {
            throw new IllegalArgumentException("payloadSizeBytes is outside the persistence limit");
        }
        occurrenceId = Objects.requireNonNull(occurrenceId, "occurrenceId");
        if (occurrenceId.isPresent() && occurrenceId.getAsLong() < 1) {
            throw new IllegalArgumentException("occurrenceId must be positive");
        }
        if (outcome == RawSnapshotPersistenceOutcome.CACHE_HIT) {
            if (occurrenceId.isPresent()) {
                throw new IllegalArgumentException("a cache hit cannot create an occurrence");
            }
        }
        else if (occurrenceId.isEmpty()) {
            throw new IllegalArgumentException(
                    "inserted and deduplicated snapshots require their occurrenceId");
        }
    }
}
