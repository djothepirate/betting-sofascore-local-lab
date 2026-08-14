package com.bettingproject.sofascorelocal.domain.eventdetails;

import java.util.Objects;

public record EventSeason(long providerSeasonId, String name) {

    public EventSeason {
        if (providerSeasonId < 1) {
            throw new IllegalArgumentException("providerSeasonId must be positive");
        }
        Objects.requireNonNull(name, "name");
        name = name.trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("name must be bounded non-blank text");
        }
    }
}
