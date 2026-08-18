package com.bettingproject.sofascorelocal.application.history;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportResult;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineImportResult;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.FixtureOrigin;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class J6OfflineHistoryDemoService {

    static final String FINISHED_SCHEDULED_MANIFEST =
            "fixtures/j6-history/scheduled-finished.manifest.json";
    static final String CORRECTED_DETAILS_MANIFEST =
            "fixtures/j6-history/details-late-correction.manifest.json";
    static final String CORRECTED_STATISTICS_MANIFEST =
            "fixtures/j6-history/statistics-late-correction.manifest.json";
    static final String CORRECTED_INCIDENTS_MANIFEST =
            "fixtures/j6-history/incidents-late-correction.manifest.json";
    static final String CORRECTED_LINEUPS_MANIFEST =
            "fixtures/j6-history/lineups-late-correction.manifest.json";

    private static final int ATTEMPTED_OBSERVATIONS = 12;

    private final J4OfflineFixtureImportService j4ImportService;
    private final J5OfflineFixtureImportService j5ImportService;
    private final CanonicalEventStore canonicalEventStore;
    private final EventDetailsStore eventDetailsStore;
    private final J5EventDataStore eventDataStore;
    private final ClasspathFixtureLoader fixtureLoader;
    private final ScheduledEventsV1Parser scheduledEventsParser;
    private final EventDetailsV1Parser eventDetailsParser;
    private final EventStatisticsV1Parser statisticsParser;
    private final EventIncidentsV1Parser incidentsParser;
    private final EventLineupsV1Parser lineupsParser;

    @Autowired
    public J6OfflineHistoryDemoService(
            J4OfflineFixtureImportService j4ImportService,
            J5OfflineFixtureImportService j5ImportService,
            CanonicalEventStore canonicalEventStore,
            EventDetailsStore eventDetailsStore,
            J5EventDataStore eventDataStore) {
        this(
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

    J6OfflineHistoryDemoService(
            J4OfflineFixtureImportService j4ImportService,
            J5OfflineFixtureImportService j5ImportService,
            CanonicalEventStore canonicalEventStore,
            EventDetailsStore eventDetailsStore,
            J5EventDataStore eventDataStore,
            ClasspathFixtureLoader fixtureLoader,
            ScheduledEventsV1Parser scheduledEventsParser,
            EventDetailsV1Parser eventDetailsParser,
            EventStatisticsV1Parser statisticsParser,
            EventIncidentsV1Parser incidentsParser,
            EventLineupsV1Parser lineupsParser) {
        this.j4ImportService = Objects.requireNonNull(j4ImportService, "j4ImportService");
        this.j5ImportService = Objects.requireNonNull(j5ImportService, "j5ImportService");
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore,
                "canonicalEventStore");
        this.eventDetailsStore = Objects.requireNonNull(
                eventDetailsStore,
                "eventDetailsStore");
        this.eventDataStore = Objects.requireNonNull(eventDataStore, "eventDataStore");
        this.fixtureLoader = Objects.requireNonNull(fixtureLoader, "fixtureLoader");
        this.scheduledEventsParser = Objects.requireNonNull(
                scheduledEventsParser,
                "scheduledEventsParser");
        this.eventDetailsParser = Objects.requireNonNull(
                eventDetailsParser,
                "eventDetailsParser");
        this.statisticsParser = Objects.requireNonNull(statisticsParser, "statisticsParser");
        this.incidentsParser = Objects.requireNonNull(incidentsParser, "incidentsParser");
        this.lineupsParser = Objects.requireNonNull(lineupsParser, "lineupsParser");
    }

    @Transactional
    public J6OfflineHistoryDemoResult importCorpus() {
        J4OfflineFixtureImportResult j4 = j4ImportService.importNominalCorpus();
        J5OfflineImportResult j5 = j5ImportService.importNominalCorpus(j4.canonicalEventId());
        CanonicalEventIdentity identity = CanonicalEventIdentity.sofascore(900_001L);
        if (!identity.value().equals(j4.canonicalEventId())
                || !identity.value().equals(j5.canonicalEventId())) {
            throw new J6OfflineHistoryDemoException(
                    J6OfflineHistoryDemoError.EVENT_ID_MISMATCH);
        }

        List<Boolean> inserted = new ArrayList<>(ATTEMPTED_OBSERVATIONS);
        inserted.add(j4.scheduledObservationInserted());
        inserted.add(j4.detailEventObservationInserted());
        inserted.add(j4.detailInserted());
        inserted.add(j5.statisticsInserted());
        inserted.add(j5.incidentsInserted());
        inserted.add(j5.lineupsInserted());

        LoadedFixture scheduledFixture = synthetic(FINISHED_SCHEDULED_MANIFEST);
        var scheduledResult = scheduledEventsParser.parse(scheduledFixture);
        if (scheduledResult.status() != ScheduledEventsParseStatus.PARSED
                || scheduledResult.page().orElseThrow().events().size() != 1) {
            throw incompatible();
        }
        var scheduledEvent = scheduledResult.page().orElseThrow().events().getFirst();
        requireEventId(identity, scheduledEvent.providerEventId());
        inserted.add(canonicalEventStore.save(CanonicalEventObservation.from(
                scheduledEvent,
                source(scheduledFixture))).inserted());

        LoadedFixture detailsFixture = synthetic(CORRECTED_DETAILS_MANIFEST);
        var detailsResult = eventDetailsParser.parse(detailsFixture);
        if (detailsResult.status() != EventDetailsParseStatus.PARSED) {
            throw incompatible();
        }
        var details = detailsResult.details().orElseThrow();
        requireEventId(identity, details.providerEventId());
        EventSourceTrace detailsSource = source(detailsFixture);
        inserted.add(canonicalEventStore.save(CanonicalEventObservation.from(
                details.asScheduledEvent(),
                detailsSource)).inserted());
        inserted.add(eventDetailsStore.save(EventDetailObservation.from(
                identity,
                details,
                detailsSource)).inserted());

        inserted.add(importJ5(
                identity,
                synthetic(CORRECTED_STATISTICS_MANIFEST),
                statisticsParser::parse));
        inserted.add(importJ5(
                identity,
                synthetic(CORRECTED_INCIDENTS_MANIFEST),
                incidentsParser::parse));
        inserted.add(importJ5(
                identity,
                synthetic(CORRECTED_LINEUPS_MANIFEST),
                lineupsParser::parse));

        int insertedCount = Math.toIntExact(inserted.stream().filter(Boolean::booleanValue).count());
        return new J6OfflineHistoryDemoResult(
                identity.value(),
                ATTEMPTED_OBSERVATIONS,
                insertedCount,
                ATTEMPTED_OBSERVATIONS - insertedCount);
    }

    private boolean importJ5(
            CanonicalEventIdentity identity,
            LoadedFixture fixture,
            FixtureParser parser) {
        J5ParseResult<? extends J5EventData> result = parser.parse(fixture);
        if (result.status() != J5ParseStatus.PARSED) {
            throw incompatible();
        }
        J5EventData data = result.data().orElseThrow();
        requireEventId(identity, data.providerEventId());
        return eventDataStore.save(J5EventDataObservation.from(
                identity,
                data,
                source(fixture),
                result.completeness().orElseThrow())).inserted();
    }

    private LoadedFixture synthetic(String manifest) {
        LoadedFixture fixture = fixtureLoader.load(manifest);
        if (fixture.manifest().fixtureOrigin() != FixtureOrigin.SYNTHETIC) {
            throw incompatible();
        }
        return fixture;
    }

    private static EventSourceTrace source(LoadedFixture fixture) {
        return EventSourceTrace.syntheticFixture(
                fixture.manifest().fixtureId(),
                fixture.rawSha256(),
                fixture.manifest().parserVersion(),
                fixture.manifest().recordedAt());
    }

    private static void requireEventId(CanonicalEventIdentity identity, long providerEventId) {
        if (identity.providerEventId() != providerEventId) {
            throw new J6OfflineHistoryDemoException(
                    J6OfflineHistoryDemoError.EVENT_ID_MISMATCH);
        }
    }

    private static J6OfflineHistoryDemoException incompatible() {
        return new J6OfflineHistoryDemoException(
                J6OfflineHistoryDemoError.CORPUS_INCOMPATIBLE);
    }

    @FunctionalInterface
    interface FixtureParser {
        J5ParseResult<? extends J5EventData> parse(LoadedFixture fixture);
    }
}
