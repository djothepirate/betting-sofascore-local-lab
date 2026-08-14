package com.bettingproject.sofascorelocal.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public record J4EventSearchResult(
        LocalDate date,
        ZoneId zoneId,
        Instant fromInclusive,
        Instant toExclusive,
        List<J4EventSearchItem> events) {

    public J4EventSearchResult {
        date = Objects.requireNonNull(date, "date");
        zoneId = Objects.requireNonNull(zoneId, "zoneId");
        fromInclusive = Objects.requireNonNull(fromInclusive, "fromInclusive");
        toExclusive = Objects.requireNonNull(toExclusive, "toExclusive");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }
}
