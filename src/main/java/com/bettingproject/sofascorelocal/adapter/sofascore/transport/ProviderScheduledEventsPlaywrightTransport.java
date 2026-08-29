package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.ScheduledEventsProviderPageTransport;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** J3 scheduled-events adapter backed only by one isolated Playwright worker campaign. */
@Component
public final class ProviderScheduledEventsPlaywrightTransport
        implements ScheduledEventsProviderPageTransport {

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";
    private static final Set<SofascoreEndpointType> ALLOWED_ENDPOINTS =
            Set.of(SofascoreEndpointType.SCHEDULED_EVENTS);

    private final PlaywrightProviderCampaignFactory campaignFactory;

    public ProviderScheduledEventsPlaywrightTransport(
            PlaywrightProviderCampaignFactory campaignFactory) {
        this.campaignFactory = Objects.requireNonNull(campaignFactory, "campaignFactory");
    }

    @Override
    public Campaign openCampaign(UUID campaignId) {
        Objects.requireNonNull(campaignId, "campaignId");
        try {
            PlaywrightProviderCampaign delegate = campaignFactory.open(
                    campaignId, ALLOWED_ENDPOINTS);
            return new Campaign() {

                private boolean closed;

                @Override
                public ScheduledEventsTransportResponse execute(
                        ScheduledEventsProviderPageRequest request) {
                    Objects.requireNonNull(request, "request");
                    if (closed) {
                        throw new ScheduledEventsTransportException(
                                ScheduledEventsTransportFailure.OPERATOR_STOP);
                    }
                    try {
                        PlaywrightProviderResponse response = delegate.execute(
                                PlaywrightProviderRequest.scheduledEvents(
                                        request.date(), request.page()));
                        return new ScheduledEventsTransportResponse(
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
                    if (closed) {
                        return;
                    }
                    closed = true;
                    try {
                        delegate.close();
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

    private static String contentType(PlaywrightProviderResponse response) {
        return response.contentType().isBlank()
                ? FALLBACK_CONTENT_TYPE
                : response.contentType();
    }

    static ScheduledEventsTransportException translate(
            PlaywrightProviderException exception) {
        ScheduledEventsTransportFailure failure = switch (exception.failure()) {
            case TIMEOUT -> ScheduledEventsTransportFailure.TIMEOUT;
            case PAYLOAD_TOO_LARGE -> ScheduledEventsTransportFailure.PAYLOAD_TOO_LARGE;
            case SENSITIVE_CONTENT_REJECTED ->
                    ScheduledEventsTransportFailure.SENSITIVE_CONTENT_REJECTED;
            case UNEXPECTED_ROUTE, REDIRECT_BLOCKED, UNEXPECTED_CONTENT ->
                    ScheduledEventsTransportFailure.UNEXPECTED_CONTENT;
            case OPERATOR_STOP -> ScheduledEventsTransportFailure.OPERATOR_STOP;
            default -> ScheduledEventsTransportFailure.IO_FAILURE;
        };
        return new ScheduledEventsTransportException(failure);
    }
}
