package com.bettingproject.sofascorelocal.application.network.playwright;

import java.util.Objects;

public final class PlaywrightProviderException extends RuntimeException {

    private final PlaywrightProviderFailure failure;

    public PlaywrightProviderException(PlaywrightProviderFailure failure) {
        this(failure, null);
    }

    PlaywrightProviderException(PlaywrightProviderFailure failure, Throwable cause) {
        super("Playwright provider campaign failed safely: "
                + Objects.requireNonNull(failure, "failure"), cause);
        this.failure = failure;
    }

    public PlaywrightProviderFailure failure() {
        return failure;
    }
}
