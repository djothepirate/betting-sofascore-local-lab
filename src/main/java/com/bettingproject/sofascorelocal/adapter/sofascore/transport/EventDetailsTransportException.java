package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import java.util.Objects;

public final class EventDetailsTransportException extends RuntimeException {

    private final EventDetailsTransportFailure failure;

    public EventDetailsTransportException(EventDetailsTransportFailure failure) {
        super("event-details transport failed safely: "
                + Objects.requireNonNull(failure, "failure"));
        this.failure = failure;
    }

    public EventDetailsTransportFailure failure() {
        return failure;
    }
}
