package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;

import java.util.Objects;

public record J5OfflineBatchEventPayloads(
        J5OfflineBatchPlanEvent event,
        RawPayloadEvidence statistics,
        RawPayloadEvidence incidents,
        RawPayloadEvidence lineups) {

    public J5OfflineBatchEventPayloads {
        event = Objects.requireNonNull(event, "event");
        statistics = Objects.requireNonNull(statistics, "statistics");
        incidents = Objects.requireNonNull(incidents, "incidents");
        lineups = Objects.requireNonNull(lineups, "lineups");
    }

    public long totalBytes() {
        return (long) statistics.sizeBytes() + incidents.sizeBytes() + lineups.sizeBytes();
    }
}
