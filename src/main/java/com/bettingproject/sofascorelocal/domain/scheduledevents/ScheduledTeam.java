package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.util.Objects;

public record ScheduledTeam(long providerTeamId, String name) {

    public ScheduledTeam {
        if (providerTeamId <= 0) {
            throw new IllegalArgumentException("providerTeamId must be positive");
        }
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
