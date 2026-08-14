package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ScheduledEventsEnvelopeDto(
        PayloadShape payloadShape,
        List<ScheduledEventDto> events,
        List<ScheduledTournamentAvailabilityDto> scheduledTournaments,
        boolean hasNextPage) {

    public ScheduledEventsEnvelopeDto {
        payloadShape = Objects.requireNonNull(payloadShape, "payloadShape");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        scheduledTournaments = List.copyOf(Objects.requireNonNull(
                scheduledTournaments,
                "scheduledTournaments"));
        if (payloadShape == PayloadShape.EVENT_LIST && !scheduledTournaments.isEmpty()) {
            throw new IllegalArgumentException(
                    "An event-list payload cannot contain scheduled tournaments");
        }
        if (payloadShape == PayloadShape.SCHEDULED_TOURNAMENT_LIST && !events.isEmpty()) {
            throw new IllegalArgumentException(
                    "A scheduled-tournament payload cannot contain event-list entries");
        }
    }

    public enum PayloadShape {
        EVENT_LIST,
        SCHEDULED_TOURNAMENT_LIST
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

    public record ScheduledTournamentAvailabilityDto(
            TournamentDto tournament,
            TournamentDto uniqueTournament,
            Map<Integer, Integer> timezoneEventCount) {

        public ScheduledTournamentAvailabilityDto {
            tournament = Objects.requireNonNull(tournament, "tournament");
            timezoneEventCount = Map.copyOf(Objects.requireNonNull(
                    timezoneEventCount,
                    "timezoneEventCount"));
        }
    }
}
