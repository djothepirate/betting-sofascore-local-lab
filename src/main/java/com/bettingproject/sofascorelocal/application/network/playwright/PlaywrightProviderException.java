package com.bettingproject.sofascorelocal.application.network.playwright;

import java.util.Objects;

public final class PlaywrightProviderException extends RuntimeException {

    private final PlaywrightProviderFailure failure;
    private final PlaywrightTransportDiagnostic diagnostic;

    public PlaywrightProviderException(PlaywrightProviderFailure failure) {
        this(failure, (Throwable) null);
    }

    PlaywrightProviderException(PlaywrightProviderFailure failure, Throwable cause) {
        this(failure, cause, null);
    }

    public PlaywrightProviderException(PlaywrightProviderFailure failure, PlaywrightTransportDiagnostic diagnostic) {
        this(failure, null, diagnostic);
    }

    PlaywrightProviderException(PlaywrightProviderFailure failure, Throwable cause, PlaywrightTransportDiagnostic diagnostic) {
        super("Playwright provider campaign failed safely: "
                + Objects.requireNonNull(failure, "failure"), cause);
        this.failure = failure;
        this.diagnostic = diagnostic;
    }

    public PlaywrightProviderFailure failure() {
        return failure;
    }

    public PlaywrightTransportDiagnostic diagnostic() { return diagnostic; }
}
