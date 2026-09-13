package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record PlaywrightProviderResponse(
        Instant requestedAt,
        Instant receivedAt,
        int httpStatus,
        String contentType,
        Duration latency,
        RawPayloadEvidence payload,
        Optional<PlaywrightProviderEntityTag> entityTag,
        PlaywrightTransportDiagnostic diagnostic) {

    public PlaywrightProviderResponse(Instant requestedAt, Instant receivedAt, int httpStatus,
            String contentType, Duration latency, RawPayloadEvidence payload) {
        this(requestedAt, receivedAt, httpStatus, contentType, latency, payload, Optional.empty(), null);
    }

    public PlaywrightProviderResponse(Instant requestedAt, Instant receivedAt, int httpStatus,
            String contentType, Duration latency, RawPayloadEvidence payload,
            PlaywrightTransportDiagnostic diagnostic) {
        this(requestedAt, receivedAt, httpStatus, contentType, latency, payload, Optional.empty(), diagnostic);
    }

    public PlaywrightProviderResponse {
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = Objects.requireNonNull(contentType, "contentType").trim();
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(payload, "payload");
        entityTag = Objects.requireNonNull(entityTag, "entityTag");
        if (receivedAt.isBefore(requestedAt)
                || httpStatus < 100
                || httpStatus > 599
                || contentType.length() > 160
                || contentType.chars().anyMatch(Character::isISOControl)
                || latency.isNegative()) {
            throw new IllegalArgumentException("invalid bounded Playwright response");
        }
        if (diagnostic != null && (!diagnostic.responseComplete()
                || !requestedAt.equals(diagnostic.requestedAt()) || diagnostic.httpStatus() != httpStatus
                || diagnostic.headersReceivedAt().isAfter(receivedAt)))
            throw new IllegalArgumentException("response diagnostic mismatch");
    }
}
