package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV14ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-27T10:00:00Z");
    private static final long EVENT_ID = 16717086L;

    private final EventIncidentsV14Parser parser = new EventIncidentsV14Parser();

    @Test
    void acceptsTheObservedLiveExtraTimeMarkerWithoutRewritingIt() throws IOException {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(fixture());

        var result = parser.parse(500L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV14Parser.PARSER_VERSION);
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .extracting(J5ParseWarning::code)
                .containsOnly(J5ParseWarning.Code.PROVIDER_SENTINEL_NORMALIZED);
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(3);
            var extraTime = data.incidents().getFirst();
            assertThat(extraTime.incidentType()).isEqualTo("period");
            assertThat(extraTime.periodText()).contains("Extra time");
            assertThat(extraTime.minute()).contains(120);
            assertThat(extraTime.addedTime()).isEmpty();
            assertThat(extraTime.homeScore()).contains(1);
            assertThat(extraTime.awayScore()).contains(1);
            assertThat(extraTime.detailLabel()).isEqualTo("Extra time");
        });

        var historicalV13 = new EventIncidentsV13Parser().parse(
                500L, EVENT_ID, payload, RECEIVED_AT);
        assertThat(historicalV13.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(historicalV13.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].text");
    }

    @Test
    void rejectsAnExtraTimeMarkerExplicitlyMarkedAsNotLive() {
        var result = parse("""
                {"incidents":[{
                  "incidentType":"period","text":"Extra time","isLive":false,
                  "time":120,"addedTime":999,"homeScore":1,"awayScore":1
                }]}
                """);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::code, J5ParseProblem::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                                "$.incidents[0]"));
    }

    @Test
    void keepsAMissingLiveFlagAsMeasuredPartialData() {
        var result = parse("""
                {"incidents":[{
                  "incidentType":"period","text":"Extra time",
                  "time":120,"addedTime":999,"homeScore":1,"awayScore":1
                }]}
                """);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths()).containsExactly("$.incidents[0].isLive");
        });
    }

    @Test
    void retainsV13CardReasonsAndKeepsFuturePeriodLabelsClosed() {
        var inheritedCard = parse("""
                {"incidents":[{
                  "incidentType":"card","incidentClass":"yellow","reason":"Leaving field",
                  "time":74,"isHome":false,"player":{"id":817886,"name":"Yongjing Cao"}
                }]}
                """);
        assertThat(inheritedCard.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(inheritedCard.data()).hasValueSatisfying(data ->
                assertThat(data.incidents().getFirst().reason()).contains("Leaving field"));

        var unknown = parse("""
                {"incidents":[{
                  "incidentType":"period","text":"Future extra period","isLive":true,
                  "time":120,"homeScore":1,"awayScore":1
                }]}
                """);
        assertThat(unknown.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(unknown.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].text");
    }

    private J5ParseResult<EventIncidents> parse(String json) {
        return parser.parse(
                500L,
                EVENT_ID,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);
    }

    private byte[] fixture() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/fixtures/provider-j5/incidents-provider-live-extra-time.json")) {
            return java.util.Objects.requireNonNull(input).readAllBytes();
        }
    }
}
