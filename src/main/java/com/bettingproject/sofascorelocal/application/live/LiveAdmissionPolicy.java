package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.live.LiveCadence;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public final class LiveAdmissionPolicy {
    private final LiveCampaignProperties properties;
    private final LiveStorageCapacityProbe storage;
    public LiveAdmissionPolicy(LiveCampaignProperties properties, LiveStorageCapacityProbe storage) {
        this.properties = properties; this.storage = storage;
    }
    public void admit(int matches) {
        Duration interval = LiveCadence.forMatches(matches);
        admit(matches, interval);
    }
    public void admit(int matches, Duration interval) {
        properties.validate();
        if (matches < 1 || matches > properties.getQualifiedMatchCapacity())
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        LiveCadence.validate(interval);
        if (matches > 1 && (properties.getQualificationSha256() == null
                || !properties.getQualificationSha256().matches("[0-9a-f]{64}")))
            throw new IllegalStateException("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
        // Simulate the maximum recurring phase: 3 J5 + one J4 per match, serialized.
        // One extra J4 signal is coalesced into that same reserved cycle slot.
        long exchange = properties.getRequestEnvelope().plus(properties.getProcessingEnvelope()).plusSeconds(3).toNanos();
        long period = interval.toNanos();
        long due = 0;
        for (long cycle = 0; cycle < Math.max(1, (properties.getDuration().toNanos() + period - 1) / period); cycle++) {
            long deadline = (cycle + 1) * period;
            due = Math.max(due, cycle * period);
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
