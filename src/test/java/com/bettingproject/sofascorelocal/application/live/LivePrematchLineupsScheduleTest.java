package com.bettingproject.sofascorelocal.application.live;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

class LivePrematchLineupsScheduleTest {
    private static final Instant START = Instant.parse("2026-09-07T16:00:00Z");
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    private LiveSchedule schedule(int interval) {
        return new LiveSchedule(List.of(a), START, START.plusSeconds(14400), Duration.ofSeconds(interval), "live-v3");
    }

    private LiveSchedule.Due execute(LiveSchedule schedule, long second, String status, boolean unavailable) {
        Instant at = START.plusSeconds(second);
        var due = schedule.next(at).orElseThrow();
        schedule.started(due, at);
        schedule.completed(due, status, unavailable, Map.of(), at.plusMillis(1));
        return due;
    }

    @ParameterizedTest
    @ValueSource(ints = {60, 120, 450, 720})
    void onlyLineupsPollBeforeKickoffAndNormalTripletStartsAtExistingFamilyEligibility(int interval) {
        var schedule = schedule(interval);
        execute(schedule, 0, "notstarted", false);
        var first = execute(schedule, 3, null, true);
        assertThat(first.endpoint()).isEqualTo(EVENT_LINEUPS);
        assertThat(first.kind()).isEqualTo("J5_PREMATCH_LINEUPS");
        assertThat(first.finalCycle()).isFalse();
        assertThat(schedule.states().getFirst().state()).isEqualTo("WAITING_START");
        assertThat(schedule.next(START.plusSeconds(interval - 1))).isEmpty();

        assertThat(execute(schedule, interval, "notstarted", false).endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(schedule.next(START.plusSeconds(interval + 2))).isEmpty();
        var second = execute(schedule, interval + 3, null, false);
        assertThat(second.kind()).isEqualTo("J5_PREMATCH_LINEUPS");
        assertThat(second.cycle()).isGreaterThan(first.cycle());
        assertThat(schedule.globalStop()).isNull(); // A 404 did not retry or suspend this family.

        execute(schedule, interval * 2L, "inprogress", false);
        assertThat(schedule.next(START.plusSeconds(interval * 2L + 2))).isEmpty();
        assertThat(execute(schedule, interval * 2L + 3, null, false).endpoint()).isEqualTo(EVENT_STATISTICS);
        assertThat(execute(schedule, interval * 2L + 6, null, false).endpoint()).isEqualTo(EVENT_INCIDENTS);
        var normalLineup = execute(schedule, interval * 2L + 9, null, false);
        assertThat(normalLineup.endpoint()).isEqualTo(EVENT_LINEUPS);
        assertThat(normalLineup.kind()).isEqualTo("J5_NORMAL");
        assertThat(schedule.states().getFirst().state()).isEqualTo("COLLECTING");
        assertThat(schedule.states().getFirst().missedCycles()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v1", "live-v2"})
    void historicalPoliciesRetainOnlyJ4WhileWaiting(String policy) {
        var schedule = new LiveSchedule(List.of(a), START, START.plusSeconds(300), Duration.ofSeconds(60), policy);
        execute(schedule, 0, "notstarted", false);
        assertThat(schedule.next(START.plusSeconds(59))).isEmpty();
        assertThat(execute(schedule, 60, "notstarted", false).endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(schedule.next(START.plusSeconds(119))).isEmpty();
    }

    @Test
    void alreadyPlayingMatchDoesNotRunAPrematchLineup() {
        var schedule = schedule(60);
        execute(schedule, 0, "inprogress", false);
        assertThat(execute(schedule, 3, null, false).endpoint()).isEqualTo(EVENT_STATISTICS);
        assertThat(execute(schedule, 6, null, false).endpoint()).isEqualTo(EVENT_INCIDENTS);
        assertThat(execute(schedule, 9, null, false).kind()).isEqualTo("J5_NORMAL");
    }

    @Test
    void finishedAfterPrematchKeepsFinalLineupSpacedAndCancelsOrdinaryPrematchWork() {
        var schedule = schedule(60);
        execute(schedule, 0, "notstarted", false);
        execute(schedule, 3, null, false);
        execute(schedule, 60, "finished", false);
        assertThat(schedule.states().getFirst().state()).isEqualTo("FINALIZING");
        assertThat(schedule.next(START.plusSeconds(62))).isEmpty();
        assertThat(execute(schedule, 63, null, false).endpoint()).isEqualTo(EVENT_STATISTICS);
        assertThat(execute(schedule, 66, null, false).endpoint()).isEqualTo(EVENT_INCIDENTS);
        var finalLineup = execute(schedule, 69, null, true);
        assertThat(finalLineup.finalCycle()).isTrue();
        assertThat(finalLineup.kind()).isEqualTo("J5_FINAL");
        assertThat(schedule.states().getFirst().state()).isEqualTo("FINISHED_CONFIRMED");
        assertThat(schedule.states().getFirst().finalComplete()).isFalse();
        assertThat(schedule.next(START.plusSeconds(123))).isEmpty();
    }

    @Test
    void delayedFirstTripletDoesNotOccupyTheWorkerBeforeAllItsFamiliesAreEligible() {
        var schedule = new LiveSchedule(List.of(a, b), START, START.plusSeconds(300), Duration.ofSeconds(60), "live-v3");
        execute(schedule, 0, "notstarted", false);
        execute(schedule, 3, "notstarted", false);
        execute(schedule, 6, null, false); // A lineup.
        execute(schedule, 9, null, false); // B lineup.
        execute(schedule, 60, "inprogress", false);
        // A triplet is aligned to 66; the due J4 for B at 63 must still run.
        assertThat(schedule.next(START.plusSeconds(63)).orElseThrow().eventId()).isEqualTo(b);
        execute(schedule, 63, "inprogress", false);
        assertThat(schedule.next(START.plusSeconds(65))).isEmpty();
        assertThat(execute(schedule, 66, null, false).eventId()).isEqualTo(a);
        assertThat(execute(schedule, 69, null, false).eventId()).isEqualTo(a);
        assertThat(execute(schedule, 72, null, false).eventId()).isEqualTo(a);
        assertThat(execute(schedule, 75, null, false).eventId()).isEqualTo(b);
    }

    @Test
    void reservedFinalJ4CancelsPrematchPollingWithoutInventingFinished() {
        var schedule = schedule(60);
        execute(schedule, 0, "notstarted", false);
        execute(schedule, 3, null, false);
        schedule.reserveFinalCheck(a, START.plusSeconds(4));
        assertThat(schedule.next(START.plusSeconds(59))).isEmpty();
        var check = execute(schedule, 60, "notstarted", false);
        assertThat(check.endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(check.finalCycle()).isTrue();
        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_LIMIT");
        assertThat(schedule.next(START.plusSeconds(63))).isEmpty();
    }

    @Test
    void stoppedOrExpiredPrematchLineupsCannotDispatchAndOverloadCannotCatchUpInABurst() {
        var schedule = schedule(60);
        execute(schedule, 0, "notstarted", false);
        var pending = schedule.next(START.plusSeconds(3)).orElseThrow();
        schedule.stopEvent(a, "STOPPED_OPERATOR");
        assertThat(schedule.mayDispatch(pending, START.plusSeconds(3))).isFalse();
        assertThat(schedule.next(START.plusSeconds(3))).isEmpty();

        schedule = schedule(60);
        execute(schedule, 0, "notstarted", false);
        assertThat(schedule.next(START.plusSeconds(121))).isEmpty();
        assertThat(schedule.globalStop()).isEqualTo("STOPPED_CAPACITY");

        schedule = schedule(60);
        execute(schedule, 0, "notstarted", false);
        assertThat(schedule.next(START.plusSeconds(14400))).isEmpty();
        assertThat(schedule.globalStop()).isEqualTo("STOPPED_LIMIT");
    }

    @Test
    void prematchSchemaFailureIsolatedToItsMatchAndSecurityFailureRemainsGlobal() {
        for (String scope : List.of("EVENT", "CAMPAIGN")) {
            var schedule = new LiveSchedule(List.of(a, b), START, START.plusSeconds(300), Duration.ofSeconds(60), "live-v3");
            execute(schedule, 0, "notstarted", false);
            execute(schedule, 3, "notstarted", false);
            var due = schedule.next(START.plusSeconds(6)).orElseThrow();
            schedule.started(due, START.plusSeconds(6));
            schedule.failed(due, scope, "EVENT".equals(scope) ? "STOPPED_SCHEMA_INCOMPATIBLE" : "STOPPED_ERROR");
            if ("EVENT".equals(scope)) assertThat(schedule.next(START.plusSeconds(9)).orElseThrow().eventId()).isEqualTo(b);
            else assertThat(schedule.next(START.plusSeconds(9))).isEmpty();
        }
    }

    @Test
    void twoPrematchCompletionsBeyondTheirNextDeadlineStopCapacity() {
        var schedule = schedule(60);
        execute(schedule, 0, "notstarted", false);
        // Delays are inside the pure scheduling model; no wall-clock sleep or transport.
        var first = schedule.next(START.plusSeconds(3)).orElseThrow();
        schedule.started(first, START.plusSeconds(3));
        schedule.completed(first, null, false, Map.of(), START.plusSeconds(65));
        execute(schedule, 66, "notstarted", false);
        var second = schedule.next(START.plusSeconds(69)).orElseThrow();
        assertThat(second.endpoint()).isEqualTo(EVENT_LINEUPS);
        schedule.started(second, START.plusSeconds(69));
        schedule.completed(second, null, false, Map.of(), START.plusSeconds(125));
        assertThat(schedule.globalStop()).isEqualTo("STOPPED_CAPACITY");
        assertThat(schedule.states().getFirst().missedCycles()).isEqualTo(2);
    }
}
