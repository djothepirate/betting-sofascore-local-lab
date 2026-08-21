package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J3ManualCallExecutionResultTest {

    @Test
    void representsACompletedLocalImportWithoutInventingProviderOrCacheActivity() {
        J3ManualCallExecutionResult result =
                J3ManualCallExecutionResult.successfulLocalImport(3);

        assertThat(result.completed()).isTrue();
        assertThat(result.completedPages()).isEqualTo(3);
        assertThat(result.providerRequests()).isZero();
        assertThat(result.cacheHits()).isZero();
        assertThat(result.localJsonImports()).isEqualTo(3);
    }

    @Test
    void refusesToMixLocalImportsWithDirectPageResolutions() {
        assertThatThrownBy(() -> new J3ManualCallExecutionResult(
                true,
                2,
                null,
                null,
                1,
                0,
                1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot mix local imports");
    }
}
