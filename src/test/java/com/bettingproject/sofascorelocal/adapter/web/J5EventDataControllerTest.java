package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventSearchItem;
import com.bettingproject.sofascorelocal.application.event.J5EventDataPage;
import com.bettingproject.sofascorelocal.application.event.J5EventDataQueryService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineImportResult;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportService;
import com.bettingproject.sofascorelocal.application.network.J5LocalUnavailableEvidence;
import com.bettingproject.sofascorelocal.application.network.J5ProviderCampaignStopService;
import com.bettingproject.sofascorelocal.application.network.J5RealCampaignResult;
import com.bettingproject.sofascorelocal.application.network.J5RealControlService;
import com.bettingproject.sofascorelocal.application.network.J5RealEndpointResult;
import com.bettingproject.sofascorelocal.application.network.J5RealEventDataService;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlState;
import com.bettingproject.sofascorelocal.domain.provider.J5RealExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.PlayerMatchStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.J5UnavailableFamily;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(J5EventDataController.class)
class J5EventDataControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    J5EventDataQueryService queryService;

    @MockitoBean
    J5OfflineFixtureImportService fixtureImportService;

    @MockitoBean
    J5RealControlService realControlService;

    @MockitoBean
    J5RealEventDataService realEventDataService;

    @MockitoBean
    J5LocalJsonImportService localJsonImportService;

    @MockitoBean
    J5ProviderCampaignStopService providerCampaignStopService;

    @MockitoBean
    LocalFormTokenService formTokenService;

    @MockitoBean
    LineupCountryOverlayResolver lineupCountries;

    @MockitoBean
    CacheManager cacheManager;

    @BeforeEach
    void defaultRealQualificationIsSafelyBlocked() {
        when(realControlService.snapshot()).thenReturn(new J5RealControlSnapshot(
                J5RealControlState.LOCKED,
                Instant.parse("2026-08-15T14:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                false,
                false,
                List.of("J5_EVENT_DATA_QUALIFICATION_DISABLED")));
    }

    @Test
    void rendersAllThreeFamiliesWithCompletenessAndNoStoreHeaders() throws Exception {
        J5EventDataPage page = pageWithData();
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        when(queryService.find(
                page.current().event().identity().value(),
                "Europe/Paris")).thenReturn(Optional.of(page));

        mockMvc.perform(get(
                        "/events/{id}/statistics",
                        page.current().event().identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(view().name("event-statistics"))
                .andExpect(model().attribute("page", page))
                .andExpect(model().attribute("incidentsView", IncidentPresentation.from(
                        (EventIncidents) page.data().incidents().orElseThrow().data(),
                        page.current().event().homeTeam().name(), page.current().event().awayTeam().name())))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().string(containsString("Possession du ballon")))
                .andExpect(content().string(containsString("data-statistics")))
                .andExpect(content().string(containsString("data-stat-period=\"ALL\"")))
                .andExpect(content().string(containsString("class=\"statistics-possession\"")))
                .andExpect(content().string(containsString("/js/statistics.js")))
                .andExpect(content().string(containsString("Synthetic Home Striker")))
                .andExpect(content().string(containsString("Synthetic Incoming Player")))
                .andExpect(content().string(containsString("Synthetic Outgoing Player")))
                .andExpect(content().string(containsString("Entrant")))
                .andExpect(content().string(containsString("Sortant")))
                .andExpect(content().string(containsString("Passeur")))
                .andExpect(content().string(containsString("Classe")))
                .andExpect(content().string(containsString("Motif")))
                .andExpect(content().string(containsString("Détail")))
                .andExpect(content().string(containsString("Ordre TAB")))
                .andExpect(content().string(containsString("Synthetic Goal Assistant")))
                .andExpect(content().string(containsString("Carton annulé")))
                .andExpect(content().string(containsString("Woodwork")))
                .andExpect(content().string(containsString(
                        "<td>—</td>\n            <td>penaltyShootout</td>")))
                .andExpect(content().string(containsString("yellow")))
                .andExpect(content().string(containsString("Argument")))
                .andExpect(content().string(containsString("4-3-3")))
                .andExpect(content().string(containsString("4-4-2")))
                .andExpect(content().string(containsString("Synthetic Away Defender")))
                .andExpect(model().attributeExists("lineupsView"))
                .andExpect(content().string(containsString("data-lineups-team=\"HOME\"")))
                .andExpect(content().string(containsString("data-lineups-team=\"AWAY\"")))
                .andExpect(content().string(containsString("data-lineups-section=\"starters\"")))
                .andExpect(content().string(containsString("data-lineups-section=\"substitutes\"")))
                .andExpect(content().string(containsString("lineups-player-position-accessible")))
                .andExpect(content().string(containsString("lineups-position-heading")))
                .andExpect(content().string(containsString("/css/lineups.css")))
                .andExpect(content().string(containsString("/js/lineups.js")))
                .andExpect(content().string(containsString("PROVIDER_SCHEMA_VALIDATED=NO")))
                .andExpect(content().string(containsString("Trois familles, une confirmation, aucun retry")))
                .andExpect(content().string(containsString("J5_EVENT_DATA_QUALIFICATION_DISABLED")))
                .andExpect(content().string(containsString("COMPLETE · 100%")))
                .andExpect(content().string(containsString("PARTIAL · 0%")))
                .andExpect(content().string(not(containsString("$.incidents["))))
                .andExpect(content().string(not(containsString("$.home.players["))))
                .andExpect(content().string(containsString("b".repeat(64))))
                .andExpect(content().string(containsString("c".repeat(64))))
                .andExpect(content().string(containsString("d".repeat(64))));
    }

    @Test
    void rendersAVerifiedHistoricalLineupCountryOverlayWithoutChangingTheStoredLineup() throws Exception {
        J5EventDataPage page = pageWithData();
        var lineups = page.data().lineups().orElseThrow();
        var france = new com.bettingproject.sofascorelocal.domain.event.ProviderCountry(
                Optional.of("France"), Optional.of("FR"));
        var overlay = LineupCountryOverlay.of(java.util.Map.of(
                LineupCountryOverlay.PlayerKey.roster(LineupSide.HOME, 9701L), france));
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        when(queryService.find(page.current().event().identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(page));
        when(lineupCountries.resolve(lineups)).thenReturn(overlay);

        mockMvc.perform(get("/events/{id}/statistics", page.current().event().identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Synthetic Home Striker")))
                .andExpect(content().string(containsString("France")))
                .andExpect(content().string(containsString("/images/flags/4x3/fr.svg")))
                .andExpect(content().string(containsString("country-fallback-label")))
                .andExpect(content().string(containsString("country-accessible-prefix")))
                .andExpect(content().string(containsString("data-lineups-country-label-text")));

        assertThat(((EventLineups) lineups.data()).home().players().getFirst().country()).isEmpty();
        verify(lineupCountries).resolve(lineups);
    }

    @Test
    void usesReadableIncidentsToCompleteCardsWhenStatisticsAndLineupMetricsArePresent() throws Exception {
        J4EventSearchItem current = currentEvent();
        var statistics = new J5EventDataObservationView(41L, current.event().identity(),
                new EventStatistics(900001L, List.of()),
                source("statistics-readable", "event-statistics-v1"), J5CompletenessReport.measured(1, 1, List.of()),
                "a".repeat(64));
        var incidents = new J5EventDataObservationView(42L, current.event().identity(), new EventIncidents(900001L,
                List.of(new EventIncident(0, "goal", 18, Optional.empty(), Optional.of(true), Optional.empty(),
                        Optional.of(9701L), Optional.of("Observed scorer"), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("regular"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(9702L),
                        Optional.of("Observed assistant"), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty()),
                        new EventIncident(1, "substitution", 83, Optional.empty(), Optional.of(true), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.of(9704L), Optional.of("Observed incoming"),
                                Optional.of(9703L), Optional.of("Observed outgoing"), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty()),
                        new EventIncident(2, "card", 58, Optional.empty(), Optional.of(true), Optional.empty(),
                                Optional.of(9701L), Optional.of("Observed scorer"), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("yellow"),
                                Optional.empty()),
                        new EventIncident(3, "card", 59, Optional.empty(), Optional.of(false), Optional.empty(),
                                Optional.of(9705L), Optional.of("Observed away defender"), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("red"),
                                Optional.empty()))),
                source("incidents-readable", "event-incidents-v1"), J5CompletenessReport.measured(1, 1, List.of()),
                "b".repeat(64));
        var lineups = new J5EventDataObservationView(43L, current.event().identity(), new EventLineups(900001L, true,
                new TeamLineup(LineupSide.HOME, Optional.of("4-3-3"), List.of(
                        new EventLineupPlayer(9701L, "Observed scorer", Optional.empty(), Optional.of("F"), true,
                                Optional.empty(), Optional.of(new PlayerMatchStatistics(
                                        Map.of("rating", new BigDecimal("7.1")), Map.of()))),
                        new EventLineupPlayer(9702L, "Observed assistant", Optional.empty(), Optional.of("F"), true),
                        new EventLineupPlayer(9703L, "Observed outgoing", Optional.empty(), Optional.of("M"), true,
                                Optional.empty(), Optional.of(new PlayerMatchStatistics(
                                        Map.of("rating", new BigDecimal("6.8")), Map.of()))),
                        new EventLineupPlayer(9704L, "Observed incoming", Optional.empty(), Optional.of("M"), false,
                                Optional.empty(), Optional.of(new PlayerMatchStatistics(
                                        Map.of("minutesPlayed", BigDecimal.ZERO), Map.of()))))),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of(
                        new EventLineupPlayer(9705L, "Observed away defender", Optional.empty(), Optional.of("D"), true,
                                Optional.empty(), Optional.of(new PlayerMatchStatistics(
                                        Map.of("rating", new BigDecimal("6.3")), Map.of())))))),
                source("lineups-readable", "event-lineups-v1"), J5CompletenessReport.measured(1, 1, List.of()),
                "c".repeat(64));
        var page = new J5EventDataPage(ZoneId.of("Europe/Paris"), current,
                new J5EventDataBundle(Optional.of(statistics), Optional.of(incidents), Optional.of(lineups)));
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        when(queryService.find(current.event().identity().value(), "Europe/Paris")).thenReturn(Optional.of(page));

        mockMvc.perform(get("/events/{id}/statistics", current.event().identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("lineupsView"))
                .andExpect(content().string(containsString("But · 18′")))
                .andExpect(content().string(containsString("Passe décisive · 18′")))
                .andExpect(content().string(not(containsString("Carton jaune · 58′"))))
                .andExpect(content().string(not(containsString("Carton rouge · 59′"))))
                .andExpect(content().string(not(containsString("Sortie observée · 83′"))))
                .andExpect(content().string(not(containsString("Entrée observée · 83′"))))
                .andExpect(content().string(containsString("data-lineups-incident-decoration=\"yellow-card\"")))
                .andExpect(content().string(containsString("data-lineups-incident-decoration=\"red-card\"")))
                .andExpect(content().string(containsString("data-lineups-incident-decoration=\"substitution-out\"")))
                .andExpect(content().string(containsString("data-lineups-incident-decoration=\"substitution-in\"")))
                .andExpect(content().string(containsString("lineups-incident-card-icon")))
                .andExpect(content().string(containsString("lineups-substitution-arrow\">↑")))
                .andExpect(content().string(containsString("lineups-substitution-arrow\">↓")))
                .andExpect(content().string(containsString("lineups-substitution-injury")))
                .andExpect(content().string(containsString("lineups-substitution-counterparty")))
                .andExpect(content().string(containsString("Observed outgoing")))
                .andExpect(content().string(containsString("Observed incoming")))
                .andExpect(content().string(containsString("· 83′")))
                .andExpect(content().string(not(containsString("class=\"lineups-incident-source\""))))
                .andExpect(content().string(containsString("data-lineups-incident-source=\"EVENT_INCIDENTS\"")))
                .andExpect(content().string(containsString("Statistiques des joueurs non disponibles pour ce match")))
                .andExpect(content().string(not(containsString("data-lineups-player-details"))))
                .andExpect(content().string(not(containsString("data-lineups-statistics-hint"))));
    }

    @Test
    void rendersExplicitAbsenceWithoutAttemptingAnyImport() throws Exception {
        J4EventSearchItem current = currentEvent();
        J5EventDataPage page = new J5EventDataPage(
                ZoneId.of("Europe/Paris"),
                current,
                J5EventDataBundle.empty());
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        when(queryService.find(current.event().identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(page));

        mockMvc.perform(get(
                        "/events/{id}/statistics",
                        current.event().identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucune statistique locale")))
                .andExpect(content().string(containsString("Aucun incident local")))
                .andExpect(content().string(containsString("Aucune composition locale")));
    }

    @Test
    void unavailableIncidentsDoNotBecomeAnEmptyGraphicalObservation() throws Exception {
        J4EventSearchItem current = currentEvent();
        var incidents = new J5EventDataObservationView(31L, current.event().identity(),
                J5UnavailableFamily.emptyObservation(SofascoreEndpointType.EVENT_INCIDENTS, 900001L),
                EventSourceTrace.providerSnapshot(31, "e".repeat(64), "event-incidents-unavailable-v1",
                        Instant.parse("2026-08-15T19:50:46Z")),
                J5CompletenessReport.unavailable(), "f".repeat(64));
        var page = new J5EventDataPage(ZoneId.of("Europe/Paris"), current,
                new J5EventDataBundle(Optional.empty(), Optional.of(incidents), Optional.empty()));
        when(queryService.find(current.event().identity().value(), "Europe/Paris")).thenReturn(Optional.of(page));
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        mockMvc.perform(get("/events/{id}/statistics", current.event().identity().value()))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("incidentsView"))
                .andExpect(model().attribute("incidents", incidents));
        verifyNoInteractions(realEventDataService, fixtureImportService, localJsonImportService);
    }

    @Test
    void unavailableLineupsNeverRenderAsProvisionalOrEmptyTeamCards() throws Exception {
        J4EventSearchItem current = currentEvent();
        var lineups = new J5EventDataObservationView(31L, current.event().identity(),
                J5UnavailableFamily.emptyObservation(SofascoreEndpointType.EVENT_LINEUPS, 900001L),
                EventSourceTrace.providerSnapshot(31, "e".repeat(64), "event-lineups-unavailable-v1",
                        Instant.parse("2026-08-15T19:50:46Z")),
                J5CompletenessReport.unavailable(), "f".repeat(64));
        var page = new J5EventDataPage(ZoneId.of("Europe/Paris"), current,
                new J5EventDataBundle(Optional.empty(), Optional.empty(), Optional.of(lineups)));
        when(queryService.find(current.event().identity().value(), "Europe/Paris")).thenReturn(Optional.of(page));
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        mockMvc.perform(get("/events/{id}/statistics", current.event().identity().value()))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("lineupsView"))
                .andExpect(content().string(containsString("Compositions indisponibles chez le fournisseur")))
                .andExpect(content().string(not(containsString("data-lineups-team"))))
                .andExpect(content().string(not(containsString("Composition provisoire"))));
        verifyNoInteractions(realEventDataService, fixtureImportService, localJsonImportService);
    }

    @Test
    void rendersTheThreeFileImportChoiceForAnAwaitingCampaign() throws Exception {
        J4EventSearchItem current = currentEvent();
        J5EventDataPage page = new J5EventDataPage(
                ZoneId.of("Europe/Paris"),
                current,
                J5EventDataBundle.empty());
        UUID requestId = UUID.fromString("82000000-0000-0000-0000-000000000008");
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        when(queryService.find(current.event().identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(page));
        when(realControlService.snapshot()).thenReturn(new J5RealControlSnapshot(
                J5RealControlState.AWAITING_CONFIRMATION,
                Instant.parse("2026-08-21T07:00:00Z"),
                requestId,
                "CONFIRMER J5 REAL 900001 STATISTICS INCIDENTS LINEUPS 123456",
                Instant.parse("2026-08-21T07:00:00Z"),
                Instant.parse("2026-08-21T07:05:00Z"),
                current.event().identity().value(),
                900001L,
                List.of(),
                null,
                false,
                true,
                List.of()));

        mockMvc.perform(get(
                        "/events/{id}/statistics",
                        current.event().identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Option B — Trois preuves locales, zéro appel")))
                .andExpect(content().string(containsString("name=\"statisticsFile\"")))
                .andExpect(content().string(containsString(
                        "name=\"statisticsUnavailable404\"")))
                .andExpect(content().string(containsString("name=\"incidentsFile\"")))
                .andExpect(content().string(containsString(
                        "name=\"incidentsUnavailable404\"")))
                .andExpect(content().string(containsString("name=\"lineupsFile\"")))
                .andExpect(content().string(containsString(
                        "name=\"lineupsUnavailable404\"")))
                .andExpect(content().string(containsString(
                        "https://www.sofascore.com/api/v1/event/900001/statistics")))
                .andExpect(content().string(containsString(
                        "https://www.sofascore.com/api/v1/event/900001/incidents")))
                .andExpect(content().string(containsString(
                        "https://www.sofascore.com/api/v1/event/900001/lineups")))
                .andExpect(content().string(containsString(
                        "Importer les trois familles — ZÉRO APPEL")));
    }

    @Test
    void rendersANewPreparationForAnotherEventAfterACompletedCampaign() throws Exception {
        J4EventSearchItem current = currentEvent();
        J5EventDataPage page = new J5EventDataPage(
                ZoneId.of("Europe/Paris"),
                current,
                J5EventDataBundle.empty());
        long completedEventId = 16391135L;
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        when(queryService.find(current.event().identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(page));
        when(realControlService.snapshot()).thenReturn(new J5RealControlSnapshot(
                J5RealControlState.COMPLETED_LOCKED,
                Instant.parse("2026-08-16T06:25:38Z"),
                UUID.fromString("60000000-0000-0000-0000-000000000006"),
                null,
                Instant.parse("2026-08-16T06:25:30Z"),
                null,
                CanonicalEventIdentity.sofascore(completedEventId).value(),
                completedEventId,
                J5RealControlService.ORDERED_ENDPOINTS,
                "COMPLETED",
                false,
                true,
                List.of()));

        mockMvc.perform(get(
                        "/events/{id}/statistics",
                        current.event().identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("COMPLETED_LOCKED")))
                .andExpect(content().string(containsString(
                        "une nouvelle préparation explicite peut créer")))
                .andExpect(content().string(containsString("value=\"900001\"")))
                .andExpect(content().string(not(containsString("disabled=\"disabled\""))))
                .andExpect(content().string(not(containsString("CONFIRMER J5 REAL"))));

        verifyNoInteractions(realEventDataService);
    }

    @Test
    void rendersACompletedCampaignAsLockedAfterTheGlobalStop() throws Exception {
        J4EventSearchItem current = currentEvent();
        J5EventDataPage page = new J5EventDataPage(
                ZoneId.of("Europe/Paris"),
                current,
                J5EventDataBundle.empty());
        long completedEventId = 16391135L;
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        when(queryService.find(current.event().identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(page));
        when(realControlService.snapshot()).thenReturn(new J5RealControlSnapshot(
                J5RealControlState.COMPLETED_LOCKED,
                Instant.parse("2026-08-16T06:25:38Z"),
                UUID.fromString("60000000-0000-0000-0000-000000000006"),
                null,
                Instant.parse("2026-08-16T06:25:30Z"),
                null,
                CanonicalEventIdentity.sofascore(completedEventId).value(),
                completedEventId,
                J5RealControlService.ORDERED_ENDPOINTS,
                "COMPLETED",
                true,
                true,
                List.of()));

        mockMvc.perform(get(
                        "/events/{id}/statistics",
                        current.event().identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("COMPLETED_LOCKED")))
                .andExpect(content().string(containsString(
                        "L’arrêt global est actif")))
                .andExpect(content().string(containsString("disabled=\"disabled\"")))
                .andExpect(content().string(not(containsString(
                        "une nouvelle préparation explicite peut créer"))));

        verifyNoInteractions(realEventDataService);
    }

    @Test
    void rendersAProvider404AsUnavailableRatherThanAsAnEmptyStatisticsList()
            throws Exception {
        J4EventSearchItem current = currentEvent();
        EventSourceTrace source = EventSourceTrace.providerSnapshot(
                30L,
                "e".repeat(64),
                J5UnavailableFamily.normalizerVersion(
                        SofascoreEndpointType.EVENT_STATISTICS),
                Instant.parse("2026-08-15T19:50:46Z"));
        var statistics = new J5EventDataObservationView(
                30L,
                current.event().identity(),
                new EventStatistics(900001L, List.of()),
                source,
                J5CompletenessReport.unavailable(),
                "f".repeat(64));
        J5EventDataPage page = new J5EventDataPage(
                ZoneId.of("Europe/Paris"),
                current,
                new J5EventDataBundle(
                        Optional.of(statistics),
                        Optional.empty(),
                        Optional.empty()));
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        when(queryService.find(current.event().identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(page));

        mockMvc.perform(get(
                        "/events/{id}/statistics",
                        current.event().identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Statistiques indisponibles chez le fournisseur")))
                .andExpect(content().string(containsString("UNAVAILABLE · N/A")))
                .andExpect(content().string(containsString(
                        "elle n’est ni une panne de transport ni une liste vide valide")))
                .andExpect(content().string(not(containsString("Aucune statistique locale"))))
                .andExpect(content().string(not(containsString("<th>Métrique</th>"))));
    }

    @Test
    void importsOnlyAfterConsumingTheLocalFormToken() throws Exception {
        J4EventSearchItem current = currentEvent();
        when(fixtureImportService.importNominalCorpus(current.event().identity().value()))
                .thenReturn(new J5OfflineImportResult(
                        current.event().identity().value(),
                        11L,
                        true,
                        J5CompletenessStatus.COMPLETE,
                        12L,
                        true,
                        J5CompletenessStatus.COMPLETE,
                        13L,
                        true,
                        J5CompletenessStatus.COMPLETE));

        mockMvc.perform(post(
                        "/events/{id}/statistics/offline-demo",
                        current.event().identity().value())
                        .param("localFormToken", "one-use-token")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + current.event().identity().value()
                                + "/statistics?zone=Europe%2FParis"));

        verify(formTokenService).consume(
                any(HttpSession.class),
                org.mockito.ArgumentMatchers.eq("one-use-token"));
        verify(fixtureImportService).importNominalCorpus(current.event().identity().value());
    }

    @Test
    void preparesTheDisplayedCanonicalIdentityOnlyAfterConsumingTheToken() throws Exception {
        J4EventSearchItem current = currentEvent();

        mockMvc.perform(post(
                        "/events/{id}/statistics/real/prepare",
                        current.event().identity().value())
                        .param("localFormToken", "one-use-token")
                        .param("eventId", "900001")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + current.event().identity().value()
                                + "/statistics?zone=Europe%2FParis"));

        verify(formTokenService).consume(any(HttpSession.class), eq("one-use-token"));
        verify(realControlService).prepare(current.event().identity().value(), 900001L);
        verifyNoInteractions(realEventDataService);
    }

    @Test
    void executesOnlyTheClaimProducedByTheProtectedConfirmation() throws Exception {
        J4EventSearchItem current = currentEvent();
        UUID requestId = UUID.fromString("80000000-0000-0000-0000-000000000008");
        J5RealExecutionClaim claim = new J5RealExecutionClaim(
                requestId,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                current.event().identity().value(),
                900001L);
        when(realControlService.confirmAndClaim(requestId, "exact phrase", true))
                .thenReturn(claim);
        when(realEventDataService.execute(
                claim, current.event().identity().value())).thenReturn(new J5RealCampaignResult(
                requestId,
                current.event().identity().value(),
                900001L,
                false,
                "HTTP_429",
                1,
                0,
                List.of()));

        mockMvc.perform(post(
                        "/events/{id}/statistics/real/execute",
                        current.event().identity().value())
                        .param("localFormToken", "one-use-token")
                        .param("requestId", requestId.toString())
                        .param("confirmationText", "exact phrase")
                        .param("acknowledged", "true")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("j5RealErrorCode", "HTTP_429"))
                .andExpect(redirectedUrl(
                        "/events/" + current.event().identity().value()
                                + "/statistics?zone=Europe%2FParis"));

        verify(formTokenService).consume(any(HttpSession.class), eq("one-use-token"));
        verify(realControlService).confirmAndClaim(requestId, "exact phrase", true);
        verify(realEventDataService).execute(claim, current.event().identity().value());
    }

    @Test
    void delegatesAConsumedClaimRouteMismatchToTheAuditedService() throws Exception {
        J4EventSearchItem current = currentEvent();
        UUID requestId = UUID.fromString("80000000-0000-0000-0000-000000000009");
        long claimedProviderEventId = 900002L;
        UUID claimedCanonicalEventId =
                CanonicalEventIdentity.sofascore(claimedProviderEventId).value();
        J5RealExecutionClaim claim = new J5RealExecutionClaim(
                requestId,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                claimedCanonicalEventId,
                claimedProviderEventId);
        J5RealCampaignResult mismatch = new J5RealCampaignResult(
                requestId,
                claimedCanonicalEventId,
                claimedProviderEventId,
                false,
                "EVENT_ID_MISMATCH",
                0,
                0,
                List.of());
        when(realControlService.confirmAndClaim(requestId, "exact phrase", true))
                .thenReturn(claim);
        when(realEventDataService.execute(
                claim, current.event().identity().value())).thenReturn(mismatch);

        mockMvc.perform(post(
                        "/events/{id}/statistics/real/execute",
                        current.event().identity().value())
                        .param("localFormToken", "one-use-token")
                        .param("requestId", requestId.toString())
                        .param("confirmationText", "exact phrase")
                        .param("acknowledged", "true")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("j5RealErrorCode", "EVENT_ID_MISMATCH"))
                .andExpect(redirectedUrl(
                        "/events/" + current.event().identity().value()
                                + "/statistics?zone=Europe%2FParis"));

        verify(realControlService).confirmAndClaim(requestId, "exact phrase", true);
        verify(realEventDataService).execute(claim, current.event().identity().value());
        verify(realControlService, never()).fail(any(), any());
    }

    @Test
    void importsTheThreeLocalJsonBodiesWithoutExecutingTheProviderService() throws Exception {
        J4EventSearchItem current = currentEvent();
        UUID requestId = UUID.fromString("81000000-0000-0000-0000-000000000008");
        String confirmation =
                "CONFIRMER J5 REAL 900001 STATISTICS INCIDENTS LINEUPS 123456";
        RawPayloadEvidence statistics = RawPayloadEvidence.capture(
                "{\"statistics\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RawPayloadEvidence incidents = RawPayloadEvidence.capture(
                "{\"incidents\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RawPayloadEvidence lineups = RawPayloadEvidence.capture(
                "{\"confirmed\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        J5RealCampaignResult result = localImportResult(
                requestId, current.event().identity().value());
        when(localJsonImportService.importCampaign(
                current.event().identity().value(),
                requestId,
                confirmation,
                true,
                statistics,
                incidents,
                lineups)).thenReturn(result);

        mockMvc.perform(multipart(
                        "/events/{id}/statistics/real/import-json",
                        current.event().identity().value())
                        .file(new MockMultipartFile(
                                "statisticsFile", "statistics.json", "application/json",
                                statistics.bytes()))
                        .file(new MockMultipartFile(
                                "incidentsFile", "incidents.json", "application/json",
                                incidents.bytes()))
                        .file(new MockMultipartFile(
                                "lineupsFile", "lineups.json", "application/json",
                                lineups.bytes()))
                        .param("localFormToken", "one-use-token")
                        .param("requestId", requestId.toString())
                        .param("confirmationText", confirmation)
                        .param("acknowledged", "true")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("j5RealResult", result))
                .andExpect(flash().attribute("j5RealMessageKind", "safe"))
                .andExpect(redirectedUrl(
                        "/events/" + current.event().identity().value()
                                + "/statistics?zone=Europe%2FParis"));

        verify(formTokenService).consume(any(HttpSession.class), eq("one-use-token"));
        verify(localJsonImportService).importCampaign(
                current.event().identity().value(),
                requestId,
                confirmation,
                true,
                statistics,
                incidents,
                lineups);
        verifyNoInteractions(realEventDataService);
    }

    @Test
    void replacesAnExplicitlyObserved404WithCanonicalLocalEvidence() throws Exception {
        J4EventSearchItem current = currentEvent();
        UUID requestId = UUID.fromString("81000000-0000-0000-0000-000000000009");
        String confirmation =
                "CONFIRMER J5 REAL 900001 STATISTICS INCIDENTS LINEUPS 654321";
        RawPayloadEvidence incidents = RawPayloadEvidence.capture(
                "{\"incidents\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RawPayloadEvidence lineups = RawPayloadEvidence.capture(
                "{\"confirmed\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        J5RealCampaignResult result = localImportResult(
                requestId, current.event().identity().value());
        when(localJsonImportService.importCampaign(
                current.event().identity().value(),
                requestId,
                confirmation,
                true,
                J5LocalUnavailableEvidence.declared404(),
                incidents,
                lineups)).thenReturn(result);

        mockMvc.perform(multipart(
                        "/events/{id}/statistics/real/import-json",
                        current.event().identity().value())
                        .file(new MockMultipartFile(
                                "incidentsFile", "incidents.json", "application/json",
                                incidents.bytes()))
                        .file(new MockMultipartFile(
                                "lineupsFile", "lineups.json", "application/json",
                                lineups.bytes()))
                        .param("localFormToken", "one-use-token")
                        .param("requestId", requestId.toString())
                        .param("confirmationText", confirmation)
                        .param("acknowledged", "true")
                        .param("statisticsUnavailable404", "true")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("j5RealResult", result))
                .andExpect(flash().attribute(
                        "j5RealMessage", containsString("1 déclaration(s) 404")))
                .andExpect(redirectedUrl(
                        "/events/" + current.event().identity().value()
                                + "/statistics?zone=Europe%2FParis"));

        verify(localJsonImportService).importCampaign(
                current.event().identity().value(),
                requestId,
                confirmation,
                true,
                J5LocalUnavailableEvidence.declared404(),
                incidents,
                lineups);
        verifyNoInteractions(realEventDataService);
    }

    @Test
    void rejectsAFileAndA404DeclarationForTheSameFamily() throws Exception {
        J4EventSearchItem current = currentEvent();

        mockMvc.perform(multipart(
                        "/events/{id}/statistics/real/import-json",
                        current.event().identity().value())
                        .file(new MockMultipartFile(
                                "statisticsFile", "statistics.json", "application/json",
                                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile(
                                "incidentsFile", "incidents.json", "application/json",
                                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile(
                                "lineupsFile", "lineups.json", "application/json",
                                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("localFormToken", "one-use-token")
                        .param("requestId", UUID.randomUUID().toString())
                        .param("confirmationText", "exact phrase")
                        .param("acknowledged", "true")
                        .param("statisticsUnavailable404", "true")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute(
                        "j5RealMessage", containsString("jamais les deux")));

        verifyNoInteractions(localJsonImportService, realEventDataService);
    }

    @Test
    void rejectsAMissingFamilyWithoutA404Declaration() throws Exception {
        J4EventSearchItem current = currentEvent();

        mockMvc.perform(multipart(
                        "/events/{id}/statistics/real/import-json",
                        current.event().identity().value())
                        .file(new MockMultipartFile(
                                "incidentsFile", "incidents.json", "application/json",
                                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile(
                                "lineupsFile", "lineups.json", "application/json",
                                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("localFormToken", "one-use-token")
                        .param("requestId", UUID.randomUUID().toString())
                        .param("confirmationText", "exact phrase")
                        .param("acknowledged", "true")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute(
                        "j5RealMessage", containsString("sauf si le 404 observé")));

        verifyNoInteractions(localJsonImportService, realEventDataService);
    }

    @Test
    void appliesTheJ5GlobalStopOnlyAfterConsumingTheToken() throws Exception {
        J4EventSearchItem current = currentEvent();

        mockMvc.perform(post(
                        "/events/{id}/statistics/real/stop",
                        current.event().identity().value())
                        .param("localFormToken", "one-use-token")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + current.event().identity().value()
                                + "/statistics?zone=Europe%2FParis"));

        verify(formTokenService).consume(any(HttpSession.class), eq("one-use-token"));
        verify(providerCampaignStopService).stopAll();
    }

    private static J5RealCampaignResult localImportResult(
            UUID requestId,
            UUID canonicalEventId) {
        List<J5RealEndpointResult> endpoints = List.of(
                localEndpoint(SofascoreEndpointType.EVENT_STATISTICS, 410L, 510L, 'a'),
                localEndpoint(SofascoreEndpointType.EVENT_INCIDENTS, 411L, 511L, 'b'),
                localEndpoint(SofascoreEndpointType.EVENT_LINEUPS, 412L, 512L, 'c'));
        return new J5RealCampaignResult(
                requestId,
                canonicalEventId,
                900001L,
                true,
                "COMPLETED",
                0,
                3,
                endpoints);
    }

    private static J5RealEndpointResult localEndpoint(
            SofascoreEndpointType endpointType,
            long snapshotId,
            long observationId,
            char hashCharacter) {
        return new J5RealEndpointResult(
                endpointType,
                snapshotId,
                String.valueOf(hashCharacter).repeat(64),
                24,
                observationId,
                true,
                J5CompletenessStatus.EMPTY_VALID,
                100,
                0);
    }

    private static J5EventDataPage pageWithData() {
        J4EventSearchItem current = currentEvent();
        EventSourceTrace statisticsSource = source(
                "event-statistics-nominal",
                "event-statistics-v1");
        EventSourceTrace incidentsSource = source(
                "event-incidents-nominal",
                "event-incidents-v1");
        EventSourceTrace lineupsSource = source(
                "event-lineups-nominal",
                "event-lineups-v1");
        var statistics = new J5EventDataObservationView(
                11L,
                current.event().identity(),
                new EventStatistics(
                        900001L,
                        List.of(new EventStatisticMetric(
                                "ALL",
                                "Match overview",
                                "ballPossession",
                                "Ball possession",
                                Optional.of("54%"),
                                Optional.of("46%")))),
                statisticsSource,
                J5CompletenessReport.measured(2, 2, List.of()),
                "b".repeat(64));
        var incidents = new J5EventDataObservationView(
                12L,
                current.event().identity(),
                new EventIncidents(
                        900001L,
                        List.of(
                                new EventIncident(
                                        0,
                                        "goal",
                                        18,
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(9101L),
                                        Optional.of(9701L),
                                        Optional.of("Synthetic Home Striker"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(1),
                                        Optional.of(0),
                                        Optional.of("regular"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(9705L),
                                        Optional.of("Synthetic Goal Assistant"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty()),
                                new EventIncident(
                                        1,
                                        "substitution",
                                        83,
                                        Optional.empty(),
                                        Optional.of(true),
                                        Optional.of(9101L),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(9702L),
                                        Optional.of("Synthetic Incoming Player"),
                                        Optional.of(9703L),
                                        Optional.of("Synthetic Outgoing Player"),
                                        Optional.empty(),
                                        Optional.empty()),
                                new EventIncident(
                                        2,
                                        "card",
                                        58,
                                        Optional.empty(),
                                        Optional.of(false),
                                        Optional.empty(),
                                        Optional.of(9704L),
                                        Optional.of("Provider Player"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of("yellow"),
                                        Optional.of("Argument"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(true),
                                        Optional.empty(),
                                        Optional.empty()),
                                new EventIncident(
                                        3,
                                        "penaltyShootout",
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(true),
                                        Optional.empty(),
                                        Optional.of(9706L),
                                        Optional.of("Synthetic Shootout Taker"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(5),
                                        Optional.of(4),
                                        Optional.of("missed"),
                                        Optional.of("woodwork"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of("Woodwork"),
                                        Optional.of(9)))),
                incidentsSource,
                J5CompletenessReport.measured(
                        0,
                        1,
                        List.of("$.incidents[0].isHome")),
                "c".repeat(64));
        var lineups = new J5EventDataObservationView(
                13L,
                current.event().identity(),
                new EventLineups(
                        900001L,
                        true,
                        new TeamLineup(
                                LineupSide.HOME,
                                Optional.of("4-3-3"),
                                List.of(new EventLineupPlayer(
                                        9701L,
                                        "Synthetic Home Striker",
                                        Optional.of(9),
                                        Optional.of("F"),
                                        true))),
                        new TeamLineup(
                                LineupSide.AWAY,
                                Optional.of("4-4-2"),
                                List.of(new EventLineupPlayer(
                                        9802L,
                                        "Synthetic Away Defender",
                                        Optional.of(4),
                                        Optional.of("D"),
                                        false)))),
                lineupsSource,
                J5CompletenessReport.measured(
                        8,
                        9,
                        List.of("$.home.players[12].jerseyNumber")),
                "d".repeat(64));
        return new J5EventDataPage(
                ZoneId.of("Europe/Paris"),
                current,
                new J5EventDataBundle(
                        Optional.of(statistics),
                        Optional.of(incidents),
                        Optional.of(lineups)));
    }

    private static J4EventSearchItem currentEvent() {
        var event = new CanonicalEventObservationView(
                1L,
                CanonicalEventIdentity.sofascore(900001L),
                Instant.parse("2026-08-12T14:00:00Z"),
                new ScheduledTeam(9101L, "Synthetic Home FC"),
                new ScheduledTeam(9202L, "Synthetic Away FC"),
                new ScheduledEventStatus("notstarted", Optional.of("Not started")),
                Optional.of(new ScheduledTournament(9303L, "Synthetic League")),
                source("event-details-nominal", "event-details-v1"),
                "a".repeat(64),
                1L);
        return new J4EventSearchItem(
                event,
                event.startsAt().atZone(ZoneId.of("Europe/Paris")));
    }

    private static EventSourceTrace source(String fixtureId, String parserVersion) {
        return EventSourceTrace.syntheticFixture(
                fixtureId,
                "a".repeat(64),
                parserVersion,
                Instant.parse("2026-08-15T12:30:00Z"));
    }
}
