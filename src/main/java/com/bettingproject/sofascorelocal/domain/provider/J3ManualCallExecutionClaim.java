package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record J3ManualCallExecutionClaim(
        UUID requestId,
        LocalDate date,
        URI providerOrigin,
        int firstPage) {

    public J3ManualCallExecutionClaim {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(providerOrigin, "providerOrigin");
        ScheduledEventsProviderPageRequest.parseExactProviderOrigin(
                providerOrigin.toString());
        if (firstPage < ScheduledEventsProviderPageRequest.FIRST_PAGE
                || firstPage > ScheduledEventsProviderPageRequest.LAST_PAGE) {
            throw new IllegalArgumentException("firstPage must be between 1 and 5");
        }
    }
}
