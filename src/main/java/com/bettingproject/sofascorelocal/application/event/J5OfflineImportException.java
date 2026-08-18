package com.bettingproject.sofascorelocal.application.event;

import java.util.Objects;

public final class J5OfflineImportException extends RuntimeException {

    private final J5OfflineImportError error;

    public J5OfflineImportException(J5OfflineImportError error) {
        super("J5 offline fixture import failed: " + Objects.requireNonNull(error, "error"));
        this.error = error;
    }

    public J5OfflineImportError error() {
        return error;
    }
}
