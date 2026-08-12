package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;

public interface ScheduledEventsTransport {

    ScheduledEventsTransportResponse execute(ScheduledEventsTransportRequest request);
}
