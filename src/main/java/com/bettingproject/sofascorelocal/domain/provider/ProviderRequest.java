package com.bettingproject.sofascorelocal.domain.provider;

import java.util.Map;
import java.util.Objects;

public record ProviderRequest(
        SofascoreEndpointType endpointType,
        Map<String, String> parameters) {

    public ProviderRequest {
        Objects.requireNonNull(endpointType, "endpointType");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
