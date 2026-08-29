package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;

public record J4RealPhase2ControlSnapshot(
        J4RealPhase2State state,
        Instant changedAt,
        UUID requestId,
        String confirmationPhrase,
        Instant preparedAt,
        Instant expiresAt,
        UUID canonicalEventId,
        Long eventId,
        boolean eventCompleted,
        String terminalCode,
        boolean providerTransportAvailable,
        List<String> providerBlockers) {

    public J4RealPhase2ControlSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(changedAt, "changedAt");
        providerBlockers = List.copyOf(Objects.requireNonNull(
                providerBlockers, "providerBlockers"));
        if (providerTransportAvailable != providerBlockers.isEmpty()) {
            throw new IllegalArgumentException(
                    "provider availability and blockers must be mutually exclusive");
        }
        if (eventId != null) {
            EventDetailsProviderRequest.requirePhase2EventId(eventId);
        }
        if ((canonicalEventId == null) != (eventId == null)) {
            throw new IllegalArgumentException(
                    "canonical event id and provider event id must be present together");
        }
        if (canonicalEventId != null) {
            new CanonicalEventIdentity(
                    canonicalEventId, CanonicalEventIdentity.SOFASCORE, eventId);
        }
        if (state == J4RealPhase2State.AWAITING_CONFIRMATION) {
            if (requestId == null
                    || confirmationPhrase == null
                    || preparedAt == null
                    || expiresAt == null
                    || !expiresAt.isAfter(preparedAt)
                    || canonicalEventId == null
                    || eventId == null
                    || eventCompleted
                    || terminalCode != null) {
                throw new IllegalArgumentException(
                        "awaiting confirmation requires one bounded active intent");
            }
        }
        if (state == J4RealPhase2State.EXECUTING
                && (requestId == null || canonicalEventId == null || eventId == null)) {
            throw new IllegalArgumentException(
                    "executing phase requires a request id and event id");
        }
        if (eventCompleted && eventId == null) {
            throw new IllegalArgumentException("a completed event requires its event id");
        }
    }

    public boolean locked() {
        return state != J4RealPhase2State.AWAITING_CONFIRMATION
                && state != J4RealPhase2State.EXECUTING;
    }

    public boolean awaitingConfirmation() {
        return state == J4RealPhase2State.AWAITING_CONFIRMATION;
    }

    public boolean preparationAllowed() {
        return state == J4RealPhase2State.LOCKED
                || state == J4RealPhase2State.COMPLETED_LOCKED
                || state == J4RealPhase2State.EXPIRED_LOCKED;
    }
}
