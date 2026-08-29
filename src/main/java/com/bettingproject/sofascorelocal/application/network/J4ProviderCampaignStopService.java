package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class J4ProviderCampaignStopService {

    private static final Set<SofascoreEndpointType> EVENT_DETAILS_ONLY =
            Set.of(SofascoreEndpointType.EVENT_DETAILS);

    private final PlaywrightProviderSupervisor supervisor;
    private final J4RealPhase1ControlService phase1Control;
    private final J4RealPhase2ControlService phase2Control;

    public J4ProviderCampaignStopService(
            PlaywrightProviderSupervisor supervisor,
            J4RealPhase1ControlService phase1Control,
            J4RealPhase2ControlService phase2Control) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.phase1Control = Objects.requireNonNull(phase1Control, "phase1Control");
        this.phase2Control = Objects.requireNonNull(phase2Control, "phase2Control");
    }

    public void stopAll() {
        RuntimeException failure = null;
        try {
            phase1Control.stop(this::stopOwnedCampaign);
        }
        catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            phase2Control.stop(this::stopOwnedCampaign);
        }
        catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            }
            else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw new J4ProviderCampaignStopException(failure);
        }
    }

    private void stopOwnedCampaign(UUID requestId) {
        if (requestId != null) {
            supervisor.stopCampaign(requestId, EVENT_DETAILS_ONLY);
        }
    }
}
