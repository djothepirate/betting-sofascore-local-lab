package com.bettingproject.sofascorelocal.application.fixture;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.FixtureOrigin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineFixtureCorpusServiceTest {

    @Test
    void inventoriesEveryVerifiedFixtureAndItsExpectedParserOutcome() {
        OfflineFixtureCorpusService service = new OfflineFixtureCorpusService();

        FixtureCorpusOverview overview = service.loadOverview();

        assertThat(overview.family()).isEqualTo(SofascoreEndpointType.SCHEDULED_EVENTS);
        assertThat(overview.origin()).isEqualTo(FixtureOrigin.SYNTHETIC);
        assertThat(overview.providerSchemaValidated()).isTrue();
        assertThat(overview.parserVersion()).isEqualTo("scheduled-events-v1");
        assertThat(overview.declaredCount()).isEqualTo(10);
        assertThat(overview.availableCount()).isEqualTo(10);
        assertThat(overview.parsedCount()).isEqualTo(6);
        assertThat(overview.schemaIncompatibleCount()).isEqualTo(3);
        assertThat(overview.unexpectedContentCount()).isEqualTo(1);
        assertThat(overview.loadingFailureCount()).isZero();
        assertThat(overview.availability())
                .isEqualTo(FixtureCorpusOverview.Availability.AVAILABLE_OFFLINE);
    }

    @Test
    void reportsAnIncompleteCorpusInsteadOfFailingTheDashboardWhenResourcesAreMissing() {
        ClassLoader emptyClassLoader = new ClassLoader(null) { };
        OfflineFixtureCorpusService service = new OfflineFixtureCorpusService(
                new ClasspathFixtureLoader(emptyClassLoader),
                new ScheduledEventsV1Parser());

        FixtureCorpusOverview overview = service.loadOverview();

        assertThat(overview.declaredCount()).isEqualTo(10);
        assertThat(overview.availableCount()).isZero();
        assertThat(overview.parsedCount()).isZero();
        assertThat(overview.schemaIncompatibleCount()).isZero();
        assertThat(overview.unexpectedContentCount()).isZero();
        assertThat(overview.loadingFailureCount()).isEqualTo(10);
        assertThat(overview.availability())
                .isEqualTo(FixtureCorpusOverview.Availability.INCOMPLETE);
    }
}
