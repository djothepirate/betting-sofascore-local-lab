package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import java.time.Duration;
import java.util.Objects;

/** Offline replay bound for the V10 pressure envelope. It performs no provider work. */
final class GroupedLiveAdmissionSimulationV10 {
    static final int MAXIMUM_DEPARTURES_PER_MINUTE = 35;
    static final int MAXIMUM_DEPARTURES_PER_HOUR = 2100;
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private GroupedLiveAdmissionSimulationV10() { }

    static boolean fits(int matches, GroupedAdmissionProfile profile) {
        if (!hasStrictMinuteDepartureBudget(matches, profile)) return false;
        // The J4-driven state machine is intentionally the immutable V9 implementation.
        return GroupedLiveAdmissionSimulationV9.fits(matches, V10GroupedScheduleProfile.asV9SchedulerProfile(profile));
    }

    static boolean hasStrictMinuteDepartureBudget(int matches, GroupedAdmissionProfile profile) {
        if (matches < 1 || matches > LiveAdmissionPolicyV10.V10_MAXIMUM_SELECTION_SIZE) return false;
        Objects.requireNonNull(profile);
        try {
            return Math.multiplyExact(V10GroupedScheduleProfile.strictGroupReservation(profile).toNanos(), matches)
                    <= MINUTE.toNanos();
        } catch (ArithmeticException invalid) {
            return false;
        }
    }
}
