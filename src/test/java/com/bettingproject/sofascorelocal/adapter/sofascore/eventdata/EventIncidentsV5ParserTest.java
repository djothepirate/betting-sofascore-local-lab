package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV5ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-16T09:16:58Z");
    private static final long EVENT_ID = 16483632L;
    private final EventIncidentsV5Parser parser = new EventIncidentsV5Parser();

    @Test
    void usesProviderBenchTimeForANegativeTechnicalTimeMarker() {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "incidentClass":"yellow",
                  "time":-5,
                  "benchTime":58,
                  "reversedPeriodTime":6,
                  "isHome":false,
                  "reason":"Argument",
                  "player":{"id":1053241,"name":"Provider Player"}
                }]}
                """;

        var result = parser.parse(
                60L,
                EVENT_ID,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV5Parser.PARSER_VERSION);
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .anySatisfy(warning -> {
                    assertThat(warning.code()).isEqualTo(
                            J5ParseWarning.Code.PROVIDER_BENCH_CARD_MINUTE_USED);
                    assertThat(warning.path()).isEqualTo("$.incidents[0].benchTime");
                });
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(completeness.presentSignals()).isEqualTo(1);
            assertThat(completeness.expectedSignals()).isEqualTo(1);
        });
        assertThat(result.data()).hasValueSatisfying(incidents -> {
            assertThat(incidents.incidents()).hasSize(1);
            assertThat(incidents.incidents().getFirst().minute()).contains(58);
            assertThat(incidents.incidents().getFirst().incidentClass())
                    .contains("yellow");
            assertThat(incidents.incidents().getFirst().reason()).contains("Argument");
            assertThat(incidents.incidents().getFirst().playerName())
                    .contains("Provider Player");
        });
    }

    @Test
    void keepsTheV4ContractStrictWhileV5OwnsTheNewProviderShape() {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "time":-5,
                  "benchTime":58,
                  "isHome":false
                }]}
                """;
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                json.getBytes(StandardCharsets.UTF_8));

        var v4 = new EventIncidentsV4Parser().parse(
                60L, EVENT_ID, payload, RECEIVED_AT);
        var v5 = parser.parse(60L, EVENT_ID, payload, RECEIVED_AT);

        assertThat(v4.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(v4.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].time");
        assertThat(v5.status()).isEqualTo(J5ParseStatus.PARSED);
    }

    @Test
    void rejectsAnotherNegativeCardMarkerThatHasNotBeenQualified() {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "time":-4,
                  "benchTime":58,
                  "isHome":false
                }]}
                """;

        var result = parser.parse(
                61L,
                EVENT_ID,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].time");
    }

    @Test
    void rejectsANegativeTechnicalMarkerWithoutAValidBenchTime() {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "time":-5,
                  "isHome":false
                }]}
                """;

        var result = parser.parse(
                62L,
                EVENT_ID,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].benchTime");
    }

    @Test
    void doesNotApplyTheBenchCardRuleToAnotherIncidentType() {
        String json = """
                {"incidents":[{
                  "incidentType":"goal",
                  "time":-5,
                  "benchTime":58,
                  "isHome":false,
                  "homeScore":0,
                  "awayScore":1
                }]}
                """;

        var result = parser.parse(
                63L,
                EVENT_ID,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].time");
    }

    @Test
    void doesNotRetainCardMetadataForAnotherIncidentType() {
        String json = """
                {"incidents":[{
                  "incidentType":"goal",
                  "incidentClass":"regular",
                  "time":86,
                  "isHome":false,
                  "reason":"Argument",
                  "homeScore":0,
                  "awayScore":1
                }]}
                """;

        var result = parser.parse(
                64L,
                EVENT_ID,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data()).hasValueSatisfying(incidents -> {
            assertThat(incidents.incidents().getFirst().incidentClass()).isEmpty();
            assertThat(incidents.incidents().getFirst().reason()).isEmpty();
        });
    }

    @Test
    void preservesAllV4PeriodAndSubstitutionSemantics() throws Exception {
        var result = parser.parse(
                32L,
                16412917L,
                payload("incidents-provider-period-markers.json"),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV5Parser.PARSER_VERSION);
        assertThat(result.warnings())
                .anySatisfy(warning -> assertThat(warning.code())
                        .isEqualTo(J5ParseWarning.Code.PROVIDER_SENTINEL_NORMALIZED));
        assertThat(result.data()).hasValueSatisfying(incidents -> {
            var substitution = incidents.incidents().get(2);
            assertThat(substitution.playerInName())
                    .contains("Synthetic Incoming Player");
            assertThat(substitution.playerOutName())
                    .contains("Synthetic Outgoing Player");
        });
    }

    private RawPayloadEvidence payload(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/fixtures/provider-j5/" + name)) {
            return RawPayloadEvidence.capture(input.readAllBytes());
        }
    }
}
