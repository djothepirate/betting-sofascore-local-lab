package com.bettingproject.sofascorelocal.application;

import com.bettingproject.sofascorelocal.domain.provider.ConnectorMode;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.stereotype.Component;

@Component
public class ConnectorGate {

    public ConnectorMode mode() {
        return ConnectorMode.LOCKED_OFFLINE_J1;
    }

    public void requireNetworkCallAllowed(SofascoreEndpointType endpointType) {
        throw new NetworkAccessDisabledException(
                "NETWORK_CALLS_NOT_IMPLEMENTED_J1: " + endpointType
                        + ". A J3 Work Order and an explicit architecture review are required.");
    }
}
