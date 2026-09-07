package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
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
    @ValueSource(ints = {2, 3})
    void qualifiedLoadKeepsEveryFamilySpacedAndFinalizesAllMatchesBeforeFourHours(int count) {
        var properties = profile(count);
        new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE).admit(count);
        var targets = targets(count);
        var schedule = new LiveSchedule(targets, START, START.plus(Duration.ofHours(4)));
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
            if (starts.containsKey(key)) assertThat(Duration.between(starts.get(key), now)).isGreaterThanOrEqualTo(Duration.ofSeconds(60));
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
            String status = now.isBefore(START.plus(Duration.ofMinutes(230))) ? "inprogress" : "finished";
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
    @ValueSource(ints = {2, 3})
    void theSameSelectionWithTenSecondResponsesIsRefusedEvenWithAQualificationReference(int count) {
        var properties = profile(count);
        properties.setRequestEnvelope(Duration.ofSeconds(10));
        assertThatThrownBy(() -> new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE).admit(count))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
    }

    private static LiveCampaignProperties profile(int count) {
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(count);
        properties.setRequestEnvelope(Duration.ofMillis(count == 2 ? 3000 : 750));
        properties.setQualificationSha256("b".repeat(64));
        return properties;
    }
    private static List<UUID> targets(int count) {
        return IntStream.range(0, count).mapToObj(i -> CanonicalEventIdentity.sofascore(17_000_001L + i).value()).toList();
    }
}
