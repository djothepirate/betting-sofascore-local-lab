package com.bettingproject.sofascorelocal.application.network;

import java.util.Objects;

public final class J4RealPhase2ControlException extends RuntimeException {

    private final J4RealPhase2ControlError error;

    public J4RealPhase2ControlException(J4RealPhase2ControlError error) {
        super("J4 real phase-2 control rejected the operation: "
                + Objects.requireNonNull(error, "error"));
        this.error = error;
    }

    public J4RealPhase2ControlError error() {
        return error;
    }
}
