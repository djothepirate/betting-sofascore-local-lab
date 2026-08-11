package com.bettingproject.sofascorelocal.application.fixture;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.fixture.FixtureOrigin;

import java.util.Objects;

public record FixtureCorpusOverview(
        SofascoreEndpointType family,
        FixtureOrigin origin,
        boolean providerSchemaValidated,
        String parserVersion,
        int declaredCount,
        int availableCount,
        int parsedCount,
        int schemaIncompatibleCount,
        int unexpectedContentCount,
        int loadingFailureCount) {

    public enum Availability {
        AVAILABLE_OFFLINE,
        INCOMPLETE
    }

    public FixtureCorpusOverview {
        family = Objects.requireNonNull(family, "family");
        origin = Objects.requireNonNull(origin, "origin");
        parserVersion = Objects.requireNonNull(parserVersion, "parserVersion");
        if (parserVersion.isBlank()) {
            throw new IllegalArgumentException("parserVersion must not be blank");
        }
        requireNonNegative(declaredCount, "declaredCount");
        requireNonNegative(availableCount, "availableCount");
        requireNonNegative(parsedCount, "parsedCount");
        requireNonNegative(schemaIncompatibleCount, "schemaIncompatibleCount");
        requireNonNegative(unexpectedContentCount, "unexpectedContentCount");
        requireNonNegative(loadingFailureCount, "loadingFailureCount");

        if (availableCount + loadingFailureCount != declaredCount) {
            throw new IllegalArgumentException(
                    "Available fixtures and loading failures must equal the declared count");
        }
        if (parsedCount + schemaIncompatibleCount + unexpectedContentCount != availableCount) {
            throw new IllegalArgumentException(
                    "Parse status counts must equal the available fixture count");
        }
    }

    public Availability availability() {
        return loadingFailureCount == 0
                ? Availability.AVAILABLE_OFFLINE
                : Availability.INCOMPLETE;
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
