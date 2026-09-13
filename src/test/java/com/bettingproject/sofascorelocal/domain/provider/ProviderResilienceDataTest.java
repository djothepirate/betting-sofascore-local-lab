package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureProfile.*;

class ProviderResilienceDataTest {
    @org.junit.jupiter.api.Test
    void closedDepartureProfilesKeepV1DefaultsAndExposeTheQualifiedV8Envelope() {
        assertThat(LEGACY_V1.persistenceValue()).isEqualTo("legacy-v1");
        assertThat(LEGACY_V1.minimumDepartureInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(LEGACY_V1.maximumDeparturesPerMinute()).isEqualTo(25);
        assertThat(LEGACY_V1.maximumDeparturesPerHour()).isEqualTo(1000);

        assertThat(LIVE_V8.persistenceValue()).isEqualTo("live-v8");
        assertThat(LIVE_V8.minimumDepartureInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(LIVE_V8.maximumDeparturesPerMinute()).isEqualTo(45);
        assertThat(LIVE_V8.maximumDeparturesPerHour()).isEqualTo(2756);
    }

    @org.junit.jupiter.api.Test
    void persistedPreV48NullIsLegacyAndUnknownProfilesFailClosed() {
        assertThat(ProviderResilienceData.DepartureProfile.fromPersistenceValue(null)).isEqualTo(LEGACY_V1);
        assertThat(ProviderResilienceData.DepartureProfile.fromPersistenceValue("live-v8")).isEqualTo(LIVE_V8);
        assertThatThrownBy(()->ProviderResilienceData.DepartureProfile.fromPersistenceValue("unbounded"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PROVIDER_DEPARTURE_PROFILE_UNSUPPORTED");
    }
}
