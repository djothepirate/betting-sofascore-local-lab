package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.FamilySchedule;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

/** Fixed nominal phases and one contiguous, individually accountable group at a time. */
final class GroupedLiveScheduleV4 {
    private final Duration period;
    private final int lineupRounds;
    private final String policyVersion;
    private static final List<SofascoreEndpointType> FINAL = List.of(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private final LinkedHashMap<UUID, Event> events = new LinkedHashMap<>();
    private final Instant endsAt;
    private final String campaignScope;
    private long groupSequence;
    private Event contiguous;
    private LiveSchedule.Due inFlight;
    private String globalStop;
    private Instant deferredUntil;

    GroupedLiveScheduleV4(List<UUID> targets, Instant start, Instant endsAt, Duration interval, UUID campaignId) {
        this(targets, start, endsAt, interval, campaignId, "live-v4");
    }

    GroupedLiveScheduleV4(List<UUID> targets, Instant start, Instant endsAt, Duration interval,
                         UUID campaignId, String policyVersion) {
        this.policyVersion = Objects.requireNonNull(policyVersion);
        this.period = switch (policyVersion) {
            case "live-v4" -> Duration.ofSeconds(60);
            case "live-v5", "live-v6" -> Duration.ofSeconds(100);
            default -> throw new IllegalArgumentException("LIVE_GROUPED_POLICY_INVALID");
        };
        if (!period.equals(interval)) throw new IllegalArgumentException(switch (policyVersion) {
            case "live-v6" -> "LIVE_V6_INTERVAL_REQUIRED";
            case "live-v5" -> "LIVE_V5_INTERVAL_REQUIRED";
            default -> "LIVE_V4_INTERVAL_REQUIRED";
        });
        if ("live-v5".equals(policyVersion) && targets.size() > 20)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        if (resilient() && targets.size() > LiveAdmissionPolicy.V6_MAXIMUM_SELECTION_SIZE)
            throw new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        this.lineupRounds = (int) (300 / period.toSeconds());
        this.endsAt = endsAt;
        this.campaignScope = campaignId == null ? "offline|" + start + "|" + endsAt : campaignId.toString();
        for (int i = 0; i < targets.size(); i++) {
            events.put(targets.get(i), new Event(targets.get(i), i,
                    start.plusNanos(period.toNanos() * i / targets.size())));
        }
    }

    Optional<LiveSchedule.Due> next(Instant now) {
        if (inFlight != null || globalStop != null) return Optional.empty();
        if (!now.isBefore(endsAt)) { stopAll("STOPPED_LIMIT"); return Optional.empty(); }
        if (deferredUntil != null) {
            if (now.isBefore(deferredUntil)) return Optional.empty();
            deferredUntil = null;
        }
        for (Event event : events.values()) {
            if (event.active() && !event.finalizing && !event.pressureDeferred
                    && !now.isBefore(event.nominalAt().plus(period.multipliedBy(2)))) {
                event.missedCycles += 2;
                stopAll("STOPPED_CAPACITY");
                return Optional.empty();
            }
        }
        if (contiguous != null && contiguous.active() && !contiguous.pending.isEmpty()) {
            return Optional.of(contiguous.pending.getFirst());
        }
        contiguous = null;
        Event selected = null;
        Instant selectedAt = null;
        boolean selectedCriticalDue = false;
        for (Event event : events.values()) {
            if (!event.active()) continue;
            Instant eligible = nextAt(event);
            boolean criticalDue = !event.finalizing && eligible != null && !eligible.isAfter(now);
            // V6 can deliberately remain behind nominal phases under shared pressure.
            // Serve the oldest eligible work, including a deferred final family, so
            // recurring critical rounds cannot starve finalization until the hard end.
            if (eligible != null && (selectedAt == null || !resilient() && criticalDue && !selectedCriticalDue
                    || (resilient() || criticalDue == selectedCriticalDue) && eligible.isBefore(selectedAt))) {
                selected = event;
                selectedAt = eligible;
                selectedCriticalDue = criticalDue;
            }
        }
        if (selected == null || selectedAt.isAfter(now)) return Optional.empty();
        if (selected.pending.isEmpty()) beginGroup(selected, now);
        return Optional.of(selected.pending.getFirst());
    }

    boolean mayDispatch(LiveSchedule.Due due, Instant now) {
        Event event = events.get(due.eventId());
        return globalStop == null && event != null && event.active() && now.isBefore(endsAt)
                && (deferredUntil == null || !now.isBefore(deferredUntil))
                && !now.isBefore(due.dueAt()) && !event.pending.isEmpty()
                && event.pending.getFirst().equals(due)
                && (contiguous == null || contiguous == event);
    }

    void defer(LiveSchedule.Due due, Instant notBefore) {
        if (!resilient()) throw new IllegalStateException("LIVE_DEFER_UNSUPPORTED_POLICY");
        Objects.requireNonNull(notBefore);
        Event event = event(due.eventId());
        if (inFlight != null || globalStop != null || !event.active() || event.pending.isEmpty()
                || !event.pending.getFirst().equals(due) || !notBefore.isAfter(due.dueAt()))
            throw new IllegalStateException("LIVE_DISPATCH_CANCELLED");
        Instant bounded = notBefore.isAfter(endsAt) ? endsAt : notBefore;
        if (deferredUntil == null || bounded.isAfter(deferredUntil)) deferredUntil = bounded;
        for (Event selected : events.values()) if (selected.active()) selected.pressureDeferred = true;
    }

    void started(LiveSchedule.Due due, Instant now) {
        if (inFlight != null || !mayDispatch(due, now)) throw new IllegalStateException("LIVE_DISPATCH_CANCELLED");
        Event event = events.get(due.eventId());
        if (!eligible(event, due.endpoint(), now)) throw new IllegalStateException("LIVE_FAMILY_CADENCE");
        event.lastStarts.put(due.endpoint(), now);
        contiguous = event;
        inFlight = due;
    }

    void deferAfterTimeout(LiveSchedule.Due due, Instant endedAt) {
        if (!resilient() || !Objects.equals(inFlight, due))
            throw new IllegalStateException("LIVE_UNEXPECTED_TIMEOUT");
        Event event = event(due.eventId());
        inFlight = null;
        if (!event.active() || globalStop != null) return;
        event.finalComplete = false;
        event.familyMisses.merge(due.endpoint(), 1L, Long::sum);
        event.missedCycles++;
        event.pending.clear();
        contiguous = null;
        if (due.finalCycle() || event.finalizing || event.reserveFinish) {
            stopEvent(event.id, "STOPPED_ERROR");
            return;
        }
        // No continuation of a partly completed group and no catch-up burst.
        // Hold every family of this event until a fresh J4 establishes its phase.
        event.round++;
        Instant retryAt = endedAt.plus(LiveTimeoutRecoveryPolicy.RETRY_DELAY);
        if (!retryAt.isBefore(endsAt)) { stopEvent(event.id, "STOPPED_LIMIT"); return; }
        event.phase = retryAt.minus(period.multipliedBy(event.round));
        event.consecutiveMisses = 0;
        event.pressureDeferred = false;
    }

    void completed(LiveSchedule.Due due, String status, boolean unavailable,
                   Map<String, Boolean> signals, Instant now) {
        if (!Objects.equals(inFlight, due)) throw new IllegalStateException("LIVE_UNEXPECTED_COMPLETION");
        inFlight = null;
        Event event = events.get(due.eventId());
        if (!event.active() || globalStop != null) return;
        event.pending.removeFirst();
        long familySeconds = due.endpoint() == EVENT_LINEUPS && !"J5_PREMATCH_LINEUPS".equals(due.kind()) ? 300 : period.toSeconds();
        if (!due.finalCycle() && !event.pressureDeferred && !now.isBefore(due.dueAt().plusSeconds(familySeconds)))
            event.familyMisses.merge(due.endpoint(), 1L, Long::sum);
        if (due.endpoint() == EVENT_DETAILS) {
            if (unavailable) { stopEvent(event.id, "STOPPED_REVIEW_REQUIRED"); return; }
            if ("postponed".equals(status)) {
                event.sport = status;
                stopEvent(event.id, "STOPPED_POSTPONED");
                return;
            }
            if (!Set.of("notstarted", "inprogress", "finished").contains(status == null ? "" : status)
                    || "inprogress".equals(event.sport) && "notstarted".equals(status)) {
                stopEvent(event.id, "STOPPED_REVIEW_REQUIRED");
                return;
            }
            if (resilient() && !Objects.equals(event.sport, status)) {
                boolean lineupWasUnavailable = event.unavailableUntil.containsKey(EVENT_LINEUPS);
                event.unavailableCounts.clear();
                event.unavailableUntil.clear();
                event.unavailableIntervals.clear();
                if (lineupWasUnavailable) event.nextLineupRound = event.round;
            }
            event.sport = status;
            if ("finished".equals(status)) {
                event.finalizing = true;
                event.finalReadyAt = now;
                event.state = "FINALIZING";
                event.finalGood = true;
                event.finalRemaining.addAll(FINAL);
                appendFinalEligible(event, now);
            } else if (event.reserveFinish) {
                stopEvent(event.id, "STOPPED_LIMIT");
                return;
            } else if ("notstarted".equals(status)) {
                event.state = "WAITING_START";
                if (eligible(event, EVENT_LINEUPS, now)) append(event, EVENT_LINEUPS, "J5_PREMATCH_LINEUPS", false, now);
            } else {
                event.state = event.checkingFinish ? "CHECKING_FINISH" : "COLLECTING";
                // Critical periods are nominal phases, not a sleep between endpoints.
                // A recent prematch lineup never defers incidents or statistics at kickoff.
                if (eligible(event, EVENT_INCIDENTS, now)) append(event, EVENT_INCIDENTS, "J5_NORMAL", false, now);
                if (eligible(event, EVENT_STATISTICS, now)) append(event, EVENT_STATISTICS, "J5_NORMAL", false, now);
                if (lineupRoundDue(event) && eligible(event, EVENT_LINEUPS, now))
                    append(event, EVENT_LINEUPS, "J5_NORMAL", false, now);
            }
        } else {
            if (event.finalizing) {
                event.finalRemaining.remove(due.endpoint());
                event.finalGood &= !unavailable;
                // A lineup that was too recent immediately after J4 can become
                // admissible while incidents/statistics are processed. Reuse the
                // same group if it is ready now; never wait inside that group.
                if (event.pending.isEmpty()) appendFinalEligible(event, now);
            } else {
                if (resilient()) updateAvailability(event, due, unavailable, now);
                if (due.endpoint() == EVENT_LINEUPS) event.nextLineupRound = nextLineupPhase(event, event.round);
                if (signals.values().stream().anyMatch(Boolean.TRUE::equals)) {
                    event.checkingFinish = true;
                    event.state = "CHECKING_FINISH";
                }
            }
        }
        if (event.pending.isEmpty()) finishGroup(event, now);
    }

    private void beginGroup(Event event, Instant now) {
        // Reservation/transport admission can take time. Keep the chosen identity
        // stable until dispatch or cancellation so group sequences cannot overtake.
        contiguous = event;
        event.sequence = groupSequence++;
        String groupPolicy = !"live-v4".equals(policyVersion) ? policyVersion + "|critical=" + period.toSeconds() : policyVersion;
        event.groupId = UUID.nameUUIDFromBytes((groupPolicy + "|" + campaignScope + "|" + event.id + "|" + event.sequence)
                .getBytes(StandardCharsets.UTF_8));
        event.ordinal = 0;
        event.groupNominal = event.nominalAt();
        if (event.finalizing) appendFinalEligible(event, now);
        else append(event, EVENT_DETAILS, event.reserveFinish ? "J4_FINAL_CHECK"
                : event.round == 0 ? "J4_INITIAL" : "J4_CYCLE", event.reserveFinish, now);
    }

    private void append(Event event, SofascoreEndpointType endpoint, String kind, boolean finalCycle, Instant now) {
        Instant dueAt = event.finalizing ? earliest(event, endpoint, event.finalReadyAt)
                : event.reserveFinish ? event.reserveAt : event.groupNominal;
        event.pending.add(new LiveSchedule.Due(event.id, endpoint, event.sequence, kind, dueAt,
                finalCycle, event.groupId, event.sequence, event.ordinal++));
    }

    private void appendFinalEligible(Event event, Instant now) {
        for (SofascoreEndpointType endpoint : FINAL) {
            if (event.finalRemaining.contains(endpoint) && eligible(event, endpoint, now))
                append(event, endpoint, "J5_FINAL", true, now);
        }
    }

    private void finishGroup(Event event, Instant now) {
        contiguous = null;
        if (event.finalizing) {
            if (event.finalRemaining.isEmpty()) {
                event.finalComplete = event.finalGood;
                event.state = "FINISHED_CONFIRMED";
            }
            return;
        }
        if (resilient() && event.pressureDeferred) {
            event.round++;
            Instant next = event.groupNominal.plus(period);
            Instant lastCheck = event.lastStarts.get(EVENT_DETAILS);
            if (lastCheck != null && lastCheck.plus(period).isAfter(next)) next = lastCheck.plus(period);
            if (!next.isAfter(now)) next = now.plus(period);
            // A held group produces one future round, never a burst of expired rounds.
            event.phase = next.minus(period.multipliedBy(event.round));
            event.consecutiveMisses = 0;
            event.pressureDeferred = false;
            return;
        }
        if (!now.isBefore(event.groupNominal.plus(period.multipliedBy(2)))) {
            event.missedCycles += 2;
            stopAll("STOPPED_CAPACITY");
            return;
        }
        if (!now.isBefore(event.groupNominal.plus(period))) {
            event.missedCycles++;
            event.consecutiveMisses++;
        } else event.consecutiveMisses = 0;
        event.round++;
        // A single slow group is one missed cycle. Coalescing its overlapping
        // nominal round must not count that same isolated delay twice. The next
        // service starts at a future fixed phase, never an immediate catch-up burst.
        while (!event.nominalAt().isAfter(now)) event.round++;
        if (event.consecutiveMisses >= 2) stopAll("STOPPED_CAPACITY");
    }

    private Instant nextAt(Event event) {
        return pressureBound(nextUnrestrictedAt(event));
    }

    private Instant nextUnrestrictedAt(Event event) {
        if (!event.pending.isEmpty()) return event.pending.getFirst().dueAt();
        if (event.finalizing) return event.finalRemaining.stream()
                .map(endpoint -> earliest(event, endpoint, event.finalReadyAt)).min(Comparator.naturalOrder()).orElse(null);
        return event.reserveAt == null ? event.nominalAt() : event.reserveAt;
    }

    private Instant earliest(Event event, SofascoreEndpointType endpoint, Instant candidate) {
        if (resilient() && endpoint != EVENT_DETAILS && !event.finalizing) {
            Instant unavailableUntil = event.unavailableUntil.get(endpoint);
            if (unavailableUntil != null && unavailableUntil.isAfter(candidate)) candidate = unavailableUntil;
        }
        if (endpoint != EVENT_LINEUPS || !event.finalizing) return candidate;
        Instant previous = event.lastStarts.get(endpoint);
        return previous != null && previous.plus(period).isAfter(candidate) ? previous.plus(period) : candidate;
    }

    private boolean eligible(Event event, SofascoreEndpointType endpoint, Instant now) {
        return !earliest(event, endpoint, now).isAfter(now);
    }

    private void updateAvailability(Event event, LiveSchedule.Due due, boolean unavailable, Instant now) {
        SofascoreEndpointType endpoint = due.endpoint();
        if (!unavailable) {
            event.unavailableCounts.remove(endpoint);
            event.unavailableUntil.remove(endpoint);
            event.unavailableIntervals.remove(endpoint);
            return;
        }
        int count = Math.min(3, event.unavailableCounts.getOrDefault(endpoint, 0) + 1);
        long nominal = endpoint == EVENT_LINEUPS && !"J5_PREMATCH_LINEUPS".equals(due.kind()) ? 300 : period.toSeconds();
        long interval = Math.min(900, Math.max(300, nominal * 2) * (1L << (count - 1)));
        event.unavailableCounts.put(endpoint, count);
        event.unavailableIntervals.put(endpoint, interval);
        event.unavailableUntil.put(endpoint, now.plusSeconds(interval));
    }

    private boolean resilient() { return "live-v6".equals(policyVersion); }

    private Instant pressureBound(Instant candidate) {
        if (candidate == null || !resilient()) return candidate;
        if (deferredUntil != null && candidate.isBefore(deferredUntil)) candidate = deferredUntil;
        return candidate.isAfter(endsAt) ? endsAt : candidate;
    }

    private boolean lineupRoundDue(Event event) {
        return !event.lastStarts.containsKey(EVENT_LINEUPS) || event.round >= event.nextLineupRound;
    }

    private long nextLineupPhase(Event event, long after) {
        long next = after + 1;
        while (next % lineupRounds != event.index % lineupRounds) next++;
        return next;
    }

    void reserveFinalCheck(UUID id, Instant now) {
        Event event = event(id);
        if (!event.active() || event.finalizing) return;
        if (event.reserveFinish) { stopEvent(id, "STOPPED_LIMIT"); return; }
        event.reserveFinish = true;
        Instant previous = event.lastStarts.get(EVENT_DETAILS);
        event.reserveAt = previous != null && previous.plus(period).isAfter(now) ? previous.plus(period) : now;
        event.pending.clear();
        if (contiguous == event) contiguous = null;
    }

    void failed(LiveSchedule.Due due, String scope, String reason) {
        inFlight = null;
        Event event = event(due.eventId());
        event.finalComplete = false;
        if ("FINISHED_CONFIRMED".equals(event.state)) event.state = reason;
        if ("EVENT".equals(scope)) stopEvent(event.id, reason); else stopAll(reason);
    }

    void stopEvent(UUID id, String reason) {
        Event event = event(id);
        if (event.active()) event.state = reason;
        event.pending.clear();
        event.finalRemaining.clear();
        if (contiguous == event) contiguous = null;
    }

    void stopAll(String reason) {
        globalStop = reason;
        events.keySet().forEach(id -> stopEvent(id, reason));
    }

    boolean terminal() { return globalStop != null || events.values().stream().noneMatch(Event::active); }
    String globalStop() { return globalStop; }

    List<LiveSchedule.EventState> states() {
        return events.values().stream().map(event -> new LiveSchedule.EventState(event.id, event.state,
                event.sport, event.active() ? nextAt(event) : null, event.missedCycles, event.finalComplete)).toList();
    }

    List<FamilySchedule> familySchedules(UUID id) {
        Event event = event(id);
        List<FamilySchedule> result = new ArrayList<>();
        for (SofascoreEndpointType endpoint : List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS)) {
            Instant due = null;
            long seconds = endpoint == EVENT_LINEUPS && !"notstarted".equals(event.sport) ? 300 : period.toSeconds();
            if (event.active()) {
                Optional<LiveSchedule.Due> pending = event.pending.stream().filter(call -> call.endpoint() == endpoint).findFirst();
                if (pending.isPresent()) due = pending.orElseThrow().dueAt();
                else if (event.finalizing) {
                    if (event.finalRemaining.contains(endpoint)) due = earliest(event, endpoint, event.finalReadyAt);
                } else if (endpoint == EVENT_DETAILS || "inprogress".equals(event.sport)
                        || endpoint == EVENT_LINEUPS && "notstarted".equals(event.sport)) {
                    long nextRound = event.pending.isEmpty() ? event.round : event.round + 1;
                    due = endpoint == EVENT_LINEUPS && "inprogress".equals(event.sport)
                            ? event.phase.plus(period.multipliedBy(Math.max(nextRound, event.nextLineupRound)))
                            : event.reserveAt != null ? event.reserveAt : event.phase.plus(period.multipliedBy(nextRound));
                    due = earliest(event, endpoint, due);
                }
            }
            if (resilient()) {
                seconds = event.unavailableIntervals.getOrDefault(endpoint, seconds);
                due = pressureBound(due);
            }
            result.add(new FamilySchedule(endpoint, due, seconds, event.familyMisses.getOrDefault(endpoint, 0L)));
        }
        return List.copyOf(result);
    }

    private Event event(UUID id) {
        Event event = events.get(id);
        if (event == null) throw new IllegalArgumentException("unknown selected event");
        return event;
    }

    private final class Event {
        final UUID id;
        final int index;
        Instant phase;
        final EnumMap<SofascoreEndpointType, Instant> lastStarts = new EnumMap<>(SofascoreEndpointType.class);
        final EnumMap<SofascoreEndpointType, Long> familyMisses = new EnumMap<>(SofascoreEndpointType.class);
        final EnumMap<SofascoreEndpointType, Integer> unavailableCounts = new EnumMap<>(SofascoreEndpointType.class);
        final EnumMap<SofascoreEndpointType, Instant> unavailableUntil = new EnumMap<>(SofascoreEndpointType.class);
        final EnumMap<SofascoreEndpointType, Long> unavailableIntervals = new EnumMap<>(SofascoreEndpointType.class);
        final EnumSet<SofascoreEndpointType> finalRemaining = EnumSet.noneOf(SofascoreEndpointType.class);
        final List<LiveSchedule.Due> pending = new ArrayList<>();
        String state = "WAITING_START", sport;
        long round, nextLineupRound, sequence, missedCycles;
        int ordinal, consecutiveMisses;
        UUID groupId;
        Instant groupNominal, reserveAt, finalReadyAt;
        boolean checkingFinish, reserveFinish, finalizing, finalGood = true, finalComplete, pressureDeferred;
        Event(UUID id, int index, Instant phase) { this.id = id; this.index = index; this.phase = phase; }
        Instant nominalAt() { return phase.plus(period.multipliedBy(round)); }
        boolean active() { return !state.startsWith("STOPPED") && !"FINISHED_CONFIRMED".equals(state); }
    }
}
