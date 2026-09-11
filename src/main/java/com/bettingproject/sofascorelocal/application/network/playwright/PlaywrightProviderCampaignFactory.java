package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.Set;
import java.util.UUID;

public interface PlaywrightProviderCampaignFactory {

    PlaywrightProviderCampaign open(
            UUID campaignId,
            Set<SofascoreEndpointType> allowedEndpoints);

    /** Explicit live-v4 policy; historical factories remain unchanged. */
    default PlaywrightProviderCampaign openLiveGrouped(
            UUID campaignId, Set<SofascoreEndpointType> allowedEndpoints) {
        throw new UnsupportedOperationException("grouped live dispatch is not implemented by this transport");
    }

    /** Explicit live-v5 server authority; never falls back to a historical factory. */
    default PlaywrightProviderCampaign openLiveGroupedV5(
            UUID campaignId, Set<SofascoreEndpointType> allowedEndpoints) {
        throw new UnsupportedOperationException("grouped live-v5 dispatch is not implemented by this transport");
    }

    /** Explicit live-v6 authority, allowing unavailable in-play families to be deferred. */
    default PlaywrightProviderCampaign openLiveGroupedV6(
            UUID campaignId, Set<SofascoreEndpointType> allowedEndpoints) {
        throw new UnsupportedOperationException("grouped live-v6 dispatch is not implemented by this transport");
    }

    /** Explicit V7 kickoff-window authority; no fallback can silently grant prematch groups. */
    default PlaywrightProviderCampaign openLiveGroupedV7(UUID campaignId, Set<SofascoreEndpointType> allowedEndpoints) {
        throw new UnsupportedOperationException("grouped live-v7 dispatch is not implemented by this transport");
    }

    /** Explicit V8 ten-match authority; it cannot fall back to an earlier live policy. */
    default PlaywrightProviderCampaign openLiveGroupedV8(UUID campaignId, Set<SofascoreEndpointType> allowedEndpoints) {
        throw new UnsupportedOperationException("grouped live-v8 dispatch is not implemented by this transport");
    }

    /**
     * Explicit V9 J4-controlled authority. Its scheduling gates reduce work, while its
     * transport pressure remains bounded by the previously qualified V8 local profile.
     */
    default PlaywrightProviderCampaign openLiveGroupedV9(UUID campaignId, Set<SofascoreEndpointType> allowedEndpoints) {
        throw new UnsupportedOperationException("grouped live-v9 dispatch is not implemented by this transport");
    }

    /** One explicitly launched, single-event J5 manual group in statistics/incidents/lineups order. */
    default PlaywrightProviderCampaign openManualJ5Grouped(
            UUID campaignId, Set<SofascoreEndpointType> allowedEndpoints) {
        throw new UnsupportedOperationException("grouped manual J5 dispatch is not implemented by this transport");
    }
}
