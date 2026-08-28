package com.bettingproject.sofascorelocal.application.network.playwright;

public interface PlaywrightProviderCampaign extends AutoCloseable {

    PlaywrightProviderResponse execute(PlaywrightProviderRequest request);

    @Override
    void close();
}
