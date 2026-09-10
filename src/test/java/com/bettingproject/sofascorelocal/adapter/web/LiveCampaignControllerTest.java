package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignDiagnostic;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightTransportDiagnostic;
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
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.LiveDiagnosticStore;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import com.bettingproject.sofascorelocal.security.InvalidLocalFormTokenException;
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
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
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
    @MockitoBean private LiveDiagnosticStore diagnostics;
    @MockitoBean private EventDetailsStore details;
    @MockitoBean private LineupCountryOverlayResolver lineupCountries;
    @MockitoBean private CacheManager cacheManager;

    @BeforeEach
    void localObservationsOnly() {
        clearInvocations(service);
        when(events.findByObservationId(any(), anyLong())).thenReturn(Optional.empty());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void deferredTimeoutProofIsVisibleButNoFutureRetryIsPromisedAfterOperatorStop(boolean stopped) throws Exception {
        UUID attemptId=UUID.randomUUID();
        var publication=new Publication("FAILED","NONE","PLAYWRIGHT_TIMEOUT_RETRY_DEFERRED",NOW.plusSeconds(41),null,false,null);
        var result=new Result(attemptId,publication,NormalizedReferences.none());
        var cursor=new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS,attemptId,null,null,null,null,null,null,
                NormalizedReferences.none(),result,null,
                new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,NOW.plusSeconds(340),300,0));
        var attempt=new AttemptView(new ReservedAttempt(attemptId,EVENT_ID,900001L,SofascoreEndpointType.EVENT_DETAILS,1,
                "NORMAL",NOW.plusSeconds(10),NOW.plusSeconds(10),false),NOW.plusSeconds(10),null,null,null,result);
        var proof=new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.READING_BODY,30000,
                NOW.plusSeconds(10),NOW.plusSeconds(12),200,null,false)
                .withExchangeEnd(NOW.plusSeconds(40),PlaywrightTransportDiagnostic.ExchangeEndReason.ABORTED,true);
        var view=new CampaignView(manifest(),stopped?"STOPPED_OPERATOR":"RUNNING",null,NOW,NOW.plusSeconds(14400),1,0,44,null,
                List.of(new EventView(manifest().targets().getFirst(),stopped?"STOPPED_OPERATOR":"COLLECTING",null,1,0,
                        NOW.plusSeconds(340),List.of(cursor))),List.of(attempt),List.of());
        when(service.state(CAMPAIGN_ID)).thenReturn(view);
        when(diagnostics.findTransport(CAMPAIGN_ID,attemptId)).thenReturn(Optional.of(proof));
        String path="/live-campaigns/"+CAMPAIGN_ID;
        String body=mvc.perform(get(path).header("Host",HOST)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var notice=Pattern.compile("<p\\b([^>]*data-live-timeout-retry[^>]*)>").matcher(body);
        assertThat(notice.find()).isTrue();assertThat(notice.group(1).contains("hidden")).isEqualTo(stopped);
        assertThat(body).contains("Fin du transport prouvée", "ABORTED", "Réutilisation du contexte vérifiée",NOW.plusSeconds(40).toString());
        var state=mvc.perform(get(path+"/state").header("Host",HOST)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(stopped?"STOPPED_OPERATOR":"RUNNING"))
                .andExpect(jsonPath("$.runtimeStatus").doesNotExist())
                .andExpect(jsonPath("$.events[0].families[0].transport.contextReusable").value(true))
                .andExpect(jsonPath("$.events[0].families[0].transport.responseComplete").value(false))
                .andExpect(jsonPath("$.events[0].families[0].lastReceivedAt").doesNotExist())
                .andExpect(jsonPath("$.events[0].families[0].lastSuccessfulAt").doesNotExist())
                .andExpect(jsonPath("$.events[0].families[0].receivedSnapshotId").doesNotExist());
        if(stopped) state.andExpect(jsonPath("$.events[0].families[0].schedule.nextDueAt").doesNotExist());
        else state.andExpect(jsonPath("$.events[0].families[0].schedule.nextDueAt").value(NOW.plusSeconds(340).toString()));
    }

    @Test
    void orphanCleanupFormClosesOnlyTheExpectedGenerationAndRevealsNewPreparationAfterRedirect() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Guard guard = new Guard("CLEANUP_REQUIRED", CAMPAIGN_ID,
                new Owner(UUID.randomUUID(), 654321, NOW), 7, NOW);
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("INTERRUPTED", 43));
        when(service.orphanCleanupGuard(CAMPAIGN_ID)).thenReturn(Optional.of(guard), Optional.empty());
        String path = "/live-campaigns/" + CAMPAIGN_ID + "/finalize-interruption";
        String page = mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST).session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("orphanCleanup", guard))
                .andExpect(model().attribute("canPrepareAgain", false))
                .andExpect(content().string(containsString("Clôturer la session interrompue")))
                .andExpect(content().string(containsString("son état INTERRUPTED sont conservés")))
                .andExpect(content().string(containsString("Aucune collecte n’est lancée")))
                .andExpect(content().string(not(containsString("Préparer une nouvelle campagne avec ces rencontres"))))
                .andExpect(content().string(not(containsString("654321"))))
                .andExpect(content().string(not(containsString(guard.owner().instanceId().toString()))))
                .andReturn().getResponse().getContentAsString();
        String form = renderedForm(page, path);
        String token = hiddenValue(form, "localFormToken");
        assertThat(hiddenValue(form, "guardGeneration")).isEqualTo("7");
        var result = mvc.perform(post(path).header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", token).param("guardGeneration", hiddenValue(form, "guardGeneration")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/live-campaigns/" + CAMPAIGN_ID))
                .andExpect(flash().attribute("liveSuccess", containsString("aucune collecte n’a été lancée")))
                .andReturn();
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST).session(session)
                        .flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canPrepareAgain", true))
                .andExpect(content().string(containsString("La session interrompue est clôturée")))
                .andExpect(content().string(containsString(">INTERRUPTED</span>")))
                .andExpect(content().string(not(containsString("/finalize-interruption"))))
                .andExpect(content().string(containsString("Préparer une nouvelle campagne avec ces rencontres")));
        mvc.perform(post(path).header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", token).param("guardGeneration", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(resultValue -> assertThat(resultValue.getResolvedException()).isInstanceOf(InvalidLocalFormTokenException.class));
        verify(service, times(2)).state(CAMPAIGN_ID);
        verify(service, times(2)).runtimeStatus(CAMPAIGN_ID);
        verify(service, times(2)).orphanCleanupGuard(CAMPAIGN_ID);
        verify(service).finalizeInterruptedCleanup(CAMPAIGN_ID, 7);
        verifyNoMoreInteractions(service);
    }

    @Test
    void orphanCleanupRequiresPostSameOriginAndAnUnusedLocalToken() throws Exception {
        String path = "/live-campaigns/" + CAMPAIGN_ID + "/finalize-interruption";
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(path).header("Host", HOST)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(path).header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("guardGeneration", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(InvalidLocalFormTokenException.class));
        mvc.perform(post(path).header("Host", HOST).header("Origin", "https://foreign.invalid")
                        .session(session).param("guardGeneration", "7").param("localFormToken", tokens.issue(session)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("guardGeneration", "7").param("localFormToken", "invalid-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "0", "-1", "9223372036854775808"})
    void orphanCleanupRejectsMissingOrInvalidGenerationBeforeServiceMutation(String generation) throws Exception {
        MockHttpSession session = new MockHttpSession();
        var request = post("/live-campaigns/" + CAMPAIGN_ID + "/finalize-interruption")
                .header("Host", HOST).header("Origin", ORIGIN).session(session)
                .param("localFormToken", tokens.issue(session));
        if (!generation.isEmpty()) request.param("guardGeneration", generation);
        mvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void orphanCleanupRejectsMalformedIdAndPreservesTheRequestedCampaignIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/live-campaigns/not-a-uuid/finalize-interruption")
                        .header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", tokens.issue(session)).param("guardGeneration", "7"))
                .andExpect(status().isBadRequest());
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000999");
        doThrow(new NoSuchElementException("LIVE_CAMPAIGN_NOT_FOUND"))
                .when(service).finalizeInterruptedCleanup(missing, 7);
        mvc.perform(post("/live-campaigns/" + missing + "/finalize-interruption")
                        .header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", tokens.issue(session)).param("guardGeneration", "7"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Cette campagne n’existe pas")));
        verify(service).finalizeInterruptedCleanup(missing, 7);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"LIVE_CLEANUP_OWNER_ACTIVE,application active",
            "LIVE_CLEANUP_PROCESS_UNVERIFIED,n’a pas pu être vérifié",
            "LIVE_CLEANUP_PROCESS_ACTIVE,encore actif",
            "LIVE_CLEANUP_STATE_CHANGED,Actualiser la page",
            "LIVE_CLEANUP_BUSY,clôture de session est déjà en cours",
            "LIVE_ORPHAN_CLEANUP_GUARD_CHANGED,Actualiser la page",
            "LIVE_ORPHAN_CLEANUP_INCOMPLETE,session reste verrouillée"})
    void orphanCleanupRefusalsHaveActionableMessagesAndConsumeTheToken(String code, String message) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = tokens.issue(session);
        String path = "/live-campaigns/" + CAMPAIGN_ID + "/finalize-interruption";
        doThrow(new IllegalStateException(code)).when(service).finalizeInterruptedCleanup(CAMPAIGN_ID, 7);
        mvc.perform(post(path).header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", token).param("guardGeneration", "7"))
                .andExpect(status().isConflict()).andExpect(model().attribute("liveErrorCode", code))
                .andExpect(model().attribute("retryCampaignId", CAMPAIGN_ID))
                .andExpect(content().string(containsString(message)))
                .andExpect(content().string(containsString("href=\"/live-campaigns/" + CAMPAIGN_ID + "\">Revenir à la campagne</a>")))
                .andExpect(content().string(not(containsString("Ouvrir la campagne à clôturer"))));
        mvc.perform(post(path).header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", token).param("guardGeneration", "7"))
                .andExpect(status().isBadRequest());
        verify(service).finalizeInterruptedCleanup(CAMPAIGN_ID, 7);
        verifyNoMoreInteractions(service);
    }

    @Test
    void invalidOrphanGuardUsesItsActualArgumentExceptionTypeAndLinksBackToAReadableCampaign() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = tokens.issue(session);
        String path = "/live-campaigns/" + CAMPAIGN_ID + "/finalize-interruption";
        // JdbcLiveCampaignStore.completeOrphanCleanup raises IllegalArgumentException for this code.
        doThrow(new IllegalArgumentException("LIVE_ORPHAN_CLEANUP_INVALID_GUARD"))
                .when(service).finalizeInterruptedCleanup(CAMPAIGN_ID, 7);
        mvc.perform(post(path).header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", token).param("guardGeneration", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException()).isExactlyInstanceOf(IllegalArgumentException.class))
                .andExpect(model().attribute("liveErrorCode", "LIVE_ORPHAN_CLEANUP_INVALID_GUARD"))
                .andExpect(model().attribute("retryCampaignId", CAMPAIGN_ID))
                .andExpect(content().string(containsString("Actualiser la page de la campagne")))
                .andExpect(content().string(containsString("href=\"/live-campaigns/" + CAMPAIGN_ID + "\">Revenir à la campagne</a>")))
                .andExpect(content().string(not(containsString("La sélection ou le manifeste est invalide"))));
        mvc.perform(post(path).header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", token).param("guardGeneration", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(InvalidLocalFormTokenException.class));
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("INTERRUPTED", 43));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST).session(session))
                .andExpect(status().isOk()).andExpect(view().name("live-campaign"));
        verify(service).finalizeInterruptedCleanup(CAMPAIGN_ID, 7);
        verify(service).state(CAMPAIGN_ID);
        verify(service).runtimeStatus(CAMPAIGN_ID);
        verify(service).orphanCleanupGuard(CAMPAIGN_ID);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INTERRUPTED", "STOPPED_ERROR", "STOPPED_OPERATOR", "STOPPED_LIMIT", "STOPPED_CAPACITY", "COMPLETED"})
    void terminalCampaignWithoutOrphanGuardOffersPreparationOnly(String state) throws Exception {
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign(state, 43));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk()).andExpect(model().attribute("canPrepareAgain", true))
                .andExpect(content().string(containsString("Préparer une nouvelle campagne avec ces rencontres")))
                .andExpect(content().string(containsString("Les rencontres terminées ou reportées seront exclues")))
                .andExpect(content().string(not(containsString("/finalize-interruption"))))
                .andExpect(content().string(not(containsString("name=\"confirmation\""))))
                .andExpect(content().string(not(containsString("Lancer la campagne live"))));
        verify(service).state(CAMPAIGN_ID);
        verify(service).runtimeStatus(CAMPAIGN_ID);
        verify(service).orphanCleanupGuard(CAMPAIGN_ID);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PREPARED", "RUNNING"})
    void nonTerminalCampaignDoesNotOfferOrphanCleanupOrDuplicatePreparation(String state) throws Exception {
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign(state, 43));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk()).andExpect(model().attribute("canPrepareAgain", false))
                .andExpect(content().string(not(containsString("/finalize-interruption"))))
                .andExpect(content().string(not(containsString("Préparer une nouvelle campagne avec ces rencontres"))));
        verify(service).state(CAMPAIGN_ID);
        verify(service).runtimeStatus(CAMPAIGN_ID);
        verify(service).orphanCleanupGuard(CAMPAIGN_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void prepareAgainFormUsesEveryManifestTargetAndOnlyRequestsNewAdmission() throws Exception {
        UUID secondId = CanonicalEventIdentity.sofascore(900002L).value();
        List<UUID> ids = List.of(EVENT_ID, secondId);
        List<Target> targets = List.of(manifest().targets().getFirst(), new Target(secondId, 900002L, 2, 2));
        Manifest original = new Manifest(CAMPAIGN_ID, HASH, "live-v1", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, 1_000_000, 2, targets);
        // The preserved manifest remains authoritative even if the rendered event list is incomplete.
        when(service.state(CAMPAIGN_ID)).thenReturn(new CampaignView(original, "INTERRUPTED", "OWNER_PROCESS_ABSENT",
                NOW, NOW.plusSeconds(14400), 42, 1000, 43, null, List.of(), List.of(), List.of()));
        UUID nextId = UUID.fromString("00000000-0000-0000-0000-000000000059");
        Manifest next = new Manifest(nextId, HASH, "live-v1", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, 1_000_000, 2, targets);
        when(service.prepareSelection(ids)).thenReturn(new LiveCampaignService.Preparation(next, List.of()));
        MockHttpSession session = new MockHttpSession();
        String page = mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String form = renderedForm(page, "/live-campaigns/prepare");
        var values = Pattern.compile("name=\"eventId\"[^>]*value=\"([^\"]+)\"").matcher(form)
                .results().map(match -> match.group(1)).toList();
        assertThat(values).containsExactlyElementsOf(ids.stream().map(UUID::toString).toList());
        assertThat(form).doesNotContain("confirmation", "manifestHash", "/launch");
        clearInvocations(service);
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", hiddenValue(form, "localFormToken"))
                        .param("eventId", values.toArray(String[]::new)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/live-campaigns/" + nextId));
        verify(service).prepareSelection(ids);
        verifyNoMoreInteractions(service);
    }

    @Test
    void providerCleanupRefusalLinksToTheOtherCampaignThatActuallyOwnsTheGuard() throws Exception {
        UUID previous = UUID.fromString("00000000-0000-0000-0000-000000000057");
        when(service.launch(CAMPAIGN_ID, HASH)).thenThrow(new IllegalStateException("LIVE_PROVIDER_CLEANUP_REQUIRED"));
        when(service.providerCleanupCampaignId()).thenReturn(Optional.of(previous));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/launch").header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("manifestHash", HASH).param("confirmation", "true"))
                .andExpect(status().isConflict()).andExpect(model().attribute("cleanupCampaignId", previous))
                .andExpect(content().string(containsString("href=\"/live-campaigns/" + previous + "\"")))
                .andExpect(content().string(containsString("Ouvrir la campagne à clôturer")))
                .andExpect(content().string(containsString("Clôturer la session interrompue")))
                .andExpect(content().string(not(containsString("/finalize-interruption"))));
        verify(service).launch(CAMPAIGN_ID, HASH);
        verify(service).providerCleanupCampaignId();
        verifyNoMoreInteractions(service);
    }

    private static String renderedForm(String page, String action) {
        var match = Pattern.compile("(?s)<form\\b[^>]*action=\"" + Pattern.quote(action) + "\"[^>]*>.*?</form>").matcher(page);
        assertThat(match.find()).as("POST form for %s", action).isTrue();
        assertThat(match.group()).contains("method=\"post\"");
        return match.group();
    }

    private static String hiddenValue(String form, String name) {
        var match = Pattern.compile("<input\\b[^>]*name=\"" + Pattern.quote(name) + "\"[^>]*value=\"([^\"]+)\"").matcher(form);
        assertThat(match.find()).as("Hidden input %s", name).isTrue();
        return match.group(1);
    }

    @Test
    void preparedPageOffersCancellationAndItsControlledPostOnlyCancelsThePreparation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("PREPARED", 1));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Annuler la préparation")))
                .andExpect(content().string(containsString("/live-campaigns/" + CAMPAIGN_ID + "/cancel-preparation")));
        clearInvocations(service);
        String token = tokens.issue(session);
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/cancel-preparation")
                        .header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("manifestHash", HASH).param("localFormToken", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/live-campaigns/" + CAMPAIGN_ID));
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/cancel-preparation")
                        .header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("manifestHash", HASH).param("localFormToken", token))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(InvalidLocalFormTokenException.class));
        verify(service).cancelPreparation(CAMPAIGN_ID, HASH);
        verifyNoMoreInteractions(service);
    }

    @Test
    void cancellationRequiresPostSameOriginAndLocalToken() throws Exception {
        String path = "/live-campaigns/" + CAMPAIGN_ID + "/cancel-preparation";
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(path).header("Host", HOST)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(path).header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("manifestHash", HASH))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(InvalidLocalFormTokenException.class));
        mvc.perform(post(path).header("Host", HOST).header("Origin", "https://foreign.invalid")
                        .session(session).param("manifestHash", HASH).param("localFormToken", tokens.issue(session)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void launchedPreparationCancellationExplainsTheConflictWithoutStoppingIt() throws Exception {
        MockHttpSession session = new MockHttpSession();
        doThrow(new IllegalStateException("LIVE_PREPARATION_ALREADY_LAUNCHED"))
                .when(service).cancelPreparation(CAMPAIGN_ID, HASH);
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/cancel-preparation")
                        .header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("manifestHash", HASH).param("localFormToken", tokens.issue(session)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Cette campagne a déjà été lancée")))
                .andExpect(content().string(containsString("LIVE_PREPARATION_ALREADY_LAUNCHED")));
        verify(service).cancelPreparation(CAMPAIGN_ID, HASH);
        verifyNoMoreInteractions(service);
    }

    @Test
    void cancelledPreparationRetainsItsSelectionAndExplainsThatItWasNeverLaunched() throws Exception {
        CampaignView base = campaign("PREPARED", 1);
        when(service.state(CAMPAIGN_ID)).thenReturn(new CampaignView(base.manifest(), "STOPPED_OPERATOR",
                "PREPARATION_CANCELLED", null, null, 0, 0, 3, null,
                List.of(new EventView(base.manifest().targets().getFirst(), "STOPPED_OPERATOR",
                        "PREPARATION_CANCELLED", 0, 0, null, List.of())), List.of(), List.of()));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Préparation annulée")))
                .andExpect(content().string(containsString("Aucune collecte n’a été lancée")))
                .andExpect(content().string(containsString("Non lancée")))
                .andExpect(content().string(not(containsString("Fixée au lancement"))))
                .andExpect(content().string(not(containsString("Annuler la préparation"))))
                .andExpect(content().string(not(containsString("Lancer la campagne live"))))
                .andExpect(content().string(not(containsString("Arrêter toute la campagne"))));
        verify(service, never()).cancelPreparation(any(), any());
    }

    @Test
    void runningCampaignDoesNotOfferPreparationCancellation() throws Exception {
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("RUNNING", 2));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Annuler la préparation"))))
                .andExpect(content().string(not(containsString("/cancel-preparation"))))
                .andExpect(content().string(containsString("Arrêter toute la campagne")));
        verify(service, never()).cancelPreparation(any(), any());
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
                .andExpect(model().attribute("excludedPostponed", List.of()))
                .andExpect(content().string(containsString("Ces rencontres sont déjà terminées")))
                .andExpect(content().string(containsString("Aucune campagne live n’a été créée")))
                .andExpect(content().string(containsString("&lt;Home&gt; — Away · finished")))
                .andExpect(content().string(not(containsString("<Home>"))))
                .andExpect(content().string(not(containsString("name=\"confirmation\""))));
        verify(service).prepareSelection(List.of(EVENT_ID));
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void postponedOnlyOrMixedExcludedSelectionExplainsNoCampaignAndEscapesNames(boolean alsoFinished) throws Exception {
        MockHttpSession session = new MockHttpSession();
        var postponed = List.of(postponedEvent());
        var finished = alsoFinished ? List.of(finishedEvent()) : List.<CanonicalEventObservationView>of();
        var selection = alsoFinished ? List.of(EVENT_ID, postponed.getFirst().identity().value())
                : List.of(postponed.getFirst().identity().value());
        when(service.prepareSelection(selection))
                .thenReturn(new LiveCampaignService.Preparation(null, finished, postponed));

        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("eventId", selection.stream().map(UUID::toString).toArray(String[]::new)))
                .andExpect(status().isOk()).andExpect(view().name("live-campaign-ineligible"))
                .andExpect(model().attribute("excludedFinished", finished))
                .andExpect(model().attribute("excludedPostponed", postponed))
                .andExpect(content().string(containsString(alsoFinished
                        ? "Ces rencontres ne sont pas éligibles au suivi live" : "Ces rencontres sont reportées")))
                .andExpect(content().string(containsString("Aucune campagne live n’a été créée")))
                .andExpect(content().string(containsString("aucun appel fournisseur n’a été effectué")))
                .andExpect(content().string(containsString("&lt;Reported&gt; — Away · postponed (reportée)")))
                .andExpect(content().string(not(containsString("<Reported>"))))
                .andExpect(content().string(not(containsString("Toutes les rencontres sélectionnées ont le statut local finished"))))
                .andExpect(content().string(not(containsString("name=\"confirmation\""))));
        verify(service).prepareSelection(selection);
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
    void anEligibleSelectionShowsItsPostponedExclusionsOnTheConfirmationPage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var postponed = List.of(postponedEvent());
        var selection = List.of(EVENT_ID, postponed.getFirst().identity().value());
        when(service.prepareSelection(selection))
                .thenReturn(new LiveCampaignService.Preparation(manifest(), List.of(), postponed));

        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("eventId", selection.stream().map(UUID::toString).toArray(String[]::new)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/live-campaigns/" + CAMPAIGN_ID))
                .andExpect(flash().attribute("excludedPostponed", postponed));
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("PREPARED", 1));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST)
                        .flashAttr("excludedPostponed", postponed))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Les rencontres suivantes sont reportées (postponed)")))
                .andExpect(content().string(containsString("Elles ont été exclues du manifeste")))
                .andExpect(content().string(containsString("&lt;Reported&gt; — Away")))
                .andExpect(content().string(not(containsString("<Reported>"))))
                .andExpect(content().string(not(containsString("Les rencontres suivantes sont déjà terminées"))))
                .andExpect(content().string(containsString("name=\"confirmation\"")));
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "STOPPED_ALREADY_POSTPONED|La rencontre a été constatée reportée (postponed) localement au lancement",
            "STOPPED_POSTPONED|La rencontre a été signalée reportée (postponed) par J4"
    })
    void postponedEventsExplainTheirStopWithoutOfferingAnotherEventStop(String state, String explanation) throws Exception {
        var base = campaign("RUNNING", 2);
        when(service.state(CAMPAIGN_ID)).thenReturn(new CampaignView(base.manifest(), "RUNNING", null,
                NOW, NOW.plusSeconds(14400), 0, 0, 2, null,
                List.of(new EventView(base.manifest().targets().getFirst(), state, state,
                        0, 0, null, List.of())), List.of(), List.of()));

        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(explanation)))
                .andExpect(content().string(containsString(state)))
                .andExpect(content().string(not(containsString("Arrêter cette rencontre"))));
        verify(service, never()).launch(any(), any());
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

    @Test
    void eventsPostponedAfterPreparationHaveAnActionableLaunchRefusal() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(service.launch(CAMPAIGN_ID, HASH)).thenThrow(new IllegalArgumentException("LIVE_ALL_EVENTS_INELIGIBLE"));

        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/launch").header("Host", HOST)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("manifestHash", HASH).param("confirmation", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("liveErrorCode", "LIVE_ALL_EVENTS_INELIGIBLE"))
                .andExpect(content().string(containsString("reportées (postponed) ou déjà terminées (finished)")))
                .andExpect(content().string(containsString("Aucun lancement live ni appel fournisseur")))
                .andExpect(content().string(containsString("préparer une autre sélection")));
        verify(service).launch(CAMPAIGN_ID, HASH);
        verifyNoMoreInteractions(service);
    }

    private static CanonicalEventObservationView finishedEvent() {
        return new CanonicalEventObservationView(17, CanonicalEventIdentity.sofascore(900001L), NOW,
                new ScheduledTeam(1, "<Home>"), new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus("finished", Optional.empty()), Optional.empty(),
                EventSourceTrace.providerSnapshot(23, HASH, "event-details-v2", NOW), HASH, 1);
    }

    private static CanonicalEventObservationView postponedEvent() {
        return new CanonicalEventObservationView(18, CanonicalEventIdentity.sofascore(900002L), NOW,
                new ScheduledTeam(3, "<Reported>"), new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus("postponed", Optional.empty()), Optional.empty(),
                EventSourceTrace.providerSnapshot(24, HASH, "event-details-v2", NOW), HASH, 1);
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
        verify(service).runtimeStatus(CAMPAIGN_ID);
        verify(service).orphanCleanupGuard(CAMPAIGN_ID);
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
        verify(service).runtimeStatus(CAMPAIGN_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void periodDescriptionIsEscapedInHtmlAndSeparateFromTheTechnicalStateInJson() throws Exception {
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("RUNNING", 42));
        when(events.findByObservationId(EVENT_ID, 1)).thenReturn(Optional.of(
                new CanonicalEventObservationView(1, CanonicalEventIdentity.sofascore(900001L), NOW,
                        new ScheduledTeam(1, "Home"), new ScheduledTeam(2, "Away"),
                        new ScheduledEventStatus("inprogress", Optional.of("<b>Halftime</b>")), Optional.empty(),
                        EventSourceTrace.providerSnapshot(1, HASH, "event-details-v2", NOW), HASH, 1)));

        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;b&gt;Halftime&lt;/b&gt;")))
                .andExpect(content().string(not(containsString("<b>Halftime</b>"))));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID + "/state").header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].sportStatus").value("inprogress"))
                .andExpect(jsonPath("$.events[0].sportStatusLabel").value("<b>Halftime</b>"));
        verify(service, never()).launch(any(), any());
    }

    @Test
    void eventRoutesReadOnlyTheRequestedIdentities() throws Exception {
        when(service.eventStates(List.of(EVENT_ID))).thenReturn(List.of(campaign("RUNNING", 43)));
        when(service.runtimeStatus(CAMPAIGN_ID)).thenReturn(Optional.of(new LiveCampaignService.RuntimeStatus(
                "STOPPED_ERROR", "LOCAL_CLEANUP_PENDING", true, true, false)));
        mvc.perform(get("/events/state").header("Host", HOST).param("eventId", EVENT_ID.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].revision").value(43))
                .andExpect(jsonPath("$[0].state").value("RUNNING"))
                .andExpect(jsonPath("$[0].runtimeStatus.cleanupPending").value(true));
        mvc.perform(get("/events/" + EVENT_ID + "/state").header("Host", "127.0.0.1:8087"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("RUNNING"))
                .andExpect(jsonPath("$[0].runtimeStatus.collectionStopped").value(true));
        verify(service, times(2)).eventStates(List.of(EVENT_ID));
        verify(service, times(2)).runtimeStatus(CAMPAIGN_ID);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppedCollectionOffersOnlyGlobalCleanupAndPreservesTheDurableState(boolean cleanupInProgress) throws Exception {
        when(service.state(CAMPAIGN_ID)).thenReturn(campaign("RUNNING", 43));
        when(service.runtimeStatus(CAMPAIGN_ID)).thenReturn(Optional.of(new LiveCampaignService.RuntimeStatus(
                "STOPPED_ERROR", "LOCAL_CLEANUP_PENDING", true, true, cleanupInProgress)));

        var page = mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("État enregistré")))
                .andExpect(content().string(containsString(cleanupInProgress
                        ? "Collecte arrêtée / clôture locale en cours." : "Collecte arrêtée / clôture locale requise.")))
                .andExpect(content().string(not(containsString("Arrêter cette rencontre"))))
                .andReturn().getResponse().getContentAsString();
        var button = Pattern.compile("(?s)<form\\b[^>]*\\bdata-live-global-stop-form\\b[^>]*>.*?<button\\b([^>]*)>([^<]*)</button>")
                .matcher(page);
        assertThat(button.find()).isTrue();
        assertThat(button.group(2)).isEqualTo(cleanupInProgress ? "Clôture locale en cours" : "Finaliser la clôture locale");
        assertThat(button.group(1).contains("disabled")).isEqualTo(cleanupInProgress);
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID + "/state").header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(43))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.events[0].state").value("WAITING_START"))
                .andExpect(jsonPath("$.runtimeStatus.cleanupInProgress").value(cleanupInProgress));
        verify(service, times(2)).state(CAMPAIGN_ID);
        verify(service, times(2)).runtimeStatus(CAMPAIGN_ID);
        verify(service).orphanCleanupGuard(CAMPAIGN_ID);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"LIVE_PROVIDER_CLEANUP_REQUIRED,session fournisseur précédente reste verrouillée",
            "LIVE_LAUNCH_FAILED,lancement de la campagne a échoué"})
    void launchLifecycleFailuresExposeTheirKnownCodeAndAction(String code, String message) throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(service.launch(CAMPAIGN_ID, HASH)).thenThrow(new IllegalStateException(code));
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/launch").header("Host", HOST)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("manifestHash", HASH).param("confirmation", "true"))
                .andExpect(status().isConflict())
                .andExpect(model().attribute("liveErrorCode", code))
                .andExpect(content().string(containsString(message)));
        verify(service).launch(CAMPAIGN_ID, HASH);
        if (code.equals("LIVE_PROVIDER_CLEANUP_REQUIRED")) verify(service).providerCleanupCampaignId();
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
            "LIVE_CAPACITY_QUALIFICATION_REQUIRED,preuve de qualification",
            "LIVE_V8_FRESHNESS_CAPACITY_UNAVAILABLE,fraîcheur de 60 secondes"})
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

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 10, 25})
    void multipleEligibleEventsReachTheConfirmationPageAndLaunchTheSameManifest(int count) throws Exception {
        var targets = IntStream.range(0, count).mapToObj(i -> new Target(
                CanonicalEventIdentity.sofascore(900001L + i).value(), 900001L + i, i + 1L, i + 1L)).toList();
        var ids = targets.stream().map(Target::canonicalEventId).toList();
        var interval = com.bettingproject.sofascorelocal.domain.live.LiveCadence.forMatches(count);
        var manifest = new Manifest(CAMPAIGN_ID, HASH, "live-v2", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, Math.min(3000L, count * 1000L) * 5 * 1024 * 1024, 25, targets,
                new AdmissionProfile(Duration.ofSeconds(1), Duration.ofSeconds(1), "b".repeat(64)), interval);
        when(service.prepareSelection(ids)).thenReturn(new LiveCampaignService.Preparation(manifest, List.of()));
        when(service.state(CAMPAIGN_ID)).thenReturn(new CampaignView(manifest, "PREPARED", null, null, null,
                0, 0, 1, null, targets.stream().map(t -> new EventView(t, "PREPARED", null, 0, 0, null, List.of())).toList(),
                List.of(), List.of()));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("eventId", ids.stream().map(UUID::toString).toArray(String[]::new)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/live-campaigns/" + CAMPAIGN_ID));
        var page = mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST).session(session))
                .andExpect(status().isOk()).andExpect(model().attribute("manifest", manifest))
                .andExpect(content().string(containsString("name=\"confirmation\"")))
                .andExpect(content().string(containsString(interval.toSeconds() + " secondes"))).andReturn();
        assertThat(renderedEventIds(page.getResponse().getContentAsString()))
                .containsExactlyElementsOf(ids.stream().limit(10).map(UUID::toString).toList());
        verify(service, never()).launch(any(), any());
        mvc.perform(post("/live-campaigns/" + CAMPAIGN_ID + "/launch").header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("localFormToken", tokens.issue(session))
                        .param("manifestHash", HASH).param("confirmation", "true"))
                .andExpect(status().is3xxRedirection());
        verify(service).prepareSelection(ids);
        verify(service).launch(CAMPAIGN_ID, HASH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PREPARED", "RUNNING", "COMPLETED", "INTERRUPTED", "STOPPED_OPERATOR", "STOPPED_ERROR", "STOPPED_LIMIT"})
    void campaignPagesKeepStableSlicesAndTheCompleteManifestForEveryLifecycleState(String state) throws Exception {
        var view = paginatedCampaign(state, 17);
        when(service.state(CAMPAIGN_ID)).thenReturn(view);
        var ids = view.manifest().targets().stream().map(t -> t.canonicalEventId().toString()).toList();
        String base = "/live-campaigns/" + CAMPAIGN_ID;
        String first = mvc.perform(get(base).header("Host", HOST)).andExpect(status().isOk())
                .andExpect(model().attribute("manifest", view.manifest())).andReturn().getResponse().getContentAsString();
        assertThat(renderedEventIds(first)).containsExactlyElementsOf(ids.subList(0, 10));
        assertThat(first).contains("data-live-state-url=\"" + base + "/state?page=1\"");
        String second = mvc.perform(get(base).param("page", "2").header("Host", HOST)).andExpect(status().isOk())
                .andExpect(model().attribute("manifest", view.manifest())).andReturn().getResponse().getContentAsString();
        assertThat(renderedEventIds(second)).containsExactlyElementsOf(ids.subList(10, 17));
        assertThat(second).contains("data-live-state-url=\"" + base + "/state?page=2\"", "aria-current=\"page\"");
        assertThat(Pattern.compile("<nav\\b[^>]*data-live-pagination").matcher(second).results().count()).isEqualTo(2);
        assertThat(first).contains("?page=2#campaign-events");
        assertThat(second).contains("?page=1#campaign-events");
        String bounded = mvc.perform(get(base).param("page", "2147483647").header("Host", HOST))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(renderedEventIds(bounded)).containsExactlyElementsOf(ids.subList(10, 17));
        assertThat(bounded).contains("/state?page=2\"");
        verify(service, never()).launch(any(), any());
        verify(service, never()).stop(any(), any());
    }

    @Test
    void paginatedStateReturnsOnlyTheRequestedSliceAndUnpagedStateRemainsComplete() throws Exception {
        var view = paginatedCampaign("RUNNING", 17);
        when(service.state(CAMPAIGN_ID)).thenReturn(view);
        String path = "/live-campaigns/" + CAMPAIGN_ID + "/state";
        mvc.perform(get(path).param("page", "1").header("Host", HOST)).andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(10)).andExpect(jsonPath("$.pagination.number").value(1))
                .andExpect(jsonPath("$.pagination.totalElements").value(17)).andExpect(jsonPath("$.reservedCalls").value(123));
        mvc.perform(get(path).param("page", "2").header("Host", HOST)).andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(7))
                .andExpect(jsonPath("$.events[0].canonicalEventId").value(view.events().get(10).target().canonicalEventId().toString()))
                .andExpect(jsonPath("$.events[6].canonicalEventId").value(view.events().get(16).target().canonicalEventId().toString()))
                .andExpect(jsonPath("$.pagination.number").value(2))
                .andExpect(jsonPath("$.pagination.size").value(10))
                .andExpect(jsonPath("$.pagination.totalElements").value(17))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.reservedCalls").value(123))
                .andExpect(jsonPath("$.receivedBytes").value(45678))
                .andExpect(jsonPath("$.cadence.targetSeconds").value(view.manifest().cycleInterval().toSeconds()))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get(path).param("page", "999").header("Host", HOST)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.number").value(2)).andExpect(jsonPath("$.events.length()").value(7));
        mvc.perform(get(path).header("Host", HOST)).andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(17))
                .andExpect(jsonPath("$.events[0].canonicalEventId").value(view.events().getFirst().target().canonicalEventId().toString()))
                .andExpect(jsonPath("$.events[16].canonicalEventId").value(view.events().getLast().target().canonicalEventId().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "FIRST", "CLEANUP", "BOTH"})
    void stopDiagnosticsAreGlobalOptionalAndSeparateFromTheLedgerState(String mode) throws Exception {
        LiveCampaignDiagnostic first = mode.equals("FIRST") || mode.equals("BOTH")
                ? new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.STORAGE_CHECK, "LIVE_STORAGE_PROBE_TIMEOUT", NOW) : null;
        LiveCampaignDiagnostic cleanup = mode.equals("CLEANUP") || mode.equals("BOTH")
                ? new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_EXCLUSION,
                        "RUNTIME_OR_STORAGE_FAILURE", NOW.plusSeconds(5)) : null;
        when(service.state(CAMPAIGN_ID)).thenReturn(paginatedCampaign("RUNNING", 17));
        when(service.runtimeStatus(CAMPAIGN_ID)).thenReturn(Optional.of(new LiveCampaignService.RuntimeStatus(
                "STOPPED_ERROR", "LOCAL_CLEANUP_PENDING", true, true, false, first, cleanup)));
        String base = "/live-campaigns/" + CAMPAIGN_ID;
        String body = mvc.perform(get(base).param("page", "2").header("Host", HOST))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var box = Pattern.compile("(?s)<details\\b([^>]*data-live-diagnostics[^>]*)>(.*?)</details>").matcher(body);
        assertThat(box.find()).isTrue();
        assertThat(box.group(1).contains("hidden")).isEqualTo(first == null && cleanup == null);
        assertThat(Pattern.compile("\\bopen(?:=|\\s|$)").matcher(box.group(1)).find()).isFalse();
        String diagnosticBody = box.group(2);
        assertThat(box.find()).as("one global diagnostic box, never one per event").isFalse();
        assertThat(diagnosticBody).contains("Diagnostics de collecte et de clôture", "Première erreur", "Dernier échec de clôture",
                "Instant (UTC)", "reste consultable après redémarrage", "informations non observées");
        assertThat(body).contains("href=\"/provider-access\"");
        for (String key : List.of("firstFailure", "cleanupFailure")) {
            LiveCampaignDiagnostic expected = key.equals("firstFailure") ? first : cleanup;
            var section = Pattern.compile("(?s)<section\\b([^>]*data-live-diagnostic=\"" + key + "\"[^>]*)>(.*?)</section>")
                    .matcher(diagnosticBody);
            assertThat(section.find()).isTrue();
            assertThat(section.group(1).contains("hidden")).isEqualTo(expected == null);
            if (expected != null) assertThat(section.group(2)).contains(expected.phase().name(), expected.code(), expected.occurredAt().toString());
            for (String field : List.of("attempt", "endpoint", "transport-phase", "timeout", "requested-at",
                    "headers-at", "http-status", "retry-after", "complete"))
                assertThat(Pattern.compile("<dd\\b[^>]*data-live-diagnostic-" + field + "[^>]*>\\s*—\\s*</dd>")
                        .matcher(section.group(2)).find()).as("unknown historical field %s", field).isTrue();
        }
        var state = mvc.perform(get(base + "/state").param("page", "2").header("Host", HOST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(42))
                .andExpect(jsonPath("$.state").value("RUNNING"));
        if (first == null) state.andExpect(jsonPath("$.runtimeStatus.firstFailure").doesNotExist());
        else state.andExpect(jsonPath("$.runtimeStatus.firstFailure.phase").value("STORAGE_CHECK"))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.code").value("LIVE_STORAGE_PROBE_TIMEOUT"))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.occurredAt").value(NOW.toString()))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.attemptId").doesNotExist())
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.endpoint").doesNotExist())
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.transport").doesNotExist());
        if (cleanup == null) state.andExpect(jsonPath("$.runtimeStatus.cleanupFailure").doesNotExist());
        else state.andExpect(jsonPath("$.runtimeStatus.cleanupFailure.phase").value("CLEANUP_EXCLUSION"))
                .andExpect(jsonPath("$.runtimeStatus.cleanupFailure.code").value("RUNTIME_OR_STORAGE_FAILURE"))
                .andExpect(jsonPath("$.runtimeStatus.cleanupFailure.occurredAt").value(NOW.plusSeconds(5).toString()))
                .andExpect(jsonPath("$.runtimeStatus.cleanupFailure.attemptId").doesNotExist())
                .andExpect(jsonPath("$.runtimeStatus.cleanupFailure.endpoint").doesNotExist())
                .andExpect(jsonPath("$.runtimeStatus.cleanupFailure.transport").doesNotExist());
        verify(service, never()).stop(any(), any());
        verify(service, never()).launch(any(), any());
    }

    @Test
    void durablePartialRefusalMetadataIsVisibleWithoutClaimingACompleteResponse() throws Exception {
        UUID attemptId=UUID.randomUUID();
        var transport=new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.PARENT_IPC_WAIT,30_000,
                NOW.minusSeconds(10),NOW.minusSeconds(9),429,NOW.plusSeconds(60),false);
        var first=new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.TRANSPORT,"PROVIDER_HTTP_429",NOW,
                attemptId,SofascoreEndpointType.EVENT_STATISTICS,transport);
        when(service.state(CAMPAIGN_ID)).thenReturn(paginatedCampaign("STOPPED_ERROR",17));
        when(service.runtimeStatus(CAMPAIGN_ID)).thenReturn(Optional.of(new LiveCampaignService.RuntimeStatus(
                "STOPPED_ERROR","STOPPED_ERROR",true,false,false,first,null)));
        String path="/live-campaigns/"+CAMPAIGN_ID;
        String html=mvc.perform(get(path).header("Host",HOST)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains(attemptId.toString(),"EVENT_STATISTICS","PARENT_IPC_WAIT","30000 ms",
                "data-live-diagnostic-http-status>429</dd>","data-live-diagnostic-complete>Non</dd>",
                NOW.minusSeconds(10).toString(),NOW.minusSeconds(9).toString(),NOW.plusSeconds(60).toString());
        mvc.perform(get(path+"/state").header("Host",HOST)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STOPPED_ERROR"))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.endpoint").value("EVENT_STATISTICS"))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.transport.phase").value("PARENT_IPC_WAIT"))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.transport.requestTimeoutMillis").value(30_000))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.transport.httpStatus").value(429))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.transport.retryAfterNotBefore").value(NOW.plusSeconds(60).toString()))
                .andExpect(jsonPath("$.runtimeStatus.firstFailure.transport.responseComplete").value(false));
        verify(service,never()).stop(any(),any());
        verify(service,never()).launch(any(),any());
    }

    @Test
    void pageTwoRendersStoppedAutonomyWithoutRewritingTheRunningLedgerState() throws Exception {
        var view = paginatedCampaign("RUNNING", 17);
        when(service.state(CAMPAIGN_ID)).thenReturn(view);
        when(service.runtimeStatus(CAMPAIGN_ID)).thenReturn(Optional.of(new LiveCampaignService.RuntimeStatus(
                "STOPPED_ERROR", "LOCAL_CLEANUP_PENDING", true, true, false)));
        var response = mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST).param("page", "2"))
                .andExpect(status().isOk()).andExpect(model().attribute("manifest", view.manifest())).andReturn();
        var projected = (LiveCampaignPresentation.Campaign) response.getModelAndView().getModel().get("campaign");
        assertThat(projected.state()).isEqualTo("RUNNING");
        assertThat(projected.revision()).isEqualTo(view.revision());
        assertThat(projected.pagination().number()).isEqualTo(2);
        assertThat(projected.pagination().totalElements()).isEqualTo(17);
        assertThat(projected.cadence().targetSeconds()).isEqualTo(100);
        assertThat(projected.cadence().estimatedRemainingSeconds()).isZero();
        String body = response.getResponse().getContentAsString();
        assertThat(renderedEventIds(body)).containsExactlyElementsOf(view.events().subList(10, 17).stream()
                .map(event -> event.target().canonicalEventId().toString()).toList());
        assertThat(Pattern.compile("<dd\\b[^>]*data-live-autonomy[^>]*>\\s*Collecte arrêtée\\s*</dd>")
                .matcher(body).find()).as("SSR autonomy reflects the stopped process despite RUNNING in the ledger").isTrue();
        verify(service, never()).stop(any(), any());
        verify(service, never()).launch(any(), any());
    }

    @Test
    void incomingEventLinkSelectsItsPageAndUnknownTargetsReturnNotFound() throws Exception {
        var view = paginatedCampaign("RUNNING", 17);
        when(service.state(CAMPAIGN_ID)).thenReturn(view);
        UUID target = view.events().get(10).target().canonicalEventId();
        String body = mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST)
                        .param("eventId", target.toString()).param("page", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(renderedEventIds(body)).containsExactlyElementsOf(view.events().subList(10, 17).stream()
                .map(event -> event.target().canonicalEventId().toString()).toList());
        assertThat(body).contains("id=\"live-event-" + target + "\"", "/state?page=2\"");
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST)
                        .param("eventId", UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "2147483648"})
    void invalidPagesFailBeforeAnyMutation(String page) throws Exception {
        when(service.state(CAMPAIGN_ID)).thenReturn(paginatedCampaign("RUNNING", 17));
        String base = "/live-campaigns/" + CAMPAIGN_ID;
        for (String suffix : List.of("", "/state"))
            mvc.perform(get(base + suffix).param("page", page).header("Host", HOST)).andExpect(status().isBadRequest());
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post(base + "/stop").param("page", page).param("localFormToken", tokens.issue(session))
                        .header("Host", HOST).header("Origin", ORIGIN).session(session)).andExpect(status().isBadRequest());
        verify(service, never()).stop(any(), any());
        verify(service, never()).launch(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10})
    void onePageCampaignsHaveNoPaginationNavigation(int count) throws Exception {
        when(service.state(CAMPAIGN_ID)).thenReturn(paginatedCampaign("COMPLETED", count));
        String body = mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).param("page", "2").header("Host", HOST))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(renderedEventIds(body)).hasSize(count);
        assertThat(body).doesNotContain("data-live-pagination").contains("/state?page=1\"");
    }

    @Test
    void everyCampaignActionRetainsPageTwoWithoutChangingItsServiceArguments() throws Exception {
        String base = "/live-campaigns/" + CAMPAIGN_ID;
        MockHttpSession session = new MockHttpSession();
        for (String suffix : List.of("/launch", "/cancel-preparation", "/stop", "/events/" + EVENT_ID + "/stop", "/finalize-interruption")) {
            mvc.perform(post(base + suffix).header("Host", HOST).header("Origin", ORIGIN).session(session)
                            .param("localFormToken", tokens.issue(session)).param("page", "2")
                            .param("manifestHash", HASH).param("confirmation", "true").param("guardGeneration", "7"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(base + "?page=2"));
        }
        verify(service).launch(CAMPAIGN_ID, HASH);
        verify(service).cancelPreparation(CAMPAIGN_ID, HASH);
        verify(service).stop(CAMPAIGN_ID, null);
        verify(service).stop(CAMPAIGN_ID, EVENT_ID);
        verify(service).finalizeInterruptedCleanup(CAMPAIGN_ID, 7);
        mvc.perform(post(base + "/stop").header("Host", HOST).header("Origin", ORIGIN).session(session)
                        .param("localFormToken", tokens.issue(session)).param("page", "1"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(base));
    }

    @Test
    void pageTwoFormsKeepTheirPageAndReprepareStillSubmitsAllSeventeenTargets() throws Exception {
        String base = "/live-campaigns/" + CAMPAIGN_ID;
        MockHttpSession session = new MockHttpSession();
        for (String state : List.of("PREPARED", "RUNNING", "INTERRUPTED", "COMPLETED")) {
            var view = paginatedCampaign(state, 17);
            when(service.state(CAMPAIGN_ID)).thenReturn(view);
            when(service.orphanCleanupGuard(CAMPAIGN_ID)).thenReturn(state.equals("INTERRUPTED")
                    ? Optional.of(new Guard("CLEANUP_REQUIRED", CAMPAIGN_ID,
                            new Owner(UUID.randomUUID(), 654321, NOW), 7, NOW)) : Optional.empty());
            String body = mvc.perform(get(base).param("page", "2").header("Host", HOST).session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var forms = Pattern.compile("(?s)<form\\b[^>]*action=\"" + Pattern.quote(base) + "/[^\"]+\"[^>]*>.*?</form>").matcher(body);
            int formCount = 0;
            while (forms.find()) {
                formCount++;
                assertThat(hiddenValue(forms.group(), "page")).isEqualTo("2");
            }
            assertThat(formCount).isEqualTo(switch (state) {
                case "PREPARED" -> 2;
                case "RUNNING" -> 8;
                case "INTERRUPTED" -> 1;
                default -> 0;
            });
            if (state.equals("COMPLETED")) {
                String form = renderedForm(body, "/live-campaigns/prepare");
                var ids = view.manifest().targets().stream().map(Target::canonicalEventId).toList();
                for (UUID id : ids) assertThat(form).contains("value=\"" + id + "\"");
                when(service.prepareSelection(ids)).thenReturn(new LiveCampaignService.Preparation(view.manifest(), List.of()));
                mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", ORIGIN).session(session)
                                .param("localFormToken", hiddenValue(form, "localFormToken"))
                                .param("eventId", ids.stream().map(UUID::toString).toArray(String[]::new)))
                        .andExpect(status().is3xxRedirection());
                verify(service).prepareSelection(ids);
            }
        }
        verify(service, never()).launch(any(), any());
    }

    private static List<String> renderedEventIds(String body) {
        return Pattern.compile("data-live-event-id=\"([^\"]+)\"").matcher(body).results().map(m -> m.group(1)).toList();
    }

    private static CampaignView paginatedCampaign(String state, int count) {
        var targets = IntStream.range(0, Math.max(1, count)).mapToObj(i -> new Target(
                CanonicalEventIdentity.sofascore(900001L + i).value(), 900001L + i, i + 1L, i + 1L)).toList();
        var envelopes = new java.util.EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var endpoint : List.of(SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS))
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(400), Duration.ofMillis(100)));
        var manifest = new Manifest(CAMPAIGN_ID, HASH, "live-v5", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                2500, 20000, 1_000_000, 20, targets, new AdmissionProfile(Duration.ofSeconds(10),
                Duration.ofSeconds(1), "", new GroupedAdmissionProfile(envelopes, "b".repeat(64), "live-v5")), Duration.ofSeconds(100));
        return new CampaignView(manifest, state, null, state.equals("PREPARED") ? null : NOW,
                state.equals("PREPARED") ? null : NOW.plusSeconds(14400), 123, 45678, 42, null,
                targets.stream().limit(count).map(t -> new EventView(t, state.equals("RUNNING") ? "COLLECTING" : state,
                        null, 4, 1024, null, List.of())).toList(), List.of(), List.of());
    }

    private static Manifest manifest() {
        return new Manifest(CAMPAIGN_ID, HASH, "live-v1", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, 1_000_000, 1, List.of(new Target(EVENT_ID, 900001L, 1, 1)));
    }

    @ParameterizedTest
    @CsvSource({"live-v4,60,10,1000,3000,5 minutes nominales", "live-v5,100,20,2500,20000,5 minutes nominales",
            "live-v8,60,10,2500,20000,60 secondes nominales"})
    void groupedPreparationRendersActualCapacityCadencesProofAndAutonomyBeforeLaunch(String policy, int interval,
                int capacity, int eventCalls, int campaignCalls, String lineupLabel) throws Exception {
        var envelopes = new java.util.EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var endpoint : List.of(SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS))
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(400), Duration.ofMillis(100)));
        var target = manifest().targets().getFirst();
        var grouped = new Manifest(CAMPAIGN_ID, HASH, policy, NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                eventCalls, campaignCalls, 1_000_000, capacity, List.of(target), new AdmissionProfile(Duration.ofSeconds(10),
                Duration.ofSeconds(1), "", new GroupedAdmissionProfile(envelopes, "b".repeat(64), policy)), Duration.ofSeconds(interval));
        when(service.state(CAMPAIGN_ID)).thenReturn(new CampaignView(grouped, "PREPARED", null, null, null,
                0, 0, 1, null, List.of(new EventView(target, "PREPARED", null, 0, 0, null, List.of())), List.of(), List.of()));
        mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(interval + " secondes par rencontre")))
                .andExpect(content().string(containsString(lineupLabel)))
                .andExpect(content().string(containsString(capacity + " rencontres")))
                .andExpect(content().string(containsString("240 minutes environ")))
                .andExpect(content().string(containsString("b".repeat(64))))
                .andExpect(content().string(containsString("400 ms")))
                .andExpect(content().string(not(containsString("le premier triplet attend"))));
        if ("live-v8".equals(policy)) {
            mvc.perform(get("/live-campaigns/" + CAMPAIGN_ID).header("Host", HOST))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("500 ms")))
                    .andExpect(content().string(containsString("45 départs par minute et 2 756 par heure")))
                    .andExpect(content().string(containsString("créneaux qualifiés de 60 secondes")));
        }
        verify(service, never()).launch(any(), any());
    }

    @Test
    void staleSelectionReturnsAnExplicitConflictAndNeverLaunches() throws Exception {
        when(service.prepareSelection(List.of(EVENT_ID))).thenThrow(new IllegalStateException("LIVE_EVENT_ALREADY_IN_CAMPAIGN"));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/live-campaigns/prepare").header("Host", HOST).header("Origin", ORIGIN)
                        .session(session).param("localFormToken", tokens.issue(session)).param("eventId", EVENT_ID.toString()))
                .andExpect(status().isConflict()).andExpect(model().attribute("liveErrorCode", "LIVE_EVENT_ALREADY_IN_CAMPAIGN"))
                .andExpect(content().string(containsString("appartient déjà à une campagne en cours")))
                .andExpect(content().string(containsString("STOPPED_ERROR")));
        verify(service, never()).launch(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"RUNNING,COLLECTING,true", "RUNNING,STOPPED_ERROR,false", "STOPPED_ERROR,STOPPED_ERROR,false"})
    void stateRefreshIncludesTheSelectionRule(String campaignState, String eventState, boolean blocked) throws Exception {
        var view = campaign(campaignState, 50);
        when(service.eventStates(List.of(EVENT_ID))).thenReturn(List.of(new CampaignView(view.manifest(), campaignState,
                null, NOW, view.endsAt(), 0, 0, 50, null,
                List.of(new EventView(view.manifest().targets().getFirst(), eventState, null, 0, 0, NOW, List.of())),
                List.of(), List.of())));
        mvc.perform(get("/events/state").header("Host", HOST).param("eventId", EVENT_ID.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].events[0].selectionBlocked").value(blocked));
    }

    private static CampaignView campaign(String state, long revision) {
        return new CampaignView(manifest(), state, null, state.equals("PREPARED") ? null : NOW,
                state.equals("PREPARED") ? null : NOW.plusSeconds(14400), 0, 0, revision, null,
                List.of(new EventView(manifest().targets().getFirst(), "WAITING_START", null, 0, 0,
                        NOW, List.of())), List.of(), List.of());
    }
}
