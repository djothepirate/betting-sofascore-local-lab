package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsProviderRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderTournamentScheduledEventsPlaywrightTransportTest {

    @Test
    void sendsOnlyTheValidatedScalarsAndPreservesA404Response() {
        byte[] body = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
        Instant requestedAt = Instant.parse("2026-08-27T09:00:00Z");
        Instant receivedAt = requestedAt.plusMillis(19);
        RecordingCampaign campaign = new RecordingCampaign(new PlaywrightProviderResponse(
                requestedAt,
                receivedAt,
                404,
                "application/json",
                Duration.between(requestedAt, receivedAt),
                RawPayloadEvidence.capture(body)));
        RecordingFactory factory = new RecordingFactory(campaign);
        var transport = new ProviderTournamentScheduledEventsPlaywrightTransport(factory);
        UUID campaignId = UUID.fromString("7fc634b6-bc93-47dc-aa76-e7f5c9f9fd94");
        var request = new TournamentScheduledEventsProviderRequest(
                URI.create(TournamentScheduledEventsProviderRequest.EXPECTED_ORIGIN),
                LocalDate.parse("2026-08-27"),
                17L);

        try (var session = transport.openCampaign(campaignId)) {
            var response = session.execute(request);

            assertThat(response.requestKey()).isEqualTo(
                    "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-27"
                            + "|uniqueTournamentId=17");
            assertThat(response.httpStatus()).isEqualTo(404);
            assertThat(response.contentType()).isEqualTo("application/json");
            assertThat(response.payload().bytes()).isEqualTo(body);
            assertThat(response.retryNotBefore()).isNull();
        }

        assertThat(factory.campaignId).isEqualTo(campaignId);
        assertThat(factory.allowedEndpoints)
                .containsExactly(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS);
        assertThat(campaign.request).isEqualTo(
                PlaywrightProviderRequest.tournamentScheduledEvents(
                        LocalDate.parse("2026-08-27"), 17L));
        assertThat(campaign.closeCount).isEqualTo(1);
    }

    private static final class RecordingFactory implements PlaywrightProviderCampaignFactory {

        private final PlaywrightProviderCampaign campaign;
        private UUID campaignId;
        private Set<SofascoreEndpointType> allowedEndpoints;

        private RecordingFactory(PlaywrightProviderCampaign campaign) {
            this.campaign = campaign;
        }

        @Override
        public PlaywrightProviderCampaign open(
                UUID requestedCampaignId,
                Set<SofascoreEndpointType> requestedEndpoints) {
            campaignId = requestedCampaignId;
            allowedEndpoints = Set.copyOf(requestedEndpoints);
            return campaign;
        }
    }

    private static final class RecordingCampaign implements PlaywrightProviderCampaign {

        private final PlaywrightProviderResponse response;
        private PlaywrightProviderRequest request;
        private int closeCount;

        private RecordingCampaign(PlaywrightProviderResponse response) {
            this.response = response;
        }

        @Override
        public PlaywrightProviderResponse execute(PlaywrightProviderRequest actualRequest) {
            request = actualRequest;
            return response;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
