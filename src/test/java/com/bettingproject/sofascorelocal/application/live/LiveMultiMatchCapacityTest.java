package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.live.LiveCadence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

/** Full-duration virtual-clock load, including the four families in finish surveillance. */
class LiveMultiMatchCapacityTest {
    private static final Instant START = Instant.parse("2026-09-07T12:00:00Z");

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 10, 25})
    void qualifiedLoadKeepsEveryFamilySpacedAndFinalizesAllMatchesBeforeFourHours(int count) {
        var properties = profile(count);
        new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE).admit(count);
        var targets = targets(count);
        Duration interval = LiveCadence.forMatches(count);
        var schedule = new LiveSchedule(targets, START, START.plus(Duration.ofHours(4)), interval);
        var starts = new HashMap<String, Instant>();
        var calls = new HashMap<UUID, Integer>();
        var finalCalls = new HashMap<UUID, Integer>();
        Instant now = START;
        UUID contiguous = null;
        int expectedFamily = 0;
        while (!schedule.terminal()) {
            assertThat(now).isBefore(START.plus(Duration.ofHours(4)));
            var next = schedule.next(now);
            if (next.isEmpty()) { now = now.plusMillis(250); continue; }
            var due = next.orElseThrow();
            String key = due.eventId() + "/" + due.endpoint();
            if (starts.containsKey(key)) assertThat(Duration.between(starts.get(key), now)).isGreaterThanOrEqualTo(interval);
            starts.put(key, now);
            if (due.endpoint() != EVENT_DETAILS) {
                if (contiguous == null) contiguous = due.eventId();
                assertThat(due.eventId()).isEqualTo(contiguous);
                assertThat(due.endpoint()).isEqualTo(LiveSchedule.J5.get(expectedFamily++));
                if (expectedFamily == 3) { expectedFamily = 0; contiguous = null; }
            } else assertThat(contiguous).isNull();
            schedule.started(due, now);
            calls.merge(due.eventId(), 1, Integer::sum);
            if (due.finalCycle() && due.endpoint() != EVENT_DETAILS) finalCalls.merge(due.eventId(), 1, Integer::sum);
            now = now.plus(properties.getRequestEnvelope()).plus(properties.getProcessingEnvelope());
            Instant finish = START.plus(Duration.ofHours(4)).minus(interval.multipliedBy(2)).minusSeconds(600);
            String status = now.isBefore(finish) ? "inprogress" : "finished";
            schedule.completed(due, due.endpoint() == EVENT_DETAILS ? status : null, false,
                    due.endpoint() == EVENT_INCIDENTS ? Map.of("synthetic-finish-signal", true) : Map.of(), now);
            now = now.plusSeconds(3);
        }
        assertThat(schedule.globalStop()).isNull();
        assertThat(schedule.states()).allSatisfy(state -> {
            assertThat(state.state()).isEqualTo("FINISHED_CONFIRMED");
            assertThat(state.finalComplete()).isTrue();
            assertThat(state.missedCycles()).isZero();
        });
        assertThat(finalCalls).hasSize(count).allSatisfy((event, total) -> assertThat(total).isEqualTo(3));
        assertThat(calls.values()).allSatisfy(total -> assertThat(total).isLessThanOrEqualTo(1000));
        assertThat(calls.values().stream().mapToInt(Integer::intValue).sum()).isLessThanOrEqualTo(3000);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 10, 25})
    void theSameSelectionWithTenSecondResponsesIsRefusedEvenWithAQualificationReference(int count) {
        var properties = profile(count);
        properties.setRequestEnvelope(Duration.ofSeconds(10));
        assertThatThrownBy(() -> new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE).admit(count))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 16, 25})
    void prematchLineupsAndStaggeredKickoffsKeepCapacityFamilySpacingAndUniqueFinalization(int count) {
        var properties = profile(count);
        new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE).admit(count);
        var targets = targets(count);
        Duration interval = LiveCadence.forMatches(count);
        var schedule = new LiveSchedule(targets, START, START.plus(Duration.ofHours(4)), interval, "live-v3");
        var starts = new HashMap<String, Instant>();
        var calls = new HashMap<UUID, Integer>();
        var finalCalls = new HashMap<UUID, Integer>();
        var prematchCalls = new HashMap<UUID, Integer>();
        var sport = new HashMap<UUID, String>();
        var identities = new java.util.HashSet<String>();
        Instant now = START;
        UUID contiguous = null;
        int expectedFamily = 0;
        while (!schedule.terminal()) {
            assertThat(now).isBefore(START.plus(Duration.ofHours(4)));
            var next = schedule.next(now);
            if (next.isEmpty()) { now = now.plusMillis(250); continue; }
            var due = next.orElseThrow();
            assertThat(identities.add(due.eventId() + "/" + due.cycle() + "/" + due.endpoint())).isTrue();
            String key = due.eventId() + "/" + due.endpoint();
            if (starts.containsKey(key)) assertThat(Duration.between(starts.get(key), now)).isGreaterThanOrEqualTo(interval);
            starts.put(key, now);
            boolean prematch = "J5_PREMATCH_LINEUPS".equals(due.kind());
            if (prematch) {
                assertThat(contiguous).isNull();
                assertThat(due.endpoint()).isEqualTo(EVENT_LINEUPS);
                assertThat(sport.get(due.eventId())).isEqualTo("notstarted");
                prematchCalls.merge(due.eventId(), 1, Integer::sum);
            } else if (due.endpoint() != EVENT_DETAILS) {
                assertThat(sport.get(due.eventId())).isIn("inprogress", "finished");
                if (contiguous == null) contiguous = due.eventId();
                assertThat(due.eventId()).isEqualTo(contiguous);
                assertThat(due.endpoint()).isEqualTo(LiveSchedule.J5.get(expectedFamily++));
                if (expectedFamily == 3) { expectedFamily = 0; contiguous = null; }
            } else assertThat(contiguous).isNull();
            schedule.started(due, now);
            calls.merge(due.eventId(), 1, Integer::sum);
            if (due.finalCycle() && due.endpoint() != EVENT_DETAILS) finalCalls.merge(due.eventId(), 1, Integer::sum);
            now = now.plus(properties.getRequestEnvelope()).plus(properties.getProcessingEnvelope());
            int targetIndex = targets.indexOf(due.eventId());
            Instant kickoff = START.plus(Duration.ofMinutes(30)).plusSeconds((long) targetIndex * 45);
            Instant finish = kickoff.plus(Duration.ofMinutes(100));
            String status = now.isBefore(kickoff) ? "notstarted" : now.isBefore(finish) ? "inprogress" : "finished";
            if (due.endpoint() == EVENT_DETAILS) sport.put(due.eventId(), status);
            boolean unavailable = prematch && prematchCalls.get(due.eventId()) == 1;
            schedule.completed(due, due.endpoint() == EVENT_DETAILS ? status : null, unavailable,
                    due.endpoint() == EVENT_INCIDENTS ? Map.of("synthetic-finish-signal", true) : Map.of(), now);
            now = now.plusSeconds(3);
        }
        assertThat(schedule.globalStop()).isNull();
        assertThat(schedule.states()).allSatisfy(state -> {
            assertThat(state.state()).isEqualTo("FINISHED_CONFIRMED");
            assertThat(state.finalComplete()).isTrue();
            assertThat(state.missedCycles()).isZero();
        });
        assertThat(prematchCalls).hasSize(count).allSatisfy((event, total) -> assertThat(total).isGreaterThanOrEqualTo(2));
        assertThat(finalCalls).hasSize(count).allSatisfy((event, total) -> assertThat(total).isEqualTo(3));
        assertThat(calls.values()).allSatisfy(total -> assertThat(total).isLessThanOrEqualTo(1000));
        assertThat(calls.values().stream().mapToInt(Integer::intValue).sum()).isLessThanOrEqualTo(3000);
    }

    private static LiveCampaignProperties profile(int count) {
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(count);
        properties.setRequestEnvelope(Duration.ofSeconds(1));
        properties.setQualificationSha256("b".repeat(64));
        return properties;
    }
    private static List<UUID> targets(int count) {
        return IntStream.range(0, count).mapToObj(i -> CanonicalEventIdentity.sofascore(17_000_001L + i).value()).toList();
    }
}
