package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.ScheduledEventDto;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.ScheduledTournamentAvailabilityDto;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.TeamDto;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.TournamentDto;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournamentAvailability;

import java.time.Instant;
import java.util.Optional;

public final class ScheduledEventsMapper {

    public ScheduledEventsPage map(ScheduledEventsEnvelopeDto source) {
        return new ScheduledEventsPage(
                switch (source.payloadShape()) {
                    case EVENT_LIST -> ScheduledEventsPage.PayloadShape.EVENT_LIST;
                    case SCHEDULED_TOURNAMENT_LIST ->
                        ScheduledEventsPage.PayloadShape.SCHEDULED_TOURNAMENT_LIST;
                },
                source.events().stream().map(this::mapEvent).toList(),
                source.scheduledTournaments().stream()
                        .map(this::mapScheduledTournament)
                        .toList(),
                source.hasNextPage());
    }

    private ScheduledEvent mapEvent(ScheduledEventDto source) {
        return new ScheduledEvent(
                source.id(),
                Instant.ofEpochSecond(source.startTimestamp()),
                mapTeam(source.homeTeam()),
                mapTeam(source.awayTeam()),
                new ScheduledEventStatus(
                        source.status().type(),
                        Optional.ofNullable(source.status().description())),
                Optional.ofNullable(source.tournament()).map(this::mapTournament));
    }

    private ScheduledTeam mapTeam(TeamDto source) {
        return new ScheduledTeam(source.id(), source.name());
    }

    private ScheduledTournament mapTournament(TournamentDto source) {
        return new ScheduledTournament(source.id(), source.name());
    }

    private ScheduledTournamentAvailability mapScheduledTournament(
            ScheduledTournamentAvailabilityDto source) {
        return new ScheduledTournamentAvailability(
                mapTournament(source.tournament()),
                Optional.ofNullable(source.tournamentCategory())
                        .map(category -> category.name()),
                Optional.ofNullable(source.uniqueTournament()).map(this::mapTournament),
                source.timezoneEventCount());
    }
}
