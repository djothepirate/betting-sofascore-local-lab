package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J4RealPhase1ControlSnapshot(
        J4RealPhase1State state,
        Instant changedAt,
        UUID requestId,
        String confirmationPhrase,
        Instant preparedAt,
        Instant expiresAt,
        int completedEvents,
        String terminalCode,
        boolean providerTransportAvailable,
        List<String> providerBlockers) {

    public J4RealPhase1ControlSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(changedAt, "changedAt");
        providerBlockers = List.copyOf(Objects.requireNonNull(
                providerBlockers, "providerBlockers"));
        if (completedEvents < 0
                || completedEvents > EventDetailsProviderRequest.PHASE_1_EVENT_IDS.size()) {
            throw new IllegalArgumentException("completedEvents is outside phase-1 bounds");
        }
        if (providerTransportAvailable != providerBlockers.isEmpty()) {
            throw new IllegalArgumentException(
                    "provider availability and blockers must be mutually exclusive");
        }
        if (state == J4RealPhase1State.AWAITING_CONFIRMATION) {
            if (requestId == null
                    || confirmationPhrase == null
                    || preparedAt == null
                    || expiresAt == null
                    || !expiresAt.isAfter(preparedAt)
                    || terminalCode != null) {
                throw new IllegalArgumentException(
                        "awaiting confirmation requires a bounded active intent");
            }
        }
        if (state == J4RealPhase1State.EXECUTING && requestId == null) {
            throw new IllegalArgumentException("executing phase requires a request id");
        }
    }

    public boolean locked() {
        return state != J4RealPhase1State.AWAITING_CONFIRMATION
                && state != J4RealPhase1State.EXECUTING;
    }

    public boolean awaitingConfirmation() {
        return state == J4RealPhase1State.AWAITING_CONFIRMATION;
    }
}
