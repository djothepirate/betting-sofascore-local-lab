package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public record J4RealPhase2ExecutionClaim(
        UUID requestId,
        URI providerOrigin,
        long eventId) {

    public J4RealPhase2ExecutionClaim {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(providerOrigin, "providerOrigin");
        EventDetailsProviderRequest.parseExactProviderOrigin(providerOrigin.toString());
        EventDetailsProviderRequest.requirePhase2EventId(eventId);
    }
}
