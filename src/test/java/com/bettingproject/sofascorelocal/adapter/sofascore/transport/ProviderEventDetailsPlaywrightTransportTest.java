package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderFailure;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderEventDetailsPlaywrightTransportTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-28T08:00:00Z");
    private static final Instant RECEIVED_AT = REQUESTED_AT.plusMillis(31);

    @Test
    void reusesOneCampaignAndPreservesStatusContentTypeAndExactBytes() {
        byte[] firstBody = "{\"event\":{\"id\":16386245}}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] secondBody = "{\"error\":\"not found\"}"
                .getBytes(StandardCharsets.UTF_8);
        RecordingCampaign campaign = new RecordingCampaign(List.of(
                response(200, "application/json; charset=utf-8", firstBody),
                response(404, "", secondBody)));
        RecordingFactory factory = new RecordingFactory(campaign);
        var transport = new ProviderEventDetailsPlaywrightTransport(factory);
        UUID campaignId = UUID.fromString("79c64017-ed27-4f7d-8298-54a23df9d1ee");

        try (var session = transport.openCampaign(campaignId)) {
            var first = session.execute(request(16386245L));
            var second = session.execute(request(16421052L));

            assertThat(first.httpStatus()).isEqualTo(200);
            assertThat(first.contentType()).isEqualTo("application/json; charset=utf-8");
            assertThat(first.payload().bytes()).isEqualTo(firstBody);
            assertThat(second.httpStatus()).isEqualTo(404);
            assertThat(second.contentType()).isEqualTo("application/octet-stream");
            assertThat(second.payload().bytes()).isEqualTo(secondBody);
        }

        assertThat(factory.campaignId).isEqualTo(campaignId);
        assertThat(factory.allowedEndpoints)
                .containsExactly(SofascoreEndpointType.EVENT_DETAILS);
        assertThat(campaign.requests).containsExactly(
                PlaywrightProviderRequest.eventDetails(16386245L),
                PlaywrightProviderRequest.eventDetails(16421052L));
        assertThat(campaign.closeCount).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("failureMappings")
    void translatesWorkerFailuresWithoutLeakingTheirCause(
            PlaywrightProviderFailure workerFailure,
            EventDetailsTransportFailure expectedFailure) {
        RecordingCampaign campaign = new RecordingCampaign(
                new PlaywrightProviderException(workerFailure));
        var transport = new ProviderEventDetailsPlaywrightTransport(
                new RecordingFactory(campaign));

        assertThatThrownBy(() -> {
            try (var session = transport.openCampaign(
                    UUID.fromString("a14cecf6-e2bb-4a12-8460-0687420b3d6f"))) {
                session.execute(request(16386245L));
            }
        })
                .isInstanceOf(EventDetailsTransportException.class)
                .hasMessageNotContaining("Playwright")
                .extracting("failure")
                .isEqualTo(expectedFailure);
        assertThat(campaign.closeCount).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(
            value = PlaywrightProviderFailure.class,
            names = {"UNEXPECTED_ROUTE", "REDIRECT_BLOCKED", "UNEXPECTED_CONTENT"})
    void mapsRouteIntegrityFailuresToUnexpectedContent(
            PlaywrightProviderFailure workerFailure) {
        EventDetailsTransportException translated =
                ProviderEventDetailsPlaywrightTransport.translate(
                        new PlaywrightProviderException(workerFailure));

        assertThat(translated.failure())
                .isEqualTo(EventDetailsTransportFailure.UNEXPECTED_CONTENT);
        assertThat(translated.getCause()).isNull();
    }

    private static Stream<Arguments> failureMappings() {
        return Stream.of(
                Arguments.of(
                        PlaywrightProviderFailure.TIMEOUT,
                        EventDetailsTransportFailure.TIMEOUT),
                Arguments.of(
                        PlaywrightProviderFailure.PAYLOAD_TOO_LARGE,
                        EventDetailsTransportFailure.PAYLOAD_TOO_LARGE),
                Arguments.of(
                        PlaywrightProviderFailure.SENSITIVE_CONTENT_REJECTED,
                        EventDetailsTransportFailure.SENSITIVE_CONTENT_REJECTED),
                Arguments.of(
                        PlaywrightProviderFailure.OPERATOR_STOP,
                        EventDetailsTransportFailure.OPERATOR_STOP),
                Arguments.of(
                        PlaywrightProviderFailure.RUNTIME_FAILURE,
                        EventDetailsTransportFailure.IO_FAILURE));
    }

    private static EventDetailsProviderRequest request(long eventId) {
        URI origin = URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        return EventDetailsProviderRequest.PHASE_1_EVENT_IDS.contains(eventId)
                ? EventDetailsProviderRequest.phase1(origin, eventId)
                : EventDetailsProviderRequest.phase2(origin, eventId);
    }

    private static PlaywrightProviderResponse response(
            int status,
            String contentType,
            byte[] body) {
        return new PlaywrightProviderResponse(
                REQUESTED_AT,
                RECEIVED_AT,
                status,
                contentType,
                Duration.between(REQUESTED_AT, RECEIVED_AT),
                RawPayloadEvidence.capture(body));
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

        private final List<PlaywrightProviderResponse> responses;
        private final PlaywrightProviderException failure;
        private final List<PlaywrightProviderRequest> requests = new ArrayList<>();
        private int responseIndex;
        private int closeCount;

        private RecordingCampaign(List<PlaywrightProviderResponse> responses) {
            this.responses = List.copyOf(responses);
            failure = null;
        }

        private RecordingCampaign(PlaywrightProviderException failure) {
            responses = List.of();
            this.failure = failure;
        }

        @Override
        public PlaywrightProviderResponse execute(PlaywrightProviderRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return responses.get(responseIndex++);
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
