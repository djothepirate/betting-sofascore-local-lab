package com.bettingproject.sofascorelocal.application.live;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

class GroupedLiveScheduleV5Test {
    private static final Instant START = Instant.parse("2026-09-08T12:00:00Z");
    private static List<UUID> targets(int count) { return IntStream.range(1, count + 1).mapToObj(i -> new UUID(0, i)).toList(); }
    private static LiveSchedule schedule(List<UUID> targets) {
        return new LiveSchedule(targets, START, START.plusSeconds(14_400), Duration.ofSeconds(100), "live-v5");
    }
    private static LiveSchedule.Due execute(LiveSchedule schedule, Instant at, String status) {
        var due = schedule.next(at).orElseThrow();
        schedule.started(due, at);
        schedule.completed(due, status, false, Map.of(), at);
        return due;
    }
    private static List<LiveSchedule.Due> group(LiveSchedule schedule, Instant at, String status) {
        List<LiveSchedule.Due> calls = new ArrayList<>();
        calls.add(execute(schedule, at, status));
        UUID group = calls.getFirst().groupId();
        while (schedule.next(at).filter(next -> next.groupId().equals(group)).isPresent())
            calls.add(execute(schedule, at, null));
        return calls;
    }

    @Test void twentyMatchesKeepHundredSecondPhasesAndSpreadLineupsAcrossThreeEstablishedRounds() {
        var ids = targets(20);
        var schedule = schedule(ids);
        List<LiveSchedule.Due> all = new ArrayList<>();
        for (int round = 0; round < 10; round++) {
            int lineups = 0;
            for (int i = 0; i < ids.size(); i++) {
                Instant at = START.plusSeconds(round * 100L).plusNanos(i * 5_000_000_000L);
                var calls = group(schedule, at, "inprogress");
                all.addAll(calls);
                assertThat(calls).extracting(LiveSchedule.Due::eventId).containsOnly(ids.get(i));
                assertThat(calls.subList(0, 3)).extracting(LiveSchedule.Due::endpoint)
                        .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS);
                assertThat(calls).extracting(LiveSchedule.Due::dueAt).containsOnly(at);
                assertThat(calls).extracting(LiveSchedule.Due::groupId).containsOnly(calls.getFirst().groupId());
                for (int ordinal = 0; ordinal < calls.size(); ordinal++) assertThat(calls.get(ordinal).groupOrdinal()).isEqualTo(ordinal);
                lineups += (int) calls.stream().filter(call -> call.endpoint() == EVENT_LINEUPS).count();
            }
            assertThat(lineups).isEqualTo(round == 0 ? 20 : round % 3 == 2 ? 6 : 7);
        }
        assertThat(all.stream().map(LiveSchedule.Due::groupSequence).distinct()).containsExactlyElementsOf(
                java.util.stream.LongStream.range(0, 200).boxed().toList());
        for (UUID id : ids) {
            var receptions = all.stream().filter(call -> call.eventId().equals(id) && call.endpoint() == EVENT_LINEUPS)
                    .map(LiveSchedule.Due::dueAt).toList();
            for (int i = 2; i < receptions.size(); i++)
                assertThat(Duration.between(receptions.get(i - 1), receptions.get(i))).isEqualTo(Duration.ofSeconds(300));
            assertThat(schedule.familySchedules(id)).allSatisfy(family ->
                    assertThat(family.intervalSeconds()).isEqualTo(family.endpoint() == EVENT_LINEUPS ? 300 : 100));
        }
        assertThat(schedule.states()).allSatisfy(state -> assertThat(state.missedCycles()).isZero());
    }

    @Test void prematchAndKickoffUseHundredSecondsWithoutHoldingCriticalFamilies() {
        var ids = targets(1);
        var schedule = schedule(ids);
        execute(schedule, START, "notstarted");
        execute(schedule, START.plusSeconds(20), null);
        assertThat(schedule.familySchedules(ids.getFirst())).filteredOn(f -> f.endpoint() == EVENT_LINEUPS)
                .singleElement().satisfies(f -> assertThat(f.intervalSeconds()).isEqualTo(100));
        var calls = group(schedule, START.plusSeconds(100), "inprogress");
        assertThat(calls.subList(0, 3)).extracting(LiveSchedule.Due::endpoint)
                .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS);
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plusSeconds(200));
    }

    @Test void recentFinalLineupWaitsInItsOwnGroupAndAnotherMatchContinues() {
        var ids = targets(2);
        var schedule = schedule(ids);
        execute(schedule, START, "notstarted");
        execute(schedule, START.plusSeconds(60), null);
        group(schedule, START.plusSeconds(60), "inprogress");
        var finalCheck = execute(schedule, START.plusSeconds(100), "finished");
        execute(schedule, START.plusSeconds(100), null);
        execute(schedule, START.plusSeconds(100), null);
        var other = group(schedule, START.plusSeconds(150), "inprogress");
        assertThat(other.getFirst().eventId()).isEqualTo(ids.get(1));
        var finalLineup = execute(schedule, START.plusSeconds(160), null);
        assertThat(finalLineup.eventId()).isEqualTo(ids.getFirst());
        assertThat(finalLineup.endpoint()).isEqualTo(EVENT_LINEUPS);
        assertThat(finalLineup.groupOrdinal()).isZero();
        assertThat(finalLineup.groupId()).isNotEqualTo(finalCheck.groupId());
        assertThat(schedule.states().getFirst().finalComplete()).isTrue();
    }

    @Test void postponedAndOperatorStopCancelEveryRemainingFamilyWithoutStoppingOtherMatches() {
        var ids = targets(2);
        var schedule = schedule(ids);
        group(schedule, START, "inprogress");
        group(schedule, START.plusSeconds(50), "inprogress");
        execute(schedule, START.plusSeconds(100), "postponed");
        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_POSTPONED");
        assertThat(schedule.familySchedules(ids.getFirst())).allSatisfy(f -> assertThat(f.nextDueAt()).isNull());
        execute(schedule, START.plusSeconds(150), "inprogress");
        var cancelled = schedule.next(START.plusSeconds(150)).orElseThrow();
        schedule.stopEvent(ids.get(1), "STOPPED_OPERATOR");
        assertThat(schedule.mayDispatch(cancelled, START.plusSeconds(151))).isFalse();
        assertThat(schedule.terminal()).isTrue();
    }

    @Test void lateGroupsRetainTheirPhaseAndTwoExpiredRoundsStopWithoutCatchup() {
        var schedule = schedule(targets(1));
        group(schedule, START, "inprogress");
        var late = group(schedule, START.plusSeconds(115), "inprogress");
        assertThat(late.getFirst().dueAt()).isEqualTo(START.plusSeconds(100));
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plusSeconds(200));
        assertThat(schedule.next(START.plusSeconds(400))).isEmpty();
        assertThat(schedule.globalStop()).isEqualTo("STOPPED_CAPACITY");
    }

    @Test void finalReservationUsesNewGroupAndNamesCannotCollideWithHistoricalPolicy() {
        UUID campaign = UUID.randomUUID();
        var ids = targets(1);
        var v5 = new LiveSchedule(ids, START, START.plusSeconds(14_400), Duration.ofSeconds(100), "live-v5", campaign);
        var v4 = new LiveSchedule(ids, START, START.plusSeconds(14_400), Duration.ofSeconds(60), "live-v4", campaign);
        var initial = execute(v5, START, "inprogress");
        assertThat(initial.groupId()).isNotEqualTo(v4.next(START).orElseThrow().groupId());
        v5.reserveFinalCheck(ids.getFirst(), START.plusSeconds(1));
        var check = execute(v5, START.plusSeconds(100), "finished");
        assertThat(check.finalCycle()).isTrue();
        assertThat(check.groupId()).isNotEqualTo(initial.groupId());
        assertThat(check.groupSequence()).isGreaterThan(initial.groupSequence());
    }

    @Test void wrongCadenceAndTwentyFirstMatchAreRejectedInsteadOfChangingPolicy() {
        assertThatThrownBy(() -> new LiveSchedule(targets(1), START, START.plusSeconds(300), Duration.ofSeconds(60), "live-v5"))
                .hasMessage("LIVE_V5_INTERVAL_REQUIRED");
        assertThatThrownBy(() -> new LiveSchedule(targets(1), START, START.plusSeconds(300), Duration.ofSeconds(75), "live-v5"))
                .hasMessage("LIVE_V5_INTERVAL_REQUIRED");
        assertThatThrownBy(() -> schedule(targets(21))).hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        assertThatThrownBy(() -> new LiveSchedule(targets(1), START, START.plusSeconds(300), Duration.ofSeconds(100), "live-v4"))
                .hasMessage("LIVE_V4_INTERVAL_REQUIRED");
    }
}
