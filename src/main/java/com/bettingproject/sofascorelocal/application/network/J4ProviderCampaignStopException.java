package com.bettingproject.sofascorelocal.application.network;

/** Reports that an owned J4 Playwright campaign stop could not be confirmed. */
public final class J4ProviderCampaignStopException extends RuntimeException {

    public J4ProviderCampaignStopException(Throwable cause) {
        super("The owned J4 Playwright provider campaign stop could not be confirmed", cause);
    }
}
