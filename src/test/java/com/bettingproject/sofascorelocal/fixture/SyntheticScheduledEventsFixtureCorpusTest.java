package com.bettingproject.sofascorelocal.fixture;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticScheduledEventsFixtureCorpusTest {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final ClasspathFixtureLoader LOADER = new ClasspathFixtureLoader();

    private static final String NOMINAL =
            "fixtures/scheduled-events/nominal.manifest.json";
    private static final String ORDER_VARIANT =
            "fixtures/scheduled-events/nominal-property-order-variant.manifest.json";
    private static final String OPTIONAL_FIELD_MISSING =
            "fixtures/scheduled-events/optional-field-missing.manifest.json";
    private static final String EMPTY_EVENTS =
            "fixtures/scheduled-events/empty-events-array.manifest.json";
    private static final String UNKNOWN_EXTRA_FIELD =
            "fixtures/scheduled-events/unknown-extra-field.manifest.json";
    private static final String REQUIRED_FIELD_MISSING =
            "fixtures/schema-breaks/scheduled-events-required-field-missing.manifest.json";
    private static final String NUMERIC_FIELD_AS_STRING =
            "fixtures/schema-breaks/scheduled-events-numeric-field-as-string.manifest.json";
    private static final String UNEXPECTED_OBJECT =
            "fixtures/schema-breaks/scheduled-events-unexpected-object.manifest.json";
    private static final String UNEXPECTED_HTML =
            "fixtures/schema-breaks/scheduled-events-unexpected-html.manifest.json";

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario(NOMINAL, "scheduled-events-nominal", FixtureContentKind.JSON),
            new Scenario(
                    ORDER_VARIANT,
                    "scheduled-events-nominal-property-order-variant",
                    FixtureContentKind.JSON),
            new Scenario(
                    OPTIONAL_FIELD_MISSING,
                    "scheduled-events-optional-field-missing",
                    FixtureContentKind.JSON),
            new Scenario(
                    EMPTY_EVENTS,
                    "scheduled-events-empty-events-array",
                    FixtureContentKind.JSON),
            new Scenario(
                    UNKNOWN_EXTRA_FIELD,
                    "scheduled-events-unknown-extra-field",
                    FixtureContentKind.JSON),
            new Scenario(
                    REQUIRED_FIELD_MISSING,
                    "scheduled-events-required-field-missing",
                    FixtureContentKind.JSON),
            new Scenario(
                    NUMERIC_FIELD_AS_STRING,
                    "scheduled-events-numeric-field-as-string",
                    FixtureContentKind.JSON),
            new Scenario(
                    UNEXPECTED_OBJECT,
                    "scheduled-events-unexpected-object",
                    FixtureContentKind.JSON),
            new Scenario(
                    UNEXPECTED_HTML,
                    "scheduled-events-unexpected-html",
                    FixtureContentKind.HTML));

    @Test
    void loadsTheCompleteSyntheticCorpusWithVerifiedMetadataAndIntegrity() {
        List<LoadedFixture> fixtures = SCENARIOS.stream()
                .map(scenario -> LOADER.load(scenario.manifestResource()))
                .toList();

        assertThat(fixtures).hasSize(9);
        assertThat(fixtures)
                .extracting(fixture -> fixture.manifest().fixtureId())
                .containsExactlyElementsOf(SCENARIOS.stream().map(Scenario::fixtureId).toList())
                .doesNotHaveDuplicates();
        assertThat(fixtures)
                .extracting(fixture -> fixture.manifest().payloadResource())
                .doesNotHaveDuplicates();
        assertThat(fixtures)
                .extracting(LoadedFixture::rawSha256)
                .doesNotHaveDuplicates();

        for (int index = 0; index < fixtures.size(); index++) {
            LoadedFixture fixture = fixtures.get(index);
            Scenario scenario = SCENARIOS.get(index);

            assertThat(fixture.contentKind()).isEqualTo(scenario.contentKind());
            assertThat(fixture.manifest().endpointType())
                    .isEqualTo(SofascoreEndpointType.SCHEDULED_EVENTS);
            assertThat(fixture.manifest().fixtureOrigin()).isEqualTo(FixtureOrigin.SYNTHETIC);
            assertThat(fixture.manifest().providerSchemaValidated()).isFalse();
            assertThat(fixture.manifest().recordedAt())
                    .isEqualTo(Instant.parse("2026-08-12T00:00:00Z"));
            assertThat(fixture.manifest().httpStatus()).isNull();
            assertThat(fixture.manifest().parserVersion()).isEqualTo("UNASSIGNED");
            assertThat(fixture.manifest().maximumBytes()).isEqualTo(4096);
            assertThat(fixture.manifest().minimized()).isFalse();
            assertThat(fixture.manifest().removedFields()).isEmpty();
            assertThat(fixture.sizeBytes())
                    .isPositive()
                    .isLessThanOrEqualTo(fixture.manifest().maximumBytes());

            if (scenario.contentKind() == FixtureContentKind.JSON) {
                assertThat(fixture.canonicalJsonSha256()).isPresent();
            }
            else {
                assertThat(fixture.canonicalJsonSha256()).isEmpty();
            }
        }
    }

    @Test
    void keepsCanonicalHashStableWhenOnlyObjectPropertyOrderChanges() {
        LoadedFixture nominal = LOADER.load(NOMINAL);
        LoadedFixture orderVariant = LOADER.load(ORDER_VARIANT);

        assertThat(nominal.rawSha256()).isNotEqualTo(orderVariant.rawSha256());
        assertThat(nominal.canonicalJsonSha256())
                .isEqualTo(orderVariant.canonicalJsonSha256())
                .contains("fbf6395ac9621a8228813f6a28172c26d0dcaf7ab76f9c983c14fe9ddb568d48");

        LoadedFixture contentChanged = LOADER.load(UNKNOWN_EXTRA_FIELD);
        assertThat(contentChanged.canonicalJsonSha256())
                .isNotEqualTo(nominal.canonicalJsonSha256());
    }

    @Test
    void payloadsRepresentTheirDeclaredSyntheticScenarios() throws JacksonException {
        JsonNode nominalEvent = firstEvent(NOMINAL);
        assertThat(nominalEvent.get("id").isIntegralNumber()).isTrue();
        assertThat(nominalEvent.has("tournament")).isTrue();
        assertThat(nominalEvent.get("status").has("description")).isTrue();

        JsonNode optionalFieldMissing = firstEvent(OPTIONAL_FIELD_MISSING);
        assertThat(optionalFieldMissing.has("tournament")).isFalse();
        assertThat(optionalFieldMissing.get("status").has("description")).isFalse();

        JsonNode requiredFieldMissing = firstEvent(REQUIRED_FIELD_MISSING);
        assertThat(requiredFieldMissing.has("id")).isFalse();

        JsonNode numericFieldAsString = firstEvent(NUMERIC_FIELD_AS_STRING);
        assertThat(numericFieldAsString.get("id").isString()).isTrue();

        JsonNode emptyEvents = json(EMPTY_EVENTS);
        assertThat(emptyEvents.get("events").isArray()).isTrue();
        assertThat(emptyEvents.get("events").size()).isZero();

        JsonNode unknownExtraField = json(UNKNOWN_EXTRA_FIELD);
        assertThat(unknownExtraField.has("syntheticPageMarker")).isTrue();
        assertThat(unknownExtraField.get("events").get(0).has("syntheticExtra")).isTrue();

        JsonNode unexpectedObject = json(UNEXPECTED_OBJECT);
        assertThat(unexpectedObject.get("events").isObject()).isTrue();

        assertThat(LOADER.load(UNEXPECTED_HTML).contentKind())
                .isEqualTo(FixtureContentKind.HTML);
    }

    private static JsonNode firstEvent(String manifestResource) throws JacksonException {
        return json(manifestResource).get("events").get(0);
    }

    private static JsonNode json(String manifestResource) throws JacksonException {
        return JSON_MAPPER.readTree(LOADER.load(manifestResource).rawPayload());
    }

    private record Scenario(
            String manifestResource,
            String fixtureId,
            FixtureContentKind contentKind) {
    }
}
