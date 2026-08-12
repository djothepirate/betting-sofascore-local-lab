package com.bettingproject.sofascorelocal.application;

import com.bettingproject.sofascorelocal.domain.provider.ConnectorMode;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.stereotype.Component;

@Component
public class ConnectorGate {

    public ConnectorMode mode() {
        return ConnectorMode.LOCKED_OFFLINE_J3_POLICY;
    }

    public void requireNetworkCallAllowed(SofascoreEndpointType endpointType) {
        throw new NetworkAccessDisabledException(
                "NETWORK_TRANSPORT_NOT_AUTHORIZED_J3: " + endpointType
                        + ". Offline policy eligibility never authorizes a provider call.");
    }
}
