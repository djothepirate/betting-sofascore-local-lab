package com.bettingproject.sofascorelocal.fixture;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ClasspathFixtureLoader {

    private static final int MAXIMUM_MANIFEST_BYTES = 64 * 1024;
    private static final Pattern MANIFEST_RESOURCE_PATTERN = Pattern.compile(
            "fixtures/(?:[A-Za-z0-9._-]+/)*[A-Za-z0-9._-]+\\.manifest\\.json");
    private static final ObjectMapper MANIFEST_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ClassLoader classLoader;

    public ClasspathFixtureLoader() {
        this(ClasspathFixtureLoader.class.getClassLoader());
    }

    public ClasspathFixtureLoader(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    public LoadedFixture load(String manifestResource) {
        String manifestPath = requireManifestResourcePath(manifestResource);
        byte[] manifestBytes = readResource(
                manifestPath,
                MAXIMUM_MANIFEST_BYTES,
                FixtureLoadingException.Reason.MANIFEST_TOO_LARGE);
        rejectSensitiveContent("manifest", manifestBytes);

        FixtureManifest manifest = readManifest(manifestPath, manifestBytes);
        byte[] payload = readResource(
                manifest.payloadResource(),
                manifest.maximumBytes(),
                FixtureLoadingException.Reason.PAYLOAD_TOO_LARGE);
        rejectSensitiveContent(manifest.fixtureId(), payload);

        String rawSha256 = FixturePayloadHasher.rawSha256(payload);
        if (!rawSha256.equals(manifest.expectedRawSha256())) {
            throw new FixtureLoadingException(
                    FixtureLoadingException.Reason.RAW_HASH_MISMATCH,
                    "Raw SHA-256 mismatch for fixture '" + manifest.fixtureId() + "'");
        }

        FixtureContentKind contentKind = classify(payload, manifest.contentType());
        Optional<String> canonicalJsonSha256 = canonicalHash(
                manifest,
                payload,
                contentKind);

        return new LoadedFixture(
                manifest,
                payload,
                contentKind,
                rawSha256,
                canonicalJsonSha256);
    }

    private static String requireManifestResourcePath(String value) {
        if (value == null
                || value.isBlank()
                || value.contains("..")
                || value.contains("\\")
                || !MANIFEST_RESOURCE_PATTERN.matcher(value).matches()) {
            throw new FixtureLoadingException(
                    FixtureLoadingException.Reason.INVALID_RESOURCE_PATH,
                    "Manifest resource must be a normalized *.manifest.json path below fixtures/");
        }
        return value;
    }

    private FixtureManifest readManifest(String manifestPath, byte[] manifestBytes) {
        try {
            FixtureManifestDocument document = MANIFEST_MAPPER.readValue(
                    manifestBytes,
                    FixtureManifestDocument.class);
            return document.toManifest();
        }
        catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
            throw new FixtureLoadingException(
                    FixtureLoadingException.Reason.INVALID_MANIFEST,
                    "Invalid fixture manifest: " + manifestPath);
        }
    }

    private byte[] readResource(
            String resourcePath,
            int maximumBytes,
            FixtureLoadingException.Reason tooLargeReason) {
        try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new FixtureLoadingException(
                        FixtureLoadingException.Reason.RESOURCE_NOT_FOUND,
                        "Classpath fixture resource not found: " + resourcePath);
            }

            byte[] content = input.readNBytes(maximumBytes + 1);
            if (content.length > maximumBytes) {
                throw new FixtureLoadingException(
                        tooLargeReason,
                        "Classpath fixture resource exceeds its size limit: " + resourcePath);
            }
            return content;
        }
        catch (IOException exception) {
            throw new FixtureLoadingException(
                    FixtureLoadingException.Reason.RESOURCE_READ_FAILED,
                    "Unable to read classpath fixture resource: " + resourcePath);
        }
    }

    private static void rejectSensitiveContent(String fixtureReference, byte[] content) {
        List<String> findings = SensitiveContentScanner.findings(content);
        if (!findings.isEmpty()) {
            throw new FixtureLoadingException(
                    FixtureLoadingException.Reason.SENSITIVE_CONTENT,
                    "Forbidden sensitive-data patterns in fixture '"
                            + fixtureReference
                            + "': "
                            + findings);
        }
    }

    private static FixtureContentKind classify(byte[] payload, String declaredContentType) {
        int prefixLength = Math.min(payload.length, 1024);
        String prefix = new String(payload, 0, prefixLength, StandardCharsets.UTF_8).stripLeading();
        if (prefix.startsWith("\uFEFF")) {
            prefix = prefix.substring(1).stripLeading();
        }

        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        String lowerContentType = declaredContentType.toLowerCase(Locale.ROOT);

        if (lowerPrefix.startsWith("<!doctype html")
                || lowerPrefix.startsWith("<html")
                || lowerPrefix.startsWith("<head")
                || lowerPrefix.startsWith("<body")) {
            return FixtureContentKind.HTML;
        }
        if (prefix.startsWith("{") || prefix.startsWith("[")) {
            return FixtureContentKind.JSON;
        }
        if (lowerContentType.contains("text/html")) {
            return FixtureContentKind.HTML;
        }
        if (lowerContentType.contains("application/json")
                || lowerContentType.contains("+json")) {
            return FixtureContentKind.JSON;
        }
        return FixtureContentKind.OTHER;
    }

    private static Optional<String> canonicalHash(
            FixtureManifest manifest,
            byte[] payload,
            FixtureContentKind contentKind) {
        if (contentKind != FixtureContentKind.JSON) {
            if (manifest.expectedCanonicalJsonSha256() != null) {
                throw new FixtureLoadingException(
                        FixtureLoadingException.Reason.INVALID_MANIFEST,
                        "A non-JSON fixture must not declare a canonical JSON hash: '"
                                + manifest.fixtureId()
                                + "'");
            }
            return Optional.empty();
        }

        String canonicalHash = FixturePayloadHasher.canonicalJsonSha256(payload);
        if (manifest.expectedCanonicalJsonSha256() == null) {
            throw new FixtureLoadingException(
                    FixtureLoadingException.Reason.INVALID_MANIFEST,
                    "A JSON fixture requires a canonical JSON hash: '"
                            + manifest.fixtureId()
                            + "'");
        }
        if (!canonicalHash.equals(manifest.expectedCanonicalJsonSha256())) {
            throw new FixtureLoadingException(
                    FixtureLoadingException.Reason.CANONICAL_HASH_MISMATCH,
                    "Canonical JSON SHA-256 mismatch for fixture '"
                            + manifest.fixtureId()
                            + "'");
        }
        return Optional.of(canonicalHash);
    }

    private record FixtureManifestDocument(
            Integer manifestVersion,
            String fixtureId,
            String endpointType,
            String fixtureOrigin,
            Boolean providerSchemaValidated,
            String recordedAt,
            Integer httpStatus,
            String contentType,
            String parserVersion,
            String payloadResource,
            Integer maximumBytes,
            String expectedRawSha256,
            String expectedCanonicalJsonSha256,
            Boolean minimized,
            List<String> removedFields) {

        FixtureManifest toManifest() {
            if (manifestVersion == null
                    || providerSchemaValidated == null
                    || maximumBytes == null
                    || minimized == null) {
                throw new IllegalArgumentException("Required manifest field is missing");
            }

            return new FixtureManifest(
                    manifestVersion,
                    fixtureId,
                    SofascoreEndpointType.valueOf(endpointType),
                    FixtureOrigin.valueOf(fixtureOrigin),
                    providerSchemaValidated,
                    Instant.parse(recordedAt),
                    httpStatus,
                    contentType,
                    parserVersion,
                    payloadResource,
                    maximumBytes,
                    expectedRawSha256,
                    expectedCanonicalJsonSha256,
                    minimized,
                    removedFields);
        }
    }
}
