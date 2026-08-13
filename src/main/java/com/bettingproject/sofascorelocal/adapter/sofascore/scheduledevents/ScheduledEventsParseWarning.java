package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import java.util.Objects;

public record ScheduledEventsParseWarning(Code code, String path, String message) {

    public enum Code {
        EMPTY_EVENTS,
        EMPTY_SCHEDULED_TOURNAMENTS,
        EMPTY_TIMEZONE_EVENT_COUNT,
        OPTIONAL_FIELD_MISSING,
        UNKNOWN_FIELD
    }

    public ScheduledEventsParseWarning {
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
