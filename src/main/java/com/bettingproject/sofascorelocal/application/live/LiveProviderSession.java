package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.playwright.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.util.Set;
import java.util.UUID;

/** One explicitly opened context for exactly the four existing event endpoints. */
public final class LiveProviderSession implements AutoCloseable {
    public static final Set<SofascoreEndpointType> ENDPOINTS = Set.of(
            SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS, SofascoreEndpointType.EVENT_LINEUPS);
    private final PlaywrightProviderCampaign campaign;
    public LiveProviderSession(PlaywrightProviderCampaignFactory factory, UUID campaignId) {
        campaign = factory.open(campaignId, ENDPOINTS);
    }
    public PlaywrightProviderResponse execute(long providerId, SofascoreEndpointType endpoint,
                                               PlaywrightDispatchAdmission admission) {
        return campaign.execute(new PlaywrightProviderRequest(endpoint, null, 0, 0, providerId), admission);
    }
    @Override public void close() { campaign.close(); }
}
