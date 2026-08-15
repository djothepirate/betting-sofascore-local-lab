package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV3ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-15T21:43:34Z");
    private final EventIncidentsV3Parser parser = new EventIncidentsV3Parser();

    @Test
    void preservesProviderPeriodMarkersWithoutTreating999AsLiteralAddedMinutes()
            throws Exception {
        var result = parser.parse(
                32L,
                16412917L,
                payload("incidents-provider-period-markers.json"),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion()).isEqualTo("event-incidents-v3");
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .anySatisfy(warning -> {
                    assertThat(warning.code())
                            .isEqualTo(J5ParseWarning.Code.PROVIDER_SENTINEL_NORMALIZED);
                    assertThat(warning.path()).isEqualTo("$.incidents[0].addedTime");
                });
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(completeness.presentSignals()).isEqualTo(4);
            assertThat(completeness.expectedSignals()).isEqualTo(4);
        });
        assertThat(result.data()).hasValueSatisfying(incidents -> {
            assertThat(incidents.incidents()).hasSize(4);
            assertThat(incidents.incidents().getFirst().incidentType()).isEqualTo("period");
            assertThat(incidents.incidents().getFirst().minute()).isEqualTo(90);
            assertThat(incidents.incidents().getFirst().addedTime()).isEmpty();
            assertThat(incidents.incidents().get(1).addedTime()).contains(5);
        });
    }

    @Test
    void stillRejects999ForASideSpecificIncident() {
        String json = """
                {"incidents":[{
                  "incidentType":"card",
                  "time":45,
                  "addedTime":999,
                  "isHome":true
                }]}
                """;

        var result = parser.parse(
                33L,
                16412917L,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .containsExactly("$.incidents[0].addedTime");
    }

    private RawPayloadEvidence payload(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/fixtures/provider-j5/" + name)) {
            return RawPayloadEvidence.capture(input.readAllBytes());
        }
    }
}
