package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.util.Objects;

public record J3CircuitSnapshot(
        J3CircuitState state,
        J3CircuitReason reason,
        Instant changedAt,
        Instant retryNotBefore) {

    public J3CircuitSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(changedAt, "changedAt");

        if (state == J3CircuitState.CLOSED) {
            if (reason != J3CircuitReason.NONE || retryNotBefore != null) {
                throw new IllegalArgumentException(
                        "a closed circuit cannot carry an incident reason or retry boundary");
            }
        } else if (state == J3CircuitState.LOCKED) {
            if (reason != J3CircuitReason.STARTUP_LOCK
                    && reason != J3CircuitReason.OPERATOR_STOP
                    && reason != J3CircuitReason.QUALIFICATION_TERMINAL_LOCK) {
                throw new IllegalArgumentException(
                        "a locked circuit requires a recognized lock reason");
            }
            if (retryNotBefore != null) {
                throw new IllegalArgumentException("a locked circuit cannot carry retryNotBefore");
            }
        } else {
            if (reason == J3CircuitReason.NONE
                    || reason == J3CircuitReason.STARTUP_LOCK
                    || reason == J3CircuitReason.OPERATOR_STOP
                    || reason == J3CircuitReason.QUALIFICATION_TERMINAL_LOCK) {
                throw new IllegalArgumentException("an open circuit requires an incident reason");
            }
            if ((reason == J3CircuitReason.HTTP_TOO_MANY_REQUESTS)
                    != (retryNotBefore != null)) {
                throw new IllegalArgumentException(
                        "only a rate-limit incident carries retryNotBefore");
            }
        }
    }

    public boolean transportMayBeEvaluated() {
        return state == J3CircuitState.CLOSED;
    }
}
