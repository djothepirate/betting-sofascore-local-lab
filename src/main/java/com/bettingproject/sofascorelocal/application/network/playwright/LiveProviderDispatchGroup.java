package com.bettingproject.sofascorelocal.application.network.playwright;

import java.util.Objects;
import java.util.UUID;

/** Server-created scope for one bounded event group; its authority is fixed when the campaign opens. */
public record LiveProviderDispatchGroup(UUID campaignId, UUID groupId,
                                       long providerEventId, Phase phase) {
    public enum Phase { CHECK, PREMATCH, IN_PLAY, FINALIZING, MANUAL_J5 }

    public LiveProviderDispatchGroup {
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(phase, "phase");
        if (providerEventId < 1 || providerEventId > 999_999_999L)
            throw new IllegalArgumentException("invalid live group event");
    }
}
