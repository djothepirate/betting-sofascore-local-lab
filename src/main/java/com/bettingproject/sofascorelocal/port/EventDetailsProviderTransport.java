package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;

public interface EventDetailsProviderTransport {

    EventDetailsTransportResponse execute(EventDetailsProviderRequest request);
}
