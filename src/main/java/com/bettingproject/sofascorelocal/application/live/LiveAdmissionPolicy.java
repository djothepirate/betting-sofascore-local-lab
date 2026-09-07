package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.springframework.stereotype.Component;

@Component
public final class LiveAdmissionPolicy {
    private final LiveCampaignProperties properties;
    private final LiveStorageCapacityProbe storage;
    public LiveAdmissionPolicy(LiveCampaignProperties properties, LiveStorageCapacityProbe storage) {
        this.properties = properties; this.storage = storage;
    }
    public void admit(int matches) {
        properties.validate();
        if (matches < 1 || matches > properties.getQualifiedMatchCapacity())
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        // Simulate the maximum recurring phase: 3 J5 + one J4 per match, serialized.
        // One extra J4 signal is coalesced into that same reserved per-minute slot.
        long exchange = properties.getRequestEnvelope().plus(properties.getProcessingEnvelope()).plusSeconds(3).toMillis();
        long due = 0;
        for (int minute = 0; minute < Math.max(1, properties.getDuration().toMinutes()); minute++) {
            long deadline = (minute + 1L) * 60_000;
            due = Math.max(due, minute * 60_000L);
            for (int match = 0; match < matches; match++) for (int family = 0; family < 4; family++) due += exchange;
            if (due > deadline) throw new IllegalArgumentException("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        }
        requireStorage(maximumBytes(matches));
    }
    public long maximumBytes(int matches) {
        return Math.multiplyExact(Math.min(3000L, 1000L * matches), RawPayloadEvidence.MAXIMUM_BYTES);
    }
    public void requireStorage(long remainingRawBytes) {
        // Two times the remaining raw envelope plus a fixed floor covers index/projection overhead.
        long required = Math.addExact(Math.multiplyExact(Math.max(0, remainingRawBytes), 2), properties.getDiskReserveBytes());
        if (storage.availableBytes() < required) throw new IllegalStateException("LIVE_STORAGE_CAPACITY_REFUSED");
    }
}
