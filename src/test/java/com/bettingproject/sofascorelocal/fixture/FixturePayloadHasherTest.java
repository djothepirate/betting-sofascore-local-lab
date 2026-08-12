package com.bettingproject.sofascorelocal.fixture;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixturePayloadHasherTest {

    @Test
    void canonicalHashIgnoresWhitespaceAndObjectPropertyOrderRecursively() {
        byte[] left = bytes("""
                {"z":3,"nested":{"b":2,"a":1},"items":[{"y":2,"x":1}]}
                """);
        byte[] right = bytes("""
                {
                  "items": [{"x": 1, "y": 2}],
                  "nested": {"a": 1, "b": 2},
                  "z": 3
                }
                """);

        assertThat(FixturePayloadHasher.rawSha256(left))
                .isNotEqualTo(FixturePayloadHasher.rawSha256(right));
        assertThat(FixturePayloadHasher.canonicalJsonSha256(left))
                .isEqualTo(FixturePayloadHasher.canonicalJsonSha256(right));
        assertThat(new String(
                FixturePayloadHasher.canonicalizeJson(left),
                StandardCharsets.UTF_8))
                .isEqualTo("{\"items\":[{\"x\":1,\"y\":2}],"
                        + "\"nested\":{\"a\":1,\"b\":2},\"z\":3}");
    }

    @Test
    void canonicalHashPreservesArrayOrderAndDetectsRealContentChanges() {
        byte[] first = bytes("{\"events\":[1,2,3]}");
        byte[] reordered = bytes("{\"events\":[3,2,1]}");
        byte[] changed = bytes("{\"events\":[1,2,4]}");

        assertThat(FixturePayloadHasher.canonicalJsonSha256(first))
                .isNotEqualTo(FixturePayloadHasher.canonicalJsonSha256(reordered))
                .isNotEqualTo(FixturePayloadHasher.canonicalJsonSha256(changed));
    }

    @Test
    void rejectsDuplicatePropertiesAndTrailingContent() {
        assertThatThrownBy(() -> FixturePayloadHasher.canonicalizeJson(
                bytes("{\"eventId\":1,\"eventId\":2}")))
                .isInstanceOfSatisfying(FixtureLoadingException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(FixtureLoadingException.Reason.INVALID_JSON));

        assertThatThrownBy(() -> FixturePayloadHasher.canonicalizeJson(
                bytes("{\"eventId\":1} trailing")))
                .isInstanceOfSatisfying(FixtureLoadingException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(FixtureLoadingException.Reason.INVALID_JSON));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
