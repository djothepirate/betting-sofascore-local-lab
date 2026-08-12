package com.bettingproject.sofascorelocal.adapter.sofascore;

import com.bettingproject.sofascorelocal.application.ConnectorGate;
import com.bettingproject.sofascorelocal.domain.provider.ProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResponse;
import com.bettingproject.sofascorelocal.port.SofascoreDataProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DisabledSofascoreDataProvider implements SofascoreDataProvider {

    private final ConnectorGate connectorGate;

    public DisabledSofascoreDataProvider(ConnectorGate connectorGate) {
        this.connectorGate = connectorGate;
    }

    @Override
    public ProviderResponse load(ProviderRequest request) {
        Objects.requireNonNull(request, "request");
        connectorGate.requireNetworkCallAllowed(request.endpointType());
        throw new IllegalStateException("Unreachable while the J1 connector gate is active");
    }
}
