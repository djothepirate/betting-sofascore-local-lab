package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryControlSnapshot;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class J3ProviderCampaignStopService {

    private final PlaywrightProviderSupervisor supervisor;
    private final J3ManualCallControlService scheduledEventsControl;
    private final TournamentEventDiscoveryControlService tournamentDiscoveryControl;

    public J3ProviderCampaignStopService(
            PlaywrightProviderSupervisor supervisor,
            J3ManualCallControlService scheduledEventsControl,
            TournamentEventDiscoveryControlService tournamentDiscoveryControl) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.scheduledEventsControl = Objects.requireNonNull(
                scheduledEventsControl, "scheduledEventsControl");
        this.tournamentDiscoveryControl = Objects.requireNonNull(
                tournamentDiscoveryControl, "tournamentDiscoveryControl");
    }

    public J3ManualCallControlSnapshot stopScheduledEvents() {
        return scheduledEventsControl.stopGlobally(requestId -> stopOwnedCampaign(
                requestId,
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)));
    }

    public TournamentEventDiscoveryControlSnapshot stopTournamentDiscovery() {
        return tournamentDiscoveryControl.stop(requestId -> stopOwnedCampaign(
                requestId,
                Set.of(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS)));
    }

    private void stopOwnedCampaign(
            UUID requestId,
            Set<SofascoreEndpointType> allowedEndpoints) {
        if (requestId == null) {
            return;
        }
        try {
            supervisor.stopCampaign(requestId, allowedEndpoints);
        }
        catch (RuntimeException exception) {
            throw new J3ProviderCampaignStopException(exception);
        }
    }
}
