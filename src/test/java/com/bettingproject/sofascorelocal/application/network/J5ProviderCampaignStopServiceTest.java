package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J5ProviderCampaignStopServiceTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000015");
    private static final Set<SofascoreEndpointType> J5_ENDPOINTS = Set.of(
            SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);

    private final PlaywrightProviderSupervisor supervisor =
            mock(PlaywrightProviderSupervisor.class);
    private final J5RealControlService control = mock(J5RealControlService.class);
    private final J5ProviderCampaignStopService service =
            new J5ProviderCampaignStopService(supervisor, control);

    @Test
    void signalsOnlyTheExactlyOwnedJ5CampaignBeforeLocking() {
        invokeCallback();

        service.stopAll();

        verify(supervisor).stopCampaign(REQUEST_ID, J5_ENDPOINTS);
        verify(control).stop(any());
    }

    @Test
    void reportsAnUnconfirmedWorkerStopAfterTheControlHasAppliedItsLock() {
        invokeCallback();
        when(supervisor.stopCampaign(REQUEST_ID, J5_ENDPOINTS))
                .thenThrow(new IllegalStateException("safe failure"));

        assertThatThrownBy(service::stopAll)
                .isInstanceOf(J5ProviderCampaignStopException.class)
                .hasMessage(
                        "The owned J5 Playwright provider campaign stop could not be confirmed");

        verify(control).stop(any());
    }

    @SuppressWarnings("unchecked")
    private void invokeCallback() {
        when(control.stop(any())).thenAnswer(invocation -> {
            Consumer<UUID> callback = invocation.getArgument(0);
            callback.accept(REQUEST_ID);
            return null;
        });
    }
}
