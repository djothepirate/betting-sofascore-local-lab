package com.bettingproject.sofascorelocal.domain.eventdata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TeamLineup(
        LineupSide side,
        Optional<String> formation,
        List<EventLineupPlayer> players,
        Optional<List<MissingLineupPlayer>> missingPlayers) {

    public TeamLineup(LineupSide side, Optional<String> formation, List<EventLineupPlayer> players) {
        this(side, formation, players, Optional.empty());
    }

    public TeamLineup {
        side = Objects.requireNonNull(side, "side");
        formation = Objects.requireNonNull(formation, "formation")
                .map(value -> boundedText(value, "formation", 32));
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        missingPlayers = Objects.requireNonNull(missingPlayers, "missingPlayers").map(values -> {
            if (values.size() > 128) throw new IllegalArgumentException("too many missing players");
            return List.copyOf(values);
        });
    }

    public boolean hasEnrichedData() {
        return missingPlayers.isPresent() || players.stream()
                .anyMatch(player -> player.captain().isPresent() || player.statistics().isPresent() || player.country().isPresent());
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
