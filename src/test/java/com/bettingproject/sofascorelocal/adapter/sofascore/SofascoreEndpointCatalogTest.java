package com.bettingproject.sofascorelocal.adapter.sofascore;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SofascoreEndpointCatalogTest {

    private final SofascoreEndpointCatalog catalog = new SofascoreEndpointCatalog();

    @Test
    void declaresAllLogicalFamiliesWithoutAnyCallableUri() {
        assertThat(catalog.list())
                .hasSize(SofascoreEndpointType.values().length)
                .allSatisfy(definition -> {
                    assertThat(definition.manualOnly()).isTrue();
                    assertThat(definition.callable()).isFalse();
                    assertThat(definition.uriTemplateConfigured()).isFalse();
                });
    }

    @Test
    void usesTheInitialCachePolicyFromTheScopingDocument() {
        assertThat(catalog.get(SofascoreEndpointType.SCHEDULED_EVENTS).cacheTtl())
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(catalog.get(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS).cacheTtl())
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(catalog.get(SofascoreEndpointType.TOURNAMENT_STANDINGS).cacheTtl())
                .isEqualTo(Duration.ofHours(6));
    }
}
