package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Terminal, metadata-only proof of one bounded explicit manual collection.
 */
public record J3MinimizedCollectionEvidence(
        LocalDate collectionDate,
        J3ManualCallIntentState terminalState,
        int initialCompletedPages,
        int completedPages,
        Integer failedPage,
        String terminalCode,
        Instant generatedAt,
        boolean globalStopActive,
        J3CircuitState finalCircuitState,
        J3CircuitReason finalCircuitReason,
        Duration cacheTtl,
        List<J3MinimizedPageEvidence> pageAttempts) {

    public J3MinimizedCollectionEvidence {
        Objects.requireNonNull(collectionDate, "collectionDate");
        Objects.requireNonNull(terminalState, "terminalState");
        terminalCode = requireSafeCode(terminalCode);
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(finalCircuitState, "finalCircuitState");
        Objects.requireNonNull(finalCircuitReason, "finalCircuitReason");
        Objects.requireNonNull(cacheTtl, "cacheTtl");
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("cacheTtl must be positive");
        }
        pageAttempts = List.copyOf(pageAttempts);
        if (terminalState != J3ManualCallIntentState.COMPLETED
                && terminalState != J3ManualCallIntentState.FAILED
                && terminalState != J3ManualCallIntentState.CANCELLED_BY_GLOBAL_STOP) {
            throw new IllegalArgumentException("evidence requires a terminal intent state");
        }
        if (initialCompletedPages != 0) {
            throw new IllegalArgumentException("a dynamic collection must start at page 1");
        }
        if (completedPages < 0
                || completedPages > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
            throw new IllegalArgumentException("completedPages must be between 0 and 25");
        }
        if (!globalStopActive || finalCircuitState != J3CircuitState.LOCKED) {
            throw new IllegalArgumentException("terminal evidence requires a locked global stop");
        }
        if (pageAttempts.size()
                > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
            throw new IllegalArgumentException("too many page attempts");
        }
        for (int index = 0; index < pageAttempts.size(); index++) {
            if (pageAttempts.get(index).page() != initialCompletedPages + index + 1) {
                throw new IllegalArgumentException(
                        "page evidence must be sequential and start at page 1");
            }
        }
        if (terminalState == J3ManualCallIntentState.COMPLETED) {
            if (completedPages < 1 || failedPage != null || !"NONE".equals(terminalCode)
                    || pageAttempts.size() != completedPages
                    || pageAttempts.stream().anyMatch(attempt -> !attempt.snapshotRecorded())
                    || !Boolean.FALSE.equals(pageAttempts.getLast().hasNextPage())
                    || pageAttempts.stream().limit(pageAttempts.size() - 1L)
                            .anyMatch(attempt -> !Boolean.TRUE.equals(
                                    attempt.hasNextPage()))
                    || finalCircuitReason
                            != J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK) {
                throw new IllegalArgumentException(
                        "completed evidence requires persisted pages and a terminal lock");
            }
        }
        else {
            if (failedPage == null || failedPage != completedPages + 1
                    || "NONE".equals(terminalCode)) {
                throw new IllegalArgumentException(
                        "failed or cancelled evidence requires the next page and a terminal code");
            }
            int completedAttempts = completedPages - initialCompletedPages;
            if (pageAttempts.size() != completedAttempts
                    && pageAttempts.size() != completedAttempts + 1) {
                throw new IllegalArgumentException(
                        "page attempts must stop before or on the terminal page");
            }
            if ("PAGINATION_LIMIT_REACHED".equals(terminalCode)
                    && (completedPages
                                    != ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE
                            || pageAttempts.stream().anyMatch(
                                    attempt -> !Boolean.TRUE.equals(attempt.hasNextPage())))) {
                throw new IllegalArgumentException(
                        "the pagination limit requires 25 continuing parsed pages");
            }
        }
    }

    private static String requireSafeCode(String value) {
        Objects.requireNonNull(value, "terminalCode");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 96
                || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("terminalCode must be a safe code");
        }
        return normalized;
    }
}
