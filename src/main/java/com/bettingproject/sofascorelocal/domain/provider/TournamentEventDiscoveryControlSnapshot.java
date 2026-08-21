package com.bettingproject.sofascorelocal.domain.provider;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TournamentEventDiscoveryControlSnapshot(
        TournamentEventDiscoveryState state,
        Instant changedAt,
        UUID requestId,
        String confirmationPhrase,
        Instant preparedAt,
        Instant expiresAt,
        LocalDate collectionDate,
        J3TournamentCatalogOption selection,
        String terminalCode,
        boolean providerTransportAvailable,
        List<String> providerBlockers) {

    public TournamentEventDiscoveryControlSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(changedAt, "changedAt");
        providerBlockers = List.copyOf(Objects.requireNonNull(
                providerBlockers, "providerBlockers"));
        if (providerTransportAvailable != providerBlockers.isEmpty()) {
            throw new IllegalArgumentException(
                    "provider availability and blockers must be mutually exclusive");
        }
        if (state == TournamentEventDiscoveryState.AWAITING_CONFIRMATION) {
            if (requestId == null
                    || confirmationPhrase == null
                    || preparedAt == null
                    || expiresAt == null
                    || !expiresAt.isAfter(preparedAt)
                    || collectionDate == null
                    || selection == null
                    || terminalCode != null) {
                throw new IllegalArgumentException(
                        "awaiting confirmation requires one complete bounded intent");
            }
        }
        if (state == TournamentEventDiscoveryState.EXECUTING
                && (requestId == null || collectionDate == null || selection == null)) {
            throw new IllegalArgumentException("executing discovery requires its selection");
        }
    }

    public boolean awaitingConfirmation() {
        return state == TournamentEventDiscoveryState.AWAITING_CONFIRMATION;
    }

    public boolean executing() {
        return state == TournamentEventDiscoveryState.EXECUTING;
    }

    public boolean terminalLocked() {
        return state == TournamentEventDiscoveryState.FAILED_LOCKED
                || state == TournamentEventDiscoveryState.STOPPED_LOCKED
                || state == TournamentEventDiscoveryState.EXPIRED_LOCKED;
    }

    public boolean completed() {
        return state == TournamentEventDiscoveryState.COMPLETED;
    }

    public Optional<J3TournamentCatalogOption> selectedOption() {
        return Optional.ofNullable(selection);
    }
}
