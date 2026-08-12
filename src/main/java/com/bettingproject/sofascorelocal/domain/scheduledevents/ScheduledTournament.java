package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.util.Objects;

public record ScheduledTournament(long providerTournamentId, String name) {

    public ScheduledTournament {
        if (providerTournamentId <= 0) {
            throw new IllegalArgumentException("providerTournamentId must be positive");
        }
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
