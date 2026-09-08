package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @Test
    void resolvesTheProviderIdOnlyFromTheServerComputedDateSelection() {
        var event = event();
        Instant from = Instant.parse("2026-08-11T22:00:00Z");
        Instant to = Instant.parse("2026-08-12T22:00:00Z");
        when(canonicalEventStore.findLatestStartingBetween(from, to))
                .thenReturn(List.of(event));

        assertThat(service.findSofascoreIdentityInSelection(
                event.identity().value(), LocalDate.parse("2026-08-12"), "Europe/Paris"))
                .contains(event.identity());

        var unknown = java.util.UUID.fromString(
                "70000000-0000-0000-0000-000000000004");
        assertThat(service.findSofascoreIdentityInSelection(
                unknown, LocalDate.parse("2026-08-12"), "Europe/Paris"))
                .isEmpty();

        when(canonicalEventStore.findLatestStartingBetween(
                Instant.parse("2026-08-12T22:00:00Z"),
                Instant.parse("2026-08-13T22:00:00Z")))
                .thenReturn(List.of());
        assertThat(service.findSofascoreIdentityInSelection(
                event.identity().value(), LocalDate.parse("2026-08-13"), "Europe/Paris"))
                .isEmpty();
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

    @ParameterizedTest
    @CsvSource({"finished,true,3,0,Victoire sur tapis vert,3 – 0",
            "finished,false,0,0,finished,0 – 0", "finished,,3,,finished,—",
            "inprogress,true,1,2,inprogress,1 – 2", "postponed,,0,1,postponed,0 – 1"})
    void showsOnlyExplicitAwardForFinishedAndBothDisplayScores(
            String status, Boolean awarded, Integer home, Integer away, String label, String score) {
        var base = event();
        var source = EventSourceTrace.providerSnapshot(42, "c".repeat(64), "event-details-v3", base.source().receivedAt());
        var canonical = new CanonicalEventObservationView(2, base.identity(), base.startsAt(), base.homeTeam(),
                base.awayTeam(), new ScheduledEventStatus(status, Optional.empty()), base.tournament(), source, "d".repeat(64), 2);
        var details = new EventDetails(base.identity().providerEventId(), base.startsAt(), base.homeTeam(), base.awayTeam(),
                canonical.status(), base.tournament(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.ofNullable(awarded), Optional.ofNullable(home), Optional.ofNullable(away));
        when(canonicalEventStore.findLatestByCanonicalId(base.identity().value())).thenReturn(Optional.of(canonical));
        when(eventDetailsStore.findLatest(base.identity().value())).thenReturn(Optional.of(
                new EventDetailObservationView(4, base.identity(), details, source, "e".repeat(64))));

        var current = service.findDetail(base.identity().value(), "UTC").orElseThrow().current();
        assertThat(current.sportStatusLabel()).isEqualTo(label);
        assertThat(current.result().score()).isEqualTo(score);
    }

    @Test
    void aNewerDiscoveryObservationNeverInheritsAnEarlierJ4AwardOrScore() {
        var base = event();
        var status = new ScheduledEventStatus("finished", Optional.empty());
        var canonical = new CanonicalEventObservationView(2, base.identity(), base.startsAt(), base.homeTeam(),
                base.awayTeam(), status, base.tournament(), EventSourceTrace.providerSnapshot(44, "f".repeat(64),
                "tournament-scheduled-v1", base.source().receivedAt().plusSeconds(60)), "d".repeat(64), 2);
        var details = new EventDetails(base.identity().providerEventId(), base.startsAt(), base.homeTeam(), base.awayTeam(),
                status, base.tournament(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(true), Optional.of(3), Optional.of(0));
        when(canonicalEventStore.findLatestByCanonicalId(base.identity().value())).thenReturn(Optional.of(canonical));
        when(eventDetailsStore.findLatest(base.identity().value())).thenReturn(Optional.of(new EventDetailObservationView(
                4, base.identity(), details, EventSourceTrace.providerSnapshot(42, "c".repeat(64), "event-details-v3",
                base.source().receivedAt()), "e".repeat(64))));

        var current = service.findDetail(base.identity().value(), "UTC").orElseThrow().current();
        assertThat(current.sportStatusLabel()).isEqualTo("finished");
        assertThat(current.result().score()).isEqualTo("—");
    }
}
