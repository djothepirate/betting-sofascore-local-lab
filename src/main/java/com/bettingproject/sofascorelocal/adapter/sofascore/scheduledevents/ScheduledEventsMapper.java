package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.ScheduledEventDto;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.TeamDto;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.TournamentDto;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;

import java.time.Instant;
import java.util.Optional;

public final class ScheduledEventsMapper {

    public ScheduledEventsPage map(ScheduledEventsEnvelopeDto source) {
        return new ScheduledEventsPage(
                source.events().stream().map(this::mapEvent).toList(),
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
}
