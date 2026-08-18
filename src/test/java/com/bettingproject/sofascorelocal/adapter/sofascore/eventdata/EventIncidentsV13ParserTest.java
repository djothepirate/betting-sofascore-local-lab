package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV13ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-18T13:00:00Z");
    private static final long EVENT_ID = 16692000L;

    private final EventIncidentsV13Parser parser = new EventIncidentsV13Parser();

    @Test
    void acceptsTheObservedLeavingFieldCardReasonWithoutRewritingIt() {
        String json = observedCard("Leaving field");
        RawPayloadEvidence payload = payload(json);

        var result = parser.parse(190L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(1);
            var card = data.incidents().getFirst();
            assertThat(card.incidentType()).isEqualTo("card");
            assertThat(card.minute()).contains(74);
            assertThat(card.home()).contains(false);
            assertThat(card.playerProviderId()).contains(817886L);
            assertThat(card.playerName()).contains("Yongjing Cao");
            assertThat(card.incidentClass()).contains("yellow");
            assertThat(card.reason()).contains("Leaving field");
            assertThat(card.motifLabel()).isEqualTo("Leaving field");
        });

        var historicalV12 = new EventIncidentsV12Parser().parse(
                190L, EVENT_ID, payload, RECEIVED_AT);
        assertThat(historicalV12.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historicalV12.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].reason");
    }

    @Test
    void keepsUndocumentedReasonsClosedAndPreservesTheV12ShootoutRule() {
        var unknown = parse(observedCard("Future undocumented reason"));

        assertThat(unknown.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(unknown.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].reason");

        var inheritedShootout = parse("""
                {"incidents":[
                  {
                    "text":"PEN","homeScore":1,"awayScore":0,"isLive":false,
                    "period":"penalties","time":999,"addedTime":999,
                    "incidentType":"period"
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"scored",
                    "isHome":true,"player":{"name":"Penalty Taker"},
                    "homeScore":1,"awayScore":0,"sequence":1,
                    "description":"Scored","reason":"scored"
                  },
                  {
                    "text":"FT","homeScore":0,"awayScore":0,"isLive":false,
                    "time":90,"addedTime":999,"incidentType":"period"
                  }
                ]}
                """);

        assertThat(inheritedShootout.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(inheritedShootout.evidence().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(inheritedShootout.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(3);
            assertThat(data.incidents().getFirst().minute()).isEmpty();
            assertThat(data.incidents().get(1).minute()).isEmpty();
            assertThat(data.incidents().get(2).minute()).contains(90);
        });
    }

    private J5ParseResult<EventIncidents> parse(String json) {
        return parser.parse(190L, EVENT_ID, payload(json), RECEIVED_AT);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String observedCard(String reason) {
        return """
                {"incidents":[{
                  "player":{
                    "name":"Yongjing Cao","slug":"yongjing-cao","shortName":"Y. Cao",
                    "position":"M","jerseyNumber":"37","height":180,"userCount":55,
                    "gender":"M","sofascoreId":"pfjak7","id":817886,
                    "marketValueCurrency":"EUR","dateOfBirthTimestamp":855964800,
                    "proposedMarketValueRaw":{"value":370000,"currency":"EUR"},
                    "fieldTranslations":{
                      "nameTranslation":{"ar":"يونغجينغ كاو","ru":"Юнцзин Цао"},
                      "shortNameTranslation":{"ar":"ي. كاو","ru":"Ю. Цао"}
                    }
                  },
                  "playerName":"Yongjing Cao","reason":"%s","rescinded":false,
                  "id":125444628,"time":74,"isHome":false,
                  "incidentClass":"yellow","incidentType":"card",
                  "reversedPeriodTime":17
                }]}
                """.formatted(reason);
    }
}
