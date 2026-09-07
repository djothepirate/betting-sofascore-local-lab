package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.config.LiveCampaignWebMvcConfiguration;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LiveCampaignController.class)
@Import({LiveCampaignPresentation.class, LocalFormTokenService.class, LiveCampaignWebMvcConfiguration.class})
class LiveCampaignControllerTest {
    private static final String HOST = "localhost:8087";
    private static final String ORIGIN = "http://" + HOST;
    private static final UUID CAMPAIGN_ID = UUID.fromString("00000000-0000-0000-0000-000000000058");
    private static final UUID EVENT_ID = CanonicalEventIdentity.sofascore(900001L).value();
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final String HASH = "a".repeat(64);
    @Autowired private MockMvc mvc;
    @Autowired private LocalFormTokenService tokens;
    @MockitoBean private LiveCampaignService service;
    @MockitoBean private CanonicalEventStore events;
    @MockitoBean private J5EventDataStore data;
    @MockitoBean private CacheManager cacheManager;

    @BeforeEach
    void localObservationsOnly() {
        clearInvocations(service);
        when(events.findByObservationId(any(), anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void preparationPreservesExactSelectionAndNeverLaunches() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(service.prepareSelection(List.of(EVENT_ID))).thenReturn(new LiveCampaignService.Preparation(manifest(), List.of()));
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("eventId", EVENT_ID.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/live-campaigns/" + CAMPAIGN_ID));
        verify(service).prepareSelection(List.of(EVENT_ID));
        verifyNoMoreInteractions(service);
    }

    @Test
    void finishedOnlySelectionExplainsWhyNoCampaignIsCreated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var excluded = List.of(finishedEvent());
        when(service.prepareSelection(List.of(EVENT_ID)))
                .thenReturn(new LiveCampaignService.Preparation(null, excluded));
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("eventId", EVENT_ID.toString()))
                .andExpect(status().isOk()).andExpect(view().name("live-campaign-ineligible"))
                .andExpect(model().attribute("excludedFinished", excluded))
                .andExpect(content().string(containsString("Aucune campagne live n’a été créée")))
                .andExpect(content().string(containsString("&lt;Home&gt; — Away · finished")))
                .andExpect(content().string(not(containsString("<Home>"))))
                .andExpect(content().string(not(containsString("name=\"confirmation\""))));
        verify(service).prepareSelection(List.of(EVENT_ID));
        verifyNoMoreInteractions(service);
    }

    @Test
    void mixedSelectionExplainsExcludedEventsBeforeLaunchConfirmation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        UUID eligible = CanonicalEventIdentity.sofascore(900002L).value();
        var excluded = List.of(finishedEvent());
        when(service.prepareSelection(List.of(EVENT_ID, eligible)))
                .thenReturn(new LiveCampaignService.Preparation(manifest(), excluded));
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("eventId", EVENT_ID.toString(), eligible.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/live-campaigns/" + CAMPAIGN_ID))
                .andExpect(flash().attribute("excludedFinished", excluded));
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("PREPARED", 1));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST)
                        .flashAttr("excludedFinished", excluded))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Elles ont été exclues du manifeste")))
                .andExpect(content().string(containsString("&lt;Home&gt; — Away")));
        verify(service, never()).launch(any(), any());
    }

    @Test
    void skippedEventLabelsItsHistoricalSportStatusWithoutInventingAnObservation() throws Exception {
        var base = campaign("RUNNING", 2);
        when(service.state(CAMPAIGN_ID)).thenReturn(new CampaignView(base.manifest(), "RUNNING", null,
                NOW, NOW.plusSeconds(14400), 0, 0, 2, null,
                List.of(new EventView(base.manifest().targets().getFirst(), "STOPPED_ALREADY_FINISHED",
                        "STOPPED_ALREADY_FINISHED", 0, 0, null, List.of())), List.of(), List.of()));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("décrit l’observation figée à la préparation")))
                .andExpect(content().string(containsString("aucun appel fournisseur.")))
                .andExpect(content().string(not(containsString("Arrêter cette rencontre"))));
    }

    @Test
    void eventFinishedAfterPreparationHasAnActionableLaunchRefusal() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(service.launch(CAMPAIGN_ID, HASH)).thenThrow(new IllegalArgumentException("LIVE_ALL_EVENTS_FINISHED"));
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/launch").header("Host", HOST)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("manifestHash", HASH).param("confirmation", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("déjà terminées (finished)")))
                .andExpect(content().string(containsString("Aucun lancement live ni appel fournisseur")));
    }

    private static CanonicalEventObservationView finishedEvent() {
        return new CanonicalEventObservationView(17, CanonicalEventIdentity.sofascore(900001L), NOW,
                new ScheduledTeam(1, "<Home>"), new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus("finished", Optional.empty()), Optional.empty(),
                EventSourceTrace.providerSnapshot(23, HASH, "event-details-v2", NOW), HASH, 1);
    }

    @Test
    void preparationPageRendersExactManifestAndExplicitLaunchConfirmation() throws Exception {
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("PREPARED", 7));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk()).andExpect(view().name("live-campaign"))
                .andExpect(content().string(containsString(HASH)))
                .andExpect(content().string(containsString("name=\"confirmation\"")))
                .andExpect(content().string(containsString("240 minutes")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
        verify(service).state(CAMPAIGN_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void runningPageRendersFamilyEvidenceAndEscapesNormalizedText() throws Exception {
        UUID attemptId = UUID.randomUUID();
        var refs = new NormalizedReferences(null, null, 11L, HASH);
        var result = new Result(attemptId, new Publication("PARSED", "EVENT", "OK", NOW,
                "statistics-v2", true, "COLLECTING", null, null, null, "COMPLETE", 100), refs);
        var family = new FamilyCursor(SofascoreEndpointType.EVENT_STATISTICS, attemptId, attemptId,
                attemptId, attemptId, NOW, NOW, NOW, refs, result, result);
        var attempt = new AttemptView(new ReservedAttempt(attemptId, EVENT_ID, 900001L,
                SofascoreEndpointType.EVENT_STATISTICS, 1, "NORMAL", NOW, NOW, false),
                NOW, 3L, 5L, NOW, result);
        when(service.state(CAMPAIGN_ID)).thenReturn(new CampaignView(manifest(), "RUNNING", null,
                NOW, NOW.plusSeconds(14400), 1, 100, 8, null,
                List.of(new EventView(manifest().targets().getFirst(), "COLLECTING", null,
                        1, 100, NOW.plusSeconds(60), List.of(family))), List.of(attempt), List.of()));
        when(data.findByObservationId(EVENT_ID, SofascoreEndpointType.EVENT_STATISTICS, 11L))
                .thenReturn(Optional.of(new J5EventDataObservationView(11L,
                        CanonicalEventIdentity.sofascore(900001L),
                        new EventStatistics(900001L, List.of(new EventStatisticMetric("ALL", "Match", "shots",
                                "<img src=x>", Optional.of("3"), Optional.of("4")))),
                        EventSourceTrace.providerSnapshot(3, HASH, "statistics-v2", NOW),
                        J5CompletenessReport.measured(1, 1, List.of()), HASH)));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-live-family=\"EVENT_STATISTICS\"")))
                .andExpect(content().string(containsString("&lt;img src=x&gt;")))
                .andExpect(content().string(not(containsString("<img src=x>"))))
                .andExpect(content().string(containsString("Arrêter cette rencontre")))
                .andExpect(content().string(containsString("data-live-occurrence")));
    }

    @Test
    void aLaunchTokenIsOneShotAndRequiresConfirmation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = tokens.issue(session);
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/launch").header("Host", HOST)
                        .header("Origin", ORIGIN).session(session).param("localFormToken", token)
                        .param("manifestHash", HASH).param("confirmation", "true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/launch").header("Host", HOST)
                        .header("Origin", ORIGIN).session(session).param("localFormToken", token)
                        .param("manifestHash", HASH).param("confirmation", "true"))
                .andExpect(status().isBadRequest());
        verify(service, times(1)).launch(CAMPAIGN_ID, HASH);
        verifyNoMoreInteractions(service);
    }

    @Test
    void missingConfirmationDoesNotLaunch() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/launch").header("Host", HOST)
                        .session(session).param("localFormToken", tokens.issue(session)).param("manifestHash", HASH))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void individualAndGlobalStopsRemainDistinctAndTokenProtected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/stop").header("Host", HOST)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("eventId", EVENT_ID.toString())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/stop").header("Host", HOST)
                        .session(session).param("localFormToken", tokens.issue(session)))
                .andExpect(status().is3xxRedirection());
        verify(service).stop(CAMPAIGN_ID, EVENT_ID);
        verify(service).stop(CAMPAIGN_ID, null);
    }

    @Test
    void explicitEventStopRouteConsumesItsTokenAndPreservesTheTarget() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = tokens.issue(session);
        String path = "/live-campaigns/" + CAMPAIGN_ID + "/events/" + EVENT_ID + "/stop";
        mvc.perform(post(path).header("Host", HOST).session(session).param("localFormToken", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/live-campaigns/" + CAMPAIGN_ID));
        mvc.perform(post(path).header("Host", HOST).session(session).param("localFormToken", token))
                .andExpect(status().isBadRequest());
        verify(service).stop(CAMPAIGN_ID, EVENT_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void stateReadsExposeMonotoneRevisionWithoutRuntimeOwnershipOrTransport() throws Exception {
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("RUNNING", 42));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID + "/state").header("Host", HOST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(42))
                .andExpect(jsonPath("$.events[0].canonicalEventId").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.ownership").doesNotExist())
                .andExpect(jsonPath("$.attempts").doesNotExist())
                .andExpect(jsonPath("$.manifest").doesNotExist())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        verify(service).state(CAMPAIGN_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void eventRoutesReadOnlyTheRequestedIdentities() throws Exception {
        when(service.eventStates(List.of(EVENT_ID))).thenReturn(List.of(campaign("RUNNING", 43)));
        mvc.perform(get("/events/state").header("Host", HOST).param("eventId", EVENT_ID.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].revision").value(43));
        mvc.perform(get("/events/" + EVENT_ID + "/state").header("Host", "127.0.0.1:8087"))
                .andExpect(status().isOk());
        verify(service, times(2)).eventStates(List.of(EVENT_ID));
        verifyNoMoreInteractions(service);
    }

    @Test
    void unknownCampaignIsAnExplicitLocalNotFound() throws Exception {
        when(service.state(CAMPAIGN_ID)).thenThrow(new NoSuchElementException("LIVE_CAMPAIGN_NOT_FOUND"));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID + "/state").header("Host", HOST))
                .andExpect(status().isNotFound());
        verify(service).state(CAMPAIGN_ID);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://attacker.example", "null", "http://127.0.0.1:8087"})
    void foreignOrMismatchedOriginsCannotPrepareOrObserve(String origin) throws Exception {
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", origin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/events/state").header("Host", HOST).header("Origin", origin))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"iframe", "frame", "object", "embed"})
    void embeddedLiveDocumentsAreRejectedBeforeReadingLocalData(String destination) throws Exception {
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST)
                        .header("Sec-Fetch-Dest", destination))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Frame-Options", "DENY"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsDnsRebindingForwardingAndDuplicateHeaders() throws Exception {
        mvc.perform(get("/events/state").header("Host", "attacker.example:8087"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/events/state").header("Host", HOST, HOST))
                .andExpect(status().isForbidden());
        mvc.perform(get("/events/state").header("Host", HOST).header("Forwarded", "host=" + HOST))
                .andExpect(status().isForbidden());
        mvc.perform(get("/events/state").header("Host", HOST).header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-Forwarded-Host", "X-Forwarded-Proto", "X-Forwarded-Port",
            "X-Forwarded-For", "X-Forwarded-Prefix"})
    void forwardedBrowserBoundariesCannotReachLiveReads(String headerName) throws Exception {
        mvc.perform(get("/events/state").header("Host", HOST).header(headerName, "untrusted"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void missingDockerConfigurationExplainsPreparationRefusalWithoutSuggestingProviderOptIn() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(service.prepareSelection(List.of(EVENT_ID)))
                .thenThrow(new IllegalStateException("LIVE_STORAGE_PROBE_NOT_CONFIGURED"));
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).session(session)
                        .param("localFormToken", tokens.issue(session)).param("eventId", EVENT_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(model().attribute("liveErrorCode", "LIVE_STORAGE_PROBE_NOT_CONFIGURED"))
                .andExpect(content().string(containsString("SOFASCORE_LIVE_DOCKER_EXECUTABLE")))
                .andExpect(content().string(containsString("SOFASCORE_LIVE_POSTGRES_CONTAINER")))
                .andExpect(content().string(containsString("Aucun appel fournisseur")))
                .andExpect(content().string(not(containsString("l’opt-in"))));
        verify(service).prepareSelection(List.of(EVENT_ID));
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"LIVE_STORAGE_PROBE_TIMEOUT,délai prévu", "LIVE_STORAGE_PROBE_FAILED,ne peut pas être mesuré",
            "LIVE_STORAGE_PROBE_INVALID,ne peut pas être mesuré", "LIVE_STORAGE_PROBE_INTERRUPTED,interrompu",
            "LIVE_STORAGE_CAPACITY_REFUSED,insuffisant", "LIVE_POLICY_INVALID,limites",
            "LIVE_CAPACITY_QUALIFICATION_REQUIRED,preuve de qualification"})
    void localPreparationFailuresExposeOnlyTheirKnownCodeAndAction(String code, String action) throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(service.prepareSelection(List.of(EVENT_ID))).thenThrow(new IllegalStateException(code));
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).session(session)
                        .param("localFormToken", tokens.issue(session)).param("eventId", EVENT_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(model().attribute("liveErrorCode", code))
                .andExpect(content().string(containsString(code)))
                .andExpect(content().string(containsString(action)));
        verify(service, never()).launch(any(), any());
    }

    @Test
    void capacityRefusalSuggestsReducedSelectionAndNeverEchoesArbitraryExceptionText() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(service.prepareSelection(List.of(EVENT_ID))).thenThrow(
                new IllegalArgumentException("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY"));
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).session(session)
                        .param("localFormToken", tokens.issue(session)).param("eventId", EVENT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Réduire la sélection")));
        doThrow(new IllegalStateException("SECRET_PRIVATE_RUNTIME")).when(service).prepareSelection(List.of(EVENT_ID));
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).session(session)
                        .param("localFormToken", tokens.issue(session)).param("eventId", EVENT_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(model().attribute("liveErrorCode", "LIVE_REQUEST_REJECTED"))
                .andExpect(content().string(not(containsString("SECRET_PRIVATE_RUNTIME"))));
    }

    private static Manifest manifest() {
        return new Manifest(CAMPAIGN_ID, HASH, "live-v1", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, 1_000_000, 1, List.of(new Target(EVENT_ID, 900001L, 1, 1)));
    }

    private static CampaignView campaign(String state, long revision) {
        return new CampaignView(manifest(), state, null, state.equals("PREPARED") ? null : NOW,
                state.equals("PREPARED") ? null : NOW.plusSeconds(14400), 0, 0, revision, null,
                List.of(new EventView(manifest().targets().getFirst(), "WAITING_START", null, 0, 0,
                        NOW, List.of())), List.of(), List.of());
    }
}
