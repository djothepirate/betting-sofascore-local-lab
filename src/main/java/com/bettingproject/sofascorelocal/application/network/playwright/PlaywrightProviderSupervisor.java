package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PlaywrightProviderSupervisor {

    PlaywrightProviderStopReceipt stopCampaign(
            UUID campaignId,
            Set<SofascoreEndpointType> allowedEndpoints);

    Optional<UUID> activeCampaignId();
}
