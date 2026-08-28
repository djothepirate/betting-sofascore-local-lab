package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import com.bettingproject.sofascorelocal.application.network.J3TournamentCatalogService;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryControlService;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryResult;
import com.bettingproject.sofascorelocal.application.event.TournamentDiscoveredEventView;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotInspectionCatalog;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspectionService;
import com.bettingproject.sofascorelocal.application.retention.J6RawPayloadRetentionService;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionPreview;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryState;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalog;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentEventCountStatus;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
    private J3TournamentCatalogService tournamentCatalogService;

    @MockitoBean
    private TournamentEventDiscoveryControlService tournamentDiscoveryControlService;

    @MockitoBean
    private LocalFormTokenService formTokenService;

    @MockitoBean
    private RawSnapshotJsonInspectionService snapshotInspectionService;

    @MockitoBean
    private J6RawPayloadRetentionService retentionService;

    @BeforeEach
    void snapshotInspectionIsUnavailableByDefault() {
        when(tournamentCatalogService.latest()).thenReturn(J3TournamentCatalog.unavailable(
                J3TournamentCatalogStatus.NO_COLLECTION_EVIDENCE,
                Optional.empty()));
        when(tournamentDiscoveryControlService.snapshot()).thenReturn(
                new TournamentEventDiscoveryControlSnapshot(
                        TournamentEventDiscoveryState.LOCKED,
                        Instant.parse("2026-08-18T12:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        List.of("TOURNAMENT_EVENT_DISCOVERY_DISABLED")));
        when(snapshotInspectionService.loadCatalog())
                .thenReturn(RawSnapshotInspectionCatalog.unavailable());
        when(retentionService.preview()).thenReturn(new J6RetentionPreview(
                30,
                Instant.parse("2026-08-18T12:00:00Z"),
                Instant.parse("2026-07-19T12:00:00Z"),
                0,
                0,
                Optional.empty(),
                List.of(),
                "a".repeat(64)));
    }

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
        RawSnapshotInspectionSummary inspectionSummary = new RawSnapshotInspectionSummary(
                41L,
                "SCHEDULED_EVENTS",
                "SCHEDULED_EVENTS|date=2026-08-14|page=1",
                Instant.parse("2026-08-14T09:31:23Z"),
                200,
                "application/json; charset=utf-8",
                39L,
                "a".repeat(64),
                "scheduled-events-v1",
                RawSnapshotSchemaStatus.PARSED);
        when(snapshotInspectionService.loadCatalog()).thenReturn(
                RawSnapshotInspectionCatalog.available(List.of(inspectionSummary)));
        J3TournamentCatalogOption tournamentOption = new J3TournamentCatalogOption(
                119_880,
                "UEFA Champions League, Playoff Round",
                "Europe",
                7,
                "UEFA Champions League",
                Map.of(7200, 2),
                List.of(41L));
        when(tournamentCatalogService.latest()).thenReturn(J3TournamentCatalog.available(
                LocalDate.of(2026, 8, 14),
                List.of(41L),
                List.of(tournamentOption),
                1));
        when(tournamentDiscoveryControlService.snapshot()).thenReturn(
                new TournamentEventDiscoveryControlSnapshot(
                        TournamentEventDiscoveryState.LOCKED,
                        Instant.parse("2026-08-18T12:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        true,
                        List.of()));
        UUID discoveredEventId = UUID.fromString(
                "c9cbad3e-b46b-55df-94f7-f55bd4f23999");
        TournamentEventDiscoveryResult discoveryResult =
                new TournamentEventDiscoveryResult(
                        UUID.fromString("28776548-1f0e-43cf-a277-cf048a919c21"),
                        true,
                        "COMPLETED",
                        0,
                        true,
                        81,
                        "b".repeat(64),
                        512,
                        TournamentEventCountStatus.COUNT_VERIFIED,
                        1,
                        1,
                        0,
                        0,
                        0,
                        1,
                        0,
                        0,
                        List.of(new TournamentDiscoveredEventView(
                                discoveredEventId,
                                16_707_704,
                                Instant.parse("2026-08-18T19:00:00Z"),
                                "Fenerbahçe",
                                "Olympique Lyonnais",
                                "notstarted")));

        mockMvc.perform(get("/dashboard")
                        .flashAttr("tournamentDiscoveryResult", discoveryResult))
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
                        "J3 / Inspection locale en lecture seule")))
                .andExpect(content().string(containsString(
                        "J6 / Rétention des payloads bruts")))
                .andExpect(content().string(containsString("AUCUNE PURGE WEB")))
                .andExpect(content().string(containsString(
                        "SCHEDULED_EVENTS|date=2026-08-14|page=1")))
                .andExpect(content().string(containsString("Inspecter le JSON")))
                .andExpect(content().string(containsString(
                        "Lancer la collecte fournisseur — BLOQUÉE")))
                .andExpect(content().string(containsString(
                        "Rencontres datées et accès direct à J5")))
                .andExpect(content().string(containsString("value=\"119880\"")))
                .andExpect(content().string(containsString(
                        "UEFA Champions League, Playoff Round - Europe")))
                .andExpect(content().string(containsString(
                        "tournament.category.name")))
                .andExpect(content().string(containsString("uniqueTournament.id")))
                .andExpect(content().string(containsString(
                        "Occurrences exclues (identité/portée/fuseau)")))
                .andExpect(content().string(containsString(
                        "timezoneEventCount")))
                .andExpect(content().string(containsString(
                        "seul corps JSON obtenu manuellement")))
                .andExpect(content().string(containsString(
                        "/events/" + discoveredEventId
                                + "/statistics?zone=Europe%2FParis")))
                .andExpect(content().string(containsString(
                        "Ouvrir J5 sans saisie d’ID")));

        UUID preparedRequestId = UUID.fromString(
                "c15b0969-03b2-47e4-a651-77eef7c4ed4d");
        when(tournamentDiscoveryControlService.snapshot()).thenReturn(
                new TournamentEventDiscoveryControlSnapshot(
                        TournamentEventDiscoveryState.AWAITING_CONFIRMATION,
                        Instant.parse("2026-08-18T12:00:00Z"),
                        preparedRequestId,
                        "CONFIRMER EVENEMENTS TOURNOI 119880 UNIQUE 7 DATE 2026-08-14 000042",
                        Instant.parse("2026-08-18T12:00:00Z"),
                        Instant.parse("2026-08-18T12:05:00Z"),
                        LocalDate.of(2026, 8, 14),
                        tournamentOption,
                        null,
                        false,
                        true,
                        List.of(
                                "PLAYWRIGHT_RUNTIME_DISABLED",
                                "PLAYWRIGHT_WORKER_ARTIFACT_INVALID")));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "action=\"/tournament-event-discovery/import-json\"")))
                .andExpect(content().string(not(containsString(
                        "action=\"/tournament-event-discovery/execute\""))))
                .andExpect(content().string(containsString(
                        "enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("name=\"jsonFile\"")))
                .andExpect(content().string(containsString(preparedRequestId.toString())))
                .andExpect(content().string(containsString(
                        "Importer, valider et relier à J5")));

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
                        "Option A — Collecte fournisseur directe")))
                .andExpect(content().string(containsString(
                        "action=\"/manual-call/import-json-pages\"")))
                .andExpect(content().string(containsString(
                        "enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString(
                        "name=\"pageFiles\"")))
                .andExpect(content().string(containsString(
                        "Option B — Import J3 paginé sans réseau")))
                .andExpect(content().string(containsString(
                        "Importer et valider J3 — ZÉRO APPEL")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(
                        "REPRISE PAGES 3-5"))));
    }
}
