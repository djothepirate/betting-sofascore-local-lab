package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import java.util.Objects;

public record EventDetailsParseWarning(Code code, String path, String message) {

    public enum Code {
        OPTIONAL_FIELD_MISSING,
        UNKNOWN_FIELD
    }

    public EventDetailsParseWarning {
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
