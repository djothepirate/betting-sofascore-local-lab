package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;

import java.util.Objects;
import java.util.Optional;

public record J3ScheduledEventsOutcome(
        Optional<RawSnapshotPersistenceResult> persistence,
        J3CircuitSnapshot circuit,
        boolean retryScheduled) {

    public J3ScheduledEventsOutcome {
        persistence = Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(circuit, "circuit");
        if (retryScheduled) {
            throw new IllegalArgumentException(
                    "J3 stop and incident policies must never schedule a retry");
        }
    }

    public static J3ScheduledEventsOutcome recorded(
            RawSnapshotPersistenceResult persistence,
            J3CircuitSnapshot circuit) {
        return new J3ScheduledEventsOutcome(
                Optional.of(Objects.requireNonNull(persistence, "persistence")),
                circuit,
                false);
    }

    public static J3ScheduledEventsOutcome failedBeforeSnapshot(
            J3CircuitSnapshot circuit) {
        return new J3ScheduledEventsOutcome(Optional.empty(), circuit, false);
    }
}
