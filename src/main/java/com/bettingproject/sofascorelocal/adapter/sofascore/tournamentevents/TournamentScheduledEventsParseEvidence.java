package com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record TournamentScheduledEventsParseEvidence(
        String sourceId,
        String rawSha256,
        Optional<String> canonicalJsonSha256,
        Instant receivedAt,
        String parserVersion) {

    public TournamentScheduledEventsParseEvidence {
        sourceId = requireText(sourceId, "sourceId");
        rawSha256 = requireText(rawSha256, "rawSha256");
        canonicalJsonSha256 = Objects.requireNonNull(
                canonicalJsonSha256,
                "canonicalJsonSha256");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
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
