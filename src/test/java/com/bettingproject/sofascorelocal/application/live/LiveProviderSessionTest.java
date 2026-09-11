package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderEntityTag;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightDispatchAdmission;
import com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LiveProviderSessionTest {
    @ParameterizedTest
    @ValueSource(strings = {"live-v1", "live-v2", "live-v3", "live-v4", "live-v5", "live-v6", "live-v7", "live-v8", "live-v9"})
    void opensOnlyTheExplicitPolicyFactory(String policy) {
        var factory = mock(PlaywrightProviderCampaignFactory.class);
        var campaign = mock(PlaywrightProviderCampaign.class);
        var id = UUID.randomUUID();
        switch (policy) {
            case "live-v9" -> when(factory.openLiveGroupedV9(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            case "live-v8" -> when(factory.openLiveGroupedV8(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            case "live-v7" -> when(factory.openLiveGroupedV7(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            case "live-v6" -> when(factory.openLiveGroupedV6(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            case "live-v5" -> when(factory.openLiveGroupedV5(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            case "live-v4" -> when(factory.openLiveGrouped(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            default -> when(factory.open(id, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
        }
        try (var ignored = new LiveProviderSession(factory, id, policy)) {
            switch (policy) {
                case "live-v9" -> verify(factory).openLiveGroupedV9(id, LiveProviderSession.ENDPOINTS);
                case "live-v8" -> verify(factory).openLiveGroupedV8(id, LiveProviderSession.ENDPOINTS);
                case "live-v7" -> verify(factory).openLiveGroupedV7(id, LiveProviderSession.ENDPOINTS);
                case "live-v6" -> verify(factory).openLiveGroupedV6(id, LiveProviderSession.ENDPOINTS);
                case "live-v5" -> verify(factory).openLiveGroupedV5(id, LiveProviderSession.ENDPOINTS);
                case "live-v4" -> verify(factory).openLiveGrouped(id, LiveProviderSession.ENDPOINTS);
                default -> verify(factory).open(id, LiveProviderSession.ENDPOINTS);
            }
            verifyNoMoreInteractions(factory);
        }
        verify(campaign).close();
    }

    @Test
    void v9SendsAValidatorOnlyAfterTheExactParsedResultWasCommittedAndReusesItsFactsOn304() {
        var factory = mock(PlaywrightProviderCampaignFactory.class);
        var campaign = mock(PlaywrightProviderCampaign.class);
        var campaignId = UUID.randomUUID();
        var original = PlaywrightProviderEntityTag.of("\"initial-v9\"");
        var refreshed = PlaywrightProviderEntityTag.of("\"refreshed-v9\"");
        var dispatched = new ArrayList<PlaywrightProviderRequest>();
        when(factory.openLiveGroupedV9(campaignId, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
        when(campaign.executeGrouped(any(), any(), any())).thenAnswer(invocation -> {
            dispatched.add(invocation.getArgument(0));
            return switch (dispatched.size()) {
                case 1 -> response(200, Optional.of(original), "{\"event\":{}}");
                case 3 -> response(304, Optional.of(refreshed), "");
                default -> response(304, Optional.empty(), "");
            };
        });
        var facts = new LiveProviderSession.ScheduleFacts("suspended", Map.of("finish", false),
                Instant.parse("2026-09-11T10:00:00Z"), null, null);

        try (var session = new LiveProviderSession(factory, campaignId, "live-v9")) {
            var first = execute(session, campaignId);
            assertThat(dispatched.getFirst().ifNoneMatch()).isEmpty();

            // The transport response alone has no authority to seed a later conditional request.
            var beforeCommit = execute(session, campaignId);
            assertThat(dispatched.get(1).ifNoneMatch()).isEmpty();
            assertThat(session.revalidated(17_000_001L, SofascoreEndpointType.EVENT_DETAILS, beforeCommit)).isEmpty();

            session.retainParsed(17_000_001L, SofascoreEndpointType.EVENT_DETAILS, first, facts);
            var correlated304 = execute(session, campaignId);
            assertThat(dispatched.get(2).ifNoneMatch()).contains(original);
            var exchange = session.revalidated(17_000_001L, SofascoreEndpointType.EVENT_DETAILS, correlated304)
                    .orElseThrow();
            assertThat(exchange.facts()).isEqualTo(facts);
            session.acceptNotModified(exchange);

            execute(session, campaignId);
            assertThat(dispatched.get(3).ifNoneMatch()).contains(refreshed);
        }
    }

    @Test
    void legacyLivePoliciesNeverRetainOrSendAConditionalValidator() {
        var factory = mock(PlaywrightProviderCampaignFactory.class);
        var campaign = mock(PlaywrightProviderCampaign.class);
        var campaignId = UUID.randomUUID();
        var entityTag = PlaywrightProviderEntityTag.of("\"v8-must-stay-unconditional\"");
        var dispatched = new ArrayList<PlaywrightProviderRequest>();
        when(factory.openLiveGroupedV8(campaignId, LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
        when(campaign.executeGrouped(any(), any(), any())).thenAnswer(invocation -> {
            dispatched.add(invocation.getArgument(0));
            return response(200, Optional.of(entityTag), "{\"event\":{}}");
        });

        try (var session = new LiveProviderSession(factory, campaignId, "live-v8")) {
            var first = execute(session, campaignId);
            session.retainParsed(17_000_001L, SofascoreEndpointType.EVENT_DETAILS, first,
                    new LiveProviderSession.ScheduleFacts("inprogress", Map.of(), null, null, null));
            execute(session, campaignId);
            assertThat(dispatched).extracting(PlaywrightProviderRequest::ifNoneMatch)
                    .allSatisfy(validator -> assertThat(validator).isEmpty());
        }
    }

    private static PlaywrightProviderResponse execute(LiveProviderSession session, UUID campaignId) {
        return session.executeGrouped(17_000_001L, SofascoreEndpointType.EVENT_DETAILS,
                new LiveProviderDispatchGroup(campaignId, UUID.randomUUID(), 17_000_001L,
                        LiveProviderDispatchGroup.Phase.CHECK), PlaywrightDispatchAdmission.UNRESTRICTED);
    }

    private static PlaywrightProviderResponse response(int status,
                                                        Optional<PlaywrightProviderEntityTag> entityTag,
                                                        String body) {
        Instant at = Instant.parse("2026-09-11T10:00:00Z");
        return new PlaywrightProviderResponse(at, at, status,
                status == 304 ? "" : "application/json", Duration.ZERO,
                RawPayloadEvidence.capture(body.getBytes(StandardCharsets.UTF_8)), entityTag, null);
    }
}
