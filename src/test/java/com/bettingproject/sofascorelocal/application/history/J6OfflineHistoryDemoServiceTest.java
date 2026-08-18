package com.bettingproject.sofascorelocal.application.history;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportResult;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineImportResult;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventObservationPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J6OfflineHistoryDemoServiceTest {

    private static final UUID EVENT_ID =
            CanonicalEventIdentity.sofascore(900_001L).value();

    private J4OfflineFixtureImportService j4ImportService;
    private J5OfflineFixtureImportService j5ImportService;
    private CanonicalEventStore canonicalEventStore;
    private EventDetailsStore eventDetailsStore;
    private J5EventDataStore eventDataStore;
    private J6OfflineHistoryDemoService service;

    @BeforeEach
    void setUp() {
        j4ImportService = mock(J4OfflineFixtureImportService.class);
        j5ImportService = mock(J5OfflineFixtureImportService.class);
        canonicalEventStore = mock(CanonicalEventStore.class);
        eventDetailsStore = mock(EventDetailsStore.class);
        eventDataStore = mock(J5EventDataStore.class);
        service = new J6OfflineHistoryDemoService(
                j4ImportService,
                j5ImportService,
                canonicalEventStore,
                eventDetailsStore,
                eventDataStore,
                new ClasspathFixtureLoader(),
                new ScheduledEventsV1Parser(),
                new EventDetailsV1Parser(),
                new EventStatisticsV1Parser(),
                new EventIncidentsV1Parser(),
                new EventLineupsV1Parser());
    }

    @Test
    void importsTheSyntheticMultiStreamChronology() {
        stubBase(true);
        when(canonicalEventStore.save(any()))
                .thenReturn(
                        new EventObservationPersistenceResult(3, EVENT_ID, 3, true),
                        new EventObservationPersistenceResult(4, EVENT_ID, 4, true));
        when(eventDetailsStore.save(any()))
                .thenReturn(new EventDetailPersistenceResult(2, EVENT_ID, true));
        when(eventDataStore.save(any()))
                .thenReturn(
                        persistence(21, SofascoreEndpointType.EVENT_STATISTICS, true),
                        persistence(22, SofascoreEndpointType.EVENT_INCIDENTS, true),
                        persistence(23, SofascoreEndpointType.EVENT_LINEUPS, true));

        J6OfflineHistoryDemoResult result = service.importCorpus();

        assertThat(result.canonicalEventId()).isEqualTo(EVENT_ID);
        assertThat(result.attemptedObservations()).isEqualTo(12);
        assertThat(result.insertedObservations()).isEqualTo(12);
        assertThat(result.deduplicatedObservations()).isZero();
        verify(j5ImportService).importNominalCorpus(EVENT_ID);
    }

    @Test
    void reportsEveryObservationAsDeduplicatedOnAnIdempotentReplay() {
        stubBase(false);
        when(canonicalEventStore.save(any()))
                .thenReturn(
                        new EventObservationPersistenceResult(3, EVENT_ID, 4, false),
                        new EventObservationPersistenceResult(4, EVENT_ID, 4, false));
        when(eventDetailsStore.save(any()))
                .thenReturn(new EventDetailPersistenceResult(2, EVENT_ID, false));
        when(eventDataStore.save(any()))
                .thenReturn(
                        persistence(21, SofascoreEndpointType.EVENT_STATISTICS, false),
                        persistence(22, SofascoreEndpointType.EVENT_INCIDENTS, false),
                        persistence(23, SofascoreEndpointType.EVENT_LINEUPS, false));

        J6OfflineHistoryDemoResult result = service.importCorpus();

        assertThat(result.insertedObservations()).isZero();
        assertThat(result.deduplicatedObservations()).isEqualTo(12);
    }

    private void stubBase(boolean inserted) {
        when(j4ImportService.importNominalCorpus()).thenReturn(
                new J4OfflineFixtureImportResult(
                        EVENT_ID,
                        Instant.parse("2026-08-12T10:00:00Z"),
                        2,
                        inserted,
                        inserted,
                        inserted));
        when(j5ImportService.importNominalCorpus(EVENT_ID)).thenReturn(
                new J5OfflineImportResult(
                        EVENT_ID,
                        11,
                        inserted,
                        J5CompletenessStatus.COMPLETE,
                        12,
                        inserted,
                        J5CompletenessStatus.COMPLETE,
                        13,
                        inserted,
                        J5CompletenessStatus.COMPLETE));
    }

    private static J5EventDataPersistenceResult persistence(
            long observationId,
            SofascoreEndpointType endpointType,
            boolean inserted) {
        return new J5EventDataPersistenceResult(
                observationId,
                EVENT_ID,
                endpointType,
                inserted);
    }
}
