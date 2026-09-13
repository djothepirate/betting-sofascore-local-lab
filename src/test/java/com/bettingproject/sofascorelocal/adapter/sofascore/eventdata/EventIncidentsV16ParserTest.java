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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventIncidentsV16ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-09-07T20:31:00Z");
    private static final long EVENT_ID = 900001L;
    private static final String AWARD = """
            {"incidentType":"inGamePenalty","incidentClass":"awarded","time":83,"isHome":true}
            """.strip();

    private final EventIncidentsV16Parser parser = new EventIncidentsV16Parser();

    @Test
    void preservesAnAwardWithoutInventingAShooterOrAShotOutcome() {
        RawPayloadEvidence payload = payload(envelope(AWARD));

        var result = parser.parse(2340L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion()).isEqualTo(EventIncidentsV16Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.evidence().recordedAt()).isEqualTo(RECEIVED_AT);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.problems()).isEmpty();
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).singleElement().satisfies(incident -> {
                assertThat(incident.incidentType()).isEqualTo("inGamePenalty");
                assertThat(incident.incidentClass()).contains("awarded");
                assertThat(incident.minute()).contains(83);
                assertThat(incident.home()).contains(true);
                assertThat(incident.playerName()).isEmpty();
                assertThat(incident.homeScore()).isEmpty();
                assertThat(incident.awayScore()).isEmpty();
                assertThat(incident.reason()).isEmpty();
                assertThat(incident.description()).isEmpty();
                assertThat(incident.goalOrigin()).isEmpty();
            });
        });
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(completeness.presentSignals()).isEqualTo(2);
            assertThat(completeness.expectedSignals()).isEqualTo(2);
            assertThat(completeness.missingPaths()).isEmpty();
        });

        var historicalV15 = new EventIncidentsV15Parser().parse(
                2340L, EVENT_ID, payload, RECEIVED_AT);
        assertThat(historicalV15.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historicalV15.evidence().parserVersion())
                .isEqualTo(EventIncidentsV15Parser.PARSER_VERSION);
        assertThat(historicalV15.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.code()).isEqualTo(J5ParseProblem.Code.VALUE_OUT_OF_RANGE);
            assertThat(problem.path()).isEqualTo("$.incidents[0].incidentClass");
        });
    }

    @Test
    void retainsASuppliedPlayerAndScoreWithoutTreatingThemAsThePenaltyResult() {
        var result = parse(envelope(withAwardFields("""
                "player":{"id":1001,"name":"Synthetic player"},"homeScore":1,"awayScore":0
                """.strip())));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data().orElseThrow().incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.incidentClass()).contains("awarded");
            assertThat(incident.playerName()).contains("Synthetic player");
            assertThat(incident.homeScore()).contains(1);
            assertThat(incident.awayScore()).contains(0);
            assertThat(incident.reason()).isEmpty();
        });
    }

    @Test
    void stillMeasuresAMissingSideAndKeepsMissedPenaltyCompletenessUnchanged() {
        var missingSide = parse(envelope(AWARD.replace(",\"isHome\":true", "")));
        assertThat(missingSide.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(missingSide.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.scorePercent()).isEqualTo(50);
            assertThat(completeness.missingPaths()).containsExactly("$.incidents[0].isHome");
        });

        String missed = envelope(AWARD.replace("awarded", "missed"));
        var current = parse(missed);
        var historical = new EventIncidentsV15Parser().parse(
                2340L, EVENT_ID, payload(missed), RECEIVED_AT);
        assertThat(current.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(current.data()).isEqualTo(historical.data());
        assertThat(current.completeness()).isEqualTo(historical.completeness());
        assertThat(current.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths()).containsExactly(
                    "$.incidents[0].player", "$.incidents[0].reason", "$.incidents[0].description");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"reason\":\"offTarget\"",
            "\"reason\":\"goalkeeperSave\"",
            "\"reason\":\"woodwork\"",
            "\"description\":\"Off target\"",
            "\"description\":\"Goalkeeper save\"",
            "\"description\":\"Woodwork\"",
            "\"reason\":\"offTarget\",\"description\":\"Off target\""
    })
    void rejectsAnAwardCarryingAMissedOutcome(String contradictoryFields) {
        var result = parse(envelope(withAwardFields(contradictoryFields)));

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems()).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo(J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH);
            assertThat(problem.path()).isEqualTo("$.incidents[0]");
        });
        assertThat(result.data()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"reason\":\"scored\"", "\"description\":\"Scored\"",
            "\"reason\":\"awarded\"", "\"description\":\"Awarded\"",
            "\"reason\":1", "\"description\":{}", "\"player\":{}"
    })
    void doesNotWidenOtherFieldsOrWeakenPlayerValidation(String invalidFields) {
        assertThat(parse(envelope(withAwardFields(invalidFields))).status())
                .isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"homeScore\":1", "\"awayScore\":1",
            "\"homeScore\":1,\"awayScore\":null", "\"homeScore\":null,\"awayScore\":1",
            "\"homeScore\":0,\"awayScore\":null", "\"homeScore\":null,\"awayScore\":0"
    })
    void reportsAnIncompleteScoreAsAStructuredFailureWithoutConstructingAPartialIncident(String fields) {
        var result = parse(envelope(withAwardFields(fields)));

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.evidence().parserVersion()).isEqualTo(EventIncidentsV16Parser.PARSER_VERSION);
        assertThat(result.data()).isEmpty();
        assertThat(result.completeness()).isEmpty();
        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.code()).isEqualTo(J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH);
            assertThat(problem.path()).isEqualTo("$.incidents[0]");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"homeScore\":null", "\"awayScore\":null", "\"homeScore\":null,\"awayScore\":null"
    })
    void retainsNullScoresAsAbsentWithoutInventingZero(String fields) {
        var result = parse(envelope(withAwardFields(fields)));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data().orElseThrow().incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.homeScore()).isEmpty();
            assertThat(incident.awayScore()).isEmpty();
        });
        assertThat(result.completeness().orElseThrow().status()).isEqualTo(J5CompletenessStatus.COMPLETE);
    }

    @Test
    void retainsAnExplicitZeroScorePairAndLeavesHistoricalFailureDiagnosticsUnchanged() {
        var result = parse(envelope(withAwardFields("\"homeScore\":0,\"awayScore\":0")));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data().orElseThrow().incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.homeScore()).contains(0);
            assertThat(incident.awayScore()).contains(0);
        });
        RawPayloadEvidence historicalInput = payload(envelope(
                withAwardFields("\"homeScore\":1").replace("awarded", "missed")));
        assertThatThrownBy(() -> new EventIncidentsV6Parser().parse(
                2340L, EVENT_ID, historicalInput, RECEIVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("home and away scores must be present together");
        assertThatThrownBy(() -> new EventIncidentsV15Parser().parse(
                2340L, EVENT_ID, historicalInput, RECEIVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("home and away scores must be present together");
    }

    @ParameterizedTest
    @ValueSource(strings = {"goal", "card", "substitution", "varDecision", "penaltyShootout"})
    void doesNotAcceptAwardedAsAnotherIncidentClass(String incidentType) {
        var result = parse(envelope(AWARD.replace("inGamePenalty", incidentType)));

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems()).extracting(J5ParseProblem::path)
                .contains("$.incidents[0].incidentClass");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"Awarded\"", "\"awardedFuture\"", "\"scored\"", "1", "{}"})
    void keepsTheInGamePenaltyVocabularyExact(String invalidClass) {
        assertThat(parse(envelope(AWARD.replace("\"awarded\"", invalidClass))).status())
                .isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "301", "\"83\""})
    void stillRequiresAnOrdinaryInGameMinute(String invalidMinute) {
        assertThat(parse(envelope(AWARD.replace("\"time\":83", "\"time\":" + invalidMinute)))
                .status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
    }

    @Test
    void keepsTheInheritedV15TerminalShootoutAndItsClosedActionGuard() {
        String terminalShootout = """
                {"incidents":[
                  {"incidentType":"period","text":"PEN","period":"penalties",
                   "isLive":false,"time":999,"addedTime":999,"homeScore":1,"awayScore":0},
                  {"incidentType":"penaltyShootout","incidentClass":"missed","isHome":false,
                   "sequence":2,"homeScore":1,"awayScore":0,"footballPassingNetworkAction":[]},
                  {"incidentType":"penaltyShootout","incidentClass":"scored","isHome":true,
                   "sequence":1,"homeScore":1,"awayScore":0,"footballPassingNetworkAction":[]},
                  {"incidentType":"period","text":"FT","isLive":false,
                   "time":90,"homeScore":0,"awayScore":0}
                ]}
                """;
        var current = parse(terminalShootout);
        var historical = new EventIncidentsV15Parser().parse(
                2340L, EVENT_ID, payload(terminalShootout), RECEIVED_AT);

        assertThat(current.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(current.data()).isEqualTo(historical.data());
        assertThat(current.completeness()).isEqualTo(historical.completeness());
        assertThat(current.warnings()).isEqualTo(historical.warnings());
        assertThat(parse(terminalShootout.replace(
                "\"footballPassingNetworkAction\":[]", "\"footballPassingNetworkAction\":null"))
                .status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
    }

    private J5ParseResult<EventIncidents> parse(String json) {
        return parser.parse(2340L, EVENT_ID, payload(json), RECEIVED_AT);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String envelope(String incident) {
        return "{\"incidents\":[" + incident + "]}";
    }

    private static String withAwardFields(String fields) {
        return AWARD.substring(0, AWARD.length() - 1) + "," + fields + "}";
    }
}
