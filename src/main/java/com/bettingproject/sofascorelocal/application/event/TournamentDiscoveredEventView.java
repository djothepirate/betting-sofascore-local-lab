package com.bettingproject.sofascorelocal.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TournamentDiscoveredEventView(
        UUID canonicalEventId,
        long providerEventId,
        Instant startsAt,
        String homeTeamName,
        String awayTeamName,
        String statusType) {

    public TournamentDiscoveredEventView {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        Objects.requireNonNull(startsAt, "startsAt");
        homeTeamName = requireText(homeTeamName, "homeTeamName");
        awayTeamName = requireText(awayTeamName, "awayTeamName");
        statusType = requireText(statusType, "statusType");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
