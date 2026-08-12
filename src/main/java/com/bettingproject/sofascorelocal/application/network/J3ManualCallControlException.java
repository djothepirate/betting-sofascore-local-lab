package com.bettingproject.sofascorelocal.application.network;

import java.util.Objects;

public final class J3ManualCallControlException extends RuntimeException {

    private final J3ManualCallControlError error;

    public J3ManualCallControlException(J3ManualCallControlError error) {
        super("manual-call control rejected the transition: "
                + Objects.requireNonNull(error, "error"));
        this.error = error;
    }

    public J3ManualCallControlError error() {
        return error;
    }
}
