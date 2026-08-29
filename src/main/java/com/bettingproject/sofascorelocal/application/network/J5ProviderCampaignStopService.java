package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class J5ProviderCampaignStopService {

    private static final Set<SofascoreEndpointType> J5_EVENT_DATA_ENDPOINTS = Set.of(
            SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);

    private final PlaywrightProviderSupervisor supervisor;
    private final J5RealControlService controlService;

    public J5ProviderCampaignStopService(
            PlaywrightProviderSupervisor supervisor,
            J5RealControlService controlService) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.controlService = Objects.requireNonNull(controlService, "controlService");
    }

    public void stopAll() {
        try {
            controlService.stop(this::stopOwnedCampaign);
        }
        catch (RuntimeException exception) {
            throw new J5ProviderCampaignStopException(exception);
        }
    }

    private void stopOwnedCampaign(UUID requestId) {
        if (requestId != null) {
            supervisor.stopCampaign(requestId, J5_EVENT_DATA_ENDPOINTS);
        }
    }
}
