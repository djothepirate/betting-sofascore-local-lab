package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record J3ManualCallIntentSnapshot(
        UUID requestId,
        LocalDate date,
        String requestKey,
        J3ManualCallIntentState state,
        String confirmationPhrase,
        Instant preparedAt,
        Instant expiresAt,
        Instant confirmedAt) {

    public J3ManualCallIntentSnapshot {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(date, "date");
        requestKey = requireText(requestKey, "requestKey");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(preparedAt, "preparedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(preparedAt)) {
            throw new IllegalArgumentException("expiresAt must be after preparedAt");
        }
        if (!requestKey.equals(SofascoreEndpointType.SCHEDULED_EVENTS.name() + "|date=" + date)) {
            throw new IllegalArgumentException("requestKey must match the scheduled-events date");
        }

        if (state == J3ManualCallIntentState.AWAITING_CONFIRMATION) {
            confirmationPhrase = requireText(confirmationPhrase, "confirmationPhrase");
            if (confirmedAt != null) {
                throw new IllegalArgumentException(
                        "an awaiting intent cannot have a confirmation time");
            }
        }
        else {
            if (confirmationPhrase != null) {
                throw new IllegalArgumentException(
                        "only an awaiting intent may expose its confirmation phrase");
            }
            if ((state == J3ManualCallIntentState.CONFIRMED_BLOCKED)
                    != (confirmedAt != null)) {
                throw new IllegalArgumentException(
                        "only a confirmed intent requires a confirmation time");
            }
        }
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
