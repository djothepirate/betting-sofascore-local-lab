package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable raw snapshot selected as a fresh cache candidate for one exact
 * SCHEDULED_EVENTS date/page key.
 */
public record J3CachedScheduledEventsPage(
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

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    public J3CachedScheduledEventsPage {
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

        if (!requestKey.startsWith(SofascoreEndpointType.SCHEDULED_EVENTS.name()
                + "|date=") || !requestKey.contains("|page=")) {
            throw new IllegalArgumentException(
                    "requestKey must identify one SCHEDULED_EVENTS provider page");
        }
        if (receivedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("receivedAt cannot precede requestedAt");
        }
        if (cachedAt.isBefore(receivedAt)) {
            throw new IllegalArgumentException("cachedAt cannot precede the stored response");
        }
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalArgumentException("a cache entry requires a successful HTTP status");
        }
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency cannot be negative");
        }
        if (!VERSION_PATTERN.matcher(parserVersion).matches()) {
            throw new IllegalArgumentException("parserVersion contains unsafe characters");
        }
    }

    public ScheduledEventsTransportResponse asTransportResponse() {
        return new ScheduledEventsTransportResponse(
                requestKey,
                requestedAt,
                receivedAt,
                httpStatus,
                contentType,
                latency,
                payload);
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
