package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventLineupsV2ParserTest {

    private final EventLineupsV2Parser parser = new EventLineupsV2Parser();

    @Test
    void acceptsProviderJerseyTextAndPlayerPositionFallback() throws Exception {
        var result = parser.parse(61L, 16391135L, payload("lineups-nominal.json"), now());

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.data()).hasValueSatisfying(lineups -> {
            assertThat(lineups.confirmed()).isTrue();
            assertThat(lineups.home().players().getFirst().shirtNumber()).contains(1);
            assertThat(lineups.home().players().getFirst().position()).contains("G");
            assertThat(lineups.away().players().getFirst().starter()).isFalse();
        });
    }

    @Test
    void preservesAnExplicitUnconfirmedEmptyResponse() throws Exception {
        var result = parser.parse(
                62L, 16421052L, payload("lineups-unconfirmed-empty.json"), now());

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.EMPTY_VALID));
        assertThat(result.data()).hasValueSatisfying(lineups -> {
            assertThat(lineups.confirmed()).isFalse();
            assertThat(lineups.home().players()).isEmpty();
            assertThat(lineups.away().players()).isEmpty();
        });
    }

    private RawPayloadEvidence payload(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/fixtures/provider-j5/" + name)) {
            return RawPayloadEvidence.capture(input.readAllBytes());
        }
    }

    private static Instant now() {
        return Instant.parse("2026-08-15T14:02:00Z");
    }
}
