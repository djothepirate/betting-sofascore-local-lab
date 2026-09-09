package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;

class LiveProviderSessionTest {
    @ParameterizedTest
    @ValueSource(strings = {"live-v1", "live-v2", "live-v3", "live-v4", "live-v5", "live-v6", "live-v7"})
    void opensOnlyTheExplicitPolicyFactory(String policy) {
        var factory = mock(PlaywrightProviderCampaignFactory.class);
        var campaign = mock(PlaywrightProviderCampaign.class);
        var id = UUID.randomUUID();
        switch (policy) {
            case "live-v7" -> when(factory.openLiveGroupedV7(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            case "live-v6" -> when(factory.openLiveGroupedV6(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            case "live-v5" -> when(factory.openLiveGroupedV5(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            case "live-v4" -> when(factory.openLiveGrouped(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            default -> when(factory.open(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
        }
        try (var ignored = new LiveProviderSession(factory, id, policy)) {
            switch (policy) {
                case "live-v7" -> verify(factory).openLiveGroupedV7(id, LiveProviderSession.ENDPOINTS);
                case "live-v6" -> verify(factory).openLiveGroupedV6(id, LiveProviderSession.ENDPOINTS);
                case "live-v5" -> verify(factory).openLiveGroupedV5(id, LiveProviderSession.ENDPOINTS);
                case "live-v4" -> verify(factory).openLiveGrouped(id, LiveProviderSession.ENDPOINTS);
                default -> verify(factory).open(id, LiveProviderSession.ENDPOINTS);
            }
            verifyNoMoreInteractions(factory);
        }
        verify(campaign).close();
    }
}
