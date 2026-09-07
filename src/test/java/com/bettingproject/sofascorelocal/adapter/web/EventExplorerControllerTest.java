package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.application.event.J4EventDetailResult;
import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J4EventSearchItem;
import com.bettingproject.sofascorelocal.application.event.J4EventSearchResult;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportResult;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J4ScheduledEventsSnapshotNormalizationService;
import com.bettingproject.sofascorelocal.application.event.J4SnapshotNormalizationResult;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.application.network.J4ProviderCampaignStopException;
import com.bettingproject.sofascorelocal.application.network.J4ProviderCampaignStopService;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsPhase1Service;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsPhase1Result;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsPhase2Result;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsPhase2Service;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsUnavailableResult;
import com.bettingproject.sofascorelocal.application.network.J4RealPhase1ControlService;
import com.bettingproject.sofascorelocal.application.network.J4RealPhase2ControlService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventSeason;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventVenue;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1ControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1State;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2ControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2State;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
    private J4RealPhase2ControlService realPhase2ControlService;

    @MockitoBean
    private J4RealEventDetailsPhase2Service realPhase2Service;

    @MockitoBean
    private J4ProviderCampaignStopService providerCampaignStopService;

    @MockitoBean
    private LocalFormTokenService formTokenService;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private LiveCampaignService liveCampaigns;

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
        when(realPhase2ControlService.snapshot()).thenReturn(
                new J4RealPhase2ControlSnapshot(
                        J4RealPhase2State.LOCKED,
                        Instant.parse("2026-08-15T00:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        false,
                        List.of("J4_EVENT_DETAILS_PHASE_2_DISABLED")));
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
                .andExpect(content().string(containsString(
                        "Une identité canonique, un appel confirmé")))
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    var searchForm = Pattern.compile(
                            "(?s)<form\\b[^>]*class=\"event-search-form\"[^>]*>.*?</form>").matcher(html);
                    assertThat(searchForm.find()).as("local date search form").isTrue();
                    assertThat(searchForm.group()).contains("method=\"get\"", "action=\"/events\"",
                                    "name=\"date\"", "name=\"zone\"")
                            .doesNotContain("name=\"eventId\"", "name=\"providerEventId\"",
                                    "name=\"canonicalEventId\"");

                    var liveForm = Pattern.compile(
                            "(?s)<form\\b[^>]*id=\"live-selection\"[^>]*>.*?</form>").matcher(html);
                    assertThat(liveForm.find()).as("dedicated live preparation form").isTrue();
                    assertThat(liveForm.group()).contains("method=\"post\"",
                            "action=\"/live-campaigns/prepare\"", "name=\"localFormToken\"");
                    var liveSelection = Pattern.compile(
                            "(?s)<input\\b[^>]*name=\"eventId\"[^>]*>").matcher(html);
                    assertThat(liveSelection.find()).as("canonical UUID selection for live preparation").isTrue();
                    assertThat(liveSelection.group()).contains("type=\"checkbox\"", "form=\"live-selection\"",
                                    "value=\"" + event.identity().value() + "\"", "disabled=\"disabled\"")
                            .doesNotContain("value=\"" + event.identity().providerEventId() + "\"");
                    assertThat(liveSelection.find()).as("one live control for the one fixture event").isFalse();
                })
                .andExpect(content().string(containsString("name=\"canonicalEventId\"")))
                .andExpect(content().string(containsString(
                        "J4_EVENT_DETAILS_PHASE_2_DISABLED")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void providerSelectionReflectsCampaignLockInTheInitialHtml(boolean blocked) throws Exception {
        var event = providerEvent();
        var date = event.startsAt().atZone(ZoneId.of("Europe/Paris")).toLocalDate();
        var search = new J4EventSearchResult(date, ZoneId.of("Europe/Paris"),
                date.atStartOfDay(ZoneId.of("Europe/Paris")).toInstant(),
                date.plusDays(1).atStartOfDay(ZoneId.of("Europe/Paris")).toInstant(),
                List.of(new J4EventSearchItem(event, event.startsAt().atZone(ZoneId.of("Europe/Paris")))));
        when(queryService.search(date, "Europe/Paris")).thenReturn(search);
        when(liveCampaigns.selectionBlockedEvents(List.of(event.identity().value())))
                .thenReturn(blocked ? Set.of(event.identity().value()) : Set.of());
        var response = mockMvc.perform(get("/events").param("date", date.toString()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var input = Pattern.compile("<input\\b[^>]*name=\"eventId\"[^>]*>").matcher(response.getContentAsString());
        assertThat(input.find()).isTrue();
        assertThat(input.group()).contains("data-live-provider-eligible=\"true\"");
        assertThat(input.group().contains("disabled=\"disabled\"")).isEqualTo(blocked);
    }

    @Test
    void explainsThatAScheduledTournamentSnapshotContainsCompetitionsButNoMatches() throws Exception {
        var event = event();
        var search = new J4EventSearchResult(
                LocalDate.parse("2026-08-19"),
                ZoneId.of("Europe/Paris"),
                Instant.parse("2026-08-18T22:00:00Z"),
                Instant.parse("2026-08-19T22:00:00Z"),
                List.of(new J4EventSearchItem(
                        event,
                        event.startsAt().atZone(ZoneId.of("Europe/Paris")))));
        var normalization = new J4SnapshotNormalizationResult(
                308L,
                RawSnapshotSchemaStatus.PARSED,
                ScheduledEventsParseStatus.PARSED,
                "SCHEDULED_TOURNAMENT_LIST",
                0,
                2,
                0,
                0,
                List.of());
        when(queryService.search(LocalDate.parse("2026-08-19"), "Europe/Paris"))
                .thenReturn(search);

        mockMvc.perform(get("/events")
                        .param("date", "2026-08-19")
                        .param("zone", "Europe/Paris")
                        .flashAttr("snapshotNormalization", normalization))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"snapshot-competition-normalization\"")))
                .andExpect(content().string(containsString(
                        "Snapshot <span>308</span>")))
                .andExpect(content().string(containsString(
                        "<span>2</span> comp")))
                .andExpect(content().string(containsString(
                        "Cet endpoint ne fournit aucune rencontre programm")))
                .andExpect(content().string(containsString(
                        "aucune observation J4 n")));
    }

    @ParameterizedTest
    @CsvSource({"1st half,1st half", "Halftime,Halftime", "2nd half,2nd half",
            ",inprogress", "<b>Halftime</b>,&lt;b&gt;Halftime&lt;/b&gt;"})
    void eventListAndDetailShowObservedPeriodWithEscapingAndMissingDescriptionFallback(
            String description, String expected) throws Exception {
        var base = providerEvent();
        var event = new CanonicalEventObservationView(base.observationId(), base.identity(), base.startsAt(),
                base.homeTeam(), base.awayTeam(), new ScheduledEventStatus("inprogress", Optional.ofNullable(description)),
                base.tournament(), base.source(), base.normalizedSha256(), base.observationCount());
        var zone = ZoneId.of("Europe/Paris");
        var date = event.startsAt().atZone(zone).toLocalDate();
        var item = new J4EventSearchItem(event, event.startsAt().atZone(zone));
        when(queryService.search(date, "Europe/Paris")).thenReturn(new J4EventSearchResult(date, zone,
                date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant(), List.of(item)));
        when(queryService.findDetail(event.identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(new J4EventDetailResult(zone, item, List.of(item), Optional.empty())));

        for (String path : List.of("/events", "/events/" + event.identity().value())) {
            String html = mockMvc.perform(get(path).param("date", date.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var badge = Pattern.compile("<span\\b[^>]*data-live-sport-status[^>]*>(.*?)</span>").matcher(html);
            assertThat(badge.find()).isTrue();
            assertThat(badge.group(1)).isEqualTo(expected);
            assertThat(html).doesNotContain("<b>Halftime</b>");
            if (path.equals("/events")) assertThat(html).contains("data-live-finished=\"false\"");
        }
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
                .andExpect(content().string(containsString("Export canonique J7")))
                .andExpect(content().string(containsString(
                        "/events/" + event.identity().value() + "/exports")))
                .andExpect(content().string(containsString("event-details-nominal")));
    }

    @Test
    void returnsToTheProviderEventsCivilDateAndRendersItsRealProvenance() throws Exception {
        var event = providerEvent();
        var source = EventSourceTrace.providerSnapshot(
                16L,
                "c".repeat(64),
                "event-details-v2",
                Instant.parse("2026-08-15T06:59:05.791963Z"));
        var details = new EventDetailObservationView(
                2L,
                event.identity(),
                new EventDetails(
                        16386245L,
                        event.startsAt(),
                        event.homeTeam(),
                        event.awayTeam(),
                        event.status(),
                        event.tournament(),
                        Optional.of(new EventVenue(
                                101L,
                                "Stade Geoffroy Guichard",
                                Optional.of("Saint Etienne"))),
                        Optional.of(new EventSeason(2026L, "Ligue 2 26/27")),
                        Optional.of("2")),
                source,
                "d".repeat(64));
        var current = new J4EventSearchItem(
                event,
                event.startsAt().atZone(ZoneId.of("Europe/Paris")));
        var detail = new J4EventDetailResult(
                ZoneId.of("Europe/Paris"),
                current,
                List.of(current),
                Optional.of(details));
        when(queryService.findDetail(event.identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(detail));

        mockMvc.perform(get("/events/{id}", event.identity().value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(view().name("event-detail"))
                .andExpect(model().attribute(
                        "detailSearchDate", LocalDate.parse("2026-08-14")))
                .andExpect(content().string(containsString(
                        "/events?date=2026-08-14&amp;zone=Europe/Paris")))
                .andExpect(content().string(containsString("PROVIDER_SNAPSHOT")))
                .andExpect(content().string(containsString("snapshot:16")))
                .andExpect(content().string(containsString("event-details-v2")));
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

    @Test
    void preparesOneCanonicalEventWithoutCallingTheProvider() throws Exception {
        CanonicalEventIdentity identity = CanonicalEventIdentity.sofascore(17000001L);
        when(queryService.findSofascoreIdentityInSelection(
                identity.value(), LocalDate.parse("2026-08-15"), "Europe/Paris"))
                .thenReturn(Optional.of(identity));
        when(realPhase2ControlService.prepare(identity)).thenReturn(
                new J4RealPhase2ControlSnapshot(
                        J4RealPhase2State.AWAITING_CONFIRMATION,
                        Instant.parse("2026-08-15T12:00:00Z"),
                        UUID.fromString("60000000-0000-0000-0000-000000000004"),
                        "CONFIRMER EVENT_DETAILS " + identity.value()
                                + " 17000001 000042",
                        Instant.parse("2026-08-15T12:00:00Z"),
                        Instant.parse("2026-08-15T12:05:00Z"),
                        identity.value(),
                        17000001L,
                        false,
                        null,
                        true,
                        List.of()));

        mockMvc.perform(post("/events/real-phase2/prepare")
                        .param("localFormToken", "one-use-token")
                        .param("canonicalEventId", identity.value().toString())
                        .param("date", "2026-08-15")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events?date=2026-08-15&zone=Europe%2FParis"));

        verify(queryService).findSofascoreIdentityInSelection(
                identity.value(), LocalDate.parse("2026-08-15"), "Europe/Paris");
        verify(realPhase2ControlService).prepare(identity);
        verify(realPhase2Service, org.mockito.Mockito.never()).execute(any());
    }

    @Test
    void rejectsAValidUuidThatIsAbsentFromTheServerComputedDateSelection() throws Exception {
        UUID unknown = CanonicalEventIdentity.sofascore(17000002L).value();
        when(queryService.findSofascoreIdentityInSelection(
                unknown, LocalDate.parse("2026-08-15"), "Europe/Paris"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/events/real-phase2/prepare")
                        .param("localFormToken", "one-use-token")
                        .param("canonicalEventId", unknown.toString())
                        .param("date", "2026-08-15")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events?date=2026-08-15&zone=Europe%2FParis"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute(
                                "realPhase2ErrorCode",
                                "CANONICAL_EVENT_NOT_IN_SELECTION"));

        verify(realPhase2ControlService, org.mockito.Mockito.never()).prepare(any());
        verify(realPhase2Service, org.mockito.Mockito.never()).execute(any());
    }

    @Test
    void executesOnlyTheEventBoundToThePreparedPhaseTwoClaim() throws Exception {
        UUID requestId = UUID.fromString("60000000-0000-0000-0000-000000000004");
        CanonicalEventIdentity identity = CanonicalEventIdentity.sofascore(17000001L);
        var claim = new J4RealPhase2ExecutionClaim(
                requestId,
                URI.create("https://www.sofascore.com"),
                identity.value(),
                17000001L);
        when(realPhase2ControlService.confirmAndClaim(
                requestId,
                "CONFIRMER EVENT_DETAILS " + identity.value() + " 17000001 000042",
                true)).thenReturn(claim);
        when(realPhase2Service.execute(claim)).thenReturn(
                new J4RealEventDetailsPhase2Result(
                        requestId,
                        17000001L,
                        false,
                        "HTTP_429",
                        1,
                        List.of()));

        mockMvc.perform(post("/events/real-phase2/execute")
                        .param("localFormToken", "one-use-token")
                        .param("requestId", requestId.toString())
                        .param("confirmationText", "CONFIRMER EVENT_DETAILS "
                                + identity.value() + " 17000001 000042")
                        .param("acknowledged", "true")
                        .param("date", "2026-08-15")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events?date=2026-08-15&zone=Europe%2FParis"));

        verify(realPhase2ControlService).confirmAndClaim(
                requestId,
                "CONFIRMER EVENT_DETAILS " + identity.value() + " 17000001 000042",
                true);
        verify(realPhase2Service).execute(claim);
    }

    @Test
    void rendersAnExact404AsMinimizedUnavailableEvidence() throws Exception {
        var unavailable = new J4RealEventDetailsUnavailableResult(
                17000001L,
                901L,
                404,
                "d".repeat(64),
                21,
                RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE);
        var result = new J4RealEventDetailsPhase2Result(
                UUID.fromString("60000000-0000-0000-0000-000000000004"),
                17000001L,
                true,
                "COMPLETED_UNAVAILABLE",
                1,
                List.of(),
                List.of(unavailable));
        when(queryService.search(LocalDate.parse("2026-08-15"), "Europe/Paris"))
                .thenReturn(new J4EventSearchResult(
                        LocalDate.parse("2026-08-15"),
                        ZoneId.of("Europe/Paris"),
                        Instant.parse("2026-08-14T22:00:00Z"),
                        Instant.parse("2026-08-15T22:00:00Z"),
                        List.of()));

        mockMvc.perform(get("/events")
                        .param("date", "2026-08-15")
                        .param("zone", "Europe/Paris")
                        .flashAttr("realPhase2Result", result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("COMPLETED_UNAVAILABLE")))
                .andExpect(content().string(containsString("ENDPOINT_UNAVAILABLE")))
                .andExpect(content().string(containsString(">404</td>")))
                .andExpect(content().string(containsString("d".repeat(64))));
    }

    @Test
    void rendersPhaseOneUnavailableTargetsAsCompletedOutcomes() throws Exception {
        var first = new J4RealEventDetailsUnavailableResult(
                16386245L,
                911L,
                404,
                "e".repeat(64),
                21,
                RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE);
        var second = new J4RealEventDetailsUnavailableResult(
                16421052L,
                912L,
                404,
                "f".repeat(64),
                22,
                RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE);
        var result = new J4RealEventDetailsPhase1Result(
                UUID.fromString("30000000-0000-0000-0000-000000000004"),
                true,
                "COMPLETED",
                2,
                0,
                List.of(),
                List.of(first, second));
        when(queryService.search(LocalDate.parse("2026-08-15"), "Europe/Paris"))
                .thenReturn(new J4EventSearchResult(
                        LocalDate.parse("2026-08-15"),
                        ZoneId.of("Europe/Paris"),
                        Instant.parse("2026-08-14T22:00:00Z"),
                        Instant.parse("2026-08-15T22:00:00Z"),
                        List.of()));

        mockMvc.perform(get("/events")
                        .param("date", "2026-08-15")
                        .param("zone", "Europe/Paris")
                        .flashAttr("realPhase1Result", result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 CIBLE(S) TRAIT")))
                .andExpect(content().string(containsString("16386245")))
                .andExpect(content().string(containsString("16421052")))
                .andExpect(content().string(containsString("ENDPOINT_UNAVAILABLE")));
    }

    @Test
    void globalStopLocksBothRealQualificationPaths() throws Exception {
        mockMvc.perform(post("/events/real/stop")
                        .param("localFormToken", "one-use-token")
                        .param("date", "2026-08-15")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events?date=2026-08-15&zone=Europe%2FParis"));

        verify(providerCampaignStopService).stopAll();
    }

    @Test
    void globalStopFailureDoesNotAlsoAdvertiseAConfirmedStop() throws Exception {
        doThrow(new J4ProviderCampaignStopException(
                new IllegalStateException("cleanup not confirmed")))
                .when(providerCampaignStopService).stopAll();

        mockMvc.perform(post("/events/real/stop")
                        .param("localFormToken", "one-use-token")
                        .param("date", "2026-08-15")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events?date=2026-08-15&zone=Europe%2FParis"))
                .andExpect(flash().attribute(
                        "realPhase1ErrorCode",
                        "PLAYWRIGHT_STOP_UNCONFIRMED"))
                .andExpect(flash().attributeCount(1));

        verify(providerCampaignStopService).stopAll();
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

    private static CanonicalEventObservationView providerEvent() {
        return new CanonicalEventObservationView(
                2L,
                CanonicalEventIdentity.sofascore(16386245L),
                Instant.parse("2026-08-14T18:45:00Z"),
                new ScheduledTeam(1001L, "Saint-Étienne"),
                new ScheduledTeam(1002L, "Clermont Foot"),
                new ScheduledEventStatus("finished", Optional.of("Finished")),
                Optional.of(new ScheduledTournament(7L, "Ligue 2")),
                EventSourceTrace.providerSnapshot(
                        16L,
                        "c".repeat(64),
                        "event-details-v2",
                        Instant.parse("2026-08-15T06:59:05.791963Z")),
                "d".repeat(64),
                1L);
    }
}
