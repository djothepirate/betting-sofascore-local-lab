package com.bettingproject.sofascorelocal.domain.delivery;

import java.util.Objects;

/**
 * Domain exception whose message never incorporates rejected input.
 */
public final class J7DeliveryException extends RuntimeException {

    private final J7DeliveryError error;

    public J7DeliveryException(J7DeliveryError error) {
        super(requireError(error).name());
        this.error = error;
    }

    public J7DeliveryError error() {
        return error;
    }

    private static J7DeliveryError requireError(J7DeliveryError error) {
        return Objects.requireNonNull(error, "error");
    }
}
