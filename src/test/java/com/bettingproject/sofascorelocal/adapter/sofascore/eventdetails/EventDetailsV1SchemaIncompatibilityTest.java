package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventDetailsV1SchemaIncompatibilityTest {

    private final ClasspathFixtureLoader loader = new ClasspathFixtureLoader();
    private final EventDetailsV1Parser parser = new EventDetailsV1Parser();

    @Test
    void rejectsAMissingRequiredTeamWithoutExposingPartialDetails() {
        var result = parser.parse(loader.load(
                "fixtures/schema-breaks/event-details-required-field-missing.manifest.json"));

        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.details()).isEmpty();
        assertThat(result.problems())
                .extracting(EventDetailsParseProblem::path)
                .contains("$.awayTeam");
        assertThat(result.problems())
                .extracting(EventDetailsParseProblem::code)
                .contains(EventDetailsParseProblem.Code.REQUIRED_FIELD_MISSING);
    }

    @Test
    void rejectsANumericIdentifierChangedToTextWithoutCoercion() {
        var result = parser.parse(loader.load(
                "fixtures/schema-breaks/event-details-id-as-string.manifest.json"));

        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.details()).isEmpty();
        assertThat(result.problems())
                .anySatisfy(problem -> {
                    assertThat(problem.code())
                            .isEqualTo(EventDetailsParseProblem.Code.TYPE_MISMATCH);
                    assertThat(problem.path()).isEqualTo("$.id");
                });
    }

    @Test
    void rejectsAnotherEndpointBeforeAttemptingAnyMapping() {
        var result = parser.parse(loader.load(
                "fixtures/scheduled-events/nominal.manifest.json"));

        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.details()).isEmpty();
        assertThat(result.problems())
                .singleElement()
                .satisfies(problem -> assertThat(problem.code())
                        .isEqualTo(EventDetailsParseProblem.Code.UNSUPPORTED_ENDPOINT));
    }
}
