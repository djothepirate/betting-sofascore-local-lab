package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.FixtureOrigin;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class J4OfflineFixtureImportService {

    public static final String SCHEDULED_EVENT_MANIFEST =
            "fixtures/scheduled-events/nominal.manifest.json";
    public static final String EVENT_DETAIL_MANIFEST =
            "fixtures/event-details/nominal.manifest.json";

    private final CanonicalEventStore canonicalEventStore;
    private final EventDetailsStore eventDetailsStore;
    private final ClasspathFixtureLoader fixtureLoader;
    private final ScheduledEventsV1Parser scheduledEventsParser;
    private final EventDetailsV1Parser eventDetailsParser;

    @Autowired
    public J4OfflineFixtureImportService(
            CanonicalEventStore canonicalEventStore,
            EventDetailsStore eventDetailsStore) {
        this(
                canonicalEventStore,
                eventDetailsStore,
                new ClasspathFixtureLoader(),
                new ScheduledEventsV1Parser(),
                new EventDetailsV1Parser());
    }

    J4OfflineFixtureImportService(
            CanonicalEventStore canonicalEventStore,
            EventDetailsStore eventDetailsStore,
            ClasspathFixtureLoader fixtureLoader,
            ScheduledEventsV1Parser scheduledEventsParser,
            EventDetailsV1Parser eventDetailsParser) {
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore,
                "canonicalEventStore");
        this.eventDetailsStore = Objects.requireNonNull(eventDetailsStore, "eventDetailsStore");
        this.fixtureLoader = Objects.requireNonNull(fixtureLoader, "fixtureLoader");
        this.scheduledEventsParser = Objects.requireNonNull(
                scheduledEventsParser,
                "scheduledEventsParser");
        this.eventDetailsParser = Objects.requireNonNull(
                eventDetailsParser,
                "eventDetailsParser");
    }

    @Transactional
    public J4OfflineFixtureImportResult importNominalCorpus() {
        return importFixturePair(SCHEDULED_EVENT_MANIFEST, EVENT_DETAIL_MANIFEST);
    }

    J4OfflineFixtureImportResult importFixturePair(
            String scheduledManifest,
            String eventDetailManifest) {
        LoadedFixture scheduledFixture = fixtureLoader.load(scheduledManifest);
        LoadedFixture detailFixture = fixtureLoader.load(eventDetailManifest);
        if (scheduledFixture.manifest().fixtureOrigin() != FixtureOrigin.SYNTHETIC
                || detailFixture.manifest().fixtureOrigin() != FixtureOrigin.SYNTHETIC) {
            throw new IllegalArgumentException("J4 offline import accepts synthetic fixtures only");
        }

        var scheduledResult = scheduledEventsParser.parse(scheduledFixture);
        if (scheduledResult.status() != ScheduledEventsParseStatus.PARSED) {
            throw new J4OfflineFixtureImportException(
                    J4OfflineFixtureImportError.SCHEDULED_FIXTURE_INCOMPATIBLE);
        }
        ScheduledEventsPage page = scheduledResult.page().orElseThrow();
        if (page.payloadShape() != ScheduledEventsPage.PayloadShape.EVENT_LIST
                || page.events().size() != 1) {
            throw new J4OfflineFixtureImportException(
                    J4OfflineFixtureImportError.SCHEDULED_FIXTURE_EVENT_COUNT);
        }
        ScheduledEvent scheduledEvent = page.events().getFirst();

        var detailResult = eventDetailsParser.parse(detailFixture);
        if (detailResult.status() != EventDetailsParseStatus.PARSED) {
            throw new J4OfflineFixtureImportException(
                    J4OfflineFixtureImportError.DETAIL_FIXTURE_INCOMPATIBLE);
        }
        EventDetails eventDetails = detailResult.details().orElseThrow();
        if (eventDetails.providerEventId() != scheduledEvent.providerEventId()) {
            throw new J4OfflineFixtureImportException(
                    J4OfflineFixtureImportError.EVENT_ID_MISMATCH);
        }

        EventSourceTrace scheduledSource = EventSourceTrace.syntheticFixture(
                scheduledFixture.manifest().fixtureId(),
                scheduledFixture.rawSha256(),
                ScheduledEventsV1Parser.PARSER_VERSION,
                scheduledFixture.manifest().recordedAt());
        EventSourceTrace detailSource = EventSourceTrace.syntheticFixture(
                detailFixture.manifest().fixtureId(),
                detailFixture.rawSha256(),
                EventDetailsV1Parser.PARSER_VERSION,
                detailFixture.manifest().recordedAt());
        CanonicalEventIdentity identity = CanonicalEventIdentity.sofascore(
                scheduledEvent.providerEventId());

        var scheduledPersistence = canonicalEventStore.save(
                CanonicalEventObservation.from(scheduledEvent, scheduledSource));
        var detailEventPersistence = canonicalEventStore.save(
                CanonicalEventObservation.from(eventDetails.asScheduledEvent(), detailSource));
        var detailPersistence = eventDetailsStore.save(EventDetailObservation.from(
                identity,
                eventDetails,
                detailSource));

        return new J4OfflineFixtureImportResult(
                identity.value(),
                eventDetails.startsAt(),
                detailEventPersistence.observationCount(),
                scheduledPersistence.inserted(),
                detailEventPersistence.inserted(),
                detailPersistence.inserted());
    }
}
