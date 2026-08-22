package com.bettingproject.sofascorelocal.application.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record J5OfflineBatchControlSnapshot(
        J5OfflineBatchState state,
        Instant changedAt,
        J5OfflineBatchPlan plan,
        String terminalCode,
        J5OfflineBatchResult result,
        boolean offlineAvailable,
        List<String> offlineBlockers) {

    public J5OfflineBatchControlSnapshot {
        state = Objects.requireNonNull(state, "state");
        changedAt = Objects.requireNonNull(changedAt, "changedAt");
        offlineBlockers = List.copyOf(Objects.requireNonNull(
                offlineBlockers, "offlineBlockers"));
        if (offlineAvailable != offlineBlockers.isEmpty()) {
            throw new IllegalArgumentException("offline availability and blockers conflict");
        }
        if ((state == J5OfflineBatchState.AWAITING_CONFIRMATION
                || state == J5OfflineBatchState.EXECUTING)
                && plan == null) {
            throw new IllegalArgumentException("active offline batch lacks its plan");
        }
    }

    public boolean awaitingConfirmation() {
        return state == J5OfflineBatchState.AWAITING_CONFIRMATION;
    }

    public boolean executing() {
        return state == J5OfflineBatchState.EXECUTING;
    }

    public boolean terminal() {
        return switch (state) {
            case COMPLETED_LOCKED, FAILED_LOCKED, STOPPED_LOCKED, EXPIRED_LOCKED -> true;
            default -> false;
        };
    }

    public boolean preparationAllowed() {
        return state != J5OfflineBatchState.AWAITING_CONFIRMATION
                && state != J5OfflineBatchState.EXECUTING
                && offlineAvailable;
    }
}
