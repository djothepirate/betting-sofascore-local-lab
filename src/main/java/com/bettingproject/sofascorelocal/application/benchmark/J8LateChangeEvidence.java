package com.bettingproject.sofascorelocal.application.benchmark;

import com.bettingproject.sofascorelocal.domain.history.J6HistoryStream;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public record J8LateChangeEvidence(
        UUID canonicalEventId,
        J6HistoryStream stream,
        long observationId,
        long previousObservationId,
        long sourceSnapshotId,
        Instant receivedAt,
        OptionalLong previousDirectStateObservationId,
        Optional<String> previousDirectStateStatus,
        Optional<Instant> previousDirectStateAt) {

    public J8LateChangeEvidence {
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        stream = Objects.requireNonNull(stream, "stream");
        if (observationId < 1 || previousObservationId < 1
                || observationId == previousObservationId
                || sourceSnapshotId < 1) {
            throw new IllegalArgumentException(
                    "late-change observation and snapshot identifiers are invalid");
        }
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        previousDirectStateObservationId = Objects.requireNonNull(
                previousDirectStateObservationId,
                "previousDirectStateObservationId");
        previousDirectStateStatus = Objects.requireNonNull(
                previousDirectStateStatus, "previousDirectStateStatus");
        previousDirectStateAt = Objects.requireNonNull(
                previousDirectStateAt, "previousDirectStateAt");
        if (previousDirectStateObservationId.isPresent()
                    != previousDirectStateStatus.isPresent()
                || previousDirectStateStatus.isPresent()
                    != previousDirectStateAt.isPresent()) {
            throw new IllegalArgumentException(
                    "the previous direct state identifier, status and time must be paired");
        }
        if (previousDirectStateObservationId.isPresent()
                && previousDirectStateObservationId.orElseThrow() < 1) {
            throw new IllegalArgumentException(
                    "the previous direct state observation identifier is invalid");
        }
        previousDirectStateStatus.ifPresent(status -> {
            if (status.isBlank() || status.length() > 64) {
                throw new IllegalArgumentException(
                        "the previous direct state status is invalid");
            }
        });
        if (previousDirectStateAt.isPresent()
                && previousDirectStateAt.orElseThrow().isAfter(receivedAt)) {
            throw new IllegalArgumentException(
                    "the previous direct state cannot follow the change");
        }
    }
}
