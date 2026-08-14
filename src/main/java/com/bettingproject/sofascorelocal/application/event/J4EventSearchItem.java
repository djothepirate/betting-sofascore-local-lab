package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;

import java.time.ZonedDateTime;
import java.util.Objects;

public record J4EventSearchItem(
        CanonicalEventObservationView event,
        ZonedDateTime startsAtInZone) {

    public J4EventSearchItem {
        event = Objects.requireNonNull(event, "event");
        startsAtInZone = Objects.requireNonNull(startsAtInZone, "startsAtInZone");
    }
}
