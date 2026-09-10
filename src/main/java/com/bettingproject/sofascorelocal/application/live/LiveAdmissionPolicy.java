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
    public static final int V5_MAXIMUM_SELECTION_SIZE = 20;
    public static final int V6_MAXIMUM_SELECTION_SIZE = 7;
    public static final int V7_MAXIMUM_SELECTION_SIZE = 3;
    /** V8 is separately qualified: ten 60-second four-family groups under the isolated pressure policy. */
    public static final int V8_MAXIMUM_SELECTION_SIZE = 10;
    public static final int V6_MAXIMUM_CALLS_PER_MINUTE = 25;
    public static final int V6_MAXIMUM_CALLS_PER_HOUR = 1000;
    public static final int V8_MAXIMUM_CALLS_PER_MINUTE = 45;
    public static final int V8_MAXIMUM_CALLS_PER_HOUR = 2756;
    public static final long V5_MAXIMUM_RAW_BYTES = 15_728_640_000L;
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

    /** V5 keeps a separately bounded byte allowance; increasing call limits does not inflate it. */
    public long maximumBytesV5(int matches) {
        if (matches < 1 || matches > V5_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        return V5_MAXIMUM_RAW_BYTES;
    }

    public void admitV5(int matches, GroupedAdmissionProfile profile) {
        properties.validate();
        if (matches < 1 || matches > properties.getQualifiedMatchCapacity() || matches > V5_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        if (matches > qualifiedCapacityV5(profile))
            throw new IllegalArgumentException("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        requireStorage(maximumBytesV5(matches));
    }

    public void admitV6(int matches, GroupedAdmissionProfile profile) {
        properties.validate();
        if (matches < 1 || matches > properties.getQualifiedMatchCapacity() || matches > V6_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        if (matches > qualifiedCapacityV6(profile))
            throw new IllegalArgumentException("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        requireStorage(maximumBytesV5(matches));
    }

    public void admitV7(int matches, GroupedAdmissionProfile profile) {
        properties.validate();
        if (matches < 1 || matches > properties.getQualifiedMatchCapacity() || matches > V7_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        if (matches > qualifiedCapacityV7(profile)) throw new IllegalArgumentException("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        requireStorage(maximumBytesV5(matches));
    }

    public void admitV8(int matches, GroupedAdmissionProfile profile) {
        properties.validate();
        if (matches < 1 || matches > properties.getQualifiedMatchCapacity() || matches > V8_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        if (matches > qualifiedCapacityV8(profile)) throw new IllegalArgumentException("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        requireStorage(maximumBytesV5(matches));
    }

    /** 240 ordinary calls/hour plus four initial and four final calls, with 10% headroom. */
    public static int qualifiedCapacityV7(GroupedAdmissionProfile profile) {
        int hourlyCapacity = (V6_MAXIMUM_CALLS_PER_HOUR * 9 / 10) / 248;
        return qualifiedGroupedCapacity(profile, "live-v7", Math.min(V7_MAXIMUM_SELECTION_SIZE, hourlyCapacity));
    }

    /**
     * Ten events use at most 2,480 planned hourly departures (240 ordinary plus four
     * initial and four final calls each), leaving 276 of the durable 2,756 budget
     * unallocated. This hourly planning margin is separate from V51's 60-second
     * group-reservation bound below.
     */
    public static int qualifiedCapacityV8(GroupedAdmissionProfile profile) {
        if (profile == null) throw new IllegalStateException("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
        if (!"live-v8".equals(profile.policyVersion())) throw new IllegalStateException("LIVE_GROUPED_POLICY_MISMATCH");
        int hourlyCapacity = (V8_MAXIMUM_CALLS_PER_HOUR * 9 / 10) / 248;
        long phaseReservation = LiveSchedule.v8StrictGroupReservation(profile).toNanos();
        int temporalCapacity = (int) Math.min(V8_MAXIMUM_SELECTION_SIZE,
                Duration.ofMinutes(1).toNanos() / phaseReservation);
        int capacity = Math.min(hourlyCapacity, temporalCapacity);
        // This exact phase formula is also enforced by append-only Flyway V51.
        // Replay keeps admission honest about actual request emissions and
        // explicit recovery states, which arithmetic alone cannot establish.
        while (capacity > 0 && !GroupedLiveAdmissionSimulationV8.fits(capacity, profile)) capacity--;
        return capacity;
    }

    /**
     * Pure temporal bound, independent of the configured/operator maximum and storage.
     * The established five-minute sequence has five calls to each critical family,
     * one lineup call and five inter-group pauses per match. Ten percent of that
     * capacity remains unallocated. Startup, kickoff, prematch and finalization waves
     * are checked separately by the production-scheduler replay below.
     */
    public static int qualifiedCapacityV4(GroupedAdmissionProfile profile) {
        return qualifiedGroupedCapacity(profile, "live-v4", LiveCadence.MAXIMUM_SELECTION_SIZE);
    }

    public static int qualifiedCapacityV5(GroupedAdmissionProfile profile) {
        return qualifiedGroupedCapacity(profile, "live-v5", V5_MAXIMUM_SELECTION_SIZE);
    }

    /**
     * 120 ordinary calls/hour plus four initial and four final calls per event.
     * 7 * 128 = 896, inside 90% of the local 1000/hour allowance; 8 would use 1024.
     * The shared rolling limiter can still postpone calls when earlier campaigns
     * consumed that allowance. This is not a promise of a 100-second interval.
     */
    public static int qualifiedCapacityV6(GroupedAdmissionProfile profile) {
        int hourlyCapacity = (V6_MAXIMUM_CALLS_PER_HOUR * 9 / 10) / 128;
        return qualifiedGroupedCapacity(profile, "live-v6", Math.min(V6_MAXIMUM_SELECTION_SIZE, hourlyCapacity));
    }

    private static int qualifiedGroupedCapacity(GroupedAdmissionProfile profile, String policyVersion, int maximumMatches) {
        if (profile == null) throw new IllegalStateException("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
        if (!policyVersion.equals(profile.policyVersion())) throw new IllegalStateException("LIVE_GROUPED_POLICY_MISMATCH");
        long rounds = profile.lineupInterval().toSeconds() / profile.criticalInterval().toSeconds();
        long weightedNanos = Math.multiplyExact(profile.interGroupDelay().toNanos(), rounds);
        if ("live-v6".equals(policyVersion) || "live-v7".equals(policyVersion) || "live-v8".equals(policyVersion)) {
            // The durable limiter waits after every exchange, including same-group calls.
            weightedNanos = Math.multiplyExact(profile.minimumRequestStartInterval().toNanos(), 3 * rounds + 1);
        }
        for (SofascoreEndpointType endpoint : new SofascoreEndpointType[] {
                SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS}) {
            weightedNanos = Math.addExact(weightedNanos,
                    Math.multiplyExact(profile.envelope(endpoint).exchangeEnvelope().toNanos(), rounds));
        }
        weightedNanos = Math.addExact(weightedNanos,
                profile.envelope(SofascoreEndpointType.EVENT_LINEUPS).exchangeEnvelope().toNanos());
        // Work in five-minute integer nanoseconds: no rounded 1/5 lineup duration,
        // and no nanosecond over the headroom boundary can disappear by truncation.
        long usableNanos = Math.multiplyExact(profile.lineupInterval().toNanos(), 9) / 10;
        int capacity = (int) Math.min(maximumMatches, usableNanos / weightedNanos);
        while (capacity > 0 && !("live-v8".equals(policyVersion)
                ? GroupedLiveAdmissionSimulationV8.fits(capacity, profile)
                : "live-v7".equals(policyVersion)
                ? GroupedLiveAdmissionSimulationV7.fits(capacity, profile)
                : GroupedLiveAdmissionSimulation.fits(capacity, profile))) capacity--;
        return capacity;
    }

    /** Steady in-play estimate only; startup, prematch and final waves are additional. */
    public static double estimatedLiveCallsPerMinuteV4(int matches) {
        if (matches < 1 || matches > LiveCadence.MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("invalid live selection size");
        return matches * 3.2;
    }

    /** Three critical rounds plus one lineup per five minutes, excluding startup/finalization. */
    public static double estimatedLiveCallsPerMinuteV5(int matches) {
        if (matches < 1 || matches > V5_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("invalid live selection size");
        return matches * 2.0;
    }
    public static double estimatedLiveCallsPerMinuteV6(int matches) {
        if (matches < 1 || matches > V6_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("invalid live selection size");
        return matches * 2.0;
    }
    public static double estimatedLiveCallsPerMinuteV7(int matches) {
        if (matches < 1 || matches > V7_MAXIMUM_SELECTION_SIZE) throw new IllegalArgumentException("invalid live selection size");
        return matches * 4.0;
    }
    public static double estimatedLiveCallsPerMinuteV8(int matches) {
        if (matches < 1 || matches > V8_MAXIMUM_SELECTION_SIZE) throw new IllegalArgumentException("invalid live selection size");
        return matches * 4.0;
    }
    public void requireStorage(long remainingRawBytes) {
        // Two times the remaining raw envelope plus a fixed floor covers index/projection overhead.
        long required = Math.addExact(Math.multiplyExact(Math.max(0, remainingRawBytes), 2), properties.getDiskReserveBytes());
        if (storage.availableBytes() < required) throw new IllegalStateException("LIVE_STORAGE_CAPACITY_REFUSED");
    }
}
