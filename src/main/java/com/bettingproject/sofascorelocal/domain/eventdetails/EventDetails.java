package com.bettingproject.sofascorelocal.domain.eventdetails;

import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record EventDetails(
        long providerEventId,
        Instant startsAt,
        ScheduledTeam homeTeam,
        ScheduledTeam awayTeam,
        ScheduledEventStatus status,
        Optional<ScheduledTournament> tournament,
        Optional<EventVenue> venue,
        Optional<EventSeason> season,
        Optional<String> round) {

    public EventDetails {
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
        homeTeam = Objects.requireNonNull(homeTeam, "homeTeam");
        awayTeam = Objects.requireNonNull(awayTeam, "awayTeam");
        status = Objects.requireNonNull(status, "status");
        tournament = Objects.requireNonNull(tournament, "tournament");
        venue = Objects.requireNonNull(venue, "venue");
        season = Objects.requireNonNull(season, "season");
        round = Objects.requireNonNull(round, "round").map(value -> {
            String normalized = value.trim();
            if (normalized.isEmpty() || normalized.length() > 64) {
                throw new IllegalArgumentException("round must be bounded non-blank text");
            }
            return normalized;
        });
    }

    public ScheduledEvent asScheduledEvent() {
        return new ScheduledEvent(
                providerEventId,
                startsAt,
                homeTeam,
                awayTeam,
                status,
                tournament);
    }
}
