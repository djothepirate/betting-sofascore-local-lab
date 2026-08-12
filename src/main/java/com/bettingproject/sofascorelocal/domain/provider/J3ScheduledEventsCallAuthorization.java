package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.util.Objects;

public record J3ScheduledEventsCallAuthorization(
        boolean featureEnabled,
        boolean globalStopActive,
        boolean endpointAllowed,
        boolean endpointConfigured,
        boolean operatorActivated,
        boolean manualConfirmationPresent,
        boolean cacheHit,
        J3CircuitSnapshot circuit,
        Duration minimumDelay) {

    public J3ScheduledEventsCallAuthorization {
        Objects.requireNonNull(circuit, "circuit");
        Objects.requireNonNull(minimumDelay, "minimumDelay");
        if (minimumDelay.compareTo(Duration.ofSeconds(3)) < 0) {
            throw new IllegalArgumentException("minimumDelay must be at least three seconds");
        }
    }
}
