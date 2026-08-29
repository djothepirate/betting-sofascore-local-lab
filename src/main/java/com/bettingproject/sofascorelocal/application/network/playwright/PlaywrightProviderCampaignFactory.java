package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.Set;
import java.util.UUID;

public interface PlaywrightProviderCampaignFactory {

    PlaywrightProviderCampaign open(
            UUID campaignId,
            Set<SofascoreEndpointType> allowedEndpoints);
}
