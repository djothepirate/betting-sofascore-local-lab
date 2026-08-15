package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public record J4RealPhase1ExecutionClaim(UUID requestId, URI providerOrigin) {

    public J4RealPhase1ExecutionClaim {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(providerOrigin, "providerOrigin");
        EventDetailsProviderRequest.parseExactProviderOrigin(providerOrigin.toString());
    }
}
