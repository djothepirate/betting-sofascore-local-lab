package com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents;

import java.util.Objects;

public record TournamentScheduledEventsParseProblem(
        Code code,
        String path,
        String message) {

    public enum Code {
        INVALID_JSON,
        PAGINATION_UNSUPPORTED,
        REQUIRED_FIELD_MISSING,
        TEXT_CONTAINS_CONTROL_CHARACTER,
        TEXT_TOO_LONG,
        TYPE_MISMATCH,
        UNEXPECTED_CONTENT_KIND,
        UNSUPPORTED_ENDPOINT,
        VALUE_OUT_OF_RANGE
    }

    public TournamentScheduledEventsParseProblem {
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
