package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

class LiveTimeoutRecoveryTest {
    private static final Instant START = Instant.parse("2026-09-09T18:00:00Z");
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(value = SofascoreEndpointType.class, names = {"EVENT_DETAILS", "EVENT_INCIDENTS", "EVENT_STATISTICS", "EVENT_LINEUPS"})
    void timedOutGroupIsAbandonedAndNextGroupStartsWithJ4AfterFiveMinutes(SofascoreEndpointType endpoint) {
        LiveSchedule schedule = schedule(List.of(A), 3600);
        LiveSchedule.Due due = schedule.next(START).orElseThrow();
        while (due.endpoint() != endpoint) {
            schedule.started(due, START);
            schedule.completed(due, "inprogress", false, Map.of(), START);
            due = schedule.next(START).orElseThrow();
        }
        schedule.started(due, START);
        Instant ended = START.plusSeconds(30);
        schedule.deferAfterTimeout(due, ended);
        assertThat(schedule.next(ended.plusSeconds(299))).isEmpty();
        assertThat(schedule.states().getFirst().nextDueAt()).isEqualTo(ended.plusSeconds(300));
        assertThat(schedule.familySchedules(A)).allSatisfy(f -> {
            if (f.nextDueAt() != null) assertThat(f.nextDueAt()).isAfterOrEqualTo(ended.plusSeconds(300));
        });
        var resumed = schedule.next(ended.plusSeconds(300)).orElseThrow();
        assertThat(resumed.endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(resumed.groupId()).isNotEqualTo(due.groupId());
        assertThat(resumed.groupOrdinal()).isZero();
        assertThat(schedule.globalStop()).isNull();
        assertThat(schedule.states().getFirst().missedCycles()).isEqualTo(1);
    }

    @Test void anotherEventKeepsItsOwnDeadlineWhileTheFirstWaits() {
        LiveSchedule schedule = schedule(List.of(A, B), 3600);
        var due = schedule.next(START).orElseThrow();
        schedule.started(due, START);
        schedule.deferAfterTimeout(due, START.plusSeconds(30));
        assertThat(schedule.next(START.plusSeconds(49))).isEmpty();
        var other = schedule.next(START.plusSeconds(50)).orElseThrow();
        assertThat(other.eventId()).isEqualTo(B);
        assertThat(other.endpoint()).isEqualTo(EVENT_DETAILS);
        assertThat(other.dueAt()).isEqualTo(START.plusSeconds(50));
    }

    @Test void finalTimeoutClosesOnlyThatEventWithoutASecondFinalAttempt() {
        LiveSchedule schedule = schedule(List.of(A, B), 3600);
        var j4 = schedule.next(START).orElseThrow();
        schedule.started(j4, START);
        schedule.completed(j4, "finished", false, Map.of(), START);
        var finalDue = schedule.next(START).orElseThrow();
        assertThat(finalDue.finalCycle()).isTrue();
        schedule.started(finalDue, START);
        schedule.deferAfterTimeout(finalDue, START.plusSeconds(30));
        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_ERROR");
        assertThat(schedule.states().getFirst().finalComplete()).isFalse();
        assertThat(schedule.familySchedules(A)).allSatisfy(f -> assertThat(f.nextDueAt()).isNull());
        assertThat(schedule.globalStop()).isNull();
        assertThat(schedule.next(START.plusSeconds(50)).orElseThrow().eventId()).isEqualTo(B);
    }

    @Test void delayCannotExtendTheCampaignWindow() {
        LiveSchedule schedule = schedule(List.of(A), 300);
        var due = schedule.next(START).orElseThrow();
        schedule.started(due, START);
        schedule.deferAfterTimeout(due, START.plusSeconds(30));
        assertThat(schedule.states().getFirst().state()).isEqualTo("STOPPED_LIMIT");
        assertThat(schedule.next(START.plusSeconds(330))).isEmpty();
    }

    @Test void aSecondTimeoutWithoutAnInterveningParsedSuccessIsNotIsolated() {
        var allowance = new LiveTimeoutRecoveryPolicy();
        assertThat(allowance.admit(A, EVENT_DETAILS)).isTrue();
        assertThat(allowance.admit(B, EVENT_DETAILS)).isFalse();
    }

    @Test void anotherSuccessDoesNotPermitRepeatedTimeoutsOfTheSameFamily() {
        var allowance = new LiveTimeoutRecoveryPolicy();
        assertThat(allowance.admit(A, EVENT_STATISTICS)).isTrue();
        allowance.successful(B, EVENT_DETAILS);
        assertThat(allowance.admit(A, EVENT_STATISTICS)).isFalse();
        allowance.successful(A, EVENT_STATISTICS);
        assertThat(allowance.admit(A, EVENT_STATISTICS)).isTrue();
    }

    @Test void threeRecoveriesAreAHardSessionLimitDespiteInterveningSuccesses() {
        var allowance = new LiveTimeoutRecoveryPolicy();
        for (int i = 0; i < 3; i++) {
            assertThat(allowance.admit(A, EVENT_DETAILS)).isTrue();
            allowance.successful(A, EVENT_DETAILS);
        }
        assertThat(allowance.admit(A, EVENT_DETAILS)).isFalse();
    }

    private static LiveSchedule schedule(List<UUID> ids, int seconds) {
        return new LiveSchedule(ids, START, START.plusSeconds(seconds), Duration.ofSeconds(100), "live-v6", UUID.randomUUID());
    }
}
