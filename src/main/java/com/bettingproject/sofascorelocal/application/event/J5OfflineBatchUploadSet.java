package com.bettingproject.sofascorelocal.application.event;

import java.util.List;
import java.util.Objects;

public record J5OfflineBatchUploadSet(
        List<J5OfflineBatchEventPayloads> events,
        long totalBytes) {

    public J5OfflineBatchUploadSet {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (events.isEmpty()
                || events.size() > J5OfflineBatchPlanService.MAXIMUM_EVENTS
                || totalBytes < 1
                || totalBytes > J5OfflineBatchUploadService.MAXIMUM_TOTAL_BYTES
                || events.stream().mapToLong(J5OfflineBatchEventPayloads::totalBytes).sum()
                        != totalBytes) {
            throw new IllegalArgumentException("offline batch upload set is inconsistent");
        }
    }
}
