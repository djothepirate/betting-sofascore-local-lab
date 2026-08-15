package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import java.util.Objects;

public final class J5EventDataTransportException extends RuntimeException {

    private final J5EventDataTransportFailure failure;

    public J5EventDataTransportException(J5EventDataTransportFailure failure) {
        super(Objects.requireNonNull(failure, "failure").name());
        this.failure = failure;
    }

    public J5EventDataTransportFailure failure() {
        return failure;
    }
}
