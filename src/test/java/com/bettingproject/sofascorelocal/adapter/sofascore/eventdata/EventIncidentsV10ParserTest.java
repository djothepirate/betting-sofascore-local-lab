package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV10ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-18T10:09:39Z");
    private static final long EVENT_ID = 16251993L;

    private final EventIncidentsV10Parser parser = new EventIncidentsV10Parser();

    @Test
    void acceptsTheObservedRegularOriginOnlyForARegularGoal() {
        String json = regularGoal("regular", "regular");
        RawPayloadEvidence payload = payload(json);

        var result = parser.parse(179L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV10Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.warnings())
                .filteredOn(warning -> warning.code()
                        == J5ParseWarning.Code.PROVIDER_REGULAR_GOAL_ORIGIN_OMITTED)
                .singleElement()
                .satisfies(warning -> assertThat(warning.path())
                        .isEqualTo("$.incidents[0].from"));
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(1);
            var goal = data.incidents().getFirst();
            assertThat(goal.incidentType()).isEqualTo("goal");
            assertThat(goal.incidentClass()).contains("regular");
            assertThat(goal.goalOrigin()).isEmpty();
            assertThat(goal.playerName()).contains("Goal Scorer");
            assertThat(goal.homeScore()).contains(0);
            assertThat(goal.awayScore()).contains(1);
        });

        var historicalV9 = new EventIncidentsV9Parser().parse(
                179L, EVENT_ID, payload, RECEIVED_AT);
        assertThat(historicalV9.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historicalV9.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].from");
    }

    @Test
    void keepsCrossedAndUndocumentedGoalOriginsClosed() {
        List<String> incompatiblePayloads = List.of(
                regularGoal("penalty", "regular"),
                regularGoal("ownGoal", "regular"),
                regularGoal("regular", "future-origin"));

        for (String json : incompatiblePayloads) {
            var result = parse(json);
            assertThat(result.status())
                    .as(json)
                    .isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
            assertThat(result.problems()).as(json).isNotEmpty();
        }
    }

    @Test
    void preservesV9BenchCardsAndV8WoodworkPenaltyFamilies() {
        String json = """
                {"incidents":[
                  {
                    "incidentType":"card","incidentClass":"yellow",
                    "time":-5,"benchTime":90,"benchAddedTime":9,"isHome":true,
                    "playerName":"Home Manager","reason":"Other reason"
                  },
                  {
                    "incidentType":"inGamePenalty","incidentClass":"missed",
                    "time":58,"isHome":true,"player":{"name":"Penalty Taker"},
                    "reason":"woodwork","description":"Woodwork"
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"missed",
                    "time":121,"isHome":false,"player":{"name":"Shootout Taker"},
                    "reason":"woodwork","description":"Woodwork","sequence":1
                  }
                ]}
                """;

        var result = parse(json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(3);
            assertThat(data.incidents().getFirst().reason()).contains("Other reason");
            assertThat(data.incidents().get(1).reason()).contains("woodwork");
            assertThat(data.incidents().get(2).reason()).contains("woodwork");
        });
    }

    private J5ParseResult<EventIncidents> parse(String json) {
        return parser.parse(179L, EVENT_ID, payload(json), RECEIVED_AT);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String regularGoal(String incidentClass, String from) {
        return """
                {"incidents":[{
                  "incidentType":"goal","incidentClass":"%s","from":"%s",
                  "time":16,"isHome":false,
                  "player":{"id":914501,"name":"Goal Scorer"},
                  "homeScore":0,"awayScore":1
                }]}
                """.formatted(incidentClass, from);
    }
}
