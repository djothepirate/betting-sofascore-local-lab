package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.FamilySchedule;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/** V9 kickoff windows, independent of the immutable V4–V6 calendar. No network or clock reads. */
final class GroupedLiveScheduleV9 {
    private static final Duration MINUTE = Duration.ofSeconds(60);
    static final Duration POST_EXCHANGE_FENCE = Duration.ofMillis(500);
    /**
     * A static reservation between adjacent V9 groups.  It is not a fifth
     * post-exchange fence: it absorbs the supervisor's bounded worker-start
     * gate before the next group reaches REQUEST_SENT.  Keeping it in the
     * slot plan makes the ten initial phases safe even when one authenticated
     * departure is later than its neighbour.
     */
    static final Duration INTER_GROUP_SLOT_RESERVE = Duration.ofSeconds(1);
    /**
     * Normal in-play J4s are offered this far before their actual
     * REQUEST_SENT deadline. The bounded worker-start jitter is intentionally
     * no greater than half the static inter-group slot reserve: a late emission is
     * explicitly requalified instead of being presented as a 60-second normal
     * departure.
     */
    static final Duration PLAY_REQUEST_EMISSION_HEAD_START = Duration.ofMillis(500);
    private static final List<SofascoreEndpointType> J5 = List.of(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private static final List<SofascoreEndpointType> FAMILIES = List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private enum Mode { INITIAL, WARMUP, LINEUPS, KICKOFF, PLAY, FINAL, RECHECK, ENVELOPE_RECHECK,
        PRESSURE_RECHECK, CADENCE_RECHECK, HALFTIME_HOLD, HALFTIME_RECHECK, SUSPENDED_RECHECK }
    private final LinkedHashMap<UUID, Event> events = new LinkedHashMap<>();
    private final Instant endsAt;
    private final String campaignScope;
    private final SlotPlan slots;
    private long sequence;
    private Event contiguous;
    private LiveSchedule.Due inFlight;
    private String globalStop;
    private Instant deferredUntil;
    /** A proven local closing fence is transient and never makes a collection missed. */
    private Instant postExchangeFenceUntil;

    GroupedLiveScheduleV9(List<UUID> targets, Instant start, Instant endsAt, Duration interval, UUID campaignId,
                           GroupedAdmissionProfile profile) {
        this(targets, start, endsAt, interval, campaignId, profile, 0);
    }

    GroupedLiveScheduleV9(List<UUID> targets, Instant start, Instant endsAt, Duration interval, UUID campaignId,
                           GroupedAdmissionProfile profile, long initialGroupSequence) {
        if (!MINUTE.equals(interval)) throw new IllegalArgumentException("LIVE_V9_INTERVAL_REQUIRED");
        if (targets.size() > LiveAdmissionPolicy.V9_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        if (initialGroupSequence < 0) throw new IllegalArgumentException("LIVE_V9_GROUP_SEQUENCE_REQUIRED");
        this.endsAt = endsAt;
        slots = SlotPlan.from(profile);
        campaignScope = campaignId == null ? "offline|" + start + "|" + endsAt : campaignId.toString();
        sequence = initialGroupSequence;
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
        if (postExchangeFenceUntil != null) {
            if (now.isBefore(postExchangeFenceUntil)) return Optional.empty();
            postExchangeFenceUntil = null;
        }
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
                && (postExchangeFenceUntil == null || !now.isBefore(postExchangeFenceUntil))
                && (deferredUntil == null || !now.isBefore(deferredUntil)) && !now.isBefore(due.dueAt())
                && !event.pending.isEmpty() && event.pending.getFirst().equals(due)
                && (contiguous == null || contiguous == event);
    }

    /**
     * Hold the active pending due until the completed-exchange fence closes.
     * This is deliberately distinct from {@link #defer(LiveSchedule.Due, Instant)}:
     * no state, family schedule, group identity, or missed-cycle counter changes.
     */
    void waitForPostExchangeFence(LiveSchedule.Due due, Instant notBefore) {
        Event event = event(due.eventId());
        if (inFlight != null || globalStop != null || !event.active() || event.pending.isEmpty()
                || !event.pending.getFirst().equals(due) || !notBefore.isAfter(due.dueAt()))
            throw new IllegalStateException("LIVE_DISPATCH_CANCELLED");
        postExchangeFenceUntil = bounded(postExchangeFenceUntil == null || notBefore.isAfter(postExchangeFenceUntil)
                ? notBefore : postExchangeFenceUntil);
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
        // target whose next V9 slot falls before the same hold must expose
        // that missed collection as pressure too; otherwise its UI state
        // would incorrectly remain COLLECTING at a 60-second cadence. This is
        // deliberately independent of the source family's phase: a pressure
        // decision made while a prematch family is due still holds PLAY groups.
        for (Event other : events.values()) {
            if (other == event || !other.active() || other.mode != Mode.PLAY) continue;
            Instant normalTarget = rawNextAt(other);
            // Equality is pressure too: the first recheck released at this
            // instant occupies the single dispatcher before this peer could
            // begin its normal group. Leaving it in PLAY would make its next
            // departure exceed the 60-second target without a missed-cycle
            // record or an explicit pressure state.
            if (normalTarget != null && !normalTarget.isAfter(deferredUntil))
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
                if (j5Callable(event, family, event.sport, false) && available(event, family, normalTarget))
                    event.familyMisses.merge(family, 1L, Long::sum);
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
     * Rephase the active V9 group from the authenticated worker-side request
     * emission.  A transport can leave measurably after local admission; V9's
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
        // `dueAt` for a normal J4 already includes the fixed request-emission
        // head start. Do not silently extend an established PLAY cadence when
        // the worker consumes more than its bounded jitter: finish this
        // response for audit, then leave that normal cadence through a durable
        // requalification state. An initial, kickoff, or recovery J4 has no
        // prior normal minute to claim; a completed inprogress response from
        // one of those waves establishes PLAY and rephases J5 from its
        // authenticated REQUEST_SENT timestamp.
        if (event.mode == Mode.PLAY && due.endpoint() == EVENT_DETAILS
                && requestedAt.isAfter(due.dueAt().plus(PLAY_REQUEST_EMISSION_HEAD_START)))
            event.strictEmissionMissed = true;
    }

    void completed(LiveSchedule.Due due, String status, boolean unavailable, Map<String, Boolean> signals,
                   Instant now, Instant kickoff, Boolean confirmed, LiveJ4ControlFacts controls,
                   String responseCode) {
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
            if (unavailable || controls == null) { stopEvent(event.id, "STOPPED_REVIEW_REQUIRED"); return; }
            applyControls(event, controls);
            if (controls.isFinalResultOnly()) { stopEvent(event.id, "STOPPED_FINAL_RESULT_ONLY"); return; }
            if (!controls.hasSupportedDetailId()) { stopEvent(event.id, "STOPPED_DETAIL_ID_UNSUPPORTED"); return; }
            if ("postponed".equals(status)) { event.sport = status; stopEvent(event.id, "STOPPED_POSTPONED"); return; }
            boolean recheckingHalftime = event.mode == Mode.HALFTIME_HOLD || event.mode == Mode.HALFTIME_RECHECK;
            if (!Set.of("notstarted", "delayed", "inprogress", "suspended", "interrupted", "canceled", "finished")
                    .contains(status == null ? "" : status)
                    || "inprogress".equals(event.sport) && waitingForKickoff(status) && !recheckingHalftime) {
                stopEvent(event.id, "STOPPED_REVIEW_REQUIRED"); return;
            }
            if (!"inprogress".equals(status)) event.strictEmissionMissed = false;
            if (!Objects.equals(event.sport, status)) {
                event.unavailableUntil.clear(); event.unavailableCounts.clear(); event.unavailableIntervals.clear();
            }
            event.rephasePlaying = "inprogress".equals(status)
                    && (waitingForKickoff(event.sport) || event.mode == Mode.RECHECK || event.mode == Mode.ENVELOPE_RECHECK
                    || event.mode == Mode.CADENCE_RECHECK || event.mode == Mode.HALFTIME_HOLD
                    || event.mode == Mode.HALFTIME_RECHECK || event.mode == Mode.SUSPENDED_RECHECK);
            if (terminalStatus(status)) {
                event.sport = status;
                event.mode = Mode.FINAL; event.state = "FINALIZING"; event.finalGood = true;
                // A missing-detailId statistics endpoint is deliberately rechecked once at terminal J4,
                // even after three consecutive observed 404s.  It is still a normal bounded group.
                appendEligibleJ5(event, status, "J5_FINAL", true);
            } else if ("suspended".equals(status)) {
                // A temporary sporting suspension is not a transport failure and must never
                // terminate the local campaign.  Its J4 observation invalidates any J5 work
                // remaining in the current group; only another J4 is authorized one minute later.
                event.sport = status;
                holdForSuspension(event, now.plus(MINUTE));
                return;
            } else if (recheckingHalftime && !controls.isSecondHalf(status)) {
                // The fifteen-minute quiet period has already elapsed.  Until J4 explicitly reports
                // the second half, this state performs J4 only at a one-minute cadence; it never
                // falls back to prematch or J5 work merely because the provider still says halftime.
                event.sport = status;
                holdForHalftime(event, now.plus(MINUTE), Mode.HALFTIME_RECHECK,
                        "WAITING_HALFTIME_RECHECK");
                return;
            } else if ("delayed".equals(status)) {
                if (event.reserveFinish) { stopEvent(event.id, "STOPPED_LIMIT"); return; }
                if (kickoff == null) { stopEvent(event.id, "STOPPED_REVIEW_REQUIRED"); return; }
                boolean changedKickoff = !kickoff.equals(event.kickoff);
                event.kickoff = kickoff;
                event.sport = status;
                event.state = "WAITING_START";
                event.rephasePlaying = false;
                if (changedKickoff) event.warmupDone = false;

                Instant warmup = kickoff.minusSeconds(3600);
                if (!event.warmupDone && !now.isBefore(warmup) && now.isBefore(kickoff.minusSeconds(300))) {
                    event.mode = Mode.WARMUP;
                    event.warmupDone = true;
                    appendEligibleJ5(event, status, "J5_PREMATCH_HOUR", false);
                    if (event.pending.isEmpty()) finishGroup(event, now);
                    return;
                }
                event.pending.clear();
                if (contiguous == event) contiguous = null;
                if (!now.isBefore(warmup)) event.warmupDone = true;
                planPrematch(event, now);
                return;
            }
            if (kickoff != null && !kickoff.equals(event.kickoff)) {
                event.kickoff = kickoff; event.warmupDone = false;
            }
            event.sport = status;
            if (event.reserveFinish) { stopEvent(event.id, "STOPPED_LIMIT"); return; }
            else if ("inprogress".equals(status)) {
                if (controls.isHalftime(status)) {
                    holdForHalftime(event, now.plus(Duration.ofMinutes(15)), Mode.HALFTIME_HOLD,
                            "WAITING_HALFTIME_RECHECK");
                    return;
                }
                if (event.strictEmissionMissed) {
                    deferAfterCadence(event, now);
                    return;
                }
                event.mode = Mode.PLAY; event.state = "COLLECTING";
                appendEligibleJ5(event, status, "J5_NORMAL", false);
            } else {
                event.state = "WAITING_START";
                if (event.kickoff == null) { stopEvent(event.id, "STOPPED_REVIEW_REQUIRED"); return; }
                if (event.mode == Mode.INITIAL || event.mode == Mode.WARMUP) {
                    appendEligibleJ5(event, status, "J5_PREMATCH_INITIAL", false);
                    if (!now.isBefore(event.kickoff.minusSeconds(3600))) event.warmupDone = true;
                }
            }
        } else {
            boolean missingDetailStatistics = due.endpoint() == EVENT_STATISTICS
                    && event.controls != null
                    && event.controls.hasMissingDetailId();
            if (missingDetailStatistics && !event.statisticsSuppressed) {
                if (unavailable && "HTTP_404".equals(responseCode)) {
                    event.missingDetailStatistics404s++;
                    if (event.missingDetailStatistics404s >= 3) event.statisticsSuppressed = true;
                } else {
                    event.missingDetailStatistics404s = 0;
                    event.statisticsSuppressed = false;
                }
            }
            if (event.mode == Mode.FINAL) event.finalGood &= !unavailable;
            // Missing detailId uses the dedicated three-consecutive-HTTP-404 contract.  The generic
            // 300/600/900-second availability backoff would suppress the second observation and
            // make it impossible to establish the required consecutive evidence.
            else if (!missingDetailStatistics) updateAvailability(event, due.endpoint(), unavailable, now);
            if (due.endpoint() == EVENT_LINEUPS && !unavailable && confirmed != null) event.confirmed = confirmed;
        }
        if (event.pending.isEmpty()) finishGroup(event, now);
    }

    private static boolean terminalStatus(String status) {
        return "interrupted".equals(status) || "canceled".equals(status) || "finished".equals(status);
    }

    private static boolean statisticsOrIncidentsStatus(String status) {
        return "inprogress".equals(status) || terminalStatus(status);
    }

    private void applyControls(Event event, LiveJ4ControlFacts controls) {
        event.controls = controls;
        // A detailId=1 observation resumes the documented full family contract;
        // stale 404 observations from the genuinely-missing-detailId branch do
        // not suppress a later known statistics endpoint.
        if (controls.detailId() == LiveJ4ControlFacts.DetailIdFact.ONE) {
            event.missingDetailStatistics404s = 0;
            event.statisticsSuppressed = false;
        }
    }

    private void appendEligibleJ5(Event event, String status, String kind, boolean finalCycle) {
        for (SofascoreEndpointType family : J5) {
            boolean terminalMissingDetailStatistics = finalCycle
                    && family == EVENT_STATISTICS
                    && event.controls != null
                    && event.controls.hasMissingDetailId()
                    && event.statisticsSuppressed;
            if (terminalMissingDetailStatistics && event.terminalStatisticsAttemptIssued) continue;
            if (j5Callable(event, family, status, finalCycle)
                    && (finalCycle || available(event, family, event.groupDue))) {
                append(event, family, kind, finalCycle);
                // Mark before the worker is allowed to leave. A timeout, a 404, or a duplicate
                // terminal J4 can then never turn the documented final recheck into a retry loop.
                if (terminalMissingDetailStatistics) event.terminalStatisticsAttemptIssued = true;
            }
        }
    }

    private static boolean j5Callable(Event event, SofascoreEndpointType endpoint, String status, boolean finalCycle) {
        LiveJ4ControlFacts controls = event.controls;
        if (controls == null || !controls.hasSupportedDetailId()) return false;
        // The suspension branch keeps its J4 observation active every minute, but all J5
        // families—including otherwise pre-match-callable lineups—must remain dormant.
        if ("suspended".equals(status)) return false;
        if (endpoint == EVENT_LINEUPS) return controls.lineupsCallable();
        if (!statisticsOrIncidentsStatus(status)) return false;
        if (endpoint == EVENT_INCIDENTS) return true;
        if (endpoint == EVENT_STATISTICS) return finalCycle || !event.statisticsSuppressed;
        return false;
    }

    private void holdForHalftime(Event event, Instant next, Mode mode, String state) {
        event.pending.clear();
        if (contiguous == event) contiguous = null;
        event.mode = mode;
        event.state = state;
        event.next = bounded(next);
    }

    private void holdForSuspension(Event event, Instant next) {
        event.pending.clear();
        if (contiguous == event) contiguous = null;
        event.mode = Mode.SUSPENDED_RECHECK;
        event.state = "WAITING_SUSPENDED_RECHECK";
        event.next = bounded(next);
    }

    private void beginGroup(Event event, Instant now) {
        contiguous = event; event.groupSequence = sequence++;
        event.groupId = UUID.nameUUIDFromBytes(("live-v9|" + campaignScope + "|" + event.id + "|" + event.groupSequence)
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
            case CADENCE_RECHECK -> "J4_CADENCE_RECHECK";
            case HALFTIME_HOLD, HALFTIME_RECHECK -> "J4_HALFTIME_RECHECK";
            case SUSPENDED_RECHECK -> "J4_SUSPENDED_RECHECK";
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
        if (event.mode == Mode.HALFTIME_HOLD || event.mode == Mode.HALFTIME_RECHECK
                || event.mode == Mode.SUSPENDED_RECHECK) return;
        if ("inprogress".equals(event.sport)) {
            recordCoalescedRounds(event, now);
            event.mode = Mode.PLAY;
            Instant departure = event.lastStarts.getOrDefault(EVENT_DETAILS, event.groupDue);
            // Rebase from the previous authenticated departure, but offer the
            // next request 500 ms early. A worker start no later than the
            // bounded jitter therefore leaves REQUEST_SENT at or before the
            // strict 60-second deadline. The inter-group slot reservation is
            // static and remains separate from this per-event head start.
            event.next = nextFutureMinute(departure.minus(PLAY_REQUEST_EMISSION_HEAD_START), now);
            event.rephasePlaying = false;
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
        if (event.controls != null && event.controls.lineupsCallable() && !event.confirmed && now.isBefore(quiet)) {
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
            for (var family : J5) if (j5Callable(event, family, event.sport, false) && available(event, family, skipped))
                event.familyMisses.merge(family, 1L, Long::sum);
        }
    }

    private boolean available(Event event, SofascoreEndpointType endpoint, Instant now) {
        if (endpoint == EVENT_STATISTICS && event.statisticsSuppressed) return false;
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

    /**
     * A late authenticated normal J4 must be requalified rather than rephased as fresh.
     *
     * The strict group is abandoned, including its ordinary J5 work, but cadence-only
     * recovery is not a transport timeout. Re-offer the next J4 on the event's normal
     * authenticated-departure phase. This retains the missed-cycle evidence and avoids
     * a catch-up burst while preventing the unrelated five-minute timeout backoff from
     * turning a bounded worker-start overrun into several minutes of stale J5 data.
     */
    private void deferAfterCadence(Event event, Instant completedAt) {
        event.strictEmissionMissed = false;
        event.pending.clear(); contiguous = null; event.finalComplete = false;
        event.missedCycles++;
        event.familyMisses.merge(EVENT_DETAILS, 1L, Long::sum);
        for (var family : J5) if (j5Callable(event, family, event.sport, false) && available(event, family, completedAt))
            event.familyMisses.merge(family, 1L, Long::sum);
        event.mode = Mode.CADENCE_RECHECK;
        event.state = "WAITING_CADENCE_RECHECK";
        Instant departure = event.lastStarts.getOrDefault(EVENT_DETAILS, event.groupDue);
        event.next = nextFutureMinute(departure.minus(PLAY_REQUEST_EMISSION_HEAD_START), completedAt);
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
                boolean j5Family = endpoint != EVENT_DETAILS;
                boolean callable = !j5Family || ((event.mode != Mode.HALFTIME_HOLD && event.mode != Mode.HALFTIME_RECHECK
                        && event.mode != Mode.SUSPENDED_RECHECK)
                        && j5Callable(event, endpoint, event.sport, event.mode == Mode.FINAL));
                if (callable) {
                    next = event.pending.stream().filter(d -> d.endpoint() == endpoint).map(LiveSchedule.Due::dueAt).findFirst().orElse(null);
                    if (next == null && !event.pending.isEmpty() && event.mode == Mode.PLAY)
                        next = event.groupDue.plus(MINUTE).plus(slots.offset(endpoint));
                    if (next == null && event.pending.isEmpty() && event.next != null && (endpoint == EVENT_DETAILS && event.mode != Mode.LINEUPS
                            || endpoint == EVENT_LINEUPS && event.mode == Mode.LINEUPS)) next = event.next;
                    if (next == null && event.pending.isEmpty() && event.next != null
                            && (event.mode == Mode.PLAY || event.mode == Mode.INITIAL || event.mode == Mode.WARMUP
                            || event.mode == Mode.RECHECK || event.mode == Mode.ENVELOPE_RECHECK || event.mode == Mode.CADENCE_RECHECK
                            || event.mode == Mode.PRESSURE_RECHECK))
                        next = event.next.plus(slots.offset(endpoint));
                    Instant available = event.unavailableUntil.get(endpoint);
                    if (available != null && next != null && event.mode == Mode.PLAY && available.isAfter(next)) next = available;
                    next = dispatchBound(next);
                }
            }
            interval = event.unavailableIntervals.getOrDefault(endpoint, interval);
            result.add(new FamilySchedule(endpoint, next, interval, event.familyMisses.getOrDefault(endpoint, 0L)));
        }
        return List.copyOf(result);
    }
    private Instant nextAt(Event event) { return dispatchBound(rawNextAt(event)); }
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
    private Instant dispatchBound(Instant next) {
        if (next == null) return null;
        Instant candidate = next;
        if (postExchangeFenceUntil != null && postExchangeFenceUntil.isAfter(candidate)) candidate = postExchangeFenceUntil;
        if (deferredUntil != null && deferredUntil.isAfter(candidate)) candidate = deferredUntil;
        return bounded(candidate);
    }
    private Instant bounded(Instant value) { return value.isAfter(endsAt) ? endsAt : value; }
    private Event event(UUID id) { Event event = events.get(id); if (event == null) throw new IllegalArgumentException("unknown selected event"); return event; }

    private static final class SlotPlan {
        private final EnumMap<SofascoreEndpointType, Duration> offsets = new EnumMap<>(SofascoreEndpointType.class);
        private final EnumMap<SofascoreEndpointType, Duration> envelopes = new EnumMap<>(SofascoreEndpointType.class);
        private final Duration groupReservation;

        private SlotPlan(GroupedAdmissionProfile profile) {
            if (profile == null || !"live-v9".equals(profile.policyVersion()))
                throw new IllegalArgumentException("LIVE_V9_SLOT_PROFILE_REQUIRED");
            Duration offset = Duration.ZERO;
            for (var endpoint : FAMILIES) {
                Duration exchange = profile.envelope(endpoint).exchangeEnvelope();
                offsets.put(endpoint, offset);
                envelopes.put(endpoint, exchange);
                offset = offset.plus(exchange).plus(POST_EXCHANGE_FENCE);
            }
            // Preserve the four internal family offsets. The one-second static
            // reservation applies only before the next event group can begin;
            // it preserves a 500 ms closing fence even when an adjacent normal
            // J4 consumes its bounded 500 ms worker-start jitter.
            groupReservation = offset.plus(INTER_GROUP_SLOT_RESERVE);
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
        boolean warmupDone, confirmed, reserveFinish, finalGood = true, finalComplete, rephasePlaying, strictEmissionMissed;
        LiveJ4ControlFacts controls;
        int missingDetailStatistics404s;
        boolean statisticsSuppressed, terminalStatisticsAttemptIssued;
        Event(UUID id, Instant next, Duration kickoffPhase) { this.id = id; this.next = next; this.kickoffPhase = kickoffPhase; }
        boolean active() { return !state.startsWith("STOPPED") && !"FINISHED_CONFIRMED".equals(state); }
    }
}
