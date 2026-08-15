package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseStatus;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.FixtureOrigin;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class J5OfflineFixtureImportService {

    public static final String STATISTICS_MANIFEST =
            "fixtures/event-statistics/nominal.manifest.json";
    public static final String INCIDENTS_MANIFEST =
            "fixtures/event-incidents/nominal.manifest.json";
    public static final String LINEUPS_MANIFEST =
            "fixtures/event-lineups/nominal.manifest.json";

    private final CanonicalEventStore canonicalEventStore;
    private final J5EventDataStore eventDataStore;
    private final ClasspathFixtureLoader fixtureLoader;
    private final EventStatisticsV1Parser statisticsParser;
    private final EventIncidentsV1Parser incidentsParser;
    private final EventLineupsV1Parser lineupsParser;

    @Autowired
    public J5OfflineFixtureImportService(
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore) {
        this(
                canonicalEventStore,
                eventDataStore,
                new ClasspathFixtureLoader(),
                new EventStatisticsV1Parser(),
                new EventIncidentsV1Parser(),
                new EventLineupsV1Parser());
    }

    J5OfflineFixtureImportService(
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore,
            ClasspathFixtureLoader fixtureLoader,
            EventStatisticsV1Parser statisticsParser,
            EventIncidentsV1Parser incidentsParser,
            EventLineupsV1Parser lineupsParser) {
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore,
                "canonicalEventStore");
        this.eventDataStore = Objects.requireNonNull(eventDataStore, "eventDataStore");
        this.fixtureLoader = Objects.requireNonNull(fixtureLoader, "fixtureLoader");
        this.statisticsParser = Objects.requireNonNull(statisticsParser, "statisticsParser");
        this.incidentsParser = Objects.requireNonNull(incidentsParser, "incidentsParser");
        this.lineupsParser = Objects.requireNonNull(lineupsParser, "lineupsParser");
    }

    @Transactional
    public J5OfflineImportResult importNominalCorpus(UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        var event = canonicalEventStore.findLatestByCanonicalId(canonicalEventId)
                .orElseThrow(() -> new J5OfflineImportException(
                        J5OfflineImportError.EVENT_NOT_FOUND));
        CanonicalEventIdentity identity = event.identity();

        LoadedFixture statisticsFixture = fixtureLoader.load(STATISTICS_MANIFEST);
        LoadedFixture incidentsFixture = fixtureLoader.load(INCIDENTS_MANIFEST);
        LoadedFixture lineupsFixture = fixtureLoader.load(LINEUPS_MANIFEST);
        if (List.of(statisticsFixture, incidentsFixture, lineupsFixture).stream()
                .anyMatch(fixture -> fixture.manifest().fixtureOrigin()
                        != FixtureOrigin.SYNTHETIC)) {
            throw new J5OfflineImportException(J5OfflineImportError.SOURCE_NOT_SYNTHETIC);
        }

        var statistics = statisticsParser.parse(statisticsFixture);
        var incidents = incidentsParser.parse(incidentsFixture);
        var lineups = lineupsParser.parse(lineupsFixture);
        requireParsed(statistics, J5OfflineImportError.STATISTICS_FIXTURE_INCOMPATIBLE);
        requireParsed(incidents, J5OfflineImportError.INCIDENTS_FIXTURE_INCOMPATIBLE);
        requireParsed(lineups, J5OfflineImportError.LINEUPS_FIXTURE_INCOMPATIBLE);
        if (statistics.data().orElseThrow().providerEventId() != identity.providerEventId()
                || incidents.data().orElseThrow().providerEventId()
                        != identity.providerEventId()
                || lineups.data().orElseThrow().providerEventId()
                        != identity.providerEventId()) {
            throw new J5OfflineImportException(J5OfflineImportError.EVENT_ID_MISMATCH);
        }

        var statisticsPersistence = eventDataStore.save(observation(
                identity,
                statisticsFixture,
                statistics));
        var incidentsPersistence = eventDataStore.save(observation(
                identity,
                incidentsFixture,
                incidents));
        var lineupsPersistence = eventDataStore.save(observation(
                identity,
                lineupsFixture,
                lineups));
        return new J5OfflineImportResult(
                identity.value(),
                statisticsPersistence.observationId(),
                statisticsPersistence.inserted(),
                statistics.completeness().orElseThrow().status(),
                incidentsPersistence.observationId(),
                incidentsPersistence.inserted(),
                incidents.completeness().orElseThrow().status(),
                lineupsPersistence.observationId(),
                lineupsPersistence.inserted(),
                lineups.completeness().orElseThrow().status());
    }

    private static void requireParsed(
            J5ParseResult<? extends J5EventData> result,
            J5OfflineImportError error) {
        if (result.status() != J5ParseStatus.PARSED) {
            throw new J5OfflineImportException(error);
        }
    }

    private static J5EventDataObservation observation(
            CanonicalEventIdentity identity,
            LoadedFixture fixture,
            J5ParseResult<? extends J5EventData> result) {
        J5EventData data = result.data().orElseThrow();
        EventSourceTrace source = EventSourceTrace.syntheticFixture(
                fixture.manifest().fixtureId(),
                fixture.rawSha256(),
                fixture.manifest().parserVersion(),
                fixture.manifest().recordedAt());
        return J5EventDataObservation.from(
                identity,
                data,
                source,
                result.completeness().orElseThrow());
    }
}
