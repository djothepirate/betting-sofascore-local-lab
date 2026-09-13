package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV17ParserTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-09-07T21:39:41.935Z");
    private static final long EVENT_ID = 900002L;
    private static final String CARD = """
            {"incidents":[{
              "incidentType":"card","incidentClass":"red","time":64,"isHome":false,
              "rescinded":false,"player":{"id":1002,"name":"Synthetic player"},
              "reason":"Professional handball"
            }]}
            """;

    private final EventIncidentsV17Parser parser = new EventIncidentsV17Parser();

    @Test
    void acceptsTheExactCardReasonWithUnchangedRawAndNormalizedProvenance() {
        RawPayloadEvidence raw = payload(CARD);
        var result = parser.parse(2427L, EVENT_ID, raw, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.evidence().parserVersion()).isEqualTo(EventIncidentsV17Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(raw.sha256());
        assertThat(result.evidence().recordedAt()).isEqualTo(RECEIVED_AT);
        assertThat(result.completeness().orElseThrow().status()).isEqualTo(J5CompletenessStatus.COMPLETE);
        assertThat(result.data().orElseThrow().incidents()).singleElement().satisfies(card -> {
            assertThat(card.incidentType()).isEqualTo("card");
            assertThat(card.incidentClass()).contains("red");
            assertThat(card.reason()).contains("Professional handball");
            assertThat(card.motifLabel()).isEqualTo("Professional handball");
            assertThat(card.minute()).contains(64);
            assertThat(card.addedTime()).isEmpty();
            assertThat(card.home()).contains(false);
            assertThat(card.rescinded()).contains(false);
        });
        var observation = J5EventDataObservation.from(
                CanonicalEventIdentity.sofascore(EVENT_ID), result.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(2427L, raw.sha256(),
                        EventIncidentsV17Parser.PARSER_VERSION, RECEIVED_AT),
                result.completeness().orElseThrow());
        assertThat(observation.source().parserVersion()).isEqualTo(EventIncidentsV17Parser.PARSER_VERSION);
        assertThat(observation.source().payloadSha256()).isEqualTo(raw.sha256());

        var historical = new EventIncidentsV16Parser().parse(2427L, EVENT_ID, raw, RECEIVED_AT);
        assertThat(historical.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historical.evidence().parserVersion()).isEqualTo(EventIncidentsV16Parser.PARSER_VERSION);
        assertThat(historical.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.code()).isEqualTo(J5ParseProblem.Code.VALUE_OUT_OF_RANGE);
            assertThat(problem.path()).isEqualTo("$.incidents[0].reason");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Handball", "Professional foul last man", "Other reason",
            "Off the ball foul", "Leaving field", "Foul"})
    void retainsHistoricalCardReasonsWithoutChangingDataOrCompleteness(String reason) {
        String json = CARD.replace("Professional handball", reason);
        var current = parse(json);
        var historical = new EventIncidentsV16Parser().parse(2427L, EVENT_ID, payload(json), RECEIVED_AT);

        assertThat(current.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(current.data()).isEqualTo(historical.data());
        assertThat(current.completeness()).isEqualTo(historical.completeness());
        assertThat(current.warnings()).isEqualTo(historical.warnings());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"professional handball\"", "\"Professional Handball\"",
            "\"Professional handball future\"", "\"Unknown reason\"", "0", "false", "[]", "{}"})
    void keepsUnknownReasonsAndWrongJsonTypesClosed(String reasonJson) {
        var result = parse(CARD.replace("\"Professional handball\"", reasonJson));

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems()).extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].reason");
        assertThat(result.data()).isEmpty();
    }

    @Test
    void keepsAbsentAndNullReasonsOptionalAndPreservesExplicitZeroAddedTime() {
        String absent = CARD.replace(",\n  \"reason\":\"Professional handball\"", "");
        String explicitNull = CARD.replace("\"Professional handball\"", "null");
        var withoutReason = parse(absent);
        var nullReason = parse(explicitNull);
        assertThat(withoutReason.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(nullReason.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(withoutReason.data()).isEqualTo(nullReason.data());
        assertThat(nullReason.data().orElseThrow().incidents().getFirst().reason()).isEmpty();

        var zero = parse(CARD.replace("\"time\":64", "\"time\":0,\"addedTime\":0"));
        assertThat(zero.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(zero.data().orElseThrow().incidents().getFirst().minute()).contains(0);
        assertThat(zero.data().orElseThrow().incidents().getFirst().addedTime()).contains(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inGamePenalty", "penaltyShootout"})
    void doesNotAddTheCardReasonToPenaltyVocabularies(String incidentType) {
        String json = CARD.replace("\"card\"", "\"" + incidentType + "\"")
                .replace("\"red\"", "\"missed\"");
        var result = parse(json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems()).extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].reason");
    }

    @Test
    void preservesTheV16AwardAndStructuredAtomicScoreRules() {
        String awarded = """
                {"incidents":[{"incidentType":"inGamePenalty","incidentClass":"awarded",
                  "time":83,"isHome":true}]}
                """;
        var current = parse(awarded);
        var historical = new EventIncidentsV16Parser().parse(2427L, EVENT_ID, payload(awarded), RECEIVED_AT);
        assertThat(current.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(current.data()).isEqualTo(historical.data());
        assertThat(current.completeness()).isEqualTo(historical.completeness());

        var incompleteScore = parse(awarded.replace("\"time\":83", "\"time\":83,\"awayScore\":0"));
        assertThat(incompleteScore.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(incompleteScore.problems()).singleElement().satisfies(problem ->
                assertThat(problem.code()).isEqualTo(J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH));
    }

    private J5ParseResult<EventIncidents> parse(String json) {
        return parser.parse(2427L, EVENT_ID, payload(json), RECEIVED_AT);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }
}
