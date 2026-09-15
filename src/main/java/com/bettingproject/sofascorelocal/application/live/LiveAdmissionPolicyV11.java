package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import java.time.Duration;
import java.util.Objects;

/**
 * Current local live admission policy. V11 is independently replayed so immutable
 * V9 evidence and its historical maximum of ten matches remain reproducible.
 */
final class LiveAdmissionPolicyV11 {
    static final int V11_MAXIMUM_SELECTION_SIZE = 8;
    static final int V11_MAXIMUM_CALLS_PER_MINUTE = 35;
    static final int V11_MAXIMUM_CALLS_PER_HOUR = 2100;
    private static final int HOURLY_CALLS_PER_MATCH = 248;
    private final LiveCampaignProperties properties;
    private final LiveAdmissionPolicy storage;

    LiveAdmissionPolicyV11(LiveCampaignProperties properties, LiveAdmissionPolicy storage) {
        this.properties = Objects.requireNonNull(properties);
        this.storage = Objects.requireNonNull(storage);
    }

    void admit(int matches, GroupedAdmissionProfile profile) {
        properties.validate();
        if (matches < 1 || matches > properties.getQualifiedMatchCapacity() || matches > V11_MAXIMUM_SELECTION_SIZE) {
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        }
        if (matches > qualifiedCapacity(profile)) {
            throw new IllegalArgumentException("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        }
        storage.requireStorage(storage.maximumBytesV5(matches));
    }

    static int qualifiedCapacity(GroupedAdmissionProfile profile) {
        if (profile == null) throw new IllegalStateException("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
        // V11 owns its configuration and replay. The nested V9 value-object shape is
        // retained for endpoint envelopes; the replay executes the V11 scheduler.
        if (!"live-v9".equals(profile.policyVersion())) throw new IllegalStateException("LIVE_GROUPED_POLICY_MISMATCH");
        int hourlyCapacity = V11_MAXIMUM_CALLS_PER_HOUR / HOURLY_CALLS_PER_MATCH;
        long phaseReservation = V10GroupedScheduleProfile.strictGroupReservation(profile).toNanos();
        int temporalCapacity = (int) Math.min(V11_MAXIMUM_SELECTION_SIZE,
                Duration.ofMinutes(1).toNanos() / phaseReservation);
        int capacity = Math.min(hourlyCapacity, temporalCapacity);
        while (capacity > 0 && !GroupedLiveAdmissionSimulationV11.fits(capacity, profile)) capacity--;
        return capacity;
    }

    static double estimatedLiveCallsPerMinute(int matches) {
        if (matches < 1 || matches > V11_MAXIMUM_SELECTION_SIZE) {
            throw new IllegalArgumentException("invalid live selection size");
        }
        return matches * 4.0d;
    }
}
