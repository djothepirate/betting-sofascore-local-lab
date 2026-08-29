package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsTransportResponse;

import java.util.UUID;

public interface TournamentScheduledEventsProviderTransport {

    Campaign openCampaign(UUID campaignId);

    interface Campaign extends AutoCloseable {

        TournamentScheduledEventsTransportResponse execute(
                TournamentScheduledEventsProviderRequest request);

        @Override
        default void close() {
            // Legacy transports do not own a campaign-scoped resource.
        }
    }
}
