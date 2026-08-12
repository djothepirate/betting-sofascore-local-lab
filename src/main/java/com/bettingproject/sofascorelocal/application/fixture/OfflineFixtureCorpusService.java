package com.bettingproject.sofascorelocal.application.fixture;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.FixtureLoadingException;
import com.bettingproject.sofascorelocal.fixture.FixtureOrigin;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class OfflineFixtureCorpusService {

    private static final List<String> SCHEDULED_EVENTS_MANIFESTS = List.of(
            "fixtures/scheduled-events/nominal.manifest.json",
            "fixtures/scheduled-events/nominal-property-order-variant.manifest.json",
            "fixtures/scheduled-events/optional-field-missing.manifest.json",
            "fixtures/scheduled-events/empty-events-array.manifest.json",
            "fixtures/scheduled-events/unknown-extra-field.manifest.json",
            "fixtures/schema-breaks/scheduled-events-required-field-missing.manifest.json",
            "fixtures/schema-breaks/scheduled-events-numeric-field-as-string.manifest.json",
            "fixtures/schema-breaks/scheduled-events-unexpected-object.manifest.json",
            "fixtures/schema-breaks/scheduled-events-unexpected-html.manifest.json");

    private final ClasspathFixtureLoader fixtureLoader;
    private final ScheduledEventsV1Parser parser;

    public OfflineFixtureCorpusService() {
        this(new ClasspathFixtureLoader(), new ScheduledEventsV1Parser());
    }

    OfflineFixtureCorpusService(
            ClasspathFixtureLoader fixtureLoader,
            ScheduledEventsV1Parser parser) {
        this.fixtureLoader = Objects.requireNonNull(fixtureLoader, "fixtureLoader");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public FixtureCorpusOverview loadOverview() {
        int availableCount = 0;
        int parsedCount = 0;
        int schemaIncompatibleCount = 0;
        int unexpectedContentCount = 0;
        int loadingFailureCount = 0;

        for (String manifestResource : SCHEDULED_EVENTS_MANIFESTS) {
            try {
                ScheduledEventsParseStatus status = parser.parse(
                        fixtureLoader.load(manifestResource)).status();
                availableCount++;
                switch (status) {
                    case PARSED -> parsedCount++;
                    case SCHEMA_INCOMPATIBLE -> schemaIncompatibleCount++;
                    case UNEXPECTED_CONTENT -> unexpectedContentCount++;
                }
            }
            catch (FixtureLoadingException exception) {
                loadingFailureCount++;
            }
        }

        return new FixtureCorpusOverview(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                FixtureOrigin.SYNTHETIC,
                false,
                ScheduledEventsV1Parser.PARSER_VERSION,
                SCHEDULED_EVENTS_MANIFESTS.size(),
                availableCount,
                parsedCount,
                schemaIncompatibleCount,
                unexpectedContentCount,
                loadingFailureCount);
    }
}
