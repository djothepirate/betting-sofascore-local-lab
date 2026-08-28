package com.bettingproject.sofascorelocal.application.network;

/**
 * Reports that the owned Playwright worker stop could not be confirmed after
 * the corresponding J3 business control was safely locked.
 */
public final class J3ProviderCampaignStopException extends RuntimeException {

    public J3ProviderCampaignStopException(Throwable cause) {
        super("The owned Playwright provider campaign stop could not be confirmed", cause);
    }
}
