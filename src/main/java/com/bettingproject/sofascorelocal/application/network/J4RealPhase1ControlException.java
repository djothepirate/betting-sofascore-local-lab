package com.bettingproject.sofascorelocal.application.network;

import java.util.Objects;

public final class J4RealPhase1ControlException extends RuntimeException {

    private final J4RealPhase1ControlError error;

    public J4RealPhase1ControlException(J4RealPhase1ControlError error) {
        super("J4 real phase-1 control rejected the operation: "
                + Objects.requireNonNull(error, "error"));
        this.error = error;
    }

    public J4RealPhase1ControlError error() {
        return error;
    }
}
