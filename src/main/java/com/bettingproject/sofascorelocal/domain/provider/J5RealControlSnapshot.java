package com.bettingproject.sofascorelocal.domain.provider;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J5RealControlSnapshot(
        J5RealControlState state,
        Instant changedAt,
        UUID requestId,
        String confirmationPhrase,
        Instant preparedAt,
        Instant expiresAt,
        UUID canonicalEventId,
        Long eventId,
        List<SofascoreEndpointType> completedEndpoints,
        String terminalCode,
        boolean globalStopActive,
        boolean providerTransportAvailable,
        List<String> providerBlockers) {

    public J5RealControlSnapshot {
        state = Objects.requireNonNull(state, "state");
        changedAt = Objects.requireNonNull(changedAt, "changedAt");
        completedEndpoints = List.copyOf(Objects.requireNonNull(
                completedEndpoints, "completedEndpoints"));
        providerBlockers = List.copyOf(Objects.requireNonNull(
                providerBlockers, "providerBlockers"));
        if (providerTransportAvailable != providerBlockers.isEmpty()) {
            throw new IllegalArgumentException("provider availability and blockers conflict");
        }
        if (eventId != null) {
            J5EventDataProviderRequest.requireEventId(eventId);
            if (canonicalEventId == null
                    || !CanonicalEventIdentity.sofascore(eventId).value()
                            .equals(canonicalEventId)) {
                throw new IllegalArgumentException("event identity is inconsistent");
            }
        }
        if (completedEndpoints.stream().anyMatch(
                endpoint -> !J5EventDataProviderRequest.ALLOWED_ENDPOINTS.contains(endpoint))) {
            throw new IllegalArgumentException("completed endpoint is outside J5 scope");
        }
        if (state == J5RealControlState.AWAITING_CONFIRMATION
                && (requestId == null
                || confirmationPhrase == null
                || preparedAt == null
                || expiresAt == null
                || !expiresAt.isAfter(preparedAt)
                || eventId == null
                || !completedEndpoints.isEmpty()
                || terminalCode != null)) {
            throw new IllegalArgumentException("pending J5 confirmation is incomplete");
        }
        if (state == J5RealControlState.EXECUTING
                && (requestId == null || eventId == null)) {
            throw new IllegalArgumentException("executing J5 campaign lacks its identity");
        }
    }

    public boolean awaitingConfirmation() {
        return state == J5RealControlState.AWAITING_CONFIRMATION;
    }

    public boolean executing() {
        return state == J5RealControlState.EXECUTING;
    }

    public boolean preparationAllowed() {
        return !globalStopActive
                && (state == J5RealControlState.LOCKED
                || state == J5RealControlState.COMPLETED_LOCKED)
                && providerTransportAvailable;
    }

    public boolean terminal() {
        return switch (state) {
            case COMPLETED_LOCKED, FAILED_LOCKED, STOPPED_LOCKED, EXPIRED_LOCKED -> true;
            default -> false;
        };
    }
}
