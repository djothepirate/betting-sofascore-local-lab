package com.bettingproject.sofascorelocal.application.network;

import java.util.Objects;

public final class TournamentScheduledEventsProjectionException extends RuntimeException {

    private final TournamentScheduledEventsProjectionError error;

    public TournamentScheduledEventsProjectionException(
            TournamentScheduledEventsProjectionError error) {
        super("tournament scheduled-events projection rejected the payload: "
                + Objects.requireNonNull(error, "error"));
        this.error = error;
    }

    public TournamentScheduledEventsProjectionError error() {
        return error;
    }
}
