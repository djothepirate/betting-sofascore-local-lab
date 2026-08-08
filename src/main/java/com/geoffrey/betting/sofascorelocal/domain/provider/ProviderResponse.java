package com.geoffrey.betting.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;

public record ProviderResponse(
        SofascoreEndpointType endpointType,
        Instant requestedAt,
        Instant receivedAt,
        Integer httpStatus,
        String contentType,
        Duration latency,
        String payload,
        String payloadSha256,
        String parserVersion,
        String schemaStatus) {
}
