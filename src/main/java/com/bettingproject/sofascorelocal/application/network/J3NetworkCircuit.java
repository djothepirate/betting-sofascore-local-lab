package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitIncident;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;

import java.time.Instant;
import java.util.Objects;

/**
 * In-memory state model only. It neither resolves an endpoint nor performs transport.
 */
public final class J3NetworkCircuit {

    private J3CircuitSnapshot snapshot;

    private J3NetworkCircuit(Instant startedAt) {
        snapshot = new J3CircuitSnapshot(
                J3CircuitState.LOCKED,
                J3CircuitReason.STARTUP_LOCK,
                Objects.requireNonNull(startedAt, "startedAt"),
                null);
    }

    public static J3NetworkCircuit lockedAt(Instant startedAt) {
        return new J3NetworkCircuit(startedAt);
    }

    public synchronized J3CircuitSnapshot snapshot() {
        return snapshot;
    }

    public synchronized J3CircuitSnapshot activateByOperator(Instant activatedAt) {
        requireChronological(activatedAt);
        if (snapshot.state() == J3CircuitState.OPEN) {
            throw new IllegalStateException(
                    "an open circuit must be stopped and reviewed before reactivation");
        }
        if (snapshot.state() != J3CircuitState.LOCKED) {
            throw new IllegalStateException("only a locked circuit can be activated");
        }
        snapshot = new J3CircuitSnapshot(
                J3CircuitState.CLOSED,
                J3CircuitReason.NONE,
                activatedAt,
                null);
        return snapshot;
    }

    public synchronized J3CircuitSnapshot stopByOperator(Instant stoppedAt) {
        requireChronological(stoppedAt);
        snapshot = new J3CircuitSnapshot(
                J3CircuitState.LOCKED,
                J3CircuitReason.OPERATOR_STOP,
                stoppedAt,
                null);
        return snapshot;
    }

    public synchronized J3CircuitSnapshot lockAfterCollection(Instant lockedAt) {
        requireChronological(lockedAt);
        if (snapshot.state() == J3CircuitState.LOCKED) {
            throw new IllegalStateException("a locked circuit cannot be terminally locked again");
        }
        snapshot = new J3CircuitSnapshot(
                J3CircuitState.LOCKED,
                J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK,
                lockedAt,
                null);
        return snapshot;
    }

    public synchronized J3CircuitSnapshot recordIncident(J3CircuitIncident incident) {
        Objects.requireNonNull(incident, "incident");
        requireChronological(incident.occurredAt());
        if (snapshot.state() != J3CircuitState.CLOSED) {
            throw new IllegalStateException("an incident can only be recorded while the circuit is closed");
        }
        snapshot = new J3CircuitSnapshot(
                J3CircuitState.OPEN,
                incident.reason(),
                incident.occurredAt(),
                incident.retryNotBefore());
        return snapshot;
    }

    private void requireChronological(Instant transitionAt) {
        Objects.requireNonNull(transitionAt, "transitionAt");
        if (transitionAt.isBefore(snapshot.changedAt())) {
            throw new IllegalArgumentException("circuit transitions must be chronological");
        }
    }
}
