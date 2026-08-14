package com.bettingproject.sofascorelocal.domain.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalEventIdentityTest {

    @Test
    void derivesAStableLocalIdentityOnlyFromProviderAndProviderEventId() {
        var first = CanonicalEventIdentity.sofascore(123456789L);
        var repeated = CanonicalEventIdentity.sofascore(123456789L);
        var other = CanonicalEventIdentity.sofascore(123456790L);

        assertThat(repeated).isEqualTo(first);
        assertThat(other.value()).isNotEqualTo(first.value());
        assertThat(first.provider()).isEqualTo("SOFASCORE");
        assertThat(first.providerEventId()).isEqualTo(123456789L);
    }

    @Test
    void rejectsAnUuidThatDoesNotMatchItsProviderIdentity() {
        var first = CanonicalEventIdentity.sofascore(11L);

        assertThatThrownBy(() -> new CanonicalEventIdentity(
                first.value(),
                "SOFASCORE",
                12L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
    }
}
