package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record EventDetailsTransportResponse(
        String requestKey,
        Instant requestedAt,
        Instant receivedAt,
        int httpStatus,
        String contentType,
        Duration latency,
        RawPayloadEvidence payload) {

    private static final int MAXIMUM_CONTENT_TYPE_LENGTH = 160;

    public EventDetailsTransportResponse {
        requestKey = requireText(requestKey, "requestKey", 512);
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = requireText(contentType, "contentType", MAXIMUM_CONTENT_TYPE_LENGTH);
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(payload, "payload");
        if (!requestKey.matches("EVENT_DETAILS\\|eventId=[1-9][0-9]{0,8}")) {
            throw new IllegalArgumentException(
                    "requestKey must identify one bounded J4 event");
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
    }

    public SofascoreEndpointType endpointType() {
        return SofascoreEndpointType.EVENT_DETAILS;
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be bounded safe text");
        }
        return normalized;
    }
}
