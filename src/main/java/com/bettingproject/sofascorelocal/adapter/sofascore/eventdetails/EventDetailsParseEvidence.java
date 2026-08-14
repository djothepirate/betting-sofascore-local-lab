package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record EventDetailsParseEvidence(
        String fixtureId,
        String rawSha256,
        Optional<String> canonicalJsonSha256,
        Instant recordedAt,
        String parserVersion) {

    public EventDetailsParseEvidence {
        fixtureId = Objects.requireNonNull(fixtureId, "fixtureId");
        rawSha256 = Objects.requireNonNull(rawSha256, "rawSha256");
        canonicalJsonSha256 = Objects.requireNonNull(
                canonicalJsonSha256,
                "canonicalJsonSha256");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        parserVersion = Objects.requireNonNull(parserVersion, "parserVersion");
    }
}
