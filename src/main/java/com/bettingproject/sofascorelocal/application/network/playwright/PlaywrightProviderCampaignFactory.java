package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.Set;
import java.util.UUID;

public interface PlaywrightProviderCampaignFactory {

    PlaywrightProviderCampaign open(
            UUID campaignId,
            Set<SofascoreEndpointType> allowedEndpoints);

    /** Explicit live-v4 policy; historical and manual factories remain unchanged. */
    default PlaywrightProviderCampaign openLiveGrouped(
            UUID campaignId, Set<SofascoreEndpointType> allowedEndpoints) {
        throw new UnsupportedOperationException("grouped live dispatch is not implemented by this transport");
    }
}
