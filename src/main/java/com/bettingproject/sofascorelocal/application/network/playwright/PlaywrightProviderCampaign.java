package com.bettingproject.sofascorelocal.application.network.playwright;

public interface PlaywrightProviderCampaign extends AutoCloseable {

    PlaywrightProviderResponse execute(PlaywrightProviderRequest request);

    default PlaywrightProviderResponse execute(PlaywrightProviderRequest request,
                                               PlaywrightDispatchAdmission admission) {
        throw new UnsupportedOperationException("guarded dispatch is not implemented by this transport");
    }

    default PlaywrightProviderResponse executeGrouped(PlaywrightProviderRequest request,
            LiveProviderDispatchGroup group, PlaywrightDispatchAdmission admission) {
        throw new UnsupportedOperationException("grouped live dispatch is not implemented by this transport");
    }

    @Override
    void close();
}
