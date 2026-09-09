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

    /** A timeout alone never proves that the exchange ended or that its context is reusable. */
    public boolean recoverableTimeout() {
        return failure == PlaywrightProviderFailure.TIMEOUT && diagnostic != null
                && diagnostic.contextReusable() && diagnostic.exchangeEndedAt() != null
                && diagnostic.exchangeEndReason() != null && !diagnostic.responseComplete()
                && !Integer.valueOf(403).equals(diagnostic.httpStatus())
                && !Integer.valueOf(429).equals(diagnostic.httpStatus());
    }
}
