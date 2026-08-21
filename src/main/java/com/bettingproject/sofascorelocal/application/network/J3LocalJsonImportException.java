package com.bettingproject.sofascorelocal.application.network;

import java.util.Objects;

public final class J3LocalJsonImportException extends RuntimeException {

    private final J3LocalJsonImportError error;

    public J3LocalJsonImportException(J3LocalJsonImportError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public J3LocalJsonImportError error() {
        return error;
    }
}
