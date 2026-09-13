package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventDetailsV3ParserTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-09-08T09:00:00Z");
    private final EventDetailsV3Parser parser = new EventDetailsV3Parser();

    @Test
    void readsAwardAndDisplayOnlyWithoutChangingTheTechnicalStatusOrProvenance() {
        RawPayloadEvidence payload = payload(envelope("""
                ,"isAwarded":true,"homeScore":{"display":0,"current":9},
                "awayScore":{"display":999,"normaltime":2}
                """));
        var parsed = parser.parse(381, payload, RECEIVED_AT);
        assertThat(parsed.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(parsed.evidence()).isEqualTo(new EventDetailsParseEvidence(
                "snapshot:381", payload.sha256(), java.util.Optional.empty(), RECEIVED_AT,
                EventDetailsV3Parser.PARSER_VERSION));
        assertThat(parsed.details()).hasValueSatisfying(details -> {
            assertThat(details.isAwarded()).contains(true);
            assertThat(details.homeDisplayScore()).contains(0);
            assertThat(details.awayDisplayScore()).contains(999);
            assertThat(details.status().type()).isEqualTo("postponed");
            assertThat(details.status().description()).contains("Postponed");
        });
        assertThat(parsed.warnings()).extracting(EventDetailsParseWarning::path)
                .contains("$.event.homeScore.current", "$.event.awayScore.normaltime");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", ",\"isAwarded\":null,\"homeScore\":null,\"awayScore\":null",
            ",\"homeScore\":{},\"awayScore\":{}",
            ",\"homeScore\":{\"display\":null},\"awayScore\":{\"current\":7}"
    })
    void leavesMissingNullAndEmptyScoreObjectsAbsentWithoutFallingBackToCurrent(String fields) {
        var result = parser.parse(382, payload(envelope(fields)), RECEIVED_AT);
        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(result.details()).hasValueSatisfying(details -> {
            assertThat(details.isAwarded()).isEmpty();
            assertThat(details.homeDisplayScore()).isEmpty();
            assertThat(details.awayDisplayScore()).isEmpty();
        });
    }

    @Test
    void keepsFalseZeroAndAnAbsentOpposingScoreDistinct() {
        var result = parser.parse(383, payload(envelope(
                ",\"isAwarded\":false,\"homeScore\":{\"display\":0}")), RECEIVED_AT);
        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(result.details()).hasValueSatisfying(details -> {
            assertThat(details.isAwarded()).contains(false);
            assertThat(details.homeDisplayScore()).contains(0);
            assertThat(details.awayDisplayScore()).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "\"true\"", "{}", "[]"})
    void rejectsNonBooleanAwardFlagsAtTheirExactPath(String value) {
        var result = parser.parse(384, payload(envelope(
                ",\"isAwarded\":" + value + ",\"homeScore\":{\"display\":1}")), RECEIVED_AT);
        assertFailure(result, "$.event.isAwarded", EventDetailsParseProblem.Code.TYPE_MISMATCH);
    }

    @ParameterizedTest
    @CsvSource({"homeScore,[]", "awayScore,7", "homeScore,true", "awayScore,'\"2\"'"})
    void rejectsNonObjectScoreContainersAtTheirExactPath(String side, String value) {
        var result = parser.parse(385, payload(envelope(
                ",\"" + side + "\":" + value)), RECEIVED_AT);
        assertFailure(result, "$.event." + side, EventDetailsParseProblem.Code.TYPE_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1.2", "\"0\"", "true", "[]", "{}"})
    void rejectsNonIntegralDisplayValuesWithoutPartialDetails(String value) {
        var result = parser.parse(386, payload(envelope(
                ",\"homeScore\":{\"display\":0},\"awayScore\":{\"display\":" + value + "}")), RECEIVED_AT);
        assertFailure(result, "$.event.awayScore.display", EventDetailsParseProblem.Code.TYPE_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1000", "2147483648", "99999999999999999999999999"})
    void rejectsOutOfRangeScoresWithoutOverflow(String value) {
        var result = parser.parse(387, payload(envelope(
                ",\"homeScore\":{\"display\":" + value + "}")), RECEIVED_AT);
        assertFailure(result, "$.event.homeScore.display", EventDetailsParseProblem.Code.VALUE_OUT_OF_RANGE);
    }

    @Test
    void removesOnlyTheThreeNewKnownFieldWarningsAndPreservesAllOtherV2Warnings() {
        var bytes = payload(EventDetailsV2ParserTest.nominal(388)
                .replace("\"customId\":", "\"isAwarded\":false,\"homeScore\":{},\"awayScore\":{},\"customId\":"));
        var historical = new EventDetailsV2Parser().parse(388, bytes, RECEIVED_AT);
        var current = parser.parse(388, bytes, RECEIVED_AT);
        Set<String> newlyKnown = Set.of("$.event.isAwarded", "$.event.homeScore", "$.event.awayScore");
        assertThat(historical.warnings()).filteredOn(w -> newlyKnown.contains(w.path())).hasSize(3);
        assertThat(current.warnings()).containsExactlyElementsOf(historical.warnings().stream()
                .filter(w -> w.code() != EventDetailsParseWarning.Code.UNKNOWN_FIELD
                        || !newlyKnown.contains(w.path())).toList());
        assertThat(historical.details().orElseThrow().isAwarded()).isEmpty();
        assertThat(current.details().orElseThrow().isAwarded()).contains(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{bad-json", "{\"event\":{},\"event\":{}}", "{} {}", "[]", "{}"})
    void preservesV2FailureClassificationWithV3Evidence(String json) {
        var bytes = payload(json);
        var historical = new EventDetailsV2Parser().parse(389, bytes, RECEIVED_AT);
        var current = parser.parse(389, bytes, RECEIVED_AT);
        assertThat(current.status()).isEqualTo(historical.status());
        assertThat(current.problems()).isEqualTo(historical.problems());
        assertThat(current.warnings()).isEqualTo(historical.warnings());
        assertThat(current.details()).isEmpty();
        assertThat(current.evidence().parserVersion()).isEqualTo(EventDetailsV3Parser.PARSER_VERSION);
        assertThat(current.evidence().rawSha256()).isEqualTo(bytes.sha256());
    }

    private static void assertFailure(EventDetailsParseResult result, String path, EventDetailsParseProblem.Code code) {
        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.details()).isEmpty();
        assertThat(result.evidence().parserVersion()).isEqualTo(EventDetailsV3Parser.PARSER_VERSION);
        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.path()).isEqualTo(path);
            assertThat(problem.code()).isEqualTo(code);
        });
    }

    private static String envelope(String fields) {
        return """
                {"event":{"id":38001,"startTimestamp":1786793400,
                "homeTeam":{"id":1,"name":"Synthetic Home"},
                "awayTeam":{"id":2,"name":"Synthetic Away"},
                "status":{"type":"postponed","description":"Postponed"}%s}}
                """.formatted(fields);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }
}
