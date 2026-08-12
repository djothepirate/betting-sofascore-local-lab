package com.bettingproject.sofascorelocal.fixture;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathFixtureLoaderTest {

    @Test
    void loadsJsonFromClasspathAndVerifiesBothHashes() {
        byte[] payload = bytes("{\"z\":2,\"a\":1}");
        String payloadPath = "fixtures/infrastructure/nominal.json";
        String manifestPath = "fixtures/infrastructure/nominal.manifest.json";
        ClasspathFixtureLoader loader = loaderWith(
                manifestPath,
                manifest("nominal", payloadPath, payload, "application/json", 1024, null),
                payloadPath,
                payload);

        LoadedFixture loaded = loader.load(manifestPath);

        assertThat(loaded.manifest().fixtureOrigin()).isEqualTo(FixtureOrigin.SYNTHETIC);
        assertThat(loaded.manifest().providerSchemaValidated()).isFalse();
        assertThat(loaded.contentKind()).isEqualTo(FixtureContentKind.JSON);
        assertThat(loaded.rawSha256()).isEqualTo(FixturePayloadHasher.rawSha256(payload));
        assertThat(loaded.canonicalJsonSha256())
                .contains(FixturePayloadHasher.canonicalJsonSha256(payload));
        assertThat(loaded.sizeBytes()).isEqualTo(payload.length);

        byte[] exposedCopy = loaded.rawPayload();
        exposedCopy[0] = 'X';
        assertThat(loaded.rawPayload()).isEqualTo(payload);
    }

    @Test
    void classifiesHtmlBeforeTrustingTheDeclaredContentType() {
        byte[] payload = bytes("<!DOCTYPE html><html><body>temporarily unavailable</body></html>");
        String payloadPath = "fixtures/infrastructure/unexpected.html";
        String manifestPath = "fixtures/infrastructure/unexpected.manifest.json";
        ClasspathFixtureLoader loader = loaderWith(
                manifestPath,
                manifest("unexpected-html", payloadPath, payload, "application/json", 1024, null),
                payloadPath,
                payload);

        LoadedFixture loaded = loader.load(manifestPath);

        assertThat(loaded.contentKind()).isEqualTo(FixtureContentKind.HTML);
        assertThat(loaded.canonicalJsonSha256()).isEmpty();
    }

    @Test
    void classifiesNonJsonAndNonHtmlContentAsOther() {
        byte[] payload = bytes("offline fixture note");
        String payloadPath = "fixtures/infrastructure/note.txt";
        String manifestPath = "fixtures/infrastructure/note.manifest.json";
        ClasspathFixtureLoader loader = loaderWith(
                manifestPath,
                manifest("note", payloadPath, payload, "text/plain", 1024, null),
                payloadPath,
                payload);

        LoadedFixture loaded = loader.load(manifestPath);

        assertThat(loaded.contentKind()).isEqualTo(FixtureContentKind.OTHER);
        assertThat(loaded.canonicalJsonSha256()).isEmpty();
    }

    @Test
    void rejectsPayloadAboveTheManifestLimit() {
        byte[] payload = bytes("{\"events\":[]}");
        String payloadPath = "fixtures/infrastructure/oversized.json";
        String manifestPath = "fixtures/infrastructure/oversized.manifest.json";
        ClasspathFixtureLoader loader = loaderWith(
                manifestPath,
                manifest("oversized", payloadPath, payload, "application/json", 4, null),
                payloadPath,
                payload);

        assertReason(
                () -> loader.load(manifestPath),
                FixtureLoadingException.Reason.PAYLOAD_TOO_LARGE);
    }

    @Test
    void rejectsSensitiveContentWithoutDisclosingTheDetectedValue() {
        String sensitiveValue = "fixture-secret-value";
        byte[] payload = bytes("{\"access_token\":\"" + sensitiveValue + "\"}");
        String payloadPath = "fixtures/infrastructure/sensitive.json";
        String manifestPath = "fixtures/infrastructure/sensitive.manifest.json";
        ClasspathFixtureLoader loader = loaderWith(
                manifestPath,
                manifest("sensitive", payloadPath, payload, "application/json", 1024, null),
                payloadPath,
                payload);

        assertThatThrownBy(() -> loader.load(manifestPath))
                .isInstanceOfSatisfying(FixtureLoadingException.class, exception -> {
                    assertThat(exception.reason())
                            .isEqualTo(FixtureLoadingException.Reason.SENSITIVE_CONTENT);
                    assertThat(exception.getMessage())
                            .contains("CREDENTIAL_FIELD")
                            .doesNotContain(sensitiveValue);
                });
    }

    @Test
    void rejectsRawAndCanonicalHashMismatchesSeparately() {
        byte[] payload = bytes("{\"events\":[]}");
        String payloadPath = "fixtures/infrastructure/hash-mismatch.json";
        String rawManifestPath = "fixtures/infrastructure/raw-mismatch.manifest.json";
        ClasspathFixtureLoader rawMismatchLoader = loaderWith(
                rawManifestPath,
                manifest(
                        "raw-mismatch",
                        payloadPath,
                        payload,
                        "application/json",
                        1024,
                        "0".repeat(64),
                        FixturePayloadHasher.canonicalJsonSha256(payload)),
                payloadPath,
                payload);

        assertReason(
                () -> rawMismatchLoader.load(rawManifestPath),
                FixtureLoadingException.Reason.RAW_HASH_MISMATCH);

        String canonicalManifestPath = "fixtures/infrastructure/canonical-mismatch.manifest.json";
        ClasspathFixtureLoader canonicalMismatchLoader = loaderWith(
                canonicalManifestPath,
                manifest(
                        "canonical-mismatch",
                        payloadPath,
                        payload,
                        "application/json",
                        1024,
                        null,
                        "0".repeat(64)),
                payloadPath,
                payload);

        assertReason(
                () -> canonicalMismatchLoader.load(canonicalManifestPath),
                FixtureLoadingException.Reason.CANONICAL_HASH_MISMATCH);
    }

    @Test
    void rejectsMalformedJsonAndInvalidManifestPaths() {
        byte[] payload = bytes("{\"events\": }");
        String payloadPath = "fixtures/infrastructure/malformed.json";
        String manifestPath = "fixtures/infrastructure/malformed.manifest.json";
        ClasspathFixtureLoader loader = loaderWith(
                manifestPath,
                manifest(
                        "malformed",
                        payloadPath,
                        payload,
                        "application/json",
                        1024,
                        null,
                        "0".repeat(64)),
                payloadPath,
                payload);

        assertReason(
                () -> loader.load(manifestPath),
                FixtureLoadingException.Reason.INVALID_JSON);
        assertReason(
                () -> loader.load("../outside.manifest.json"),
                FixtureLoadingException.Reason.INVALID_RESOURCE_PATH);
    }

    @Test
    void rejectsUnknownManifestFields() {
        byte[] payload = bytes("{\"events\":[]}");
        String payloadPath = "fixtures/infrastructure/unknown-field.json";
        String manifestPath = "fixtures/infrastructure/unknown-field.manifest.json";
        String validManifest = new String(
                manifest("unknown-field", payloadPath, payload, "application/json", 1024, null),
                StandardCharsets.UTF_8);
        byte[] invalidManifest = bytes(validManifest.replaceFirst(
                "\\}\\s*$",
                ",\n  \"unexpectedField\": true\n}"));
        ClasspathFixtureLoader loader = loaderWith(
                manifestPath,
                invalidManifest,
                payloadPath,
                payload);

        assertReason(
                () -> loader.load(manifestPath),
                FixtureLoadingException.Reason.INVALID_MANIFEST);
    }

    @Test
    void reportsMissingClasspathResourcesExplicitly() {
        ClasspathFixtureLoader loader = new ClasspathFixtureLoader(
                new MapResourceClassLoader(Map.of()));

        assertReason(
                () -> loader.load("fixtures/infrastructure/missing.manifest.json"),
                FixtureLoadingException.Reason.RESOURCE_NOT_FOUND);
    }

    private static ClasspathFixtureLoader loaderWith(
            String manifestPath,
            byte[] manifest,
            String payloadPath,
            byte[] payload) {
        Map<String, byte[]> resources = new HashMap<>();
        resources.put(manifestPath, manifest);
        resources.put(payloadPath, payload);
        return new ClasspathFixtureLoader(new MapResourceClassLoader(resources));
    }

    private static byte[] manifest(
            String fixtureId,
            String payloadPath,
            byte[] payload,
            String contentType,
            int maximumBytes,
            String rawHashOverride) {
        String canonicalHash = contentType.contains("json")
                && !new String(payload, StandardCharsets.UTF_8).stripLeading().startsWith("<")
                ? FixturePayloadHasher.canonicalJsonSha256(payload)
                : null;
        return manifest(
                fixtureId,
                payloadPath,
                payload,
                contentType,
                maximumBytes,
                rawHashOverride,
                canonicalHash);
    }

    private static byte[] manifest(
            String fixtureId,
            String payloadPath,
            byte[] payload,
            String contentType,
            int maximumBytes,
            String rawHashOverride,
            String canonicalHashOverride) {
        String rawHash = rawHashOverride == null
                ? FixturePayloadHasher.rawSha256(payload)
                : rawHashOverride;
        String canonicalHashJson = canonicalHashOverride == null
                ? "null"
                : "\"" + canonicalHashOverride + "\"";
        return bytes("""
                {
                  "manifestVersion": 1,
                  "fixtureId": "%s",
                  "endpointType": "SCHEDULED_EVENTS",
                  "fixtureOrigin": "SYNTHETIC",
                  "providerSchemaValidated": false,
                  "recordedAt": "2026-08-12T00:00:00Z",
                  "httpStatus": null,
                  "contentType": "%s",
                  "parserVersion": "UNASSIGNED",
                  "payloadResource": "%s",
                  "maximumBytes": %d,
                  "expectedRawSha256": "%s",
                  "expectedCanonicalJsonSha256": %s,
                  "minimized": false,
                  "removedFields": []
                }
                """.formatted(
                fixtureId,
                contentType,
                payloadPath,
                maximumBytes,
                rawHash,
                canonicalHashJson));
    }

    private static void assertReason(
            ThrowingOperation operation,
            FixtureLoadingException.Reason reason) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(FixtureLoadingException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(reason));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    private static final class MapResourceClassLoader extends ClassLoader {

        private final Map<String, byte[]> resources;

        private MapResourceClassLoader(Map<String, byte[]> resources) {
            super(null);
            this.resources = Map.copyOf(resources);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            byte[] resource = resources.get(name);
            return resource == null ? null : new ByteArrayInputStream(resource);
        }
    }
}
