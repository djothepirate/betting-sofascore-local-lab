package com.bettingproject.sofascorelocal.domain.export;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record J7ExportDecision(
        J7ExportStatus status,
        Instant decidedAt,
        Optional<String> reason,
        String relativePath,
        String contentSha256,
        long contentSizeBytes) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J7ExportDecision {
        status = Objects.requireNonNull(status, "status");
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        reason = Objects.requireNonNull(reason, "reason");
        relativePath = Objects.requireNonNull(relativePath, "relativePath");
        contentSha256 = Objects.requireNonNull(contentSha256, "contentSha256");
        if (!status.isTerminal()
                || contentSizeBytes < 1
                || relativePath.isBlank()
                || !SHA_256.matcher(contentSha256).matches()) {
            throw new IllegalArgumentException("decision must be terminal with a positive size");
        }
        if (status == J7ExportStatus.HUMAN_VALIDATED && reason.isPresent()) {
            throw new IllegalArgumentException("validation cannot have a rejection reason");
        }
        if (status == J7ExportStatus.REJECTED && reason.isEmpty()) {
            throw new IllegalArgumentException("rejection requires a reason");
        }
    }
}
