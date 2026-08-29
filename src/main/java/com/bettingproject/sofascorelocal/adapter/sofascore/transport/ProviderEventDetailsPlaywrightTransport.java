package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.EventDetailsProviderTransport;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** J4 EVENT_DETAILS adapter backed only by one isolated Playwright worker campaign. */
@Component
public final class ProviderEventDetailsPlaywrightTransport
        implements EventDetailsProviderTransport {

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";
    private static final Set<SofascoreEndpointType> ALLOWED_ENDPOINTS =
            Set.of(SofascoreEndpointType.EVENT_DETAILS);

    private final PlaywrightProviderCampaignFactory campaignFactory;

    public ProviderEventDetailsPlaywrightTransport(
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
                public EventDetailsTransportResponse execute(
                        EventDetailsProviderRequest request) {
                    Objects.requireNonNull(request, "request");
                    if (closed) {
                        throw new EventDetailsTransportException(
                                EventDetailsTransportFailure.OPERATOR_STOP);
                    }
                    try {
                        PlaywrightProviderResponse response = delegate.execute(
                                PlaywrightProviderRequest.eventDetails(request.eventId()));
                        return new EventDetailsTransportResponse(
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

    static EventDetailsTransportException translate(
            PlaywrightProviderException exception) {
        EventDetailsTransportFailure failure = switch (exception.failure()) {
            case TIMEOUT -> EventDetailsTransportFailure.TIMEOUT;
            case PAYLOAD_TOO_LARGE -> EventDetailsTransportFailure.PAYLOAD_TOO_LARGE;
            case SENSITIVE_CONTENT_REJECTED ->
                    EventDetailsTransportFailure.SENSITIVE_CONTENT_REJECTED;
            case UNEXPECTED_ROUTE, REDIRECT_BLOCKED, UNEXPECTED_CONTENT ->
                    EventDetailsTransportFailure.UNEXPECTED_CONTENT;
            case OPERATOR_STOP -> EventDetailsTransportFailure.OPERATOR_STOP;
            default -> EventDetailsTransportFailure.IO_FAILURE;
        };
        return new EventDetailsTransportException(failure);
    }
}
