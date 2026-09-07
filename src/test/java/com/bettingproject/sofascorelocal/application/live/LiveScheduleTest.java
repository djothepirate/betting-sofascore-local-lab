package com.bettingproject.sofascorelocal.application.live;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;

class LiveScheduleTest {
    final Instant start = Instant.parse("2026-09-07T15:00:00Z");
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    LiveSchedule one() { return new LiveSchedule(List.of(a), start, start.plusSeconds(14400)); }
    LiveSchedule.Due execute(LiveSchedule s, Instant at, String status, boolean unavailable, Map<String,Boolean> signals) {
        var due = s.next(at).orElseThrow(); s.started(due, at); s.completed(due, status, unavailable, signals, at.plusMillis(1)); return due;
    }
    void cycle(LiveSchedule s, Instant at, Map<String,Boolean> signals) {
        assertThat(execute(s,at,null,false,Map.of()).endpoint()).isEqualTo(EVENT_STATISTICS);
        assertThat(execute(s,at.plusSeconds(3),null,false,signals).endpoint()).isEqualTo(EVENT_INCIDENTS);
        assertThat(execute(s,at.plusSeconds(6),null,false,Map.of()).endpoint()).isEqualTo(EVENT_LINEUPS);
    }
    @Test void delayedKickoffNeverStartsJ5BeforeJ4Proof() {
        var s=one();
        assertThat(execute(s,start,"notstarted",false,Map.of()).endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(s.next(start.plusSeconds(59))).isEmpty();
        assertThat(execute(s,start.plusSeconds(60),"notstarted",false,Map.of()).kind()).isEqualTo("J4_WAIT");
        execute(s,start.plusSeconds(120),"inprogress",false,Map.of());
        cycle(s,start.plusSeconds(121),Map.of());
        assertThat(s.next(start.plusSeconds(180))).isEmpty();
        assertThat(s.next(start.plusSeconds(181))).isPresent();
    }

    @ParameterizedTest @ValueSource(ints = {60, 90, 120, 270, 720})
    void delayedKickoffAndEveryUnavailableJ5FamilyKeepTheFrozenInterval(int seconds) {
        var s = new LiveSchedule(List.of(a), start, start.plusSeconds(14400), Duration.ofSeconds(seconds));
        execute(s, start, "notstarted", false, Map.of());
        assertThat(s.next(start.plusSeconds(seconds - 1))).isEmpty();
        execute(s, start.plusSeconds(seconds), "inprogress", false, Map.of());
        for (int family = 0; family < 3; family++)
            assertThat(execute(s, start.plusSeconds(seconds + 1 + family * 3L), null, true, Map.of()).endpoint())
                    .isEqualTo(LiveSchedule.J5.get(family));
        assertThat(s.globalStop()).isNull();
        assertThat(s.states().getFirst().state()).isEqualTo("COLLECTING");
        // The J4 fallback must not dispatch before the selected family interval either.
        assertThat(s.next(start.plusSeconds(2L * seconds))).isEmpty();
        Instant nextCycle = start.plusSeconds(2L * seconds + 1);
        if (seconds >= 300) {
            assertThat(execute(s, nextCycle, "inprogress", false, Map.of()).endpoint()).isEqualTo(EVENT_DETAILS);
            nextCycle = nextCycle.plusSeconds(3);
        }
        assertThat(execute(s, nextCycle, null, false, Map.of()).endpoint())
                .isEqualTo(EVENT_STATISTICS);
        assertThat(s.states().getFirst().missedCycles()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = SofascoreEndpointType.class, names = {"EVENT_STATISTICS", "EVENT_INCIDENTS", "EVENT_LINEUPS"})
    void delayedThirdMatchWithUnavailableFirstFamilyKeepsAllThreeOnTheirOrdinaryCycles(SofascoreEndpointType unavailableFamily) {
        UUID waiting = UUID.randomUUID();
        var schedule = new LiveSchedule(List.of(a, b, waiting), start, start.plusSeconds(1000));
        Map<UUID, List<LiveSchedule.Due>> calls = new HashMap<>();
        List<Instant> statisticsDispatches = new ArrayList<>();
        for (int second = 0; second < 240; second++) {
            Instant at = start.plusSeconds(second);
            var next = schedule.next(at);
            if (next.isEmpty()) continue;
            var due = next.orElseThrow();
            calls.computeIfAbsent(due.eventId(), ignored -> new ArrayList<>()).add(due);
            boolean delayed = due.eventId().equals(waiting);
            if (delayed && due.endpoint() == EVENT_STATISTICS) statisticsDispatches.add(at);
            boolean unavailable = delayed && due.endpoint() == unavailableFamily
                    && calls.get(waiting).stream().filter(call -> call.endpoint() == unavailableFamily).count() == 1;
            String sport = due.endpoint() == EVENT_DETAILS ? delayed && second < 120 ? "notstarted" : "inprogress" : null;
            schedule.started(due, at);
            schedule.completed(due, sport, unavailable, Map.of(), at.plusMillis(1));
        }
        assertThat(schedule.terminal()).isFalse();
        assertThat(schedule.states()).allSatisfy(event -> {
            assertThat(event.state()).isEqualTo("COLLECTING");
            assertThat(event.missedCycles()).isZero();
        });
        var delayedCalls = calls.get(waiting);
        assertThat(delayedCalls.subList(0, 3)).allSatisfy(call -> assertThat(call.endpoint()).isEqualTo(EVENT_DETAILS));
        assertThat(delayedCalls.subList(3, 6)).extracting(LiveSchedule.Due::endpoint)
                .containsExactly(EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS);
        var statistics = delayedCalls.stream().filter(call -> call.endpoint() == EVENT_STATISTICS).toList();
        assertThat(statistics).hasSize(2);
        assertThat(statistics.get(1).dueAt()).isEqualTo(statisticsDispatches.getFirst().plusSeconds(60));
        assertThat(Duration.between(statisticsDispatches.getFirst(), statisticsDispatches.getLast()))
                .isEqualTo(Duration.ofSeconds(60));
        for (UUID active : List.of(a, b)) assertThat(calls.get(active).stream()
                .filter(call -> call.endpoint() == EVENT_STATISTICS).count()).isEqualTo(4);
    }
    @Test void halftimeIsOneCheckAndDuplicateSignalDoesNotRearm() {
        var s=one(); execute(s,start,"inprogress",false,Map.of());
        cycle(s,start.plusSeconds(1),Map.of("HT",false));
        var check=execute(s,start.plusSeconds(60),"inprogress",false,Map.of());
        assertThat(check.kind()).isEqualTo("J4_SIGNAL");
        cycle(s,start.plusSeconds(61),Map.of("HT",false));
        assertThat(s.next(start.plusSeconds(120))).isEmpty();
        assertThat(s.states().getFirst().state()).isEqualTo("COLLECTING");
    }
    @Test void finalSignalKeepsJ5AndOnlyFinishedConfirmsSportWithSpacedFinalCycle() {
        var s=one(); execute(s,start,"inprogress",false,Map.of());
        cycle(s,start.plusSeconds(1),Map.of("injury90",true));
        assertThat(s.states().getFirst().state()).isEqualTo("CHECKING_FINISH");
        execute(s,start.plusSeconds(60),"inprogress",false,Map.of());
        cycle(s,start.plusSeconds(61),Map.of("injury90",true));
        execute(s,start.plusSeconds(120),"finished",false,Map.of());
        assertThat(s.next(start.plusMillis(120500))).isEmpty();
        cycle(s,start.plusSeconds(121),Map.of("injury90",true));
        assertThat(s.terminal()).isTrue();
        assertThat(s.states().getFirst().sportStatus()).isEqualTo("finished");
        assertThat(s.states().getFirst().finalComplete()).isTrue();
    }
    @Test void finalUnavailableDoesNotLoseFinishedProof() {
        var s=one(); execute(s,start,"finished",false,Map.of());
        execute(s,start.plusSeconds(1),null,true,Map.of());
        execute(s,start.plusSeconds(4),null,false,Map.of());
        execute(s,start.plusSeconds(7),null,false,Map.of());
        assertThat(s.states().getFirst().state()).isEqualTo("FINISHED_CONFIRMED");
        assertThat(s.states().getFirst().finalComplete()).isFalse();
    }
    @Test void schemaStopsOnlyFirstButTransportStopsBoth() {
        var s=new LiveSchedule(List.of(a,b),start,start.plusSeconds(1000));
        var first=s.next(start).orElseThrow(); s.started(first,start); s.failed(first,"EVENT","STOPPED_SCHEMA_INCOMPATIBLE");
        assertThat(s.next(start).orElseThrow().eventId()).isEqualTo(b);
        var second=s.next(start).orElseThrow(); s.started(second,start); s.failed(second,"CAMPAIGN","STOPPED_ERROR");
        assertThat(s.terminal()).isTrue();
    }
    @Test void individualStopDuringOrBeforeGetDoesNotReactivate() {
        var s=one(); var due=s.next(start).orElseThrow();
        s.stopEvent(a,"STOPPED_OPERATOR"); assertThat(s.mayDispatch(due,start)).isFalse();
        s=one(); due=s.next(start).orElseThrow(); s.started(due,start); s.stopEvent(a,"STOPPED_OPERATOR");
        s.completed(due,"inprogress",false,Map.of(),start.plusSeconds(1));
        assertThat(s.next(start.plusSeconds(65))).isEmpty();
        assertThat(s.states().getFirst().state()).isEqualTo("STOPPED_OPERATOR");
    }
    @Test void noSignalUsesFiveMinuteFallbackAndClockLimitNeverInventsFinished() {
        var s=one(); execute(s,start,"inprogress",false,Map.of());
        for(int minute=0;minute<5;minute++) cycle(s,start.plusSeconds(1+minute*60L),Map.of());
        assertThat(s.next(start.plusSeconds(301)).orElseThrow().kind()).isEqualTo("J4_FALLBACK");
        s.next(start.plusSeconds(14400));
        assertThat(s.states().getFirst().sportStatus()).isEqualTo("inprogress");
        assertThat(s.states().getFirst().state()).isEqualTo("STOPPED_LIMIT");
    }
    @Test void reserveOffersOneFinalJ4ButDoesNotExceedWindow() {
        var s=one(); execute(s,start,"inprogress",false,Map.of()); s.reserveFinalCheck(a,start.plusSeconds(1));
        assertThat(s.next(start.plusSeconds(59))).isEmpty();
        assertThat(execute(s,start.plusSeconds(60),"inprogress",false,Map.of()).finalCycle()).isTrue();
        assertThat(s.terminal()).isTrue(); assertThat(s.states().getFirst().state()).isEqualTo("STOPPED_LIMIT");
    }
    @Test void twoUnservedCyclesStopCapacityWithoutBurst() {
        var s=one(); execute(s,start,"inprogress",false,Map.of());
        assertThat(s.next(start.plusSeconds(122))).isEmpty();
        assertThat(s.globalStop()).isEqualTo("STOPPED_CAPACITY");
    }
    @Test void twoExpiredCyclesAlsoStopAnAlreadyStartedTripletBeforeItsNextFamily() {
        var s=one(); execute(s,start,"inprogress",false,Map.of());
        assertThat(execute(s,start.plusSeconds(1),null,false,Map.of()).endpoint()).isEqualTo(EVENT_STATISTICS);
        // Publication/storage can delay the next family while the transport watchdog remains responsive.
        assertThat(s.next(start.plusSeconds(122))).isEmpty();
        assertThat(s.globalStop()).isEqualTo("STOPPED_CAPACITY");
        assertThat(s.states().getFirst().missedCycles()).isGreaterThanOrEqualTo(2);
    }
    @Test void statusRegressionAndJ4UnavailableRequireReview() {
        var s=one(); execute(s,start,"inprogress",false,Map.of()); s.reserveFinalCheck(a,start.plusSeconds(1));
        execute(s,start.plusSeconds(60),"notstarted",false,Map.of());
        assertThat(s.states().getFirst().state()).isEqualTo("STOPPED_REVIEW_REQUIRED");
        s=one(); execute(s,start,null,true,Map.of()); assertThat(s.states().getFirst().state()).isEqualTo("STOPPED_REVIEW_REQUIRED");
    }
}
