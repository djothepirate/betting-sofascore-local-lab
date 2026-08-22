package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessingPlan;

import java.util.Objects;

public record J5OfflineBatchPreparedEvent(
        J5OfflineBatchPlanEvent event,
        J5LocalJsonImportProcessingPlan processingPlan) {

    public J5OfflineBatchPreparedEvent {
        event = Objects.requireNonNull(event, "event");
        processingPlan = Objects.requireNonNull(processingPlan, "processingPlan");
        if (!event.canonicalEventId().equals(processingPlan.canonicalEventId())
                || event.providerEventId() != processingPlan.eventId()) {
            throw new IllegalArgumentException("prepared event identity is inconsistent");
        }
    }
}
