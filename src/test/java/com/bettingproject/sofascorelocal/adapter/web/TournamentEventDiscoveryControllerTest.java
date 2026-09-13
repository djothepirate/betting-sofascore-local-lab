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
    void preparationPostsOnlyThePhaseIdentityAndDoesNotExecuteTransport() throws Exception {
        mockMvc.perform(post("/tournament-event-discovery/prepare")
                        .param("localFormToken", "token")
                        .param("tournamentId", "119880").param("collectionId",REQUEST_ID.toString()).param("date","2026-09-13"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard?j3Date=2026-09-13#tournament-event-discovery"))
                .andExpect(flash().attribute("tournamentDiscoveryMessageKind", "safe"));

        verify(formTokenService).consume(any(HttpSession.class), org.mockito.ArgumentMatchers.eq("token"));
        verify(controlService).prepare(REQUEST_ID,LocalDate.parse("2026-09-13"),119_880);
        verify(discoveryService, never()).execute(any());
    }

    @Test
    void exactConfirmationExecutesTheSingleDiscoveryClaim() throws Exception {
        TournamentEventDiscoveryExecutionClaim claim = claim();
        when(controlService.confirmAndClaim(REQUEST_ID, "phrase exacte", true))
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

        mockMvc.perform(post("/tournament-event-discovery/execute")
                        .param("localFormToken", "token")
                        .param("requestId", REQUEST_ID.toString())
                        .param("confirmationText", "phrase exacte")
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#tournament-event-discovery"))
                .andExpect(flash().attribute("tournamentDiscoveryResult", result))
                .andExpect(flash().attribute("tournamentDiscoveryMessageKind", "safe"));

        verify(discoveryService).execute(claim);
    }

    @Test
    void invalidConfirmationNeverReachesTheDiscoveryService() throws Exception {
        when(controlService.confirmAndClaim(REQUEST_ID, "wrong", true))
                .thenThrow(new TournamentEventDiscoveryControlException(
                        TournamentEventDiscoveryControlError.CONFIRMATION_TEXT_MISMATCH));

        mockMvc.perform(post("/tournament-event-discovery/execute")
                        .param("localFormToken", "token")
                        .param("requestId", REQUEST_ID.toString())
                        .param("confirmationText", "wrong")
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute(
                        "tournamentDiscoveryErrorCode",
                        "CONFIRMATION_TEXT_MISMATCH"));

        verify(discoveryService, never()).execute(any());
    }

    @Test
    void importsOnlyTheJsonResponseBodyAfterTheExistingExactConfirmation() throws Exception {
        TournamentEventDiscoveryLocalImportClaim claim = localImportClaim();
        when(controlService.confirmAndClaimLocalImport(REQUEST_ID, "phrase exacte", true))
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

        mockMvc.perform(multipart("/tournament-event-discovery/import-json")
                        .file(jsonFile)
                        .param("localFormToken", "token")
                        .param("requestId", REQUEST_ID.toString())
                        .param("confirmationText", "phrase exacte")
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#tournament-event-discovery"))
                .andExpect(flash().attribute("tournamentDiscoveryResult", result))
                .andExpect(flash().attribute("tournamentDiscoveryMessageKind", "safe"));

        verify(controlService).confirmAndClaimLocalImport(
                REQUEST_ID, "phrase exacte", true);
        verify(discoveryService).importLocalJson(
                org.mockito.ArgumentMatchers.eq(claim),
                any(RawPayloadEvidence.class));
        verify(discoveryService, never()).execute(any());
    }

    @Test
    void rejectsAFileContainingCookieHeadersBeforeConsumingTheConfirmation() throws Exception {
        MockMultipartFile jsonFile = new MockMultipartFile(
                "jsonFile",
                "not-a-response-body.txt",
                "text/plain",
                "Cookie: session=value\n{\"events\":[]}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tournament-event-discovery/import-json")
                        .file(jsonFile)
                        .param("localFormToken", "token")
                        .param("requestId", REQUEST_ID.toString())
                        .param("confirmationText", "phrase exacte")
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute(
                        "tournamentDiscoveryErrorCode",
                        "LOCAL_IMPORT_SENSITIVE_CONTENT"));

        verify(controlService, never()).confirmAndClaimLocalImport(
                any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(discoveryService, never()).importLocalJson(any(), any());
        verify(discoveryService, never()).execute(any());
    }

    @Test
    void rejectsAnOversizedLocalImportBeforeConsumingTheConfirmation() throws Exception {
        MockMultipartFile jsonFile = new MockMultipartFile(
                "jsonFile",
                "response.json",
                "application/json",
                new byte[RawPayloadEvidence.MAXIMUM_BYTES + 1]);

        mockMvc.perform(multipart("/tournament-event-discovery/import-json")
                        .file(jsonFile)
                        .param("localFormToken", "token")
                        .param("requestId", REQUEST_ID.toString())
                        .param("confirmationText", "phrase exacte")
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute(
                        "tournamentDiscoveryErrorCode",
                        "LOCAL_IMPORT_TOO_LARGE"));

        verify(controlService, never()).confirmAndClaimLocalImport(
                any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(discoveryService, never()).importLocalJson(any(), any());
    }

    @Test
    void delegatesTheStopToTheWorkerFirstApplicationOrchestrator() throws Exception {
        mockMvc.perform(post("/tournament-event-discovery/stop")
                        .param("localFormToken", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#tournament-event-discovery"))
                .andExpect(flash().attribute(
                        "tournamentDiscoveryMessageKind", "danger"));

        verify(providerCampaignStopService).stopTournamentDiscovery();
        verify(controlService, never()).stop();
    }

    @Test
    void exposesOnlyASafeMessageWhenWorkerStopCannotBeConfirmed() throws Exception {
        when(providerCampaignStopService.stopTournamentDiscovery())
                .thenThrow(new J3ProviderCampaignStopException(
                        new IllegalStateException("internal detail")));

        mockMvc.perform(post("/tournament-event-discovery/stop")
                        .param("localFormToken", "token"))
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
