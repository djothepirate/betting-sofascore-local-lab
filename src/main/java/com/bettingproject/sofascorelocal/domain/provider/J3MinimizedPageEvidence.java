package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Metadata-only evidence for one provider page attempt. Raw response bytes, request URIs,
 * headers and session data are deliberately absent from this model.
 */
public record J3MinimizedPageEvidence(
        int page,
        Instant requestedAt,
        Instant receivedAt,
        Integer httpStatus,
        Long latencyMillis,
        Long snapshotId,
        RawSnapshotPersistenceOutcome persistenceOutcome,
        Integer payloadSizeBytes,
        String payloadSha256,
        RawSnapshotSchemaStatus schemaStatus,
        String terminalCode) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public J3MinimizedPageEvidence {
        if (page < ScheduledEventsProviderPageRequest.FIRST_PAGE
                || page > ScheduledEventsProviderPageRequest.LAST_PAGE) {
            throw new IllegalArgumentException("page must be in the fixed qualification range");
        }
        Objects.requireNonNull(requestedAt, "requestedAt");
        terminalCode = normalizeCode(terminalCode);

        boolean recorded = snapshotId != null;
        if (recorded) {
            Objects.requireNonNull(receivedAt, "receivedAt");
            Objects.requireNonNull(httpStatus, "httpStatus");
            Objects.requireNonNull(latencyMillis, "latencyMillis");
            Objects.requireNonNull(persistenceOutcome, "persistenceOutcome");
            Objects.requireNonNull(payloadSizeBytes, "payloadSizeBytes");
            Objects.requireNonNull(payloadSha256, "payloadSha256");
            Objects.requireNonNull(schemaStatus, "schemaStatus");
            if (snapshotId < 1 || httpStatus < 100 || httpStatus > 599
                    || latencyMillis < 0 || payloadSizeBytes < 0
                    || payloadSizeBytes > RawPayloadEvidence.MAXIMUM_BYTES
                    || !SHA256_PATTERN.matcher(payloadSha256).matches()) {
                throw new IllegalArgumentException("recorded page evidence contains invalid metadata");
            }
            if (receivedAt.isBefore(requestedAt)) {
                throw new IllegalArgumentException("receivedAt cannot precede requestedAt");
            }
        }
        else if (receivedAt != null || httpStatus != null || latencyMillis != null
                || persistenceOutcome != null || payloadSizeBytes != null
                || payloadSha256 != null || schemaStatus != null) {
            throw new IllegalArgumentException(
                    "a page without a snapshot cannot expose response metadata");
        }
    }

    public static J3MinimizedPageEvidence recorded(
            int page,
            ScheduledEventsTransportResponse response,
            RawSnapshotPersistenceResult persistence,
            RawSnapshotSchemaStatus schemaStatus,
            String terminalCode) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(persistence, "persistence");
        return new J3MinimizedPageEvidence(
                page,
                response.requestedAt(),
                response.receivedAt(),
                response.httpStatus(),
                response.latency().toMillis(),
                persistence.snapshotId(),
                persistence.outcome(),
                persistence.payloadSizeBytes(),
                persistence.payloadSha256(),
                schemaStatus,
                terminalCode);
    }

    public static J3MinimizedPageEvidence failedBeforeSnapshot(
            int page,
            Instant requestedAt,
            String terminalCode) {
        return new J3MinimizedPageEvidence(
                page,
                requestedAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                terminalCode);
    }

    public boolean snapshotRecorded() {
        return snapshotId != null;
    }

    private static String normalizeCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 96
                || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("terminalCode must be a safe code");
        }
        return normalized;
    }
}
