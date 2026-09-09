package com.bettingproject.sofascorelocal.domain.eventdata;

import java.util.Objects;
import java.util.Optional;

public record EventLineupPlayer(
        long providerPlayerId,
        String name,
        Optional<Integer> shirtNumber,
        Optional<String> position,
        boolean starter,
        Optional<Boolean> captain,
        Optional<PlayerMatchStatistics> statistics) {

    public EventLineupPlayer(long providerPlayerId, String name, Optional<Integer> shirtNumber,
            Optional<String> position, boolean starter) {
        this(providerPlayerId, name, shirtNumber, position, starter, Optional.empty(), Optional.empty());
    }

    public EventLineupPlayer(long providerPlayerId, String name, Optional<Integer> shirtNumber,
            Optional<String> position, boolean starter, Optional<Boolean> captain) {
        this(providerPlayerId, name, shirtNumber, position, starter, captain, Optional.empty());
    }

    public EventLineupPlayer {
        if (providerPlayerId < 1) {
            throw new IllegalArgumentException("providerPlayerId must be positive");
        }
        name = boundedText(name, "name", 200);
        shirtNumber = Objects.requireNonNull(shirtNumber, "shirtNumber");
        if (shirtNumber.isPresent()) {
            int value = shirtNumber.orElseThrow();
            if (value < 1 || value > 999) {
                throw new IllegalArgumentException("shirtNumber must be between 1 and 999");
            }
        }
        position = Objects.requireNonNull(position, "position")
                .map(value -> boundedText(value, "position", 32));
        captain = Objects.requireNonNull(captain, "captain");
        statistics = Objects.requireNonNull(statistics, "statistics");
    }

    private static String boundedText(String value, String fieldName, int maximumLength) {
        String normalized = Objects.requireNonNull(value, fieldName).trim();
        if (normalized.isEmpty()
                || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + " must be bounded non-control text");
        }
        return normalized;
    }
}
