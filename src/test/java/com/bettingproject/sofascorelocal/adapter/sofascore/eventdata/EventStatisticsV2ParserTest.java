package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventStatisticsV2ParserTest {

    private final EventStatisticsV2Parser parser = new EventStatisticsV2Parser();

    @Test
    void parsesProviderShapeWithoutTrustingAnEventIdInThePayload() throws Exception {
        var result = parser.parse(41L, 16391135L, payload("statistics-nominal.json"), now());

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion()).isEqualTo("event-statistics-v2");
        assertThat(result.data()).hasValueSatisfying(statistics -> {
            assertThat(statistics.providerEventId()).isEqualTo(16391135L);
            assertThat(statistics.metrics()).hasSize(2);
            assertThat(statistics.metrics().get(1).homeValue()).contains("12");
        });
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.warnings()).extracting(J5ParseWarning::path)
                .contains("$.providerMetadata");
    }

    @Test
    void rejectsAmbiguousJsonWithoutReturningPartialData() {
        var payload = RawPayloadEvidence.capture(
                "{\"statistics\":[],\"statistics\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var result = parser.parse(42L, 16391135L, payload, now());

        assertThat(result.status()).isEqualTo(J5ParseStatus.UNEXPECTED_CONTENT);
        assertThat(result.data()).isEmpty();
        assertThat(result.completeness()).isEmpty();
    }

    private RawPayloadEvidence payload(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/fixtures/provider-j5/" + name)) {
            return RawPayloadEvidence.capture(input.readAllBytes());
        }
    }

    private static Instant now() {
        return Instant.parse("2026-08-15T14:00:00Z");
    }
}
