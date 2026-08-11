package com.bettingproject.sofascorelocal.fixture;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record FixtureManifest(
        int manifestVersion,
        String fixtureId,
        SofascoreEndpointType endpointType,
        FixtureOrigin fixtureOrigin,
        boolean providerSchemaValidated,
        Instant recordedAt,
        Integer httpStatus,
        String contentType,
        String parserVersion,
        String payloadResource,
        int maximumBytes,
        String expectedRawSha256,
        String expectedCanonicalJsonSha256,
        boolean minimized,
        List<String> removedFields) {

    public static final int CURRENT_MANIFEST_VERSION = 1;
    public static final int HARD_MAXIMUM_PAYLOAD_BYTES = 5 * 1024 * 1024;

    private static final Pattern FIXTURE_ID_PATTERN =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern RESOURCE_PATH_PATTERN =
            Pattern.compile("fixtures/(?:[A-Za-z0-9._-]+/)*[A-Za-z0-9._-]+");

    public FixtureManifest {
        if (manifestVersion != CURRENT_MANIFEST_VERSION) {
            throw new IllegalArgumentException("Unsupported fixture manifest version: " + manifestVersion);
        }

        fixtureId = requireText(fixtureId, "fixtureId");
        if (fixtureId.length() > 100 || !FIXTURE_ID_PATTERN.matcher(fixtureId).matches()) {
            throw new IllegalArgumentException("fixtureId must use lower-case kebab-case");
        }

        endpointType = Objects.requireNonNull(endpointType, "endpointType");
        fixtureOrigin = Objects.requireNonNull(fixtureOrigin, "fixtureOrigin");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");

        if (fixtureOrigin == FixtureOrigin.SYNTHETIC && providerSchemaValidated) {
            throw new IllegalArgumentException("A synthetic fixture cannot validate a provider schema");
        }
        if (fixtureOrigin == FixtureOrigin.PROVIDER_OBSERVED && httpStatus == null) {
            throw new IllegalArgumentException("A provider-observed fixture requires an HTTP status");
        }
        if (httpStatus != null && (httpStatus < 100 || httpStatus > 599)) {
            throw new IllegalArgumentException("httpStatus must be between 100 and 599");
        }

        contentType = requireText(contentType, "contentType");
        parserVersion = requireText(parserVersion, "parserVersion");
        payloadResource = requireResourcePath(payloadResource);

        if (maximumBytes < 1 || maximumBytes > HARD_MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "maximumBytes must be between 1 and " + HARD_MAXIMUM_PAYLOAD_BYTES);
        }

        expectedRawSha256 = requireSha256(expectedRawSha256, "expectedRawSha256");
        expectedCanonicalJsonSha256 = optionalSha256(
                expectedCanonicalJsonSha256,
                "expectedCanonicalJsonSha256");

        removedFields = normalizeRemovedFields(removedFields);
        if (!minimized && !removedFields.isEmpty()) {
            throw new IllegalArgumentException("removedFields require minimized=true");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String requireResourcePath(String value) {
        String resourcePath = requireText(value, "payloadResource");
        if (resourcePath.contains("..")
                || resourcePath.contains("\\")
                || !RESOURCE_PATH_PATTERN.matcher(resourcePath).matches()) {
            throw new IllegalArgumentException(
                    "payloadResource must be a normalized path below fixtures/");
        }
        return resourcePath;
    }

    private static String requireSha256(String value, String fieldName) {
        String hash = requireText(value, fieldName).toLowerCase(Locale.ROOT);
        if (!SHA_256_PATTERN.matcher(hash).matches()) {
            throw new IllegalArgumentException(fieldName + " must contain 64 hexadecimal characters");
        }
        return hash;
    }

    private static String optionalSha256(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireSha256(value, fieldName);
    }

    private static List<String> normalizeRemovedFields(List<String> values) {
        if (values == null) {
            return List.of();
        }

        List<String> normalized = values.stream()
                .map(value -> requireText(value, "removedFields entry"))
                .toList();
        Set<String> unique = new LinkedHashSet<>(normalized);
        if (unique.size() != normalized.size()) {
            throw new IllegalArgumentException("removedFields must not contain duplicates");
        }
        return List.copyOf(unique);
    }
}
