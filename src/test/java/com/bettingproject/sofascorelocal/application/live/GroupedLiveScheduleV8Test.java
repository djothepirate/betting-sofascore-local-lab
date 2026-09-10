package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_INCIDENTS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_LINEUPS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_STATISTICS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GroupedLiveScheduleV8Test {
    private static final Instant START = Instant.parse("2026-09-10T15:00:00Z");
    private static final UUID EVENT = new UUID(8, 1);
    private static final UUID SECOND = new UUID(8, 2);
    private static final List<SofascoreEndpointType> FOUR = List.of(
            EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private static final Duration EXCHANGE = Duration.ofMillis(600);
    private static final GroupedAdmissionProfile PROFILE = profile();
    private static final Duration GROUP_RESERVATION = GroupedLiveScheduleV8.strictGroupReservation(PROFILE);

    private static GroupedAdmissionProfile profile() {
        var costs = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var endpoint : FOUR) costs.put(endpoint, new EndpointEnvelope(Duration.ofMillis(500), Duration.ofMillis(100)));
        return new GroupedAdmissionProfile(costs, "8".repeat(64), "live-v8");
    }

    @Test
    void tenSimultaneousKickoffsKeepEveryFamilyOnItsStableSerialMinutePhase() {
        var ids = IntStream.rangeClosed(1, 10).mapToObj(index -> new UUID(8, index)).toList();
        var schedule = schedule(ids);
        Instant kickoff = START.plusSeconds(3_600);

        var first = schedule.next(START).orElseThrow();
        UUID expectedGroup = UUID.nameUUIDFromBytes(("live-v8|offline|" + START + "|"
                + START.plusSeconds(14_400) + "|" + ids.getFirst() + "|0").getBytes(StandardCharsets.UTF_8));
        assertThat(first.groupId()).isEqualTo(expectedGroup);

        for (int index = 0; index < ids.size(); index++) {
            Instant phase = START.plus(GROUP_RESERVATION.multipliedBy(index));
            assertThat(group(schedule, phase, "notstarted", kickoff, true)).isEqualTo(FOUR);
        }

        var firstWaveDepartures = new ArrayList<Instant>();
        Instant serialDeparture = kickoff;
        for (int index = 0; index < ids.size(); index++) {
            firstWaveDepartures.add(serialDeparture);
            serialDeparture = serialGroup(schedule, serialDeparture, ids.get(index), kickoff, true);
        }

        assertThat(schedule.states()).extracting(LiveSchedule.EventState::nextDueAt)
                .containsExactly(firstWaveDepartures.stream().map(departure -> departure.plusSeconds(60)).toArray(Instant[]::new));
        for (int index = 0; index < ids.size(); index++) {
            Instant departure = firstWaveDepartures.get(index).plusSeconds(60);
            serialDeparture = serialGroup(schedule, departure, ids.get(index), kickoff, true);
        }
        assertThat(schedule.states()).allSatisfy(state -> {
            assertThat(state.state()).isEqualTo("COLLECTING");
            assertThat(state.missedCycles()).isZero();
        });
    }

    @Test
    void kickoffSoonAfterTheInitialWaveKeepsEachTargetOnItsStableMinutePhase() {
        var ids = IntStream.rangeClosed(1, 10).mapToObj(index -> new UUID(8, index)).toList();
        var schedule = schedule(ids);
        Instant kickoff = START.plusSeconds(20);

        for (int index = 0; index < ids.size(); index++) {
            Instant phase = START.plus(GROUP_RESERVATION.multipliedBy(index));
            assertThat(group(schedule, phase, "notstarted", kickoff, true)).isEqualTo(FOUR);
        }

        assertThat(schedule.states()).extracting(LiveSchedule.EventState::nextDueAt)
                .containsExactly(IntStream.range(0, ids.size())
                        .mapToObj(index -> START.plusSeconds(60).plus(GROUP_RESERVATION.multipliedBy(index)))
                        .toArray(Instant[]::new));
        assertThat(schedule.next(kickoff)).isEmpty();
        assertThat(schedule.familySchedules(ids.getFirst())).satisfiesExactly(
                family -> {
                    assertThat(family.endpoint()).isEqualTo(EVENT_DETAILS);
                    assertThat(family.nextDueAt()).isEqualTo(START.plusSeconds(60));
                },
                family -> {
                    assertThat(family.endpoint()).isEqualTo(EVENT_INCIDENTS);
                    assertThat(family.nextDueAt()).isNull();
                },
                family -> {
                    assertThat(family.endpoint()).isEqualTo(EVENT_STATISTICS);
                    assertThat(family.nextDueAt()).isNull();
                },
                family -> {
                    assertThat(family.endpoint()).isEqualTo(EVENT_LINEUPS);
                    assertThat(family.nextDueAt()).isNull();
                });

        assertThat(group(schedule, START.plusSeconds(60), "inprogress", kickoff, true)).isEqualTo(FOUR);
    }

    @Test
    void kickoffPhaseNeverSchedulesASecondJ4LessThanOneMinuteAfterInitialNotstarted() {
        var offsets = List.of(Duration.ofMillis(59_999), Duration.ofSeconds(60), Duration.ofMillis(60_001));
        var expected = List.of(Duration.ofSeconds(60), Duration.ofSeconds(60), Duration.ofMillis(60_001));

        for (int index = 0; index < offsets.size(); index++) {
            var schedule = schedule(EVENT);
            Instant kickoff = START.plus(offsets.get(index));
            assertThat(group(schedule, START, "notstarted", kickoff, true)).isEqualTo(FOUR);
            assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plus(expected.get(index)));
        }
    }

    @Test
    void firstDepartureDeferredByTheFenceRebasesEveryQualifiedFamilySlotAndTheNextMinute() {
        var schedule = schedule(EVENT);
        var j4 = schedule.next(START).orElseThrow();
        Instant departure = START.plusMillis(500);
        schedule.defer(j4, departure);

        j4 = schedule.next(departure).orElseThrow();
        schedule.started(j4, departure);
        schedule.completed(j4, "inprogress", false, Map.of(), departure.plus(EXCHANGE), START, true);

        Instant now = departure.plus(EXCHANGE);
        var j5Families = List.of(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
        for (int index = 0; index < j5Families.size(); index++) {
            var endpoint = j5Families.get(index);
            var j5 = nextAtOrAfter(schedule, now);
            assertThat(j5.endpoint()).isEqualTo(endpoint);
            assertThat(j5.dueAt()).isEqualTo(departure.plus(EXCHANGE.plus(GroupedLiveScheduleV8.POST_EXCHANGE_FENCE)
                    .multipliedBy(index + 1L)));
            now = j5.dueAt();
            complete(schedule, j5, now, "inprogress", false, START, true);
        }

        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(departure.plusSeconds(60));
    }

    @Test
    void authenticatedWorkerDepartureRephasesTheNextNormalMinuteAfterLocalAuthorizationDelay() {
        var schedule = schedule(EVENT);
        var j4 = schedule.next(START).orElseThrow();
        Instant requestedAt = START.plusMillis(250);

        // Local admission succeeds first, but the worker sends the request later.
        schedule.started(j4, START);
        schedule.departed(j4, requestedAt);
        schedule.completed(j4, "inprogress", false, Map.of(), requestedAt.plus(EXCHANGE), START, true);

        Instant now = requestedAt.plus(EXCHANGE);
        for (var endpoint : List.of(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS)) {
            var j5 = nextAtOrAfter(schedule, now);
            assertThat(j5.endpoint()).isEqualTo(endpoint);
            now = j5.dueAt();
            schedule.started(j5, now);
            schedule.departed(j5, now);
            schedule.completed(j5, null, false, Map.of(), now, null, endpoint == EVENT_LINEUPS ? true : null);
        }

        var nextJ4 = schedule.next(requestedAt.plusSeconds(60)).orElseThrow();
        assertThat(nextJ4.endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(nextJ4.dueAt()).isEqualTo(requestedAt.plusSeconds(60));
        schedule.started(nextJ4, nextJ4.dueAt());
        schedule.departed(nextJ4, nextJ4.dueAt());
        assertThat(Duration.between(requestedAt, nextJ4.dueAt())).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void v8RequiresTheMinuteCadenceQualifiedSlotsAndNeverAcceptsAnEleventhTarget() {
        var ids = IntStream.rangeClosed(1, 11).mapToObj(index -> new UUID(8, index)).toList();

        assertThatIllegalArgumentException().isThrownBy(() -> new LiveSchedule(ids, START,
                START.plusSeconds(14_400), Duration.ofSeconds(60), "live-v8"))
                .withMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        assertThatIllegalArgumentException().isThrownBy(() -> new LiveSchedule(List.of(EVENT), START,
                START.plusSeconds(14_400), Duration.ofSeconds(100), "live-v8"))
                .withMessage("LIVE_V8_INTERVAL_REQUIRED");
        assertThatIllegalArgumentException().isThrownBy(() -> new LiveSchedule(List.of(EVENT), START,
                START.plusSeconds(14_400), Duration.ofSeconds(60), "live-v8"))
                .withMessage("LIVE_V8_SLOT_PROFILE_REQUIRED");
    }

    @Test
    void delayedStatusIsAnExplicitExceptionThatRebasesToTheNewKickoffAndKeepsTheTargetRunnable() {
        var schedule = schedule(EVENT);
        Instant original = START.plusSeconds(3_600);
        Instant revised = START.plusSeconds(10_800);

        assertThat(group(schedule, START, "notstarted", original, false)).isEqualTo(FOUR);
        assertThat(group(schedule, original, "delayed", revised, false)).containsExactly(EVENT_DETAILS);

        var state = schedule.states().getFirst();
        assertThat(state.state()).isEqualTo("WAITING_START");
        assertThat(state.sportStatus()).isEqualTo("delayed");
        assertThat(state.nextDueAt()).isEqualTo(revised.minusSeconds(3_600));
        assertThat(schedule.next(revised.minusSeconds(3_601))).isEmpty();
        assertThat(group(schedule, revised.minusSeconds(3_600), "notstarted", revised, false)).isEqualTo(FOUR);
    }

    @Test
    void delayedResponseJustAfterRevisedHourWarmupKeepsTheOneTimeJ5WarmupBeforeTheQuietWindow() {
        var schedule = schedule(EVENT);
        Instant original = START.plusSeconds(3_600);
        Instant revised = START.plusSeconds(7_200);

        assertThat(group(schedule, START, "notstarted", original, false)).isEqualTo(FOUR);

        Instant revisedWarmup = revised.minusSeconds(3_600);
        var delayedJ4 = nextAtOrAfter(schedule, original);
        assertThat(delayedJ4.endpoint()).isEqualTo(EVENT_DETAILS);
        schedule.started(delayedJ4, revisedWarmup);
        Instant completedJustAfterWarmup = revisedWarmup.plusMillis(1);
        schedule.completed(delayedJ4, "delayed", false, Map.of(), completedJustAfterWarmup, revised, false);

        assertThat(group(schedule, completedJustAfterWarmup, "delayed", revised, false, null,
                List.of(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS)))
                .containsExactly(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
        var state = schedule.states().getFirst();
        assertThat(state.state()).isEqualTo("WAITING_START");
        assertThat(state.sportStatus()).isEqualTo("delayed");
        assertThat(state.nextDueAt()).isBefore(revised.minusSeconds(300));
    }

    @Test
    void inPlay404IsAnExplicitFamilyAvailabilityExceptionUntilItsOwnWindowEnds() {
        var schedule = schedule(EVENT);
        assertThat(group(schedule, START, "inprogress", START, false, EVENT_STATISTICS)).isEqualTo(FOUR);

        for (int minute = 1; minute <= 5; minute++)
            assertThat(group(schedule, START.plusSeconds(minute * 60L), "inprogress", START, false, null,
                    List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_LINEUPS)))
                    .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_LINEUPS);
        assertThat(group(schedule, START.plusSeconds(360), "inprogress", START, false)).isEqualTo(FOUR);
        assertThat(schedule.states().getFirst().missedCycles()).isZero();
    }

    @Test
    void durableInPlayPressureSkipsTheLateFamiliesAndUsesAnExplicitJ4Recheck() {
        var schedule = schedule(EVENT);
        assertThat(group(schedule, START, "inprogress", START, false)).isEqualTo(FOUR);

        var j4 = nextAtOrAfter(schedule, START.plusSeconds(60));
        assertThat(j4).satisfies(due -> {
            assertThat(due.endpoint()).isEqualTo(EVENT_DETAILS);
            assertThat(due.kind()).isEqualTo("J4_CYCLE");
        });
        complete(schedule, j4, j4.dueAt(), "inprogress", false, START, false);

        var lateJ5 = nextAtOrAfter(schedule, j4.dueAt());
        assertThat(lateJ5.endpoint()).isEqualTo(EVENT_INCIDENTS);
        Instant notBefore = lateJ5.dueAt().plusSeconds(60);
        schedule.defer(lateJ5, notBefore);

        var state = schedule.states().getFirst();
        assertThat(state.state()).isEqualTo("WAITING_PRESSURE_RECHECK");
        assertThat(state.missedCycles()).isEqualTo(1);
        assertThat(state.nextDueAt()).isEqualTo(notBefore);
        assertThat(schedule.familySchedules(EVENT)).satisfiesExactly(
                family -> {
                    assertThat(family.endpoint()).isEqualTo(EVENT_DETAILS);
                    assertThat(family.nextDueAt()).isEqualTo(notBefore);
                    assertThat(family.missedCycles()).isZero();
                },
                family -> {
                    assertThat(family.endpoint()).isEqualTo(EVENT_INCIDENTS);
                    assertThat(family.nextDueAt()).isEqualTo(notBefore.plusMillis(1_100));
                    assertThat(family.missedCycles()).isEqualTo(1);
                },
                family -> {
                    assertThat(family.endpoint()).isEqualTo(EVENT_STATISTICS);
                    assertThat(family.nextDueAt()).isEqualTo(notBefore.plusMillis(2_200));
                    assertThat(family.missedCycles()).isEqualTo(1);
                },
                family -> {
                    assertThat(family.endpoint()).isEqualTo(EVENT_LINEUPS);
                    assertThat(family.nextDueAt()).isEqualTo(notBefore.plusMillis(3_300));
                    assertThat(family.missedCycles()).isEqualTo(1);
                });
        assertThat(schedule.next(notBefore.minusNanos(1))).isEmpty();

        var recheck = schedule.next(notBefore).orElseThrow();
        assertThat(recheck.endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(recheck.kind()).isEqualTo("J4_PRESSURE_RECHECK");
        assertThat(recheck.groupId()).isNotEqualTo(j4.groupId());
    }

    @Test
    void globalPressureRequalifiesEveryInPlayTargetWhoseNormalSlotFallsInsideTheHold() {
        var schedule = schedule(EVENT, SECOND);
        assertThat(group(schedule, START, "inprogress", START, false)).isEqualTo(FOUR);
        Instant secondPhase = START.plus(GROUP_RESERVATION);
        assertThat(group(schedule, secondPhase, "inprogress", START, false)).isEqualTo(FOUR);

        var firstNormal = nextAtOrAfter(schedule, START.plusSeconds(60));
        assertThat(firstNormal.eventId()).isEqualTo(EVENT);
        Instant notBefore = secondPhase.plusSeconds(60).plusMillis(1);
        schedule.defer(firstNormal, notBefore);

        assertThat(schedule.states()).allSatisfy(state -> {
            assertThat(state.state()).isEqualTo("WAITING_PRESSURE_RECHECK");
            assertThat(state.nextDueAt()).isEqualTo(notBefore);
            assertThat(state.missedCycles()).isEqualTo(1);
        });
        for (UUID eventId : List.of(EVENT, SECOND))
            assertThat(schedule.familySchedules(eventId)).extracting(family -> family.missedCycles())
                    .containsExactly(1L, 1L, 1L, 1L);

        var recheck = schedule.next(notBefore.plusMillis(1)).orElseThrow();
        assertThat(recheck.eventId()).isEqualTo(EVENT);
        assertThat(recheck.kind()).isEqualTo("J4_PRESSURE_RECHECK");
        assertThat(recheck.dueAt()).isEqualTo(notBefore);
    }

    @Test
    void isolatedTimeoutIsAnExplicitRecoveryExceptionWithoutBlockingAnotherTarget() {
        var schedule = schedule(EVENT, SECOND);
        var timed = schedule.next(START).orElseThrow();
        schedule.started(timed, START);
        schedule.deferAfterTimeout(timed, START.plusSeconds(30));

        assertThat(schedule.next(START.plusSeconds(30)).orElseThrow().eventId()).isEqualTo(SECOND);
        group(schedule, START.plusSeconds(30), "inprogress", START, false);
        assertThat(schedule.states().stream().filter(state -> state.eventId().equals(EVENT)).findFirst().orElseThrow().nextDueAt())
                .isEqualTo(START.plusSeconds(330));

        schedule.stopEvent(SECOND, "STOPPED_MANUAL");
        var retry = schedule.next(START.plusSeconds(330)).orElseThrow();
        assertThat(retry.endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(retry.groupId()).isNotEqualTo(timed.groupId());
        assertThat(retry.kind()).isEqualTo("J4_TIMEOUT_RECHECK");
    }

    @Test
    void completionBeyondTheImmutableEnvelopeLeavesTheStrictPathForAnExplicitRecheck() {
        var schedule = schedule(EVENT);
        var due = schedule.next(START).orElseThrow();
        schedule.started(due, START);
        Instant completed = START.plus(EXCHANGE).plusNanos(1);
        schedule.completed(due, "inprogress", false, Map.of(), completed, START, false);

        var state = schedule.states().getFirst();
        assertThat(state.state()).isEqualTo("WAITING_ENVELOPE_RECHECK");
        assertThat(state.missedCycles()).isEqualTo(1);
        assertThat(state.nextDueAt()).isEqualTo(completed.plusSeconds(300));
        assertThat(nextAtOrAfter(schedule, state.nextDueAt()).kind()).isEqualTo("J4_ENVELOPE_RECHECK");
    }

    private static LiveSchedule schedule(UUID... ids) {
        return schedule(List.of(ids));
    }

    private static LiveSchedule schedule(List<UUID> ids) {
        return new LiveSchedule(ids, START, START.plusSeconds(14_400), Duration.ofSeconds(60), "live-v8", null, PROFILE);
    }

    private static List<SofascoreEndpointType> group(LiveSchedule schedule, Instant now, String sport,
                                                       Instant kickoff, Boolean confirmed) {
        return group(schedule, now, sport, kickoff, confirmed, null);
    }

    private static List<SofascoreEndpointType> group(LiveSchedule schedule, Instant now, String sport,
                                                       Instant kickoff, Boolean confirmed, SofascoreEndpointType unavailable) {
        return group(schedule, now, sport, kickoff, confirmed, unavailable,
                "delayed".equals(sport) ? List.of(EVENT_DETAILS) : FOUR);
    }

    private static List<SofascoreEndpointType> group(LiveSchedule schedule, Instant now, String sport,
                                                       Instant kickoff, Boolean confirmed, SofascoreEndpointType unavailable,
                                                       List<SofascoreEndpointType> expected) {
        var endpoints = new ArrayList<SofascoreEndpointType>();
        var due = nextAtOrAfter(schedule, now);
        UUID groupId = due.groupId();
        for (int index = 0; index < expected.size(); index++) {
            if (index > 0) due = nextAtOrAfter(schedule, now);
            assertThat(due.groupId()).isEqualTo(groupId);
            now = due.dueAt();
            endpoints.add(due.endpoint());
            complete(schedule, due, now, sport, unavailable == due.endpoint(), kickoff, confirmed);
        }
        assertThat(endpoints).containsExactlyElementsOf(expected);
        return endpoints;
    }

    private static Instant serialGroup(LiveSchedule schedule, Instant departure, UUID expectedEvent,
                                       Instant kickoff, Boolean confirmed) {
        Instant now = departure;
        for (var endpoint : FOUR) {
            var due = nextAtOrAfter(schedule, now);
            assertThat(due.eventId()).isEqualTo(expectedEvent);
            assertThat(due.endpoint()).isEqualTo(endpoint);
            assertThat(due.dueAt()).isEqualTo(now);
            schedule.started(due, now);
            now = now.plus(EXCHANGE);
            schedule.completed(due, due.endpoint() == EVENT_DETAILS ? "inprogress" : null, false, Map.of(), now,
                    due.endpoint() == EVENT_DETAILS ? kickoff : null,
                    due.endpoint() == EVENT_LINEUPS ? confirmed : null);
            if (endpoint != EVENT_LINEUPS) now = now.plus(GroupedLiveScheduleV8.POST_EXCHANGE_FENCE);
        }
        return now.plus(GroupedLiveScheduleV8.POST_EXCHANGE_FENCE);
    }

    private static LiveSchedule.Due nextAtOrAfter(LiveSchedule schedule, Instant now) {
        var ready = schedule.next(now);
        if (ready.isPresent()) return ready.orElseThrow();
        Instant next = schedule.states().stream().map(LiveSchedule.EventState::nextDueAt)
                .filter(value -> value != null && value.isAfter(now)).min(Instant::compareTo).orElseThrow();
        return schedule.next(next).orElseThrow();
    }

    private static void complete(LiveSchedule schedule, LiveSchedule.Due due, Instant now, String status,
                                 boolean unavailable, Instant kickoff, Boolean confirmed) {
        schedule.started(due, now);
        schedule.completed(due, due.endpoint() == EVENT_DETAILS ? status : null, unavailable, Map.of(), now,
                due.endpoint() == EVENT_DETAILS ? kickoff : null,
                due.endpoint() == EVENT_LINEUPS ? confirmed : null);
    }
}
