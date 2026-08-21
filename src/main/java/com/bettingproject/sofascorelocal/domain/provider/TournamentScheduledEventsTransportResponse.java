package com.bettingproject.sofascorelocal.domain.provider;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Raw, bounded response returned by the exact tournament event-discovery transport. */
public record TournamentScheduledEventsTransportResponse(
        String requestKey,
        Instant requestedAt,
        Instant receivedAt,
        int httpStatus,
        String contentType,
        Duration latency,
        RawPayloadEvidence payload,
        Instant retryNotBefore) {

    private static final int MAXIMUM_CONTENT_TYPE_LENGTH = 160;
    private static final Pattern REQUEST_KEY_PATTERN = Pattern.compile(
            "^TOURNAMENT_SCHEDULED_EVENTS\\|date=([^|]+)"
                    + "\\|uniqueTournamentId=([1-9][0-9]*)$");

    public TournamentScheduledEventsTransportResponse {
        requestKey = requireCanonicalRequestKey(requestKey);
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = requireText(
                contentType,
                "contentType",
                MAXIMUM_CONTENT_TYPE_LENGTH);
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(payload, "payload");
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

    public TournamentScheduledEventsTransportResponse(
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
        return SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS;
    }

    public static String requireCanonicalRequestKey(String value) {
        String normalized = requireText(value, "requestKey", 512);
        Matcher matcher = REQUEST_KEY_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "requestKey must identify tournament scheduled events by date and ID");
        }
        try {
            LocalDate.parse(matcher.group(1));
            if (Long.parseLong(matcher.group(2)) < 1) {
                throw new IllegalArgumentException(
                        "requestKey uniqueTournamentId must be positive");
            }
        }
        catch (DateTimeException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "requestKey must contain an ISO date and a positive long ID",
                    exception);
        }
        return normalized;
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
