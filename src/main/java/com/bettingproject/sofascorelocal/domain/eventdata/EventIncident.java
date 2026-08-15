package com.bettingproject.sofascorelocal.domain.eventdata;

import java.util.Objects;
import java.util.Optional;

public record EventIncident(
        int sequence,
        String incidentType,
        int minute,
        Optional<Integer> addedTime,
        Optional<Boolean> home,
        Optional<Long> participantProviderId,
        Optional<Long> playerProviderId,
        Optional<String> playerName,
        Optional<Integer> homeScore,
        Optional<Integer> awayScore) {

    public EventIncident {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence cannot be negative");
        }
        incidentType = boundedText(incidentType, "incidentType", 64);
        if (minute < 0 || minute > 300) {
            throw new IllegalArgumentException("minute must be between 0 and 300");
        }
        addedTime = boundedInteger(addedTime, "addedTime", 0, 30);
        home = Objects.requireNonNull(home, "home");
        participantProviderId = positiveLong(participantProviderId, "participantProviderId");
        playerProviderId = positiveLong(playerProviderId, "playerProviderId");
        playerName = Objects.requireNonNull(playerName, "playerName")
                .map(value -> boundedText(value, "playerName", 200));
        if (playerProviderId.isPresent() != playerName.isPresent()) {
            throw new IllegalArgumentException("player id and name must be present together");
        }
        homeScore = boundedInteger(homeScore, "homeScore", 0, 99);
        awayScore = boundedInteger(awayScore, "awayScore", 0, 99);
        if (homeScore.isPresent() != awayScore.isPresent()) {
            throw new IllegalArgumentException("home and away scores must be present together");
        }
    }

    public String sideLabel() {
        return home.map(value -> value ? "HOME" : "AWAY").orElse("UNKNOWN");
    }

    private static Optional<Long> positiveLong(Optional<Long> value, String name) {
        Optional<Long> normalized = Objects.requireNonNull(value, name);
        if (normalized.isPresent() && normalized.orElseThrow() < 1) {
            throw new IllegalArgumentException(name + " must be positive when present");
        }
        return normalized;
    }

    private static Optional<Integer> boundedInteger(
            Optional<Integer> value,
            String name,
            int minimum,
            int maximum) {
        Optional<Integer> normalized = Objects.requireNonNull(value, name);
        if (normalized.isPresent()) {
            int item = normalized.orElseThrow();
            if (item < minimum || item > maximum) {
                throw new IllegalArgumentException(name + " is outside the accepted range");
            }
        }
        return normalized;
    }

    private static String boundedText(String value, String name, int maximumLength) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()
                || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be bounded non-control text");
        }
        return normalized;
    }
}
