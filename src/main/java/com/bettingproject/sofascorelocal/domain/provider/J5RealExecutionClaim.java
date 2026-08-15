package com.bettingproject.sofascorelocal.domain.provider;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public record J5RealExecutionClaim(
        UUID requestId,
        URI providerOrigin,
        UUID canonicalEventId,
        long eventId) {

    public J5RealExecutionClaim {
        requestId = Objects.requireNonNull(requestId, "requestId");
        providerOrigin = Objects.requireNonNull(providerOrigin, "providerOrigin");
        EventDetailsProviderRequest.parseExactProviderOrigin(providerOrigin.toString());
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        J5EventDataProviderRequest.requireEventId(eventId);
        if (!CanonicalEventIdentity.sofascore(eventId).value().equals(canonicalEventId)) {
            throw new IllegalArgumentException("canonical event and provider id must match");
        }
    }
}
