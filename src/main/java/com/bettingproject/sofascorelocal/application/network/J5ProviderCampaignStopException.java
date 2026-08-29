package com.bettingproject.sofascorelocal.application.network;

/** Reports that the owned J5 Playwright campaign stop could not be confirmed. */
public final class J5ProviderCampaignStopException extends RuntimeException {

    public J5ProviderCampaignStopException(Throwable cause) {
        super("The owned J5 Playwright provider campaign stop could not be confirmed", cause);
    }
}
