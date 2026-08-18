package com.bettingproject.sofascorelocal.domain.history;

import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record J6ComparedVersion(
        long observationId,
        EventSourceTrace source,
        String normalizedSha256,
        Optional<J6CompletenessSummary> completeness,
        Optional<J6Score> score,
        Optional<J6SnapshotTrace> snapshotTrace) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public J6ComparedVersion {
        if (observationId < 1) {
            throw new IllegalArgumentException("observationId must be positive");
        }
        source = Objects.requireNonNull(source, "source");
        normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
        if (!SHA_256_PATTERN.matcher(normalizedSha256).matches()) {
            throw new IllegalArgumentException("normalizedSha256 must be a lower-case SHA-256");
        }
        completeness = Objects.requireNonNull(completeness, "completeness");
        score = Objects.requireNonNull(score, "score");
        snapshotTrace = Objects.requireNonNull(snapshotTrace, "snapshotTrace");
        if (source.kind() == EventSourceKind.PROVIDER_SNAPSHOT
                && (snapshotTrace.isEmpty()
                        || snapshotTrace.orElseThrow().snapshotId()
                                != source.snapshotId().orElseThrow())) {
            throw new IllegalArgumentException(
                    "provider compared versions require their snapshot trace");
        }
        if (source.kind() == EventSourceKind.SYNTHETIC_FIXTURE && snapshotTrace.isPresent()) {
            throw new IllegalArgumentException(
                    "synthetic compared versions cannot contain a snapshot trace");
        }
    }
}
