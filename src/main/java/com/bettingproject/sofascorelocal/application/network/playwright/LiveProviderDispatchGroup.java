package com.bettingproject.sofascorelocal.application.network.playwright;

import java.util.Objects;
import java.util.UUID;

/** Server-created scope for one bounded, sequential live-v4 event group. */
public record LiveProviderDispatchGroup(UUID campaignId, UUID groupId,
                                       long providerEventId, Phase phase) {
    public enum Phase { CHECK, PREMATCH, IN_PLAY, FINALIZING }

    public LiveProviderDispatchGroup {
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(phase, "phase");
        if (providerEventId < 1 || providerEventId > 999_999_999L)
            throw new IllegalArgumentException("invalid live group event");
    }
}
