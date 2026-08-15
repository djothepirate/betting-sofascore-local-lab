package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record J4CachedEventDetails(
        long snapshotId,
        String requestKey,
        Instant cachedAt,
        Instant requestedAt,
        Instant receivedAt,
        int httpStatus,
        String contentType,
        Duration latency,
        RawPayloadEvidence payload,
        String parserVersion) {

    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    public J4CachedEventDetails {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        requestKey = requireText(requestKey, "requestKey");
        Objects.requireNonNull(cachedAt, "cachedAt");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = requireText(contentType, "contentType");
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(payload, "payload");
        parserVersion = requireText(parserVersion, "parserVersion");
        if (!requestKey.matches("EVENT_DETAILS\\|eventId=[1-9][0-9]{0,8}")) {
            throw new IllegalArgumentException("cache key is outside bounded J4 events");
        }
        if (receivedAt.isBefore(requestedAt) || cachedAt.isBefore(receivedAt)) {
            throw new IllegalArgumentException("cache timestamps are inconsistent");
        }
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalArgumentException("a cache entry requires successful HTTP status");
        }
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency cannot be negative");
        }
        if (!VERSION_PATTERN.matcher(parserVersion).matches()) {
            throw new IllegalArgumentException("parserVersion contains unsafe characters");
        }
    }

    public EventDetailsTransportResponse asTransportResponse() {
        return new EventDetailsTransportResponse(
                requestKey,
                requestedAt,
                receivedAt,
                httpStatus,
                contentType,
                latency,
                payload);
    }

    public RawSnapshotPersistenceResult asPersistenceResult() {
        return new RawSnapshotPersistenceResult(
                snapshotId,
                RawSnapshotPersistenceOutcome.DEDUPLICATED,
                payload.sha256(),
                payload.sizeBytes());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be safe non-blank text");
        }
        return normalized;
    }
}
