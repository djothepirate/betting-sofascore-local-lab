package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.FamilySchedule;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/** V8 kickoff windows, independent of the immutable V4–V6 calendar. No network or clock reads. */
final class GroupedLiveScheduleV8 {
    private static final Duration MINUTE = Duration.ofSeconds(60);
    static final Duration POST_EXCHANGE_FENCE = Duration.ofMillis(500);
    private static final List<SofascoreEndpointType> J5 = List.of(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private static final List<SofascoreEndpointType> FAMILIES = List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private enum Mode { INITIAL, WARMUP, LINEUPS, KICKOFF, PLAY, FINAL, RECHECK, ENVELOPE_RECHECK, PRESSURE_RECHECK }
    private final LinkedHashMap<UUID, Event> events = new LinkedHashMap<>();
    private final Instant endsAt;
    private final String campaignScope;
    private final SlotPlan slots;
    private long sequence;
    private Event contiguous;
    private LiveSchedule.Due inFlight;
    private String globalStop;
    private Instant deferredUntil;

    GroupedLiveScheduleV8(List<UUID> targets, Instant start, Instant endsAt, Duration interval, UUID campaignId,
                          GroupedAdmissionProfile profile) {
        if (!MINUTE.equals(interval)) throw new IllegalArgumentException("LIVE_V8_INTERVAL_REQUIRED");
        if (targets.size() > LiveAdmissionPolicy.V8_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        this.endsAt = endsAt;
        slots = SlotPlan.from(profile);
        campaignScope = campaignId == null ? "offline|" + start + "|" + endsAt : campaignId.toString();
        for (int i = 0; i < targets.size(); i++) {
            Duration phase = slots.groupReservation().multipliedBy(i);
            events.put(targets.get(i), new Event(targets.get(i), start.plus(phase), phase));
        }
    }

    static Duration strictGroupReservation(GroupedAdmissionProfile profile) {
        return SlotPlan.from(profile).groupReservation();
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
        if (contiguous != null && contiguous.active() && !contiguous.pending.isEmpty()) {
            LiveSchedule.Due due = contiguous.pending.getFirst();
            return due.dueAt().isAfter(now) ? Optional.empty() : Optional.of(due);
        }
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
        // A pressure recheck has an explicit durable release instant. Preserve
        // that deadline in the reservation even when the owner thread wakes a
        // few milliseconds later; `started` still records the actual local
        // authorization and the authenticated worker departure remains final.
        if (selected.pending.isEmpty())
            beginGroup(selected, selected.mode == Mode.PRESSURE_RECHECK ? at : now);
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
        // A durable pressure gate has made this in-play group miss its qualified
        // departure.  Do not resume its remaining normal-family calls later and
        // present them as a 60-second collection: make the gap durable, retain
        // the families that were actually skipped, then restart with a fresh J4
        // recheck once the local budget permits a departure again.
        if (event.mode == Mode.PLAY) {
            deferForPressureRecheck(event, due.dueAt());
        } else if (event.mode == Mode.PRESSURE_RECHECK) {
            // A second gate can arrive before the fresh J4 leaves. It extends
            // the same explicit pressure hold rather than recreating a normal
            // group or charging the already-recorded missed collection twice.
            event.pending.clear();
            if (contiguous == event) contiguous = null;
            event.next = deferredUntil;
        }
        // The departure gate is campaign-wide. Every other normal in-play
        // target whose next V8 slot falls before the same hold must expose
        // that missed collection as pressure too; otherwise its UI state
        // would incorrectly remain COLLECTING at a 60-second cadence. This is
        // deliberately independent of the source family's phase: a pressure
        // decision made while a prematch family is due still holds PLAY groups.
        for (Event other : events.values()) {
            if (other == event || !other.active() || other.mode != Mode.PLAY) continue;
            Instant normalTarget = rawNextAt(other);
            if (normalTarget != null && normalTarget.isBefore(deferredUntil))
                deferForPressureRecheck(other, normalTarget);
        }
        if (event.mode == Mode.PRESSURE_RECHECK) return;
        // A not-yet-dispatched lineup-only group can be invalidated when its
        // eligible window closes; no group ordinal has been consumed in that case.
        if (event.mode == Mode.LINEUPS && event.kickoff != null
                && !notBefore.isBefore(event.kickoff.minusSeconds(300))) {
            event.pending.clear(); contiguous = null; planPrematch(event, notBefore);
        }
    }

    private void deferForPressureRecheck(Event event, Instant normalTarget) {
        List<LiveSchedule.Due> skipped = List.copyOf(event.pending);
        event.pending.clear();
        if (contiguous == event) contiguous = null;
        event.finalComplete = false;
        event.missedCycles++;
        if (skipped.isEmpty() || skipped.getFirst().endpoint() == EVENT_DETAILS) {
            event.familyMisses.merge(EVENT_DETAILS, 1L, Long::sum);
            for (SofascoreEndpointType family : J5)
                if (available(event, family, normalTarget)) event.familyMisses.merge(family, 1L, Long::sum);
        } else for (LiveSchedule.Due skippedDue : skipped)
            event.familyMisses.merge(skippedDue.endpoint(), 1L, Long::sum);
        event.mode = Mode.PRESSURE_RECHECK;
        event.state = "WAITING_PRESSURE_RECHECK";
        event.next = deferredUntil;
    }

    void started(LiveSchedule.Due due, Instant now) {
        if (inFlight != null || !mayDispatch(due, now)) throw new IllegalStateException("LIVE_DISPATCH_CANCELLED");
        Event event = event(due.eventId());
        event.lastStarts.put(due.endpoint(), now);
        event.departures.remove(due.endpoint());
        // A pressure gate can defer the first request after `next()` has created
        // a group.  This is only the local authorization baseline; `departed`
        // replaces it with authenticated worker evidence before a completed
        // group derives its next qualified minute.
        if (due.groupOrdinal() == 0) event.groupDue = now;
        contiguous = event; inFlight = due;
    }

    /**
     * Rephase the active V8 group from the authenticated worker-side request
     * emission.  A transport can leave measurably after local admission; V8's
     * fixed family offsets and next normal J4 wave must use that actual instant.
     */
    void departed(LiveSchedule.Due due, Instant requestedAt) {
        Objects.requireNonNull(requestedAt, "requestedAt");
        Event event = event(due.eventId());
        if (!Objects.equals(inFlight, due)) {
            // A concurrent operator stop can make a late diagnostic irrelevant.
            if (!event.active() || globalStop != null) return;
            throw new IllegalStateException("LIVE_UNEXPECTED_DEPARTURE");
        }
        Instant previous = event.departures.putIfAbsent(due.endpoint(), requestedAt);
        if (previous != null) {
            if (!previous.equals(requestedAt)) throw new IllegalStateException("LIVE_DEPARTURE_TIMESTAMP_MISMATCH");
            return;
        }
        event.lastStarts.put(due.endpoint(), requestedAt);
        if (due.groupOrdinal() == 0) event.groupDue = requestedAt;
    }

    void completed(LiveSchedule.Due due, String status, boolean unavailable, Map<String, Boolean> signals,
                   Instant now, Instant kickoff, Boolean confirmed) {
        if (!Objects.equals(inFlight, due)) throw new IllegalStateException("LIVE_UNEXPECTED_COMPLETION");
        inFlight = null;
        Event event = event(due.eventId());
        if (!event.active() || globalStop != null) return;
        event.pending.removeFirst();
        if (slots.exchangeExceeded(due.endpoint(), event.lastStarts.get(due.endpoint()), now)) {
            deferAfterEnvelope(event, due, now);
            return;
        }
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
                    && (waitingForKickoff(event.sport) || event.mode == Mode.RECHECK || event.mode == Mode.ENVELOPE_RECHECK);
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
                // kickoff lands on or has just passed that boundary. Until T-5, keep
                // the one-time J5 warm-up in this group rather than silently treating
                // a late J4 response as proof that the warm-up already ran.
                if (!event.warmupDone && !now.isBefore(warmup) && now.isBefore(kickoff.minusSeconds(300))) {
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
        event.groupId = UUID.nameUUIDFromBytes(("live-v8|" + campaignScope + "|" + event.id + "|" + event.groupSequence)
                .getBytes(StandardCharsets.UTF_8));
        // `started` establishes a local authorization baseline. `departed`
        // replaces it with the authenticated worker request timestamp, which
        // defines this event's serial phase for its remaining family slots and
        // later normal waves.
        event.ordinal = 0; event.groupDue = now;
        if (event.mode == Mode.LINEUPS) append(event, EVENT_LINEUPS, "J5_PREMATCH_LINEUPS", false);
        else append(event, EVENT_DETAILS, event.reserveFinish ? "J4_FINAL_CHECK" : switch (event.mode) {
            case INITIAL -> "J4_INITIAL";
            case WARMUP -> "J4_PREMATCH_HOUR";
            case KICKOFF -> "J4_KICKOFF_WAIT";
            case RECHECK -> "J4_TIMEOUT_RECHECK";
            case ENVELOPE_RECHECK -> "J4_ENVELOPE_RECHECK";
            case PRESSURE_RECHECK -> "J4_PRESSURE_RECHECK";
            default -> "J4_CYCLE";
        }, event.reserveFinish);
    }

    private void append(Event event, SofascoreEndpointType endpoint, String kind, boolean finalCycle) {
        // A pre-kickoff lineup retry is a one-call group. It has no preceding
        // J4/incidents/statistics exchanges, so reserving the fourth-family
        // offset would make `next()` expose a future due immediately.
        Duration offset = event.mode == Mode.LINEUPS && endpoint == EVENT_LINEUPS
                ? Duration.ZERO : slots.offset(endpoint);
        event.pending.add(new LiveSchedule.Due(event.id, endpoint, event.groupSequence, kind,
                event.groupDue.plus(offset),
                finalCycle, event.groupId, event.groupSequence, event.ordinal++));
    }

    private void finishGroup(Event event, Instant now) {
        contiguous = null;
        if (event.mode == Mode.FINAL) { event.finalComplete = event.finalGood; event.state = "FINISHED_CONFIRMED"; event.next = null; return; }
        if ("inprogress".equals(event.sport)) {
            recordCoalescedRounds(event, now);
            event.mode = Mode.PLAY;
            Instant departure = event.lastStarts.getOrDefault(EVENT_DETAILS, event.groupDue);
            if (event.rephasePlaying) {
                // Once the shared kickoff wave has completed, retain this group's
                // actual deadline for the next wave. The single dispatcher then
                // gives every group the same serial offset on each later minute.
                // An overrun is deliberately not caught up: qualification must
                // reject an envelope that cannot finish this wave before its next
                // nominal deadline.
                Instant nextWave = departure.plus(MINUTE);
                event.next = nextWave.isBefore(now) ? nextFutureMinute(departure, now) : nextWave;
                event.rephasePlaying = false;
            } else event.next = nextFutureMinute(departure, now);
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
        // Each target reserves its qualified group phase at kickoff.  A launch can
        // be close enough to T0 that its initial J4 already observed `notstarted`
        // less than one minute earlier.  In that case, do not create a duplicate
        // T0 J4+J5 wave: the existing per-family initial observations remain fresh
        // until their stable next phase, while an immediate replay would turn ten
        // four-family groups into a 60-departure rolling-minute spike.  The first
        // in-play J4 is therefore no earlier than both the kickoff phase and the
        // preceding J4's 60-second freshness boundary.  Once it reports
        // `inprogress`, the normal J5 trio follows in the same serial group.
        // A shared hold can still defer that phase, in which case the actual
        // departure becomes the rephased baseline for the next normal minute.
        Instant kickoffPhase = event.kickoff.plus(event.kickoffPhase);
        event.next = previous != null && previous.isBefore(event.kickoff)
                ? later(kickoffPhase, previous.plus(MINUTE))
                : previous == null ? kickoffPhase : nextFutureMinute(previous, now);
    }

    private static Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
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

    /** A completion beyond its immutable qualified envelope leaves the strict path. */
    private void deferAfterEnvelope(Event event, LiveSchedule.Due due, Instant completedAt) {
        event.pending.clear(); contiguous = null; event.finalComplete = false;
        event.missedCycles++; event.familyMisses.merge(due.endpoint(), 1L, Long::sum);
        if (due.finalCycle() || event.mode == Mode.FINAL || event.reserveFinish) {
            stopEvent(event.id, "STOPPED_REVIEW_REQUIRED");
            return;
        }
        event.mode = Mode.ENVELOPE_RECHECK;
        event.state = "WAITING_ENVELOPE_RECHECK";
        event.next = completedAt.plus(LiveTimeoutRecoveryPolicy.RETRY_DELAY);
        if (!event.next.isBefore(endsAt)) stopEvent(event.id, "STOPPED_LIMIT");
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
                    next = event.groupDue.plus(MINUTE).plus(slots.offset(endpoint));
                if (next == null && event.pending.isEmpty() && event.next != null && (endpoint == EVENT_DETAILS && event.mode != Mode.LINEUPS
                        || endpoint == EVENT_LINEUPS && event.mode == Mode.LINEUPS)) next = event.next;
                if (next == null && event.pending.isEmpty() && event.next != null
                        && (event.mode == Mode.PLAY || event.mode == Mode.INITIAL || event.mode == Mode.WARMUP
                        || event.mode == Mode.RECHECK || event.mode == Mode.ENVELOPE_RECHECK
                        || event.mode == Mode.PRESSURE_RECHECK))
                    next = event.next.plus(slots.offset(endpoint));
                Instant available = event.unavailableUntil.get(endpoint);
                if (available != null && next != null && event.mode == Mode.PLAY && available.isAfter(next)) next = available;
                next = pressureBound(next);
            }
            interval = event.unavailableIntervals.getOrDefault(endpoint, interval);
            result.add(new FamilySchedule(endpoint, next, interval, event.familyMisses.getOrDefault(endpoint, 0L)));
        }
        return List.copyOf(result);
    }
    private Instant nextAt(Event event) { return pressureBound(rawNextAt(event)); }
    private Instant rawNextAt(Event event) { return event.pending.isEmpty() ? event.next : event.pending.getFirst().dueAt(); }
    private boolean expiredPrematchWindow(Event event, Instant now) {
        return (event.mode == Mode.LINEUPS || event.mode == Mode.WARMUP
                || (event.mode == Mode.RECHECK || event.mode == Mode.ENVELOPE_RECHECK) && waitingForKickoff(event.sport)
                    && event.kickoff != null && now.isBefore(event.kickoff)) && event.kickoff != null
                && !now.isBefore(event.kickoff.minusSeconds(300));
    }
    private static boolean waitingForKickoff(String status) {
        return "notstarted".equals(status) || "delayed".equals(status);
    }
    private Instant pressureBound(Instant next) { return next == null ? null : bounded(deferredUntil != null && deferredUntil.isAfter(next) ? deferredUntil : next); }
    private Instant bounded(Instant value) { return value.isAfter(endsAt) ? endsAt : value; }
    private Event event(UUID id) { Event event = events.get(id); if (event == null) throw new IllegalArgumentException("unknown selected event"); return event; }

    private static final class SlotPlan {
        private final EnumMap<SofascoreEndpointType, Duration> offsets = new EnumMap<>(SofascoreEndpointType.class);
        private final EnumMap<SofascoreEndpointType, Duration> envelopes = new EnumMap<>(SofascoreEndpointType.class);
        private final Duration groupReservation;

        private SlotPlan(GroupedAdmissionProfile profile) {
            if (profile == null || !"live-v8".equals(profile.policyVersion()))
                throw new IllegalArgumentException("LIVE_V8_SLOT_PROFILE_REQUIRED");
            Duration offset = Duration.ZERO;
            for (var endpoint : FAMILIES) {
                Duration exchange = profile.envelope(endpoint).exchangeEnvelope();
                offsets.put(endpoint, offset);
                envelopes.put(endpoint, exchange);
                offset = offset.plus(exchange).plus(POST_EXCHANGE_FENCE);
            }
            groupReservation = offset;
        }

        static SlotPlan from(GroupedAdmissionProfile profile) { return new SlotPlan(profile); }
        Duration offset(SofascoreEndpointType endpoint) { return offsets.get(endpoint); }
        Duration groupReservation() { return groupReservation; }
        boolean exchangeExceeded(SofascoreEndpointType endpoint, Instant startedAt, Instant completedAt) {
            return startedAt == null || completedAt.isAfter(startedAt.plus(envelopes.get(endpoint)));
        }
    }

    private static final class Event {
        final UUID id;
        final Duration kickoffPhase;
        final EnumMap<SofascoreEndpointType, Instant> lastStarts = new EnumMap<>(SofascoreEndpointType.class);
        final EnumMap<SofascoreEndpointType, Instant> departures = new EnumMap<>(SofascoreEndpointType.class);
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
        Event(UUID id, Instant next, Duration kickoffPhase) { this.id = id; this.next = next; this.kickoffPhase = kickoffPhase; }
        boolean active() { return !state.startsWith("STOPPED") && !"FINISHED_CONFIRMED".equals(state); }
    }
}
