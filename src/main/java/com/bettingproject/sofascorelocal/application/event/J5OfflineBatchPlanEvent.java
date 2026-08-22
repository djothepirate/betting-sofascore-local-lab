package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record J5OfflineBatchPlanEvent(
        UUID canonicalEventId,
        long providerEventId,
        long canonicalObservationId,
        Instant startsAt,
        String homeTeamName,
        String awayTeamName,
        String canonicalNormalizedSha256,
        List<String> expectedFileNames) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J5OfflineBatchPlanEvent {
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        if (providerEventId < 1
                || !CanonicalEventIdentity.sofascore(providerEventId).value()
                        .equals(canonicalEventId)) {
            throw new IllegalArgumentException("batch event identity is inconsistent");
        }
        if (canonicalObservationId < 1) {
            throw new IllegalArgumentException("canonicalObservationId must be positive");
        }
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
        homeTeamName = requireDisplayText(homeTeamName, "homeTeamName");
        awayTeamName = requireDisplayText(awayTeamName, "awayTeamName");
        canonicalNormalizedSha256 = Objects.requireNonNull(
                canonicalNormalizedSha256, "canonicalNormalizedSha256");
        if (!SHA_256.matcher(canonicalNormalizedSha256).matches()) {
            throw new IllegalArgumentException("canonical hash must be lower-case SHA-256");
        }
        expectedFileNames = List.copyOf(Objects.requireNonNull(
                expectedFileNames, "expectedFileNames"));
        List<String> expected = expectedFileNames(providerEventId);
        if (!expectedFileNames.equals(expected)) {
            throw new IllegalArgumentException("expected file names are inconsistent");
        }
    }

    public static List<String> expectedFileNames(long providerEventId) {
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        String prefix = "event-" + providerEventId + "-";
        return List.of(
                prefix + "statistics.json",
                prefix + "incidents.json",
                prefix + "lineups.json");
    }

    private static String requireDisplayText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()
                || normalized.length() > 200
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must be bounded safe text");
        }
        return normalized;
    }
}
