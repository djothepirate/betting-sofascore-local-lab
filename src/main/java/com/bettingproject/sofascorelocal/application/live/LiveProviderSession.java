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
        this(factory, campaignId, "live-v3");
    }
    public LiveProviderSession(PlaywrightProviderCampaignFactory factory, UUID campaignId, String policyVersion) {
        campaign = switch (policyVersion) {
            case "live-v8" -> factory.openLiveGroupedV8(campaignId, ENDPOINTS);
            case "live-v7" -> factory.openLiveGroupedV7(campaignId, ENDPOINTS);
            case "live-v6" -> factory.openLiveGroupedV6(campaignId, ENDPOINTS);
            case "live-v5" -> factory.openLiveGroupedV5(campaignId, ENDPOINTS);
            case "live-v4" -> factory.openLiveGrouped(campaignId, ENDPOINTS);
            case null, default -> factory.open(campaignId, ENDPOINTS);
        };
    }
    public PlaywrightProviderResponse execute(long providerId, SofascoreEndpointType endpoint,
                                               PlaywrightDispatchAdmission admission) {
        return campaign.execute(new PlaywrightProviderRequest(endpoint, null, 0, 0, providerId), admission);
    }
    public PlaywrightProviderResponse executeGrouped(long providerId, SofascoreEndpointType endpoint,
            LiveProviderDispatchGroup group, PlaywrightDispatchAdmission admission) {
        return campaign.executeGrouped(new PlaywrightProviderRequest(endpoint, null, 0, 0, providerId), group, admission);
    }
    @Override public void close() { campaign.close(); }
}
