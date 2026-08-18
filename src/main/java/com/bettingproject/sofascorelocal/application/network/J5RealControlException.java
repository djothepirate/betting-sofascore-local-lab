package com.bettingproject.sofascorelocal.application.network;

import java.util.Objects;

public final class J5RealControlException extends RuntimeException {

    private final J5RealControlError error;

    public J5RealControlException(J5RealControlError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public J5RealControlError error() {
        return error;
    }
}
