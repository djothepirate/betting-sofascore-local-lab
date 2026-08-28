package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;

import java.util.UUID;

public interface ScheduledEventsProviderPageTransport {

    Campaign openCampaign(UUID campaignId);

    interface Campaign extends AutoCloseable {

        ScheduledEventsTransportResponse execute(ScheduledEventsProviderPageRequest request);

        @Override
        default void close() {
            // Legacy transports do not own a campaign-scoped resource.
        }
    }
}
