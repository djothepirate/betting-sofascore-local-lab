package com.geoffrey.betting.sofascorelocal.application;

import com.geoffrey.betting.sofascorelocal.domain.provider.ConnectorMode;
import com.geoffrey.betting.sofascorelocal.domain.provider.SofascoreEndpointType;
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
