package com.bettingproject.sofascorelocal.application.event;

import java.util.Objects;

public final class J5OfflineBatchException extends RuntimeException {

    private final J5OfflineBatchError error;

    public J5OfflineBatchException(J5OfflineBatchError error) {
        this(error, null);
    }

    public J5OfflineBatchException(J5OfflineBatchError error, Throwable cause) {
        super(Objects.requireNonNull(error, "error").name(), cause);
        this.error = error;
    }

    public J5OfflineBatchError error() {
        return error;
    }
}
