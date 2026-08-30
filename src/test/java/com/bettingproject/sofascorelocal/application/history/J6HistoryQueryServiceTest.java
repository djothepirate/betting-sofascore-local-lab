package com.bettingproject.sofascorelocal.application.history;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryClassification;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryStream;
import com.bettingproject.sofascorelocal.domain.history.J6RawPayloadState;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotOccurrenceOutcome;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotTrace;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.J6SnapshotHistoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class J6HistoryQueryServiceTest {

    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(9_600_001L);

    private CanonicalEventStore canonicalEventStore;
    private EventDetailsStore eventDetailsStore;
    private J5EventDataStore eventDataStore;
    private J6SnapshotHistoryStore snapshotHistoryStore;
    private J6HistoryQueryService service;

    @BeforeEach
    void setUp() {
        canonicalEventStore = mock(CanonicalEventStore.class);
        eventDetailsStore = mock(EventDetailsStore.class);
        eventDataStore = mock(J5EventDataStore.class);
        snapshotHistoryStore = mock(J6SnapshotHistoryStore.class);
        service = new J6HistoryQueryService(
                canonicalEventStore,
                eventDetailsStore,
                eventDataStore,
                snapshotHistoryStore,
                new J6SemanticDiffService(),
                new J6HistoryClassifier());
    }

    @Test
    void returnsEmptyForAnUnknownCanonicalEvent() {
        when(canonicalEventStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.empty());

        assertThat(service.findHistory(
                IDENTITY.value(),
                Optional.empty(),
                0,
                J6HistoryQueryService.DEFAULT_PAGE_SIZE)).isEmpty();
    }

    @Test
    void buildsNewestFirstHistoryAndMarksLateEnrichmentAndDuplicateEvidence() {
        CanonicalEventObservationView initial = state(1, "notstarted", 1);
        CanonicalEventObservationView finished = state(2, "finished", 2);
        J5EventDataObservationView before = incidents(
                10,
                101,
                'a',
                'b',
                3,
                List.of(incident(0, "goal", 10L, 1, 0)));
        J5EventDataObservationView after = incidents(
                11,
                102,
                'c',
                'd',
                4,
                List.of(
                        incident(0, "goal", 10L, 1, 0),
                        incident(1, "card", 20L, null, null)));
        when(canonicalEventStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.of(finished));
        when(canonicalEventStore.findHistory(IDENTITY.value()))
                .thenReturn(List.of(finished, initial));
        when(eventDataStore.findHistory(
                IDENTITY.value(),
                SofascoreEndpointType.EVENT_INCIDENTS))
                .thenReturn(List.of(after, before));
        when(snapshotHistoryStore.findTraces(anySet())).thenReturn(Map.of(
                101L, trace(101, 1, 0, J6SnapshotOccurrenceOutcome.INSERTED),
                102L, trace(102, 2, 1, J6SnapshotOccurrenceOutcome.DEDUPLICATED)));

        var page = service.findHistory(
                IDENTITY.value(),
                Optional.of(J6HistoryStream.EVENT_INCIDENTS),
                0,
                25).orElseThrow();

        assertThat(page.totalVersions()).isEqualTo(2);
        assertThat(page.versions())
                .extracting(version -> version.observationId())
                .containsExactly(11L, 10L);
        assertThat(page.versions().getFirst().classification())
                .isEqualTo(J6HistoryClassification.LATE_ENRICHMENT);
        assertThat(page.versions().getFirst().classifications())
                .containsExactlyInAnyOrder(
                        J6HistoryClassification.LATE_ENRICHMENT,
                        J6HistoryClassification.TECHNICAL_DUPLICATE);
        assertThat(page.versions().getFirst().previousObservationId())
                .hasValue(10L);
        assertThat(page.versions().getFirst().changesFromPrevious())
                .anySatisfy(change -> {
                    assertThat(change.field()).isEqualTo("incidents");
                    assertThat(change.kind().name()).isEqualTo("ADDED");
                });
        assertThat(page.versions().getLast().classification())
                .isEqualTo(J6HistoryClassification.BASELINE);
    }

    @Test
    void comparesArbitraryChronologicalVersionsAndRejectsReverseOrder() {
        CanonicalEventObservationView finished = state(2, "finished", 2);
        J5EventDataObservationView before = incidents(
                10, 101, 'a', 'b', 3,
                List.of(incident(0, "goal", 10L, 1, 0)));
        J5EventDataObservationView after = incidents(
                11, 102, 'c', 'd', 4,
                List.of(
                        incident(0, "goal", 10L, 1, 0),
                        incident(1, "card", 20L, null, null)));
        when(canonicalEventStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.of(finished));
        when(canonicalEventStore.findHistory(IDENTITY.value()))
                .thenReturn(List.of(finished));
        when(eventDataStore.findByObservationId(
                IDENTITY.value(), SofascoreEndpointType.EVENT_INCIDENTS, 10))
                .thenReturn(Optional.of(before));
        when(eventDataStore.findByObservationId(
                IDENTITY.value(), SofascoreEndpointType.EVENT_INCIDENTS, 11))
                .thenReturn(Optional.of(after));
        when(snapshotHistoryStore.findTraces(anySet())).thenReturn(Map.of(
                101L, trace(101, 1, 0, J6SnapshotOccurrenceOutcome.INSERTED),
                102L, trace(102, 1, 0, J6SnapshotOccurrenceOutcome.INSERTED)));

        var comparison = service.compare(
                IDENTITY.value(),
                J6HistoryStream.EVENT_INCIDENTS,
                10,
                11).orElseThrow();

        assertThat(comparison.classification())
                .isEqualTo(J6HistoryClassification.LATE_ENRICHMENT);
        assertThat(comparison.fromVersion().observationId()).isEqualTo(10);
        assertThat(comparison.toVersion().observationId()).isEqualTo(11);
        assertThat(comparison.changes()).isNotEmpty();
        assertThatThrownBy(() -> service.compare(
                IDENTITY.value(),
                J6HistoryStream.EVENT_INCIDENTS,
                11,
                10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must precede");
    }

    @Test
    void exactTransitionRemainsStableWhenCurrentHistoryGainsABackdatedVersion() {
        J5EventDataObservationView before = incidents(
                10, 101, 'a', 'b', 1,
                List.of(incident(0, "goal", 10L, 1, 0)));
        J5EventDataObservationView backdatedAfterAsOf = incidents(
                12, 103, 'e', 'f', 2,
                List.of(incident(0, "goal", 10L, 1, 0)));
        J5EventDataObservationView after = incidents(
                11, 102, 'c', 'd', 3,
                List.of(
                        incident(0, "goal", 10L, 1, 0),
                        incident(1, "card", 20L, null, null)));
        when(eventDataStore.findByObservationId(
                IDENTITY.value(), SofascoreEndpointType.EVENT_INCIDENTS, 10))
                .thenReturn(Optional.of(before));
        when(eventDataStore.findByObservationId(
                IDENTITY.value(), SofascoreEndpointType.EVENT_INCIDENTS, 11))
                .thenReturn(Optional.of(after));
        var first = service.classifyExactTransition(
                IDENTITY.value(),
                J6HistoryStream.EVENT_INCIDENTS,
                10,
                11,
                true).orElseThrow();
        when(eventDataStore.findHistory(
                IDENTITY.value(), SofascoreEndpointType.EVENT_INCIDENTS))
                .thenReturn(List.of(after, backdatedAfterAsOf, before));
        var replay = service.classifyExactTransition(
                IDENTITY.value(),
                J6HistoryStream.EVENT_INCIDENTS,
                10,
                11,
                true).orElseThrow();

        assertThat(first.classification())
                .isEqualTo(J6HistoryClassification.LATE_ENRICHMENT);
        assertThat(replay.classification()).isEqualTo(first.classification());
        assertThat(replay.changes()).isEqualTo(first.changes());
        assertThat(replay.previousObservationId()).isEqualTo(10);
        assertThat(replay.observationId()).isEqualTo(11);
        verify(eventDataStore, never()).findHistory(
                IDENTITY.value(), SofascoreEndpointType.EVENT_INCIDENTS);
        verifyNoInteractions(canonicalEventStore);
        verifyNoInteractions(snapshotHistoryStore);
    }

    @Test
    void detectsAnIntermediateTerminalStateWhenComparingNonAdjacentStateVersions() {
        CanonicalEventObservationView initial = providerState(
                1, 201, "notstarted", 'a', 'd', 1);
        CanonicalEventObservationView finished = providerState(
                2, 202, "finished", 'b', 'e', 2);
        CanonicalEventObservationView corrected = providerState(
                3, 203, "canceled", 'c', 'f', 3);
        when(canonicalEventStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.of(corrected));
        when(canonicalEventStore.findHistory(IDENTITY.value()))
                .thenReturn(List.of(corrected, finished, initial));
        when(canonicalEventStore.findByObservationId(IDENTITY.value(), 1))
                .thenReturn(Optional.of(initial));
        when(canonicalEventStore.findByObservationId(IDENTITY.value(), 3))
                .thenReturn(Optional.of(corrected));
        when(snapshotHistoryStore.findTraces(anySet())).thenReturn(Map.of(
                201L, trace(201, 1, 0, J6SnapshotOccurrenceOutcome.INSERTED),
                203L, trace(203, 1, 0, J6SnapshotOccurrenceOutcome.INSERTED)));

        var comparison = service.compare(
                IDENTITY.value(),
                J6HistoryStream.EVENT_STATE,
                1,
                3).orElseThrow();

        assertThat(comparison.classification())
                .isEqualTo(J6HistoryClassification.LATE_CORRECTION);
        assertThat(comparison.changes())
                .anySatisfy(change -> assertThat(change.field()).isEqualTo("status.type"));
    }

    @Test
    void classifiesTheFirstPostTerminalProviderVersionAsLate() {
        CanonicalEventObservationView initial = state(1, "notstarted", 1);
        CanonicalEventObservationView finished = state(2, "finished", 2);
        J5EventDataObservationView before = incidents(
                10,
                101,
                'a',
                'b',
                1,
                List.of(incident(0, "goal", 10L, 1, 0)));
        J5EventDataObservationView after = incidents(
                11,
                102,
                'c',
                'd',
                3,
                List.of(
                        incident(0, "goal", 10L, 1, 0),
                        incident(1, "card", 20L, null, null)));
        when(canonicalEventStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.of(finished));
        when(canonicalEventStore.findHistory(IDENTITY.value()))
                .thenReturn(List.of(finished, initial));
        when(eventDataStore.findHistory(
                IDENTITY.value(),
                SofascoreEndpointType.EVENT_INCIDENTS))
                .thenReturn(List.of(after, before));
        when(snapshotHistoryStore.findTraces(anySet())).thenReturn(Map.of(
                101L, trace(101, 1, 0, J6SnapshotOccurrenceOutcome.INSERTED),
                102L, trace(102, 1, 0, J6SnapshotOccurrenceOutcome.INSERTED)));

        var page = service.findHistory(
                IDENTITY.value(),
                Optional.of(J6HistoryStream.EVENT_INCIDENTS),
                0,
                25).orElseThrow();

        assertThat(page.versions().getFirst().classification())
                .isEqualTo(J6HistoryClassification.LATE_ENRICHMENT);
    }

    @Test
    void enforcesTheBoundedPaginationContract() {
        assertThatThrownBy(() -> service.findHistory(
                IDENTITY.value(), Optional.empty(), -1, 25))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findHistory(
                IDENTITY.value(), Optional.empty(), 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CanonicalEventObservationView state(
            long observationId,
            String status,
            long hour) {
        Instant receivedAt = Instant.parse("2026-08-18T10:00:00Z").plusSeconds(hour * 3_600);
        return new CanonicalEventObservationView(
                observationId,
                IDENTITY,
                Instant.parse("2026-08-20T18:00:00Z"),
                new ScheduledTeam(1, "Home"),
                new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus(status, Optional.empty()),
                Optional.empty(),
                EventSourceTrace.syntheticFixture(
                        "state-" + observationId,
                        hash('a'),
                        "scheduled-events-v1",
                        receivedAt),
                hash(status.equals("finished") ? 'b' : 'a'),
                2);
    }

    private static CanonicalEventObservationView providerState(
            long observationId,
            long snapshotId,
            String status,
            char payloadHash,
            char normalizedHash,
            long hour) {
        Instant receivedAt = Instant.parse("2026-08-18T10:00:00Z")
                .plusSeconds(hour * 3_600);
        return new CanonicalEventObservationView(
                observationId,
                IDENTITY,
                Instant.parse("2026-08-20T18:00:00Z"),
                new ScheduledTeam(1, "Home"),
                new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus(status, Optional.empty()),
                Optional.empty(),
                EventSourceTrace.providerSnapshot(
                        snapshotId,
                        hash(payloadHash),
                        "scheduled-events-v1",
                        receivedAt),
                hash(normalizedHash),
                observationId);
    }

    private static J5EventDataObservationView incidents(
            long observationId,
            long snapshotId,
            char payloadHash,
            char normalizedHash,
            long hour,
            List<EventIncident> incidents) {
        return new J5EventDataObservationView(
                observationId,
                IDENTITY,
                new EventIncidents(IDENTITY.providerEventId(), incidents),
                EventSourceTrace.providerSnapshot(
                        snapshotId,
                        hash(payloadHash),
                        "event-incidents-v13",
                        Instant.parse("2026-08-18T10:00:00Z")
                                .plusSeconds(hour * 3_600)),
                J5CompletenessReport.measured(1, 1, List.of()),
                hash(normalizedHash));
    }

    private static EventIncident incident(
            int sequence,
            String type,
            long playerId,
            Integer homeScore,
            Integer awayScore) {
        return new EventIncident(
                sequence,
                type,
                20 + sequence,
                Optional.empty(),
                Optional.of(true),
                Optional.empty(),
                Optional.of(playerId),
                Optional.of("Player " + playerId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(homeScore),
                Optional.ofNullable(awayScore),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static J6SnapshotTrace trace(
            long snapshotId,
            int occurrences,
            int duplicates,
            J6SnapshotOccurrenceOutcome latest) {
        return new J6SnapshotTrace(
                snapshotId,
                hash('a'),
                occurrences,
                duplicates,
                latest,
                Optional.of(Instant.parse("2026-08-18T14:00:00Z")),
                J6RawPayloadState.RETAINED);
    }

    private static String hash(char value) {
        return Character.toString(value).repeat(64);
    }
}
