package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.live.LiveCadence;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
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

    /** Admission of a fixed-minute policy never lengthens its cadence to fit a selection. */
    public void admitV4(int matches, GroupedAdmissionProfile profile) {
        properties.validate();
        if (matches < 1 || matches > properties.getQualifiedMatchCapacity()
                || matches > LiveCadence.MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        if (matches > qualifiedCapacityV4(profile))
            throw new IllegalArgumentException("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        requireStorage(maximumBytes(matches));
    }

    /**
     * Pure temporal bound, independent of the configured/operator maximum and storage.
     * The established five-minute sequence has five calls to each critical family,
     * one lineup call and five inter-group pauses per match. Ten percent of that
     * capacity remains unallocated. Startup, kickoff, prematch and finalization waves
     * are checked separately by the production-scheduler replay below.
     */
    public static int qualifiedCapacityV4(GroupedAdmissionProfile profile) {
        if (profile == null) throw new IllegalStateException("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
        long weightedNanos = Math.multiplyExact(profile.interGroupDelay().toNanos(), 5);
        for (SofascoreEndpointType endpoint : new SofascoreEndpointType[] {
                SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS}) {
            weightedNanos = Math.addExact(weightedNanos,
                    Math.multiplyExact(profile.envelope(endpoint).exchangeEnvelope().toNanos(), 5));
        }
        weightedNanos = Math.addExact(weightedNanos,
                profile.envelope(SofascoreEndpointType.EVENT_LINEUPS).exchangeEnvelope().toNanos());
        // Work in five-minute integer nanoseconds: no rounded 1/5 lineup duration,
        // and no nanosecond over the headroom boundary can disappear by truncation.
        long usableNanos = Math.multiplyExact(Math.multiplyExact(profile.criticalInterval().toNanos(), 5), 9) / 10;
        int capacity = (int) Math.min(LiveCadence.MAXIMUM_SELECTION_SIZE, usableNanos / weightedNanos);
        while (capacity > 0 && !GroupedLiveAdmissionSimulation.fits(capacity, profile)) capacity--;
        return capacity;
    }

    /** Steady in-play estimate only; startup, prematch and final waves are additional. */
    public static double estimatedLiveCallsPerMinuteV4(int matches) {
        if (matches < 1 || matches > LiveCadence.MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("invalid live selection size");
        return matches * 3.2;
    }
    public void requireStorage(long remainingRawBytes) {
        // Two times the remaining raw envelope plus a fixed floor covers index/projection overhead.
        long required = Math.addExact(Math.multiplyExact(Math.max(0, remainingRawBytes), 2), properties.getDiskReserveBytes());
        if (storage.availableBytes() < required) throw new IllegalStateException("LIVE_STORAGE_CAPACITY_REFUSED");
    }
}
