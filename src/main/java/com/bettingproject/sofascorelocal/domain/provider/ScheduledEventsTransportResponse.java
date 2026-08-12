package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ScheduledEventsTransportResponse(
        String requestKey,
        Instant requestedAt,
        Instant receivedAt,
        int httpStatus,
        String contentType,
        Duration latency,
        RawPayloadEvidence payload,
        Instant retryNotBefore) {

    private static final int MAXIMUM_CONTENT_TYPE_LENGTH = 160;

    public ScheduledEventsTransportResponse {
        requestKey = requireText(requestKey, "requestKey");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = requireText(contentType, "contentType", MAXIMUM_CONTENT_TYPE_LENGTH);
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(payload, "payload");

        if (!requestKey.startsWith(SofascoreEndpointType.SCHEDULED_EVENTS.name() + "|date=")) {
            throw new IllegalArgumentException("requestKey must identify SCHEDULED_EVENTS by date");
        }
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("httpStatus must be between 100 and 599");
        }
        if (receivedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("receivedAt cannot precede requestedAt");
        }
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency cannot be negative");
        }
        if (retryNotBefore != null
                && (httpStatus != 429 || !retryNotBefore.isAfter(receivedAt))) {
            throw new IllegalArgumentException(
                    "retryNotBefore is accepted only for HTTP 429 after receivedAt");
        }
    }

    public ScheduledEventsTransportResponse(
            String requestKey,
            Instant requestedAt,
            Instant receivedAt,
            int httpStatus,
            String contentType,
            Duration latency,
            RawPayloadEvidence payload) {
        this(
                requestKey,
                requestedAt,
                receivedAt,
                httpStatus,
                contentType,
                latency,
                payload,
                null);
    }

    public SofascoreEndpointType endpointType() {
        return SofascoreEndpointType.SCHEDULED_EVENTS;
    }

    private static String requireText(String value, String name) {
        return requireText(value, name, Integer.MAX_VALUE);
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be safe non-blank text");
        }
        return normalized;
    }
}
