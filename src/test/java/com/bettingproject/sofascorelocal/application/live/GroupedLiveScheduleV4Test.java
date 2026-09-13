package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

class GroupedLiveScheduleV4Test {
    private static final Instant START = Instant.parse("2026-09-08T12:00:00Z");
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    private LiveSchedule schedule(List<UUID> targets) {
        return new LiveSchedule(targets, START, START.plusSeconds(14400), Duration.ofSeconds(60), "live-v4");
    }

    private LiveSchedule.Due execute(LiveSchedule schedule, Instant at, String status, boolean unavailable) {
        LiveSchedule.Due due = schedule.next(at).orElseThrow();
        schedule.started(due, at);
        schedule.completed(due, status, unavailable, Map.of(), at);
        return due;
    }

    private List<LiveSchedule.Due> group(LiveSchedule schedule, Instant at, String status) {
        List<LiveSchedule.Due> calls = new ArrayList<>();
        calls.add(execute(schedule, at, status, false));
        UUID group = calls.getFirst().groupId();
        while (schedule.next(at).filter(next -> next.groupId().equals(group)).isPresent())
            calls.add(execute(schedule, at, null, false));
        return calls;
    }

    @Test void criticalGroupsAreStaggeredAndKeepTheNominalMinuteAtTenMatches() {
        List<UUID> targets = new ArrayList<>();
        for (int i = 0; i < 10; i++) targets.add(UUID.randomUUID());
        LiveSchedule schedule = schedule(targets);
        List<LiveSchedule.Due> all = new ArrayList<>();
        for (int round = 0; round < 11; round++) {
            int lineups = 0;
            for (int i = 0; i < targets.size(); i++) {
                Instant at = START.plusSeconds(round * 60L + i * 6L);
                List<LiveSchedule.Due> group = group(schedule, at, "inprogress");
                all.addAll(group);
                assertThat(group).extracting(LiveSchedule.Due::eventId).containsOnly(targets.get(i));
                assertThat(group.subList(0, 3)).extracting(LiveSchedule.Due::endpoint)
                        .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS);
                assertThat(group).extracting(LiveSchedule.Due::groupId).containsOnly(group.getFirst().groupId());
                assertThat(group.getFirst().dueAt()).isEqualTo(at);
                for (int ordinal = 0; ordinal < group.size(); ordinal++) assertThat(group.get(ordinal).groupOrdinal()).isEqualTo(ordinal);
                lineups += (int) group.stream().filter(call -> call.endpoint() == EVENT_LINEUPS).count();
            }
            assertThat(lineups).isEqualTo(round == 0 ? 10 : 2);
        }
        assertThat(all.stream().map(LiveSchedule.Due::groupSequence).distinct()).containsExactlyElementsOf(
                java.util.stream.LongStream.range(0, 110).boxed().toList());
        for (UUID event : targets) {
            List<Instant> lineups = all.stream().filter(call -> call.eventId().equals(event) && call.endpoint() == EVENT_LINEUPS)
                    .map(LiveSchedule.Due::dueAt).toList();
            for (int i = 1; i < lineups.size(); i++) assertThat(Duration.between(lineups.get(i - 1), lineups.get(i)))
                    .isBetween(Duration.ofSeconds(60), Duration.ofSeconds(300));
        }
        assertThat(schedule.states()).allSatisfy(state -> assertThat(state.missedCycles()).isZero());
    }

    @Test void prematchLineupsAreImmediateThenMinutePeriodicAndDoNotBlockKickoffFamilies() {
        LiveSchedule schedule = schedule(List.of(a));
        execute(schedule, START, "notstarted", false);
        execute(schedule, START.plusSeconds(10), null, true);
        var next = schedule.next(START.plusSeconds(70)).orElseThrow();
        assertThat(next.endpoint()).isEqualTo(EVENT_DETAILS);
        execute(schedule, START.plusSeconds(70), "inprogress", false);
        assertThat(execute(schedule, START.plusSeconds(70), null, false).endpoint()).isEqualTo(EVENT_INCIDENTS);
        assertThat(execute(schedule, START.plusSeconds(70), null, false).endpoint()).isEqualTo(EVENT_STATISTICS);
        assertThat(schedule.states().getFirst().state()).isEqualTo("COLLECTING");
        assertThat(schedule.globalStop()).isNull();
    }

    @Test void aRecentPrematchLineupDoesNotHoldTheTransportDuringFinalization() {
        LiveSchedule schedule = schedule(List.of(a, b));
        execute(schedule, START, "notstarted", false);
        execute(schedule, START.plusSeconds(20), null, false);
        group(schedule, START.plusSeconds(30), "inprogress");
        execute(schedule, START.plusSeconds(80), "finished", false);
        var incidents = execute(schedule, START.plusSeconds(80), null, false);
        var statistics = execute(schedule, START.plusSeconds(80), null, false);
        assertThat(incidents.endpoint()).isEqualTo(EVENT_INCIDENTS);
        assertThat(statistics.endpoint()).isEqualTo(EVENT_STATISTICS);
        assertThat(execute(schedule, START.plusSeconds(80), null, false).endpoint()).isEqualTo(EVENT_LINEUPS);
        assertThat(schedule.states().getFirst().finalComplete()).isTrue();
        assertThat(schedule.next(START.plusSeconds(90)).orElseThrow().eventId()).isEqualTo(b);
    }

    @Test void finalLineupBeforeItsMinimumSpacingUsesALaterGroupAndOtherMatchesContinue() {
        LiveSchedule schedule = schedule(List.of(a, b));
        execute(schedule, START, "notstarted", false);
        execute(schedule, START.plusSeconds(40), null, false);
        group(schedule, START.plusSeconds(40), "inprogress");
        schedule.reserveFinalCheck(a, START.plusSeconds(60));
        execute(schedule, START.plusSeconds(60), "finished", false);
        var first = execute(schedule, START.plusSeconds(60), null, false);
        execute(schedule, START.plusSeconds(60), null, false);
        assertThat(schedule.next(START.plusSeconds(90)).orElseThrow().eventId()).isEqualTo(b);
        group(schedule, START.plusSeconds(100), "inprogress");
        var finalLineup = execute(schedule, START.plusSeconds(100), null, false);
        assertThat(finalLineup.endpoint()).isEqualTo(EVENT_LINEUPS);
        assertThat(finalLineup.finalCycle()).isTrue();
        assertThat(finalLineup.groupId()).isNotEqualTo(first.groupId());
        assertThat(finalLineup.groupOrdinal()).isZero();
        assertThat(schedule.states().getFirst().finalComplete()).isTrue();
    }

    @Test void postponedAtTheNextMinuteStopsOnlyItsGroupWithoutFinalFamilies() {
        LiveSchedule schedule = schedule(List.of(a, b));
        group(schedule, START, "inprogress");
        group(schedule, START.plusSeconds(30), "inprogress");
        execute(schedule, START.plusSeconds(60), "postponed", false);
        assertThat(schedule.states().getFirst()).satisfies(state -> {
            assertThat(state.state()).isEqualTo("STOPPED_POSTPONED");
            assertThat(state.nextDueAt()).isNull();
            assertThat(state.finalComplete()).isFalse();
        });
        assertThat(schedule.next(START.plusSeconds(90)).orElseThrow().eventId()).isEqualTo(b);
        assertThat(schedule.familySchedules(a)).allSatisfy(family -> assertThat(family.nextDueAt()).isNull());
    }

    @Test void twoExpiredNominalRoundsStopRatherThanBurstAndOneLateRoundKeepsItsPhase() {
        LiveSchedule schedule = schedule(List.of(a));
        group(schedule, START, "inprogress");
        var late = group(schedule, START.plusSeconds(75), "inprogress");
        assertThat(late.getFirst().dueAt()).isEqualTo(START.plusSeconds(60));
        assertThat(schedule.states().getFirst().missedCycles()).isZero();
        assertThat(schedule.next(START.plusSeconds(240))).isEmpty();
        assertThat(schedule.globalStop()).isEqualTo("STOPPED_CAPACITY");
    }

    @Test void stopAndDurablePublicationFailureNeverResurrectOrKeepProvisionalFinalCompleteness() {
        LiveSchedule schedule = schedule(List.of(a));
        List<LiveSchedule.Due> finalCalls = group(schedule, START, "finished");
        assertThat(schedule.states().getFirst().finalComplete()).isTrue();
        schedule.failed(finalCalls.getLast(), "CAMPAIGN", "STOPPED_ERROR");
        assertThat(schedule.states().getFirst().finalComplete()).isFalse();
        assertThat(schedule.globalStop()).isEqualTo("STOPPED_ERROR");
        assertThat(schedule.familySchedules(a)).allSatisfy(family -> assertThat(family.nextDueAt()).isNull());
    }

    @Test void legacyDueRetainsItsConstructorAndV4RefusesASlowedInterval() {
        LiveSchedule.Due legacy = new LiveSchedule.Due(a, EVENT_DETAILS, 0, "J4_INITIAL", START, false);
        assertThat(legacy.groupId()).isNull();
        assertThat(legacy.groupSequence()).isEqualTo(-1);
        assertThatThrownBy(() -> new LiveSchedule(List.of(a), START, START.plusSeconds(300), Duration.ofSeconds(90), "live-v4"))
                .hasMessage("LIVE_V4_INTERVAL_REQUIRED");
    }

    @Test void variableExchangeTimesDoNotShiftTheNextNominalGroupOrInsertCriticalSleeps() {
        LiveSchedule schedule = schedule(List.of(a));
        LiveSchedule.Due j4 = schedule.next(START).orElseThrow();
        schedule.started(j4, START);
        schedule.completed(j4, "inprogress", false, Map.of(), START.plusMillis(400));
        LiveSchedule.Due incident = schedule.next(START.plusMillis(400)).orElseThrow();
        schedule.started(incident, START.plusMillis(400));
        schedule.completed(incident, null, false, Map.of(), START.plusMillis(700));
        execute(schedule, START.plusMillis(700), null, false);
        execute(schedule, START.plusMillis(1000), null, false);

        assertThat(schedule.familySchedules(a)).filteredOn(family -> family.endpoint() == EVENT_DETAILS)
                .singleElement().satisfies(family -> assertThat(family.nextDueAt()).isEqualTo(START.plusSeconds(60)));
        execute(schedule, START.plusSeconds(60), "inprogress", false);
        // The previous incident request began at 00.400, but this group's faster
        // J4 permits 60.000. Sixty seconds is the nominal round, not a forced pause.
        assertThat(execute(schedule, START.plusSeconds(60), null, false).endpoint()).isEqualTo(EVENT_INCIDENTS);
        execute(schedule, START.plusSeconds(60), null, false);
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plusSeconds(120));
        assertThat(schedule.familySchedules(a)).filteredOn(family -> family.endpoint() == EVENT_LINEUPS)
                .singleElement().satisfies(family -> {
                    assertThat(family.intervalSeconds()).isEqualTo(300);
                    assertThat(family.nextDueAt()).isEqualTo(START.plusSeconds(300));
                });
    }

    @Test void aFinalBudgetCheckAfterJ4GetsADistinctDurableCycleAndCampaignScopedGroup() {
        UUID campaign = UUID.randomUUID();
        LiveSchedule schedule = new LiveSchedule(List.of(a), START, START.plusSeconds(14400), Duration.ofSeconds(60), "live-v4", campaign);
        LiveSchedule.Due initial = execute(schedule, START, "inprogress", false);
        schedule.reserveFinalCheck(a, START.plusSeconds(1));
        LiveSchedule.Due finalCheck = execute(schedule, START.plusSeconds(60), "finished", false);
        assertThat(finalCheck.cycle()).isGreaterThan(initial.cycle());
        assertThat(finalCheck.groupSequence()).isEqualTo(finalCheck.cycle());
        assertThat(finalCheck.groupOrdinal()).isZero();
        assertThat(finalCheck.finalCycle()).isTrue();

        LiveSchedule other = new LiveSchedule(List.of(a), START, START.plusSeconds(14400), Duration.ofSeconds(60), "live-v4", UUID.randomUUID());
        assertThat(other.next(START).orElseThrow().groupId()).isNotEqualTo(initial.groupId());
    }

    @Test void unavailableFinalFamilyLeavesExplicitlyIncompleteFinalizationAndWindowStopsFurtherCalls() {
        LiveSchedule schedule = schedule(List.of(a));
        execute(schedule, START, "finished", false);
        execute(schedule, START, null, true);
        execute(schedule, START, null, false);
        execute(schedule, START, null, false);
        assertThat(schedule.states().getFirst().state()).isEqualTo("FINISHED_CONFIRMED");
        assertThat(schedule.states().getFirst().finalComplete()).isFalse();

        schedule = schedule(List.of(a));
        group(schedule, START, "notstarted");
        assertThat(schedule.next(START.plusSeconds(14400))).isEmpty();
        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_LIMIT");
        assertThat(schedule.states().getFirst().sportStatus()).isEqualTo("notstarted");
    }

    private void delayedGroup(LiveSchedule schedule, Instant at, long elapsedSeconds) {
        execute(schedule, at, "inprogress", false);
        UUID groupId = schedule.next(at).orElseThrow().groupId();
        while (true) {
            LiveSchedule.Due due = schedule.next(at).orElseThrow();
            schedule.started(due, at);
            // The last family of the current group carries the synthetic slowdown.
            boolean last = due.endpoint() == EVENT_LINEUPS
                    || due.endpoint() == EVENT_STATISTICS && at.isAfter(START);
            schedule.completed(due, null, false, Map.of(), last ? at.plusSeconds(elapsedSeconds) : at);
            if (last) break;
            assertThat(schedule.next(at).orElseThrow().groupId()).isEqualTo(groupId);
        }
    }

    @Test void oneEightySecondSpikeIsCoalescedWithoutBurstAndCanRecover() {
        LiveSchedule schedule = schedule(List.of(a));
        delayedGroup(schedule, START, 80);
        assertThat(schedule.globalStop()).isNull();
        assertThat(schedule.states().getFirst().missedCycles()).isEqualTo(1);
        assertThat(schedule.next(START.plusSeconds(80))).isEmpty();
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plusSeconds(120));
        group(schedule, START.plusSeconds(120), "inprogress");
        assertThat(schedule.globalStop()).isNull();
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plusSeconds(180));
    }

    @Test void twoConsecutiveSlowGroupsStopTheCampaignWithoutLengtheningItsPeriod() {
        LiveSchedule schedule = schedule(List.of(a));
        delayedGroup(schedule, START, 80);
        delayedGroup(schedule, START.plusSeconds(120), 80);
        assertThat(schedule.globalStop()).isEqualTo("STOPPED_CAPACITY");
        assertThat(schedule.states().getFirst().missedCycles()).isEqualTo(2);
        assertThat(schedule.next(START.plusSeconds(240))).isEmpty();
    }

    @Test void operatorStopBetweenFamiliesOrInsideJ4NeverDispatchesTheRemainingGroup() {
        LiveSchedule schedule = schedule(List.of(a, b));
        execute(schedule, START, "inprogress", false);
        LiveSchedule.Due cancelled = schedule.next(START).orElseThrow();
        schedule.stopEvent(a, "STOPPED_OPERATOR");
        assertThat(schedule.mayDispatch(cancelled, START)).isFalse();
        LiveSchedule.Due other = schedule.next(START.plusSeconds(30)).orElseThrow();
        schedule.started(other, START.plusSeconds(30));
        schedule.stopEvent(b, "STOPPED_OPERATOR");
        schedule.completed(other, "inprogress", false, Map.of(), START.plusSeconds(31));
        assertThat(schedule.terminal()).isTrue();
        assertThat(schedule.next(START.plusSeconds(60))).isEmpty();
        assertThat(schedule.states()).extracting(LiveSchedule.EventState::state).containsOnly("STOPPED_OPERATOR");
    }
}
