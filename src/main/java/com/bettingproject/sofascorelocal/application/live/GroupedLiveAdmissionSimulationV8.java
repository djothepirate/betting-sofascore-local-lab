package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/**
 * Offline admission replay for normal V8 windows and persistent-limiter semantics.
 * It proves planned request starts only; delayed kickoffs, 404 spacing and isolated
 * timeout recovery are explicit exception paths covered by the V8 schedule tests.
 */
final class GroupedLiveAdmissionSimulationV8 {
    static final int SCENARIOS = 16;
    static final Duration POST_EXCHANGE_FENCE = GroupedLiveScheduleV8.POST_EXCHANGE_FENCE;
    static final int MAXIMUM_DEPARTURES_PER_MINUTE = 45;
    static final int MAXIMUM_DEPARTURES_PER_HOUR = 2756;
    private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final List<SofascoreEndpointType> ACTIVE_FAMILIES = List.of(
            EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private GroupedLiveAdmissionSimulationV8() { }
    static boolean fits(int matches, GroupedAdmissionProfile profile) {
        if (!hasStrictMinuteDepartureBudget(matches, profile)) return false;
        for (long kickoffOffset : new long[]{-1, 20, 3600, 7200})
            for (boolean confirmed : new boolean[]{false, true})
                for (boolean varied : new boolean[]{false, true})
                    if (!run(matches, profile, kickoffOffset, confirmed, varied)) return false;
        return true;
    }

    /**
     * The terminal fence is counted too: it closes the last exchange of one
     * minute before the first departure of the next wave may begin. Thus ten
     * four-family groups need 10 * sum(exchange + 500 ms) within one minute.
     */
    static boolean hasStrictMinuteDepartureBudget(int matches, GroupedAdmissionProfile profile) {
        if (matches < 1 || matches > LiveAdmissionPolicy.V8_MAXIMUM_SELECTION_SIZE) return false;
        Objects.requireNonNull(profile);
        try {
            return Math.multiplyExact(GroupedLiveScheduleV8.strictGroupReservation(profile).toNanos(), matches)
                    <= MINUTE.toNanos();
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    private static boolean run(int matches, GroupedAdmissionProfile profile, long offset, boolean confirmed, boolean varied) {
        List<UUID> targets = new ArrayList<>();
        for (int i = 0; i < matches; i++) targets.add(new UUID(8, i + 1));
        Instant kickoff = START.plusSeconds(offset), finish = kickoff.plusSeconds(960);
        LiveSchedule schedule = new LiveSchedule(targets, START, START.plusSeconds(14400),
                profile.criticalInterval(), "live-v8", null, profile);
        Instant now = START, lastCompletion = null;
        Deque<Instant> departures = new ArrayDeque<>();
        Map<Key, Instant> previousDepartures = new HashMap<>();
        Set<Key> familiesWithStrictMinuteIntervals = new HashSet<>();
        int calls = 0;
        while (!schedule.terminal() && calls < 4000) {
            var ready = schedule.next(now);
            if (ready.isEmpty()) {
                if (schedule.globalStop() != null) return false;
                Instant next = nextTransportWake(schedule, now);
                if (next == null) return false;
                now = next; continue;
            }
            var due = ready.orElseThrow();
            Instant allowed = nextAllowed(now, lastCompletion, departures);
            if (allowed.isAfter(now)) { schedule.defer(due, allowed); now = allowed; continue; }
            boolean playing = !now.isBefore(kickoff) && now.isBefore(finish);
            String status = due.endpoint() != EVENT_DETAILS ? null : now.isBefore(kickoff) ? "notstarted"
                    : now.isBefore(finish) ? "inprogress" : "finished";
            Instant departedAt = now;
            schedule.started(due, departedAt);
            Duration cost = profile.envelope(due.endpoint()).exchangeEnvelope();
            // The planned slot uses the qualified maximum envelope. This normal
            // replay alternates lower observed costs to prove that later within-
            // envelope exchanges still retain each family’s planned start phase.
            if (varied && Math.floorDiv(Duration.between(kickoff, now).toSeconds(), 60) % 2 == 1)
                cost = Duration.ofNanos(1);
            now = now.plus(cost);
            schedule.completed(due, status, false, Map.of(), now, kickoff,
                    due.endpoint() == EVENT_LINEUPS ? confirmed : null);
            departures.addLast(departedAt); lastCompletion = now; calls++;
            // Simultaneous T-60/T0 groups may consume one bounded serial wave.
            // No admitted wave may overrun the next minute or create a missed task.
            if (Duration.between(due.dueAt(), now).compareTo(Duration.ofSeconds(60)) > 0) return false;
            if (playing && !due.finalCycle()) {
                Key key = new Key(due.eventId(), due.endpoint());
                Instant before = previousDepartures.put(key, departedAt);
                if (before != null) {
                    if (Duration.between(before, departedAt).compareTo(MINUTE) > 0) return false;
                    familiesWithStrictMinuteIntervals.add(key);
                }
            }
            if (schedule.states().stream().anyMatch(state -> state.missedCycles() != 0 || state.state().startsWith("STOPPED"))) return false;
        }
        if (!schedule.terminal() || schedule.globalStop() != null || schedule.states().stream().anyMatch(s -> !s.finalComplete())) return false;
        return familiesWithStrictMinuteIntervals.size() == matches * ACTIVE_FAMILIES.size();
    }

    /**
     * A group remains serial while its next qualified family slot is still in
     * the future. Its EventState can therefore coexist with another event that
     * is already overdue. The real session polls until the contiguous slot is
     * eligible; the offline replay advances to the earliest *future* family
     * slot instead of mistaking that overdue peer for a cancelled dispatch.
     */
    private static Instant nextTransportWake(LiveSchedule schedule, Instant now) {
        return schedule.states().stream()
                .flatMap(state -> schedule.familySchedules(state.eventId()).stream())
                .map(com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.FamilySchedule::nextDueAt)
                .filter(next -> next != null && next.isAfter(now))
                .min(Instant::compareTo)
                .orElse(null);
    }

    private static Instant nextAllowed(Instant now, Instant lastCompletion, Deque<Instant> departures) {
        Instant candidate = lastCompletion != null && lastCompletion.plus(POST_EXCHANGE_FENCE).isAfter(now)
                ? lastCompletion.plus(POST_EXCHANGE_FENCE) : now;
        while (true) {
            Instant at = candidate;
            while (!departures.isEmpty() && !departures.getFirst().plusSeconds(3600).isAfter(at)) departures.removeFirst();
            List<Instant> hour = List.copyOf(departures);
            List<Instant> minute = hour.stream().filter(end -> end.plusSeconds(60).isAfter(at)).toList();
            if (hour.size() >= MAXIMUM_DEPARTURES_PER_HOUR) { candidate = hour.get(hour.size() - MAXIMUM_DEPARTURES_PER_HOUR).plusSeconds(3600); continue; }
            if (minute.size() >= MAXIMUM_DEPARTURES_PER_MINUTE) { candidate = minute.get(minute.size() - MAXIMUM_DEPARTURES_PER_MINUTE).plusSeconds(60); continue; }
            return candidate;
        }
    }
    private record Key(UUID event, SofascoreEndpointType endpoint) { }
}
