package com.bettingproject.sofascorelocal.domain.export;

import java.util.Objects;

public final class J7ExportException extends RuntimeException {

    private final J7ExportError error;

    public J7ExportException(J7ExportError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public J7ExportException(J7ExportError error, Throwable cause) {
        super(Objects.requireNonNull(error, "error").name(), cause);
        this.error = error;
    }

    public J7ExportError error() {
        return error;
    }
}
