package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

class GroupedLiveAdmissionSimulationTest {
    @Test
    void allTenFinishesInOneRoundDrainThirtyFinalFamiliesEvenWhenLineupsNeedSeparateGroups() {
        Instant start = Instant.parse("2030-01-01T00:00:00Z");
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        envelopes.put(EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(400)));
        envelopes.put(EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(450)));
        envelopes.put(EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(350)));
        envelopes.put(EVENT_LINEUPS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(350)));
        var profile = new GroupedAdmissionProfile(envelopes, "a".repeat(64));
        List<UUID> targets = java.util.stream.IntStream.range(1, 11).mapToObj(index -> new UUID(0, index)).toList();
        var schedule = new LiveSchedule(targets, start, start.plusSeconds(1200), Duration.ofSeconds(60), "live-v4");
        var finishedAt = new HashMap<UUID, Instant>();
        var finalCalls = new ArrayList<LiveSchedule.Due>();
        Instant now = start, lastCompletion = null;
        UUID previousGroup = null;
        int calls = 0;
        while (!schedule.terminal() && calls < 1000) {
            Optional<LiveSchedule.Due> next = schedule.next(now);
            if (next.isEmpty()) {
                Instant nextReady = schedule.states().stream().map(LiveSchedule.EventState::nextDueAt)
                        .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElseThrow();
                assertThat(nextReady).isAfter(now);
                now = nextReady;
                continue;
            }
            LiveSchedule.Due due = next.orElseThrow();
            if (!due.groupId().equals(previousGroup) && lastCompletion != null
                    && now.isBefore(lastCompletion.plusSeconds(3))) {
                now = lastCompletion.plusSeconds(3);
                continue;
            }
            long round = Duration.between(start, due.dueAt()).toSeconds() / 60;
            String status = due.endpoint() == EVENT_DETAILS ? round >= 5 ? "finished" : "inprogress" : null;
            schedule.started(due, now);
            // Round four's slower lineup request can become due for the final pass
            // after round five's faster critical group has already completed.
            Duration elapsed = due.endpoint() != EVENT_LINEUPS && round % 2 == 1
                    ? Duration.ofNanos(1) : profile.envelope(due.endpoint()).exchangeEnvelope();
            now = now.plus(elapsed);
            schedule.completed(due, status, false, Map.of(), now);
            if ("finished".equals(status)) {
                assertThat(round).as("every event first reports finished in the common nominal round").isEqualTo(5);
                assertThat(finishedAt.put(due.eventId(), now)).isNull();
            }
            if (due.finalCycle()) {
                finalCalls.add(due);
                assertThat(Duration.between(finishedAt.get(due.eventId()), now)).isLessThanOrEqualTo(Duration.ofSeconds(120));
            }
            previousGroup = due.groupId();
            lastCompletion = now;
            calls++;
        }
        assertThat(schedule.terminal()).isTrue();
        assertThat(schedule.globalStop()).isNull();
        assertThat(finishedAt).hasSize(10);
        assertThat(finalCalls).hasSize(30);
        for (UUID event : targets) {
            assertThat(finalCalls.stream().filter(due -> due.eventId().equals(event)).toList())
                    .extracting(LiveSchedule.Due::endpoint).containsExactly(EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
            assertThat(schedule.familySchedules(event)).allSatisfy(family -> assertThat(family.nextDueAt()).isNull());
        }
        assertThat(finalCalls).anyMatch(due -> due.endpoint() == EVENT_LINEUPS && due.groupOrdinal() == 0);
        assertThat(schedule.states()).allSatisfy(state -> {
            assertThat(state.finalComplete()).isTrue();
            assertThat(state.state()).isEqualTo("FINISHED_CONFIRMED");
            assertThat(state.missedCycles()).isZero();
        });
        // The production admission now includes both finalization patterns in all
        // five phases and all kickoff/duration combinations, without losing capacity.
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV4(profile)).isEqualTo(10);
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(11);
        assertThatThrownBy(() -> new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE).admitV4(11, profile))
                .hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
    }
}
