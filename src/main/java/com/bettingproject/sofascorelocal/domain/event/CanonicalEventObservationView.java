package com.bettingproject.sofascorelocal.domain.event;

import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CanonicalEventObservationView(
        long observationId,
        CanonicalEventIdentity identity,
        Instant startsAt,
        ScheduledTeam homeTeam,
        ScheduledTeam awayTeam,
        ScheduledEventStatus status,
        Optional<ScheduledTournament> tournament,
        EventSourceTrace source,
        String normalizedSha256,
        long observationCount) {

    public CanonicalEventObservationView {
        if (observationId < 1 || observationCount < 1) {
            throw new IllegalArgumentException("observation identifiers and count must be positive");
        }
        identity = Objects.requireNonNull(identity, "identity");
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
        homeTeam = Objects.requireNonNull(homeTeam, "homeTeam");
        awayTeam = Objects.requireNonNull(awayTeam, "awayTeam");
        status = Objects.requireNonNull(status, "status");
        tournament = Objects.requireNonNull(tournament, "tournament");
        source = Objects.requireNonNull(source, "source");
        normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
    }
}
