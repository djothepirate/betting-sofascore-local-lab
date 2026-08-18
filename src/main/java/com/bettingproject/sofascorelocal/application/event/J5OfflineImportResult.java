package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;

import java.util.Objects;
import java.util.UUID;

public record J5OfflineImportResult(
        UUID canonicalEventId,
        long statisticsObservationId,
        boolean statisticsInserted,
        J5CompletenessStatus statisticsCompleteness,
        long incidentsObservationId,
        boolean incidentsInserted,
        J5CompletenessStatus incidentsCompleteness,
        long lineupsObservationId,
        boolean lineupsInserted,
        J5CompletenessStatus lineupsCompleteness) {

    public J5OfflineImportResult {
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        if (statisticsObservationId < 1
                || incidentsObservationId < 1
                || lineupsObservationId < 1) {
            throw new IllegalArgumentException("J5 observation identifiers must be positive");
        }
        statisticsCompleteness = Objects.requireNonNull(
                statisticsCompleteness,
                "statisticsCompleteness");
        incidentsCompleteness = Objects.requireNonNull(
                incidentsCompleteness,
                "incidentsCompleteness");
        lineupsCompleteness = Objects.requireNonNull(
                lineupsCompleteness,
                "lineupsCompleteness");
    }
}
