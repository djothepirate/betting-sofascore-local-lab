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
        Instant confirmedAt,
        int completedPages,
        Integer failedPage,
        String terminalCode) {

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
        if (!requestKey.equals(SofascoreEndpointType.SCHEDULED_EVENTS.name()
                + "|date=" + date + "|pages=1-5")) {
            throw new IllegalArgumentException(
                    "requestKey must match the scheduled-events five-page batch");
        }
        if (completedPages < 0 || completedPages > 5) {
            throw new IllegalArgumentException("completedPages must be between 0 and 5");
        }
        if (failedPage != null && (failedPage < 1 || failedPage > 5)) {
            throw new IllegalArgumentException("failedPage must be between 1 and 5");
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
            boolean confirmedState = state == J3ManualCallIntentState.CONFIRMED_BLOCKED
                    || state == J3ManualCallIntentState.CONFIRMED_READY
                    || state == J3ManualCallIntentState.EXECUTING
                    || state == J3ManualCallIntentState.COMPLETED
                    || state == J3ManualCallIntentState.FAILED;
            if (confirmedState != (confirmedAt != null)) {
                throw new IllegalArgumentException(
                        "only confirmed and execution states require a confirmation time");
            }
        }
        if (state == J3ManualCallIntentState.COMPLETED && completedPages != 5) {
            throw new IllegalArgumentException("a completed batch requires five completed pages");
        }
        if (state == J3ManualCallIntentState.FAILED) {
            if (failedPage == null || terminalCode == null || terminalCode.isBlank()) {
                throw new IllegalArgumentException(
                        "a failed batch requires a failed page and terminal code");
            }
        }
        else if (failedPage != null || terminalCode != null) {
            throw new IllegalArgumentException(
                    "only a failed batch may carry failure details");
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
