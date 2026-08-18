package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV9ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-18T09:33:44Z");
    private static final long EVENT_ID = 16391145L;

    private final EventIncidentsV9Parser parser = new EventIncidentsV9Parser();

    @Test
    void acceptsTheObservedOtherReasonBenchCardAndNormalizesItsStoppageTime() {
        String json = observedBenchCardShape("Other reason");
        RawPayloadEvidence payload = payload(json);

        var result = parser.parse(162L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV9Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.warnings())
                .extracting(J5ParseWarning::code)
                .contains(
                        J5ParseWarning.Code.PROVIDER_BENCH_CARD_MINUTE_USED,
                        J5ParseWarning.Code.PROVIDER_BENCH_CARD_ADDED_TIME_USED,
                        J5ParseWarning.Code.UNKNOWN_FIELD);
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(1);
            var card = data.incidents().getFirst();
            assertThat(card.incidentType()).isEqualTo("card");
            assertThat(card.minute()).contains(90);
            assertThat(card.addedTime()).contains(9);
            assertThat(card.playerName()).contains("Home Manager");
            assertThat(card.incidentClass()).contains("yellow");
            assertThat(card.reason()).contains("Other reason");
            assertThat(card.motifLabel()).isEqualTo("Other reason");
        });

        var historicalV8 = new EventIncidentsV8Parser().parse(
                162L, EVENT_ID, payload, RECEIVED_AT);
        assertThat(historicalV8.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historicalV8.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].reason");
    }

    @Test
    void keepsUnknownCardReasonsClosed() {
        var result = parse(observedBenchCardShape("Future undocumented reason"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.incidents[0].reason");
    }

    @Test
    void rejectsBenchAddedTimeOutsideTheExactBenchCardContext() {
        List<String> incompatiblePayloads = List.of(
                """
                {"incidents":[{
                  "incidentType":"card","incidentClass":"yellow",
                  "time":90,"benchAddedTime":9,"isHome":true,
                  "playerName":"Booked Player","reason":"Foul"
                }]}
                """,
                """
                {"incidents":[{
                  "incidentType":"substitution","incidentClass":"regular",
                  "time":80,"benchAddedTime":9,"isHome":true,"injury":false,
                  "playerIn":{"name":"Incoming"},"playerOut":{"name":"Outgoing"}
                }]}
                """,
                """
                {"incidents":[{
                  "incidentType":"card","incidentClass":"yellow",
                  "time":-5,"benchTime":90,"addedTime":9,"benchAddedTime":9,
                  "isHome":true,"playerName":"Booked Player","reason":"Foul"
                }]}
                """);

        for (String json : incompatiblePayloads) {
            var result = parse(json);
            assertThat(result.status())
                    .as(json)
                    .isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
            assertThat(result.problems()).as(json).isNotEmpty();
        }
    }

    @Test
    void preservesTheV8WoodworkContract() {
        String json = """
                {"incidents":[{
                  "incidentType":"penaltyShootout","incidentClass":"missed",
                  "time":121,"isHome":false,"player":{"name":"Penalty Taker"},
                  "reason":"woodwork","description":"Woodwork","sequence":1
                }]}
                """;

        var result = parse(json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data()).hasValueSatisfying(data -> {
            var penalty = data.incidents().getFirst();
            assertThat(penalty.reason()).contains("woodwork");
            assertThat(penalty.description()).contains("Woodwork");
        });
    }

    private J5ParseResult<EventIncidents> parse(String json) {
        return parser.parse(162L, EVENT_ID, payload(json), RECEIVED_AT);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String observedBenchCardShape(String reason) {
        return """
                {"incidents":[{
                  "incidentType":"card",
                  "incidentClass":"yellow",
                  "time":-5,
                  "benchTime":90,
                  "benchAddedTime":9,
                  "isHome":true,
                  "playerName":"Home Manager",
                  "manager":{"id":791299,"name":"Home Manager"},
                  "reason":"%s",
                  "rescinded":false
                }]}
                """.formatted(reason);
    }
}
