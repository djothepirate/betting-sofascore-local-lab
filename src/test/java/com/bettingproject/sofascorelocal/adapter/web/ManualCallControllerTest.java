package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlError;
import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlException;
import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.application.network.J3DynamicManualCallService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionResult;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManualCallController.class)
@Import(LocalFormTokenService.class)
class ManualCallControllerTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "d476ba08-abaa-451a-b4cc-cf077f3d6833");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalFormTokenService formTokenService;

    @MockitoBean
    private J3ManualCallControlService controlService;

    @MockitoBean
    private J3DynamicManualCallService dynamicManualCallService;

    @MockitoBean
    private J3ManualCollectionEvidenceService collectionEvidenceService;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void acceptsOneSessionBoundTokenOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        when(controlService.rearmAfterGlobalStop()).thenReturn(rearmedSnapshot());

        mockMvc.perform(post("/manual-call/rearm")
                        .session(session)
                        .param("localFormToken", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#manual-call-control"))
                .andExpect(flash().attribute("manualCallMessageKind", "safe"));

        mockMvc.perform(post("/manual-call/rearm")
                        .session(session)
                        .param("localFormToken", token))
                .andExpect(status().isBadRequest());

        verify(controlService, times(1)).rearmAfterGlobalStop();
    }

    @Test
    void forwardsTheExactConfirmationAndAcknowledgementWithoutTransport() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        String phrase = "CONFIRMER SCHEDULED_EVENTS 2026-08-12 PAGINATION DYNAMIQUE MAX 25 000042";
        when(controlService.confirm(REQUEST_ID, phrase, true))
                .thenReturn(rearmedSnapshot());

        mockMvc.perform(post("/manual-call/confirm")
                        .session(session)
                        .param("localFormToken", token)
                        .param("requestId", REQUEST_ID.toString())
                        .param("confirmationText", phrase)
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#manual-call-control"))
                .andExpect(flash().attribute("manualCallMessageKind", "safe"))
                .andExpect(flash().attribute(
                        "manualCallMessage",
                        "Confirmation enregistrée. Aucun transport n’a été exécuté ; le déclenchement fournisseur reste une action distincte."));

        verify(controlService).confirm(REQUEST_ID, phrase, true);
    }

    @Test
    void exposesOnlyASafeOperatorMessageWhenATransitionIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        when(controlService.activateByOperator())
                .thenThrow(new J3ManualCallControlException(
                        J3ManualCallControlError.GLOBAL_STOP_ACTIVE));

        mockMvc.perform(post("/manual-call/activate")
                        .session(session)
                        .param("localFormToken", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#manual-call-control"))
                .andExpect(flash().attribute("manualCallMessageKind", "danger"))
                .andExpect(flash().attribute(
                        "manualCallMessage",
                        "Levez d’abord l’arrêt global."));
    }

    @Test
    void forwardsTheSingleDateAndTheGlobalStopThroughDistinctSubmissions() throws Exception {
        LocalDate date = LocalDate.parse("2026-08-12");
        MockHttpSession prepareSession = new MockHttpSession();
        String prepareToken = formTokenService.issue(prepareSession);
        when(controlService.prepare(date)).thenReturn(rearmedSnapshot());

        mockMvc.perform(post("/manual-call/prepare")
                        .session(prepareSession)
                        .param("localFormToken", prepareToken)
                        .param("date", date.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#manual-call-control"));

        MockHttpSession stopSession = new MockHttpSession();
        String stopToken = formTokenService.issue(stopSession);
        when(controlService.stopGlobally()).thenReturn(rearmedSnapshot());

        mockMvc.perform(post("/manual-call/stop")
                        .session(stopSession)
                        .param("localFormToken", stopToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#manual-call-control"));

        verify(controlService).prepare(date);
        verify(controlService).stopGlobally();
    }

    @Test
    void executesTheDynamicCollectionOnlyThroughItsOwnSingleUseSubmission() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        when(dynamicManualCallService.execute(REQUEST_ID))
                .thenReturn(J3ManualCallExecutionResult.successful(5));

        mockMvc.perform(post("/manual-call/execute")
                        .session(session)
                        .param("localFormToken", token)
                        .param("requestId", REQUEST_ID.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#manual-call-control"))
                .andExpect(flash().attribute("manualCallMessageKind", "safe"))
                .andExpect(flash().attribute(
                        "manualCallMessage",
                        "Collecte terminée : 5 page(s) ont été collectées dans l’ordre jusqu’à hasNextPage=false. L’arrêt global a été réappliqué et la preuve minimisée est prête."));

        verify(dynamicManualCallService).execute(REQUEST_ID);
    }

    @Test
    void exposesOnlyASafeMessageWhenTheBatchFailsLocally() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        when(dynamicManualCallService.execute(REQUEST_ID))
                .thenThrow(new IllegalStateException("database diagnostic must stay hidden"));

        mockMvc.perform(post("/manual-call/execute")
                        .session(session)
                        .param("localFormToken", token)
                        .param("requestId", REQUEST_ID.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard#manual-call-control"))
                .andExpect(flash().attribute("manualCallMessageKind", "danger"))
                .andExpect(flash().attribute(
                        "manualCallMessage",
                        "La collecte fournisseur a été interrompue par une erreur locale sûre. L’arrêt global a été réappliqué et aucun retry n’a été lancé."));

        verify(dynamicManualCallService).execute(REQUEST_ID);
    }

    @Test
    void downloadsOnlyTheAlreadyMinimizedEvidenceDocument() throws Exception {
        var evidence = new com.bettingproject.sofascorelocal.domain.provider.J3MinimizedCollectionEvidence(
                LocalDate.parse("2026-08-13"),
                com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState.CANCELLED_BY_GLOBAL_STOP,
                0,
                0,
                1,
                "GLOBAL_STOP_OR_CIRCUIT_BLOCK",
                Instant.parse("2026-08-13T12:00:20Z"),
                true,
                J3CircuitState.LOCKED,
                J3CircuitReason.OPERATOR_STOP,
                List.of());
        var document = new J3ManualCollectionEvidenceService.EvidenceDocument(
                evidence,
                "RAW_PAYLOAD_INCLUDED=NO\nPROVIDER_URI_INCLUDED=NO\n",
                "J3-MINIMIZED-EVIDENCE-2026-08-13.txt");
        when(collectionEvidenceService.latestDocument())
                .thenReturn(java.util.Optional.of(document));

        mockMvc.perform(get("/manual-call/evidence"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"J3-MINIMIZED-EVIDENCE-2026-08-13.txt\""))
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string(
                        "RAW_PAYLOAD_INCLUDED=NO\nPROVIDER_URI_INCLUDED=NO\n"));
    }

    private static J3ManualCallControlSnapshot rearmedSnapshot() {
        return new J3ManualCallControlSnapshot(
                false,
                J3CircuitState.LOCKED,
                J3CircuitReason.STARTUP_LOCK,
                Instant.parse("2026-08-12T12:00:00Z"),
                null,
                LocalDate.parse("2026-08-12"),
                null,
                false,
                List.of("REAL_CALL_NOT_AUTHORIZED"));
    }
}
