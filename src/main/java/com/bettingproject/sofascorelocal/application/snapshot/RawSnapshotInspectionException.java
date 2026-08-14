package com.bettingproject.sofascorelocal.application.snapshot;

import java.util.Objects;

public final class RawSnapshotInspectionException extends RuntimeException {

    private final RawSnapshotInspectionError error;

    public RawSnapshotInspectionException(RawSnapshotInspectionError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public RawSnapshotInspectionException(
            RawSnapshotInspectionError error,
            Throwable cause) {
        super(Objects.requireNonNull(error, "error").name(), cause);
        this.error = error;
    }

    public RawSnapshotInspectionError error() {
        return error;
    }
}
