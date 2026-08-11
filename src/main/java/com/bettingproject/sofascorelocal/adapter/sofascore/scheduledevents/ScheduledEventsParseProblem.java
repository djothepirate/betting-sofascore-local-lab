package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import java.util.Objects;

public record ScheduledEventsParseProblem(Code code, String path, String message) {

    public enum Code {
        INVALID_JSON,
        REQUIRED_FIELD_MISSING,
        TYPE_MISMATCH,
        UNEXPECTED_CONTENT_KIND,
        UNSUPPORTED_ENDPOINT,
        VALUE_OUT_OF_RANGE
    }

    public ScheduledEventsParseProblem {
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
