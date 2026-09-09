package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Session-only allowance; never restores a campaign or creates a transport. */
final class LiveTimeoutRecoveryPolicy {
    static final Duration RETRY_DELAY = Duration.ofMinutes(5);
    private static final int MAXIMUM_RECOVERIES = 3;
    private record Family(UUID eventId, SofascoreEndpointType endpoint) { }
    private final Set<Family> awaitingSuccess = new HashSet<>();
    private int recoveries;
    private boolean previousWasTimeout;

    boolean admit(UUID eventId, SofascoreEndpointType endpoint) {
        Family family = new Family(eventId, endpoint);
        if (previousWasTimeout || recoveries >= MAXIMUM_RECOVERIES || awaitingSuccess.contains(family)) return false;
        recoveries++;
        previousWasTimeout = true;
        awaitingSuccess.add(family);
        return true;
    }

    void successful(UUID eventId, SofascoreEndpointType endpoint) {
        previousWasTimeout = false;
        awaitingSuccess.remove(new Family(eventId, endpoint));
    }
}
