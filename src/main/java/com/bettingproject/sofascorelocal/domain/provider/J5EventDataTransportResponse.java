package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record J5EventDataTransportResponse(
        SofascoreEndpointType endpointType,
        String requestKey,
        Instant requestedAt,
        Instant receivedAt,
        int httpStatus,
        String contentType,
        Duration latency,
        RawPayloadEvidence payload) {

    public J5EventDataTransportResponse {
        endpointType = Objects.requireNonNull(endpointType, "endpointType");
        if (!J5EventDataProviderRequest.ALLOWED_ENDPOINTS.contains(endpointType)) {
            throw new IllegalArgumentException("response endpoint is outside J5 scope");
        }
        requestKey = requireText(requestKey, "requestKey", 512);
        if (!requestKey.matches(endpointType.name() + "\\|eventId=[1-9][0-9]{0,8}")) {
            throw new IllegalArgumentException("requestKey does not match the J5 endpoint");
        }
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = requireText(contentType, "contentType", 160);
        latency = Objects.requireNonNull(latency, "latency");
        payload = Objects.requireNonNull(payload, "payload");
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("httpStatus must be between 100 and 599");
        }
        if (receivedAt.isBefore(requestedAt) || latency.isNegative()) {
            throw new IllegalArgumentException("response timing is inconsistent");
        }
    }

    private static String requireText(String value, String name, int maximumLength) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()
                || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be bounded safe text");
        }
        return normalized;
    }
}
