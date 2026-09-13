package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.FamilySchedule;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

/** Fake-clock policy tests: no transport, application, database or supplier. */
class GroupedLiveScheduleV6Test {
    private static final Instant START = Instant.parse("2026-09-09T12:00:00Z");
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    private LiveSchedule schedule(List<UUID> targets) {
        return new LiveSchedule(targets, START, START.plusSeconds(14400), Duration.ofSeconds(100), "live-v6");
    }

    private LiveSchedule.Due execute(LiveSchedule schedule, long seconds, String status, boolean unavailable) {
        Instant at = START.plusSeconds(seconds);
        var due = schedule.next(at).orElseThrow();
        schedule.started(due, at);
        schedule.completed(due, status, unavailable, Map.of(), at);
        return due;
    }

    private List<LiveSchedule.Due> group(LiveSchedule schedule, long seconds, String status,
                                         Set<SofascoreEndpointType> unavailable) {
        List<LiveSchedule.Due> calls = new ArrayList<>();
        calls.add(execute(schedule, seconds, status, unavailable.contains(EVENT_DETAILS)));
        UUID id = calls.getFirst().groupId();
        Optional<LiveSchedule.Due> next;
        while ((next = schedule.next(START.plusSeconds(seconds))).filter(due -> due.groupId().equals(id)).isPresent())
            calls.add(execute(schedule, seconds, null, unavailable.contains(next.orElseThrow().endpoint())));
        return calls;
    }

    private FamilySchedule family(LiveSchedule schedule, UUID event, SofascoreEndpointType endpoint) {
        return schedule.familySchedules(event).stream().filter(value -> value.endpoint() == endpoint).findFirst().orElseThrow();
    }

    @Test void statistics404BacksOffAtThreeSixAndNineHundredSecondsWithoutSuppressingJ4OrIncidents() {
        var schedule = schedule(List.of(a));
        List<Long> statisticsAt = new ArrayList<>();
        for (long at = 0; at <= 2700; at += 100) {
            var calls = group(schedule, at, "inprogress", Set.of(EVENT_STATISTICS));
            assertThat(calls).extracting(LiveSchedule.Due::endpoint).contains(EVENT_DETAILS, EVENT_INCIDENTS);
            if (calls.stream().anyMatch(due -> due.endpoint() == EVENT_STATISTICS)) statisticsAt.add(at);
        }
        assertThat(statisticsAt).containsExactly(0L, 300L, 900L, 1800L, 2700L);
        assertThat(family(schedule, a, EVENT_STATISTICS).intervalSeconds()).isEqualTo(900);
        assertThat(family(schedule, a, EVENT_DETAILS).intervalSeconds()).isEqualTo(100);
        assertThat(schedule.globalStop()).isNull();
    }

    @Test void ParsedResponseResetsOnlyItsOwnUnavailableFamily() {
        var schedule = schedule(List.of(a));
        for (long at = 0; at <= 200; at += 100) group(schedule, at, "inprogress", Set.of(EVENT_STATISTICS, EVENT_INCIDENTS));
        group(schedule, 300, "inprogress", Set.of(EVENT_INCIDENTS));
        assertThat(family(schedule, a, EVENT_STATISTICS).intervalSeconds()).isEqualTo(100);
        assertThat(family(schedule, a, EVENT_INCIDENTS).intervalSeconds()).isEqualTo(600);
        var calls = group(schedule, 400, "inprogress", Set.of(EVENT_STATISTICS));
        assertThat(calls).extracting(LiveSchedule.Due::endpoint).contains(EVENT_STATISTICS).doesNotContain(EVENT_INCIDENTS);
        assertThat(family(schedule, a, EVENT_STATISTICS).intervalSeconds()).isEqualTo(300);
        assertThat(family(schedule, a, EVENT_STATISTICS).nextDueAt()).isEqualTo(START.plusSeconds(700));
    }

    @Test void kickoffReevaluatesMissingPrematchLineupsAndInPlayBackoffStartsAtSixHundredSeconds() {
        var schedule = schedule(List.of(a));
        group(schedule, 0, "notstarted", Set.of(EVENT_LINEUPS));
        assertThat(family(schedule, a, EVENT_LINEUPS).intervalSeconds()).isEqualTo(300);
        assertThat(group(schedule, 100, "notstarted", Set.of())).extracting(LiveSchedule.Due::endpoint)
                .containsExactly(EVENT_DETAILS);
        assertThat(group(schedule, 200, "inprogress", Set.of(EVENT_LINEUPS))).extracting(LiveSchedule.Due::endpoint)
                .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
        assertThat(family(schedule, a, EVENT_LINEUPS).intervalSeconds()).isEqualTo(600);
        List<Long> retries = new ArrayList<>();
        for (long at = 300; at <= 800; at += 100)
            if (group(schedule, at, "inprogress", Set.of(EVENT_LINEUPS)).stream().anyMatch(due -> due.endpoint() == EVENT_LINEUPS))
                retries.add(at);
        assertThat(retries).containsExactly(800L);
        assertThat(family(schedule, a, EVENT_LINEUPS).intervalSeconds()).isEqualTo(900);
    }

    @Test void finishedClearsBackoffButA404FinalFamilyStillHasOneAttemptAndAnIncompleteFinalCycle() {
        var schedule = schedule(List.of(a));
        group(schedule, 0, "inprogress", Set.of(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS));
        var finalGroup = group(schedule, 100, "finished", Set.of(EVENT_STATISTICS));
        assertThat(finalGroup).extracting(LiveSchedule.Due::endpoint)
                .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
        assertThat(finalGroup.stream().filter(LiveSchedule.Due::finalCycle)).hasSize(3);
        assertThat(schedule.states().getFirst().state()).isEqualTo("FINISHED_CONFIRMED");
        assertThat(schedule.states().getFirst().finalComplete()).isFalse();
        assertThat(schedule.next(START.plusSeconds(1000))).isEmpty();
        assertThat(schedule.familySchedules(a)).allSatisfy(value -> assertThat(value.nextDueAt()).isNull());
    }

    @Test void J4404StopsOnlyThatEventInsteadOfApplyingJ5Backoff() {
        var schedule = schedule(List.of(a, b));
        execute(schedule, 0, null, true);
        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_REVIEW_REQUIRED");
        assertThat(group(schedule, 50, "inprogress", Set.of())).extracting(LiveSchedule.Due::eventId).containsOnly(b);
        assertThat(schedule.globalStop()).isNull();
    }

    @Test void aGlobalHoldPreservesThePendingIdentityAndResumesEveryEventWithoutExpiredRoundBursts() {
        var schedule = schedule(List.of(a, b));
        var due = schedule.next(START).orElseThrow();
        schedule.defer(due, START.plusSeconds(500));
        assertThat(schedule.next(START.plusSeconds(499))).isEmpty();
        assertThat(schedule.mayDispatch(due, START.plusSeconds(499))).isFalse();
        assertThat(schedule.states()).allSatisfy(value -> assertThat(value.nextDueAt()).isEqualTo(START.plusSeconds(500)));
        assertThat(schedule.next(START.plusSeconds(500))).contains(due);
        assertThat(group(schedule, 500, "inprogress", Set.of())).extracting(LiveSchedule.Due::eventId).containsOnly(a);
        assertThat(group(schedule, 500, "inprogress", Set.of())).extracting(LiveSchedule.Due::eventId).containsOnly(b);
        assertThat(schedule.next(START.plusSeconds(599))).isEmpty();
        assertThat(schedule.states()).allSatisfy(value -> {
            assertThat(value.nextDueAt()).isEqualTo(START.plusSeconds(600));
            assertThat(value.missedCycles()).isZero();
        });
        assertThat(schedule.globalStop()).isNull();
        assertThat(group(schedule, 600, "inprogress", Set.of())).extracting(LiveSchedule.Due::eventId).containsOnly(a);
        assertThat(group(schedule, 600, "inprogress", Set.of())).extracting(LiveSchedule.Due::eventId).containsOnly(b);
    }

    @Test void aDeferredContinuationKeepsItsGroupAndOrdinalAndNeverBypassesTheCampaignDeadline() {
        var schedule = schedule(List.of(a));
        var check = execute(schedule, 0, "inprogress", false);
        var incidents = schedule.next(START).orElseThrow();
        schedule.defer(incidents, START.plusSeconds(2));
        assertThat(execute(schedule, 2, null, false)).isEqualTo(incidents);
        assertThat(incidents.groupId()).isEqualTo(check.groupId());
        assertThat(incidents.groupOrdinal()).isEqualTo(1);
        var statistics = schedule.next(START.plusSeconds(2)).orElseThrow();
        schedule.defer(statistics, START.plusSeconds(15000));
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(START.plusSeconds(14400));
        assertThat(schedule.next(START.plusSeconds(14400))).isEmpty();
        assertThat(schedule.mayDispatch(statistics, START.plusSeconds(15000))).isFalse();
        assertThat(schedule.globalStop()).isEqualTo("STOPPED_LIMIT");
    }

    @Test void theNewPolicyCannotSilentlyChangeHistoricalCadenceOrAdmitAnEighthEvent() {
        var legacy = new LiveSchedule(List.of(a), START, START.plusSeconds(1000), Duration.ofSeconds(100), "live-v5");
        var legacyDue = legacy.next(START).orElseThrow();
        assertThatThrownBy(() -> legacy.defer(legacyDue, START.plusSeconds(2))).hasMessage("LIVE_DEFER_UNSUPPORTED_POLICY");
        group(legacy, 0, "inprogress", Set.of(EVENT_STATISTICS));
        assertThat(group(legacy, 100, "inprogress", Set.of())).extracting(LiveSchedule.Due::endpoint).contains(EVENT_STATISTICS);
        assertThatThrownBy(() -> schedule(java.util.stream.IntStream.range(0, 8).mapToObj(i -> UUID.randomUUID()).toList()))
                .hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        assertThatThrownBy(() -> new LiveSchedule(List.of(a), START, START.plusSeconds(1000), Duration.ofSeconds(60), "live-v6"))
                .hasMessage("LIVE_V6_INTERVAL_REQUIRED");
    }

    @Test void anEligibleFinalLineupCannotStarveBehindSixOverloadedRecurringMatches() {
        List<UUID> targets = new ArrayList<>();
        targets.add(a);
        for (int i = 0; i < 6; i++) targets.add(UUID.randomUUID());
        Instant end = START.plusSeconds(1000);
        var schedule = new LiveSchedule(targets, START, end, Duration.ofSeconds(100), "live-v6");
        execute(schedule, 0, "notstarted", false);
        execute(schedule, 80, null, false);
        for (int i = 1; i < targets.size(); i++) group(schedule, 90, "inprogress", Set.of());
        schedule.reserveFinalCheck(a, START.plusSeconds(100));
        assertThat(group(schedule, 100, "finished", Set.of())).extracting(LiveSchedule.Due::endpoint)
                .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS);
        assertThat(family(schedule, a, EVENT_LINEUPS).nextDueAt()).isEqualTo(START.plusSeconds(180));

        Instant now = START.plusSeconds(115), finalLineupStarted = null;
        Map<UUID, Integer> recurringChecks = new HashMap<>();
        int completedCalls = 0;
        // Six regular triplets of ten-second exchanges exceed the nominal100-second round.
        // Every exchange is also explicitly deferred by the shared two-second fence.
        while (!schedule.terminal() && completedCalls < 150) {
            var next = schedule.next(now);
            if (next.isEmpty()) {
                if (schedule.terminal()) break;
                now = schedule.states().stream().map(LiveSchedule.EventState::nextDueAt)
                        .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElseThrow();
                continue;
            }
            var due = next.orElseThrow();
            Instant allowed = now.plusSeconds(2);
            schedule.defer(due, allowed);
            now = allowed;
            if (!now.isBefore(end)) {
                assertThat(schedule.next(now)).isEmpty();
                break;
            }
            assertThat(schedule.next(now)).contains(due);
            schedule.started(due, now);
            if (due.eventId().equals(a)) {
                assertThat(due.endpoint()).isEqualTo(EVENT_LINEUPS);
                assertThat(due.finalCycle()).isTrue();
                assertThat(finalLineupStarted).isNull();
                finalLineupStarted = now;
            } else if (due.endpoint() == EVENT_DETAILS) {
                recurringChecks.merge(due.eventId(), 1, Integer::sum);
            }
            now = now.plusSeconds(10);
            schedule.completed(due, due.endpoint() == EVENT_DETAILS ? "inprogress" : null, false, Map.of(), now);
            completedCalls++;
        }
        assertThat(finalLineupStarted).isNotNull().isBefore(START.plusSeconds(500));
        assertThat(recurringChecks).hasSize(6);
        assertThat(recurringChecks.values()).allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(2));
        assertThat(schedule.states().getFirst().state()).isEqualTo("FINISHED_CONFIRMED");
        assertThat(schedule.states().getFirst().finalComplete()).isTrue();
        assertThat(schedule.globalStop()).isEqualTo("STOPPED_LIMIT");
        assertThat(schedule.states()).allSatisfy(state -> assertThat(state.nextDueAt()).isNull());
    }
}
