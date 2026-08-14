package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record J3ManualCallControlSnapshot(
        boolean globalStopActive,
        J3CircuitState circuitState,
        J3CircuitReason circuitReason,
        Instant circuitChangedAt,
        Instant retryNotBefore,
        LocalDate suggestedDate,
        J3ManualCallIntentSnapshot intent,
        boolean providerTransportAvailable,
        List<String> providerBlockers) {

    public J3ManualCallControlSnapshot {
        Objects.requireNonNull(circuitState, "circuitState");
        Objects.requireNonNull(circuitReason, "circuitReason");
        Objects.requireNonNull(circuitChangedAt, "circuitChangedAt");
        Objects.requireNonNull(suggestedDate, "suggestedDate");
        providerBlockers = List.copyOf(Objects.requireNonNull(
                providerBlockers,
                "providerBlockers"));
        if (providerTransportAvailable == !providerBlockers.isEmpty()) {
            throw new IllegalArgumentException(
                    "provider availability and blockers must be mutually exclusive");
        }
    }

    public boolean operatorActivated() {
        return circuitState == J3CircuitState.CLOSED && !globalStopActive;
    }

    public boolean incidentActive() {
        return circuitState == J3CircuitState.OPEN;
    }
}
