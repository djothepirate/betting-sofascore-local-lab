package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.application.network.playwright.ChildJvmPlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderFailure;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryControlSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J3ProviderCampaignStopServiceTest {

    private static final UUID SCHEDULED_REQUEST_ID = UUID.fromString(
            "b0b13943-2552-4477-8d0f-e4a46645bc86");
    private static final UUID TOURNAMENT_REQUEST_ID = UUID.fromString(
            "49c6f745-695e-4b2d-b92e-b04d23574de5");

    private final PlaywrightProviderSupervisor supervisor =
            mock(PlaywrightProviderSupervisor.class);
    private final J3ManualCallControlService scheduledEventsControl =
            mock(J3ManualCallControlService.class);
    private final TournamentEventDiscoveryControlService tournamentDiscoveryControl =
            mock(TournamentEventDiscoveryControlService.class);
    private final J3ProviderCampaignStopService service =
            new J3ProviderCampaignStopService(
                    supervisor,
                    scheduledEventsControl,
                    tournamentDiscoveryControl);

    @Test
    void signalsTheExactlyOwnedScheduledWorkerDuringTheAtomicBusinessStop() {
        scheduledSnapshot(SCHEDULED_REQUEST_ID);

        service.stopScheduledEvents();

        verify(scheduledEventsControl).stopGlobally(any());
        verify(supervisor).stopCampaign(
                SCHEDULED_REQUEST_ID,
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
    }

    @Test
    void signalsTheExactlyOwnedTournamentWorkerDuringTheAtomicBusinessStop() {
        tournamentSnapshot(TOURNAMENT_REQUEST_ID);

        service.stopTournamentDiscovery();

        verify(tournamentDiscoveryControl).stop(any());
        verify(supervisor).stopCampaign(
                TOURNAMENT_REQUEST_ID,
                Set.of(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS));
    }

    @Test
    void locksScheduledEventsEvenWhenWorkerStopConfirmationFails() {
        scheduledSnapshot(SCHEDULED_REQUEST_ID);
        when(supervisor.stopCampaign(
                SCHEDULED_REQUEST_ID,
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .thenThrow(new IllegalStateException("safe failure"));

        assertThatThrownBy(service::stopScheduledEvents)
                .isInstanceOf(J3ProviderCampaignStopException.class)
                .hasMessage("The owned Playwright provider campaign stop could not be confirmed");

        verify(scheduledEventsControl).stopGlobally(any());
    }

    @Test
    void locksTournamentDiscoveryEvenWhenWorkerStopConfirmationFails() {
        tournamentSnapshot(TOURNAMENT_REQUEST_ID);
        when(supervisor.stopCampaign(
                TOURNAMENT_REQUEST_ID,
                Set.of(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS)))
                .thenThrow(new IllegalStateException("safe failure"));

        assertThatThrownBy(service::stopTournamentDiscovery)
                .isInstanceOf(J3ProviderCampaignStopException.class)
                .hasMessage("The owned Playwright provider campaign stop could not be confirmed");

        verify(tournamentDiscoveryControl).stop(any());
    }

    @Test
    void scheduledStopWithoutItsOwnRequestDoesNotSignalAnotherCampaign() {
        scheduledSnapshot(null);

        service.stopScheduledEvents();

        verify(scheduledEventsControl).stopGlobally(any());
        verify(supervisor, org.mockito.Mockito.never())
                .stopCampaign(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void tournamentStopWithoutItsOwnRequestDoesNotSignalAnotherCampaign() {
        tournamentSnapshot(null);

        service.stopTournamentDiscovery();

        verify(tournamentDiscoveryControl).stop(any());
        verify(supervisor, org.mockito.Mockito.never())
                .stopCampaign(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void scheduledSignalBeforeBusinessLockLeavesAnExactStopBeforeOpenTombstone() {
        scheduledSnapshot(SCHEDULED_REQUEST_ID);
        ChildJvmPlaywrightProviderSupervisor exactSupervisor = enabledSupervisor();
        J3ProviderCampaignStopService exactService = new J3ProviderCampaignStopService(
                exactSupervisor,
                scheduledEventsControl,
                tournamentDiscoveryControl);

        exactService.stopScheduledEvents();

        assertThatThrownBy(() -> exactSupervisor.open(
                SCHEDULED_REQUEST_ID,
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
    }

    @Test
    void tournamentSignalBeforeBusinessLockLeavesAnExactStopBeforeOpenTombstone() {
        tournamentSnapshot(TOURNAMENT_REQUEST_ID);
        ChildJvmPlaywrightProviderSupervisor exactSupervisor = enabledSupervisor();
        J3ProviderCampaignStopService exactService = new J3ProviderCampaignStopService(
                exactSupervisor,
                scheduledEventsControl,
                tournamentDiscoveryControl);

        exactService.stopTournamentDiscovery();

        assertThatThrownBy(() -> exactSupervisor.open(
                TOURNAMENT_REQUEST_ID,
                Set.of(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
    }

    private static ChildJvmPlaywrightProviderSupervisor enabledSupervisor() {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        return new ChildJvmPlaywrightProviderSupervisor(properties);
    }

    private void scheduledSnapshot(UUID requestId) {
        J3ManualCallControlSnapshot snapshot = mock(J3ManualCallControlSnapshot.class);
        if (requestId != null) {
            J3ManualCallIntentSnapshot intent = mock(J3ManualCallIntentSnapshot.class);
            when(intent.requestId()).thenReturn(requestId);
            when(snapshot.intent()).thenReturn(intent);
        }
        when(scheduledEventsControl.stopGlobally(any())).thenAnswer(invocation -> {
            Consumer<UUID> beforeLock = invocation.getArgument(0);
            if (requestId != null) {
                beforeLock.accept(requestId);
            }
            return snapshot;
        });
    }

    private void tournamentSnapshot(UUID requestId) {
        TournamentEventDiscoveryControlSnapshot snapshot =
                mock(TournamentEventDiscoveryControlSnapshot.class);
        when(snapshot.requestId()).thenReturn(requestId);
        when(tournamentDiscoveryControl.stop(any())).thenAnswer(invocation -> {
            Consumer<UUID> beforeLock = invocation.getArgument(0);
            if (requestId != null) {
                beforeLock.accept(requestId);
            }
            return snapshot;
        });
    }
}
