package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;

public interface ScheduledEventsProviderPageTransport {

    ScheduledEventsTransportResponse execute(ScheduledEventsProviderPageRequest request);
}
