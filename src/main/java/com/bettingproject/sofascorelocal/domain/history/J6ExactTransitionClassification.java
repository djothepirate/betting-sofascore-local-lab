package com.bettingproject.sofascorelocal.domain.history;

import java.util.List;
import java.util.Objects;

/**
 * Classification of one exact immutable observation transition.
 */
public record J6ExactTransitionClassification(
        J6HistoryStream stream,
        long previousObservationId,
        long observationId,
        J6HistoryClassification classification,
        List<J6SemanticChange> changes) {

    public J6ExactTransitionClassification {
        stream = Objects.requireNonNull(stream, "stream");
        if (previousObservationId < 1 || observationId < 1
                || previousObservationId == observationId) {
            throw new IllegalArgumentException(
                    "exact transition observation ids must be positive and distinct");
        }
        classification = Objects.requireNonNull(classification, "classification");
        if (classification == J6HistoryClassification.BASELINE) {
            throw new IllegalArgumentException(
                    "an exact transition classification cannot be a baseline");
        }
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }
}
