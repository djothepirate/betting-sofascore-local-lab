package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class ScheduledEventsV1SchemaIncompatibilityTest {

    private static final String REQUIRED_FIELD_MISSING =
            "fixtures/schema-breaks/scheduled-events-required-field-missing.manifest.json";
    private static final String NUMERIC_FIELD_AS_STRING =
            "fixtures/schema-breaks/scheduled-events-numeric-field-as-string.manifest.json";
    private static final String UNEXPECTED_OBJECT =
            "fixtures/schema-breaks/scheduled-events-unexpected-object.manifest.json";
    private static final String UNEXPECTED_HTML =
            "fixtures/schema-breaks/scheduled-events-unexpected-html.manifest.json";

    private static final ClasspathFixtureLoader LOADER = new ClasspathFixtureLoader();
    private static final ScheduledEventsV1Parser PARSER = new ScheduledEventsV1Parser();

    @Test
    void reportsEveryMissingRequiredPathWithoutExposingAPartialPage() {
        LoadedFixture fixture = LOADER.load(REQUIRED_FIELD_MISSING);

        ScheduledEventsParseResult result = PARSER.parse(fixture);

        assertFailedResult(
                result,
                fixture,
                ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(
                        ScheduledEventsParseProblem::code,
                        ScheduledEventsParseProblem::path)
                .containsExactly(
                        tuple(
                                ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                                "$.events[0].id"),
                        tuple(
                                ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                                "$.events[0].status"));
        assertThat(result.warnings())
                .extracting(
                        ScheduledEventsParseWarning::code,
                        ScheduledEventsParseWarning::path)
                .containsExactly(tuple(
                        ScheduledEventsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                        "$.events[0].tournament"));
    }

    @Test
    void rejectsANumericIdentifierEncodedAsTextWithoutCoercion() {
        LoadedFixture fixture = LOADER.load(NUMERIC_FIELD_AS_STRING);

        ScheduledEventsParseResult result = PARSER.parse(fixture);

        assertFailedResult(
                result,
                fixture,
                ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(
                        ScheduledEventsParseProblem::code,
                        ScheduledEventsParseProblem::path)
                .containsExactly(
                        tuple(
                                ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                                "$.events[0].id"),
                        tuple(
                                ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                                "$.events[0].status"));
        assertThat(result.warnings())
                .extracting(
                        ScheduledEventsParseWarning::code,
                        ScheduledEventsParseWarning::path)
                .containsExactly(tuple(
                        ScheduledEventsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                        "$.events[0].tournament"));
    }

    @Test
    void rejectsAnObjectWhereTheEventsArrayIsRequired() {
        LoadedFixture fixture = LOADER.load(UNEXPECTED_OBJECT);

        ScheduledEventsParseResult result = PARSER.parse(fixture);

        assertFailedResult(
                result,
                fixture,
                ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(
                        ScheduledEventsParseProblem::code,
                        ScheduledEventsParseProblem::path)
                .containsExactly(tuple(
                        ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                        "$.events"));
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void classifiesHtmlBeforeJsonParsingAndKeepsTheFailureTraceable() {
        LoadedFixture fixture = LOADER.load(UNEXPECTED_HTML);

        ScheduledEventsParseResult result = PARSER.parse(fixture);

        assertFailedResult(
                result,
                fixture,
                ScheduledEventsParseStatus.UNEXPECTED_CONTENT);
        assertThat(result.problems())
                .extracting(
                        ScheduledEventsParseProblem::code,
                        ScheduledEventsParseProblem::path)
                .containsExactly(tuple(
                        ScheduledEventsParseProblem.Code.UNEXPECTED_CONTENT_KIND,
                        "$"));
        assertThat(result.warnings()).isEmpty();
        assertThat(result.evidence().canonicalJsonSha256()).isEmpty();
    }

    @Test
    void rejectsQualifiedProviderEntriesWithMissingIdentityAndStringCounts() {
        ScheduledEventsParseResult result = parseTransportJson("""
                {
                  "scheduled": [
                    {
                      "timezoneEventCount": {
                        "0": "1"
                      }
                    }
                  ],
                  "hasNextPage": false
                }
                """);

        assertThat(result.status()).isEqualTo(ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.page()).isEmpty();
        assertThat(result.problems())
                .extracting(
                        ScheduledEventsParseProblem::code,
                        ScheduledEventsParseProblem::path)
                .containsExactly(
                        tuple(
                                ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                                "$.scheduled[0].tournament"),
                        tuple(
                                ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                                "$.scheduled[0].timezoneEventCount.0"));
    }

    @Test
    void rejectsAnAmbiguousRootContainingBothSupportedShapes() {
        ScheduledEventsParseResult result = parseTransportJson("""
                {
                  "events": [],
                  "scheduled": [],
                  "hasNextPage": false
                }
                """);

        assertThat(result.status()).isEqualTo(ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.page()).isEmpty();
        assertThat(result.problems())
                .extracting(
                        ScheduledEventsParseProblem::code,
                        ScheduledEventsParseProblem::path)
                .containsExactly(tuple(
                        ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                        "$"));
    }

    private static void assertFailedResult(
            ScheduledEventsParseResult result,
            LoadedFixture fixture,
            ScheduledEventsParseStatus expectedStatus) {
        assertThat(result.status()).isEqualTo(expectedStatus);
        assertThat(result.page()).isEmpty();
        assertThat(result.problems())
                .isNotEmpty()
                .allSatisfy(problem -> assertThat(problem.message()).isNotBlank());
        assertThat(result.evidence())
                .isEqualTo(new ScheduledEventsParseEvidence(
                        fixture.manifest().fixtureId(),
                        fixture.rawSha256(),
                        fixture.canonicalJsonSha256(),
                        Instant.parse("2026-08-12T00:00:00Z"),
                        ScheduledEventsV1Parser.PARSER_VERSION));
    }

    private static ScheduledEventsParseResult parseTransportJson(String json) {
        Instant requestedAt = Instant.parse("2026-08-13T12:00:00Z");
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        return PARSER.parseTransportResponse(new ScheduledEventsTransportResponse(
                "SCHEDULED_EVENTS|date=2026-08-13|page=1",
                requestedAt,
                requestedAt.plusMillis(25),
                200,
                "application/json",
                Duration.ofMillis(25),
                RawPayloadEvidence.capture(payload)));
    }
}
