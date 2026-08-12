package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record J3CircuitIncident(
        J3CircuitReason reason,
        Instant occurredAt,
        Instant retryNotBefore) {

    private static final Set<J3CircuitReason> INCIDENT_REASONS = EnumSet.of(
            J3CircuitReason.HTTP_BAD_REQUEST,
            J3CircuitReason.HTTP_UNAUTHORIZED,
            J3CircuitReason.HTTP_FORBIDDEN,
            J3CircuitReason.HTTP_TOO_MANY_REQUESTS,
            J3CircuitReason.TIMEOUT,
            J3CircuitReason.TRANSPORT_IO_FAILURE,
            J3CircuitReason.PAYLOAD_TOO_LARGE,
            J3CircuitReason.SENSITIVE_CONTENT_REJECTED,
            J3CircuitReason.UNEXPECTED_CONTENT,
            J3CircuitReason.SCHEMA_INCOMPATIBLE,
            J3CircuitReason.SERVER_ERROR);

    public J3CircuitIncident {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!INCIDENT_REASONS.contains(reason)) {
            throw new IllegalArgumentException("reason must identify a network or parsing incident");
        }
        if (reason == J3CircuitReason.HTTP_TOO_MANY_REQUESTS) {
            if (retryNotBefore == null || !retryNotBefore.isAfter(occurredAt)) {
                throw new IllegalArgumentException(
                        "HTTP_TOO_MANY_REQUESTS requires retryNotBefore after occurredAt");
            }
        } else if (retryNotBefore != null) {
            throw new IllegalArgumentException(
                    "retryNotBefore is only accepted for HTTP_TOO_MANY_REQUESTS");
        }
    }

    public static J3CircuitIncident at(J3CircuitReason reason, Instant occurredAt) {
        return new J3CircuitIncident(reason, occurredAt, null);
    }

    public static J3CircuitIncident rateLimited(Instant occurredAt, Instant retryNotBefore) {
        return new J3CircuitIncident(
                J3CircuitReason.HTTP_TOO_MANY_REQUESTS,
                occurredAt,
                retryNotBefore);
    }
}
