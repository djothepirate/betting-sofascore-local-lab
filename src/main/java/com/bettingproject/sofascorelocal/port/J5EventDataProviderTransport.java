package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataTransportResponse;

public interface J5EventDataProviderTransport {

    J5EventDataTransportResponse execute(J5EventDataProviderRequest request);
}
