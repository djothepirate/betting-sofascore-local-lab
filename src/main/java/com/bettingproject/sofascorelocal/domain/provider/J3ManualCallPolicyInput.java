package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record J3ManualCallPolicyInput(
        SofascoreEndpointType endpointType,
        boolean featureEnabled,
        boolean globalStopActive,
        boolean endpointAllowed,
        boolean endpointConfigured,
        boolean operatorActivated,
        boolean manualConfirmationPresent,
        boolean cacheHit,
        J3CircuitSnapshot circuit,
        boolean callInProgress,
        Instant lastTransportStartedAt,
        Instant evaluatedAt,
        Duration minimumDelay) {

    public J3ManualCallPolicyInput {
        Objects.requireNonNull(endpointType, "endpointType");
        Objects.requireNonNull(circuit, "circuit");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(minimumDelay, "minimumDelay");
        if (minimumDelay.compareTo(Duration.ofSeconds(3)) < 0) {
            throw new IllegalArgumentException("minimumDelay must be at least three seconds");
        }
        if (lastTransportStartedAt != null && lastTransportStartedAt.isAfter(evaluatedAt)) {
            throw new IllegalArgumentException("lastTransportStartedAt cannot be in the future");
        }
        if (circuit.changedAt().isAfter(evaluatedAt)) {
            throw new IllegalArgumentException("circuit state cannot originate in the future");
        }
    }
}
