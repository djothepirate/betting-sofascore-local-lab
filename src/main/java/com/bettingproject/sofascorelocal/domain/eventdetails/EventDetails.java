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
        Optional<String> round,
        Optional<Boolean> isAwarded,
        Optional<Integer> homeDisplayScore,
        Optional<Integer> awayDisplayScore) {

    /** Historical V1/V2 contract: no award flag or displayed score was normalized. */
    public EventDetails(
            long providerEventId,
            Instant startsAt,
            ScheduledTeam homeTeam,
            ScheduledTeam awayTeam,
            ScheduledEventStatus status,
            Optional<ScheduledTournament> tournament,
            Optional<EventVenue> venue,
            Optional<EventSeason> season,
            Optional<String> round) {
        this(providerEventId, startsAt, homeTeam, awayTeam, status, tournament, venue,
                season, round, Optional.empty(), Optional.empty(), Optional.empty());
    }

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
        isAwarded = Objects.requireNonNull(isAwarded, "isAwarded");
        homeDisplayScore = requireDisplayScore(homeDisplayScore, "homeDisplayScore");
        awayDisplayScore = requireDisplayScore(awayDisplayScore, "awayDisplayScore");
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

    private static Optional<Integer> requireDisplayScore(Optional<Integer> score, String field) {
        Objects.requireNonNull(score, field);
        score.ifPresent(value -> {
            if (value < 0 || value > 999) {
                throw new IllegalArgumentException(field + " must be between 0 and 999");
            }
        });
        return score;
    }
}
