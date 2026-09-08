package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataTransportResponse;

import java.util.UUID;

public interface J5EventDataProviderTransport {

    Campaign openCampaign(UUID campaignId);

    interface Campaign extends AutoCloseable {

        J5EventDataTransportResponse execute(J5EventDataProviderRequest request);

        /** Server-side claim and ownership check at the final transport boundary. */
        default J5EventDataTransportResponse execute(
                J5EventDataProviderRequest request, Runnable dispatchGuard) {
            dispatchGuard.run();
            return execute(request);
        }

        @Override
        default void close() {
            // Test doubles may not own external resources.
        }
    }
}
