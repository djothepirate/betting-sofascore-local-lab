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
        int scheduledTournamentCount,
        int insertedObservationCount,
        int deduplicatedObservationCount,
        List<UUID> canonicalEventIds) {

    public J4SnapshotNormalizationResult {
        historicalSchemaStatus = Objects.requireNonNull(
                historicalSchemaStatus,
                "historicalSchemaStatus");
        currentParseStatus = Objects.requireNonNull(currentParseStatus, "currentParseStatus");
        payloadShape = Objects.requireNonNull(payloadShape, "payloadShape");
        canonicalEventIds = List.copyOf(Objects.requireNonNull(
                canonicalEventIds,
                "canonicalEventIds"));
        if (snapshotId < 1
                || parsedEventCount < 0
                || scheduledTournamentCount < 0
                || insertedObservationCount < 0
                || deduplicatedObservationCount < 0
                || insertedObservationCount + deduplicatedObservationCount != parsedEventCount
                || canonicalEventIds.size() != parsedEventCount) {
            throw new IllegalArgumentException("snapshot normalization counts are inconsistent");
        }
        boolean eventList = "EVENT_LIST".equals(payloadShape);
        boolean scheduledTournamentList = "SCHEDULED_TOURNAMENT_LIST".equals(payloadShape);
        boolean noParsedShape = "NONE".equals(payloadShape);
        if ((currentParseStatus == ScheduledEventsParseStatus.PARSED && noParsedShape)
                || (currentParseStatus != ScheduledEventsParseStatus.PARSED && !noParsedShape)
                || (eventList && scheduledTournamentCount != 0)
                || (scheduledTournamentList && parsedEventCount != 0)
                || (noParsedShape && (parsedEventCount != 0 || scheduledTournamentCount != 0))
                || (!eventList && !scheduledTournamentList && !noParsedShape)) {
            throw new IllegalArgumentException(
                    "snapshot normalization payload shape is inconsistent");
        }
    }
}
