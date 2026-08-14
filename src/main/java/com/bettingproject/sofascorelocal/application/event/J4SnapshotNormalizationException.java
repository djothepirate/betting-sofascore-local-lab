package com.bettingproject.sofascorelocal.application.event;

import java.util.Objects;

public final class J4SnapshotNormalizationException extends RuntimeException {

    private final J4SnapshotNormalizationError error;

    public J4SnapshotNormalizationException(J4SnapshotNormalizationError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public J4SnapshotNormalizationException(
            J4SnapshotNormalizationError error,
            Throwable cause) {
        super(Objects.requireNonNull(error, "error").name(), cause);
        this.error = error;
    }

    public J4SnapshotNormalizationError error() {
        return error;
    }
}
