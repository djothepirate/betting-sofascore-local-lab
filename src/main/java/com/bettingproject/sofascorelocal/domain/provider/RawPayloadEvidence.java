package com.bettingproject.sofascorelocal.domain.provider;

import com.bettingproject.sofascorelocal.security.SensitiveContentScanner;
import com.bettingproject.sofascorelocal.security.Sha256;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class RawPayloadEvidence {

    public static final int MAXIMUM_BYTES = 5 * 1024 * 1024;

    private final byte[] bytes;
    private final String sha256;

    private RawPayloadEvidence(byte[] bytes) {
        this.bytes = bytes;
        sha256 = Sha256.hex(bytes);
    }

    public static RawPayloadEvidence capture(byte[] content) {
        Objects.requireNonNull(content, "content");
        if (content.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException(
                    "raw payload exceeds the five MiB persistence limit");
        }

        List<String> findings = SensitiveContentScanner.findings(content);
        if (!findings.isEmpty()) {
            throw new IllegalArgumentException(
                    "raw payload contains forbidden sensitive-data patterns: " + findings);
        }

        return new RawPayloadEvidence(content.clone());
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public int sizeBytes() {
        return bytes.length;
    }

    public String sha256() {
        return sha256;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof RawPayloadEvidence other
                && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "RawPayloadEvidence[sizeBytes=" + sizeBytes() + ", sha256=" + sha256 + "]";
    }
}
