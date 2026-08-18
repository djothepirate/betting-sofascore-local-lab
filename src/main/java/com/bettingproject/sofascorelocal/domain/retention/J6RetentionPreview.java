package com.bettingproject.sofascorelocal.domain.retention;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record J6RetentionPreview(
        int retentionDays,
        Instant generatedAt,
        Instant cutoffAt,
        long totalEligibleCount,
        long totalEligibleBytes,
        Optional<Instant> oldestEligibleReceivedAt,
        List<J6RetentionCandidate> candidates,
        String planSha256) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J6RetentionPreview {
        if (retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalArgumentException("retentionDays must be between 1 and 3650");
        }
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        cutoffAt = Objects.requireNonNull(cutoffAt, "cutoffAt");
        oldestEligibleReceivedAt = Objects.requireNonNull(
                oldestEligibleReceivedAt,
                "oldestEligibleReceivedAt");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        planSha256 = Objects.requireNonNull(planSha256, "planSha256").trim();
        if (!cutoffAt.isBefore(generatedAt)) {
            throw new IllegalArgumentException("cutoffAt must precede generatedAt");
        }
        if (totalEligibleCount < 0
                || totalEligibleBytes < 0
                || candidates.size() > totalEligibleCount
                || selectedPayloadBytes(candidates) > totalEligibleBytes) {
            throw new IllegalArgumentException("retention preview counters are inconsistent");
        }
        if ((totalEligibleCount == 0) != oldestEligibleReceivedAt.isEmpty()) {
            throw new IllegalArgumentException("oldest eligible timestamp is inconsistent");
        }
        if (!SHA_256.matcher(planSha256).matches()) {
            throw new IllegalArgumentException("planSha256 must be a lower-case SHA-256");
        }
    }

    public int selectedCount() {
        return candidates.size();
    }

    public long selectedPayloadBytes() {
        return selectedPayloadBytes(candidates);
    }

    public boolean truncated() {
        return totalEligibleCount > candidates.size();
    }

    public String confirmationPhrase() {
        return "PURGER " + selectedCount() + " PAYLOADS J6 " + planSha256;
    }

    private static long selectedPayloadBytes(List<J6RetentionCandidate> candidates) {
        return candidates.stream()
                .mapToLong(J6RetentionCandidate::payloadSizeBytes)
                .reduce(0L, Math::addExact);
    }
}
