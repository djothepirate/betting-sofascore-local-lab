package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import java.util.Objects;

public record EventDetailsParseProblem(Code code, String path, String message) {

    public enum Code {
        INVALID_JSON,
        REQUIRED_FIELD_MISSING,
        TYPE_MISMATCH,
        UNEXPECTED_CONTENT_KIND,
        UNSUPPORTED_ENDPOINT,
        UNSUPPORTED_PARSER_VERSION,
        VALUE_OUT_OF_RANGE
    }

    public EventDetailsParseProblem {
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
