package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventDetailResult;
import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J4EventSearchItem;
import com.bettingproject.sofascorelocal.application.event.J4EventSearchResult;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportResult;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J4ScheduledEventsSnapshotNormalizationService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
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
import java.time.LocalDate;
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

@WebMvcTest(EventExplorerController.class)
class EventExplorerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private J4EventQueryService queryService;

    @MockitoBean
    private J4OfflineFixtureImportService fixtureImportService;

    @MockitoBean
    private J4ScheduledEventsSnapshotNormalizationService normalizationService;

    @MockitoBean
    private LocalFormTokenService formTokenService;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void rendersDateSearchWithCanonicalIdentityAndNoStoreHeaders() throws Exception {
        var event = event();
        var search = new J4EventSearchResult(
                LocalDate.parse("2026-08-12"),
                ZoneId.of("Europe/Paris"),
                Instant.parse("2026-08-11T22:00:00Z"),
                Instant.parse("2026-08-12T22:00:00Z"),
                List.of(new J4EventSearchItem(
                        event,
                        event.startsAt().atZone(ZoneId.of("Europe/Paris")))));
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("one-use-token");
        when(queryService.search(LocalDate.parse("2026-08-12"), "Europe/Paris"))
                .thenReturn(search);

        mockMvc.perform(get("/events")
                        .param("date", "2026-08-12")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(view().name("events"))
                .andExpect(model().attribute("search", search))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().string(containsString("Synthetic Home FC")))
                .andExpect(content().string(containsString(event.identity().value().toString())))
                .andExpect(content().string(containsString("AUCUN TRANSPORT FOURNISSEUR")));
    }

    @Test
    void rendersCurrentEventAndExplicitlyMissingOfflineDetail() throws Exception {
        var event = event();
        var detail = new J4EventDetailResult(
                ZoneId.of("Europe/Paris"),
                new J4EventSearchItem(
                        event,
                        event.startsAt().atZone(ZoneId.of("Europe/Paris"))),
                List.of(new J4EventSearchItem(
                        event,
                        event.startsAt().atZone(ZoneId.of("Europe/Paris")))),
                Optional.empty());
        when(queryService.findDetail(event.identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(detail));

        mockMvc.perform(get("/events/{id}", event.identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(view().name("event-detail"))
                .andExpect(model().attribute("detail", detail))
                .andExpect(content().string(containsString("Aucun détail importé")))
                .andExpect(content().string(containsString("event-details-nominal")));
    }

    @Test
    void importsTheOfflineDemoOnlyAfterConsumingTheLocalFormToken() throws Exception {
        var event = event();
        when(fixtureImportService.importNominalCorpus()).thenReturn(
                new J4OfflineFixtureImportResult(
                        event.identity().value(),
                        event.startsAt(),
                        2L,
                        true,
                        true,
                        true));

        mockMvc.perform(post("/events/offline-demo")
                        .param("localFormToken", "one-use-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events?date=2026-08-12&zone=Europe%2FParis"));

        verify(formTokenService).consume(
                any(HttpSession.class),
                org.mockito.ArgumentMatchers.eq("one-use-token"));
        verify(fixtureImportService).importNominalCorpus();
    }

    private static CanonicalEventObservationView event() {
        return new CanonicalEventObservationView(
                1L,
                CanonicalEventIdentity.sofascore(900001L),
                Instant.parse("2026-08-12T14:00:00Z"),
                new ScheduledTeam(9101L, "Synthetic Home FC"),
                new ScheduledTeam(9202L, "Synthetic Away FC"),
                new ScheduledEventStatus("notstarted", Optional.of("Not started")),
                Optional.of(new ScheduledTournament(9303L, "Synthetic League")),
                EventSourceTrace.syntheticFixture(
                        "event-details-nominal",
                        "a".repeat(64),
                        "event-details-v1",
                        Instant.parse("2026-08-15T00:00:00Z")),
                "b".repeat(64),
                2L);
    }
}
