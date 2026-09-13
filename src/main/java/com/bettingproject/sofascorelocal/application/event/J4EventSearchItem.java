package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;

import java.time.ZonedDateTime;
import java.util.Objects;

public record J4EventSearchItem(
        CanonicalEventObservationView event,
        ZonedDateTime startsAtInZone,
        J4EventResult result) {

    public J4EventSearchItem(CanonicalEventObservationView event, ZonedDateTime startsAtInZone) {
        this(event, startsAtInZone, J4EventResult.absent());
    }

    public J4EventSearchItem {
        event = Objects.requireNonNull(event, "event");
        startsAtInZone = Objects.requireNonNull(startsAtInZone, "startsAtInZone");
        result = Objects.requireNonNull(result, "result");
    }

    public String sportStatusLabel() { return result.statusLabel(event.status()); }
}
