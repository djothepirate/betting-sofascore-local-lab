package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventDetailsV1ParserTest {

    private final ClasspathFixtureLoader loader = new ClasspathFixtureLoader();
    private final EventDetailsV1Parser parser = new EventDetailsV1Parser();

    @Test
    void parsesTheNominalMinimizedDetailAndKeepsFixtureEvidence() {
        var result = parser.parse(loader.load(
                "fixtures/event-details/nominal.manifest.json"));

        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.evidence().sourceReference()).isEqualTo("event-details-nominal");
        assertThat(result.evidence().rawSha256())
                .isEqualTo("824a54db1aecddab300bcf0b5fb35c4224c5fbc9c70884f962f651e0e62f3341");
        assertThat(result.evidence().parserVersion()).isEqualTo("event-details-v1");
        assertThat(result.details()).hasValueSatisfying(details -> {
            assertThat(details.providerEventId()).isEqualTo(900001L);
            assertThat(details.startsAt()).isEqualTo(Instant.ofEpochSecond(1786543200));
            assertThat(details.homeTeam().name()).isEqualTo("Synthetic Home FC");
            assertThat(details.awayTeam().name()).isEqualTo("Synthetic Away FC");
            assertThat(details.tournament()).hasValueSatisfying(tournament ->
                    assertThat(tournament.name()).isEqualTo("Synthetic League"));
            assertThat(details.venue()).hasValueSatisfying(venue -> {
                assertThat(venue.name()).isEqualTo("Synthetic Park");
                assertThat(venue.city()).contains("Local City");
            });
            assertThat(details.season()).hasValueSatisfying(season ->
                    assertThat(season.name()).isEqualTo("2026"));
            assertThat(details.round()).contains("1");
        });
    }

    @Test
    void toleratesUnknownFieldsButReportsTheirExactPaths() {
        var result = parser.parse(loader.load(
                "fixtures/event-details/unknown-extra-field.manifest.json"));

        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(result.details()).isPresent();
        assertThat(result.warnings())
                .filteredOn(warning ->
                        warning.code() == EventDetailsParseWarning.Code.UNKNOWN_FIELD)
                .extracting(EventDetailsParseWarning::path)
                .containsExactly("$.coverage", "$.venue.capacity");
    }

    @Test
    void parsesAnotherValidIdentityWithoutSilentlyRebindingIt() {
        var result = parser.parse(loader.load(
                "fixtures/event-details/other-event.manifest.json"));

        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(result.details()).hasValueSatisfying(details ->
                assertThat(details.providerEventId()).isEqualTo(900002L));
        assertThat(result.warnings())
                .extracting(EventDetailsParseWarning::code)
                .contains(EventDetailsParseWarning.Code.OPTIONAL_FIELD_MISSING);
    }
}
