package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournamentAvailability;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledEventsV1ParserTest {

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
    private static final String QUALIFIED_PROVIDER_SHAPE =
            "fixtures/scheduled-events/qualified-provider-shape.manifest.json";
    private static final String QUALIFIED_PAGE_TWO_SHAPE =
            "fixtures/scheduled-events/qualified-page-two-shape.manifest.json";

    private static final ClasspathFixtureLoader LOADER = new ClasspathFixtureLoader();
    private static final ScheduledEventsV1Parser PARSER = new ScheduledEventsV1Parser();

    @Test
    void parsesTheNominalFixtureIntoTheLocalModelWithTraceability() {
        LoadedFixture fixture = LOADER.load(NOMINAL);

        ScheduledEventsParseResult result = PARSER.parse(fixture);

        assertThat(result.status()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.evidence())
                .isEqualTo(new ScheduledEventsParseEvidence(
                        "scheduled-events-nominal",
                        fixture.rawSha256(),
                        fixture.canonicalJsonSha256(),
                        Instant.parse("2026-08-12T00:00:00Z"),
                        "scheduled-events-v1"));

        ScheduledEventsPage page = result.page().orElseThrow();
        assertThat(page.hasNextPage()).isFalse();
        assertThat(page.events()).hasSize(1);

        ScheduledEvent event = page.events().getFirst();
        assertThat(event.providerEventId()).isEqualTo(900001L);
        assertThat(event.startsAt()).isEqualTo(Instant.parse("2026-08-12T14:00:00Z"));
        assertThat(event.homeTeam().providerTeamId()).isEqualTo(9101L);
        assertThat(event.homeTeam().name()).isEqualTo("Synthetic Home FC");
        assertThat(event.awayTeam().providerTeamId()).isEqualTo(9202L);
        assertThat(event.awayTeam().name()).isEqualTo("Synthetic Away FC");
        assertThat(event.status().type()).isEqualTo("notstarted");
        assertThat(event.status().description()).contains("Not started");
        assertThat(event.tournament()).hasValueSatisfying(tournament -> {
            assertThat(tournament.providerTournamentId()).isEqualTo(9303L);
            assertThat(tournament.name()).isEqualTo("Synthetic League");
        });
    }

    @Test
    void producesTheSameLocalModelWhenJsonObjectPropertiesAreReordered() {
        ScheduledEventsParseResult nominal = parse(NOMINAL);
        ScheduledEventsParseResult orderVariant = parse(ORDER_VARIANT);

        assertThat(orderVariant.status()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(orderVariant.page()).isEqualTo(nominal.page());
        assertThat(orderVariant.evidence().rawSha256())
                .isNotEqualTo(nominal.evidence().rawSha256());
        assertThat(orderVariant.evidence().canonicalJsonSha256())
                .isEqualTo(nominal.evidence().canonicalJsonSha256());
    }

    @Test
    void keepsOptionalAbsencesExplicitAndReportsThemAsWarnings() {
        ScheduledEventsParseResult result = parse(OPTIONAL_FIELD_MISSING);

        assertThat(result.status()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .extracting(
                        ScheduledEventsParseWarning::code,
                        ScheduledEventsParseWarning::path)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                ScheduledEventsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                                "$.events[0].status.description"),
                        org.assertj.core.groups.Tuple.tuple(
                                ScheduledEventsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                                "$.events[0].tournament"));

        ScheduledEvent event = result.page().orElseThrow().events().getFirst();
        assertThat(event.status().description()).isEmpty();
        assertThat(event.tournament()).isEmpty();
    }

    @Test
    void acceptsAnEmptyEventsArrayWithoutInventingAnEvent() {
        ScheduledEventsParseResult result = parse(EMPTY_EVENTS);

        assertThat(result.status()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(result.page().orElseThrow().events()).isEmpty();
        assertThat(result.warnings())
                .singleElement()
                .satisfies(warning -> {
                    assertThat(warning.code())
                            .isEqualTo(ScheduledEventsParseWarning.Code.EMPTY_EVENTS);
                    assertThat(warning.path()).isEqualTo("$.events");
                });
    }

    @Test
    void ignoresUnknownFieldsButReportsTheirExactPaths() {
        ScheduledEventsParseResult result = parse(UNKNOWN_EXTRA_FIELD);

        assertThat(result.status()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .allMatch(warning -> warning.code()
                        == ScheduledEventsParseWarning.Code.UNKNOWN_FIELD)
                .extracting(ScheduledEventsParseWarning::path)
                .containsExactlyInAnyOrder(
                        "$.syntheticPageMarker",
                        "$.events[0].syntheticExtra");
        assertThat(result.page())
                .isEqualTo(parse(NOMINAL).page());
    }

    @Test
    void parsesTheQualifiedScheduledTournamentShapeWithoutInventingEvents() {
        ScheduledEventsParseResult result = parse(QUALIFIED_PROVIDER_SHAPE);

        assertThat(result.status()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .extracting(
                        ScheduledEventsParseWarning::code,
                        ScheduledEventsParseWarning::path)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        ScheduledEventsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                        "$.scheduled[1].tournament.uniqueTournament"));

        ScheduledEventsPage page = result.page().orElseThrow();
        assertThat(page.payloadShape())
                .isEqualTo(ScheduledEventsPage.PayloadShape.SCHEDULED_TOURNAMENT_LIST);
        assertThat(page.events()).isEmpty();
        assertThat(page.hasNextPage()).isTrue();
        assertThat(page.scheduledTournaments()).hasSize(2);

        ScheduledTournamentAvailability first = page.scheduledTournaments().getFirst();
        assertThat(first.tournament().providerTournamentId()).isEqualTo(970001L);
        assertThat(first.tournament().name()).isEqualTo("Synthetic Regional League");
        assertThat(first.uniqueTournament()).hasValueSatisfying(tournament -> {
            assertThat(tournament.providerTournamentId()).isEqualTo(970101L);
            assertThat(tournament.name()).isEqualTo("Synthetic National Competition");
        });
        assertThat(first.timezoneEventCount())
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(0, 3, 3600, 2));

        ScheduledTournamentAvailability second = page.scheduledTournaments().get(1);
        assertThat(second.uniqueTournament()).isEmpty();
        assertThat(second.timezoneEventCount())
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(-10800, 1, 0, 1));
    }

    @Test
    void parsesTheQualifiedPageTwoEmptyCountArrayWithoutWeakeningOtherCounts() {
        ScheduledEventsParseResult result = parse(QUALIFIED_PAGE_TWO_SHAPE);

        assertThat(result.status()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .extracting(
                        ScheduledEventsParseWarning::code,
                        ScheduledEventsParseWarning::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                ScheduledEventsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                                "$.scheduled[1].tournament.uniqueTournament"),
                        org.assertj.core.groups.Tuple.tuple(
                                ScheduledEventsParseWarning.Code.EMPTY_TIMEZONE_EVENT_COUNT,
                                "$.scheduled[1].timezoneEventCount"));

        ScheduledEventsPage page = result.page().orElseThrow();
        assertThat(page.hasNextPage()).isTrue();
        assertThat(page.scheduledTournaments()).hasSize(2);
        assertThat(page.scheduledTournaments().getFirst().timezoneEventCount())
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(0, 2, 3600, 1));
        assertThat(page.scheduledTournaments().get(1).timezoneEventCount()).isEmpty();
    }

    private static ScheduledEventsParseResult parse(String manifestResource) {
        return PARSER.parse(LOADER.load(manifestResource));
    }
}
