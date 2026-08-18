package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record J5ParseEvidence(
        String sourceReference,
        SofascoreEndpointType endpointType,
        String rawSha256,
        Optional<String> canonicalJsonSha256,
        Instant recordedAt,
        String parserVersion) {

    public J5ParseEvidence {
        sourceReference = Objects.requireNonNull(sourceReference, "sourceReference");
        endpointType = Objects.requireNonNull(endpointType, "endpointType");
        rawSha256 = Objects.requireNonNull(rawSha256, "rawSha256");
        canonicalJsonSha256 = Objects.requireNonNull(
                canonicalJsonSha256,
                "canonicalJsonSha256");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        parserVersion = Objects.requireNonNull(parserVersion, "parserVersion");
    }
}
