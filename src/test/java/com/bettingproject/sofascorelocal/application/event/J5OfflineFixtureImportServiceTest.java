package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J5OfflineFixtureImportServiceTest {

    private final CanonicalEventStore canonicalEventStore = mock(CanonicalEventStore.class);
    private final J5EventDataStore eventDataStore = mock(J5EventDataStore.class);
    private final J5OfflineFixtureImportService service = new J5OfflineFixtureImportService(
            canonicalEventStore,
            eventDataStore);

    @Test
    void parsesAllFixturesBeforePersistingThreeTracedCompleteFamilies() {
        CanonicalEventObservationView event = event(900001L);
        when(canonicalEventStore.findLatestByCanonicalId(event.identity().value()))
                .thenReturn(Optional.of(event));
        when(eventDataStore.save(any())).thenAnswer(invocation -> {
            J5EventDataObservation observation = invocation.getArgument(0);
            long id = switch (observation.data().endpointType()) {
                case EVENT_STATISTICS -> 11L;
                case EVENT_INCIDENTS -> 12L;
                case EVENT_LINEUPS -> 13L;
                default -> throw new AssertionError("unexpected endpoint");
            };
            return new J5EventDataPersistenceResult(
                    id,
                    event.identity().value(),
                    observation.data().endpointType(),
                    true);
        });

        var result = service.importNominalCorpus(event.identity().value());

        assertThat(result.canonicalEventId()).isEqualTo(event.identity().value());
        assertThat(result.statisticsObservationId()).isEqualTo(11L);
        assertThat(result.incidentsObservationId()).isEqualTo(12L);
        assertThat(result.lineupsObservationId()).isEqualTo(13L);
        assertThat(result.statisticsInserted()).isTrue();
        assertThat(result.incidentsInserted()).isTrue();
        assertThat(result.lineupsInserted()).isTrue();

        ArgumentCaptor<J5EventDataObservation> captor =
                ArgumentCaptor.forClass(J5EventDataObservation.class);
        verify(eventDataStore, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(observation -> observation.data().endpointType())
                .containsExactly(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(captor.getAllValues())
                .allSatisfy(observation -> {
                    assertThat(observation.identity()).isEqualTo(event.identity());
                    assertThat(observation.source().kind())
                            .isEqualTo(EventSourceKind.SYNTHETIC_FIXTURE);
                    assertThat(observation.source().payloadSha256()).hasSize(64);
                    assertThat(observation.completeness().scorePercent()).isEqualTo(100);
                });
    }

    @Test
    void rejectsTheSyntheticCorpusWhenTheRequestedCanonicalIdentityIsDifferent() {
        CanonicalEventObservationView event = event(900002L);
        when(canonicalEventStore.findLatestByCanonicalId(event.identity().value()))
                .thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.importNominalCorpus(event.identity().value()))
                .isInstanceOfSatisfying(J5OfflineImportException.class, exception ->
                        assertThat(exception.error())
                                .isEqualTo(J5OfflineImportError.EVENT_ID_MISMATCH));
        verify(eventDataStore, never()).save(any());
    }

    private static CanonicalEventObservationView event(long providerEventId) {
        CanonicalEventIdentity identity = CanonicalEventIdentity.sofascore(providerEventId);
        return new CanonicalEventObservationView(
                1L,
                identity,
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
                1L);
    }
}
