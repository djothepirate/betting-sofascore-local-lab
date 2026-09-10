package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureDecision;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureProfile;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureReason;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.Snapshot;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.State;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.*;

/** No provider transport: launch admission is proven from local durable pressure only. */
class LiveCampaignV8InitialWaveAdmissionTest {
    private static final Instant NOW=Instant.parse("2030-09-09T12:00:00Z");

    @Test
    void admitsTenTargetsOnlyWhenTheSharedStoreConfirmsAllFortyInitialSlots() {
        ProviderResilienceStore resilience=mock(ProviderResilienceStore.class);
        Snapshot open=open();
        when(resilience.departureCapacityDecision(DepartureProfile.LIVE_V8,40,NOW))
                .thenReturn(new DepartureDecision(true,DepartureReason.ALLOWED,NOW,open));

        LiveCampaignService.requireV8InitialWaveCapacity(resilience,10,NOW);

        verify(resilience).departureCapacityDecision(DepartureProfile.LIVE_V8,40,NOW);
        verifyNoMoreInteractions(resilience);
    }

    @Test
    void appliesTheSameReadOnlyProofToEveryPositiveV8SelectionSize() {
        ProviderResilienceStore resilience=mock(ProviderResilienceStore.class);
        Snapshot open=open();
        when(resilience.departureCapacityDecision(DepartureProfile.LIVE_V8,28,NOW))
                .thenReturn(new DepartureDecision(true,DepartureReason.ALLOWED,NOW,open));

        LiveCampaignService.requireV8InitialWaveCapacity(resilience,7,NOW);

        verify(resilience).departureCapacityDecision(DepartureProfile.LIVE_V8,28,NOW);
        verifyNoMoreInteractions(resilience);
    }

    @Test
    void rejectsTheLaunchBeforeAnyWorkerWhenOneSharedSlotIsMissing() {
        ProviderResilienceStore resilience=mock(ProviderResilienceStore.class);
        Snapshot open=open();
        when(resilience.departureCapacityDecision(DepartureProfile.LIVE_V8,40,NOW))
                .thenReturn(new DepartureDecision(false,DepartureReason.RATE_LIMITED,NOW.plusSeconds(30),open));

        assertThatIllegalStateException().isThrownBy(() ->
                        LiveCampaignService.requireV8InitialWaveCapacity(resilience,10,NOW))
                .withMessage("LIVE_V8_FRESHNESS_CAPACITY_UNAVAILABLE");

        verify(resilience).departureCapacityDecision(DepartureProfile.LIVE_V8,40,NOW);
        verifyNoMoreInteractions(resilience);
    }

    @Test
    void preservesThePersistentRefusalCircuitInsteadOfReclassifyingItAsCapacityPressure() {
        ProviderResilienceStore resilience=mock(ProviderResilienceStore.class);
        Snapshot suspended=new Snapshot(State.SUSPENDED,1,NOW,403,NOW,null,null,
                java.util.UUID.randomUUID(),null,null,null);
        when(resilience.departureCapacityDecision(DepartureProfile.LIVE_V8,40,NOW))
                .thenReturn(new DepartureDecision(false,DepartureReason.PROVIDER_SUSPENDED,null,suspended));

        assertThatIllegalStateException().isThrownBy(() ->
                        LiveCampaignService.requireV8InitialWaveCapacity(resilience,10,NOW))
                .withMessage("PROVIDER_SUSPENDED");
    }

    private static Snapshot open() {
        return new Snapshot(State.OPEN,0,NOW,null,null,null,null,null,null,null,null);
    }
}
