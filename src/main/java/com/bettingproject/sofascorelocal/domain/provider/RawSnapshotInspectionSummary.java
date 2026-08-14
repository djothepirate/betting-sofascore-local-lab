package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Metadata-only description of one locally persisted raw snapshot.
 */
public record RawSnapshotInspectionSummary(
        long snapshotId,
        String logicalEndpoint,
        String requestKey,
        Instant receivedAt,
        Integer httpStatus,
        String contentType,
        long payloadSizeBytes,
        String payloadSha256,
        String parserVersion,
        RawSnapshotSchemaStatus schemaStatus) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public RawSnapshotInspectionSummary {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        logicalEndpoint = requireSafeText(logicalEndpoint, "logicalEndpoint");
        requestKey = requireSafeText(requestKey, "requestKey");
        Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = requireSafeText(contentType, "contentType");
        payloadSha256 = requireSafeText(payloadSha256, "payloadSha256");
        parserVersion = requireSafeText(parserVersion, "parserVersion");
        Objects.requireNonNull(schemaStatus, "schemaStatus");
        if (httpStatus == null || httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("httpStatus must be a valid HTTP status");
        }
        if (payloadSizeBytes < 0 || payloadSizeBytes > RawPayloadEvidence.MAXIMUM_BYTES) {
            throw new IllegalArgumentException("payloadSizeBytes exceeds the local raw limit");
        }
        if (!SHA256_PATTERN.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be a lowercase SHA-256");
        }
    }

    private static String requireSafeText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be safe non-blank text");
        }
        return normalized;
    }
}
