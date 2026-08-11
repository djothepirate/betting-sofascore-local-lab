package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ScheduledEvent(
        long providerEventId,
        Instant startsAt,
        ScheduledTeam homeTeam,
        ScheduledTeam awayTeam,
        ScheduledEventStatus status,
        Optional<ScheduledTournament> tournament) {

    public ScheduledEvent {
        if (providerEventId <= 0) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
        homeTeam = Objects.requireNonNull(homeTeam, "homeTeam");
        awayTeam = Objects.requireNonNull(awayTeam, "awayTeam");
        status = Objects.requireNonNull(status, "status");
        tournament = Objects.requireNonNull(tournament, "tournament");
    }
}
