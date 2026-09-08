package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/** Bounded fake-clock replay of the production grouped scheduler, never a provider call. */
final class GroupedLiveAdmissionSimulation {
    private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");
    private static final Duration FIFTEEN_SECONDS = Duration.ofSeconds(15);

    private GroupedLiveAdmissionSimulation() { }

    static boolean fits(int matches, GroupedAdmissionProfile profile) {
        // Forty V4 or twenty-four V5 bounded scenarios: lineup phases, direct in-play or prematch
        // kickoff, constant or variable exchange durations, then a common finished
        // round or two finished cohorts. Faster exchanges can split a final lineup
        // from its critical group and expose another policy-specific inter-group fence.
        int lineupRounds = (int) (profile.lineupInterval().toSeconds() / profile.criticalInterval().toSeconds());
        for (int phase = 0; phase < lineupRounds; phase++) {
            for (boolean prematch : new boolean[] {false, true}) {
                for (boolean variableExchanges : new boolean[] {false, true}) {
                    for (boolean simultaneousFinalization : new boolean[] {false, true}) {
                        if (!run(matches, profile, phase, prematch, variableExchanges, simultaneousFinalization))
                            return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean run(int matches, GroupedAdmissionProfile profile, int finalPhase,
                               boolean prematch, boolean variableExchanges, boolean simultaneousFinalization) {
        List<UUID> targets = new ArrayList<>();
        Map<UUID, Integer> positions = new HashMap<>();
        for (int i = 0; i < matches; i++) {
            UUID id = UUID.nameUUIDFromBytes(("v4-admission-" + i).getBytes(StandardCharsets.UTF_8));
            targets.add(id);
            positions.put(id, i);
        }
        LiveSchedule schedule = new LiveSchedule(targets, START, START.plusSeconds(1200),
                profile.criticalInterval(), profile.policyVersion());
        long periodSeconds = profile.criticalInterval().toSeconds();
        long lineupRounds = profile.lineupInterval().toSeconds() / periodSeconds;
        Duration maximumInterval = profile.criticalInterval().plusSeconds(15);
        Duration p95Interval = profile.criticalInterval().plusSeconds(5);
        Map<UUID, Instant> finishedAt = new HashMap<>();
        Map<Key, Instant> previousCritical = new HashMap<>();
        Map<Key, List<Duration>> criticalIntervals = new HashMap<>();
        Instant now = START, lastCompletion = null;
        UUID previousGroup = null;
        int calls = 0;
        while (!schedule.terminal() && calls < 10000) {
            Optional<LiveSchedule.Due> next = schedule.next(now);
            if (next.isEmpty()) {
                if (schedule.globalStop() != null) return false;
                Instant future = schedule.states().stream().map(LiveSchedule.EventState::nextDueAt)
                        .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
                if (future == null || !future.isAfter(now)) return false;
                now = future;
                continue;
            }
            LiveSchedule.Due due = next.orElseThrow();
            if (!due.groupId().equals(previousGroup) && lastCompletion != null
                    && now.isBefore(lastCompletion.plus(profile.interGroupDelay()))) {
                now = lastCompletion.plus(profile.interGroupDelay());
                continue;
            }
            int index = positions.get(due.eventId());
            long nominalRound = Math.floorDiv(Duration.between(START, due.dueAt()).toSeconds(), periodSeconds);
            long finalRound = lineupRounds + finalPhase
                    + (simultaneousFinalization || index < Math.max(1, matches / 2) ? 0 : 3);
            String status = due.endpoint() == EVENT_DETAILS
                    ? nominalRound >= finalRound ? "finished"
                    : prematch && nominalRound < 2 ? "notstarted" : "inprogress" : null;
            schedule.started(due, now);
            Duration elapsed = profile.envelope(due.endpoint()).exchangeEnvelope();
            if (variableExchanges && due.endpoint() != EVENT_LINEUPS && nominalRound % 2 == 1)
                elapsed = Duration.ofNanos(1);
            now = now.plus(elapsed);
            schedule.completed(due, status, false, Map.of(), now);
            if ("finished".equals(status)) finishedAt.put(due.eventId(), now);
            if (!due.finalCycle()) {
                if (Duration.between(due.dueAt(), now).compareTo(FIFTEEN_SECONDS) > 0) return false;
                if (due.endpoint() != EVENT_LINEUPS) {
                    Key key = new Key(due.eventId(), due.endpoint());
                    Instant previous = previousCritical.put(key, now);
                    if (previous != null) {
                        Duration interval = Duration.between(previous, now);
                        if (interval.compareTo(maximumInterval) > 0) return false;
                        criticalIntervals.computeIfAbsent(key, ignored -> new ArrayList<>()).add(interval);
                    }
                }
            }
            for (LiveSchedule.EventState state : schedule.states()) {
                if (state.missedCycles() != 0 || state.state().startsWith("STOPPED")) return false;
                Instant finished = finishedAt.get(state.eventId());
                if (finished != null && now.isAfter(finished.plusSeconds(120)) && !state.finalComplete()) return false;
            }
            if (due.finalCycle() && Duration.between(finishedAt.get(due.eventId()), now).compareTo(Duration.ofSeconds(120)) > 0)
                return false;
            previousGroup = due.groupId();
            lastCompletion = now;
            calls++;
        }
        if (!schedule.terminal() || schedule.globalStop() != null
                || schedule.states().stream().anyMatch(state -> !state.finalComplete())) return false;
        for (List<Duration> intervals : criticalIntervals.values()) {
            intervals.sort(Comparator.naturalOrder());
            int percentile = Math.max(0, (int) Math.ceil(intervals.size() * .95) - 1);
            if (intervals.get(percentile).compareTo(p95Interval) > 0) return false;
        }
        return true;
    }

    private record Key(UUID eventId, SofascoreEndpointType endpoint) { }
}
