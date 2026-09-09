package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/** Admission replay of real V7 windows and persistent-limiter semantics; no network. */
final class GroupedLiveAdmissionSimulationV7 {
    static final int SCENARIOS = 16;
    private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");
    private GroupedLiveAdmissionSimulationV7() { }
    static boolean fits(int matches, GroupedAdmissionProfile profile) {
        for (long kickoffOffset : new long[]{-1, 20, 3600, 7200})
            for (boolean confirmed : new boolean[]{false, true})
                for (boolean varied : new boolean[]{false, true})
                    if (!run(matches, profile, kickoffOffset, confirmed, varied)) return false;
        return true;
    }
    private static boolean run(int matches, GroupedAdmissionProfile profile, long offset, boolean confirmed, boolean varied) {
        List<UUID> targets = new ArrayList<>();
        for (int i = 0; i < matches; i++) targets.add(new UUID(7, i + 1));
        Instant kickoff = START.plusSeconds(offset), finish = kickoff.plusSeconds(960);
        LiveSchedule schedule = new LiveSchedule(targets, START, START.plusSeconds(14400),
                profile.criticalInterval(), "live-v7");
        Instant now = START, last = null;
        Deque<Instant> completions = new ArrayDeque<>();
        Map<Key, Instant> previous = new HashMap<>();
        Map<Key, List<Long>> intervals = new HashMap<>();
        int calls = 0;
        while (!schedule.terminal() && calls < 4000) {
            var ready = schedule.next(now);
            if (ready.isEmpty()) {
                if (schedule.globalStop() != null) return false;
                Instant next = schedule.states().stream().map(LiveSchedule.EventState::nextDueAt)
                        .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
                if (next == null || !next.isAfter(now)) return false;
                now = next; continue;
            }
            var due = ready.orElseThrow();
            Instant allowed = nextAllowed(now, last, completions);
            if (allowed.isAfter(now)) { schedule.defer(due, allowed); now = allowed; continue; }
            boolean playing = !now.isBefore(kickoff) && now.isBefore(finish);
            String status = due.endpoint() != EVENT_DETAILS ? null : now.isBefore(kickoff) ? "notstarted"
                    : now.isBefore(finish) ? "inprogress" : "finished";
            schedule.started(due, now);
            Duration cost = profile.envelope(due.endpoint()).exchangeEnvelope();
            if (varied && Math.floorDiv(Duration.between(kickoff, now).toSeconds(), 60) % 2 == 1)
                cost = Duration.ofNanos(1);
            now = now.plus(cost);
            schedule.completed(due, status, false, Map.of(), now, kickoff,
                    due.endpoint() == EVENT_LINEUPS ? confirmed : null);
            completions.addLast(now); last = now; calls++;
            // Simultaneous T-60/T0 groups may consume one bounded serial wave.
            // No admitted wave may overrun the next minute or create a missed task.
            if (Duration.between(due.dueAt(), now).compareTo(Duration.ofSeconds(60)) > 0) return false;
            if (playing && !due.finalCycle() && now.isAfter(kickoff.plusSeconds(120))) {
                Key key = new Key(due.eventId(), due.endpoint());
                Instant before = previous.put(key, now);
                if (before != null) {
                    long interval = Duration.between(before, now).toNanos();
                    if (interval > Duration.ofSeconds(75).toNanos()) return false;
                    intervals.computeIfAbsent(key, ignored -> new ArrayList<>()).add(interval);
                }
            }
            if (schedule.states().stream().anyMatch(state -> state.missedCycles() != 0 || state.state().startsWith("STOPPED"))) return false;
        }
        if (!schedule.terminal() || schedule.globalStop() != null || schedule.states().stream().anyMatch(s -> !s.finalComplete())) return false;
        for (var values : intervals.values()) {
            Collections.sort(values);
            if (values.get(Math.max(0, (int) Math.ceil(values.size() * .95) - 1)) > Duration.ofSeconds(65).toNanos()) return false;
        }
        return true;
    }
    private static Instant nextAllowed(Instant now, Instant last, Deque<Instant> completions) {
        Instant candidate = last != null && last.plusSeconds(2).isAfter(now) ? last.plusSeconds(2) : now;
        while (true) {
            Instant at = candidate;
            while (!completions.isEmpty() && !completions.getFirst().plusSeconds(3600).isAfter(at)) completions.removeFirst();
            List<Instant> hour = List.copyOf(completions);
            List<Instant> minute = hour.stream().filter(end -> end.plusSeconds(60).isAfter(at)).toList();
            if (hour.size() >= 1000) { candidate = hour.get(hour.size() - 1000).plusSeconds(3600); continue; }
            if (minute.size() >= 25) { candidate = minute.get(minute.size() - 25).plusSeconds(60); continue; }
            return candidate;
        }
    }
    private record Key(UUID event, SofascoreEndpointType endpoint) { }
}
