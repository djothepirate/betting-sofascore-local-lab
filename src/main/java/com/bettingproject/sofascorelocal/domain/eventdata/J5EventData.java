package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

public sealed interface J5EventData permits EventStatistics, EventIncidents, EventLineups {

    long providerEventId();

    SofascoreEndpointType endpointType();
}
