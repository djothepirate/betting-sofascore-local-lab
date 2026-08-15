package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventLineupsV1ParserTest {

    private final ClasspathFixtureLoader loader = new ClasspathFixtureLoader();
    private final EventLineupsV1Parser parser = new EventLineupsV1Parser();

    @Test
    void parsesConfirmedHomeAndAwayLineupsAsComplete() {
        var result = parser.parse(loader.load(
                "fixtures/event-lineups/nominal.manifest.json"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE);
            assertThat(completeness.scorePercent()).isEqualTo(100);
            assertThat(completeness.expectedSignals()).isEqualTo(13);
        });
        assertThat(result.data()).hasValueSatisfying(lineups -> {
            assertThat(lineups.providerEventId()).isEqualTo(900001L);
            assertThat(lineups.confirmed()).isTrue();
            assertThat(lineups.home().formation()).contains("4-3-3");
            assertThat(lineups.home().players()).hasSize(2);
            assertThat(lineups.away().formation()).contains("4-4-2");
            assertThat(lineups.away().players()).hasSize(2);
            assertThat(lineups.away().players().getLast().starter()).isFalse();
        });
    }

    @Test
    void exposesUnconfirmedAndIncompleteLineupsWithoutInventingPlayers() {
        var result = parser.parse(loader.load(
                "fixtures/event-lineups/partial-unconfirmed.manifest.json"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data()).hasValueSatisfying(lineups -> {
            assertThat(lineups.confirmed()).isFalse();
            assertThat(lineups.home().players()).hasSize(1);
            assertThat(lineups.away().players()).isEmpty();
        });
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.scorePercent()).isEqualTo(42);
            assertThat(completeness.missingPaths()).containsExactly(
                    "$.confirmed=true",
                    "$.home.formation",
                    "$.home.players[0].position",
                    "$.away.players");
        });
    }

    @Test
    void rejectsAPlayerIdentifierEncodedAsTextWithoutPartialData() {
        var result = parser.parse(loader.load(
                "fixtures/schema-breaks/event-lineups-player-id-as-string.manifest.json"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.data()).isEmpty();
        assertThat(result.completeness()).isEmpty();
        assertThat(result.problems())
                .extracting(J5ParseProblem::path)
                .contains("$.home.players[0].player.id");
    }
}
