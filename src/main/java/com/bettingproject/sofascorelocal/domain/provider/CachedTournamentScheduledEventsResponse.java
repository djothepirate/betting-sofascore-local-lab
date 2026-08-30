package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/** A verified, fresh raw response selected from the provider response cache. */
public record CachedTournamentScheduledEventsResponse(
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

    public static final String PARSER_VERSION = "tournament-scheduled-v1";

    public CachedTournamentScheduledEventsResponse {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        requestKey = TournamentScheduledEventsTransportResponse
                .requireCanonicalRequestKey(requestKey);
        Objects.requireNonNull(cachedAt, "cachedAt");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = requireText(contentType, "contentType");
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(payload, "payload");
        requireExactParserVersion(parserVersion);
        if (receivedAt.isBefore(requestedAt) || cachedAt.isBefore(receivedAt)) {
            throw new IllegalArgumentException("cache timestamps are inconsistent");
        }
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalArgumentException(
                    "a cache entry requires a successful HTTP status");
        }
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency cannot be negative");
        }
    }

    public TournamentScheduledEventsTransportResponse asTransportResponse() {
        return new TournamentScheduledEventsTransportResponse(
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
                RawSnapshotPersistenceOutcome.CACHE_HIT,
                payload.sha256(),
                payload.sizeBytes(),
                OptionalLong.empty());
    }

    public static String requireExactParserVersion(String value) {
        Objects.requireNonNull(value, "parserVersion");
        if (!PARSER_VERSION.equals(value)) {
            throw new IllegalArgumentException(
                    "parserVersion must be exactly " + PARSER_VERSION);
        }
        return value;
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
