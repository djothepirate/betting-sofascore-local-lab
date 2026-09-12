package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import java.time.Duration;

/**
 * V10 deliberately reuses the already-qualified, immutable V9 J4/J5 state machine.
 * It only changes admission and durable-pressure limits.  The converted profile is
 * ephemeral scheduler input; persisted V10 evidence always retains its V10 identity.
 */
final class V10GroupedScheduleProfile {
    private V10GroupedScheduleProfile() { }

    static GroupedAdmissionProfile asV9SchedulerProfile(GroupedAdmissionProfile profile) {
        if (profile == null || !"live-v9".equals(profile.policyVersion())) {
            throw new IllegalArgumentException("LIVE_V10_SLOT_PROFILE_REQUIRED");
        }
        return profile;
    }

    static Duration strictGroupReservation(GroupedAdmissionProfile profile) {
        return LiveSchedule.v9StrictGroupReservation(asV9SchedulerProfile(profile));
    }
}
