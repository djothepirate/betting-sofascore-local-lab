package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.Objects;
import java.util.UUID;

/** Immutable, fully prevalidated input for one local J5 import. */
public record J5LocalJsonImportProcessingPlan(
        UUID canonicalEventId,
        long eventId,
        RawPayloadEvidence statisticsPayload,
        RawPayloadEvidence incidentsPayload,
        RawPayloadEvidence lineupsPayload) {

    public J5LocalJsonImportProcessingPlan {
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        if (eventId < 1) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        statisticsPayload = Objects.requireNonNull(
                statisticsPayload, "statisticsPayload");
        incidentsPayload = Objects.requireNonNull(incidentsPayload, "incidentsPayload");
        lineupsPayload = Objects.requireNonNull(lineupsPayload, "lineupsPayload");
    }

    RawPayloadEvidence payloadFor(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> statisticsPayload;
            case EVENT_INCIDENTS -> incidentsPayload;
            case EVENT_LINEUPS -> lineupsPayload;
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
    }
}
