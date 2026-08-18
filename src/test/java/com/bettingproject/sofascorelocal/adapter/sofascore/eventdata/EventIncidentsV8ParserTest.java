package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV8ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-18T08:00:00Z");
    private static final long EVENT_ID = 14037091L;

    private final EventIncidentsV8Parser parser = new EventIncidentsV8Parser();

    @Test
    void normalizesTheExactPenaltyPeriodSentinelToTheLastEffectiveShootoutMinute() {
        RawPayloadEvidence payload = payload(observedShootoutShape());

        var result = parser.parse(139L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV8Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.warnings())
                .extracting(J5ParseWarning::code)
                .contains(
                        J5ParseWarning.Code.PROVIDER_PENALTY_PERIOD_SENTINEL_NORMALIZED,
                        J5ParseWarning.Code.PROVIDER_SENTINEL_NORMALIZED,
                        J5ParseWarning.Code.PROVIDER_NESTED_MINUTE_USED);
        assertThat(result.warnings())
                .filteredOn(warning -> warning.code()
                        == J5ParseWarning.Code.PROVIDER_PENALTY_PERIOD_SENTINEL_NORMALIZED)
                .singleElement()
                .satisfies(warning -> {
                    assertThat(warning.path()).isEqualTo("$.incidents[0].time");
                    assertThat(warning.message()).endsWith("146");
                });
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(4);
            var marker = data.incidents().getFirst();
            assertThat(marker.incidentType()).isEqualTo("period");
            assertThat(marker.periodText()).contains("PEN");
            assertThat(marker.minute()).contains(146);
            assertThat(marker.addedTime()).isEmpty();
            assertThat(marker.homeScore()).contains(3);
            assertThat(marker.awayScore()).contains(2);

            var woodwork = data.incidents().get(2);
            assertThat(woodwork.incidentType()).isEqualTo("penaltyShootout");
            assertThat(woodwork.minute()).contains(146);
            assertThat(woodwork.shootoutSequence()).contains(9);
            assertThat(woodwork.incidentClass()).contains("missed");
            assertThat(woodwork.reason()).contains("woodwork");
            assertThat(woodwork.description()).contains("Woodwork");
        });
    }

    @Test
    void keepsACardWithoutReasonValidCompleteAndDisplayedWithoutInventingAMotif() {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "incidentClass":"yellowRed",
                  "time":56,
                  "isHome":false,
                  "player":{"id":1001,"name":"Booked Player"}
                }]}
                """;

        var result = parse(119L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.data()).hasValueSatisfying(data -> {
            var card = data.incidents().getFirst();
            assertThat(card.reason()).isEmpty();
            assertThat(card.motifLabel()).isEqualTo("—");
        });
    }

    @Test
    void stillRejectsAnUnknownCardReasonWhenTheFieldIsPresent() {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "incidentClass":"yellow",
                  "time":56,
                  "isHome":false,
                  "player":{"id":1001,"name":"Booked Player"},
                  "reason":"Undocumented reason"
                }]}
                """;

        var result = parse(121L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.incidents[0].reason");
    }

    @Test
    void omitsTheObservedShotOriginOnlyForARegularGoal() {
        String json = """
                {"incidents":[{
                  "incidentType":"goal",
                  "incidentClass":"regular",
                  "from":"shot",
                  "time":45,
                  "isHome":true,
                  "player":{"id":1002,"name":"Goal Scorer"},
                  "homeScore":1,
                  "awayScore":0
                }]}
                """;

        var result = parse(120L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data()).hasValueSatisfying(data ->
                assertThat(data.incidents().getFirst().goalOrigin()).isEmpty());
        assertThat(result.warnings())
                .extracting(J5ParseWarning::code)
                .contains(J5ParseWarning.Code.PROVIDER_REGULAR_GOAL_ORIGIN_OMITTED);

        var historicalV7 = new EventIncidentsV7Parser().parse(
                120L, EVENT_ID, payload(json), RECEIVED_AT);
        assertThat(historicalV7.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inGamePenalty", "penaltyShootout"})
    void acceptsTheExactWoodworkTupleForBothPenaltyFamilies(String incidentType) {
        String sequence = "penaltyShootout".equals(incidentType)
                ? ",\"sequence\":4"
                : "";
        String json = """
                {"incidents":[{
                  "incidentType":"%s",
                  "incidentClass":"missed",
                  "time":58,
                  "isHome":true,
                  "player":{"id":943593,"name":"Penalty Taker"},
                  "reason":"woodwork",
                  "description":"Woodwork"%s
                }]}
                """.formatted(incidentType, sequence);

        var result = parse(140L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.data()).hasValueSatisfying(data -> {
            var penalty = data.incidents().getFirst();
            assertThat(penalty.incidentClass()).contains("missed");
            assertThat(penalty.reason()).contains("woodwork");
            assertThat(penalty.description()).contains("Woodwork");
            assertThat(penalty.motifLabel()).isEqualTo("Woodwork");
        });
    }

    @Test
    void rejectsIncompleteCrossedAndUnknownWoodworkOutcomes() {
        List<String> incompatiblePayloads = List.of(
                penalty("inGamePenalty", "missed", "woodwork", null, ""),
                penalty("inGamePenalty", "missed", "woodwork", "Off target", ""),
                penalty("inGamePenalty", "missed", "offTarget", "Woodwork", ""),
                penalty("penaltyShootout", "scored", "woodwork", "Woodwork", ",\"sequence\":1"),
                penalty("penaltyShootout", "missed", "post", "Woodwork", ",\"sequence\":1"));

        for (String json : incompatiblePayloads) {
            var result = parse(141L, json);
            assertThat(result.status())
                    .as(json)
                    .isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
            assertThat(result.problems()).as(json).isNotEmpty();
        }
    }

    @Test
    void rejectsThePenaltyPeriodSentinelOutsideItsExactDerivedContext() {
        List<String> incompatiblePayloads = List.of(
                """
                {"incidents":[{
                  "incidentType":"period","text":"PEN","period":"penalties",
                  "time":999,"addedTime":999,"isLive":false,
                  "homeScore":3,"awayScore":2
                }]}
                """,
                """
                {"incidents":[
                  {
                    "incidentType":"period","text":"FT","period":"penalties",
                    "time":999,"addedTime":999,"isLive":false,
                    "homeScore":3,"awayScore":2
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"scored",
                    "isHome":true,"player":{"name":"Penalty Taker"},
                    "reason":"scored","description":"Scored","sequence":1,
                    "footballPassingNetworkAction":[{"time":121}]
                  }
                ]}
                """,
                """
                {"incidents":[
                  {
                    "incidentType":"period","text":"PEN","period":"penalties",
                    "time":999,"addedTime":999,"isLive":true,
                    "homeScore":3,"awayScore":2
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"scored",
                    "isHome":true,"player":{"name":"Penalty Taker"},
                    "reason":"scored","description":"Scored","sequence":1,
                    "footballPassingNetworkAction":[{"time":121}]
                  }
                ]}
                """,
                """
                {"incidents":[
                  {
                    "incidentType":"period","text":"PEN","period":"penalties",
                    "time":999,"addedTime":999,"isLive":false,
                    "homeScore":3,"awayScore":2
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"scored",
                    "isHome":true,"player":{"name":"Penalty Taker"},
                    "reason":"scored","description":"Scored","sequence":1,
                    "footballPassingNetworkAction":[{"time":301}]
                  }
                ]}
                """);

        for (String json : incompatiblePayloads) {
            var result = parse(142L, json);
            assertThat(result.status())
                    .as(json)
                    .isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
            assertThat(result.problems()).as(json).isNotEmpty();
        }
    }

    @Test
    void preservesV7BehaviorAndItsInjurySubstitutionExtension() {
        String injurySubstitution = """
                {"incidents":[{
                  "incidentType":"substitution",
                  "incidentClass":"injury",
                  "time":46,
                  "isHome":false,
                  "injury":true,
                  "playerIn":{"id":864921,"name":"Incoming Player"},
                  "playerOut":{"id":851005,"name":"Outgoing Player"}
                }]}
                """;

        assertThat(parse(143L, injurySubstitution).status()).isEqualTo(J5ParseStatus.PARSED);

        var historicalV7 = new EventIncidentsV7Parser().parse(
                144L,
                EVENT_ID,
                payload(observedShootoutShape()),
                RECEIVED_AT);
        assertThat(historicalV7.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historicalV7.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.incidents[0].time", "$.incidents[2].reason");
    }

    private J5ParseResult<EventIncidents> parse(long snapshotId, String json) {
        return parser.parse(snapshotId, EVENT_ID, payload(json), RECEIVED_AT);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String penalty(
            String type,
            String incidentClass,
            String reason,
            String description,
            String sequence) {
        String descriptionField = description == null
                ? ""
                : ",\"description\":\"" + description + "\"";
        return """
                {"incidents":[{
                  "incidentType":"%s",
                  "incidentClass":"%s",
                  "time":58,
                  "isHome":true,
                  "player":{"name":"Penalty Taker"},
                  "reason":"%s"%s%s
                }]}
                """.formatted(type, incidentClass, reason, descriptionField, sequence);
    }

    private static String observedShootoutShape() {
        return """
                {"incidents":[
                  {
                    "incidentType":"period",
                    "text":"PEN",
                    "period":"penalties",
                    "time":999,
                    "addedTime":999,
                    "isLive":false,
                    "homeScore":3,
                    "awayScore":2
                  },
                  {
                    "incidentType":"period",
                    "text":"ET",
                    "period":"extra2",
                    "time":120,
                    "addedTime":999,
                    "isLive":false,
                    "homeScore":0,
                    "awayScore":0
                  },
                  {
                    "incidentType":"penaltyShootout",
                    "incidentClass":"missed",
                    "isHome":false,
                    "player":{"id":301,"name":"Shootout Taker"},
                    "homeScore":3,
                    "awayScore":2,
                    "reason":"woodwork",
                    "description":"Woodwork",
                    "sequence":9,
                    "footballPassingNetworkAction":[{"time":146}]
                  },
                  {
                    "incidentType":"penaltyShootout",
                    "incidentClass":"scored",
                    "isHome":true,
                    "player":{"id":302,"name":"Earlier Taker"},
                    "homeScore":1,
                    "awayScore":0,
                    "reason":"scored",
                    "description":"Scored",
                    "sequence":1,
                    "footballPassingNetworkAction":[{"time":121}]
                  }
                ]}
                """;
    }
}
