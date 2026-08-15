package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record EventDetailsParseEvidence(
        String sourceReference,
        String rawSha256,
        Optional<String> canonicalJsonSha256,
        Instant recordedAt,
        String parserVersion) {

    public EventDetailsParseEvidence {
        sourceReference = Objects.requireNonNull(sourceReference, "sourceReference");
        rawSha256 = Objects.requireNonNull(rawSha256, "rawSha256");
        canonicalJsonSha256 = Objects.requireNonNull(
                canonicalJsonSha256,
                "canonicalJsonSha256");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        parserVersion = Objects.requireNonNull(parserVersion, "parserVersion");
    }
}
