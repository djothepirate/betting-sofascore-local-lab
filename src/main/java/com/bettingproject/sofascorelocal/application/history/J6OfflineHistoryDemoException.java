package com.bettingproject.sofascorelocal.application.history;

import java.util.Objects;

public final class J6OfflineHistoryDemoException extends RuntimeException {

    private final J6OfflineHistoryDemoError error;

    public J6OfflineHistoryDemoException(J6OfflineHistoryDemoError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public J6OfflineHistoryDemoError error() {
        return error;
    }
}
