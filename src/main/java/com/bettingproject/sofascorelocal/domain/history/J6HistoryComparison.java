package com.bettingproject.sofascorelocal.domain.history;

import java.util.List;
import java.util.Objects;

public record J6HistoryComparison(
        J6HistoryStream stream,
        J6ComparedVersion fromVersion,
        J6ComparedVersion toVersion,
        J6HistoryClassification classification,
        List<J6SemanticChange> changes) {

    public J6HistoryComparison {
        stream = Objects.requireNonNull(stream, "stream");
        fromVersion = Objects.requireNonNull(fromVersion, "fromVersion");
        toVersion = Objects.requireNonNull(toVersion, "toVersion");
        if (!fromVersion.source().receivedAt().isBefore(toVersion.source().receivedAt())
                && !(fromVersion.source().receivedAt().equals(toVersion.source().receivedAt())
                        && fromVersion.observationId() < toVersion.observationId())) {
            throw new IllegalArgumentException("comparison versions must be chronological");
        }
        classification = Objects.requireNonNull(classification, "classification");
        if (classification == J6HistoryClassification.BASELINE) {
            throw new IllegalArgumentException("a comparison cannot be a baseline");
        }
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }
}
