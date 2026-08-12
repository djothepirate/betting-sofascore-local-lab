package com.bettingproject.sofascorelocal.fixture;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class FixturePayloadHasher {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private FixturePayloadHasher() {
    }

    public static String rawSha256(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static byte[] canonicalizeJson(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        try {
            JsonNode parsed = JSON_MAPPER.readTree(payload);
            if (parsed == null) {
                throw invalidJson();
            }
            return JSON_MAPPER.writeValueAsBytes(sortRecursively(parsed));
        }
        catch (JacksonException exception) {
            throw invalidJson();
        }
    }

    public static String canonicalJsonSha256(byte[] payload) {
        return rawSha256(canonicalizeJson(payload));
    }

    private static JsonNode sortRecursively(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JSON_MAPPER.createObjectNode();
            node.properties().stream()
                    .sorted((left, right) -> left.getKey().compareTo(right.getKey()))
                    .forEach(entry -> sorted.set(
                            entry.getKey(),
                            sortRecursively(entry.getValue())));
            return sorted;
        }

        if (node.isArray()) {
            ArrayNode sorted = JSON_MAPPER.createArrayNode();
            node.valueStream().forEach(value -> sorted.add(sortRecursively(value)));
            return sorted;
        }

        return node;
    }

    private static FixtureLoadingException invalidJson() {
        return new FixtureLoadingException(
                FixtureLoadingException.Reason.INVALID_JSON,
                "Fixture payload is not valid unambiguous JSON");
    }
}
