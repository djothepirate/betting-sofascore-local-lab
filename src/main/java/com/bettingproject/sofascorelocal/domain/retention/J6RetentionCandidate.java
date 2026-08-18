package com.bettingproject.sofascorelocal.domain.retention;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record J6RetentionCandidate(
        long snapshotId,
        Instant receivedAt,
        String logicalEndpoint,
        RawSnapshotSchemaStatus schemaStatus,
        long payloadSizeBytes,
        String payloadSha256) {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Z0-9_]+");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J6RetentionCandidate {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        logicalEndpoint = Objects.requireNonNull(logicalEndpoint, "logicalEndpoint").trim();
        schemaStatus = Objects.requireNonNull(schemaStatus, "schemaStatus");
        payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256").trim();
        if (!SAFE_IDENTIFIER.matcher(logicalEndpoint).matches()) {
            throw new IllegalArgumentException("logicalEndpoint must be a safe identifier");
        }
        if (schemaStatus != RawSnapshotSchemaStatus.PARSED
                && schemaStatus != RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE) {
            throw new IllegalArgumentException(
                    "only normalized parsed or unavailable snapshots are retention candidates");
        }
        if (payloadSizeBytes < 0 || payloadSizeBytes > RawPayloadEvidence.MAXIMUM_BYTES) {
            throw new IllegalArgumentException("payloadSizeBytes exceeds the raw payload bound");
        }
        if (!SHA_256.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be a lower-case SHA-256");
        }
    }
}
