package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/** Pure bounded schedule. All instants are supplied by the session clock; no network or SQL. */
public final class LiveSchedule {
    public static final List<SofascoreEndpointType> J5 = List.of(EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS);
    public record Due(UUID eventId, SofascoreEndpointType endpoint, long cycle, String kind,
                      Instant dueAt, boolean finalCycle) { }
    public record EventState(UUID eventId, String state, String sportStatus, Instant nextDueAt,
                             long missedCycles, boolean finalComplete) { }
    private final LinkedHashMap<UUID, Event> events = new LinkedHashMap<>();
    private final Instant endsAt;
    private Due inFlight;
    private UUID contiguous;
    private String globalStop;

    public LiveSchedule(List<UUID> targets, Instant start, Instant endsAt) {
        if (targets.isEmpty() || targets.size() > 3 || new HashSet<>(targets).size() != targets.size()
                || !endsAt.isAfter(start)) throw new IllegalArgumentException("invalid live schedule");
        this.endsAt = endsAt;
        targets.forEach(id -> events.put(id, new Event(id, start)));
    }

    public synchronized Optional<Due> next(Instant now) {
        if (inFlight != null || globalStop != null) return Optional.empty();
        if (!now.isBefore(endsAt)) { stopAll("STOPPED_LIMIT"); return Optional.empty(); }
        if (contiguous != null) {
            Event e = events.get(contiguous);
            if (!e.active()) contiguous = null;
            else {
                if (!e.finalizing && !now.isBefore(e.cycleDue.plusSeconds(120))) {
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
            if (e.finalizing || e.j5At != null && (e.j4At == null || j5Due(e).dueAt().isBefore(e.j4At))) {
                candidate = j5Due(e);
                if (!e.finalizing && !now.isBefore(candidate.dueAt().plusSeconds(120))) {
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
        if (previous != null && eligible.isBefore(previous.plusSeconds(60))) eligible = previous.plusSeconds(60);
        return new Due(e.id, endpoint, e.serial, e.finalizing ? "J5_FINAL" : "J5_NORMAL", eligible, e.finalizing);
    }

    public synchronized boolean mayDispatch(Due due, Instant now) {
        Event e = events.get(due.eventId());
        return globalStop == null && e != null && e.active() && now.isBefore(endsAt)
                && !now.isBefore(due.dueAt());
    }

    public synchronized void started(Due due, Instant now) {
        if (inFlight != null || !mayDispatch(due, now)) throw new IllegalStateException("LIVE_DISPATCH_CANCELLED");
        Event e = events.get(due.eventId());
        Instant previous = e.lastStarts.get(due.endpoint());
        if (previous != null && now.isBefore(previous.plusSeconds(60))) throw new IllegalStateException("LIVE_FAMILY_CADENCE");
        e.lastStarts.put(due.endpoint(), now);
        if (due.endpoint() != EVENT_DETAILS) { contiguous = e.id; if (e.familyIndex == 0) e.cycleDue = e.j5At; }
        inFlight = due;
    }

    public synchronized void completed(Due due, String status, boolean unavailable,
                                       Map<String, Boolean> signals, Instant now) {
        if (!Objects.equals(inFlight, due)) throw new IllegalStateException("LIVE_UNEXPECTED_COMPLETION");
        inFlight = null;
        Event e = events.get(due.eventId());
        if (!e.active() || globalStop != null) return;
        if (due.endpoint() == EVENT_DETAILS) {
            if (unavailable) { stopEvent(e.id, "STOPPED_REVIEW_REQUIRED"); return; }
            if (!Set.of("notstarted", "inprogress", "finished").contains(status == null ? "" : status)
                    || "inprogress".equals(e.sport) && "notstarted".equals(status)) {
                stopEvent(e.id, "STOPPED_REVIEW_REQUIRED"); return;
            }
            e.sport = status;
            e.serial++;
            Instant started = e.lastStarts.get(EVENT_DETAILS);
            if ("finished".equals(status)) {
                e.finalizing = true; e.state = "FINALIZING"; e.j4At = null;
                e.j5At = now; e.familyIndex = 0; e.finalGood = true;
            } else if (e.reserveFinish) stopEvent(e.id, "STOPPED_LIMIT");
            else if ("notstarted".equals(status)) {
                e.state = "WAITING_START"; e.j4At = started.plusSeconds(60); e.j4Kind = "J4_WAIT";
            } else {
                e.state = e.checkingFinish ? "CHECKING_FINISH" : "COLLECTING";
                e.j4At = e.checkingFinish ? started.plusSeconds(60) : now.plusSeconds(300);
                e.j4Kind = e.checkingFinish ? "J4_FINISH" : "J4_FALLBACK";
                if (e.j5At == null) e.j5At = now;
            }
            return;
        }
        e.finalGood &= !unavailable;
        for (var signal : signals.entrySet()) if (!e.finalizing && e.seenSignals.add(signal.getKey())) {
            if (signal.getValue()) { e.checkingFinish = true; e.state = "CHECKING_FINISH"; }
            Instant next = e.lastStarts.get(EVENT_DETAILS).plusSeconds(60);
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
            if (!now.isBefore(e.cycleDue.plusSeconds(60))) {
                e.missedCycles++; e.consecutiveMisses++;
                if (e.consecutiveMisses >= 2) { stopAll("STOPPED_CAPACITY"); return; }
            } else e.consecutiveMisses = 0;
            Instant next = e.j5At.plusSeconds(60);
            while (!next.plusSeconds(60).isAfter(now)) { next = next.plusSeconds(60); e.missedCycles++; }
            e.j5At = next;
        }
    }

    public synchronized void reserveFinalCheck(UUID id, Instant now) {
        Event e = events.get(id);
        if (!e.active() || e.finalizing) return;
        if (e.reserveFinish) { stopEvent(id, "STOPPED_LIMIT"); return; }
        e.reserveFinish = true; e.j5At = null; e.familyIndex = 0; contiguous = null;
        Instant previous = e.lastStarts.get(EVENT_DETAILS);
        e.j4At = previous == null || !now.isBefore(previous.plusSeconds(60)) ? now : previous.plusSeconds(60);
        e.j4Kind = "J4_FINAL_CHECK";
    }
    public synchronized void failed(Due due, String scope, String reason) {
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
        Event e = events.get(id);
        if (e == null) throw new IllegalArgumentException("unknown selected event");
        if (e.active()) e.state = reason;
        if (id.equals(contiguous)) contiguous = null;
    }
    public synchronized void stopAll(String reason) {
        globalStop = reason; events.keySet().forEach(id -> stopEvent(id, reason));
    }
    public synchronized boolean terminal() { return globalStop != null || events.values().stream().noneMatch(Event::active); }
    public synchronized String globalStop() { return globalStop; }
    public synchronized List<EventState> states() {
        return events.values().stream().map(e -> new EventState(e.id, e.state, e.sport,
                !e.active() ? null : e.j4At == null ? j5Due(e).dueAt() : e.j5At == null ? e.j4At
                        : e.j4At.isBefore(j5Due(e).dueAt()) ? e.j4At : j5Due(e).dueAt(),
                e.missedCycles, e.finalComplete)).toList();
    }
    private static final class Event {
        final UUID id; final EnumMap<SofascoreEndpointType, Instant> lastStarts = new EnumMap<>(SofascoreEndpointType.class);
        final Set<String> seenSignals = new HashSet<>();
        String state = "WAITING_START", sport, j4Kind = "J4_INITIAL";
        Instant j4At, j5At, cycleDue; long serial, missedCycles; int familyIndex, consecutiveMisses;
        boolean checkingFinish, finalizing, reserveFinish, finalGood = true, finalComplete;
        Event(UUID id, Instant start) { this.id = id; j4At = start; }
        boolean active() { return !state.startsWith("STOPPED") && !state.equals("FINISHED_CONFIRMED"); }
    }
}
