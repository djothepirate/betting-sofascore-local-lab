package com.bettingproject.sofascorelocal.fixture;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class J5EventDataFixtureCorpusTest {

    private static final List<String> MANIFESTS = List.of(
            "fixtures/event-statistics/nominal.manifest.json",
            "fixtures/event-statistics/partial-missing-away.manifest.json",
            "fixtures/schema-breaks/event-statistics-id-as-string.manifest.json",
            "fixtures/event-incidents/nominal.manifest.json",
            "fixtures/event-incidents/empty.manifest.json",
            "fixtures/schema-breaks/event-incidents-time-as-string.manifest.json",
            "fixtures/event-lineups/nominal.manifest.json",
            "fixtures/event-lineups/partial-unconfirmed.manifest.json",
            "fixtures/schema-breaks/event-lineups-player-id-as-string.manifest.json");

    @Test
    void loadsNineSyntheticJ5FixturesWithVerifiedHashesAndNoProviderClaim() {
        ClasspathFixtureLoader loader = new ClasspathFixtureLoader();

        List<LoadedFixture> fixtures = MANIFESTS.stream().map(loader::load).toList();

        assertThat(fixtures).hasSize(9);
        assertThat(fixtures)
                .allSatisfy(fixture -> {
                    assertThat(fixture.manifest().fixtureOrigin())
                            .isEqualTo(FixtureOrigin.SYNTHETIC);
                    assertThat(fixture.manifest().providerSchemaValidated()).isFalse();
                    assertThat(fixture.contentKind()).isEqualTo(FixtureContentKind.JSON);
                    assertThat(fixture.canonicalJsonSha256()).isPresent();
                });
        assertThat(fixtures)
                .extracting(fixture -> fixture.manifest().fixtureId())
                .doesNotHaveDuplicates();
    }
}
