package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ScheduledEventsParseEvidence(
        String fixtureId,
        String rawSha256,
        Optional<String> canonicalJsonSha256,
        Instant recordedAt,
        String parserVersion) {

    public ScheduledEventsParseEvidence {
        fixtureId = requireText(fixtureId, "fixtureId");
        rawSha256 = requireText(rawSha256, "rawSha256");
        canonicalJsonSha256 = Objects.requireNonNull(
                canonicalJsonSha256,
                "canonicalJsonSha256");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        parserVersion = requireText(parserVersion, "parserVersion");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
