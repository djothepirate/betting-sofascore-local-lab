package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightDispatchAdmission;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J5EventDataProviderTransport;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** J5 adapter backed only by one isolated Playwright worker campaign. */
@Component
public final class ProviderJ5EventDataPlaywrightTransport
        implements J5EventDataProviderTransport {

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";
    private static final List<SofascoreEndpointType> ORDERED_ENDPOINTS = List.of(
            SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);
    private static final Set<SofascoreEndpointType> ALLOWED_ENDPOINTS =
            Set.copyOf(ORDERED_ENDPOINTS);

    private final PlaywrightProviderCampaignFactory campaignFactory;

    public ProviderJ5EventDataPlaywrightTransport(
            PlaywrightProviderCampaignFactory campaignFactory) {
        this.campaignFactory = Objects.requireNonNull(campaignFactory, "campaignFactory");
    }

    @Override
    public Campaign openCampaign(UUID campaignId) {
        Objects.requireNonNull(campaignId, "campaignId");
        try {
            PlaywrightProviderCampaign delegate = campaignFactory.openManualJ5Grouped(
                    campaignId, ALLOWED_ENDPOINTS);
            return new Campaign() {

                private boolean closeRequested;
                private boolean cleanupCompleted;
                private long eventId;
                private int nextEndpointIndex;
                private final UUID groupId = UUID.randomUUID();

                @Override
                public J5EventDataTransportResponse execute(
                        J5EventDataProviderRequest request) {
                    return execute(request, () -> { });
                }

                @Override
                public J5EventDataTransportResponse execute(
                        J5EventDataProviderRequest request, Runnable dispatchGuard) {
                    Objects.requireNonNull(request, "request");
                    Objects.requireNonNull(dispatchGuard, "dispatchGuard");
                    if (closeRequested) {
                        throw new J5EventDataTransportException(
                                J5EventDataTransportFailure.OPERATOR_STOP);
                    }
                    if (nextEndpointIndex >= ORDERED_ENDPOINTS.size()
                            || request.endpointType()
                                    != ORDERED_ENDPOINTS.get(nextEndpointIndex)
                            || eventId != 0 && request.eventId() != eventId) {
                        throw new J5EventDataTransportException(
                                J5EventDataTransportFailure.UNEXPECTED_CONTENT);
                    }
                    if (eventId == 0) {
                        eventId = request.eventId();
                    }
                    try {
                        PlaywrightProviderResponse response = delegate.executeGrouped(
                                toPlaywrightRequest(request),
                                new LiveProviderDispatchGroup(campaignId, groupId, eventId,
                                        LiveProviderDispatchGroup.Phase.MANUAL_J5),
                                new PlaywrightDispatchAdmission() {
                                    @Override public void check() { dispatchGuard.run(); }
                                    @Override public Permit acquireDispatchPermit() {
                                        dispatchGuard.run();
                                        return () -> { };
                                    }
                                });
                        nextEndpointIndex++;
                        return new J5EventDataTransportResponse(
                                request.endpointType(),
                                request.requestKey(),
                                response.requestedAt(),
                                response.receivedAt(),
                                response.httpStatus(),
                                contentType(response),
                                response.latency(),
                                response.payload());
                    }
                    catch (PlaywrightProviderException exception) {
                        throw translate(exception);
                    }
                }

                @Override
                public void close() {
                    if (cleanupCompleted) {
                        return;
                    }
                    closeRequested = true;
                    try {
                        delegate.close();
                        cleanupCompleted = true;
                    }
                    catch (PlaywrightProviderException exception) {
                        throw translate(exception);
                    }
                }
            };
        }
        catch (PlaywrightProviderException exception) {
            throw translate(exception);
        }
    }

    private static PlaywrightProviderRequest toPlaywrightRequest(
            J5EventDataProviderRequest request) {
        return switch (request.endpointType()) {
            case EVENT_STATISTICS -> PlaywrightProviderRequest.eventStatistics(request.eventId());
            case EVENT_INCIDENTS -> PlaywrightProviderRequest.eventIncidents(request.eventId());
            case EVENT_LINEUPS -> PlaywrightProviderRequest.eventLineups(request.eventId());
            default -> throw new J5EventDataTransportException(
                    J5EventDataTransportFailure.UNEXPECTED_CONTENT);
        };
    }

    private static String contentType(PlaywrightProviderResponse response) {
        return response.contentType().isBlank()
                ? FALLBACK_CONTENT_TYPE
                : response.contentType();
    }

    static J5EventDataTransportException translate(
            PlaywrightProviderException exception) {
        J5EventDataTransportFailure failure = switch (exception.failure()) {
            case TIMEOUT -> J5EventDataTransportFailure.TIMEOUT;
            case PAYLOAD_TOO_LARGE -> J5EventDataTransportFailure.PAYLOAD_TOO_LARGE;
            case SENSITIVE_CONTENT_REJECTED ->
                    J5EventDataTransportFailure.SENSITIVE_CONTENT_REJECTED;
            case UNEXPECTED_ROUTE, REDIRECT_BLOCKED, UNEXPECTED_CONTENT ->
                    J5EventDataTransportFailure.UNEXPECTED_CONTENT;
            case OPERATOR_STOP -> J5EventDataTransportFailure.OPERATOR_STOP;
            default -> J5EventDataTransportFailure.IO_FAILURE;
        };
        return new J5EventDataTransportException(failure);
    }
}
