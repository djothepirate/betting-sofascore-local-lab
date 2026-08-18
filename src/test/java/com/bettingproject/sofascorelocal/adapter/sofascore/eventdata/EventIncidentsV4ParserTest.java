package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV4ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-15T23:29:59Z");
    private final EventIncidentsV4Parser parser = new EventIncidentsV4Parser();

    @Test
    void preservesIncomingAndOutgoingPlayersOnProviderSubstitutions() throws Exception {
        var result = parser.parse(
                32L,
                16412917L,
                payload("incidents-provider-period-markers.json"),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion()).isEqualTo("event-incidents-v4");
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .anySatisfy(warning -> assertThat(warning.code())
                        .isEqualTo(J5ParseWarning.Code.PROVIDER_SENTINEL_NORMALIZED));
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(completeness.presentSignals()).isEqualTo(6);
            assertThat(completeness.expectedSignals()).isEqualTo(6);
        });
        assertThat(result.data()).hasValueSatisfying(incidents -> {
            var substitution = incidents.incidents().get(2);
            assertThat(substitution.incidentType()).isEqualTo("substitution");
            assertThat(substitution.playerName()).isEmpty();
            assertThat(substitution.playerInProviderId()).contains(2001L);
            assertThat(substitution.playerInName()).contains("Synthetic Incoming Player");
            assertThat(substitution.playerOutProviderId()).contains(2002L);
            assertThat(substitution.playerOutName()).contains("Synthetic Outgoing Player");
        });
    }

    @Test
    void reportsAMissingOutgoingPlayerAsPartialWithoutInventingAnIdentity() {
        String json = """
                {"incidents":[{
                  "incidentType":"substitution",
                  "time":83,
                  "isHome":true,
                  "playerIn":{"id":2443678,"name":"Matviy Bodnar"}
                }]}
                """;

        var result = parser.parse(
                36L,
                16412917L,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.presentSignals()).isEqualTo(2);
            assertThat(completeness.expectedSignals()).isEqualTo(3);
            assertThat(completeness.missingPaths())
                    .containsExactly("$.incidents[0].playerOut");
        });
        assertThat(result.data()).hasValueSatisfying(incidents -> {
            var substitution = incidents.incidents().getFirst();
            assertThat(substitution.playerInName()).contains("Matviy Bodnar");
            assertThat(substitution.playerOutName()).isEmpty();
        });
    }

    @Test
    void preservesParticipantsForEverySubstitutionInSourceOrder() {
        String json = """
                {"incidents":[
                  {
                    "incidentType":"substitution",
                    "time":83,
                    "isHome":true,
                    "playerIn":{"id":2001,"name":"First Incoming Player"},
                    "playerOut":{"id":2002,"name":"First Outgoing Player"}
                  },
                  {
                    "incidentType":"substitution",
                    "time":80,
                    "isHome":false,
                    "playerIn":{"id":3001,"name":"Second Incoming Player"},
                    "playerOut":{"id":3002,"name":"Second Outgoing Player"}
                  }
                ]}
                """;

        var result = parser.parse(
                38L,
                16412917L,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(completeness.presentSignals()).isEqualTo(6);
            assertThat(completeness.expectedSignals()).isEqualTo(6);
        });
        assertThat(result.data()).hasValueSatisfying(incidents -> {
            assertThat(incidents.incidents())
                    .extracting(incident -> incident.playerInName().orElseThrow())
                    .containsExactly("First Incoming Player", "Second Incoming Player");
            assertThat(incidents.incidents())
                    .extracting(incident -> incident.playerOutName().orElseThrow())
                    .containsExactly("First Outgoing Player", "Second Outgoing Player");
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
                37L,
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
