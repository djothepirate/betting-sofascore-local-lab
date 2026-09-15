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
        J3PageResolutionSource resolutionSource,
        boolean providerRequestExecuted,
        Instant resolvedAt,
        Instant cacheStoredAt,
        Instant requestedAt,
        Instant receivedAt,
        Integer httpStatus,
        Long latencyMillis,
        Long snapshotId,
        RawSnapshotPersistenceOutcome persistenceOutcome,
        Integer payloadSizeBytes,
        String payloadSha256,
        RawSnapshotSchemaStatus schemaStatus,
        Boolean hasNextPage,
        String terminalCode,
        Long occurrenceId,
        boolean historicalCacheTimestampAbsent) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    /** Historical V6 constructor; it does not invent an occurrence absent from its proof. */
    public J3MinimizedPageEvidence(int page, J3PageResolutionSource resolutionSource,
            boolean providerRequestExecuted, Instant resolvedAt, Instant cacheStoredAt, Instant requestedAt,
            Instant receivedAt, Integer httpStatus, Long latencyMillis, Long snapshotId,
            RawSnapshotPersistenceOutcome persistenceOutcome, Integer payloadSizeBytes, String payloadSha256,
            RawSnapshotSchemaStatus schemaStatus, Boolean hasNextPage, String terminalCode) {
        this(page,resolutionSource,providerRequestExecuted,resolvedAt,cacheStoredAt,requestedAt,receivedAt,
                httpStatus,latencyMillis,snapshotId,persistenceOutcome,payloadSizeBytes,payloadSha256,
                schemaStatus,hasNextPage,terminalCode,null,false);
    }

    public J3MinimizedPageEvidence(int page, J3PageResolutionSource resolutionSource,
            boolean providerRequestExecuted, Instant resolvedAt, Instant cacheStoredAt, Instant requestedAt,
            Instant receivedAt, Integer httpStatus, Long latencyMillis, Long snapshotId,
            RawSnapshotPersistenceOutcome persistenceOutcome, Integer payloadSizeBytes, String payloadSha256,
            RawSnapshotSchemaStatus schemaStatus, Boolean hasNextPage, String terminalCode, Long occurrenceId) {
        this(page,resolutionSource,providerRequestExecuted,resolvedAt,cacheStoredAt,requestedAt,receivedAt,
                httpStatus,latencyMillis,snapshotId,persistenceOutcome,payloadSizeBytes,payloadSha256,
                schemaStatus,hasNextPage,terminalCode,occurrenceId,false);
    }

    public J3MinimizedPageEvidence withOccurrence(Long id) {
        return new J3MinimizedPageEvidence(page,resolutionSource,providerRequestExecuted,resolvedAt,
                cacheStoredAt,requestedAt,receivedAt,httpStatus,latencyMillis,snapshotId,persistenceOutcome,
                payloadSizeBytes,payloadSha256,schemaStatus,hasNextPage,terminalCode,id,historicalCacheTimestampAbsent);
    }

    public J3MinimizedPageEvidence {
        if (historicalCacheTimestampAbsent && (resolutionSource != J3PageResolutionSource.CACHE
                || cacheStoredAt != null || snapshotId == null || occurrenceId != null))
            throw new IllegalArgumentException("Only historical cache evidence may omit its unrecorded timestamp");
        if (occurrenceId != null && (occurrenceId < 1 || snapshotId == null || resolutionSource == J3PageResolutionSource.CACHE))
            throw new IllegalArgumentException("Invalid acquisition occurrence");
        if (page < ScheduledEventsProviderPageRequest.FIRST_PAGE
                || page > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
            throw new IllegalArgumentException("page must be in the bounded collection range");
        }
        Objects.requireNonNull(resolutionSource, "resolutionSource");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
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
            if (resolvedAt.isBefore(receivedAt)) {
                throw new IllegalArgumentException("resolvedAt cannot precede receivedAt");
            }
            if (cacheStoredAt != null
                    && (cacheStoredAt.isBefore(receivedAt)
                            || resolvedAt.isBefore(cacheStoredAt))) {
                throw new IllegalArgumentException(
                        "cacheStoredAt must be between receipt and resolution");
            }
            if ((schemaStatus == RawSnapshotSchemaStatus.PARSED) != (hasNextPage != null)) {
                throw new IllegalArgumentException(
                        "only a parsed page may expose hasNextPage");
            }
        }
        else if (cacheStoredAt != null || receivedAt != null || httpStatus != null
                || latencyMillis != null
                || persistenceOutcome != null || payloadSizeBytes != null
                || payloadSha256 != null || schemaStatus != null || hasNextPage != null) {
            throw new IllegalArgumentException(
                    "a page without a snapshot cannot expose response metadata");
        }
        if (resolutionSource == J3PageResolutionSource.CACHE
                && (persistenceOutcome != RawSnapshotPersistenceOutcome.CACHE_HIT
                        || (cacheStoredAt == null && !historicalCacheTimestampAbsent))) {
            throw new IllegalArgumentException("a cache resolution requires CACHE_HIT evidence");
        }
        if (resolutionSource == J3PageResolutionSource.PROVIDER
                && persistenceOutcome == RawSnapshotPersistenceOutcome.CACHE_HIT) {
            throw new IllegalArgumentException("provider evidence cannot be a cache hit");
        }
        if (providerRequestExecuted
                && resolutionSource != J3PageResolutionSource.PROVIDER) {
            throw new IllegalArgumentException(
                    "only provider evidence can mark a provider request as executed");
        }
        if (recorded && resolutionSource == J3PageResolutionSource.PROVIDER
                && !providerRequestExecuted) {
            throw new IllegalArgumentException(
                    "recorded provider evidence requires an executed request");
        }
        if (resolutionSource == J3PageResolutionSource.LOCAL_JSON_IMPORT
                && (persistenceOutcome == RawSnapshotPersistenceOutcome.CACHE_HIT
                        || cacheStoredAt != null)) {
            throw new IllegalArgumentException(
                    "a local JSON import cannot be a cache resolution");
        }
    }

    public static J3MinimizedPageEvidence recorded(
            int page,
            ScheduledEventsTransportResponse response,
            RawSnapshotPersistenceResult persistence,
            RawSnapshotSchemaStatus schemaStatus,
            Boolean hasNextPage,
            String terminalCode) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(persistence, "persistence");
        return new J3MinimizedPageEvidence(
                page,
                J3PageResolutionSource.PROVIDER,
                true,
                response.receivedAt(),
                schemaStatus == RawSnapshotSchemaStatus.PARSED
                        ? response.receivedAt()
                        : null,
                response.requestedAt(),
                response.receivedAt(),
                response.httpStatus(),
                response.latency().toMillis(),
                persistence.snapshotId(),
                persistence.outcome(),
                persistence.payloadSizeBytes(),
                persistence.payloadSha256(),
                schemaStatus,
                hasNextPage,
                terminalCode);
    }

    public static J3MinimizedPageEvidence cached(
            int page,
            J3CachedScheduledEventsPage cachedPage,
            boolean hasNextPage,
            Instant resolvedAt) {
        Objects.requireNonNull(cachedPage, "cachedPage");
        return new J3MinimizedPageEvidence(
                page,
                J3PageResolutionSource.CACHE,
                false,
                resolvedAt,
                cachedPage.cachedAt(),
                cachedPage.requestedAt(),
                cachedPage.receivedAt(),
                cachedPage.httpStatus(),
                cachedPage.latency().toMillis(),
                cachedPage.snapshotId(),
                RawSnapshotPersistenceOutcome.CACHE_HIT,
                cachedPage.payload().sizeBytes(),
                cachedPage.payload().sha256(),
                RawSnapshotSchemaStatus.PARSED,
                hasNextPage,
                null);
    }

    public static J3MinimizedPageEvidence imported(
            int page,
            ScheduledEventsTransportResponse response,
            RawSnapshotPersistenceResult persistence,
            RawSnapshotSchemaStatus schemaStatus,
            Boolean hasNextPage,
            String terminalCode) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(persistence, "persistence");
        return new J3MinimizedPageEvidence(
                page,
                J3PageResolutionSource.LOCAL_JSON_IMPORT,
                false,
                response.receivedAt(),
                null,
                response.requestedAt(),
                response.receivedAt(),
                response.httpStatus(),
                response.latency().toMillis(),
                persistence.snapshotId(),
                persistence.outcome(),
                persistence.payloadSizeBytes(),
                persistence.payloadSha256(),
                schemaStatus,
                hasNextPage,
                terminalCode);
    }

    public static J3MinimizedPageEvidence failedBeforeSnapshot(
            int page,
            Instant requestedAt,
            String terminalCode,
            boolean providerRequestExecuted) {
        return new J3MinimizedPageEvidence(
                page,
                J3PageResolutionSource.PROVIDER,
                providerRequestExecuted,
                requestedAt,
                null,
                requestedAt,
                null,
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
