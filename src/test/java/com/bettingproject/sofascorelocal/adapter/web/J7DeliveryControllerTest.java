package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryConfirmationRequest;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryExecutionGate;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryPayloadClass;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryQueryService;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryRuntimeService;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryView;
import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7DeliveryCandidate;
import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.application.export.J7ExportPreview;
import com.bettingproject.sofascorelocal.config.J7DeliveryWebMvcConfiguration;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.security.J7DeliveryConfirmationService;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(J7DeliveryController.class)
@Import({
        LocalFormTokenService.class,
        J7DeliveryConfirmationService.class,
        J7DeliveryExecutionGate.class,
        J7DeliveryWebMvcConfiguration.class
})
class J7DeliveryControllerTest {

    private static final UUID EVENT_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID EXPORT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID DELIVERY_ID =
            UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final String FILE_SHA256 = "a".repeat(64);
    private static final String DATA_SHA256 = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");
    private static final String LOCAL_HOST = "127.0.0.1:8087";
    private static final String LOCAL_ORIGIN = "http://127.0.0.1:8087";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalFormTokenService formTokenService;

    @Autowired
    private J7DeliveryExecutionGate executionGate;

    @MockitoBean
    private J7CanonicalExportService exportService;

    @MockitoBean
    private J7DeliveryQueryService queryService;

    @MockitoBean
    private J7DeliveryRuntimeService runtimeService;

    @MockitoBean
    private J7DeliveryLedgerStore ledgerStore;

    @MockitoBean
    private CacheManager cacheManager;

    private J7ExportPreview preview;

    @BeforeEach
    void setUp() {
        preview = preview();
        when(exportService.preview(EVENT_ID, EXPORT_ID)).thenReturn(preview);
        when(exportService.deliveryCandidate(EVENT_ID, EXPORT_ID)).thenReturn(
                new J7DeliveryCandidate(
                        EXPORT_ID,
                        EVENT_ID,
                        FILE_SHA256,
                        J7DeliveryPayloadClass.SYNTHETIC_ONLY));
    }

    @Test
    void prepareCreatesOnlyASessionBoundConfirmationWithoutClaimOrRuntime() throws Exception {
        when(queryService.view(preview)).thenReturn(
                deliveryView(Optional.empty(), true, false));
        MockHttpSession session = new MockHttpSession();

        MvcResult result = prepare(session, false)
                .andExpect(status().isOk())
                .andExpect(view().name("event-export-preview"))
                .andExpect(model().attribute("exportPreview", preview))
                .andExpect(model().attributeExists("deliveryConfirmationRequest"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate, max-age=0"))
                .andReturn();

        J7DeliveryConfirmationRequest request = confirmationRequest(result);
        assertThat(request.confirmationText())
                .isEqualTo("LIVRER J7 " + EXPORT_ID + " SHA256 " + FILE_SHA256);
        verify(runtimeService, never()).deliver(any(), any(), anyString(), anyInt());
        verify(ledgerStore, never()).claim(
                any(), anyString(), anyString(), anyString(), anyInt(), any());
        verify(exportService, never()).deliveryCandidate(EVENT_ID, EXPORT_ID);
    }

    @Test
    void hostileHostIsRefusedBeforeTheControllerAndDoesNotConsumeTheFormToken()
            throws Exception {
        when(queryService.view(preview)).thenReturn(
                deliveryView(Optional.empty(), true, false));
        MockHttpSession session = new MockHttpSession();
        String localFormToken = formTokenService.issue(session);

        mockMvc.perform(post(preparePath())
                        .header(HttpHeaders.HOST, "attacker.invalid:8087")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .session(session)
                        .param("localFormToken", localFormToken)
                        .param("unknownOutcomeReconciled", "false"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate, max-age=0"));

        mockMvc.perform(localPost(preparePath())
                        .session(session)
                        .param("localFormToken", localFormToken)
                        .param("unknownOutcomeReconciled", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("event-export-preview"));

        verify(exportService, times(1)).preview(EVENT_ID, EXPORT_ID);
    }

    @Test
    void exactConfirmationIsOneShotAndOnlyTheFirstExecutionReachesRuntime()
            throws Exception {
        when(queryService.view(preview)).thenReturn(
                deliveryView(Optional.empty(), true, false));
        MockHttpSession session = new MockHttpSession();
        MvcResult prepared = prepare(session, false)
                .andExpect(status().isOk())
                .andReturn();
        J7DeliveryConfirmationRequest request = confirmationRequest(prepared);
        String executeToken = modelString(prepared, "localFormToken");

        mockMvc.perform(localPost(executePath())
                        .session(session)
                        .param("localFormToken", executeToken)
                        .param("deliveryRequestId", request.requestId().toString())
                        .param("confirmationText", request.confirmationText())
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + EVENT_ID + "/exports/" + EXPORT_ID));

        verify(runtimeService).deliver(
                EVENT_ID, EXPORT_ID, request.confirmationText(), 1);

        String retryToken = formTokenService.issue(session);
        mockMvc.perform(localPost(executePath())
                        .session(session)
                        .param("localFormToken", retryToken)
                        .param("deliveryRequestId", request.requestId().toString())
                        .param("confirmationText", request.confirmationText())
                        .param("acknowledged", "true"))
                .andExpect(status().isConflict())
                .andExpect(view().name("event-delivery-error"))
                .andExpect(model().attribute(
                        "deliveryErrorCode",
                        "INVALID_OR_EXPIRED_CONFIRMATION"));

        verify(runtimeService).deliver(
                EVENT_ID, EXPORT_ID, request.confirmationText(), 1);
    }

    @Test
    void refusalBurnsTheConfirmationAndCannotBeCorrectedByReusingIt()
            throws Exception {
        when(queryService.view(preview)).thenReturn(
                deliveryView(Optional.empty(), true, false));
        MockHttpSession session = new MockHttpSession();
        MvcResult prepared = prepare(session, false)
                .andExpect(status().isOk())
                .andReturn();
        J7DeliveryConfirmationRequest request = confirmationRequest(prepared);

        mockMvc.perform(localPost(executePath())
                        .session(session)
                        .param("localFormToken", modelString(prepared, "localFormToken"))
                        .param("deliveryRequestId", request.requestId().toString())
                        .param("confirmationText", request.confirmationText()))
                .andExpect(status().isConflict())
                .andExpect(model().attribute(
                        "deliveryErrorCode",
                        "INVALID_OR_EXPIRED_CONFIRMATION"));

        mockMvc.perform(localPost(executePath())
                        .session(session)
                        .param("localFormToken", formTokenService.issue(session))
                        .param("deliveryRequestId", request.requestId().toString())
                        .param("confirmationText", request.confirmationText())
                        .param("acknowledged", "true"))
                .andExpect(status().isConflict())
                .andExpect(model().attribute(
                        "deliveryErrorCode",
                        "INVALID_OR_EXPIRED_CONFIRMATION"));

        verifyNoInteractions(runtimeService);
    }

    @Test
    void aConfirmationPreparedBeforeAnotherUnknownAttemptCannotStartTheNextOne()
            throws Exception {
        when(queryService.view(preview)).thenReturn(
                deliveryView(Optional.empty(), true, false));
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession staleSession = new MockHttpSession();
        MvcResult firstPrepared = prepare(firstSession, false)
                .andExpect(status().isOk())
                .andReturn();
        MvcResult stalePrepared = prepare(staleSession, false)
                .andExpect(status().isOk())
                .andReturn();
        J7DeliveryConfirmationRequest first = confirmationRequest(firstPrepared);
        J7DeliveryConfirmationRequest stale = confirmationRequest(stalePrepared);
        J7DeliveryLedgerStore.DeliverySnapshot unknown = snapshot(
                J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                1);
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256))
                .thenReturn(Optional.empty(), Optional.of(unknown));

        mockMvc.perform(localPost(executePath())
                        .session(firstSession)
                        .param("localFormToken", modelString(
                                firstPrepared, "localFormToken"))
                        .param("deliveryRequestId", first.requestId().toString())
                        .param("confirmationText", first.confirmationText())
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(localPost(executePath())
                        .session(staleSession)
                        .param("localFormToken", modelString(
                                stalePrepared, "localFormToken"))
                        .param("deliveryRequestId", stale.requestId().toString())
                        .param("confirmationText", stale.confirmationText())
                        .param("acknowledged", "true"))
                .andExpect(status().isConflict())
                .andExpect(model().attribute(
                        "deliveryErrorCode",
                        "INVALID_OR_EXPIRED_CONFIRMATION"));

        verify(runtimeService).deliver(
                EVENT_ID, EXPORT_ID, first.confirmationText(), 1);
    }

    @Test
    void unknownOutcomeNeedsAnExplicitHumanReconciliationAttestationBeforePreparation()
            throws Exception {
        J7DeliveryLedgerStore.DeliverySnapshot unknown = snapshot(
                J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                2);
        when(queryService.view(preview)).thenReturn(
                deliveryView(Optional.of(unknown), true, false));
        MockHttpSession session = new MockHttpSession();

        prepare(session, false)
                .andExpect(status().isConflict())
                .andExpect(view().name("event-delivery-error"))
                .andExpect(model().attribute(
                        "deliveryErrorCode",
                        "RECONCILIATION_NOT_ALLOWED"));

        MvcResult accepted = prepare(session, true)
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("deliveryConfirmationRequest"))
                .andReturn();
        assertThat(confirmationRequest(accepted).confirmationText())
                .contains(EXPORT_ID.toString(), FILE_SHA256);
        verifyNoInteractions(runtimeService);
    }

    @Test
    void staleInFlightReconciliationUsesOnlyLedgerIdentityAndNeverRuntime()
            throws Exception {
        J7DeliveryLedgerStore.DeliverySnapshot inFlight = snapshot(
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                3);
        when(exportService.preview(EVENT_ID, EXPORT_ID)).thenThrow(
                new J7ExportException(J7ExportError.FILE_TAMPERED));
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256))
                .thenReturn(Optional.of(inFlight));
        MockHttpSession session = new MockHttpSession();
        String prepareToken = formTokenService.issue(session);

        MvcResult prepared = mockMvc.perform(localPost(reconciliationPreparePath())
                        .session(session)
                        .param("localFormToken", prepareToken))
                .andExpect(status().isOk())
                .andExpect(view().name("event-delivery-reconciliation"))
                .andExpect(model().attributeDoesNotExist("exportPreview"))
                .andExpect(model().attribute("deliverySnapshot", inFlight))
                .andExpect(model().attributeExists("deliveryCandidate"))
                .andExpect(model().attributeExists("reconciliationConfirmationRequest"))
                .andExpect(content().string(containsString(
                        "Aucun fichier J7 n’a été ouvert")))
                .andReturn();
        J7DeliveryConfirmationRequest request = (J7DeliveryConfirmationRequest)
                prepared.getModelAndView().getModel()
                        .get("reconciliationConfirmationRequest");

        mockMvc.perform(localPost(reconciliationExecutePath())
                        .session(session)
                        .param("localFormToken", modelString(prepared, "localFormToken"))
                        .param("reconciliationRequestId", request.requestId().toString())
                        .param("confirmationText", request.confirmationText())
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + EVENT_ID + "/exports/" + EXPORT_ID));

        verify(ledgerStore).reconcileStaleInFlightAsUnknown(
                DELIVERY_ID, 3, NOW);
        verifyNoInteractions(queryService, runtimeService);
        verify(exportService, never()).preview(EVENT_ID, EXPORT_ID);
        verify(exportService, times(2)).deliveryCandidate(EVENT_ID, EXPORT_ID);
    }

    @Test
    void activeSenderBlocksReconciliationPreparationAndExecutionWithoutBurningChallenge()
            throws Exception {
        J7DeliveryLedgerStore.DeliverySnapshot inFlight = snapshot(
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                4);
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256))
                .thenReturn(Optional.of(inFlight));
        MockHttpSession session = new MockHttpSession();

        J7DeliveryExecutionGate.Lease activeSender = executionGate.acquire();
        try {
            mockMvc.perform(localPost(reconciliationPreparePath())
                            .session(session)
                            .param(
                                    "localFormToken",
                                    formTokenService.issue(session)))
                    .andExpect(status().isConflict())
                    .andExpect(view().name("event-delivery-error"))
                    .andExpect(model().attribute(
                            "deliveryErrorCode",
                            "DELIVERY_IN_PROGRESS"));
            verify(exportService, never()).deliveryCandidate(EVENT_ID, EXPORT_ID);
            verifyNoInteractions(queryService, runtimeService, ledgerStore);
        }
        finally {
            activeSender.close();
        }

        MvcResult prepared = mockMvc.perform(localPost(reconciliationPreparePath())
                        .session(session)
                        .param("localFormToken", formTokenService.issue(session)))
                .andExpect(status().isOk())
                .andExpect(view().name("event-delivery-reconciliation"))
                .andReturn();
        J7DeliveryConfirmationRequest request = (J7DeliveryConfirmationRequest)
                prepared.getModelAndView().getModel()
                        .get("reconciliationConfirmationRequest");

        activeSender = executionGate.acquire();
        try {
            mockMvc.perform(localPost(reconciliationExecutePath())
                            .session(session)
                            .param(
                                    "localFormToken",
                                    modelString(prepared, "localFormToken"))
                            .param(
                                    "reconciliationRequestId",
                                    request.requestId().toString())
                            .param("confirmationText", request.confirmationText())
                            .param("acknowledged", "true"))
                    .andExpect(status().isConflict())
                    .andExpect(view().name("event-delivery-error"))
                    .andExpect(model().attribute(
                            "deliveryErrorCode",
                            "DELIVERY_IN_PROGRESS"));
            verify(ledgerStore, never()).reconcileStaleInFlightAsUnknown(
                    any(), anyInt(), any());
        }
        finally {
            activeSender.close();
        }

        mockMvc.perform(localPost(reconciliationExecutePath())
                        .session(session)
                        .param("localFormToken", formTokenService.issue(session))
                        .param(
                                "reconciliationRequestId",
                                request.requestId().toString())
                        .param("confirmationText", request.confirmationText())
                        .param("acknowledged", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + EVENT_ID + "/exports/" + EXPORT_ID));

        verify(ledgerStore).reconcileStaleInFlightAsUnknown(
                DELIVERY_ID, 4, NOW);
        verify(exportService, times(2)).deliveryCandidate(EVENT_ID, EXPORT_ID);
        verifyNoInteractions(queryService, runtimeService);
    }

    private org.springframework.test.web.servlet.ResultActions prepare(
            MockHttpSession session,
            boolean unknownOutcomeReconciled) throws Exception {
        return mockMvc.perform(localPost(preparePath())
                .session(session)
                .param("localFormToken", formTokenService.issue(session))
                .param(
                        "unknownOutcomeReconciled",
                        Boolean.toString(unknownOutcomeReconciled)));
    }

    private static MockHttpServletRequestBuilder localPost(String path) {
        return localHeaders(post(path));
    }

    private static MockHttpServletRequestBuilder localHeaders(
            MockHttpServletRequestBuilder request) {
        return request
                .header(HttpHeaders.HOST, LOCAL_HOST)
                .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN);
    }

    private static J7DeliveryConfirmationRequest confirmationRequest(MvcResult result) {
        return (J7DeliveryConfirmationRequest) result.getModelAndView().getModel()
                .get("deliveryConfirmationRequest");
    }

    private static String modelString(MvcResult result, String name) {
        return (String) result.getModelAndView().getModel().get(name);
    }

    private static String preparePath() {
        return "/events/" + EVENT_ID + "/exports/" + EXPORT_ID
                + "/delivery/prepare";
    }

    private static String executePath() {
        return "/events/" + EVENT_ID + "/exports/" + EXPORT_ID
                + "/delivery/execute";
    }

    private static String reconciliationPreparePath() {
        return "/events/" + EVENT_ID + "/exports/" + EXPORT_ID
                + "/delivery/reconciliation/prepare";
    }

    private static String reconciliationExecutePath() {
        return "/events/" + EVENT_ID + "/exports/" + EXPORT_ID
                + "/delivery/reconciliation/execute";
    }

    private static J7ExportPreview preview() {
        J7ExportManifest manifest = new J7ExportManifest(
                1,
                EXPORT_ID,
                EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                NOW,
                DATA_SHA256,
                "c".repeat(64),
                "d".repeat(64),
                FILE_SHA256,
                1_024,
                "j7-synthetic.validated.json",
                "[]",
                List.of(),
                "[]",
                J7ExportStatus.HUMAN_VALIDATED,
                Optional.of(NOW),
                Optional.empty());
        return new J7ExportPreview(
                manifest,
                "{}",
                "VALIDER",
                "REJETER",
                J7DeliveryPayloadClass.SYNTHETIC_ONLY);
    }

    private static J7DeliveryView deliveryView(
            Optional<J7DeliveryLedgerStore.DeliverySnapshot> ledger,
            boolean preparationAllowed,
            boolean reconciliationAvailable) {
        return new J7DeliveryView(
                J7DeliveryPayloadClass.SYNTHETIC_ONLY,
                List.of(),
                ledger,
                preparationAllowed,
                reconciliationAvailable);
    }

    private static J7DeliveryLedgerStore.DeliverySnapshot snapshot(
            J7DeliveryLedgerStore.DeliveryState state,
            int attempts) {
        return new J7DeliveryLedgerStore.DeliverySnapshot(
                DELIVERY_ID,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                1_024,
                new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256).idempotencyKey(),
                "1.0",
                state,
                attempts,
                NOW,
                NOW);
    }
}
