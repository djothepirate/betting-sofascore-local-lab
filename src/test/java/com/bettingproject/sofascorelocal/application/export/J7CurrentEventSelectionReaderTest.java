package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.J6SnapshotHistoryStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J7CurrentEventSelectionReaderTest {

    private final CanonicalEventStore canonicalEventStore = mock(CanonicalEventStore.class);
    private final EventDetailsStore eventDetailsStore = mock(EventDetailsStore.class);
    private final J5EventDataStore eventDataStore = mock(J5EventDataStore.class);
    private final J6SnapshotHistoryStore snapshotHistoryStore = mock(
            J6SnapshotHistoryStore.class);
    private final J7CurrentEventSelectionReader reader = new J7CurrentEventSelectionReader(
            canonicalEventStore,
            eventDetailsStore,
            eventDataStore,
            snapshotHistoryStore);

    @Test
    void loadsAllSlotsAndBatchesProviderSnapshotMetadata() {
        CanonicalEventIdentity identity = CanonicalEventIdentity.sofascore(16691018L);
        UUID canonicalEventId = identity.value();
        EventSourceTrace providerSource = EventSourceTrace.providerSnapshot(
                77L,
                "7".repeat(64),
                "scheduled-events-v1",
                Instant.parse("2026-08-18T12:00:00Z"));
        CanonicalEventObservationView eventState = new CanonicalEventObservationView(
                1L,
                identity,
                Instant.parse("2026-08-20T18:45:00Z"),
                new ScheduledTeam(1L, "Home"),
                new ScheduledTeam(2L, "Away"),
                new ScheduledEventStatus("scheduled", Optional.empty()),
                Optional.empty(),
                providerSource,
                "8".repeat(64),
                1L);
        EventSourceTrace detailSource = EventSourceTrace.syntheticFixture(
                "j7-detail",
                "9".repeat(64),
                "event-details-v1",
                Instant.parse("2026-08-18T12:01:00Z"));
        EventDetails details = new EventDetails(
                identity.providerEventId(),
                eventState.startsAt(),
                eventState.homeTeam(),
                eventState.awayTeam(),
                eventState.status(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        EventDetailObservationView detail = new EventDetailObservationView(
                2L,
                identity,
                details,
                detailSource,
                "a".repeat(64));

        when(canonicalEventStore.findLatestByCanonicalId(canonicalEventId))
                .thenReturn(Optional.of(eventState));
        when(eventDetailsStore.findLatest(canonicalEventId)).thenReturn(Optional.of(detail));
        when(eventDataStore.findLatest(canonicalEventId)).thenReturn(J5EventDataBundle.empty());
        when(snapshotHistoryStore.findTraces(Set.of(77L))).thenReturn(Map.of());

        Optional<J7CurrentEventSelection> result = reader.load(canonicalEventId);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().eventState()).isSameAs(eventState);
        assertThat(result.orElseThrow().eventDetails()).contains(detail);
        ArgumentCaptor<Set<Long>> ids = ArgumentCaptor.forClass(Set.class);
        verify(snapshotHistoryStore).findTraces(ids.capture());
        assertThat(ids.getValue()).containsExactly(77L);
    }

    @Test
    void stopsWithoutReadingOptionalStoresWhenEventDoesNotExist() {
        UUID canonicalEventId = UUID.randomUUID();
        when(canonicalEventStore.findLatestByCanonicalId(canonicalEventId))
                .thenReturn(Optional.empty());

        assertThat(reader.load(canonicalEventId)).isEmpty();

        verify(eventDetailsStore, never()).findLatest(canonicalEventId);
        verify(eventDataStore, never()).findLatest(canonicalEventId);
        verify(snapshotHistoryStore, never()).findTraces(Set.of());
    }

    @Test
    void declaresAReadOnlyRepeatableReadBoundary() throws Exception {
        Transactional transaction = J7CurrentEventSelectionReader.class
                .getMethod("load", UUID.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
