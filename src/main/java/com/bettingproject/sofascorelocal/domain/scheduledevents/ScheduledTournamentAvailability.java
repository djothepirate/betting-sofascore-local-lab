package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public record ScheduledTournamentAvailability(
        ScheduledTournament tournament,
        Optional<ScheduledTournament> uniqueTournament,
        Map<Integer, Integer> timezoneEventCount) {

    public ScheduledTournamentAvailability {
        tournament = Objects.requireNonNull(tournament, "tournament");
        uniqueTournament = Objects.requireNonNull(uniqueTournament, "uniqueTournament");
        Objects.requireNonNull(timezoneEventCount, "timezoneEventCount");
        timezoneEventCount = Map.copyOf(new TreeMap<>(timezoneEventCount));
        if (timezoneEventCount.values().stream().anyMatch(count -> count == null || count < 0)) {
            throw new IllegalArgumentException(
                    "timezoneEventCount values must be non-negative");
        }
    }
}
