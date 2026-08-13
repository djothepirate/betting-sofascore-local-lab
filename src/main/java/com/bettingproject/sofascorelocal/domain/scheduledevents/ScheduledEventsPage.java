package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.util.List;
import java.util.Objects;

public record ScheduledEventsPage(
        PayloadShape payloadShape,
        List<ScheduledEvent> events,
        List<ScheduledTournamentAvailability> scheduledTournaments,
        boolean hasNextPage) {

    public ScheduledEventsPage {
        payloadShape = Objects.requireNonNull(payloadShape, "payloadShape");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        scheduledTournaments = List.copyOf(Objects.requireNonNull(
                scheduledTournaments,
                "scheduledTournaments"));
        if (payloadShape == PayloadShape.EVENT_LIST && !scheduledTournaments.isEmpty()) {
            throw new IllegalArgumentException(
                    "An event-list page cannot contain scheduled tournaments");
        }
        if (payloadShape == PayloadShape.SCHEDULED_TOURNAMENT_LIST && !events.isEmpty()) {
            throw new IllegalArgumentException(
                    "A scheduled-tournament page cannot contain event-list entries");
        }
    }

    public enum PayloadShape {
        EVENT_LIST,
        SCHEDULED_TOURNAMENT_LIST
    }
}
