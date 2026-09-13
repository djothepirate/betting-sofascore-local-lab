package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.FamilySchedule;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/** V7 kickoff windows, independent of the immutable V4–V6 calendar. No network or clock reads. */
final class GroupedLiveScheduleV7 {
    private static final Duration MINUTE = Duration.ofSeconds(60);
    private static final List<SofascoreEndpointType> J5 = List.of(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private enum Mode { INITIAL, WARMUP, LINEUPS, KICKOFF, PLAY, FINAL, RECHECK }
    private final LinkedHashMap<UUID, Event> events = new LinkedHashMap<>();
    private final Instant endsAt;
    private final String campaignScope;
    private long sequence;
    private Event contiguous;
    private LiveSchedule.Due inFlight;
    private String globalStop;
    private Instant deferredUntil;

    GroupedLiveScheduleV7(List<UUID> targets, Instant start, Instant endsAt, Duration interval, UUID campaignId) {
        if (!MINUTE.equals(interval)) throw new IllegalArgumentException("LIVE_V7_INTERVAL_REQUIRED");
        if (targets.size() > LiveAdmissionPolicy.V7_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        this.endsAt = endsAt;
        campaignScope = campaignId == null ? "offline|" + start + "|" + endsAt : campaignId.toString();
        for (int i = 0; i < targets.size(); i++) events.put(targets.get(i),
                new Event(targets.get(i), start.plusNanos(MINUTE.toNanos() * i / targets.size())));
    }

    Optional<LiveSchedule.Due> next(Instant now) {
        if (inFlight != null || globalStop != null) return Optional.empty();
        if (!now.isBefore(endsAt)) { stopAll("STOPPED_LIMIT"); return Optional.empty(); }
        if (deferredUntil != null) {
            if (now.isBefore(deferredUntil)) return Optional.empty();
            deferredUntil = null;
        }
        for (Event event : events.values()) {
            if (event.active() && expiredPrematchWindow(event, now)) {
                event.pending.clear(); if (contiguous == event) contiguous = null;
                planPrematch(event, now);
            }
        }
        if (contiguous != null && contiguous.active() && !contiguous.pending.isEmpty())
            return Optional.of(contiguous.pending.getFirst());
        contiguous = null;
        Event selected = null;
        Instant at = null;
        for (Event event : events.values()) {
            if (!event.active()) continue;
            // A limiter or slow exchange can cross the quiet window. Drop a stale
            // lineup-only task; never perform a catch-up request between T-5 and T0.
            if (event.pending.isEmpty() && (event.mode == Mode.LINEUPS || event.mode == Mode.WARMUP) && event.kickoff != null
                    && !now.isBefore(event.kickoff.minusSeconds(300))) planPrematch(event, now);
            Instant candidate = nextAt(event);
            if (candidate != null && (at == null || candidate.isBefore(at))) { selected = event; at = candidate; }
        }
        if (selected == null || at.isAfter(now)) return Optional.empty();
        if (selected.pending.isEmpty()) beginGroup(selected, now);
        return Optional.of(selected.pending.getFirst());
    }

    boolean mayDispatch(LiveSchedule.Due due, Instant now) {
        Event event = events.get(due.eventId());
        return globalStop == null && event != null && event.active() && now.isBefore(endsAt)
                && !expiredPrematchWindow(event, now)
                && (deferredUntil == null || !now.isBefore(deferredUntil)) && !now.isBefore(due.dueAt())
                && !event.pending.isEmpty() && event.pending.getFirst().equals(due)
                && (contiguous == null || contiguous == event);
    }

    void defer(LiveSchedule.Due due, Instant notBefore) {
        Event event = event(due.eventId());
        if (inFlight != null || globalStop != null || !event.active() || event.pending.isEmpty()
                || !event.pending.getFirst().equals(due) || !notBefore.isAfter(due.dueAt()))
            throw new IllegalStateException("LIVE_DISPATCH_CANCELLED");
        deferredUntil = bounded(deferredUntil == null || notBefore.isAfter(deferredUntil) ? notBefore : deferredUntil);
        // A not-yet-dispatched lineup-only group can be invalidated when its
        // eligible window closes; no group ordinal has been consumed in that case.
        if (event.mode == Mode.LINEUPS && event.kickoff != null
                && !notBefore.isBefore(event.kickoff.minusSeconds(300))) {
            event.pending.clear(); contiguous = null; planPrematch(event, notBefore);
        }
    }

    void started(LiveSchedule.Due due, Instant now) {
        if (inFlight != null || !mayDispatch(due, now)) throw new IllegalStateException("LIVE_DISPATCH_CANCELLED");
        Event event = event(due.eventId());
        event.lastStarts.put(due.endpoint(), now);
        contiguous = event; inFlight = due;
    }

    void completed(LiveSchedule.Due due, String status, boolean unavailable, Map<String, Boolean> signals,
                   Instant now, Instant kickoff, Boolean confirmed) {
        if (!Objects.equals(inFlight, due)) throw new IllegalStateException("LIVE_UNEXPECTED_COMPLETION");
        inFlight = null;
        Event event = event(due.eventId());
        if (!event.active() || globalStop != null) return;
        event.pending.removeFirst();
        if (due.endpoint() == EVENT_DETAILS) {
            if (unavailable) { stopEvent(event.id, "STOPPED_REVIEW_REQUIRED"); return; }
            if ("postponed".equals(status)) { event.sport = status; stopEvent(event.id, "STOPPED_POSTPONED"); return; }
            if (!Set.of("notstarted", "delayed", "inprogress", "finished").contains(status == null ? "" : status)
                    || "inprogress".equals(event.sport) && waitingForKickoff(status)) {
                stopEvent(event.id, "STOPPED_REVIEW_REQUIRED"); return;
            }
            if (!Objects.equals(event.sport, status)) {
                event.unavailableUntil.clear(); event.unavailableCounts.clear(); event.unavailableIntervals.clear();
            }
            event.rephasePlaying = "inprogress".equals(status)
                    && (waitingForKickoff(event.sport) || event.mode == Mode.RECHECK);
            if ("delayed".equals(status)) {
                if (event.reserveFinish) { stopEvent(event.id, "STOPPED_LIMIT"); return; }
                if (kickoff == null) { stopEvent(event.id, "STOPPED_REVIEW_REQUIRED"); return; }
                boolean changedKickoff = !kickoff.equals(event.kickoff);
                event.kickoff = kickoff;
                event.sport = status;
                event.state = "WAITING_START";
                event.rephasePlaying = false;
                if (changedKickoff) event.warmupDone = false;

                Instant warmup = kickoff.minusSeconds(3600);
                // The J4 we have just completed is the T-60 refresh when the revised
                // kickoff lands exactly on that boundary. Keep the three J5 reads in
                // this group; otherwise re-plan without an immediate second J4.
                if (!event.warmupDone && now.equals(warmup)) {
                    event.mode = Mode.WARMUP;
                    event.warmupDone = true;
                    for (var family : J5) append(event, family, "J5_PREMATCH_HOUR", false);
                    return;
                } else {
                    event.pending.clear();
                    if (contiguous == event) contiguous = null;
                    if (!now.isBefore(warmup)) event.warmupDone = true;
                    planPrematch(event, now);
                    return;
                }
            }
            if (kickoff != null && !kickoff.equals(event.kickoff)) {
                event.kickoff = kickoff; event.warmupDone = false;
            }
            event.sport = status;
            if ("finished".equals(status)) {
                event.mode = Mode.FINAL; event.state = "FINALIZING"; event.finalGood = true;
                for (var family : J5) append(event, family, "J5_FINAL", true);
            } else if (event.reserveFinish) { stopEvent(event.id, "STOPPED_LIMIT"); return; }
            else if ("inprogress".equals(status)) {
                event.mode = Mode.PLAY; event.state = "COLLECTING";
                for (var family : J5) if (available(event, family, now)) append(event, family, "J5_NORMAL", false);
            } else {
                event.state = "WAITING_START";
                if (event.kickoff == null) { stopEvent(event.id, "STOPPED_REVIEW_REQUIRED"); return; }
                if (event.mode == Mode.INITIAL || event.mode == Mode.WARMUP) {
                    for (var family : J5) append(event, family, "J5_PREMATCH_INITIAL", false);
                    if (!now.isBefore(event.kickoff.minusSeconds(3600))) event.warmupDone = true;
                }
            }
        } else {
            if (event.mode == Mode.FINAL) event.finalGood &= !unavailable;
            else updateAvailability(event, due.endpoint(), unavailable, now);
            if (due.endpoint() == EVENT_LINEUPS && !unavailable && confirmed != null) event.confirmed = confirmed;
        }
        if (event.pending.isEmpty()) finishGroup(event, now);
    }

    private void beginGroup(Event event, Instant now) {
        contiguous = event; event.groupSequence = sequence++;
        event.groupId = UUID.nameUUIDFromBytes(("live-v7|" + campaignScope + "|" + event.id + "|" + event.groupSequence)
                .getBytes(StandardCharsets.UTF_8));
        event.ordinal = 0; event.groupDue = event.next;
        if (event.mode == Mode.LINEUPS) append(event, EVENT_LINEUPS, "J5_PREMATCH_LINEUPS", false);
        else append(event, EVENT_DETAILS, event.reserveFinish ? "J4_FINAL_CHECK" : switch (event.mode) {
            case INITIAL -> "J4_INITIAL";
            case WARMUP -> "J4_PREMATCH_HOUR";
            case KICKOFF -> "J4_KICKOFF_WAIT";
            case RECHECK -> "J4_TIMEOUT_RECHECK";
            default -> "J4_CYCLE";
        }, event.reserveFinish);
    }

    private void append(Event event, SofascoreEndpointType endpoint, String kind, boolean finalCycle) {
        event.pending.add(new LiveSchedule.Due(event.id, endpoint, event.groupSequence, kind, event.groupDue,
                finalCycle, event.groupId, event.groupSequence, event.ordinal++));
    }

    private void finishGroup(Event event, Instant now) {
        contiguous = null;
        if (event.mode == Mode.FINAL) { event.finalComplete = event.finalGood; event.state = "FINISHED_CONFIRMED"; event.next = null; return; }
        if ("inprogress".equals(event.sport)) {
            recordCoalescedRounds(event, now);
            event.mode = Mode.PLAY;
            if (event.rephasePlaying) {
                // Kickoff checks share a deadline. Restore the event's stable serial
                // phase once, without adding a second group inside its nominal minute.
                Instant earliest = event.groupDue.plus(MINUTE);
                if (!earliest.isAfter(now)) earliest = now.plusNanos(1);
                event.next = event.phaseOrigin;
                while (event.next.isBefore(earliest)) event.next = event.next.plus(MINUTE);
                event.rephasePlaying = false;
            } else event.next = nextFutureMinute(event.groupDue, now);
            return;
        }
        if (event.mode == Mode.INITIAL || event.mode == Mode.WARMUP) {
            // If the initial group spans T-60, it already supplies that refresh.
            if (!now.isBefore(event.kickoff.minusSeconds(3600))) event.warmupDone = true;
        }
        if (event.mode == Mode.KICKOFF && !now.isBefore(event.kickoff)) {
            event.next = nextFutureMinute(event.groupDue, now); return;
        }
        planPrematch(event, now);
    }

    private void planPrematch(Event event, Instant now) {
        Instant warmup = event.kickoff.minusSeconds(3600), quiet = event.kickoff.minusSeconds(300);
        if (!event.warmupDone && now.isBefore(warmup)) { event.mode = Mode.WARMUP; event.next = warmup; return; }
        if (!event.warmupDone && now.isBefore(quiet)) { event.mode = Mode.WARMUP; event.next = now; return; }
        if (!event.confirmed && now.isBefore(quiet)) {
            Instant previous = event.lastStarts.get(EVENT_LINEUPS);
            Instant next = previous == null ? now : previous.plusSeconds(300);
            Instant unavailable = event.unavailableUntil.get(EVENT_LINEUPS);
            if (unavailable != null && unavailable.isAfter(next)) next = unavailable;
            if (next.isBefore(now)) next = now;
            if (next.isBefore(quiet)) { event.mode = Mode.LINEUPS; event.next = next; return; }
        }
        event.mode = Mode.KICKOFF;
        Instant previous = event.lastStarts.get(EVENT_DETAILS);
        // A shared hold can outlive kickoff. The first kickoff check remains due;
        // only an already dispatched check starts the subsequent minute cadence.
        event.next = previous == null || previous.isBefore(event.kickoff) ? event.kickoff
                : nextFutureMinute(previous, now);
    }

    private Instant nextFutureMinute(Instant nominal, Instant now) {
        Instant next = nominal.plus(MINUTE);
        while (!next.isAfter(now)) next = next.plus(MINUTE);
        return next;
    }

    private void recordCoalescedRounds(Event event, Instant now) {
        for (Instant skipped = event.groupDue.plus(MINUTE); !skipped.isAfter(now); skipped = skipped.plus(MINUTE)) {
            event.missedCycles++;
            event.familyMisses.merge(EVENT_DETAILS, 1L, Long::sum);
            for (var family : J5) if (available(event, family, skipped))
                event.familyMisses.merge(family, 1L, Long::sum);
        }
    }

    private boolean available(Event event, SofascoreEndpointType endpoint, Instant now) {
        Instant available = event.unavailableUntil.get(endpoint);
        return available == null || !now.isBefore(available);
    }

    private void updateAvailability(Event event, SofascoreEndpointType endpoint, boolean unavailable, Instant now) {
        if (!unavailable) {
            event.unavailableCounts.remove(endpoint); event.unavailableUntil.remove(endpoint); event.unavailableIntervals.remove(endpoint); return;
        }
        int count = Math.min(3, event.unavailableCounts.getOrDefault(endpoint, 0) + 1);
        long delay = endpoint == EVENT_LINEUPS && waitingForKickoff(event.sport)
                ? 300 : Math.min(900, 300L * (1L << (count - 1)));
        event.unavailableCounts.put(endpoint, count); event.unavailableIntervals.put(endpoint, delay);
        event.unavailableUntil.put(endpoint, now.plusSeconds(delay));
    }

    void deferAfterTimeout(LiveSchedule.Due due, Instant endedAt) {
        if (!Objects.equals(inFlight, due)) throw new IllegalStateException("LIVE_UNEXPECTED_TIMEOUT");
        inFlight = null;
        Event event = event(due.eventId());
        if (!event.active() || globalStop != null) return;
        event.pending.clear(); contiguous = null; event.finalComplete = false;
        event.missedCycles++; event.familyMisses.merge(due.endpoint(), 1L, Long::sum);
        if (due.finalCycle() || event.mode == Mode.FINAL || event.reserveFinish) { stopEvent(event.id, "STOPPED_ERROR"); return; }
        event.next = endedAt.plus(LiveTimeoutRecoveryPolicy.RETRY_DELAY);
        if (waitingForKickoff(event.sport) && event.kickoff != null
                && !event.next.isBefore(event.kickoff.minusSeconds(300)) && event.next.isBefore(event.kickoff))
            event.next = event.kickoff;
        if (!event.next.isBefore(endsAt)) { stopEvent(event.id, "STOPPED_LIMIT"); return; }
        event.mode = Mode.RECHECK;
    }

    void reserveFinalCheck(UUID id, Instant now) {
        Event event = event(id);
        if (!event.active() || event.mode == Mode.FINAL) return;
        if (event.reserveFinish) { stopEvent(id, "STOPPED_LIMIT"); return; }
        event.reserveFinish = true; event.pending.clear(); if (contiguous == event) contiguous = null;
        event.mode = Mode.RECHECK;
        Instant previous = event.lastStarts.get(EVENT_DETAILS);
        event.next = previous != null && previous.plusSeconds(60).isAfter(now) ? previous.plusSeconds(60) : now;
    }
    void failed(LiveSchedule.Due due, String scope, String reason) {
        inFlight = null; Event event = event(due.eventId()); event.finalComplete = false;
        if ("FINISHED_CONFIRMED".equals(event.state)) event.state = reason;
        if ("EVENT".equals(scope)) stopEvent(event.id, reason); else stopAll(reason);
    }
    void stopEvent(UUID id, String reason) {
        Event event = event(id); if (event.active()) event.state = reason;
        event.pending.clear(); event.next = null; if (contiguous == event) contiguous = null;
    }
    void stopAll(String reason) { globalStop = reason; events.keySet().forEach(id -> stopEvent(id, reason)); }
    boolean terminal() { return globalStop != null || events.values().stream().noneMatch(Event::active); }
    String globalStop() { return globalStop; }
    List<LiveSchedule.EventState> states() {
        return events.values().stream().map(event -> new LiveSchedule.EventState(event.id, event.state, event.sport,
                event.active() ? nextAt(event) : null, event.missedCycles, event.finalComplete)).toList();
    }
    List<FamilySchedule> familySchedules(UUID id) {
        Event event = event(id); List<FamilySchedule> result = new ArrayList<>();
        for (var endpoint : List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS)) {
            Instant next = null; long interval = endpoint == EVENT_LINEUPS && !"inprogress".equals(event.sport) ? 300 : 60;
            if (event.active()) {
                next = event.pending.stream().filter(d -> d.endpoint() == endpoint).map(LiveSchedule.Due::dueAt).findFirst().orElse(null);
                if (next == null && !event.pending.isEmpty() && event.mode == Mode.PLAY)
                    next = event.groupDue.plus(MINUTE);
                if (next == null && event.pending.isEmpty() && event.next != null && (endpoint == EVENT_DETAILS && event.mode != Mode.LINEUPS
                        || endpoint == EVENT_LINEUPS && event.mode == Mode.LINEUPS
                        || event.mode == Mode.PLAY || event.mode == Mode.INITIAL || event.mode == Mode.WARMUP)) next = event.next;
                Instant available = event.unavailableUntil.get(endpoint);
                if (available != null && next != null && event.mode == Mode.PLAY && available.isAfter(next)) next = available;
                next = pressureBound(next);
            }
            interval = event.unavailableIntervals.getOrDefault(endpoint, interval);
            result.add(new FamilySchedule(endpoint, next, interval, event.familyMisses.getOrDefault(endpoint, 0L)));
        }
        return List.copyOf(result);
    }
    private Instant nextAt(Event event) { return pressureBound(event.pending.isEmpty() ? event.next : event.pending.getFirst().dueAt()); }
    private boolean expiredPrematchWindow(Event event, Instant now) {
        return (event.mode == Mode.LINEUPS || event.mode == Mode.WARMUP
                || event.mode == Mode.RECHECK && waitingForKickoff(event.sport)
                    && event.kickoff != null && now.isBefore(event.kickoff)) && event.kickoff != null
                && !now.isBefore(event.kickoff.minusSeconds(300));
    }
    private static boolean waitingForKickoff(String status) {
        return "notstarted".equals(status) || "delayed".equals(status);
    }
    private Instant pressureBound(Instant next) { return next == null ? null : bounded(deferredUntil != null && deferredUntil.isAfter(next) ? deferredUntil : next); }
    private Instant bounded(Instant value) { return value.isAfter(endsAt) ? endsAt : value; }
    private Event event(UUID id) { Event event = events.get(id); if (event == null) throw new IllegalArgumentException("unknown selected event"); return event; }
    private static final class Event {
        final UUID id;
        final Instant phaseOrigin;
        final EnumMap<SofascoreEndpointType, Instant> lastStarts = new EnumMap<>(SofascoreEndpointType.class);
        final EnumMap<SofascoreEndpointType, Long> familyMisses = new EnumMap<>(SofascoreEndpointType.class);
        final EnumMap<SofascoreEndpointType, Instant> unavailableUntil = new EnumMap<>(SofascoreEndpointType.class);
        final EnumMap<SofascoreEndpointType, Integer> unavailableCounts = new EnumMap<>(SofascoreEndpointType.class);
        final EnumMap<SofascoreEndpointType, Long> unavailableIntervals = new EnumMap<>(SofascoreEndpointType.class);
        final List<LiveSchedule.Due> pending = new ArrayList<>();
        String state = "WAITING_START", sport;
        Mode mode = Mode.INITIAL;
        Instant kickoff, next, groupDue;
        UUID groupId;
        long groupSequence, missedCycles;
        int ordinal;
        boolean warmupDone, confirmed, reserveFinish, finalGood = true, finalComplete, rephasePlaying;
        Event(UUID id, Instant next) { this.id = id; this.next = next; this.phaseOrigin = next; }
        boolean active() { return !state.startsWith("STOPPED") && !"FINISHED_CONFIRMED".equals(state); }
    }
}
