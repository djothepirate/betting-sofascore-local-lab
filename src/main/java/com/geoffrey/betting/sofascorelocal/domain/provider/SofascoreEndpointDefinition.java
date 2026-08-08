package com.geoffrey.betting.sofascorelocal.domain.provider;

import java.time.Duration;

public record SofascoreEndpointDefinition(
        SofascoreEndpointType type,
        Duration cacheTtl,
        boolean manualOnly,
        boolean callable,
        boolean uriTemplateConfigured,
        String purpose) {
}
