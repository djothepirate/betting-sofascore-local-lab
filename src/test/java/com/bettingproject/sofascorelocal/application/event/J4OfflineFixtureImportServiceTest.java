package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventObservationPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailPersistenceResult;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J4OfflineFixtureImportServiceTest {

    private final CanonicalEventStore canonicalEventStore = mock(CanonicalEventStore.class);
    private final EventDetailsStore eventDetailsStore = mock(EventDetailsStore.class);
    private final J4OfflineFixtureImportService service = new J4OfflineFixtureImportService(
            canonicalEventStore,
            eventDetailsStore,
            new ClasspathFixtureLoader(),
            new ScheduledEventsV1Parser(),
            new EventDetailsV1Parser());

    @Test
    void importsTwoTraceableCanonicalObservationsAndOneOfflineDetail() {
        UUID canonicalId = CanonicalEventIdentity.sofascore(900001L).value();
        when(canonicalEventStore.save(any()))
                .thenReturn(new EventObservationPersistenceResult(1L, canonicalId, 1L, true))
                .thenReturn(new EventObservationPersistenceResult(2L, canonicalId, 2L, true));
        when(eventDetailsStore.save(any()))
                .thenReturn(new EventDetailPersistenceResult(1L, canonicalId, true));

        var result = service.importNominalCorpus();

        assertThat(result.canonicalEventId()).isEqualTo(canonicalId);
        assertThat(result.canonicalObservationCount()).isEqualTo(2L);
        assertThat(result.scheduledObservationInserted()).isTrue();
        assertThat(result.detailEventObservationInserted()).isTrue();
        assertThat(result.detailInserted()).isTrue();

        ArgumentCaptor<CanonicalEventObservation> eventCaptor =
                ArgumentCaptor.forClass(CanonicalEventObservation.class);
        verify(canonicalEventStore, org.mockito.Mockito.times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(observation -> {
                    assertThat(observation.identity().value()).isEqualTo(canonicalId);
                    assertThat(observation.source().fixtureId()).isPresent();
                    assertThat(observation.source().payloadSha256()).hasSize(64);
                });
        assertThat(eventCaptor.getAllValues())
                .extracting(observation -> observation.source().fixtureId().orElseThrow())
                .containsExactly("scheduled-events-nominal", "event-details-nominal");

        ArgumentCaptor<EventDetailObservation> detailCaptor =
                ArgumentCaptor.forClass(EventDetailObservation.class);
        verify(eventDetailsStore).save(detailCaptor.capture());
        assertThat(detailCaptor.getValue().identity().value()).isEqualTo(canonicalId);
        assertThat(detailCaptor.getValue().details().providerEventId()).isEqualTo(900001L);
        assertThat(detailCaptor.getValue().source().fixtureId())
                .contains("event-details-nominal");
    }

    @Test
    void rejectsAValidDetailForAnotherEventBeforeAnyPersistence() {
        assertThatThrownBy(() -> service.importFixturePair(
                J4OfflineFixtureImportService.SCHEDULED_EVENT_MANIFEST,
                "fixtures/event-details/other-event.manifest.json"))
                .isInstanceOf(J4OfflineFixtureImportException.class)
                .satisfies(exception -> assertThat(
                        ((J4OfflineFixtureImportException) exception).error())
                        .isEqualTo(J4OfflineFixtureImportError.EVENT_ID_MISMATCH));

        verify(canonicalEventStore, never()).save(any());
        verify(eventDetailsStore, never()).save(any());
    }
}
