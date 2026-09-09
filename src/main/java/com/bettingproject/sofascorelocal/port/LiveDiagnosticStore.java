package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignDiagnostic;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightTransportDiagnostic;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.util.Optional;
import java.util.UUID;

/** Local evidence only; no provider call or interpretation of an incomplete payload. */
public interface LiveDiagnosticStore {
    enum Kind { FIRST_FAILURE, CLEANUP_FAILURE }
    void recordTransport(UUID campaignId, UUID attemptId, SofascoreEndpointType endpoint,
                         PlaywrightTransportDiagnostic diagnostic);
    void recordFailure(UUID campaignId, Kind kind, LiveCampaignDiagnostic diagnostic);
    Optional<LiveCampaignDiagnostic> find(UUID campaignId, Kind kind);
}
