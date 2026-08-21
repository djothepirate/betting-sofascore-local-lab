package com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents;

import java.util.Objects;

public record TournamentScheduledEventsParseWarning(
        Code code,
        String path,
        String message) {

    public enum Code {
        EMPTY_EVENTS,
        UNKNOWN_FIELD,
        WARNING_LIMIT_REACHED
    }

    public TournamentScheduledEventsParseWarning {
        code = Objects.requireNonNull(code, "code");
        path = requireText(path, "path");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
