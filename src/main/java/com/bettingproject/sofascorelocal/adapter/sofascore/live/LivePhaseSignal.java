package com.bettingproject.sofascorelocal.adapter.sofascore.live;

import java.util.Objects;

/** A versioned, source-derived prompt to consult J4, never proof of a sporting result. */
public record LivePhaseSignal(String key, Kind kind) {
    public enum Kind { PERIOD_CHECK, FINISH_CHECK }

    public LivePhaseSignal {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        if (!key.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("signal key must be a SHA-256");
        }
    }
}
