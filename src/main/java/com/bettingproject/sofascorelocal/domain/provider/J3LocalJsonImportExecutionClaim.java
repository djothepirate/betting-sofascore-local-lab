package com.bettingproject.sofascorelocal.domain.provider;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record J3LocalJsonImportExecutionClaim(
        UUID requestId,
        LocalDate date,
        int firstPage) {

    public J3LocalJsonImportExecutionClaim {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(date, "date");
        if (firstPage != ScheduledEventsProviderPageRequest.FIRST_PAGE) {
            throw new IllegalArgumentException("a local import must start at page 1");
        }
    }
}
