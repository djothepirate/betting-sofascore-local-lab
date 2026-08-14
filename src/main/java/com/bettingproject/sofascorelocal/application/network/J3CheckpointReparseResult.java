package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;

import java.util.Objects;

/** Metadata-only result of reparsing an immutable local qualification checkpoint. */
public record J3CheckpointReparseResult(
        long snapshotId,
        int page,
        RawSnapshotSchemaStatus historicalSchemaStatus,
        ScheduledEventsParseStatus currentParseStatus,
        Boolean hasNextPage,
        int scheduledTournamentCount) {

    public J3CheckpointReparseResult {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        if (page < ScheduledEventsProviderPageRequest.FIRST_PAGE
                || page > ScheduledEventsProviderPageRequest.LAST_PAGE) {
            throw new IllegalArgumentException("page must be between 1 and 5");
        }
        Objects.requireNonNull(historicalSchemaStatus, "historicalSchemaStatus");
        Objects.requireNonNull(currentParseStatus, "currentParseStatus");
        if (currentParseStatus == ScheduledEventsParseStatus.PARSED) {
            Objects.requireNonNull(hasNextPage, "hasNextPage");
            if (scheduledTournamentCount < 0) {
                throw new IllegalArgumentException(
                        "scheduledTournamentCount cannot be negative");
            }
        }
        else if (hasNextPage != null || scheduledTournamentCount != 0) {
            throw new IllegalArgumentException(
                    "an incompatible checkpoint cannot expose a partial page");
        }
    }
}
