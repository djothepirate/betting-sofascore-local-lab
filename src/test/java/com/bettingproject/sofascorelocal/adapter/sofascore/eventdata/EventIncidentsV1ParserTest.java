package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV1ParserTest {

    private final ClasspathFixtureLoader loader = new ClasspathFixtureLoader();
    private final EventIncidentsV1Parser parser = new EventIncidentsV1Parser();

    @Test
    void parsesOrderedIncidentsAndKeepsTheirOptionalContext() {
        var result = parser.parse(loader.load(
                "fixtures/event-incidents/nominal.manifest.json"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(completeness.scorePercent()).isEqualTo(100);
            assertThat(completeness.expectedSignals()).isEqualTo(3);
        });
        assertThat(result.data()).hasValueSatisfying(incidents -> {
            assertThat(incidents.providerEventId()).isEqualTo(900001L);
            assertThat(incidents.incidents()).hasSize(3);
            assertThat(incidents.incidents().getFirst().incidentType()).isEqualTo("goal");
            assertThat(incidents.incidents().getFirst().sideLabel()).isEqualTo("HOME");
            assertThat(incidents.incidents().getFirst().homeScore()).contains(1);
            assertThat(incidents.incidents().getFirst().awayScore()).contains(0);
        });
    }

    @Test
    void distinguishesAnExplicitEmptyIncidentListFromMissingData() {
        var result = parser.parse(loader.load(
                "fixtures/event-incidents/empty.manifest.json"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data()).hasValueSatisfying(incidents ->
                assertThat(incidents.incidents()).isEmpty());
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.EMPTY_VALID);
            assertThat(completeness.scorePercent()).isEqualTo(100);
            assertThat(completeness.expectedSignals()).isZero();
        });
    }

    @Test
    void rejectsAnIncidentMinuteEncodedAsTextWithoutPartialData() {
        var result = parser.parse(loader.load(
                "fixtures/schema-breaks/event-incidents-time-as-string.manifest.json"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.data()).isEmpty();
        assertThat(result.completeness()).isEmpty();
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.incidents[0].time");
    }
}
