package com.bettingproject.sofascorelocal.fixture;

import org.junit.jupiter.api.Test;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventDetailsFixtureCorpusTest {

    private static final List<String> MANIFESTS = List.of(
            "fixtures/event-details/nominal.manifest.json",
            "fixtures/event-details/unknown-extra-field.manifest.json",
            "fixtures/schema-breaks/event-details-required-field-missing.manifest.json",
            "fixtures/schema-breaks/event-details-id-as-string.manifest.json",
            "fixtures/event-details/other-event.manifest.json");

    @Test
    void loadsFiveSyntheticEventDetailsFixturesWithVerifiedHashes() {
        ClasspathFixtureLoader loader = new ClasspathFixtureLoader();

        List<LoadedFixture> fixtures = MANIFESTS.stream().map(loader::load).toList();

        assertThat(fixtures).hasSize(5);
        assertThat(fixtures)
                .allSatisfy(fixture -> {
                    assertThat(fixture.manifest().endpointType())
                            .isEqualTo(SofascoreEndpointType.EVENT_DETAILS);
                    assertThat(fixture.manifest().fixtureOrigin())
                            .isEqualTo(FixtureOrigin.SYNTHETIC);
                    assertThat(fixture.manifest().providerSchemaValidated()).isFalse();
                    assertThat(fixture.manifest().parserVersion())
                            .isEqualTo("event-details-v1");
                    assertThat(fixture.contentKind()).isEqualTo(FixtureContentKind.JSON);
                    assertThat(fixture.canonicalJsonSha256()).isPresent();
                });
        assertThat(fixtures)
                .extracting(fixture -> fixture.manifest().fixtureId())
                .doesNotHaveDuplicates();
    }
}
