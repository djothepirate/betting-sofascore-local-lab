package com.bettingproject.sofascorelocal.domain.event;

import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalEventObservationTest {

    private static final EventSourceTrace SOURCE = EventSourceTrace.syntheticFixture(
            "j4-canonical-event",
            "a".repeat(64),
            "scheduled-events-v1",
            Instant.parse("2026-08-15T08:00:00Z"));

    @Test
    void keepsIdentityButChangesVersionHashWhenMutableFieldsChange() {
        ScheduledEvent original = event("Local FC", "scheduled");
        ScheduledEvent renamed = new ScheduledEvent(
                original.providerEventId(),
                original.startsAt().plusSeconds(3600),
                new ScheduledTeam(original.homeTeam().providerTeamId(), "Local United"),
                original.awayTeam(),
                new ScheduledEventStatus("postponed", Optional.of("Postponed")),
                original.tournament());

        var first = CanonicalEventObservation.from(original, SOURCE);
        var second = CanonicalEventObservation.from(renamed, SOURCE);

        assertThat(second.identity()).isEqualTo(first.identity());
        assertThat(second.normalizedSha256()).isNotEqualTo(first.normalizedSha256());
    }

    @Test
    void producesTheSameVersionHashForTheSameNormalizedContent() {
        var first = CanonicalEventObservation.from(event("Local FC", "scheduled"), SOURCE);
        var repeated = CanonicalEventObservation.from(event("Local FC", "scheduled"), SOURCE);

        assertThat(repeated.normalizedSha256()).isEqualTo(first.normalizedSha256());
    }

    private static ScheduledEvent event(String homeName, String statusType) {
        return new ScheduledEvent(
                123456789L,
                Instant.parse("2026-08-15T18:45:00Z"),
                new ScheduledTeam(101L, homeName),
                new ScheduledTeam(202L, "Visitor FC"),
                new ScheduledEventStatus(statusType, Optional.empty()),
                Optional.empty());
    }
}
