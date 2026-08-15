package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventDetailResult;
import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J4EventSearchItem;
import com.bettingproject.sofascorelocal.application.event.J4EventSearchResult;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportResult;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J4ScheduledEventsSnapshotNormalizationService;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsPhase1Service;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsPhase1Result;
import com.bettingproject.sofascorelocal.application.network.J4RealPhase1ControlService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1ControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1State;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1ExecutionClaim;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private J4RealPhase1ControlService realPhase1ControlService;

    @MockitoBean
    private J4RealEventDetailsPhase1Service realPhase1Service;

    @MockitoBean
    private LocalFormTokenService formTokenService;

    @MockitoBean
    private CacheManager cacheManager;

    @BeforeEach
    void exposeLockedRealPhaseOneControl() {
        when(realPhase1ControlService.snapshot()).thenReturn(
                new J4RealPhase1ControlSnapshot(
                        J4RealPhase1State.LOCKED,
                        Instant.parse("2026-08-15T00:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        0,
                        null,
                        false,
                        List.of("J4_EVENT_DETAILS_QUALIFICATION_DISABLED")));
    }

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
                .andExpect(content().string(containsString("LECTURE LOCALE")))
                .andExpect(content().string(containsString("16386245")))
                .andExpect(content().string(containsString("NON AUTORISÉ")));
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

    @Test
    void preparesTheFixedRealCampaignWithoutAcceptingAnEventParameter() throws Exception {
        when(realPhase1ControlService.prepare()).thenReturn(
                new J4RealPhase1ControlSnapshot(
                        J4RealPhase1State.AWAITING_CONFIRMATION,
                        Instant.parse("2026-08-15T10:00:00Z"),
                        UUID.fromString("30000000-0000-0000-0000-000000000004"),
                        "CONFIRMER EVENT_DETAILS 16386245 16421052 000042",
                        Instant.parse("2026-08-15T10:00:00Z"),
                        Instant.parse("2026-08-15T10:05:00Z"),
                        0,
                        null,
                        true,
                        List.of()));

        mockMvc.perform(post("/events/real-phase1/prepare")
                        .param("localFormToken", "one-use-token")
                        .param("date", "2026-08-15")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events?date=2026-08-15&zone=Europe%2FParis"));

        verify(formTokenService).consume(
                any(HttpSession.class),
                org.mockito.ArgumentMatchers.eq("one-use-token"));
        verify(realPhase1ControlService).prepare();
    }

    @Test
    void executesOnlyThePreviouslyPreparedFixedCampaign() throws Exception {
        UUID requestId = UUID.fromString("30000000-0000-0000-0000-000000000004");
        var claim = new J4RealPhase1ExecutionClaim(
                requestId,
                URI.create("https://www.sofascore.com"));
        when(realPhase1ControlService.confirmAndClaim(
                requestId,
                "CONFIRMER EVENT_DETAILS 16386245 16421052 000042",
                true)).thenReturn(claim);
        when(realPhase1Service.execute(claim)).thenReturn(
                new J4RealEventDetailsPhase1Result(
                        requestId,
                        false,
                        "HTTP_429",
                        1,
                        0,
                        List.of()));

        mockMvc.perform(post("/events/real-phase1/execute")
                        .param("localFormToken", "one-use-token")
                        .param("requestId", requestId.toString())
                        .param("confirmationText",
                                "CONFIRMER EVENT_DETAILS 16386245 16421052 000042")
                        .param("acknowledged", "true")
                        .param("date", "2026-08-15")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events?date=2026-08-15&zone=Europe%2FParis"));

        verify(realPhase1Service).execute(claim);
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
