package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record J3ManualCallExecutionClaim(
        UUID requestId,
        LocalDate date,
        URI providerOrigin) {

    public J3ManualCallExecutionClaim {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(providerOrigin, "providerOrigin");
        ScheduledEventsProviderPageRequest.parseExactProviderOrigin(
                providerOrigin.toString());
    }
}
