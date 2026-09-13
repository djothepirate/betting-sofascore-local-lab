package com.bettingproject.sofascorelocal.application.network.playwright;

public interface PlaywrightProviderCampaign extends AutoCloseable {

    /** A live-v11 owner may temporarily lend dispatch to a fresh, isolated J3 context. */
    default PlaywrightProviderCampaign openJ3SubOperation(J3ProviderSubOperation scope) {
        throw new UnsupportedOperationException("J3 isolated context capability is unavailable");
    }

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
