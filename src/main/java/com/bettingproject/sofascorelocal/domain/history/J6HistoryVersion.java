package com.bettingproject.sofascorelocal.domain.history;

import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

public record J6HistoryVersion(
        J6HistoryStream stream,
        long observationId,
        EventSourceTrace source,
        String normalizedSha256,
        Optional<J6CompletenessSummary> completeness,
        Optional<J6Score> score,
        J6HistoryClassification classification,
        Set<J6HistoryClassification> classifications,
        OptionalLong previousObservationId,
        List<J6SemanticChange> changesFromPrevious,
        Optional<J6SnapshotTrace> snapshotTrace) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public J6HistoryVersion {
        stream = Objects.requireNonNull(stream, "stream");
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
        classification = Objects.requireNonNull(classification, "classification");
        classifications = Set.copyOf(Objects.requireNonNull(
                classifications,
                "classifications"));
        if (!classifications.contains(classification)) {
            throw new IllegalArgumentException("classifications must contain the primary value");
        }
        previousObservationId = Objects.requireNonNull(
                previousObservationId,
                "previousObservationId");
        if (previousObservationId.isPresent()
                && previousObservationId.getAsLong() < 1) {
            throw new IllegalArgumentException("previousObservationId must be positive");
        }
        changesFromPrevious = List.copyOf(Objects.requireNonNull(
                changesFromPrevious,
                "changesFromPrevious"));
        snapshotTrace = Objects.requireNonNull(snapshotTrace, "snapshotTrace");
        if (source.kind() == EventSourceKind.PROVIDER_SNAPSHOT
                && (snapshotTrace.isEmpty()
                        || snapshotTrace.orElseThrow().snapshotId()
                                != source.snapshotId().orElseThrow())) {
            throw new IllegalArgumentException(
                    "provider history versions require their snapshot trace");
        }
        if (source.kind() == EventSourceKind.SYNTHETIC_FIXTURE && snapshotTrace.isPresent()) {
            throw new IllegalArgumentException(
                    "synthetic history versions cannot contain a snapshot trace");
        }
        if (previousObservationId.isEmpty()
                != (classification == J6HistoryClassification.BASELINE)) {
            throw new IllegalArgumentException(
                    "only the first history version can be classified as BASELINE");
        }
        if (previousObservationId.isEmpty() && !changesFromPrevious.isEmpty()) {
            throw new IllegalArgumentException("a baseline cannot contain previous changes");
        }
    }

    public static Set<J6HistoryClassification> classifications(
            J6HistoryClassification primary,
            Optional<J6SnapshotTrace> trace) {
        EnumSet<J6HistoryClassification> result = EnumSet.of(primary);
        trace.filter(J6SnapshotTrace::hasTechnicalDuplicates)
                .ifPresent(ignored -> result.add(
                        J6HistoryClassification.TECHNICAL_DUPLICATE));
        return Set.copyOf(result);
    }
}
