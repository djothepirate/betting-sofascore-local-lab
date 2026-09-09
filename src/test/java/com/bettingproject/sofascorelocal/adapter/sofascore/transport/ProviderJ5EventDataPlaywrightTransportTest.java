package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderFailure;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightDispatchAdmission;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J5EventDataProviderTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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

class ProviderJ5EventDataPlaywrightTransportTest {

    private static final UUID CAMPAIGN_ID = UUID.fromString(
            "15000000-0000-0000-0000-000000000015");
    private static final long EVENT_ID = 16_391_135L;
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-28T19:00:00Z");
    private static final Instant RECEIVED_AT = REQUESTED_AT.plusMillis(37);

    @Test
    void reusesOneCampaignInExactOrderAndPreservesEveryResponseField() {
        byte[] statisticsBody = "{\"statistics\":[]}".getBytes(StandardCharsets.UTF_8);
        byte[] incidentsBody = "{\"error\":\"not found\"}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] lineupsBody = "{\"confirmed\":true,\"home\":{}}"
                .getBytes(StandardCharsets.UTF_8);
        RecordingCampaign campaign = new RecordingCampaign(List.of(
                response(200, "application/json; charset=utf-8", statisticsBody),
                response(404, "", incidentsBody),
                response(200, "application/problem+json", lineupsBody)));
        RecordingFactory factory = new RecordingFactory(campaign);
        var transport = new ProviderJ5EventDataPlaywrightTransport(factory);

        J5EventDataProviderTransport.Campaign session = transport.openCampaign(CAMPAIGN_ID);
        var statistics = session.execute(request(SofascoreEndpointType.EVENT_STATISTICS));
        var incidents = session.execute(request(SofascoreEndpointType.EVENT_INCIDENTS));
        var lineups = session.execute(request(SofascoreEndpointType.EVENT_LINEUPS));
        session.close();
        session.close();

        assertResponse(
                statistics,
                SofascoreEndpointType.EVENT_STATISTICS,
                200,
                "application/json; charset=utf-8",
                statisticsBody);
        assertResponse(
                incidents,
                SofascoreEndpointType.EVENT_INCIDENTS,
                404,
                "application/octet-stream",
                incidentsBody);
        assertResponse(
                lineups,
                SofascoreEndpointType.EVENT_LINEUPS,
                200,
                "application/problem+json",
                lineupsBody);
        assertThat(factory.openCount).isEqualTo(1);
        assertThat(factory.campaignId).isEqualTo(CAMPAIGN_ID);
        assertThat(factory.allowedEndpoints).containsExactlyInAnyOrder(
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(campaign.requests).containsExactly(
                PlaywrightProviderRequest.eventStatistics(EVENT_ID),
                PlaywrightProviderRequest.eventIncidents(EVENT_ID),
                PlaywrightProviderRequest.eventLineups(EVENT_ID));
        assertThat(campaign.closeCount).isEqualTo(1);
        assertThat(campaign.groups).hasSize(3).allSatisfy(group -> {
            assertThat(group.campaignId()).isEqualTo(CAMPAIGN_ID);
            assertThat(group.providerEventId()).isEqualTo(EVENT_ID);
            assertThat(group.phase()).isEqualTo(LiveProviderDispatchGroup.Phase.MANUAL_J5);
        });
        assertThat(campaign.groups).extracting(LiveProviderDispatchGroup::groupId)
                .containsOnly(campaign.groups.getFirst().groupId());
    }

    @Test
    void propagatesTheFinalClaimGuardBeforeEveryGroupedWorkerDispatch() {
        RecordingCampaign campaign = successfulCampaign(3);
        var session = new ProviderJ5EventDataPlaywrightTransport(new RecordingFactory(campaign))
                .openCampaign(CAMPAIGN_ID);
        session.execute(request(SofascoreEndpointType.EVENT_STATISTICS), () -> { });
        assertThatThrownBy(() -> session.execute(request(SofascoreEndpointType.EVENT_INCIDENTS),
                () -> { throw new IllegalStateException("claim stopped"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("claim stopped");
        assertThat(campaign.requests).containsExactly(PlaywrightProviderRequest.eventStatistics(EVENT_ID));
        session.close();
    }

    @Test
    void rejectsOutOfOrderAndDifferentIdentityBeforeDelegatingToTheWorker() {
        RecordingCampaign outOfOrder = successfulCampaign(3);
        var outOfOrderSession = new ProviderJ5EventDataPlaywrightTransport(
                new RecordingFactory(outOfOrder)).openCampaign(CAMPAIGN_ID);

        assertFailure(
                () -> outOfOrderSession.execute(
                        request(SofascoreEndpointType.EVENT_INCIDENTS)),
                J5EventDataTransportFailure.UNEXPECTED_CONTENT);
        assertThat(outOfOrder.requests).isEmpty();
        outOfOrderSession.close();

        RecordingCampaign differentIdentity = successfulCampaign(3);
        var identitySession = new ProviderJ5EventDataPlaywrightTransport(
                new RecordingFactory(differentIdentity)).openCampaign(CAMPAIGN_ID);
        identitySession.execute(request(SofascoreEndpointType.EVENT_STATISTICS));

        assertFailure(
                () -> identitySession.execute(request(
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        EVENT_ID + 1)),
                J5EventDataTransportFailure.UNEXPECTED_CONTENT);
        assertThat(differentIdentity.requests)
                .containsExactly(PlaywrightProviderRequest.eventStatistics(EVENT_ID));
        identitySession.close();
    }

    @Test
    void refusesAFourthRequestAndAnyExecutionAfterAnIdempotentClose() {
        RecordingCampaign bounded = successfulCampaign(3);
        var boundedSession = new ProviderJ5EventDataPlaywrightTransport(
                new RecordingFactory(bounded)).openCampaign(CAMPAIGN_ID);
        boundedSession.execute(request(SofascoreEndpointType.EVENT_STATISTICS));
        boundedSession.execute(request(SofascoreEndpointType.EVENT_INCIDENTS));
        boundedSession.execute(request(SofascoreEndpointType.EVENT_LINEUPS));

        assertFailure(
                () -> boundedSession.execute(request(SofascoreEndpointType.EVENT_LINEUPS)),
                J5EventDataTransportFailure.UNEXPECTED_CONTENT);
        assertThat(bounded.requests).hasSize(3);
        boundedSession.close();
        boundedSession.close();
        assertThat(bounded.closeCount).isEqualTo(1);

        assertFailure(
                () -> boundedSession.execute(request(SofascoreEndpointType.EVENT_STATISTICS)),
                J5EventDataTransportFailure.OPERATOR_STOP);
        assertThat(bounded.requests).hasSize(3);
    }

    @ParameterizedTest
    @MethodSource("failureMappings")
    void translatesEveryWorkerFailureWithoutLeakingItsCause(
            PlaywrightProviderFailure workerFailure,
            J5EventDataTransportFailure expectedFailure) {
        RecordingCampaign campaign = new RecordingCampaign(
                new PlaywrightProviderException(workerFailure));
        var transport = new ProviderJ5EventDataPlaywrightTransport(
                new RecordingFactory(campaign));

        assertThatThrownBy(() -> {
            try (var session = transport.openCampaign(CAMPAIGN_ID)) {
                session.execute(request(SofascoreEndpointType.EVENT_STATISTICS));
            }
        })
                .isInstanceOf(J5EventDataTransportException.class)
                .hasMessage(expectedFailure.name())
                .hasMessageNotContaining("Playwright")
                .hasNoCause()
                .extracting("failure")
                .isEqualTo(expectedFailure);
        assertThat(campaign.closeCount).isEqualTo(1);
    }

    @Test
    void translatesOpenFailureAndRetriesCleanupWithoutReopeningExecution() {
        var openFailure = new ProviderJ5EventDataPlaywrightTransport(
                new RecordingFactory(new PlaywrightProviderException(
                        PlaywrightProviderFailure.AUTHENTICATION_FAILED)));

        assertFailure(
                () -> openFailure.openCampaign(CAMPAIGN_ID),
                J5EventDataTransportFailure.IO_FAILURE);

        RecordingCampaign campaign = successfulCampaign(3);
        campaign.closeFailure = new PlaywrightProviderException(
                PlaywrightProviderFailure.RUNTIME_FAILURE);
        var session = new ProviderJ5EventDataPlaywrightTransport(
                new RecordingFactory(campaign)).openCampaign(CAMPAIGN_ID);

        assertFailure(session::close, J5EventDataTransportFailure.IO_FAILURE);
        assertFailure(
                () -> session.execute(request(SofascoreEndpointType.EVENT_STATISTICS)),
                J5EventDataTransportFailure.OPERATOR_STOP);
        campaign.closeFailure = null;
        session.close();
        session.close();
        assertThat(campaign.closeCount).isEqualTo(2);
        assertThat(campaign.requests).isEmpty();
    }

    private static Stream<Arguments> failureMappings() {
        return Stream.of(PlaywrightProviderFailure.values())
                .map(failure -> Arguments.of(failure, expectedFailure(failure)));
    }

    private static J5EventDataTransportFailure expectedFailure(
            PlaywrightProviderFailure failure) {
        return switch (failure) {
            case TIMEOUT, IPC_TIMEOUT -> J5EventDataTransportFailure.TIMEOUT;
            case PAYLOAD_TOO_LARGE -> J5EventDataTransportFailure.PAYLOAD_TOO_LARGE;
            case SENSITIVE_CONTENT_REJECTED ->
                    J5EventDataTransportFailure.SENSITIVE_CONTENT_REJECTED;
            case UNEXPECTED_ROUTE, REDIRECT_BLOCKED, UNEXPECTED_CONTENT ->
                    J5EventDataTransportFailure.UNEXPECTED_CONTENT;
            case OPERATOR_STOP -> J5EventDataTransportFailure.OPERATOR_STOP;
            default -> J5EventDataTransportFailure.IO_FAILURE;
        };
    }

    private static void assertResponse(
            com.bettingproject.sofascorelocal.domain.provider.J5EventDataTransportResponse actual,
            SofascoreEndpointType endpoint,
            int status,
            String contentType,
            byte[] body) {
        assertThat(actual.endpointType()).isEqualTo(endpoint);
        assertThat(actual.requestKey()).isEqualTo(endpoint + "|eventId=" + EVENT_ID);
        assertThat(actual.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(actual.receivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(actual.httpStatus()).isEqualTo(status);
        assertThat(actual.contentType()).isEqualTo(contentType);
        assertThat(actual.latency()).isEqualTo(Duration.ofMillis(37));
        assertThat(actual.payload().bytes()).isEqualTo(body);
    }

    private static void assertFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            J5EventDataTransportFailure expectedFailure) {
        assertThatThrownBy(action)
                .isInstanceOf(J5EventDataTransportException.class)
                .hasMessage(expectedFailure.name())
                .hasNoCause()
                .extracting("failure")
                .isEqualTo(expectedFailure);
    }

    private static J5EventDataProviderRequest request(SofascoreEndpointType endpoint) {
        return request(endpoint, EVENT_ID);
    }

    private static J5EventDataProviderRequest request(
            SofascoreEndpointType endpoint,
            long eventId) {
        return new J5EventDataProviderRequest(
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                eventId,
                endpoint);
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

    private static RecordingCampaign successfulCampaign(int responseCount) {
        List<PlaywrightProviderResponse> responses = new ArrayList<>();
        for (int index = 0; index < responseCount; index++) {
            responses.add(response(
                    200,
                    "application/json",
                    ("{\"response\":" + index + "}").getBytes(StandardCharsets.UTF_8)));
        }
        return new RecordingCampaign(responses);
    }

    private static final class RecordingFactory implements PlaywrightProviderCampaignFactory {

        private final PlaywrightProviderCampaign campaign;
        private final PlaywrightProviderException failure;
        private UUID campaignId;
        private Set<SofascoreEndpointType> allowedEndpoints;
        private int openCount;

        private RecordingFactory(PlaywrightProviderCampaign campaign) {
            this.campaign = campaign;
            failure = null;
        }

        private RecordingFactory(PlaywrightProviderException failure) {
            campaign = null;
            this.failure = failure;
        }

        @Override
        public PlaywrightProviderCampaign open(
                UUID requestedCampaignId,
                Set<SofascoreEndpointType> requestedEndpoints) {
            throw new AssertionError("manual J5 must open its dedicated grouped authority");
        }

        @Override
        public PlaywrightProviderCampaign openManualJ5Grouped(
                UUID requestedCampaignId, Set<SofascoreEndpointType> requestedEndpoints) {
            openCount++;
            campaignId = requestedCampaignId;
            allowedEndpoints = Set.copyOf(requestedEndpoints);
            if (failure != null) {
                throw failure;
            }
            return campaign;
        }
    }

    private static final class RecordingCampaign implements PlaywrightProviderCampaign {

        private final List<PlaywrightProviderResponse> responses;
        private final PlaywrightProviderException executeFailure;
        private final List<PlaywrightProviderRequest> requests = new ArrayList<>();
        private final List<LiveProviderDispatchGroup> groups = new ArrayList<>();
        private PlaywrightProviderException closeFailure;
        private int responseIndex;
        private int closeCount;

        private RecordingCampaign(List<PlaywrightProviderResponse> responses) {
            this.responses = List.copyOf(responses);
            executeFailure = null;
        }

        private RecordingCampaign(PlaywrightProviderException executeFailure) {
            responses = List.of();
            this.executeFailure = executeFailure;
        }

        @Override
        public PlaywrightProviderResponse execute(PlaywrightProviderRequest request) {
            throw new AssertionError("manual J5 must dispatch a bounded group context");
        }

        @Override
        public PlaywrightProviderResponse executeGrouped(PlaywrightProviderRequest request,
                LiveProviderDispatchGroup group, PlaywrightDispatchAdmission admission) {
            admission.check();
            try (var ignored = admission.acquireDispatchPermit()) {
                groups.add(group);
            }
            requests.add(request);
            if (executeFailure != null) {
                throw executeFailure;
            }
            return responses.get(responseIndex++);
        }

        @Override
        public void close() {
            closeCount++;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
