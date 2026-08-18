package com.bettingproject.sofascorelocal.domain.retention;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record J6RetentionExecutionResult(
        UUID batchId,
        String planSha256,
        int purgedPayloadCount,
        long purgedPayloadBytes,
        Instant executedAt) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J6RetentionExecutionResult {
        batchId = Objects.requireNonNull(batchId, "batchId");
        planSha256 = Objects.requireNonNull(planSha256, "planSha256").trim();
        executedAt = Objects.requireNonNull(executedAt, "executedAt");
        if (!SHA_256.matcher(planSha256).matches()
                || purgedPayloadCount < 1
                || purgedPayloadBytes < 0) {
            throw new IllegalArgumentException("retention execution result is invalid");
        }
    }
}
