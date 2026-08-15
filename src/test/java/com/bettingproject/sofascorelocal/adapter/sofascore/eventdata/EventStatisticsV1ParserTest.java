package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventStatisticsV1ParserTest {

    private final ClasspathFixtureLoader loader = new ClasspathFixtureLoader();
    private final EventStatisticsV1Parser parser = new EventStatisticsV1Parser();

    @Test
    void parsesNominalStatisticsWithCompleteHomeAndAwayValues() {
        var result = parser.parse(loader.load(
                "fixtures/event-statistics/nominal.manifest.json"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(completeness.scorePercent()).isEqualTo(100);
            assertThat(completeness.expectedSignals()).isEqualTo(6);
            assertThat(completeness.missingPaths()).isEmpty();
        });
        assertThat(result.data()).hasValueSatisfying(statistics -> {
            assertThat(statistics.providerEventId()).isEqualTo(900001L);
            assertThat(statistics.metrics()).hasSize(3);
            assertThat(statistics.metrics().get(1).metricCode()).isEqualTo("totalShots");
            assertThat(statistics.metrics().get(1).homeValue()).contains("12");
            assertThat(statistics.metrics().get(1).awayValue()).contains("8");
        });
    }

    @Test
    void keepsAValidPartialResultAndReportsTheExactMissingValue() {
        var result = parser.parse(loader.load(
                "fixtures/event-statistics/partial-missing-away.manifest.json"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.scorePercent()).isEqualTo(75);
            assertThat(completeness.missingPaths()).containsExactly(
                    "$.statistics[0].groups[0].statisticsItems[0].away");
        });
        assertThat(result.warnings())
                .extracting(J5ParseWarning::path)
                .contains("$.coverage")
                .contains("$.statistics[0].groups[0].statisticsItems[0].away");
    }

    @Test
    void rejectsAProviderEventIdentifierEncodedAsTextWithoutPartialData() {
        var result = parser.parse(loader.load(
                "fixtures/schema-breaks/event-statistics-id-as-string.manifest.json"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.data()).isEmpty();
        assertThat(result.completeness()).isEmpty();
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.eventId");
    }
}
