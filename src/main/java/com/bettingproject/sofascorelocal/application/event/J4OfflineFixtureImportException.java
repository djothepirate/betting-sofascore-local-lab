package com.bettingproject.sofascorelocal.application.event;

import java.util.Objects;

public final class J4OfflineFixtureImportException extends RuntimeException {

    private final J4OfflineFixtureImportError error;

    public J4OfflineFixtureImportException(J4OfflineFixtureImportError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public J4OfflineFixtureImportError error() {
        return error;
    }
}
