package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

class GroupedLiveScheduleV7Test {
    private static final Instant START = Instant.parse("2030-01-01T10:00:00Z");
    private static final UUID EVENT = new UUID(7, 1), SECOND = new UUID(7, 2);
    private static final List<SofascoreEndpointType> FOUR = List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);

    @Test void farKickoffHasOneInitialGroupThenOneHourRefreshAndOnlyUnconfirmedLineups() {
        var schedule = schedule(EVENT);
        Instant kickoff = START.plusSeconds(7200);
        assertThat(group(schedule, START, "notstarted", kickoff, false)).isEqualTo(FOUR);
        assertThat(schedule.next(START.plusSeconds(3599))).isEmpty();
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plusSeconds(3600));
        assertThat(group(schedule, START.plusSeconds(3600), "notstarted", kickoff, false)).isEqualTo(FOUR);
        assertThat(group(schedule, START.plusSeconds(3900), "notstarted", kickoff, false)).containsExactly(EVENT_LINEUPS);
        assertThat(group(schedule, START.plusSeconds(4200), "notstarted", kickoff, true)).containsExactly(EVENT_LINEUPS);
        assertThat(schedule.next(kickoff.minusSeconds(1))).isEmpty();
        assertThat(schedule.familySchedules(EVENT).stream().filter(f -> f.endpoint() == EVENT_LINEUPS).findFirst().orElseThrow().nextDueAt()).isNull();
        assertThat(group(schedule, kickoff, "notstarted", kickoff, null)).containsExactly(EVENT_DETAILS);
        assertThat(group(schedule, kickoff.plusSeconds(60), "inprogress", kickoff, false)).isEqualTo(FOUR);
        assertThat(group(schedule, kickoff.plusSeconds(120), "inprogress", kickoff, false)).isEqualTo(FOUR);
    }

    @Test void simultaneousKickoffRestoresThreeDistinctMinutePhasesWithoutAnImmediateCatchup() {
        UUID third = new UUID(7, 3);
        var schedule = schedule(EVENT, SECOND, third); Instant kickoff = START.plusSeconds(3600);
        for (int index=0; index<3; index++)
            assertThat(group(schedule, START.plusSeconds(index*20L), "notstarted", kickoff, true)).isEqualTo(FOUR);
        for (int index=0; index<3; index++)
            assertThat(group(schedule, kickoff, "inprogress", kickoff, true)).isEqualTo(FOUR);
        assertThat(schedule.states()).extracting(LiveSchedule.EventState::nextDueAt)
                .containsExactly(kickoff.plusSeconds(60), kickoff.plusSeconds(80), kickoff.plusSeconds(100));
        assertThat(schedule.next(kickoff.plusSeconds(59))).isEmpty();
        for (int index=0; index<3; index++)
            assertThat(group(schedule, kickoff.plusSeconds(60+index*20L), "inprogress", kickoff, true)).isEqualTo(FOUR);
        assertThat(schedule.states()).extracting(LiveSchedule.EventState::nextDueAt)
                .containsExactly(kickoff.plusSeconds(120), kickoff.plusSeconds(140), kickoff.plusSeconds(160));
    }

    @Test void fiveMinutesBeforeKickoffIsQuietEvenWhenLineupsRemainUnconfirmed() {
        var schedule = schedule(EVENT); Instant kickoff = START.plusSeconds(600);
        assertThat(group(schedule, START, "notstarted", kickoff, false)).isEqualTo(FOUR);
        assertThat(schedule.next(START.plusSeconds(300))).isEmpty();
        assertThat(schedule.next(START.plusSeconds(599))).isEmpty();
        assertThat(group(schedule, kickoff, "notstarted", kickoff, null)).containsExactly(EVENT_DETAILS);
        assertThat(schedule.next(kickoff.plusSeconds(59))).isEmpty();
        assertThat(group(schedule, kickoff.plusSeconds(60), "notstarted", kickoff, null)).containsExactly(EVENT_DETAILS);
    }

    @Test void initialGroupInsideQuietWindowIsExplicitAndNeverRepeatsBeforeKickoff() {
        var schedule = schedule(EVENT); Instant kickoff = START.plusSeconds(120);
        assertThat(group(schedule, START, "notstarted", kickoff, true)).isEqualTo(FOUR);
        assertThat(schedule.next(kickoff.minusNanos(1))).isEmpty();
        assertThat(group(schedule, kickoff, "inprogress", kickoff, true)).isEqualTo(FOUR);
    }

    @Test void sharedBudgetCrossingQuietWindowDiscardsOnlyTheUndispatchedLineupTask() {
        var schedule = schedule(EVENT); Instant kickoff = START.plusSeconds(1200);
        group(schedule, START, "notstarted", kickoff, false);
        var due = schedule.next(START.plusSeconds(300)).orElseThrow();
        assertThat(due.endpoint()).isEqualTo(EVENT_LINEUPS);
        schedule.defer(due, kickoff.minusSeconds(299));
        assertThat(schedule.next(kickoff.minusSeconds(299))).isEmpty();
        assertThat(group(schedule, kickoff, "notstarted", kickoff, null)).containsExactly(EVENT_DETAILS);
    }

    @Test void kickoffCheckRemainsDueWhenSharedHoldEndsExactlyAtOrJustAfterKickoff() {
        for (long lateness : new long[]{0, 1}) {
            var schedule = schedule(EVENT); Instant kickoff = START.plusSeconds(1200);
            group(schedule, START, "notstarted", kickoff, false);
            var due = schedule.next(START.plusSeconds(300)).orElseThrow();
            schedule.defer(due, kickoff.plusSeconds(lateness));
            assertThat(schedule.next(kickoff.minusNanos(1))).isEmpty();
            var check = schedule.next(kickoff.plusSeconds(lateness)).orElseThrow();
            assertThat(check.endpoint()).isEqualTo(EVENT_DETAILS);
            assertThat(check.kind()).isEqualTo("J4_KICKOFF_WAIT");
            assertThat(check.dueAt()).isEqualTo(kickoff);
            assertThat(group(schedule, kickoff.plusSeconds(lateness), "notstarted", kickoff, null)).containsExactly(EVENT_DETAILS);
            assertThat(schedule.next(kickoff.plusSeconds(59))).isEmpty();
        }
    }

    @Test void changedKickoffFromOneHourJ4ReplansWithoutInventingAnInProgressState() {
        var schedule = schedule(EVENT); Instant original = START.plusSeconds(7200), changed = START.plusSeconds(10800);
        group(schedule, START, "notstarted", original, false);
        group(schedule, START.plusSeconds(3600), "notstarted", changed, false);
        assertThat(schedule.states().getFirst().sportStatus()).isEqualTo("notstarted");
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plusSeconds(7200));
    }

    @Test void delayedStatusRebasesToTheRevisedKickoffWithoutStoppingTheMatch() {
        var schedule = schedule(EVENT);
        Instant original = START.plusSeconds(3600), revised = START.plusSeconds(10800);

        assertThat(group(schedule, START, "notstarted", original, false)).isEqualTo(FOUR);
        assertThat(group(schedule, original, "delayed", revised, false)).containsExactly(EVENT_DETAILS);

        var state = schedule.states().getFirst();
        assertThat(state.state()).isEqualTo("WAITING_START");
        assertThat(state.sportStatus()).isEqualTo("delayed");
        assertThat(state.nextDueAt()).isEqualTo(revised.minusSeconds(3600));
        assertThat(schedule.next(revised.minusSeconds(3601))).isEmpty();
        assertThat(group(schedule, revised.minusSeconds(3600), "notstarted", revised, false)).isEqualTo(FOUR);
    }

    @Test void delayedInsideTheLastHourOnlyChecksLineupsUntilTheRevisedKickoff() {
        var schedule = schedule(EVENT); Instant revised = START.plusSeconds(1200);

        assertThat(group(schedule, START, "delayed", revised, false)).containsExactly(EVENT_DETAILS);
        assertThat(group(schedule, START, null, revised, false)).containsExactly(EVENT_LINEUPS);
        assertThat(group(schedule, START.plusSeconds(300), null, revised, false)).containsExactly(EVENT_LINEUPS);
        assertThat(schedule.next(revised.minusSeconds(300))).isEmpty();
        assertThat(group(schedule, revised, "notstarted", revised, null)).containsExactly(EVENT_DETAILS);
    }

    @Test void delayedAtTheRevisedHourBoundaryUsesTheCompletedJ4ForOneFullRefresh() {
        var schedule = schedule(EVENT); Instant revised = START.plusSeconds(3600);

        assertThat(group(schedule, START, "delayed", revised, false)).isEqualTo(FOUR);
        assertThat(schedule.states().getFirst().sportStatus()).isEqualTo("delayed");
        assertThat(schedule.next(START.plusSeconds(299))).isEmpty();
        assertThat(schedule.next(START.plusSeconds(300)).orElseThrow().endpoint()).isEqualTo(EVENT_LINEUPS);
    }

    @Test void delayedWithoutARevisedKickoffRequiresReview() {
        var schedule = schedule(EVENT); var due = schedule.next(START).orElseThrow();

        complete(schedule, due, START, "delayed", false, null, null);

        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_REVIEW_REQUIRED");
        assertThat(schedule.next(START.plusSeconds(60))).isEmpty();
    }

    @Test void delayedAfterInProgressRemainsReviewRequired() {
        var schedule = schedule(EVENT);
        assertThat(group(schedule, START, "inprogress", START, false)).isEqualTo(FOUR);
        var due = schedule.next(START.plusSeconds(60)).orElseThrow();

        complete(schedule, due, START.plusSeconds(60), "delayed", false, START.plusSeconds(3600), null);

        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_REVIEW_REQUIRED");
    }

    @Test void lateAdmissionCannotDispatchPendingLineupsInsideQuietWindow() {
        var schedule = schedule(EVENT); Instant kickoff = START.plusSeconds(1200);
        group(schedule, START, "notstarted", kickoff, false);
        var due = schedule.next(START.plusSeconds(300)).orElseThrow();
        assertThat(schedule.mayDispatch(due, kickoff.minusSeconds(300))).isFalse();
        assertThat(schedule.next(kickoff.minusSeconds(300))).isEmpty();
        assertThat(group(schedule, kickoff, "notstarted", kickoff, null)).containsExactly(EVENT_DETAILS);
    }

    @Test void repeatedPrematchLineup404KeepsFiveMinuteChecksAndDoesNotInventConfirmation() {
        var schedule = schedule(EVENT); Instant kickoff = START.plusSeconds(2400);
        group(schedule, START, "notstarted", kickoff, false);
        for (int attempt = 1; attempt <= 3; attempt++) {
            Instant now = START.plusSeconds(attempt * 300L);
            var due = schedule.next(now).orElseThrow(); assertThat(due.endpoint()).isEqualTo(EVENT_LINEUPS);
            complete(schedule, due, now, null, true, null, null);
            assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(now.plusSeconds(300));
        }
    }

    @Test void inPlay404BackoffNeverChangesJ4AndParsedResetsOnlyItsFamily() {
        var schedule = schedule(EVENT);
        for (var endpoint : FOUR) {
            var due = schedule.next(START).orElseThrow(); assertThat(due.endpoint()).isEqualTo(endpoint);
            complete(schedule, due, START, "inprogress", endpoint == EVENT_STATISTICS, START, false);
        }
        assertThat(group(schedule, START.plusSeconds(60), "inprogress", START, false))
                .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_LINEUPS);
        assertThat(group(schedule, START.plusSeconds(300), "inprogress", START, false)).isEqualTo(FOUR);
        assertThat(group(schedule, START.plusSeconds(360), "inprogress", START, false)).isEqualTo(FOUR);
    }

    @Test void intermediateProjectionDoesNotKeepTheCurrentDeadlineForAnAlreadyServedFamily() {
        var playing = schedule(EVENT);
        complete(playing, playing.next(START).orElseThrow(), START, "inprogress", false, START, false);
        assertThat(playing.familySchedules(EVENT)).allSatisfy(family ->
                assertThat(family.nextDueAt()).isEqualTo(family.endpoint() == EVENT_DETAILS ? START.plusSeconds(60) : START));
        complete(playing, playing.next(START).orElseThrow(), START, null, false, null, null);
        assertThat(playing.familySchedules(EVENT)).allSatisfy(family ->
                assertThat(family.nextDueAt()).isEqualTo(family.endpoint() == EVENT_DETAILS || family.endpoint() == EVENT_INCIDENTS
                        ? START.plusSeconds(60) : START));

        var prematch = schedule(EVENT); Instant kickoff = START.plusSeconds(7200);
        complete(prematch, prematch.next(START).orElseThrow(), START, "notstarted", false, kickoff, false);
        assertThat(prematch.familySchedules(EVENT)).allSatisfy(family ->
                assertThat(family.nextDueAt()).isEqualTo(family.endpoint() == EVENT_DETAILS ? null : START));
        group(prematch, START, "notstarted", kickoff, false);
        assertThat(prematch.familySchedules(EVENT)).allSatisfy(family ->
                assertThat(family.nextDueAt()).isEqualTo(kickoff.minusSeconds(3600)));
    }

    @Test void longSharedHoldCountsCoalescedRoundsWithoutCatchupOrCountingUnavailableFamilyAsDue() {
        var schedule = schedule(EVENT);
        for (var endpoint : FOUR) {
            var due = schedule.next(START).orElseThrow();
            complete(schedule, due, START, "inprogress", endpoint == EVENT_STATISTICS, START, false);
        }
        var delayed = schedule.next(START.plusSeconds(60)).orElseThrow();
        schedule.defer(delayed, START.plusSeconds(250));
        assertThat(schedule.next(START.plusSeconds(249))).isEmpty();
        assertThat(group(schedule, START.plusSeconds(250), "inprogress", START, false))
                .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_LINEUPS);
        assertThat(schedule.states().getFirst().missedCycles()).isEqualTo(3);
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plusSeconds(300));
        assertThat(schedule.next(START.plusSeconds(299))).isEmpty();
        assertThat(schedule.familySchedules(EVENT)).allSatisfy(family ->
                assertThat(family.missedCycles()).isEqualTo(family.endpoint() == EVENT_STATISTICS ? 0 : 3));
        assertThat(group(schedule, START.plusSeconds(300), "inprogress", START, false)).isEqualTo(FOUR);
        assertThat(schedule.states().getFirst().missedCycles()).isEqualTo(3);
    }

    @Test void timeoutStartsNewJ4AfterFiveMinutesAndDoesNotBlockSecondMatch() {
        var schedule = schedule(EVENT, SECOND);
        var timed = schedule.next(START).orElseThrow(); schedule.started(timed, START);
        schedule.deferAfterTimeout(timed, START.plusSeconds(30));
        assertThat(schedule.next(START.plusSeconds(30)).orElseThrow().eventId()).isEqualTo(SECOND);
        group(schedule, START.plusSeconds(30), "inprogress", START, false);
        assertThat(schedule.states().stream().filter(s -> s.eventId().equals(EVENT)).findFirst().orElseThrow().nextDueAt()).isEqualTo(START.plusSeconds(330));
        schedule.stopEvent(SECOND, "STOPPED_MANUAL");
        var retry = schedule.next(START.plusSeconds(330)).orElseThrow();
        assertThat(retry.endpoint()).isEqualTo(EVENT_DETAILS); assertThat(retry.groupId()).isNotEqualTo(timed.groupId());
        assertThat(retry.kind()).isEqualTo("J4_TIMEOUT_RECHECK");
    }

    @Test void prematchTimeoutRetryWaitsForKickoffWhenFiveMinuteDelayEndsInTheQuietWindow() {
        var schedule = schedule(EVENT); Instant kickoff = START.plusSeconds(780);
        group(schedule, START, "notstarted", kickoff, false);
        var timed = schedule.next(START.plusSeconds(300)).orElseThrow();
        schedule.started(timed, START.plusSeconds(300));
        schedule.deferAfterTimeout(timed, START.plusSeconds(330));
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(kickoff);
        assertThat(schedule.next(START.plusSeconds(630))).isEmpty();
        assertThat(group(schedule, kickoff, "inprogress", kickoff, false)).isEqualTo(FOUR);
    }

    @Test void delayedAdmissionOfPrematchTimeoutRetryCannotBypassTheQuietWindow() {
        var schedule = schedule(EVENT); Instant kickoff = START.plusSeconds(1200);
        group(schedule, START, "notstarted", kickoff, false);
        var timed = schedule.next(START.plusSeconds(300)).orElseThrow();
        schedule.started(timed, START.plusSeconds(300)); schedule.deferAfterTimeout(timed, START.plusSeconds(330));
        var retry = schedule.next(START.plusSeconds(630)).orElseThrow();
        assertThat(schedule.mayDispatch(retry, START.plusSeconds(900))).isFalse();
        assertThat(schedule.next(START.plusSeconds(900))).isEmpty();
        assertThat(group(schedule, kickoff, "notstarted", kickoff, null)).containsExactly(EVENT_DETAILS);
    }

    @Test void operatorStopDuringTimeoutAcknowledgesFlightAndKeepsOtherMatchRunnable() {
        var schedule = schedule(EVENT, SECOND);
        var timed = schedule.next(START).orElseThrow(); schedule.started(timed, START);
        schedule.stopEvent(EVENT, "STOPPED_MANUAL"); schedule.deferAfterTimeout(timed, START.plusSeconds(30));
        assertThat(schedule.next(START.plusSeconds(30)).orElseThrow().eventId()).isEqualTo(SECOND);
        assertThat(schedule.states().getFirst().nextDueAt()).isNull();
        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_MANUAL");
    }

    @Test void postponedEndsOnlyItsMatchAndFinishedGetsOneFinalTriplet() {
        var schedule = schedule(EVENT, SECOND);
        var due = schedule.next(START).orElseThrow(); complete(schedule, due, START, "postponed", false, START, null);
        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_POSTPONED");
        assertThat(group(schedule, START.plusSeconds(30), "finished", START, null)).isEqualTo(FOUR);
        assertThat(schedule.terminal()).isTrue(); assertThat(schedule.states().get(1).finalComplete()).isTrue();
    }

    @Test void finalTimeoutAndDeadlineNeverRescheduleOrExtendTheWindow() {
        var schedule = schedule(EVENT);
        var j4 = schedule.next(START).orElseThrow(); complete(schedule, j4, START, "finished", false, START, null);
        var timed = schedule.next(START).orElseThrow(); schedule.started(timed, START); schedule.deferAfterTimeout(timed, START.plusSeconds(30));
        assertThat(schedule.terminal()).isTrue(); assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_ERROR");
        var ending = new LiveSchedule(List.of(EVENT), START, START.plusSeconds(60), Duration.ofSeconds(60), "live-v7");
        var other = ending.next(START).orElseThrow(); ending.started(other, START); ending.deferAfterTimeout(other, START.plusSeconds(30));
        assertThat(ending.states().getFirst().state()).isEqualTo("STOPPED_LIMIT");
    }

    private static LiveSchedule schedule(UUID... ids) {
        return new LiveSchedule(List.of(ids), START, START.plusSeconds(14400), Duration.ofSeconds(60), "live-v7");
    }
    private static List<SofascoreEndpointType> group(LiveSchedule schedule, Instant now, String sport, Instant kickoff, Boolean confirmed) {
        var endpoints = new ArrayList<SofascoreEndpointType>(); UUID id = null;
        while (true) {
            var next = schedule.next(now); if (next.isEmpty()) break;
            var due = next.orElseThrow(); if (id != null && !id.equals(due.groupId())) break;
            id = due.groupId(); endpoints.add(due.endpoint()); complete(schedule, due, now, sport, false, kickoff, confirmed);
        }
        return endpoints;
    }
    private static void complete(LiveSchedule schedule, LiveSchedule.Due due, Instant now, String status,
                                 boolean unavailable, Instant kickoff, Boolean confirmed) {
        schedule.started(due, now); schedule.completed(due, due.endpoint() == EVENT_DETAILS ? status : null,
                unavailable, Map.of(), now, due.endpoint() == EVENT_DETAILS ? kickoff : null,
                due.endpoint() == EVENT_LINEUPS ? confirmed : null);
    }
}
