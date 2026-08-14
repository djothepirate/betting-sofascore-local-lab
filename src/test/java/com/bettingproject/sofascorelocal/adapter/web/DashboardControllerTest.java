package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private J3ManualCallControlService manualCallControlService;

    @MockitoBean
    private J3ManualCollectionEvidenceService collectionEvidenceService;

    @MockitoBean
    private LocalFormTokenService formTokenService;

    @Test
    void rendersTheDashboardModel() throws Exception {
        DashboardView dashboardView = new DashboardView(
                "2026-08-08T00:00:00Z",
                "EXPERIMENTAL",
                "LOCKED_OFFLINE_J3_POLICY",
                false,
                false,
                "127.0.0.1:8087",
                "NON_CONFIGURED",
                1,
                "3 s",
                "AVAILABLE",
                "2",
                0L,
                0L,
                new DashboardView.FixtureCorpusView(
                        "AVAILABLE_OFFLINE",
                        "SCHEDULED_EVENTS",
                        "SYNTHETIC",
                        true,
                        "scheduled-events-v1",
                        12,
                        12,
                        7,
                        4,
                        1,
                        0),
                null,
                List.of());
        J3ManualCallControlSnapshot manualCallSnapshot = new J3ManualCallControlSnapshot(
                true,
                J3CircuitState.LOCKED,
                J3CircuitReason.STARTUP_LOCK,
                Instant.parse("2026-08-12T12:00:00Z"),
                null,
                LocalDate.parse("2026-08-12"),
                null,
                false,
                List.of(
                        "REAL_ENDPOINT_URI_ABSENT",
                        "REAL_CALL_NOT_AUTHORIZED",
                        "CONNECTOR_GATE_LOCKED",
                        "CATALOG_NOT_CALLABLE",
                        "LIVE_PROFILE_BLOCKED"));
        ManualCallControlView manualCallView = ManualCallControlView.from(manualCallSnapshot);
        when(dashboardService.load()).thenReturn(dashboardView);
        when(manualCallControlService.snapshot()).thenReturn(manualCallSnapshot);
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("local-form-token");

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("dashboard", dashboardView))
                .andExpect(model().attribute("manualCall", manualCallView))
                .andExpect(model().attribute("localFormToken", "local-form-token"))
                .andExpect(content().string(containsString("LOCKED_OFFLINE_J3_POLICY")))
                .andExpect(content().string(containsString("12 / 12 disponibles")))
                .andExpect(content().string(containsString("scheduled-events-v1")))
                .andExpect(content().string(containsString("VALIDÉ")))
                .andExpect(content().string(containsString("ARRÊT GLOBAL ACTIF")))
                .andExpect(content().string(containsString("REAL_CALL_NOT_AUTHORIZED")))
                .andExpect(content().string(containsString(
                        "Lancer la collecte fournisseur — BLOQUÉE")));
    }

    @Test
    void rendersTheConfirmedDynamicManualCollectionAction() throws Exception {
        DashboardView dashboardView = new DashboardView(
                "2026-08-14T00:00:00Z",
                "EXPERIMENTAL",
                "LOCKED_OFFLINE_J3_POLICY",
                true,
                true,
                "127.0.0.1:8087",
                "CONFIGURED",
                1,
                "3 s",
                "AVAILABLE",
                "2",
                2L,
                0L,
                new DashboardView.FixtureCorpusView(
                        "AVAILABLE_OFFLINE",
                        "SCHEDULED_EVENTS",
                        "SYNTHETIC",
                        true,
                        "scheduled-events-v1",
                        12,
                        12,
                        7,
                        4,
                        1,
                        0),
                null,
                List.of());
        Instant preparedAt = Instant.parse("2026-08-14T00:00:00Z");
        J3ManualCallIntentSnapshot intent = new J3ManualCallIntentSnapshot(
                UUID.fromString("3ccfd0a0-7825-4bfa-977b-358be086b1e2"),
                LocalDate.parse("2026-08-13"),
                "SCHEDULED_EVENTS|date=2026-08-13|pagination=has-next-page|max=25",
                1,
                J3ManualCallIntentState.CONFIRMED_READY,
                null,
                preparedAt,
                preparedAt.plusSeconds(300),
                preparedAt.plusSeconds(30),
                0,
                null,
                null);
        J3ManualCallControlSnapshot manualCallSnapshot = new J3ManualCallControlSnapshot(
                false,
                J3CircuitState.CLOSED,
                J3CircuitReason.NONE,
                preparedAt,
                null,
                LocalDate.parse("2026-08-13"),
                intent,
                true,
                List.of());
        when(dashboardService.load()).thenReturn(dashboardView);
        when(manualCallControlService.snapshot()).thenReturn(manualCallSnapshot);
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("local-form-token");

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "COLLECTE MANUELLE DYNAMIQUE PRÊTE")))
                .andExpect(content().string(containsString(
                        "SCHEDULED_EVENTS|date=2026-08-13|pagination=has-next-page|max=25")))
                .andExpect(content().string(containsString(
                        "Lancer la collecte manuelle paginée — PAGE 1 À N")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(
                        "REPRISE PAGES 3-5"))));
    }
}
