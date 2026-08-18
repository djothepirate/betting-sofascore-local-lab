package com.bettingproject.sofascorelocal.domain.retention;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record J6BackupEvidence(
        String manifestSha256,
        String cipherSha256,
        Instant qualifiedAt,
        long coverageMaxSnapshotId,
        Instant coverageReceivedAt,
        boolean restoredAndQualified) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J6BackupEvidence {
        manifestSha256 = hash(manifestSha256, "manifestSha256");
        cipherSha256 = hash(cipherSha256, "cipherSha256");
        qualifiedAt = Objects.requireNonNull(qualifiedAt, "qualifiedAt");
        coverageReceivedAt = Objects.requireNonNull(
                coverageReceivedAt,
                "coverageReceivedAt");
        if (coverageMaxSnapshotId < 1) {
            throw new IllegalArgumentException("coverageMaxSnapshotId must be positive");
        }
    }

    private static String hash(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (!SHA_256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be a lower-case SHA-256");
        }
        return normalized;
    }
}
