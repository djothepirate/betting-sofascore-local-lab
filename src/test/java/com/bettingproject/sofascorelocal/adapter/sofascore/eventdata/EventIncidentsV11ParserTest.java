package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV11ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-18T10:30:00Z");
    private static final long EVENT_ID = 16251993L;

    private final EventIncidentsV11Parser parser = new EventIncidentsV11Parser();

    @Test
    void acceptsTheObservedOffTheBallFoulCardReasonWithoutRewritingIt() {
        String json = observedCard("Off the ball foul");
        RawPayloadEvidence payload = payload(json);

        var result = parser.parse(180L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV11Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(1);
            var card = data.incidents().getFirst();
            assertThat(card.incidentType()).isEqualTo("card");
            assertThat(card.minute()).contains(77);
            assertThat(card.home()).contains(false);
            assertThat(card.playerProviderId()).contains(877400L);
            assertThat(card.playerName()).contains("Facundo Mallo");
            assertThat(card.incidentClass()).contains("yellow");
            assertThat(card.reason()).contains("Off the ball foul");
            assertThat(card.motifLabel()).isEqualTo("Off the ball foul");
        });

        var historicalV10 = new EventIncidentsV10Parser().parse(
                180L, EVENT_ID, payload, RECEIVED_AT);
        assertThat(historicalV10.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historicalV10.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].reason");
    }

    @Test
    void keepsEveryOtherUndocumentedCardReasonClosedAndPreservesV10Rules() {
        var unknown = parse(observedCard("Future undocumented reason"));

        assertThat(unknown.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(unknown.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].reason");

        String inherited = """
                {"incidents":[
                  {
                    "incidentType":"card","incidentClass":"yellow",
                    "time":-5,"benchTime":90,"benchAddedTime":9,"isHome":true,
                    "playerName":"Home Manager","reason":"Other reason"
                  },
                  {
                    "incidentType":"goal","incidentClass":"regular","from":"regular",
                    "time":16,"isHome":false,"player":{"name":"Goal Scorer"},
                    "homeScore":0,"awayScore":1
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"missed",
                    "time":121,"isHome":false,"player":{"name":"Penalty Taker"},
                    "reason":"woodwork","description":"Woodwork","sequence":1
                  }
                ]}
                """;
        var inheritedResult = parse(inherited);

        assertThat(inheritedResult.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(inheritedResult.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(3);
            assertThat(data.incidents().getFirst().reason()).contains("Other reason");
            assertThat(data.incidents().get(1).goalOrigin()).isEmpty();
            assertThat(data.incidents().get(2).reason()).contains("woodwork");
        });
    }

    private J5ParseResult<EventIncidents> parse(String json) {
        return parser.parse(180L, EVENT_ID, payload(json), RECEIVED_AT);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String observedCard(String reason) {
        return """
                {"incidents":[{
                  "player":{
                    "name":"Facundo Mallo","firstName":"Facundo","lastName":"Mallo",
                    "slug":"facundo-mallo","shortName":"F. Mallo","position":"D",
                    "jerseyNumber":"15","height":185,"userCount":189,"gender":"M",
                    "sofascoreId":"5tgd9z","id":877400,"marketValueCurrency":"EUR",
                    "dateOfBirthTimestamp":790214400,
                    "proposedMarketValueRaw":{"value":955000,"currency":"EUR"},
                    "fieldTranslations":{
                      "nameTranslation":{"ar":"فاكوندو مالو","ru":"Факундо Мальо"},
                      "shortNameTranslation":{"ar":"ف. مالو","ru":"Ф. Мальо"}
                    }
                  },
                  "playerName":"Facundo Mallo","reason":"%s","rescinded":false,
                  "id":125440215,"time":77,"isHome":false,
                  "incidentClass":"yellow","incidentType":"card",
                  "reversedPeriodTime":14
                }]}
                """.formatted(reason);
    }
}
