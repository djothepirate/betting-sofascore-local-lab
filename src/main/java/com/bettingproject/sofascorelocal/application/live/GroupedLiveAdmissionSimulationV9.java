package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/**
 * Offline admission replay for V9 requested-departure windows and persistent-limiter
 * semantics. It includes bounded alternating worker J4 emission jitter; delayed
 * kickoffs, 404 spacing and isolated timeout recovery remain explicit exception paths
 * covered by the V9 schedule tests.
 */
final class GroupedLiveAdmissionSimulationV9 {
    static final int SCENARIOS = 16;
    static final Duration POST_EXCHANGE_FENCE = GroupedLiveScheduleV9.POST_EXCHANGE_FENCE;
    static final int MAXIMUM_DEPARTURES_PER_MINUTE = 45;
    static final int MAXIMUM_DEPARTURES_PER_HOUR = 2756;
    private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final List<SofascoreEndpointType> ACTIVE_FAMILIES = List.of(
            EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private GroupedLiveAdmissionSimulationV9() { }
    static boolean fits(int matches, GroupedAdmissionProfile profile) {
        if (!hasStrictMinuteDepartureBudget(matches, profile)) return false;
        for (long kickoffOffset : new long[]{-1, 20, 3600, 7200})
            for (boolean confirmed : new boolean[]{false, true})
                for (boolean varied : new boolean[]{false, true})
                    if (!run(matches, profile, kickoffOffset, confirmed, varied)) return false;
        return true;
    }

    /**
     * The terminal fence closes the last exchange of one group. A second,
     * independent one-second reservation absorbs the bounded worker-start gate
     * before the next group can emit REQUEST_SENT. Thus the strict proof uses
     * ten phase reservations, each sum(exchange + fence) plus the static slot
     * reserve, within one minute.
     */
    static boolean hasStrictMinuteDepartureBudget(int matches, GroupedAdmissionProfile profile) {
        if (matches < 1 || matches > LiveAdmissionPolicy.V9_MAXIMUM_SELECTION_SIZE) return false;
        Objects.requireNonNull(profile);
        try {
            return Math.multiplyExact(GroupedLiveScheduleV9.strictGroupReservation(profile).toNanos(), matches)
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
                profile.criticalInterval(), "live-v9", null, profile);
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
            Admission admission = nextAllowed(now, lastCompletion, departures);
            if (admission.notBefore().isAfter(now)) {
                if (admission.reason() == AdmissionReason.POST_EXCHANGE_FENCE)
                    schedule.waitForPostExchangeFence(due, admission.notBefore());
                else schedule.defer(due, admission.notBefore());
                now = admission.notBefore(); continue;
            }
            Instant admittedAt = now;
            schedule.started(due, admittedAt);
            Instant departedAt = admittedAt.plus(authenticatedWorkerJ4Delay(due, kickoff, varied));
            schedule.departed(due, departedAt);
            boolean playing = !departedAt.isBefore(kickoff) && departedAt.isBefore(finish);
            String status = due.endpoint() != EVENT_DETAILS ? null : departedAt.isBefore(kickoff) ? "notstarted"
                    : departedAt.isBefore(finish) ? "inprogress" : "finished";
            Duration cost = profile.envelope(due.endpoint()).exchangeEnvelope();
            // The planned slot uses the qualified maximum envelope. This normal
            // replay alternates lower observed costs to prove that later within-
            // envelope exchanges still retain each family’s planned start phase.
            if (varied && Math.floorDiv(Duration.between(kickoff, now).toSeconds(), 60) % 2 == 1)
                cost = Duration.ofNanos(1);
            now = departedAt.plus(cost);
            schedule.completed(due, status, false, Map.of(), now, kickoff,
                    due.endpoint() == EVENT_LINEUPS ? confirmed : null,
                    due.endpoint() == EVENT_DETAILS ? normalControls(status) : null);
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

    /** Alternate 0/500 ms J4 emissions per event/wave without exceeding the declared head start. */
    private static Duration authenticatedWorkerJ4Delay(LiveSchedule.Due due, Instant kickoff, boolean varied) {
        if (!varied || due.endpoint() != EVENT_DETAILS || !"J4_CYCLE".equals(due.kind())) return Duration.ZERO;
        long wave = Math.floorDiv(Duration.between(kickoff, due.dueAt()).toSeconds(), 60);
        long parity = wave + due.eventId().getLeastSignificantBits();
        return Math.floorMod(parity, 2) == 0 ? Duration.ZERO : GroupedLiveScheduleV9.PLAY_REQUEST_EMISSION_HEAD_START;
    }

    /** Full J5 is the capacity upper-bound: optional V9 suppression cannot enlarge it. */
    private static LiveJ4ControlFacts normalControls(String status) {
        return new LiveJ4ControlFacts(LiveJ4ControlFacts.BooleanFact.FALSE,
                LiveJ4ControlFacts.DetailIdFact.ONE, LiveJ4ControlFacts.BooleanFact.TRUE,
                LiveJ4ControlFacts.BooleanFact.TRUE,
                LiveJ4ControlFacts.StatusDescription.OTHER,
                LiveJ4ControlFacts.TextFact.absent());
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

    private static Admission nextAllowed(Instant now, Instant lastCompletion, Deque<Instant> departures) {
        Instant candidate = lastCompletion != null && lastCompletion.plus(POST_EXCHANGE_FENCE).isAfter(now)
                ? lastCompletion.plus(POST_EXCHANGE_FENCE) : now;
        boolean rateLimited = false;
        while (true) {
            Instant at = candidate;
            while (!departures.isEmpty() && !departures.getFirst().plusSeconds(3600).isAfter(at)) departures.removeFirst();
            List<Instant> hour = List.copyOf(departures);
            List<Instant> minute = hour.stream().filter(end -> end.plusSeconds(60).isAfter(at)).toList();
            if (hour.size() >= MAXIMUM_DEPARTURES_PER_HOUR) { rateLimited = true; candidate = hour.get(hour.size() - MAXIMUM_DEPARTURES_PER_HOUR).plusSeconds(3600); continue; }
            if (minute.size() >= MAXIMUM_DEPARTURES_PER_MINUTE) { rateLimited = true; candidate = minute.get(minute.size() - MAXIMUM_DEPARTURES_PER_MINUTE).plusSeconds(60); continue; }
            return new Admission(candidate, rateLimited ? AdmissionReason.RATE_LIMITED
                    : candidate.isAfter(now) ? AdmissionReason.POST_EXCHANGE_FENCE : AdmissionReason.ALLOWED);
        }
    }
    private enum AdmissionReason { ALLOWED, POST_EXCHANGE_FENCE, RATE_LIMITED }
    private record Admission(Instant notBefore, AdmissionReason reason) { }
    private record Key(UUID event, SofascoreEndpointType endpoint) { }
}
