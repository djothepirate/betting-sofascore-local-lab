package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ProviderCampaignStopException;
import com.bettingproject.sofascorelocal.application.network.J3ProviderCampaignStopService;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryControlError;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryControlException;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryControlService;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryResult;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryService;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryLocalImportClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoverySource;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentEventCountStatus;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TournamentEventDiscoveryController.class)
class TournamentEventDiscoveryControllerTest {

    private static final UUID REQUEST_ID =
            UUID.fromString("9db0beba-b2ff-4471-a016-ad1bdb7df5c3");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TournamentEventDiscoveryControlService controlService;

    @MockitoBean
    private TournamentEventDiscoveryService discoveryService;

    @MockitoBean
    private J3ProviderCampaignStopService providerCampaignStopService;

    @MockitoBean
    private LocalFormTokenService formTokenService;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void staleConfirmationFormsAreGoneWithoutExecutingAnything() throws Exception {
        for (String route : List.of("prepare", "execute", "import-json")) {
            mockMvc.perform(post("/tournament-event-discovery/" + route).header("Host", "localhost:8087"))
                    .andExpect(status().isGone());
        }
        org.mockito.Mockito.verifyNoInteractions(controlService, discoveryService);
    }
    @Test
    void oneClickExecutesTheServerResolvedDiscoveryClaim() throws Exception {
        TournamentEventDiscoveryExecutionClaim claim = claim();
        when(controlService.claimDirect(REQUEST_ID, LocalDate.parse("2026-09-13"), 119_880))
                .thenReturn(claim);
        TournamentEventDiscoveryResult result = new TournamentEventDiscoveryResult(
                REQUEST_ID,
                true,
                "COMPLETED",
                0,
                true,
                81,
                "a".repeat(64),
                42,
                TournamentEventCountStatus.COUNT_NOT_VERIFIABLE,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                1,
                List.of());
        when(discoveryService.execute(claim)).thenReturn(result);

        mockMvc.perform(multipart("/tournament-event-discovery/collect")
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token")
                        .param("collectionId", REQUEST_ID.toString())
                        .param("date", "2026-09-13")
                        .param("tournamentId", "119880"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard?j3Date=2026-09-13#tournament-event-discovery"))
                .andExpect(flash().attribute("tournamentDiscoveryResult", result))
                .andExpect(flash().attribute("tournamentDiscoveryMessageKind", "safe"));

        verify(discoveryService).execute(claim);
    }

    @Test
    void invalidSelectionNeverReachesTheDiscoveryService() throws Exception {
        when(controlService.claimDirect(REQUEST_ID, LocalDate.parse("2026-09-13"), 119_880))
                .thenThrow(new TournamentEventDiscoveryControlException(
                        TournamentEventDiscoveryControlError.TOURNAMENT_SELECTION_NOT_ALLOWED));

        mockMvc.perform(multipart("/tournament-event-discovery/collect")
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token")
                        .param("collectionId", REQUEST_ID.toString())
                        .param("date", "2026-09-13")
                        .param("tournamentId", "119880"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute(
                        "tournamentDiscoveryErrorCode",
                        "TOURNAMENT_SELECTION_NOT_ALLOWED"));

        verify(discoveryService, never()).execute(any());
    }

    @Test
    void oneClickImportsTheJsonResponseBodyWithoutProviderTransport() throws Exception {
        TournamentEventDiscoveryLocalImportClaim claim = localImportClaim();
        when(controlService.claimDirectLocalImport(REQUEST_ID, LocalDate.parse("2026-09-13"), 119_880))
                .thenReturn(claim);
        TournamentEventDiscoveryResult result = localImportResult();
        when(discoveryService.importLocalJson(
                org.mockito.ArgumentMatchers.eq(claim),
                any(RawPayloadEvidence.class)))
                .thenReturn(result);
        MockMultipartFile jsonFile = new MockMultipartFile(
                "jsonFile",
                "response.json",
                "application/json",
                "{\"events\":[]}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tournament-event-discovery/import")
                        .file(jsonFile)
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token")
                        .param("collectionId", REQUEST_ID.toString())
                        .param("date", "2026-09-13")
                        .param("tournamentId", "119880"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard?j3Date=2026-09-13#tournament-event-discovery"))
                .andExpect(flash().attribute("tournamentDiscoveryResult", result))
                .andExpect(flash().attribute("tournamentDiscoveryMessageKind", "safe"));

        verify(controlService).claimDirectLocalImport(
                REQUEST_ID, LocalDate.parse("2026-09-13"), 119_880);
        verify(discoveryService).importLocalJson(
                org.mockito.ArgumentMatchers.eq(claim),
                any(RawPayloadEvidence.class));
        verify(discoveryService, never()).execute(any());
    }

    @Test
    void rejectsAFileContainingCookieHeadersBeforeClaimingTheSelection() throws Exception {
        MockMultipartFile jsonFile = new MockMultipartFile(
                "jsonFile",
                "not-a-response-body.txt",
                "text/plain",
                "Cookie: session=value\n{\"events\":[]}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tournament-event-discovery/import")
                        .file(jsonFile)
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token")
                        .param("collectionId", REQUEST_ID.toString())
                        .param("date", "2026-09-13")
                        .param("tournamentId", "119880"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute(
                        "tournamentDiscoveryErrorCode",
                        "LOCAL_IMPORT_SENSITIVE_CONTENT"));

        verify(controlService, never()).claimDirectLocalImport(
                any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(discoveryService, never()).importLocalJson(any(), any());
        verify(discoveryService, never()).execute(any());
    }

    @Test
    void rejectsAnOversizedLocalImportBeforeClaimingTheSelection() throws Exception {
        MockMultipartFile jsonFile = new MockMultipartFile(
                "jsonFile",
                "response.json",
                "application/json",
                new byte[RawPayloadEvidence.MAXIMUM_BYTES + 1]);

        mockMvc.perform(multipart("/tournament-event-discovery/import")
                        .file(jsonFile)
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token")
                        .param("collectionId", REQUEST_ID.toString())
                        .param("date", "2026-09-13")
                        .param("tournamentId", "119880"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute(
                        "tournamentDiscoveryErrorCode",
                        "LOCAL_IMPORT_TOO_LARGE"));

        verify(controlService, never()).claimDirectLocalImport(
                any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(discoveryService, never()).importLocalJson(any(), any());
    }

    @Test
    void delegatesTheStopToTheWorkerFirstApplicationOrchestrator() throws Exception {
        mockMvc.perform(post("/tournament-event-discovery/stop")
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#tournament-event-discovery"))
                .andExpect(flash().attribute(
                        "tournamentDiscoveryMessageKind", "danger"));

        verify(providerCampaignStopService).stopTournamentDiscovery();
        verify(controlService, never()).stop();
    }

    @Test
    void repeatedClickCannotReuseTheConsumedLocalFormToken() throws Exception {
        var realTokens = new LocalFormTokenService();
        var session = new org.springframework.mock.web.MockHttpSession();
        String token = realTokens.issue(session);
        org.mockito.Mockito.doAnswer(invocation -> {
            realTokens.consume(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(formTokenService).consume(any(), any());
        when(controlService.claimDirect(REQUEST_ID, LocalDate.parse("2026-09-13"), 119_880))
                .thenReturn(claim());
        when(discoveryService.execute(claim())).thenReturn(localImportResult());

        mockMvc.perform(multipart("/tournament-event-discovery/collect").session(session)
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", token).param("collectionId", REQUEST_ID.toString())
                        .param("date", "2026-09-13").param("tournamentId", "119880"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(multipart("/tournament-event-discovery/collect").session(session)
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", token).param("collectionId", REQUEST_ID.toString())
                        .param("date", "2026-09-13").param("tournamentId", "119880"))
                .andExpect(status().isBadRequest());
        verify(discoveryService).execute(claim());
    }

    @Test
    void missingCollectionOrPhaseCannotTriggerACollection() throws Exception {
        mockMvc.perform(post("/tournament-event-discovery/collect")
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token").param("date", "2026-09-13")
                        .param("tournamentId", "119880"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/tournament-event-discovery/collect")
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token").param("date", "2026-09-13")
                        .param("collectionId", REQUEST_ID.toString()))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(controlService, discoveryService);
    }

    @Test
    void foreignOpaqueAndMismatchedOriginsAreRejectedBeforeReadingTheForm() throws Exception {
        for (String path : List.of("collect", "import")) {
            for (String origin : List.of("https://foreign.invalid", "null", "http://127.0.0.1:8087")) {
                mockMvc.perform(multipart("/tournament-event-discovery/" + path)
                                .header("Host", "localhost:8087").header("Origin", origin))
                        .andExpect(status().isForbidden());
            }
            mockMvc.perform(multipart("/tournament-event-discovery/" + path)
                            .header("Host", "foreign.invalid").header("Origin", "http://foreign.invalid"))
                    .andExpect(status().isForbidden());
        }
        org.mockito.Mockito.verifyNoInteractions(formTokenService, controlService, discoveryService);
    }

    @Test
    void emptyImportIsRejectedBeforeClaiming() throws Exception {
        mockMvc.perform(multipart("/tournament-event-discovery/import")
                        .file(new MockMultipartFile("jsonFile", new byte[0]))
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token").param("collectionId", REQUEST_ID.toString())
                        .param("date", "2026-09-13").param("tournamentId", "119880"))
                .andExpect(flash().attribute("tournamentDiscoveryErrorCode", "LOCAL_IMPORT_EMPTY"));
        org.mockito.Mockito.verifyNoInteractions(controlService, discoveryService);
    }

    @Test
    void unexpectedFailureLocksOnlyTheClaimOwnedByThisClick() throws Exception {
        when(controlService.claimDirect(REQUEST_ID, LocalDate.parse("2026-09-13"), 119_880))
                .thenReturn(claim());
        when(discoveryService.execute(claim())).thenThrow(new IllegalStateException("internal detail"));
        when(controlService.executionMayContinue(REQUEST_ID)).thenReturn(true);
        mockMvc.perform(post("/tournament-event-discovery/collect")
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token").param("collectionId", REQUEST_ID.toString())
                        .param("date", "2026-09-13").param("tournamentId", "119880"))
                .andExpect(flash().attribute("tournamentDiscoveryErrorCode", "LOCAL_EXECUTION_FAILURE"));
        verify(controlService).fail(REQUEST_ID, "LOCAL_EXECUTION_FAILURE");
    }

    @Test
    void exposesOnlyASafeMessageWhenWorkerStopCannotBeConfirmed() throws Exception {
        when(providerCampaignStopService.stopTournamentDiscovery())
                .thenThrow(new J3ProviderCampaignStopException(
                        new IllegalStateException("internal detail")));

        mockMvc.perform(post("/tournament-event-discovery/stop")
                        .header("Host", "localhost:8087").header("Origin", "http://localhost:8087").param("localFormToken", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#tournament-event-discovery"))
                .andExpect(flash().attribute(
                        "tournamentDiscoveryMessageKind", "danger"))
                .andExpect(flash().attribute(
                        "tournamentDiscoveryMessage",
                        "L’arrêt métier a été appliqué, mais le nettoyage du worker Playwright n’a pas pu être confirmé. Aucun nouvel appel n’est autorisé."));
    }

    private static TournamentEventDiscoveryResult localImportResult() {
        return new TournamentEventDiscoveryResult(
                REQUEST_ID,
                true,
                "COMPLETED",
                0,
                false,
                TournamentEventDiscoverySource.LOCAL_JSON_IMPORT,
                91,
                "b".repeat(64),
                13,
                TournamentEventCountStatus.COUNT_NOT_VERIFIABLE,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of());
    }

    private static TournamentEventDiscoveryExecutionClaim claim() {
        return new TournamentEventDiscoveryExecutionClaim(
                REQUEST_ID,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                LocalDate.of(2026, 8, 18),
                new J3TournamentCatalogOption(
                        119_880,
                        "UEFA Champions League, Playoff Round",
                        "Europe",
                        7,
                        "UEFA Champions League",
                        Map.of(),
                        List.of(41L)));
    }

    private static TournamentEventDiscoveryLocalImportClaim localImportClaim() {
        TournamentEventDiscoveryExecutionClaim providerClaim = claim();
        return new TournamentEventDiscoveryLocalImportClaim(
                providerClaim.requestId(),
                providerClaim.providerOrigin(),
                providerClaim.collectionDate(),
                providerClaim.selection());
    }
}
