package com.bettingproject.sofascorelocal.domain.live;

import java.time.Duration;

/** Cadence of the retained selection, independent of the operator's selection ceiling. */
public final class LiveCadence {
    public static final int MAXIMUM_SELECTION_SIZE = 100;
    private LiveCadence() { }

    public static Duration forMatches(int matches) {
        if (matches < 1 || matches > MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_INVALID");
        return Duration.ofSeconds(Math.max(60L, 30L * (matches - 1)));
    }

    public static void validate(Duration interval) {
        if (interval == null || interval.getNano() != 0 || interval.toSeconds() < 60
                || interval.compareTo(forMatches(MAXIMUM_SELECTION_SIZE)) > 0)
            throw new IllegalArgumentException("invalid live cycle interval");
    }
}
