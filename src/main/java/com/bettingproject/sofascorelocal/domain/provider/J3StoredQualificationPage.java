package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Local-only raw checkpoint used to prove which qualification pages already exist.
 * The payload bytes must never be rendered, logged or exported by this model.
 */
public record J3StoredQualificationPage(
        long snapshotId,
        LocalDate date,
        int page,
        String requestKey,
        Instant requestedAt,
        Instant receivedAt,
        int httpStatus,
        String contentType,
        Duration latency,
        RawSnapshotSchemaStatus historicalSchemaStatus,
        RawPayloadEvidence payload) {

    public J3StoredQualificationPage {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(historicalSchemaStatus, "historicalSchemaStatus");
        Objects.requireNonNull(payload, "payload");
        contentType = requireText(contentType, "contentType");
        requestKey = requireText(requestKey, "requestKey");

        String expectedRequestKey = SofascoreEndpointType.SCHEDULED_EVENTS.name()
                + "|date=" + date + "|page=" + page;
        if (!requestKey.equals(expectedRequestKey)) {
            throw new IllegalArgumentException(
                    "requestKey must identify the stored qualification page exactly");
        }
        if (!ScheduledEventsProviderPageRequest.QUALIFICATION_DATE.equals(date)) {
            throw new IllegalArgumentException("date must be the authorized qualification date");
        }
        if (page < ScheduledEventsProviderPageRequest.FIRST_PAGE
                || page > ScheduledEventsProviderPageRequest.LAST_PAGE) {
            throw new IllegalArgumentException("page must be between 1 and 5");
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

    public ScheduledEventsTransportResponse toTransportResponse() {
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
            throw new IllegalArgumentException(name + " must contain safe non-blank text");
        }
        return normalized;
    }
}
