package com.bettingproject.sofascorelocal.application.retention;

import java.util.Objects;

public final class J6RetentionException extends RuntimeException {

    private final J6RetentionError error;

    public J6RetentionException(J6RetentionError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public J6RetentionError error() {
        return error;
    }
}
