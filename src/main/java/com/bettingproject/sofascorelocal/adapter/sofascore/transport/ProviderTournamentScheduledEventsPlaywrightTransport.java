package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.TournamentScheduledEventsProviderTransport;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Tournament discovery adapter backed only by one isolated Playwright worker campaign. */
@Component
public final class ProviderTournamentScheduledEventsPlaywrightTransport
        implements TournamentScheduledEventsProviderTransport {

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";
    private static final Set<SofascoreEndpointType> ALLOWED_ENDPOINTS =
            Set.of(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS);

    private final PlaywrightProviderCampaignFactory campaignFactory;

    public ProviderTournamentScheduledEventsPlaywrightTransport(
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
                public TournamentScheduledEventsTransportResponse execute(
                        TournamentScheduledEventsProviderRequest request) {
                    Objects.requireNonNull(request, "request");
                    if (closed) {
                        throw new ScheduledEventsTransportException(
                                ScheduledEventsTransportFailure.OPERATOR_STOP);
                    }
                    try {
                        PlaywrightProviderResponse response = delegate.execute(
                                PlaywrightProviderRequest.tournamentScheduledEvents(
                                        request.date(), request.uniqueTournamentId()));
                        return new TournamentScheduledEventsTransportResponse(
                                request.requestKey(),
                                response.requestedAt(),
                                response.receivedAt(),
                                response.httpStatus(),
                                contentType(response),
                                response.latency(),
                                response.payload());
                    }
                    catch (PlaywrightProviderException exception) {
                        throw ProviderScheduledEventsPlaywrightTransport.translate(exception);
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
                        throw ProviderScheduledEventsPlaywrightTransport.translate(exception);
                    }
                }
            };
        }
        catch (PlaywrightProviderException exception) {
            throw ProviderScheduledEventsPlaywrightTransport.translate(exception);
        }
    }

    private static String contentType(PlaywrightProviderResponse response) {
        return response.contentType().isBlank()
                ? FALLBACK_CONTENT_TYPE
                : response.contentType();
    }
}
