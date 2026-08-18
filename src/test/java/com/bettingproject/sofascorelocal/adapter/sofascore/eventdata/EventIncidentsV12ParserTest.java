package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV12ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-18T12:28:26.143477Z");
    private static final long EVENT_ID = 16691018L;

    private final EventIncidentsV12Parser parser = new EventIncidentsV12Parser();

    @Test
    void acceptsAnExactTerminalShootoutWithoutAnyMinuteSourceWithoutInventingOne() {
        String json = coherentUnminutedTerminalShootout();
        RawPayloadEvidence payload = payload(json);

        var result = parser.parse(189L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV12Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths())
                    .contains(
                            "$.incidents[0].time",
                            "$.incidents[1].time",
                            "$.incidents[2].time",
                            "$.incidents[2].reason",
                            "$.incidents[2].description");
        });
        assertThat(result.warnings())
                .filteredOn(warning -> warning.code()
                        == J5ParseWarning.Code.PROVIDER_SHOOTOUT_MINUTE_ABSENT)
                .hasSize(3);
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(4);
            var marker = data.incidents().getFirst();
            var scored = data.incidents().get(1);
            var missed = data.incidents().get(2);
            assertThat(marker.periodText()).contains("PEN");
            assertThat(marker.minute()).isEmpty();
            assertThat(marker.minuteLabel()).isEqualTo("—");
            assertThat(scored.minute()).isEmpty();
            assertThat(scored.shootoutSequence()).contains(2);
            assertThat(scored.reason()).contains("scored");
            assertThat(missed.minute()).isEmpty();
            assertThat(missed.shootoutSequence()).contains(1);
            assertThat(missed.incidentClass()).contains("missed");
            assertThat(missed.reason()).isEmpty();
            assertThat(missed.description()).isEmpty();
            assertThat(missed.motifLabel()).isEqualTo("—");
        });

        var historicalV11 = new EventIncidentsV11Parser().parse(
                189L, EVENT_ID, payload, RECEIVED_AT);
        assertThat(historicalV11.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historicalV11.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly(
                        "$.incidents[0].time",
                        "$.incidents[1].footballPassingNetworkAction[0].time",
                        "$.incidents[2].footballPassingNetworkAction[0].time");
    }

    @ParameterizedTest
    @ValueSource(strings = {"inGamePenalty", "penaltyShootout"})
    void keepsAMissedPenaltyWithoutReasonOrDescriptionAsPartial(String incidentType) {
        String sequence = "penaltyShootout".equals(incidentType)
                ? ",\"sequence\":1"
                : "";
        String json = """
                {"incidents":[{
                  "incidentType":"%s","incidentClass":"missed",
                  "time":58,"isHome":true,
                  "player":{"id":301,"name":"Penalty Taker"}%s
                }]}
                """.formatted(incidentType, sequence);

        var result = parse(json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths())
                    .contains(
                            "$.incidents[0].reason",
                            "$.incidents[0].description");
        });
        assertThat(result.data()).hasValueSatisfying(data -> {
            var penalty = data.incidents().getFirst();
            assertThat(penalty.minute()).contains(58);
            assertThat(penalty.reason()).isEmpty();
            assertThat(penalty.description()).isEmpty();
            assertThat(penalty.motifLabel()).isEqualTo("—");
        });
    }

    @Test
    void keepsMissingMinutesClosedOutsideTheExactTerminalShootoutShape() {
        var isolatedShootout = parse("""
                {"incidents":[{
                  "incidentType":"penaltyShootout","incidentClass":"missed",
                  "isHome":false,"player":{"name":"Penalty Taker"},"sequence":1
                }]}
                """);
        assertThat(isolatedShootout.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(isolatedShootout.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].footballPassingNetworkAction[0].time");

        var inGamePenalty = parse("""
                {"incidents":[{
                  "incidentType":"inGamePenalty","incidentClass":"missed",
                  "isHome":false,"player":{"name":"Penalty Taker"}
                }]}
                """);
        assertThat(inGamePenalty.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(inGamePenalty.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].time");
    }

    @Test
    void rejectsASequenceGapInsteadOfTreatingItAsACompleteTerminalShootout() {
        String json = coherentUnminutedTerminalShootout().replace("\"sequence\":1", "\"sequence\":3");

        var result = parse(json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains(
                        "$.incidents[0].time",
                        "$.incidents[1].footballPassingNetworkAction[0].time",
                        "$.incidents[2].footballPassingNetworkAction[0].time");
    }

    @Test
    void rejectsAShootoutMixingTimedAndUnminutedAttempts() {
        String json = coherentUnminutedTerminalShootout()
                .replace("\"incidentType\":\"penaltyShootout\",\"incidentClass\":\"scored\"",
                        "\"incidentType\":\"penaltyShootout\",\"incidentClass\":\"scored\",\"time\":98");

        var result = parse(json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains(
                        "$.incidents[0].time",
                        "$.incidents[2].footballPassingNetworkAction[0].time");
    }

    @Test
    void rejectsATerminalScoreThatDoesNotMatchTheLastShootoutAttempt() {
        String json = coherentUnminutedTerminalShootout()
                .replace("\"text\":\"PEN\",\"homeScore\":2,\"awayScore\":1",
                        "\"text\":\"PEN\",\"homeScore\":3,\"awayScore\":1");

        var result = parse(json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains(
                        "$.incidents[0].time",
                        "$.incidents[1].footballPassingNetworkAction[0].time",
                        "$.incidents[2].footballPassingNetworkAction[0].time");
    }

    private J5ParseResult<EventIncidents> parse(String json) {
        return parser.parse(189L, EVENT_ID, payload(json), RECEIVED_AT);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String coherentUnminutedTerminalShootout() {
        return """
                {"incidents":[
                  {
                    "text":"PEN","homeScore":2,"awayScore":1,"isLive":false,
                    "period":"penalties","time":999,"addedTime":999,
                    "incidentType":"period"
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"scored",
                    "isHome":true,"player":{"id":302,"name":"Scoring Taker"},
                    "homeScore":2,"awayScore":1,"sequence":2,
                    "description":"Scored","reason":"scored"
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"missed",
                    "isHome":false,"player":{"id":301,"name":"Missing Taker"},
                    "homeScore":1,"awayScore":1,"sequence":1
                  },
                  {
                    "text":"FT","homeScore":1,"awayScore":1,"isLive":false,
                    "time":90,"addedTime":999,"incidentType":"period"
                  }
                ]}
                """;
    }
}
