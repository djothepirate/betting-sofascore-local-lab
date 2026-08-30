package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV15ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-30T04:31:58Z");
    private static final long EVENT_ID = 16691018L;
    private static final String EMPTY_ACTION_PATTERN =
            "\"footballPassingNetworkAction\": \\[\\]";
    private static final String EMPTY_ACTION_LINE_PATTERN =
            "(?m)^      \"footballPassingNetworkAction\": \\[\\],\\R";

    private final EventIncidentsV15Parser parser = new EventIncidentsV15Parser();

    @Test
    void acceptsEmptyActionArraysOnlyInsideTheCoherentTerminalUnminutedShootout() throws IOException {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(fixture().getBytes(StandardCharsets.UTF_8));

        var result = parser.parse(717L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV15Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .filteredOn(warning -> warning.code()
                        == J5ParseWarning.Code.PROVIDER_SHOOTOUT_MINUTE_ABSENT)
                .hasSize(3);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths())
                    .contains(
                            "$.incidents[0].time",
                            "$.incidents[1].time",
                            "$.incidents[2].time");
        });
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(4);
            assertThat(data.incidents().getFirst().periodText()).contains("PEN");
            assertThat(data.incidents().getFirst().minute()).isEmpty();
            assertThat(data.incidents().get(1).minute()).isEmpty();
            assertThat(data.incidents().get(2).minute()).isEmpty();
        });

        var historicalV14 = new EventIncidentsV14Parser().parse(
                717L, EVENT_ID, payload, RECEIVED_AT);
        assertThat(historicalV14.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historicalV14.problems())
                .extracting(J5ParseProblem::path)
                .contains(
                        "$.incidents[0].time",
                        "$.incidents[1].footballPassingNetworkAction[0].time",
                        "$.incidents[2].footballPassingNetworkAction[0].time");
    }

    @Test
    void retainsTheHistoricalAbsentPropertyAndAcceptsEquivalentFormsInOneShootout()
            throws IOException {
        String withBothPropertiesAbsent = fixture()
                .replaceAll(EMPTY_ACTION_LINE_PATTERN, "");
        String withOneAbsentAndOneEmpty = fixture()
                .replaceFirst(EMPTY_ACTION_LINE_PATTERN, "");

        assertThat(parse(withBothPropertiesAbsent).status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(parse(withOneAbsentAndOneEmpty).status()).isEqualTo(J5ParseStatus.PARSED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{}]", "[{\"time\":\"98\"}]", "[{\"time\":301}]", "[1]"})
    void rejectsANonEmptyIncoherentActionArrayInsteadOfTreatingItAsAbsent(
            String nonEmptyArray) throws IOException {
        String json = fixture().replaceFirst(
                EMPTY_ACTION_PATTERN,
                "\"footballPassingNetworkAction\": " + nonEmptyArray);

        var result = parse(json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .anyMatch(path -> path.startsWith(
                        "$.incidents[1].footballPassingNetworkAction[0]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "\"invalid\"", "1", "true"})
    void rejectsWrongActionTypes(String invalidValue) throws IOException {
        String json = fixture().replaceFirst(
                EMPTY_ACTION_PATTERN,
                "\"footballPassingNetworkAction\": " + invalidValue);

        assertThat(parse(json).status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
    }

    @Test
    void rejectsAShootoutMixingTimedAndUnminutedAttempts() throws IOException {
        String json = fixture().replaceFirst(
                "\"incidentClass\": \"scored\"",
                "\"incidentClass\": \"scored\",\n      \"time\": 98");

        var result = parse(json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains(
                        "$.incidents[0].time",
                        "$.incidents[2].footballPassingNetworkAction[0].time");
    }

    @Test
    void keepsSequenceAndTerminalScoreCoherenceClosedForEmptyArrays() throws IOException {
        String sequenceGap = fixture().replace("\"sequence\": 1", "\"sequence\": 3");
        String scoreMismatch = fixture().replace(
                "\"text\": \"PEN\",\n      \"homeScore\": 2",
                "\"text\": \"PEN\",\n      \"homeScore\": 3");

        assertThat(parse(sequenceGap).status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(parse(scoreMismatch).status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
    }

    @Test
    void retainsV14LiveExtraTimeAndClosedPeriodSemantics() {
        var liveExtraTime = parse("""
                {"incidents":[{
                  "incidentType":"period","text":"Extra time","isLive":true,
                  "time":120,"addedTime":999,"homeScore":1,"awayScore":1
                }]}
                """);
        var futurePeriod = parse("""
                {"incidents":[{
                  "incidentType":"period","text":"Future extra period","isLive":true,
                  "time":120,"homeScore":1,"awayScore":1
                }]}
                """);

        assertThat(liveExtraTime.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(futurePeriod.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
    }

    private J5ParseResult<EventIncidents> parse(String json) {
        return parser.parse(
                717L,
                EVENT_ID,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);
    }

    private String fixture() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/fixtures/provider-j5/incidents-terminal-shootout-empty-actions.json")) {
            return new String(java.util.Objects.requireNonNull(input).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
