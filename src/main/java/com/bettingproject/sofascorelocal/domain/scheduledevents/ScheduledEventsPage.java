package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.util.List;
import java.util.Objects;

public record ScheduledEventsPage(List<ScheduledEvent> events, boolean hasNextPage) {

    public ScheduledEventsPage {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }
}
