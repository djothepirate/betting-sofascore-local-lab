package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;

import java.util.UUID;

public interface EventDetailsProviderTransport {

    Campaign openCampaign(UUID campaignId);

    interface Campaign extends AutoCloseable {

        EventDetailsTransportResponse execute(EventDetailsProviderRequest request);

        @Override
        default void close() {
            // Legacy test transports do not own a campaign-scoped resource.
        }
    }
}
