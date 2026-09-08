package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.live.LiveCadence;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.FamilySchedule;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/** Pure bounded schedule. All instants are supplied by the session clock; no network or SQL. */
public final class LiveSchedule {
    public static final List<SofascoreEndpointType> J5 = List.of(EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS);
    public record Due(UUID eventId, SofascoreEndpointType endpoint, long cycle, String kind,
                      Instant dueAt, boolean finalCycle, UUID groupId, long groupSequence, int groupOrdinal) {
        public Due(UUID eventId, SofascoreEndpointType endpoint, long cycle, String kind,
                   Instant dueAt, boolean finalCycle) {
            this(eventId, endpoint, cycle, kind, dueAt, finalCycle, null, -1, -1);
        }
    }
    public record EventState(UUID eventId, String state, String sportStatus, Instant nextDueAt,
                             long missedCycles, boolean finalComplete) { }
    private final LinkedHashMap<UUID, Event> events = new LinkedHashMap<>();
    private final Instant endsAt;
    private final Duration interval;
    private final Duration fallbackInterval;
    private final boolean prematchLineups;
    private Due inFlight;
    private UUID contiguous;
    private String globalStop;
    private final GroupedLiveScheduleV4 grouped;

    public LiveSchedule(List<UUID> targets, Instant start, Instant endsAt) {
        this(targets, start, endsAt, Duration.ofSeconds(60));
    }

    public LiveSchedule(List<UUID> targets, Instant start, Instant endsAt, Duration interval) {
        this(targets, start, endsAt, interval, "live-v2");
    }

    public LiveSchedule(List<UUID> targets, Instant start, Instant endsAt, Duration interval,
                        String policyVersion) {
        this(targets, start, endsAt, interval, policyVersion, null);
    }

    public LiveSchedule(List<UUID> targets, Instant start, Instant endsAt, Duration interval,
                        String policyVersion, UUID campaignId) {
        if (targets.isEmpty() || targets.size() > LiveCadence.MAXIMUM_SELECTION_SIZE || new HashSet<>(targets).size() != targets.size()
                || !endsAt.isAfter(start)) throw new IllegalArgumentException("invalid live schedule");
        LiveCadence.validate(interval);
        this.endsAt = endsAt;
        this.interval = interval;
        this.prematchLineups = "live-v3".equals(policyVersion);
        this.fallbackInterval = interval.compareTo(Duration.ofMinutes(5)) > 0 ? interval : Duration.ofMinutes(5);
        targets.forEach(id -> events.put(id, new Event(id, start)));
        this.grouped = "live-v4".equals(policyVersion) || "live-v5".equals(policyVersion)
                ? new GroupedLiveScheduleV4(targets, start, endsAt, interval, campaignId, policyVersion) : null;
    }

    public synchronized Optional<Due> next(Instant now) {
        if (grouped != null) return grouped.next(now);
        if (inFlight != null || globalStop != null) return Optional.empty();
        if (!now.isBefore(endsAt)) { stopAll("STOPPED_LIMIT"); return Optional.empty(); }
        if (contiguous != null) {
            Event e = events.get(contiguous);
            if (!e.active()) contiguous = null;
            else {
                if (!e.finalizing && !now.isBefore(e.cycleDue.plus(interval.multipliedBy(2)))) {
                    e.missedCycles += 2; stopAll("STOPPED_CAPACITY"); return Optional.empty();
                }
                Due due = j5Due(e);
                return due.dueAt().isAfter(now) ? Optional.empty() : Optional.of(due);
            }
        }
        Due selected = null;
        for (Event e : events.values()) {
            if (!e.active()) continue;
            Due candidate;
            if (e.prematchLineupsAt != null && e.prematchLineupsAt.isBefore(e.j4At)) {
                candidate = prematchDue(e);
                if (!now.isBefore(candidate.dueAt().plus(interval.multipliedBy(2)))) {
                    e.missedCycles += 2; stopAll("STOPPED_CAPACITY"); return Optional.empty();
                }
            } else if (e.finalizing || e.j5At != null && (e.j4At == null || j5Due(e).dueAt().isBefore(e.j4At))) {
                candidate = j5Due(e);
                if (!e.finalizing && !now.isBefore(candidate.dueAt().plus(interval.multipliedBy(2)))) {
                    e.missedCycles += 2; stopAll("STOPPED_CAPACITY"); return Optional.empty();
                }
            } else candidate = new Due(e.id, EVENT_DETAILS, e.serial, e.j4Kind, e.j4At, e.reserveFinish);
            if (selected == null || candidate.dueAt().isBefore(selected.dueAt())) selected = candidate;
        }
        return selected != null && !selected.dueAt().isAfter(now) ? Optional.of(selected) : Optional.empty();
    }

    private Due j5Due(Event e) {
        SofascoreEndpointType endpoint = J5.get(e.familyIndex);
        Instant eligible = e.j5At;
        Instant previous = e.lastStarts.get(endpoint);
        if (previous != null && eligible.isBefore(previous.plus(interval))) eligible = previous.plus(interval);
        return new Due(e.id, endpoint, e.serial, e.finalizing ? "J5_FINAL" : "J5_NORMAL", eligible, e.finalizing);
    }

    private Due prematchDue(Event e) {
        return new Due(e.id, EVENT_LINEUPS, e.serial, "J5_PREMATCH_LINEUPS", e.prematchLineupsAt, false);
    }

    private Instant firstTripletAt(Event e, Instant now) {
        // A recent prematch lineup still owns its D interval. Align the triplet before
        // it begins so a later lineup cannot hold the shared contiguous worker idle.
        if (!prematchLineups) return now;
        Instant eligible = now;
        for (SofascoreEndpointType family : J5) {
            Instant previous = e.lastStarts.get(family);
            if (previous != null && previous.plus(interval).isAfter(eligible)) eligible = previous.plus(interval);
        }
        return eligible;
    }

    public synchronized boolean mayDispatch(Due due, Instant now) {
        if (grouped != null) return grouped.mayDispatch(due, now);
        Event e = events.get(due.eventId());
        return globalStop == null && e != null && e.active() && now.isBefore(endsAt)
                && !now.isBefore(due.dueAt());
    }

    public synchronized void started(Due due, Instant now) {
        if (grouped != null) { grouped.started(due, now); return; }
        if (inFlight != null || !mayDispatch(due, now)) throw new IllegalStateException("LIVE_DISPATCH_CANCELLED");
        Event e = events.get(due.eventId());
        Instant previous = e.lastStarts.get(due.endpoint());
        if (previous != null && now.isBefore(previous.plus(interval))) throw new IllegalStateException("LIVE_FAMILY_CADENCE");
        e.lastStarts.put(due.endpoint(), now);
        if (due.endpoint() != EVENT_DETAILS && !"J5_PREMATCH_LINEUPS".equals(due.kind())) {
            contiguous = e.id; if (e.familyIndex == 0) e.cycleDue = e.j5At;
        }
        inFlight = due;
    }

    public synchronized void completed(Due due, String status, boolean unavailable,
                                       Map<String, Boolean> signals, Instant now) {
        if (grouped != null) { grouped.completed(due, status, unavailable, signals, now); return; }
        if (!Objects.equals(inFlight, due)) throw new IllegalStateException("LIVE_UNEXPECTED_COMPLETION");
        inFlight = null;
        Event e = events.get(due.eventId());
        if (!e.active() || globalStop != null) return;
        if (due.endpoint() == EVENT_DETAILS) {
            if (unavailable) { stopEvent(e.id, "STOPPED_REVIEW_REQUIRED"); return; }
            if ("postponed".equals(status)) {
                e.sport = status;
                e.j4At = null; e.j5At = null; e.prematchLineupsAt = null;
                e.finalComplete = false;
                stopEvent(e.id, "STOPPED_POSTPONED");
                return;
            }
            if (!Set.of("notstarted", "inprogress", "finished").contains(status == null ? "" : status)
                    || "inprogress".equals(e.sport) && "notstarted".equals(status)) {
                stopEvent(e.id, "STOPPED_REVIEW_REQUIRED"); return;
            }
            e.sport = status;
            e.serial++;
            Instant started = e.lastStarts.get(EVENT_DETAILS);
            if ("finished".equals(status)) {
                e.prematchLineupsAt = null;
                e.finalizing = true; e.state = "FINALIZING"; e.j4At = null;
                e.j5At = firstTripletAt(e, now); e.familyIndex = 0; e.finalGood = true;
            } else if (e.reserveFinish) stopEvent(e.id, "STOPPED_LIMIT");
            else if ("notstarted".equals(status)) {
                e.state = "WAITING_START"; e.j4At = started.plus(interval); e.j4Kind = "J4_WAIT";
                if (prematchLineups && e.prematchLineupsAt == null) e.prematchLineupsAt = now;
            } else {
                e.prematchLineupsAt = null;
                e.state = e.checkingFinish ? "CHECKING_FINISH" : "COLLECTING";
                e.j4At = e.checkingFinish ? started.plus(interval) : now.plus(fallbackInterval);
                e.j4Kind = e.checkingFinish ? "J4_FINISH" : "J4_FALLBACK";
                if (e.j5At == null) e.j5At = firstTripletAt(e, now);
            }
            return;
        }
        if ("J5_PREMATCH_LINEUPS".equals(due.kind())) {
            // A 404 is an observed unavailability; the next ordinary poll remains at D.
            // This separate phase must not advance the statistics/incidents/lineups triplet.
            if (!now.isBefore(due.dueAt().plus(interval))) {
                e.missedCycles++; e.consecutiveMisses++;
                if (e.consecutiveMisses >= 2) { stopAll("STOPPED_CAPACITY"); return; }
            } else e.consecutiveMisses = 0;
            e.prematchLineupsAt = e.lastStarts.get(EVENT_LINEUPS).plus(interval);
            e.serial++;
            return;
        }
        e.finalGood &= !unavailable;
        for (var signal : signals.entrySet()) if (!e.finalizing && e.seenSignals.add(signal.getKey())) {
            if (signal.getValue()) { e.checkingFinish = true; e.state = "CHECKING_FINISH"; }
            Instant next = e.lastStarts.get(EVENT_DETAILS).plus(interval);
            if (next.isBefore(now)) next = now;
            if (e.j4At == null || next.isBefore(e.j4At)) e.j4At = next;
            e.j4Kind = e.checkingFinish ? "J4_FINISH" : "J4_SIGNAL";
        }
        e.familyIndex++;
        if (e.familyIndex == 3) {
            contiguous = null; e.familyIndex = 0; e.serial++;
            if (e.finalizing) {
                e.finalComplete = e.finalGood; e.state = "FINISHED_CONFIRMED"; e.j5At = null; return;
            }
            if (!now.isBefore(e.cycleDue.plus(interval))) {
                e.missedCycles++; e.consecutiveMisses++;
                if (e.consecutiveMisses >= 2) { stopAll("STOPPED_CAPACITY"); return; }
            } else e.consecutiveMisses = 0;
            Instant next = e.j5At.plus(interval);
            while (!next.plus(interval).isAfter(now)) { next = next.plus(interval); e.missedCycles++; }
            e.j5At = next;
        }
    }

    public synchronized void reserveFinalCheck(UUID id, Instant now) {
        if (grouped != null) { grouped.reserveFinalCheck(id, now); return; }
        Event e = events.get(id);
        if (!e.active() || e.finalizing) return;
        if (e.reserveFinish) { stopEvent(id, "STOPPED_LIMIT"); return; }
        e.reserveFinish = true; e.j5At = null; e.prematchLineupsAt = null; e.familyIndex = 0; contiguous = null;
        Instant previous = e.lastStarts.get(EVENT_DETAILS);
        e.j4At = previous == null || !now.isBefore(previous.plus(interval)) ? now : previous.plus(interval);
        e.j4Kind = "J4_FINAL_CHECK";
    }
    public synchronized void failed(Due due, String scope, String reason) {
        if (grouped != null) { grouped.failed(due, scope, reason); return; }
        inFlight = null;
        // A parsed response is not a durable result until publication commits.
        // In particular, a failed final publication must revoke its provisional completeness.
        Event event = events.get(due.eventId());
        event.finalComplete = false;
        if ("CAMPAIGN".equals(scope) && !"STOPPED_OPERATOR".equals(event.state)
                || "FINISHED_CONFIRMED".equals(event.state)) event.state = reason;
        if ("EVENT".equals(scope)) stopEvent(due.eventId(), reason); else stopAll(reason);
    }
    public synchronized void stopEvent(UUID id, String reason) {
        if (grouped != null) { grouped.stopEvent(id, reason); return; }
        Event e = events.get(id);
        if (e == null) throw new IllegalArgumentException("unknown selected event");
        if (e.active()) e.state = reason;
        if (id.equals(contiguous)) contiguous = null;
    }
    public synchronized void stopAll(String reason) {
        if (grouped != null) { grouped.stopAll(reason); return; }
        globalStop = reason; events.keySet().forEach(id -> stopEvent(id, reason));
    }
    public synchronized boolean terminal() { return grouped != null ? grouped.terminal() : globalStop != null || events.values().stream().noneMatch(Event::active); }
    public synchronized String globalStop() { return grouped != null ? grouped.globalStop() : globalStop; }
    public synchronized List<EventState> states() {
        if (grouped != null) return grouped.states();
        return events.values().stream().map(e -> new EventState(e.id, e.state, e.sport,
                !e.active() ? null : nextDueAt(e),
                e.missedCycles, e.finalComplete)).toList();
    }

    /** V4 family deadlines are durable metadata; legacy campaigns keep their historical event deadline. */
    public synchronized List<FamilySchedule> familySchedules(UUID eventId) {
        return grouped == null ? List.of() : grouped.familySchedules(eventId);
    }

    private Instant nextDueAt(Event e) {
        Instant next = e.j4At;
        if (e.j5At != null && (next == null || j5Due(e).dueAt().isBefore(next))) next = j5Due(e).dueAt();
        if (e.prematchLineupsAt != null && (next == null || e.prematchLineupsAt.isBefore(next))) next = e.prematchLineupsAt;
        return next;
    }
    private static final class Event {
        final UUID id; final EnumMap<SofascoreEndpointType, Instant> lastStarts = new EnumMap<>(SofascoreEndpointType.class);
        final Set<String> seenSignals = new HashSet<>();
        String state = "WAITING_START", sport, j4Kind = "J4_INITIAL";
        Instant j4At, j5At, cycleDue, prematchLineupsAt; long serial, missedCycles; int familyIndex, consecutiveMisses;
        boolean checkingFinish, finalizing, reserveFinish, finalGood = true, finalComplete;
        Event(UUID id, Instant start) { this.id = id; j4At = start; }
        boolean active() { return !state.startsWith("STOPPED") && !state.equals("FINISHED_CONFIRMED"); }
    }
}
