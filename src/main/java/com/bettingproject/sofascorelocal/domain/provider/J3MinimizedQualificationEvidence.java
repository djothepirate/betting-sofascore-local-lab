package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Terminal, metadata-only proof of the one bounded J3 qualification sequence.
 */
public record J3MinimizedQualificationEvidence(
        LocalDate qualificationDate,
        J3ManualCallIntentState terminalState,
        int completedPages,
        Integer failedPage,
        String terminalCode,
        Instant generatedAt,
        boolean globalStopActive,
        J3CircuitState finalCircuitState,
        J3CircuitReason finalCircuitReason,
        List<J3MinimizedPageEvidence> pageAttempts) {

    public J3MinimizedQualificationEvidence {
        Objects.requireNonNull(qualificationDate, "qualificationDate");
        Objects.requireNonNull(terminalState, "terminalState");
        terminalCode = requireSafeCode(terminalCode);
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(finalCircuitState, "finalCircuitState");
        Objects.requireNonNull(finalCircuitReason, "finalCircuitReason");
        pageAttempts = List.copyOf(pageAttempts);
        if (terminalState != J3ManualCallIntentState.COMPLETED
                && terminalState != J3ManualCallIntentState.FAILED
                && terminalState != J3ManualCallIntentState.CANCELLED_BY_GLOBAL_STOP) {
            throw new IllegalArgumentException("evidence requires a terminal intent state");
        }
        if (completedPages < 0 || completedPages > 5) {
            throw new IllegalArgumentException("completedPages must be between 0 and 5");
        }
        if (!globalStopActive || finalCircuitState != J3CircuitState.LOCKED) {
            throw new IllegalArgumentException("terminal evidence requires a locked global stop");
        }
        if (pageAttempts.size() > 5) {
            throw new IllegalArgumentException("at most five page attempts are allowed");
        }
        for (int index = 0; index < pageAttempts.size(); index++) {
            if (pageAttempts.get(index).page() != index + 1) {
                throw new IllegalArgumentException("page evidence must be ordered from page one");
            }
        }
        if (terminalState == J3ManualCallIntentState.COMPLETED) {
            if (completedPages != 5 || failedPage != null || !"NONE".equals(terminalCode)
                    || pageAttempts.size() != 5
                    || pageAttempts.stream().anyMatch(attempt -> !attempt.snapshotRecorded())
                    || finalCircuitReason != J3CircuitReason.QUALIFICATION_TERMINAL_LOCK) {
                throw new IllegalArgumentException(
                        "completed evidence requires five persisted pages and a terminal lock");
            }
        }
        else {
            if (failedPage == null || failedPage != completedPages + 1
                    || "NONE".equals(terminalCode)) {
                throw new IllegalArgumentException(
                        "failed or cancelled evidence requires the next page and a terminal code");
            }
            if (pageAttempts.size() != completedPages
                    && pageAttempts.size() != failedPage) {
                throw new IllegalArgumentException(
                        "page attempts must stop before or on the terminal page");
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
