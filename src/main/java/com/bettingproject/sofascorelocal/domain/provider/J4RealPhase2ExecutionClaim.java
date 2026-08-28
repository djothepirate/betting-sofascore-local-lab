package com.bettingproject.sofascorelocal.domain.provider;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public record J4RealPhase2ExecutionClaim(
        UUID requestId,
        URI providerOrigin,
        UUID canonicalEventId,
        long eventId) {

    public J4RealPhase2ExecutionClaim {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(providerOrigin, "providerOrigin");
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        EventDetailsProviderRequest.parseExactProviderOrigin(providerOrigin.toString());
        EventDetailsProviderRequest.requirePhase2EventId(eventId);
        new CanonicalEventIdentity(
                canonicalEventId,
                CanonicalEventIdentity.SOFASCORE,
                eventId);
    }
}
