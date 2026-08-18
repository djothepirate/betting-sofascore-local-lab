package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import java.util.Objects;

public record J5ParseWarning(Code code, String path, String message) {

    public enum Code {
        UNKNOWN_FIELD,
        OPTIONAL_FIELD_MISSING,
        PROVIDER_SENTINEL_NORMALIZED,
        PROVIDER_PENALTY_PERIOD_SENTINEL_NORMALIZED,
        PROVIDER_BENCH_CARD_MINUTE_USED,
        PROVIDER_BENCH_CARD_ADDED_TIME_USED,
        PROVIDER_NESTED_MINUTE_USED,
        PROVIDER_SHOOTOUT_MINUTE_ABSENT,
        PROVIDER_ALIAS_NORMALIZED,
        PROVIDER_REGULAR_GOAL_ORIGIN_OMITTED
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
