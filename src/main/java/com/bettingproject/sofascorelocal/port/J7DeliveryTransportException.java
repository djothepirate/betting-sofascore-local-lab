package com.bettingproject.sofascorelocal.port;

import java.util.Objects;

public final class J7DeliveryTransportException extends RuntimeException {

    private final J7DeliveryTransportFailure failure;

    public J7DeliveryTransportException(J7DeliveryTransportFailure failure) {
        super(Objects.requireNonNull(failure, "failure").name());
        this.failure = failure;
    }

    public J7DeliveryTransportException(
            J7DeliveryTransportFailure failure,
            Throwable cause) {
        super(Objects.requireNonNull(failure, "failure").name(), cause);
        this.failure = failure;
    }

    public J7DeliveryTransportFailure failure() {
        return failure;
    }
}
