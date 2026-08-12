package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawPayloadEvidenceTest {

    @Test
    void capturesExactBytesSizeAndSha256Defensively() {
        byte[] source = "hello".getBytes(StandardCharsets.UTF_8);

        RawPayloadEvidence evidence = RawPayloadEvidence.capture(source);
        source[0] = 'X';
        byte[] exported = evidence.bytes();
        exported[1] = 'X';

        assertThat(evidence.bytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(evidence.sizeBytes()).isEqualTo(5);
        assertThat(evidence.sha256())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(evidence.toString()).doesNotContain("hello");
    }

    @Test
    void rejectsOversizedOrSensitivePayloadsWithoutEchoingTheirValues() {
        assertThatThrownBy(() -> RawPayloadEvidence.capture(
                new byte[RawPayloadEvidence.MAXIMUM_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("five MiB");

        assertThatThrownBy(() -> RawPayloadEvidence.capture(
                "{\"access_token\":\"do-not-store-this-value\"}"
                        .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CREDENTIAL_FIELD")
                .hasMessageNotContaining("do-not-store-this-value");
    }
}
