package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventDetailsV2ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-15T10:00:00Z");
    private final EventDetailsV2Parser parser = new EventDetailsV2Parser();

    @Test
    void parsesTheProviderEnvelopeAndNestedRoundWithoutExposingUnknownValues() {
        var result = parser.parse(41L, payload(nominal(16386245L)), RECEIVED_AT);

        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(result.evidence().sourceReference()).isEqualTo("snapshot:41");
        assertThat(result.evidence().parserVersion()).isEqualTo("event-details-v2");
        assertThat(result.details()).hasValueSatisfying(details -> {
            assertThat(details.providerEventId()).isEqualTo(16386245L);
            assertThat(details.homeTeam().name()).isEqualTo("Saint-Etienne");
            assertThat(details.awayTeam().name()).isEqualTo("Clermont Foot");
            assertThat(details.status().type()).isEqualTo("finished");
            assertThat(details.tournament()).hasValueSatisfying(tournament ->
                    assertThat(tournament.name()).isEqualTo("Ligue 2"));
            assertThat(details.venue()).hasValueSatisfying(venue -> {
                assertThat(venue.name()).isEqualTo("Stade local");
                assertThat(venue.city()).contains("Saint-Etienne");
            });
            assertThat(details.season()).hasValueSatisfying(season ->
                    assertThat(season.name()).isEqualTo("2026"));
            assertThat(details.round()).contains("1");
        });
        assertThat(result.warnings())
                .anyMatch(warning -> warning.code() == EventDetailsParseWarning.Code.UNKNOWN_FIELD);
    }

    @Test
    void classifiesAMissingEventEnvelopeWithoutPartialDetails() {
        var result = parser.parse(
                42L,
                payload("{\"message\":\"not-an-event\"}"),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.details()).isEmpty();
        assertThat(result.problems())
                .anyMatch(problem -> problem.path().equals("$.event"));
    }

    @Test
    void classifiesInvalidJsonAsUnexpectedContent() {
        var result = parser.parse(43L, payload("{not-json"), RECEIVED_AT);

        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.UNEXPECTED_CONTENT);
        assertThat(result.details()).isEmpty();
        assertThat(result.problems()).singleElement()
                .satisfies(problem -> assertThat(problem.code())
                        .isEqualTo(EventDetailsParseProblem.Code.INVALID_JSON));
    }

    static String nominal(long eventId) {
        return """
                {
                  "event": {
                    "id": %d,
                    "startTimestamp": 1786793400,
                    "homeTeam": {"id": 1, "name": "Saint-Etienne", "slug": "ignored"},
                    "awayTeam": {"id": 2, "name": "Clermont Foot"},
                    "status": {"type": "finished", "description": "Ended", "code": 100},
                    "tournament": {"id": 3, "name": "Ligue 2", "slug": "ignored"},
                    "venue": {
                      "id": 4,
                      "stadium": {"name": "Stade local", "capacity": 42000},
                      "city": {"name": "Saint-Etienne", "country": "ignored"}
                    },
                    "season": {"id": 5, "name": "2026", "year": "2026"},
                    "roundInfo": {"round": 1, "name": "ignored"},
                    "customId": "ignored"
                  },
                  "extraEnvelopeField": true
                }
                """.formatted(eventId);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }
}
