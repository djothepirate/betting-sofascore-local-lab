package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J4EventQueryServiceTest {

    private final CanonicalEventStore canonicalEventStore = mock(CanonicalEventStore.class);
    private final EventDetailsStore eventDetailsStore = mock(EventDetailsStore.class);
    private final J4EventQueryService service = new J4EventQueryService(
            canonicalEventStore,
            eventDetailsStore);

    @Test
    void searchesExactCivilDateBoundsInTheRequestedZone() {
        var event = event();
        Instant from = Instant.parse("2026-08-11T22:00:00Z");
        Instant to = Instant.parse("2026-08-12T22:00:00Z");
        when(canonicalEventStore.findLatestStartingBetween(from, to))
                .thenReturn(List.of(event));

        var result = service.search(LocalDate.parse("2026-08-12"), "Europe/Paris");

        assertThat(result.fromInclusive()).isEqualTo(from);
        assertThat(result.toExclusive()).isEqualTo(to);
        assertThat(result.zoneId().getId()).isEqualTo("Europe/Paris");
        assertThat(result.events()).singleElement().satisfies(item -> {
            assertThat(item.event()).isEqualTo(event);
            assertThat(item.startsAtInZone().toString())
                    .isEqualTo("2026-08-12T16:00+02:00[Europe/Paris]");
        });
        verify(canonicalEventStore).findLatestStartingBetween(from, to);
    }

    @Test
    void returnsCurrentObservationHistoryAndExplicitlyMissingDetail() {
        var event = event();
        when(canonicalEventStore.findLatestByCanonicalId(event.identity().value()))
                .thenReturn(Optional.of(event));
        when(canonicalEventStore.findHistory(event.identity().value()))
                .thenReturn(List.of(event));
        when(eventDetailsStore.findLatest(event.identity().value())).thenReturn(Optional.empty());

        var detail = service.findDetail(event.identity().value(), "UTC");

        assertThat(detail).hasValueSatisfying(value -> {
            assertThat(value.current().event()).isEqualTo(event);
            assertThat(value.history()).hasSize(1);
            assertThat(value.offlineDetail()).isEmpty();
        });
    }

    static CanonicalEventObservationView event() {
        return new CanonicalEventObservationView(
                1L,
                CanonicalEventIdentity.sofascore(900001L),
                Instant.parse("2026-08-12T14:00:00Z"),
                new ScheduledTeam(9101L, "Synthetic Home FC"),
                new ScheduledTeam(9202L, "Synthetic Away FC"),
                new ScheduledEventStatus("notstarted", Optional.of("Not started")),
                Optional.of(new ScheduledTournament(9303L, "Synthetic League")),
                EventSourceTrace.syntheticFixture(
                        "event-details-nominal",
                        "a".repeat(64),
                        "event-details-v1",
                        Instant.parse("2026-08-15T00:00:00Z")),
                "b".repeat(64),
                2L);
    }
}
