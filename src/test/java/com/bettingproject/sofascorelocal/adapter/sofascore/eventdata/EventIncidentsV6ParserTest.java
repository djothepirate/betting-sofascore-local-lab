package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV6ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-17T12:00:00Z");
    private static final long EVENT_ID = 16281047L;
    private final EventIncidentsV6Parser parser = new EventIncidentsV6Parser();

    @Test
    void normalizesTheEightDocumentedFootballIncidentTypesWithoutLosingTheirBusinessSignals() {
        String json = """
                {"incidents":[
                  {
                    "incidentType":"period",
                    "time":90,
                    "addedTime":999,
                    "text":"FT",
                    "homeScore":2,
                    "awayScore":1
                  },
                  {
                    "incidentType":"substitution",
                    "incidentClass":"regular",
                    "time":83,
                    "isHome":true,
                    "injury":false,
                    "playerIn":{"id":2443678,"name":"Matviy Bodnar"},
                    "playerOut":{"id":2119385,"name":"Volodymyr Mulyk"}
                  },
                  {
                    "incidentType":"goal",
                    "incidentClass":"ownGoal",
                    "from":"owngoal",
                    "time":71,
                    "isHome":false,
                    "player":{"id":101,"name":"Goal Scorer"},
                    "assist1":{"id":102,"name":"Goal Assistant"},
                    "homeScore":1,
                    "awayScore":1
                  },
                  {
                    "incidentType":"card",
                    "incidentClass":"yellow",
                    "time":-5,
                    "benchTime":58,
                    "isHome":false,
                    "playerName":"Kerem Akturkoglu",
                    "reason":"Argument",
                    "rescinded":false
                  },
                  {
                    "incidentType":"injuryTime",
                    "time":45,
                    "addedTime":2,
                    "length":3
                  },
                  {
                    "incidentType":"varDecision",
                    "incidentClass":"goalAwarded",
                    "time":52,
                    "isHome":true,
                    "player":{"name":"Reviewed Player"},
                    "confirmed":true
                  },
                  {
                    "incidentType":"inGamePenalty",
                    "incidentClass":"missed",
                    "time":70,
                    "isHome":true,
                    "player":{"id":201,"name":"Penalty Taker"},
                    "reason":"offTarget",
                    "description":"Off target"
                  },
                  {
                    "incidentType":"penaltyShootout",
                    "incidentClass":"scored",
                    "isHome":false,
                    "player":{"id":301,"name":"Shootout Taker"},
                    "homeScore":4,
                    "awayScore":5,
                    "reason":"scored",
                    "description":"Scored",
                    "sequence":9,
                    "footballPassingNetworkAction":[{"time":98}]
                  }
                ]}
                """;

        var result = parse(101L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV6Parser.PARSER_VERSION);
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .extracting(J5ParseWarning::code)
                .contains(
                        J5ParseWarning.Code.PROVIDER_SENTINEL_NORMALIZED,
                        J5ParseWarning.Code.PROVIDER_BENCH_CARD_MINUTE_USED,
                        J5ParseWarning.Code.PROVIDER_NESTED_MINUTE_USED,
                        J5ParseWarning.Code.PROVIDER_ALIAS_NORMALIZED);
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(8);

            var period = data.incidents().get(0);
            assertThat(period.periodText()).contains("FT");
            assertThat(period.addedTime()).isEmpty();
            assertThat(period.homeScore()).contains(2);
            assertThat(period.awayScore()).contains(1);

            var substitution = data.incidents().get(1);
            assertThat(substitution.playerInName()).contains("Matviy Bodnar");
            assertThat(substitution.playerOutName()).contains("Volodymyr Mulyk");
            assertThat(substitution.injury()).contains(false);

            var goal = data.incidents().get(2);
            assertThat(goal.goalOrigin()).contains("ownGoal");
            assertThat(goal.assistName()).contains("Goal Assistant");

            var card = data.incidents().get(3);
            assertThat(card.minute()).contains(58);
            assertThat(card.playerName()).contains("Kerem Akturkoglu");
            assertThat(card.reason()).contains("Argument");
            assertThat(card.rescinded()).contains(false);

            var injuryTime = data.incidents().get(4);
            assertThat(injuryTime.injuryTimeLength()).contains(3);
            assertThat(injuryTime.addedTime()).contains(2);

            var varDecision = data.incidents().get(5);
            assertThat(varDecision.incidentClass()).contains("goalAwarded");
            assertThat(varDecision.varConfirmed()).contains(true);
            assertThat(varDecision.playerProviderId()).isEmpty();
            assertThat(varDecision.playerName()).contains("Reviewed Player");

            var inGamePenalty = data.incidents().get(6);
            assertThat(inGamePenalty.incidentClass()).contains("missed");
            assertThat(inGamePenalty.reason()).contains("offTarget");
            assertThat(inGamePenalty.description()).contains("Off target");

            var shootout = data.incidents().get(7);
            assertThat(shootout.minute()).contains(98);
            assertThat(shootout.shootoutSequence()).contains(9);
            assertThat(shootout.description()).contains("Scored");
        });
    }

    @Test
    void treatsMissingBusinessMetadataAsMeasuredPartialCompletenessInsteadOfSchemaFailure() {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "time":64,
                  "isHome":false
                }]}
                """;

        var result = parse(102L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths())
                    .contains(
                            "$.incidents[0].incidentClass",
                            "$.incidents[0].player");
        });
        assertThat(result.data()).hasValueSatisfying(data ->
                assertThat(data.incidents()).hasSize(1));
    }

    @Test
    void keepsAllEightIncidentTypesWhenExpectedDisplayMetadataIsMissing() {
        String json = """
                {"incidents":[
                  {"incidentType":"period","time":45},
                  {"incidentType":"substitution","time":46},
                  {"incidentType":"goal","time":47},
                  {"incidentType":"card","time":48},
                  {"incidentType":"injuryTime","time":49},
                  {"incidentType":"varDecision","time":50},
                  {"incidentType":"inGamePenalty","time":51},
                  {"incidentType":"penaltyShootout","time":52}
                ]}
                """;

        var result = parse(119L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths())
                    .contains(
                            "$.incidents[0].text",
                            "$.incidents[1].playerIn",
                            "$.incidents[2].player",
                            "$.incidents[3].incidentClass",
                            "$.incidents[4].length",
                            "$.incidents[5].confirmed",
                            "$.incidents[6].description",
                            "$.incidents[7].sequence");
        });
        assertThat(result.data()).hasValueSatisfying(data ->
                assertThat(data.incidents())
                        .extracting(incident -> incident.incidentType())
                        .containsExactly(
                                "period",
                                "substitution",
                                "goal",
                                "card",
                                "injuryTime",
                                "varDecision",
                                "inGamePenalty",
                                "penaltyShootout"));
    }

    @Test
    void acceptsAnObservedEmptyIncidentListAsAValidEmptyFamily() {
        var result = parse(103L, "{\"incidents\":[]}");

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.EMPTY_VALID));
        assertThat(result.data()).hasValueSatisfying(data ->
                assertThat(data.incidents()).isEmpty());
    }

    @Test
    void rejectsAnUnsupportedFootballIncidentType() {
        String json = """
                {"incidents":[{
                  "incidentType":"providerFutureType",
                  "time":12,
                  "isHome":true
                }]}
                """;

        var result = parse(104L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.incidents[0].incidentType");
    }

    @Test
    void rejectsAContradictoryPenaltyOutcomeTuple() {
        String json = """
                {"incidents":[{
                  "incidentType":"penaltyShootout",
                  "incidentClass":"missed",
                  "time":96,
                  "isHome":true,
                  "player":{"name":"Penalty Taker"},
                  "reason":"scored",
                  "description":"Scored",
                  "sequence":3
                }]}
                """;

        var result = parse(105L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::code)
                .contains(J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH);
    }

    @Test
    void keepsTheHistoricalV6SubstitutionClassClosedToRegular() {
        String json = """
                {"incidents":[{
                  "incidentType":"substitution",
                  "incidentClass":"injury",
                  "time":46,
                  "isHome":false,
                  "injury":true,
                  "playerIn":{"id":864921,"name":"Jack Grealish"},
                  "playerOut":{"id":851005,"name":"Jérémy Doku"}
                }]}
                """;

        var result = parse(121L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.incidents[0].incidentClass");
    }

    @ParameterizedTest
    @CsvSource({
        "HT, false",
        "FT, false",
        "ET, false",
        "PEN, false",
        "First half, true",
        "Second half, true"
    })
    void acceptsEveryDocumentedPeriodLabel(String text, boolean live) {
        String json = """
                {"incidents":[{
                  "incidentType":"period",
                  "time":45,
                  "text":"%s",
                  "isLive":%s,
                  "homeScore":0,
                  "awayScore":0
                }]}
                """.formatted(text, live);

        assertThat(parse(110L, json).status()).isEqualTo(J5ParseStatus.PARSED);
    }

    @Test
    void rejectsALivePeriodLabelExplicitlyMarkedAsNotLive() {
        String json = """
                {"incidents":[{
                  "incidentType":"period",
                  "time":45,
                  "text":"First half",
                  "isLive":false,
                  "homeScore":0,
                  "awayScore":0
                }]}
                """;

        var result = parse(111L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::code)
                .contains(J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH);
    }

    @Test
    void keepsALivePeriodLabelWithoutItsLiveFlagAsMeasuredPartialData() {
        String json = """
                {"incidents":[{
                  "incidentType":"period",
                  "time":45,
                  "text":"First half",
                  "homeScore":0,
                  "awayScore":0
                }]}
                """;

        var result = parse(118L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths())
                    .containsExactly("$.incidents[0].isLive");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"regular", "penalty", "ownGoal"})
    void acceptsEveryDocumentedGoalClass(String incidentClass) {
        String from = switch (incidentClass) {
            case "penalty" -> ",\"from\":\"penalty\"";
            case "ownGoal" -> ",\"from\":\"owngoal\"";
            default -> "";
        };
        String json = """
                {"incidents":[{
                  "incidentType":"goal",
                  "incidentClass":"%s",
                  "time":32,
                  "isHome":true,
                  "player":{"name":"Scorer"},
                  "homeScore":1,
                  "awayScore":0%s
                }]}
                """.formatted(incidentClass, from);

        assertThat(parse(112L, json).status()).isEqualTo(J5ParseStatus.PARSED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yellow", "red", "yellowRed"})
    void acceptsEveryDocumentedCardClass(String incidentClass) {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "incidentClass":"%s",
                  "time":64,
                  "isHome":false,
                  "player":{"name":"Booked Player"}
                }]}
                """.formatted(incidentClass);

        assertThat(parse(113L, json).status()).isEqualTo(J5ParseStatus.PARSED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Argument",
        "Foul",
        "Violent conduct",
        "Simulation",
        "Time wasting",
        "Professional foul last man",
        "Handball",
        "Persistent fouling",
        "Unsporting behaviour",
        "Unallowed field entering"
    })
    void acceptsEveryDocumentedCardReason(String reason) {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "incidentClass":"yellow",
                  "time":64,
                  "isHome":false,
                  "player":{"name":"Booked Player"},
                  "reason":"%s"
                }]}
                """.formatted(reason);

        assertThat(parse(114L, json).status()).isEqualTo(J5ParseStatus.PARSED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "goalNotAwarded",
        "goalAwarded",
        "penaltyAwarded",
        "penaltyNotAwarded",
        "redCardGiven",
        "cardUpgrade",
        "review"
    })
    void acceptsEveryDocumentedVarClass(String incidentClass) {
        String json = """
                {"incidents":[{
                  "incidentType":"varDecision",
                  "incidentClass":"%s",
                  "time":52,
                  "isHome":true,
                  "player":{"name":"Reviewed Player"},
                  "confirmed":true
                }]}
                """.formatted(incidentClass);

        assertThat(parse(115L, json).status()).isEqualTo(J5ParseStatus.PARSED);
    }

    @ParameterizedTest
    @CsvSource({
        "inGamePenalty, missed, offTarget, 'Off target'",
        "inGamePenalty, missed, goalkeeperSave, 'Goalkeeper save'",
        "penaltyShootout, scored, scored, Scored",
        "penaltyShootout, missed, offTarget, 'Off target'",
        "penaltyShootout, missed, goalkeeperSave, 'Goalkeeper save'"
    })
    void acceptsEveryDocumentedPenaltyOutcome(
            String type,
            String incidentClass,
            String reason,
            String description) {
        String sequence = "penaltyShootout".equals(type) ? ",\"sequence\":1" : "";
        String json = """
                {"incidents":[{
                  "incidentType":"%s",
                  "incidentClass":"%s",
                  "time":70,
                  "isHome":true,
                  "player":{"name":"Penalty Taker"},
                  "reason":"%s",
                  "description":"%s"%s
                }]}
                """.formatted(type, incidentClass, reason, description, sequence);

        assertThat(parse(116L, json).status()).isEqualTo(J5ParseStatus.PARSED);
    }

    @Test
    void rejectsScoredAsAnInGamePenaltyOutcome() {
        String json = """
                {"incidents":[{
                  "incidentType":"inGamePenalty",
                  "incidentClass":"missed",
                  "time":70,
                  "isHome":true,
                  "player":{"name":"Penalty Taker"},
                  "reason":"scored"
                }]}
                """;

        var result = parse(117L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.incidents[0].reason");
    }

    @Test
    void rejectsAContradictoryPenaltyPairEvenWhenDescriptionIsMissing() {
        String json = """
                {"incidents":[{
                  "incidentType":"penaltyShootout",
                  "incidentClass":"missed",
                  "time":96,
                  "isHome":true,
                  "player":{"name":"Penalty Taker"},
                  "reason":"scored",
                  "sequence":3
                }]}
                """;

        var result = parse(118L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::code)
                .contains(J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH);
    }

    @Test
    void rejectsANegativeTechnicalTimeOutsideTheDocumentedBenchCardCase() {
        String json = """
                {"incidents":[{
                  "incidentType":"goal",
                  "time":-5,
                  "benchTime":58,
                  "isHome":true,
                  "homeScore":1,
                  "awayScore":0
                }]}
                """;

        var result = parse(106L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.incidents[0].time");
    }

    @Test
    void rejectsBenchTimeAttachedToANonTechnicalCardTime() {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "incidentClass":"yellow",
                  "time":58,
                  "benchTime":58,
                  "isHome":false,
                  "playerName":"Bench Player"
                }]}
                """;

        var result = parse(120L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::code)
                .contains(J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH);
    }

    private J5ParseResult<EventIncidents> parse(long snapshotId, String json) {
        return parser.parse(
                snapshotId,
                EVENT_ID,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);
    }
}
