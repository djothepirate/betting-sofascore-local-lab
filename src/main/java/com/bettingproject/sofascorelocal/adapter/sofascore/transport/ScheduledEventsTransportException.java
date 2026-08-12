package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import java.util.Objects;

public final class ScheduledEventsTransportException extends RuntimeException {

    private final ScheduledEventsTransportFailure failure;

    public ScheduledEventsTransportException(ScheduledEventsTransportFailure failure) {
        super("scheduled-events transport failed safely: "
                + Objects.requireNonNull(failure, "failure"));
        this.failure = failure;
    }

    public ScheduledEventsTransportFailure failure() {
        return failure;
    }
}
