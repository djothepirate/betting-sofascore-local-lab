package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import java.util.Objects;

public record J5ParseProblem(Code code, String path, String message) {

    public enum Code {
        UNSUPPORTED_ENDPOINT,
        UNSUPPORTED_PARSER_VERSION,
        UNEXPECTED_CONTENT_KIND,
        INVALID_JSON,
        REQUIRED_FIELD_MISSING,
        TYPE_MISMATCH,
        VALUE_OUT_OF_RANGE,
        VALUE_TOO_LONG,
        ATOMIC_FIELD_MISMATCH
    }

    public J5ParseProblem {
        code = Objects.requireNonNull(code, "code");
        path = requireText(path, "path", 300);
        message = requireText(message, "message", 300);
    }

    private static String requireText(String value, String name, int maximumLength) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must be bounded non-blank text");
        }
        return normalized;
    }
}
