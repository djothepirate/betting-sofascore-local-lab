package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import java.util.List;
import java.util.Objects;

public record ScheduledEventsEnvelopeDto(
        List<ScheduledEventDto> events,
        boolean hasNextPage) {

    public ScheduledEventsEnvelopeDto {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }

    public record ScheduledEventDto(
            long id,
            long startTimestamp,
            TeamDto homeTeam,
            TeamDto awayTeam,
            StatusDto status,
            TournamentDto tournament) {

        public ScheduledEventDto {
            homeTeam = Objects.requireNonNull(homeTeam, "homeTeam");
            awayTeam = Objects.requireNonNull(awayTeam, "awayTeam");
            status = Objects.requireNonNull(status, "status");
        }
    }

    public record TeamDto(long id, String name) {
    }

    public record StatusDto(String type, String description) {
    }

    public record TournamentDto(long id, String name) {
    }
}
