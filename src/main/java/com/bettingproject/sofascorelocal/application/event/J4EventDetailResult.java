package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record J4EventDetailResult(
        ZoneId zoneId,
        J4EventSearchItem current,
        List<J4EventSearchItem> history,
        Optional<EventDetailObservationView> offlineDetail) {

    public J4EventDetailResult {
        zoneId = Objects.requireNonNull(zoneId, "zoneId");
        current = Objects.requireNonNull(current, "current");
        history = List.copyOf(Objects.requireNonNull(history, "history"));
        offlineDetail = Objects.requireNonNull(offlineDetail, "offlineDetail");
    }
}
