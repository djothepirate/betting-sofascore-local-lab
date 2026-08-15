package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import java.util.Objects;

public record J5ParseWarning(Code code, String path, String message) {

    public enum Code {
        UNKNOWN_FIELD,
        OPTIONAL_FIELD_MISSING,
        PROVIDER_SENTINEL_NORMALIZED
    }

    public J5ParseWarning {
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
