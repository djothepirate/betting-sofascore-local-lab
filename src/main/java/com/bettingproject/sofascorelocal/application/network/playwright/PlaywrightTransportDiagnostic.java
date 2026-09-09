package com.bettingproject.sofascorelocal.application.network.playwright;

import java.time.Instant;
import java.util.Objects;

/** Allowlisted partial transport evidence, never a response snapshot or provider content. */
public record PlaywrightTransportDiagnostic(
        Phase phase, int requestTimeoutMillis, Instant requestedAt, Instant headersReceivedAt,
        Integer httpStatus, Instant retryAfterNotBefore, boolean responseComplete) {
    public enum Phase { NAVIGATION, REQUEST_SENT, HEADERS_RECEIVED, READING_BODY, COMPLETE, PARENT_IPC_WAIT }

    public PlaywrightTransportDiagnostic {
        Objects.requireNonNull(phase);
        if (requestTimeoutMillis < 1 || requestTimeoutMillis > 60_000
                || httpStatus != null && (httpStatus < 100 || httpStatus > 599)
                || (headersReceivedAt == null) != (httpStatus == null)
                || headersReceivedAt != null && (requestedAt == null || headersReceivedAt.isBefore(requestedAt))
                || retryAfterNotBefore != null && (headersReceivedAt == null || retryAfterNotBefore.isBefore(headersReceivedAt))
                || responseComplete != (phase == Phase.COMPLETE)
                || responseComplete && headersReceivedAt == null
                || phase == Phase.NAVIGATION && requestedAt != null
                || phase == Phase.REQUEST_SENT && (requestedAt == null || headersReceivedAt != null)
                || (phase == Phase.HEADERS_RECEIVED || phase == Phase.READING_BODY) && headersReceivedAt == null) {
            throw new IllegalArgumentException("invalid bounded transport diagnostic");
        }
        for (Instant instant : new Instant[]{requestedAt, headersReceivedAt, retryAfterNotBefore}) {
            if (instant != null && (instant.toEpochMilli() < 1 || instant.toEpochMilli() > 253_402_300_799_999L))
                throw new IllegalArgumentException("invalid diagnostic timestamp");
        }
    }

    public PlaywrightTransportDiagnostic at(Phase next) {
        return new PlaywrightTransportDiagnostic(next, requestTimeoutMillis, requestedAt,
                headersReceivedAt, httpStatus, retryAfterNotBefore, next == Phase.COMPLETE);
    }
}
