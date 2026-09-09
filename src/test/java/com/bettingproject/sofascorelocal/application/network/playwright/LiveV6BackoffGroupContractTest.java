package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.application.live.LiveSchedule;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup.Phase.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

/** Production scheduler and transport group contract, with a supplied clock and no I/O. */
class LiveV6BackoffGroupContractTest {
    @Test
    void statistics404DoesNotPreventTheDueLineupFromBeingDispatchedAtFiveMinutes() {
        Instant start = Instant.parse("2026-09-09T15:40:00Z");
        UUID campaign = UUID.randomUUID(), event = UUID.randomUUID();
        long providerEvent = 16921300L;
        var schedule = new LiveSchedule(List.of(event), start, start.plusSeconds(14400),
                Duration.ofSeconds(100), "live-v6", campaign);
        var tracker = new LiveProviderGroupTracker(campaign, LiveProviderGroupTracker.Authority.LIVE_V6);
        List<SofascoreEndpointType> atFiveMinutes = new ArrayList<>(), atRecovery = new ArrayList<>();
        for (int second = 0; second <= 400; second += 100) {
            Instant now = start.plusSeconds(second);
            UUID groupId = null;
            while (true) {
                var candidate = schedule.next(now);
                if (candidate.isEmpty() || groupId != null && !candidate.orElseThrow().groupId().equals(groupId)) break;
                var due = candidate.orElseThrow();
                if (groupId == null) groupId = due.groupId();
                var phase = due.endpoint() == EVENT_DETAILS ? CHECK : IN_PLAY;
                var group = new LiveProviderDispatchGroup(campaign, due.groupId(), providerEvent, phase);
                var request = new PlaywrightProviderRequest(due.endpoint(), null, 0, 0, providerEvent);
                assertThat(tracker.isContinuation(request, group)).isEqualTo(due.groupOrdinal() > 0);
                assertThat(schedule.mayDispatch(due, now)).isTrue();
                schedule.started(due, now);
                tracker.dispatched(request, group);
                // The initial statistics 404 finishes later than J4; at the next 300-second
                // phase its backoff has not expired yet, while lineups are independently due.
                now = now.plusMillis(10);
                tracker.finished(true);
                schedule.completed(due, due.endpoint() == EVENT_DETAILS ? "inprogress" : null,
                        second == 0 && due.endpoint() == EVENT_STATISTICS, Map.of(), now);
                if (second == 300) atFiveMinutes.add(due.endpoint());
                if (second == 400) atRecovery.add(due.endpoint());
            }
        }
        assertThat(atFiveMinutes).containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_LINEUPS);
        assertThat(atRecovery).containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS);
        assertThat(schedule.familySchedules(event).stream().filter(value -> value.endpoint() == EVENT_STATISTICS)
                .findFirst().orElseThrow().intervalSeconds()).isEqualTo(100);
        assertThat(schedule.globalStop()).isNull();
        assertThat(schedule.states().getFirst().missedCycles()).isZero();
    }
}
