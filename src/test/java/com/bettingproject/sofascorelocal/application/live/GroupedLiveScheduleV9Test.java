package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts.BooleanFact;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts.DetailIdFact;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts.StatusDescription;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_INCIDENTS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_LINEUPS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_STATISTICS;
import static org.assertj.core.api.Assertions.assertThat;

/** Contract tests for V9 J4 control gates. All observations are synthetic and local. */
class GroupedLiveScheduleV9Test {
    private static final Instant START = Instant.parse("2026-09-11T09:00:00Z");
    private static final Instant KICKOFF = START.plusSeconds(3_600);
    private static final UUID EVENT = new UUID(9, 1);
    private static final GroupedAdmissionProfile PROFILE = profile();

    @Test
    void finalResultOnlyStopsAfterTheFirstJ4WithoutOfferingAJ5Family() {
        var schedule = schedule();
        var first = nextAtOrAfter(schedule, START);

        completeJ4(schedule, first, START, "inprogress", controls(BooleanFact.TRUE, DetailIdFact.ONE,
                BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER));

        assertThat(schedule.states()).singleElement().satisfies(state -> {
            assertThat(state.state()).isEqualTo("STOPPED_FINAL_RESULT_ONLY");
            assertThat(state.nextDueAt()).isNull();
        });
        assertThat(schedule.next(START.plusSeconds(1))).isEmpty();
        assertThat(schedule.familySchedules(EVENT)).allSatisfy(family -> assertThat(family.nextDueAt()).isNull());
    }

    @Test
    void notStartedDoesNotOfferStatisticsOrIncidentsButStillOffersPermittedLineups() {
        var schedule = schedule();
        completeJ4(schedule, nextAtOrAfter(schedule, START), START, "notstarted", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.ABSENT, BooleanFact.TRUE, StatusDescription.OTHER));

        assertThat(nextAtOrAfter(schedule, START).endpoint()).isEqualTo(EVENT_LINEUPS);
        assertThat(schedule.familySchedules(EVENT)).satisfiesExactly(
                family -> assertThat(family.endpoint()).isEqualTo(EVENT_DETAILS),
                family -> { assertThat(family.endpoint()).isEqualTo(EVENT_INCIDENTS); assertThat(family.nextDueAt()).isNull(); },
                family -> { assertThat(family.endpoint()).isEqualTo(EVENT_STATISTICS); assertThat(family.nextDueAt()).isNull(); },
                family -> { assertThat(family.endpoint()).isEqualTo(EVENT_LINEUPS); assertThat(family.nextDueAt()).isNotNull(); });
    }

    @Test
    void explicitNoPlayerStatisticsPreventsLineupsWithoutInventingOtherPrematchJ5Calls() {
        var schedule = schedule();
        completeJ4(schedule, nextAtOrAfter(schedule, START), START, "notstarted", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.FALSE, BooleanFact.FALSE, StatusDescription.OTHER));

        assertThat(schedule.next(START.plusSeconds(1))).isEmpty();
        assertThat(schedule.familySchedules(EVENT)).allSatisfy(family -> {
            if (family.endpoint() != EVENT_DETAILS) assertThat(family.nextDueAt()).isNull();
        });
    }

    @Test
    void delayedDoesNotOfferStatisticsOrIncidentsButKeepsAnAllowedLineupsWarmup() {
        var schedule = schedule();

        completeJ4(schedule, nextAtOrAfter(schedule, START), START, "delayed", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER));

        assertThat(nextAtOrAfter(schedule, START).endpoint()).isEqualTo(EVENT_LINEUPS);
        assertThat(schedule.familySchedules(EVENT)).satisfiesExactly(
                family -> assertThat(family.endpoint()).isEqualTo(EVENT_DETAILS),
                family -> { assertThat(family.endpoint()).isEqualTo(EVENT_INCIDENTS); assertThat(family.nextDueAt()).isNull(); },
                family -> { assertThat(family.endpoint()).isEqualTo(EVENT_STATISTICS); assertThat(family.nextDueAt()).isNull(); },
                family -> { assertThat(family.endpoint()).isEqualTo(EVENT_LINEUPS); assertThat(family.nextDueAt()).isNotNull(); });
    }

    @Test
    void postponedStopsImmediatelyWithoutOfferingAnyJ5Family() {
        var schedule = schedule();

        completeJ4(schedule, nextAtOrAfter(schedule, START), START, "postponed", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER));

        assertThat(schedule.states()).singleElement().satisfies(state -> {
            assertThat(state.state()).isEqualTo("STOPPED_POSTPONED");
            assertThat(state.nextDueAt()).isNull();
        });
        assertThat(schedule.next(START.plusSeconds(1))).isEmpty();
        assertThat(schedule.familySchedules(EVENT)).allSatisfy(family -> assertThat(family.nextDueAt()).isNull());
    }

    @Test
    void inProgressWithDetailIdOneRefreshesAllThreeJ5Families() {
        var schedule = schedule();
        completeJ4(schedule, nextAtOrAfter(schedule, START), START, "inprogress", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER));

        assertThat(completeEndpoints(schedule, START, 3).endpoints())
                .containsExactly(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    }

    @Test
    void inProgressWithExplicitlyUnavailablePlayerStatisticsStillRefreshesIncidentsAndStatistics() {
        var schedule = schedule();
        completeJ4(schedule, nextAtOrAfter(schedule, START), START, "inprogress", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.FALSE, BooleanFact.FALSE, StatusDescription.OTHER));

        assertThat(completeEndpoints(schedule, START, 2).endpoints())
                .containsExactly(EVENT_INCIDENTS, EVENT_STATISTICS);
        assertThat(schedule.familySchedules(EVENT)).satisfiesExactly(
                family -> assertThat(family.endpoint()).isEqualTo(EVENT_DETAILS),
                family -> assertThat(family.endpoint()).isEqualTo(EVENT_INCIDENTS),
                family -> assertThat(family.endpoint()).isEqualTo(EVENT_STATISTICS),
                family -> { assertThat(family.endpoint()).isEqualTo(EVENT_LINEUPS); assertThat(family.nextDueAt()).isNull(); });
    }

    @Test
    void interruptedPermitsTheFinalJ5TrioBeforeConfirmingTheEnd() {
        var schedule = schedule();
        completeJ4(schedule, nextAtOrAfter(schedule, START), START, "interrupted", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER));

        assertThat(completeEndpoints(schedule, START, 3).endpoints())
                .containsExactly(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
        assertThat(schedule.states()).singleElement().satisfies(state -> {
            assertThat(state.state()).isEqualTo("FINISHED_CONFIRMED");
            assertThat(state.finalComplete()).isTrue();
        });
    }

    @Test
    void nullOrOtherDetailIdStopsSafelyBeforeOfferingAnyJ5Family() {
        for (DetailIdFact detailId : List.of(DetailIdFact.NULL, DetailIdFact.OTHER)) {
            var schedule = schedule();

            completeJ4(schedule, nextAtOrAfter(schedule, START), START, "inprogress", controls(BooleanFact.FALSE,
                    detailId, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER));

            assertThat(schedule.states()).singleElement().satisfies(state -> {
                assertThat(state.state()).isEqualTo("STOPPED_DETAIL_ID_UNSUPPORTED");
                assertThat(state.nextDueAt()).isNull();
            });
            assertThat(schedule.next(START.plusSeconds(1))).isEmpty();
            assertThat(schedule.familySchedules(EVENT)).allSatisfy(family -> assertThat(family.nextDueAt()).isNull());
        }
    }

    @Test
    void missingDetailIdSuppressesOnlyStatisticsAfterThreeConsecutiveObservedHttp404sAndUsesOneTerminalRetry() {
        var schedule = schedule();
        LiveJ4ControlFacts controls = controls(BooleanFact.FALSE, DetailIdFact.ABSENT,
                BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER);
        Instant now = START;

        for (int cycle = 0; cycle < 3; cycle++) {
            var j4 = nextAtOrAfter(schedule, now);
            assertThat(j4.endpoint()).isEqualTo(EVENT_DETAILS);
            now = j4.dueAt();
            completeJ4(schedule, j4, now, "inprogress", controls);
            now = completeNormalJ5RoundWithStatistics404(schedule, now);
        }

        var fourthJ4 = nextAtOrAfter(schedule, now);
        now = fourthJ4.dueAt();
        completeJ4(schedule, fourthJ4, now, "inprogress", controls);
        var suppressedRound = completeEndpoints(schedule, now, 2);
        assertThat(suppressedRound.endpoints()).containsExactly(EVENT_INCIDENTS, EVENT_LINEUPS);

        // Continue to a terminal J4. The special final statistics call is allowed exactly once
        // despite normal statistics having been suppressed after the third observed HTTP 404.
        var terminal = nextAtOrAfter(schedule, suppressedRound.completedAt());
        now = terminal.dueAt();
        completeJ4(schedule, terminal, now, "finished", controls);
        var terminalRound = completeEndpoints(schedule, now, 3);
        assertThat(terminalRound.endpoints()).containsExactly(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
        assertThat(schedule.states()).singleElement().satisfies(state -> {
            assertThat(state.state()).isEqualTo("FINISHED_CONFIRMED");
            assertThat(state.nextDueAt()).isNull();
        });
    }

    @Test
    void fourthHttp404OnTheSingleTerminalStatisticsRecheckConfirmsTheEndButKeepsFinalCompletenessFalse() {
        var schedule = schedule();
        LiveJ4ControlFacts controls = controls(BooleanFact.FALSE, DetailIdFact.ABSENT,
                BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER);
        Instant now = START;

        for (int cycle = 0; cycle < 3; cycle++) {
            var j4 = nextAtOrAfter(schedule, now);
            now = j4.dueAt();
            completeJ4(schedule, j4, now, "inprogress", controls);
            now = completeNormalJ5RoundWithStatistics404(schedule, now);
        }

        var terminal = nextAtOrAfter(schedule, now);
        now = terminal.dueAt();
        completeJ4(schedule, terminal, now, "finished", controls);

        var incidents = nextAtOrAfter(schedule, now);
        assertThat(incidents.endpoint()).isEqualTo(EVENT_INCIDENTS);
        now = completeJ5(schedule, incidents, false, null);
        var statistics = nextAtOrAfter(schedule, now);
        assertThat(statistics.endpoint()).isEqualTo(EVENT_STATISTICS);
        now = completeJ5(schedule, statistics, true, "HTTP_404");
        var lineups = nextAtOrAfter(schedule, now);
        assertThat(lineups.endpoint()).isEqualTo(EVENT_LINEUPS);
        now = completeJ5(schedule, lineups, false, null);

        assertThat(schedule.states()).singleElement().satisfies(state -> {
            assertThat(state.state()).isEqualTo("FINISHED_CONFIRMED");
            assertThat(state.finalComplete()).isFalse();
            assertThat(state.nextDueAt()).isNull();
        });
        assertThat(schedule.next(now.plusSeconds(1))).isEmpty();
        assertThat(schedule.familySchedules(EVENT)).allSatisfy(family -> assertThat(family.nextDueAt()).isNull());
    }

    @Test
    void aNon404UnavailableStatisticsResultBreaksTheConsecutive404Sequence() {
        var schedule = schedule();
        LiveJ4ControlFacts controls = controls(BooleanFact.FALSE, DetailIdFact.ABSENT,
                BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER);
        Instant now = START;

        for (int cycle = 0; cycle < 2; cycle++) {
            var j4 = nextAtOrAfter(schedule, now); now = j4.dueAt();
            completeJ4(schedule, j4, now, "inprogress", controls);
            now = completeNormalJ5RoundWithStatistics404(schedule, now);
        }
        var resetJ4 = nextAtOrAfter(schedule, now); now = resetJ4.dueAt();
        completeJ4(schedule, resetJ4, now, "inprogress", controls);
        now = completeJ5(schedule, nextAtOrAfter(schedule, now), false, null);
        var statistics = nextAtOrAfter(schedule, now);
        assertThat(statistics.endpoint()).as("scheduled families %s", schedule.familySchedules(EVENT))
                .isEqualTo(EVENT_STATISTICS);
        now = completeJ5(schedule, statistics, true, "HTTP_500");
        now = completeJ5(schedule, nextAtOrAfter(schedule, now), false, null);

        var nextJ4 = nextAtOrAfter(schedule, now); now = nextJ4.dueAt();
        completeJ4(schedule, nextJ4, now, "inprogress", controls);
        assertThat(completeEndpoints(schedule, now, 3).endpoints())
                .containsExactly(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    }

    @Test
    void halftimeStopsEveryFamilyForFifteenMinutesThenUsesJ4OnlyUntilSecondHalf() {
        var schedule = schedule();
        completeJ4(schedule, nextAtOrAfter(schedule, START), START, "inprogress", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.HALFTIME));

        assertThat(schedule.states()).singleElement().satisfies(state -> {
            assertThat(state.state()).isEqualTo("WAITING_HALFTIME_RECHECK");
            assertThat(state.nextDueAt()).isEqualTo(START.plusSeconds(900));
        });
        assertThat(schedule.next(START.plusSeconds(899))).isEmpty();
        assertThat(schedule.familySchedules(EVENT)).allSatisfy(family -> {
            if (family.endpoint() != EVENT_DETAILS) assertThat(family.nextDueAt()).isNull();
        });

        Instant recheckAt = START.plusSeconds(900);
        var recheck = nextAtOrAfter(schedule, recheckAt);
        assertThat(recheck.endpoint()).isEqualTo(EVENT_DETAILS);
        completeJ4(schedule, recheck, recheckAt, "inprogress", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.HALFTIME));
        assertThat(schedule.states()).singleElement().satisfies(state -> {
            assertThat(state.state()).isEqualTo("WAITING_HALFTIME_RECHECK");
            assertThat(state.nextDueAt()).isEqualTo(recheckAt.plusSeconds(60));
        });

        Instant secondHalfAt = recheckAt.plusSeconds(60);
        var secondHalf = nextAtOrAfter(schedule, secondHalfAt);
        completeJ4(schedule, secondHalf, secondHalfAt, "inprogress", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.SECOND_HALF));
        assertThat(completeEndpoints(schedule, secondHalfAt, 3).endpoints())
                .containsExactly(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    }

    @Test
    void terminalStatusDuringTheHalftimeRecheckTakesPriorityOverTheOneMinuteWait() {
        var schedule = schedule();
        completeJ4(schedule, nextAtOrAfter(schedule, START), START, "inprogress", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.HALFTIME));

        Instant recheckAt = START.plusSeconds(900);
        completeJ4(schedule, nextAtOrAfter(schedule, recheckAt), recheckAt, "canceled", controls(BooleanFact.FALSE,
                DetailIdFact.ONE, BooleanFact.TRUE, BooleanFact.TRUE, StatusDescription.OTHER));
        assertThat(completeEndpoints(schedule, recheckAt, 3).endpoints())
                .containsExactly(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    }

    private static GroupedAdmissionProfile profile() {
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var endpoint : List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS))
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(500), Duration.ofMillis(100)));
        return new GroupedAdmissionProfile(envelopes, "9".repeat(64), "live-v9");
    }

    private static LiveSchedule schedule() {
        return new LiveSchedule(List.of(EVENT), START, START.plus(Duration.ofHours(4)), Duration.ofSeconds(60),
                "live-v9", null, PROFILE);
    }

    private static LiveJ4ControlFacts controls(BooleanFact finalResultOnly, DetailIdFact detailId,
                                                BooleanFact eventPlayerStatistics,
                                                BooleanFact tournamentPlayerStatistics,
                                                StatusDescription statusDescription) {
        return new LiveJ4ControlFacts(finalResultOnly, detailId, eventPlayerStatistics,
                tournamentPlayerStatistics, statusDescription);
    }

    private static Instant completeNormalJ5RoundWithStatistics404(LiveSchedule schedule, Instant now) {
        now = completeJ5(schedule, nextAtOrAfter(schedule, now), false, null);
        var statistics = nextAtOrAfter(schedule, now);
        assertThat(statistics.endpoint()).isEqualTo(EVENT_STATISTICS);
        now = completeJ5(schedule, statistics, true, "HTTP_404");
        return completeJ5(schedule, nextAtOrAfter(schedule, now), false, null);
    }

    private static CompletedEndpoints completeEndpoints(LiveSchedule schedule, Instant now, int count) {
        var endpoints = new ArrayList<SofascoreEndpointType>();
        for (int index = 0; index < count; index++) {
            var due = nextAtOrAfter(schedule, now);
            endpoints.add(due.endpoint());
            now = completeJ5(schedule, due, false, null);
        }
        return new CompletedEndpoints(List.copyOf(endpoints), now);
    }

    private static void completeJ4(LiveSchedule schedule, LiveSchedule.Due due, Instant at, String status,
                                   LiveJ4ControlFacts controls) {
        assertThat(due.endpoint()).isEqualTo(EVENT_DETAILS);
        schedule.started(due, at);
        schedule.completed(due, status, false, Map.of(), at, KICKOFF, null, controls, null);
    }

    private static Instant completeJ5(LiveSchedule schedule, LiveSchedule.Due due, boolean unavailable, String code) {
        assertThat(due.endpoint()).isNotEqualTo(EVENT_DETAILS);
        Instant at = due.dueAt();
        schedule.started(due, at);
        schedule.completed(due, null, unavailable, Map.of(), at, null,
                due.endpoint() == EVENT_LINEUPS ? Boolean.TRUE : null, null, code);
        return at;
    }

    private static LiveSchedule.Due nextAtOrAfter(LiveSchedule schedule, Instant now) {
        return schedule.next(now).orElseGet(() -> {
            Instant next = schedule.states().stream().map(LiveSchedule.EventState::nextDueAt)
                    .filter(value -> value != null && !value.isBefore(now)).min(Instant::compareTo).orElseThrow();
            return schedule.next(next).orElseThrow();
        });
    }

    private record CompletedEndpoints(List<SofascoreEndpointType> endpoints, Instant completedAt) { }
}
