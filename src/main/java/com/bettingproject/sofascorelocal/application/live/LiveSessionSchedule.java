package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts;
import com.bettingproject.sofascorelocal.port.J3LivePauseStore.MissedSlot;
import java.time.*;
import java.util.*;
import static com.bettingproject.sofascorelocal.application.live.LiveSchedule.*;

/**
 * Session facade for the independently qualified V11 policy.
 * The historical LiveSchedule bytecode and its V8/V9 evidence remain immutable.
 */
final class LiveSessionSchedule {
    private final LiveSchedule historical;
    private final GroupedLiveScheduleV11 v11;

    LiveSessionSchedule(List<UUID> targets, Instant start, Instant endsAt, Duration interval,
                        String policy, UUID campaignId, GroupedAdmissionProfile profile) {
        if ("live-v11".equals(policy)) {
            historical = null;
            v11 = new GroupedLiveScheduleV11(targets, start, endsAt, interval, campaignId,
                    V10GroupedScheduleProfile.asV9SchedulerProfile(profile));
        } else {
            v11 = null;
            historical = new LiveSchedule(targets, start, endsAt, interval, policy, campaignId, profile);
        }
    }

    synchronized void pauseForJ3(Instant now) {
        if (v11 == null) throw new IllegalStateException("J3_LIVE_POLICY_UNSUPPORTED");
        v11.pauseForJ3(now);
    }
    synchronized List<MissedSlot> resumeAfterJ3(Instant now) {
        if (v11 == null) throw new IllegalStateException("J3_LIVE_POLICY_UNSUPPORTED");
        return v11.resumeAfterJ3(now);
    }
    synchronized Optional<Due> next(Instant now) { return v11 != null ? v11.next(now) : historical.next(now); }
    synchronized boolean mayDispatch(Due due, Instant now) {
        return v11 != null ? v11.mayDispatch(due, now) : historical.mayDispatch(due, now);
    }
    synchronized void defer(Due due, Instant at) {
        if (v11 != null) v11.defer(due, at); else historical.defer(due, at);
    }
    synchronized void waitForPostExchangeFence(Due due, Instant at) {
        if (v11 != null) v11.waitForPostExchangeFence(due, at); else historical.waitForPostExchangeFence(due, at);
    }
    synchronized void deferAfterTimeout(Due due, Instant at) {
        if (v11 != null) v11.deferAfterTimeout(due, at); else historical.deferAfterTimeout(due, at);
    }
    synchronized void started(Due due, Instant at) {
        if (v11 != null) v11.started(due, at); else historical.started(due, at);
    }
    synchronized void departed(Due due, Instant at) {
        if (v11 != null) v11.departed(due, at); else historical.departed(due, at);
    }
    synchronized void completed(Due due, String status, boolean unavailable, Map<String, Boolean> signals,
                                Instant now, Instant kickoff, Boolean lineups, LiveJ4ControlFacts controls) {
        completed(due, status, unavailable, signals, now, kickoff, lineups, controls, null);
    }
    synchronized void completed(Due due, String status, boolean unavailable, Map<String, Boolean> signals,
                                Instant now, Instant kickoff, Boolean lineups, LiveJ4ControlFacts controls,
                                String responseCode) {
        if (v11 != null) v11.completed(due, status, unavailable, signals, now, kickoff, lineups, controls, responseCode);
        else historical.completed(due, status, unavailable, signals, now, kickoff, lineups, controls, responseCode);
    }
    synchronized void reserveFinalCheck(UUID id, Instant at) {
        if (v11 != null) v11.reserveFinalCheck(id, at); else historical.reserveFinalCheck(id, at);
    }
    synchronized void failed(Due due, String scope, String reason) {
        if (v11 != null) v11.failed(due, scope, reason); else historical.failed(due, scope, reason);
    }
    synchronized void stopEvent(UUID id, String reason) {
        if (v11 != null) v11.stopEvent(id, reason); else historical.stopEvent(id, reason);
    }
    synchronized void stopAll(String reason) {
        if (v11 != null) v11.stopAll(reason); else historical.stopAll(reason);
    }
    synchronized boolean terminal() { return v11 != null ? v11.terminal() : historical.terminal(); }
    synchronized String globalStop() { return v11 != null ? v11.globalStop() : historical.globalStop(); }
    synchronized List<EventState> states() { return v11 != null ? v11.states() : historical.states(); }
    synchronized List<FamilySchedule> familySchedules(UUID id) {
        return v11 != null ? v11.familySchedules(id) : historical.familySchedules(id);
    }
}
