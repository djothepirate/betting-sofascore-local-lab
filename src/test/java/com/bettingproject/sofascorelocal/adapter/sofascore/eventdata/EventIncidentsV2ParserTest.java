package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV2ParserTest {

    @Test
    void parsesOrderedProviderIncidentsAndNestedTeamIdentity() throws Exception {
        var result = new EventIncidentsV2Parser().parse(
                51L, 16391135L, payload(), Instant.parse("2026-08-15T14:01:00Z"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(completeness.expectedSignals()).isEqualTo(2);
        });
        assertThat(result.data()).hasValueSatisfying(incidents -> {
            assertThat(incidents.providerEventId()).isEqualTo(16391135L);
            assertThat(incidents.incidents()).hasSize(2);
            assertThat(incidents.incidents().getFirst().participantProviderId()).contains(101L);
            assertThat(incidents.incidents().getLast().addedTime()).contains(1);
        });
    }

    private RawPayloadEvidence payload() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/fixtures/provider-j5/incidents-nominal.json")) {
            return RawPayloadEvidence.capture(input.readAllBytes());
        }
    }
}
