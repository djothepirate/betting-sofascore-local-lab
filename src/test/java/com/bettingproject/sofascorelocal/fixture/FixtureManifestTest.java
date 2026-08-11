package com.bettingproject.sofascorelocal.fixture;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureManifestTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void acceptsSyntheticMetadataWithoutClaimingProviderValidation() {
        FixtureManifest manifest = manifest(
                FixtureOrigin.SYNTHETIC,
                false,
                "fixtures/infrastructure/nominal.json");

        assertThat(manifest.manifestVersion()).isEqualTo(1);
        assertThat(manifest.fixtureId()).isEqualTo("infrastructure-nominal");
        assertThat(manifest.endpointType()).isEqualTo(SofascoreEndpointType.SCHEDULED_EVENTS);
        assertThat(manifest.fixtureOrigin()).isEqualTo(FixtureOrigin.SYNTHETIC);
        assertThat(manifest.providerSchemaValidated()).isFalse();
    }

    @Test
    void rejectsProviderValidationClaimForSyntheticFixture() {
        assertThatThrownBy(() -> manifest(
                FixtureOrigin.SYNTHETIC,
                true,
                "fixtures/infrastructure/nominal.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("synthetic fixture");
    }

    @Test
    void rejectsResourceTraversalAndOversizedLimits() {
        assertThatThrownBy(() -> manifest(
                FixtureOrigin.SYNTHETIC,
                false,
                "fixtures/../outside.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("normalized path");

        assertThatThrownBy(() -> new FixtureManifest(
                1,
                "infrastructure-nominal",
                SofascoreEndpointType.SCHEDULED_EVENTS,
                FixtureOrigin.SYNTHETIC,
                false,
                Instant.parse("2026-08-12T00:00:00Z"),
                null,
                "application/json",
                "UNASSIGNED",
                "fixtures/infrastructure/nominal.json",
                FixtureManifest.HARD_MAXIMUM_PAYLOAD_BYTES + 1,
                HASH,
                null,
                false,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumBytes");
    }

    @Test
    void keepsRemovedFieldMetadataImmutable() {
        List<String> removedFields = new ArrayList<>(List.of("debug.internal"));
        FixtureManifest manifest = new FixtureManifest(
                1,
                "infrastructure-nominal",
                SofascoreEndpointType.SCHEDULED_EVENTS,
                FixtureOrigin.SYNTHETIC,
                false,
                Instant.parse("2026-08-12T00:00:00Z"),
                null,
                "application/json",
                "UNASSIGNED",
                "fixtures/infrastructure/nominal.json",
                1024,
                HASH,
                null,
                true,
                removedFields);

        removedFields.add("late.mutation");

        assertThat(manifest.removedFields()).containsExactly("debug.internal");
        assertThatThrownBy(() -> manifest.removedFields().add("mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static FixtureManifest manifest(
            FixtureOrigin origin,
            boolean providerSchemaValidated,
            String payloadResource) {
        return new FixtureManifest(
                1,
                "infrastructure-nominal",
                SofascoreEndpointType.SCHEDULED_EVENTS,
                origin,
                providerSchemaValidated,
                Instant.parse("2026-08-12T00:00:00Z"),
                origin == FixtureOrigin.PROVIDER_OBSERVED ? 200 : null,
                "application/json",
                "UNASSIGNED",
                payloadResource,
                1024,
                HASH,
                null,
                false,
                List.of());
    }
}
