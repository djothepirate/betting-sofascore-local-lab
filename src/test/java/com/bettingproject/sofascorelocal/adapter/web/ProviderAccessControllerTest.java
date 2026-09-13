package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.config.LiveCampaignWebMvcConfiguration;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.Guard;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.Owner;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.*;
import com.bettingproject.sofascorelocal.port.ProviderCampaignGuardStore;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProviderAccessController.class)
@Import({LocalFormTokenService.class, LiveCampaignWebMvcConfiguration.class})
class ProviderAccessControllerTest {
    private static final String HOST="localhost:8087", ORIGIN="http://localhost:8087";
    @Autowired MockMvc mvc;
    @Autowired LocalFormTokenService tokens;
    @MockitoBean ProviderResilienceStore store;
    @MockitoBean ProviderCampaignGuardStore guard;
    @MockitoBean PlaywrightProviderSupervisor supervisor;
    @MockitoBean ManualProviderRequestCoordinator coordinator;
    @MockitoBean LiveCampaignService campaigns;
    @MockitoBean CacheManager cacheManager;

    @BeforeEach void configureLocalState() {
        var state=snapshot(null);
        when(store.snapshot()).thenReturn(state);
        when(store.departureDecision(any())).thenReturn(new DepartureDecision(false,DepartureReason.PROVIDER_SUSPENDED,null,state));
        when(guard.snapshot()).thenReturn(new Guard("FREE",null,null,1,Instant.now()));
        when(supervisor.activeCampaignId()).thenReturn(Optional.empty());
        when(campaigns.orphanedManualCleanupGuard()).thenReturn(Optional.empty());
        doAnswer(call->{call.<Runnable>getArgument(0).run();return null;}).when(coordinator).withExclusiveLocalCleanup(any());
    }

    @Test void observationIsReadOnlyAndDoesNotAuthorizeProviderTraffic() throws Exception {
        mvc.perform(get("/provider-access").header("Host",HOST)).andExpect(status().isOk())
                .andExpect(view().name("provider-access")).andExpect(model().attribute("suspended",true))
                .andExpect(header().string("Cache-Control","no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("45 départs par minute")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2&nbsp;756 par heure")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("500&nbsp;ms")));
        verify(store,never()).tryReserveDeparture(any(),any());
        verify(store,never()).rearm(anyLong(),any());
        verifyNoInteractions(supervisor,coordinator);
    }

    @Test void rearmRequiresValidSingleUseTokenAndExplicitConfirmation() throws Exception {
        MockHttpSession session=new MockHttpSession();
        mvc.perform(post("/provider-access/rearm").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("version","7").param("confirmation","true")).andExpect(status().isBadRequest());
        String token=tokens.issue(session);
        mvc.perform(post("/provider-access/rearm").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("version","7").param("confirmation","false").param("localFormToken",token)).andExpect(status().isBadRequest());
        mvc.perform(post("/provider-access/rearm").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("version","7").param("confirmation","true").param("localFormToken",token)).andExpect(status().isBadRequest());
        verify(store,never()).rearm(anyLong(),any());
        verifyNoInteractions(coordinator,supervisor);
    }

    @Test void manualRearmDoesNotStartStopOrReserveAnyProviderRequest() throws Exception {
        MockHttpSession session=new MockHttpSession();String token=tokens.issue(session);
        mvc.perform(post("/provider-access/rearm").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Dest","document")
                .param("version","7").param("confirmation","true").param("localFormToken",token))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/provider-access"));
        verify(store).rearm(eq(7L),any());
        verify(store,never()).tryReserveDeparture(any(),any());
        verify(supervisor,never()).stopCampaign(any(),any());
        verify(coordinator).withExclusiveLocalCleanup(any());
        verifyNoMoreInteractions(coordinator);
    }

    @Test void staleVersionIsRejectedWithoutRetryingOrClearingBudget() throws Exception {
        when(store.rearm(eq(6L),any())).thenThrow(new IllegalStateException("PROVIDER_REARM_STALE_VERSION"));
        MockHttpSession session=new MockHttpSession();
        mvc.perform(post("/provider-access/rearm").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("version","6").param("confirmation","true").param("localFormToken",tokens.issue(session)))
                .andExpect(status().isConflict()).andExpect(view().name("live-campaign-error"));
        verify(store,times(1)).rearm(eq(6L),any());
        verify(store,never()).tryReserveDeparture(any(),any());
        verify(store,never()).markDepartureFinished(any(),any());
        verify(supervisor,never()).stopCampaign(any(),any());
    }

    @Test void unresolvedDeparturePreventsRearmAndItsExplicitCloseDoesNotClearRefusal() throws Exception {
        UUID dispatch=UUID.randomUUID();when(store.snapshot()).thenReturn(snapshot(dispatch));
        MockHttpSession session=new MockHttpSession();
        mvc.perform(post("/provider-access/rearm").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("version","7").param("confirmation","true").param("localFormToken",tokens.issue(session)))
                .andExpect(status().isConflict());
        verify(store,never()).rearm(anyLong(),any());
        mvc.perform(post("/provider-access/finish-uncertain-departure").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("dispatchId",dispatch.toString()).param("confirmation","true").param("localFormToken",tokens.issue(session)))
                .andExpect(status().is3xxRedirection());
        verify(store).markDepartureFinished(eq(dispatch),any());
        verify(store,never()).rearm(anyLong(),any());
        verify(store,never()).tryReserveDeparture(any(),any());
        verify(supervisor,never()).stopCampaign(any(),any());
    }

    @Test void orphanedManualGuardHasItsOwnExplicitLocalRecoveryWithoutProviderTraffic() throws Exception {
        UUID manualCampaign=UUID.randomUUID();
        Guard orphan=new Guard("CLEANUP_REQUIRED",manualCampaign,
                new Owner(UUID.randomUUID(), 987654L, Instant.parse("2026-09-13T09:00:00Z")),17,Instant.now());
        when(campaigns.orphanedManualCleanupGuard()).thenReturn(Optional.of(orphan));
        MockHttpSession session=new MockHttpSession();
        mvc.perform(get("/provider-access").session(session).header("Host",HOST)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Garde manuelle orpheline")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/provider-access/release-orphaned-manual-guard")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"generation\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("987654"))));
        clearInvocations(campaigns,store,supervisor,coordinator);
        mvc.perform(post("/provider-access/release-orphaned-manual-guard").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("campaignId",manualCampaign.toString()).param("generation","17").param("confirmation","true")
                .param("localFormToken",tokens.issue(session))).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider-access"));
        verify(campaigns).finalizeOrphanedManualCleanup(manualCampaign,17L);
        verify(store,never()).rearm(anyLong(),any());
        verify(store,never()).markDepartureFinished(any(),any());
        verify(store,never()).tryReserveDeparture(any(),any());
        verify(supervisor,never()).stopCampaign(any(),any());
        verifyNoInteractions(coordinator);
    }

    @Test void orphanedManualGuardRecoveryRequiresLocalTokenConfirmationAndValidGeneration() throws Exception {
        UUID manualCampaign=UUID.randomUUID(); MockHttpSession session=new MockHttpSession();
        mvc.perform(post("/provider-access/release-orphaned-manual-guard").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("campaignId",manualCampaign.toString()).param("generation","1").param("confirmation","true"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/provider-access/release-orphaned-manual-guard").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("campaignId",manualCampaign.toString()).param("generation","1").param("confirmation","false")
                .param("localFormToken",tokens.issue(session))).andExpect(status().isBadRequest());
        mvc.perform(post("/provider-access/release-orphaned-manual-guard").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("campaignId",manualCampaign.toString()).param("generation","0").param("confirmation","true")
                .param("localFormToken",tokens.issue(session))).andExpect(status().isBadRequest());
        mvc.perform(post("/provider-access/release-orphaned-manual-guard").session(session).header("Host",HOST).header("Origin","https://foreign.example")
                .param("campaignId",manualCampaign.toString()).param("generation","1").param("confirmation","true")
                .param("localFormToken",tokens.issue(session))).andExpect(status().isForbidden());
        verify(campaigns,never()).finalizeOrphanedManualCleanup(any(),anyLong());
        verify(store,never()).rearm(anyLong(),any());
        verify(store,never()).markDepartureFinished(any(),any());
        verifyNoInteractions(coordinator,supervisor);
    }

    @Test void orphanedManualGuardProcessProofRefusalKeepsAllProviderControlsBlocked() throws Exception {
        UUID manualCampaign=UUID.randomUUID();
        doThrow(new IllegalStateException("LIVE_CLEANUP_PROCESS_UNVERIFIED"))
                .when(campaigns).finalizeOrphanedManualCleanup(manualCampaign,17L);
        MockHttpSession session=new MockHttpSession();
        mvc.perform(post("/provider-access/release-orphaned-manual-guard").session(session).header("Host",HOST).header("Origin",ORIGIN)
                .param("campaignId",manualCampaign.toString()).param("generation","17").param("confirmation","true")
                .param("localFormToken",tokens.issue(session))).andExpect(status().isConflict())
                .andExpect(view().name("live-campaign-error"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ne peut pas encore être prouvé localement")));
        verify(campaigns).finalizeOrphanedManualCleanup(manualCampaign,17L);
        verify(store,never()).rearm(anyLong(),any());
        verify(store,never()).markDepartureFinished(any(),any());
        verify(store,never()).tryReserveDeparture(any(),any());
        verifyNoInteractions(coordinator,supervisor);
    }

    @Test void crossOriginCannotReachRearmEvenWithAnOtherwiseValidToken() throws Exception {
        MockHttpSession session=new MockHttpSession();
        mvc.perform(post("/provider-access/rearm").session(session).header("Host",HOST).header("Origin","https://foreign.example")
                .param("version","7").param("confirmation","true").param("localFormToken",tokens.issue(session)))
                .andExpect(status().isForbidden());
        verify(store,never()).rearm(anyLong(),any());
        verifyNoInteractions(coordinator,supervisor);
    }

    @Test void nullOriginCannotReachRearmEvenWithAnOtherwiseValidToken() throws Exception {
        MockHttpSession session=new MockHttpSession();
        mvc.perform(post("/provider-access/rearm").session(session).header("Host",HOST).header("Origin","null")
                .param("version","7").param("confirmation","true").param("localFormToken",tokens.issue(session)))
                .andExpect(status().isForbidden());
        verify(store,never()).rearm(anyLong(),any());
        verifyNoInteractions(coordinator,supervisor);
    }

    private static Snapshot snapshot(UUID unresolved) {
        Instant now=Instant.now().minusSeconds(5);
        return new Snapshot(State.SUSPENDED,7,now,403,now,null,null,UUID.randomUUID(),UUID.randomUUID(),null,unresolved);
    }
}
