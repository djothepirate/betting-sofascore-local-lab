package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventSearchItem;
import com.bettingproject.sofascorelocal.application.event.J5EventDataPage;
import com.bettingproject.sofascorelocal.application.event.J5EventDataQueryService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineImportResult;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    LocalFormTokenService formTokenService;

    @MockitoBean
    CacheManager cacheManager;

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
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().string(containsString("Ball possession")))
                .andExpect(content().string(containsString("Synthetic Home Striker")))
                .andExpect(content().string(containsString("4-3-3")))
                .andExpect(content().string(containsString("PROVIDER_SCHEMA_VALIDATED=NO")))
                .andExpect(content().string(containsString("COMPLETE · 100%")))
                .andExpect(content().string(containsString("PARTIAL · 0%")))
                .andExpect(content().string(containsString("$.incidents[0].isHome")))
                .andExpect(content().string(containsString("b".repeat(64))))
                .andExpect(content().string(containsString("c".repeat(64))))
                .andExpect(content().string(containsString("d".repeat(64))));
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
                        List.of(new EventIncident(
                                0,
                                "goal",
                                18,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(9101L),
                                Optional.of(9701L),
                                Optional.of("Synthetic Home Striker"),
                                Optional.of(1),
                                Optional.of(0)))),
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
                J5CompletenessReport.measured(9, 9, List.of()),
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
