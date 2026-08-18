package com.bettingproject.sofascorelocal.domain.retention;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record J6RetentionExecutionRequest(
        Instant cutoffAt,
        String planSha256,
        String confirmationPhrase,
        J6BackupEvidence backupEvidence) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J6RetentionExecutionRequest {
        cutoffAt = Objects.requireNonNull(cutoffAt, "cutoffAt");
        planSha256 = Objects.requireNonNull(planSha256, "planSha256").trim();
        confirmationPhrase = Objects.requireNonNull(
                confirmationPhrase,
                "confirmationPhrase");
        backupEvidence = Objects.requireNonNull(backupEvidence, "backupEvidence");
        if (!SHA_256.matcher(planSha256).matches()) {
            throw new IllegalArgumentException("planSha256 must be a lower-case SHA-256");
        }
        if (confirmationPhrase.length() > 160
                || confirmationPhrase.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("confirmationPhrase must be bounded safe text");
        }
    }
}
