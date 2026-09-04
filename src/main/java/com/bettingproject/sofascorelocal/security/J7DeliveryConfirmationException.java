package com.bettingproject.sofascorelocal.security;

import java.util.Objects;

/** Exception whose message never incorporates a session, request, identifier, hash or text. */
public final class J7DeliveryConfirmationException extends RuntimeException {

    private final J7DeliveryConfirmationError error;

    public J7DeliveryConfirmationException(J7DeliveryConfirmationError error) {
        super(requireError(error).name());
        this.error = error;
    }

    public J7DeliveryConfirmationError error() {
        return error;
    }

    private static J7DeliveryConfirmationError requireError(
            J7DeliveryConfirmationError error) {
        return Objects.requireNonNull(error, "error");
    }
}
