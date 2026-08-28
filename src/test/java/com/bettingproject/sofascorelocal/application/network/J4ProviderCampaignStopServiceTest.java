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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J4ProviderCampaignStopServiceTest {

    private static final UUID PHASE1_ID = UUID.fromString(
            "40100000-0000-0000-0000-000000000014");
    private static final UUID PHASE2_ID = UUID.fromString(
            "40200000-0000-0000-0000-000000000014");

    private final PlaywrightProviderSupervisor supervisor =
            mock(PlaywrightProviderSupervisor.class);
    private final J4RealPhase1ControlService phase1 = mock(J4RealPhase1ControlService.class);
    private final J4RealPhase2ControlService phase2 = mock(J4RealPhase2ControlService.class);
    private final J4ProviderCampaignStopService service =
            new J4ProviderCampaignStopService(supervisor, phase1, phase2);

    @Test
    void signalsOnlyTheTwoExactlyOwnedEventDetailsCampaignsBeforeLocking() {
        invokeCallback(phase1, PHASE1_ID);
        invokeCallback(phase2, PHASE2_ID);

        service.stopAll();

        verify(supervisor).stopCampaign(
                PHASE1_ID, Set.of(SofascoreEndpointType.EVENT_DETAILS));
        verify(supervisor).stopCampaign(
                PHASE2_ID, Set.of(SofascoreEndpointType.EVENT_DETAILS));
        verify(phase1).stop(any());
        verify(phase2).stop(any());
    }

    @Test
    void attemptsBothBusinessLocksWhenTheFirstWorkerStopFails() {
        invokeCallback(phase1, PHASE1_ID);
        invokeCallback(phase2, PHASE2_ID);
        when(supervisor.stopCampaign(
                PHASE1_ID, Set.of(SofascoreEndpointType.EVENT_DETAILS)))
                .thenThrow(new IllegalStateException("safe failure"));

        assertThatThrownBy(service::stopAll)
                .isInstanceOf(J4ProviderCampaignStopException.class)
                .hasMessage("The owned J4 Playwright provider campaign stop could not be confirmed");

        verify(phase1).stop(any());
        verify(phase2).stop(any());
        verify(supervisor).stopCampaign(
                PHASE2_ID, Set.of(SofascoreEndpointType.EVENT_DETAILS));
    }

    @Test
    void anIdenticalPhaseRequestIsStoppedIdempotently() {
        invokeCallback(phase1, PHASE1_ID);
        invokeCallback(phase2, PHASE1_ID);

        service.stopAll();

        verify(supervisor, times(2)).stopCampaign(
                PHASE1_ID, Set.of(SofascoreEndpointType.EVENT_DETAILS));
    }

    @SuppressWarnings("unchecked")
    private static void invokeCallback(J4RealPhase1ControlService control, UUID requestId) {
        when(control.stop(any())).thenAnswer(invocation -> {
            Consumer<UUID> callback = invocation.getArgument(0);
            callback.accept(requestId);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static void invokeCallback(J4RealPhase2ControlService control, UUID requestId) {
        when(control.stop(any())).thenAnswer(invocation -> {
            Consumer<UUID> callback = invocation.getArgument(0);
            callback.accept(requestId);
            return null;
        });
    }
}
