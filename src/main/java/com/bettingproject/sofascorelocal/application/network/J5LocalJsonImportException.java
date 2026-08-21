package com.bettingproject.sofascorelocal.application.network;

import java.util.Objects;

public final class J5LocalJsonImportException extends RuntimeException {

    private final J5LocalJsonImportError error;

    public J5LocalJsonImportException(J5LocalJsonImportError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public J5LocalJsonImportError error() {
        return error;
    }
}
