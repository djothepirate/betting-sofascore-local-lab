package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J4SnapshotNormalizationResult(
        long snapshotId,
        RawSnapshotSchemaStatus historicalSchemaStatus,
        ScheduledEventsParseStatus currentParseStatus,
        String payloadShape,
        int parsedEventCount,
        int insertedObservationCount,
        int deduplicatedObservationCount,
        List<UUID> canonicalEventIds) {

    public J4SnapshotNormalizationResult {
        if (snapshotId < 1
                || parsedEventCount < 0
                || insertedObservationCount < 0
                || deduplicatedObservationCount < 0
                || insertedObservationCount + deduplicatedObservationCount != parsedEventCount) {
            throw new IllegalArgumentException("snapshot normalization counts are inconsistent");
        }
        historicalSchemaStatus = Objects.requireNonNull(
                historicalSchemaStatus,
                "historicalSchemaStatus");
        currentParseStatus = Objects.requireNonNull(currentParseStatus, "currentParseStatus");
        payloadShape = Objects.requireNonNull(payloadShape, "payloadShape");
        canonicalEventIds = List.copyOf(Objects.requireNonNull(
                canonicalEventIds,
                "canonicalEventIds"));
    }
}
