package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.*;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.application.network.*;
import com.bettingproject.sofascorelocal.config.LiveCampaignWebMvcConfiguration;
import com.bettingproject.sofascorelocal.domain.event.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.*;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.LiveDiagnosticStore;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Explicit Chromium qualification of the actual MVC forms and response security policy.
 * Every browser request is fulfilled in memory by MockMvc; the operator's port and database
 * are untouched, and no request is allowed to reach a provider or an actual HTTP listener.
 */
@WebMvcTest({EventExplorerController.class, LiveCampaignController.class})
@Import({LiveCampaignPresentation.class, LocalFormTokenService.class, LiveCampaignWebMvcConfiguration.class})
class LiveCampaignFormBrowserQualificationIT {
    private static final UUID EVENT_ID = CanonicalEventIdentity.sofascore(900001L).value();
    private static final UUID CAMPAIGN_ID = UUID.fromString("00000000-0000-0000-0000-000000000058");
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    @Autowired private MockMvc mvc;
    @MockitoBean private LiveCampaignService campaigns;
    @MockitoBean private CanonicalEventStore events;
    @MockitoBean private EventDetailsStore details;
    @MockitoBean private J5EventDataStore data;
    @MockitoBean private LiveDiagnosticStore diagnostics;
    @MockitoBean private LineupCountryOverlayResolver lineupCountries;
    @MockitoBean private CacheManager cacheManager;
    @MockitoBean private J4EventQueryService query;
    @MockitoBean private J4OfflineFixtureImportService imports;
    @MockitoBean private J4ScheduledEventsSnapshotNormalizationService normalization;
    @MockitoBean private J4RealPhase1ControlService phase1Control;
    @MockitoBean private J4RealEventDetailsPhase1Service phase1;
    @MockitoBean private J4RealPhase2ControlService phase2Control;
    @MockitoBean private J4RealEventDetailsPhase2Service phase2;
    @MockitoBean private J4ProviderCampaignStopService providerStop;

    @ParameterizedTest
    @ValueSource(strings = {"localhost:8087", "127.0.0.1:8087"})
    @Timeout(120)
    void renderedFormsKeepExactOriginAndOpaquePostStillFails(String host) throws Exception {
        String configured = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(configured).as("explicit browser cache opt-in").isNotBlank();
        assertThat(Path.of(configured).toRealPath()).isEqualTo(
                Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
        arrangeLocalObservations();
        AtomicReference<String> state = new AtomicReference<>("PREPARED");
        when(campaigns.state(CAMPAIGN_ID)).thenAnswer(ignored -> campaign(state.get()));
        doAnswer(ignored -> { state.set("RUNNING"); return null; })
                .when(campaigns).launch(CAMPAIGN_ID, HASH);

        String origin = "http://" + host;
        MockHttpSession session = new MockHttpSession();
        List<ObservedPost> posts = new ArrayList<>();
        AtomicBoolean oldPolicy = new AtomicBoolean();
        AtomicInteger externalRequests = new AtomicInteger();
        AtomicReference<Throwable> bridgeFailure = new AtomicReference<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK).setOffline(true))) {
            context.route("**/*", route -> {
                Request browserRequest = route.request();
                if (!browserRequest.url().startsWith(origin + "/")) {
                    externalRequests.incrementAndGet(); route.abort(); return;
                }
                try {
                    URI uri = URI.create(browserRequest.url());
                    var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(browserRequest.method()), uri)
                            .session(session);
                    browserRequest.allHeaders().forEach((name, value) -> {
                        if (!name.equalsIgnoreCase("host")) request.header(name, value);
                    });
                    // Host is the URL authority the browser would put on the wire. Origin and
                    // Fetch Metadata are copied unchanged from Chromium, never manufactured.
                    request.header("Host", uri.getRawAuthority());
                    if (browserRequest.postData() != null) {
                        for (String pair : browserRequest.postData().split("&")) {
                            String[] parts = pair.split("=", 2);
                            request.param(decode(parts[0]), parts.length == 1 ? "" : decode(parts[1]));
                        }
                    }
                    var response = mvc.perform(request).andReturn().getResponse();
                    if (browserRequest.method().equals("POST")) {
                        posts.add(new ObservedPost(uri.getPath(), browserRequest.headerValue("origin"),
                                response.getStatus()));
                    }
                    // Playwright does not route a redirect chain's subsequent requests. Render
                    // this controller's local redirect in MockMvc as well; never dispatch it to
                    // the operator's listener. The original POST status remains in the evidence.
                    if (response.getRedirectedUrl() != null) {
                        String location = response.getRedirectedUrl();
                        assertThat(location).startsWith("/live-campaigns/").doesNotContain(":", "//");
                        response = mvc.perform(MockMvcRequestBuilders.get(URI.create(origin + location))
                                .header("Host", host).session(session)).andReturn().getResponse();
                    }
                    var headers = new LinkedHashMap<String, String>();
                    for (String name : response.getHeaderNames()) headers.put(name, response.getHeader(name));
                    if (oldPolicy.get() && uri.getPath().equals("/events"))
                        headers.put("Referrer-Policy", "no-referrer");
                    route.fulfill(new Route.FulfillOptions().setStatus(response.getStatus())
                            .setHeaders(headers).setBodyBytes(response.getContentAsByteArray()));
                } catch (Throwable failure) {
                    bridgeFailure.compareAndSet(null, failure); route.abort();
                }
            });
            Page page = context.newPage();
            checkSelectionCeilings(page, origin);
            arrangeLocalObservations();
            Response search = page.navigate(origin + "/events?date=2026-09-07&zone=Europe%2FParis");
            assertThat(search.status()).isEqualTo(200);
            assertThat(search.headerValue("Referrer-Policy")).isEqualTo("same-origin");
            page.locator("input[name=eventId][value='" + EVENT_ID + "']").check();
            Response prepared = page.waitForNavigation(() -> page.locator("[data-live-prepare]").click());
            assertThat(prepared.status()).isEqualTo(200);
            assertThat(prepared.headerValue("Referrer-Policy")).isEqualTo("same-origin");
            assertThat(page.locator("#campaign-title").textContent()).isEqualTo(CAMPAIGN_ID.toString());
            page.locator("input[name=confirmation]").check();
            assertThat(page.waitForNavigation(() -> page.locator("form[action$='/launch'] button").click())
                    .status()).isEqualTo(200);
            assertThat(page.waitForNavigation(() -> page.locator("form[action$='/events/" + EVENT_ID
                    + "/stop'] button").click()).status()).isEqualTo(200);
            assertThat(page.waitForNavigation(() -> page.locator("form[action='/live-campaigns/"
                    + CAMPAIGN_ID + "/stop'] button").click()).status()).isEqualTo(200);
            assertThat(posts).containsExactly(
                    new ObservedPost("/live-campaigns/prepare", origin, 302),
                    new ObservedPost("/live-campaigns/" + CAMPAIGN_ID + "/launch", origin, 302),
                    new ObservedPost("/live-campaigns/" + CAMPAIGN_ID + "/events/" + EVENT_ID + "/stop", origin, 302),
                    new ObservedPost("/live-campaigns/" + CAMPAIGN_ID + "/stop", origin, 302));

            var finished = observation("finished");
            showEvent(finished);
            when(campaigns.prepareSelection(List.of(EVENT_ID)))
                    .thenReturn(new LiveCampaignService.Preparation(null, List.of(finished)));
            page.navigate(origin + "/events?date=2026-09-07&zone=Europe%2FParis");
            page.locator("input[name=eventId][value='" + EVENT_ID + "']").check();
            Response ineligible = page.waitForNavigation(() -> page.locator("[data-live-prepare]").click());
            assertThat(ineligible.status()).isEqualTo(200);
            assertThat(page.locator("h1").textContent()).contains("déjà terminées");
            assertThat(page.locator("[role=status]").textContent()).contains("Aucune campagne live n’a été créée");
            assertThat(page.locator("form[action$='/launch']").count()).isZero();
            assertThat(posts.getLast()).isEqualTo(new ObservedPost("/live-campaigns/prepare", origin, 200));

            // Reproduce the reported failure using the previous response policy, a newly
            // rendered form and a valid fresh token. Strict opaque-origin denial remains.
            oldPolicy.set(true);
            page.navigate(origin + "/events?date=2026-09-07&zone=Europe%2FParis");
            page.locator("input[name=eventId][value='" + EVENT_ID + "']").check();
            Response denied = page.waitForNavigation(() -> page.locator("[data-live-prepare]").click());
            assertThat(denied.status()).isEqualTo(403);
            assertThat(denied.body()).isEmpty();
            assertThat(posts.getLast()).isEqualTo(new ObservedPost("/live-campaigns/prepare", "null", 403));
            assertThat(bridgeFailure.get()).isNull();
            assertThat(externalRequests).hasValue(0);
        }
        verify(campaigns, times(2)).prepareSelection(List.of(EVENT_ID));
        verify(campaigns, times(1)).launch(CAMPAIGN_ID, HASH);
        verify(campaigns, times(1)).stop(CAMPAIGN_ID, EVENT_ID);
        verify(campaigns, times(1)).stop(CAMPAIGN_ID, null);
        verifyNoInteractions(imports, normalization, phase1, phase2, providerStop);
    }

    private void arrangeLocalObservations() {
        when(campaigns.selectionMaximum()).thenReturn(1);
        showEvent(observation("notstarted"));
        when(phase1Control.snapshot()).thenReturn(new J4RealPhase1ControlSnapshot(J4RealPhase1State.LOCKED,
                NOW, null, null, null, null, 0, null, false, List.of("QUALIFICATION_DISABLED")));
        when(phase2Control.snapshot()).thenReturn(new J4RealPhase2ControlSnapshot(J4RealPhase2State.LOCKED,
                NOW, null, null, null, null, null, null, false, null, false, List.of("QUALIFICATION_DISABLED")));
        when(campaigns.prepareSelection(List.of(EVENT_ID)))
                .thenReturn(new LiveCampaignService.Preparation(manifest(), List.of()));
        when(campaigns.eventStates(any())).thenReturn(List.of());
    }

    private void checkSelectionCeilings(Page page, String origin) {
        for (int ceiling : new int[] {0, 7, 5, 10, 25}) {
            var zone = ZoneId.of("Europe/Paris");
            var items = new ArrayList<J4EventSearchItem>();
            for (int i = 0; i < ceiling + 2; i++) {
                var event = new CanonicalEventObservationView(i + 1L, CanonicalEventIdentity.sofascore(900001L + i), NOW,
                        new ScheduledTeam(9101L, "Synthetic Home " + i), new ScheduledTeam(9202L, "Synthetic Away"),
                        new ScheduledEventStatus(i == ceiling + 1 ? "finished" : "notstarted", Optional.empty()),
                        Optional.empty(), EventSourceTrace.providerSnapshot(1L, HASH, "event-details-v2", NOW), HASH, 1L);
                items.add(new J4EventSearchItem(event, NOW.atZone(zone)));
            }
            when(query.search(LocalDate.of(2026, 9, 7), "Europe/Paris")).thenReturn(new J4EventSearchResult(
                    LocalDate.of(2026, 9, 7), zone, NOW.minusSeconds(3600), NOW.plusSeconds(3600), items));
            when(campaigns.selectionMaximum()).thenReturn(ceiling);
            page.navigate(origin + "/events?date=2026-09-07&zone=Europe%2FParis");
            var inputs = page.locator("input[form=live-selection][name=eventId]");
            assertThat(inputs.count()).isEqualTo(ceiling + 2);
            assertThat(page.locator("[data-live-prepare]").isDisabled()).isTrue();
            if (ceiling == 0) {
                for (int i = 0; i < inputs.count(); i++) {
                    assertThat(inputs.nth(i).isDisabled()).isTrue();
                    assertThat(inputs.nth(i).isChecked()).isFalse();
                }
                assertThat(inputs.last().getAttribute("data-live-finished")).isEqualTo("true");
                assertThat(page.locator("[data-live-eligible-count]").textContent())
                        .isEqualTo("Sélection indisponible : aucune capacité de collecte qualifiée.")
                        .doesNotContain("éligible", "/ 0");
                assertThat(page.locator("[data-live-selection-count]").textContent())
                        .isEqualTo("0 rencontre sélectionnée");
                assertThat(page.locator("#live-selection").textContent()).doesNotContain("live-v5");
                continue;
            }
            for (int i = 0; i < ceiling; i++) inputs.nth(i).check();
            assertThat(inputs.nth(ceiling).isDisabled()).isTrue();
            assertThat(inputs.nth(ceiling + 1).isEnabled()).isTrue();
            inputs.nth(ceiling + 1).check();
            assertThat(page.locator("[data-live-eligible-count]").textContent())
                    .isEqualTo(ceiling + " rencontres sélectionnées admissibles / " + ceiling);
            assertThat(page.locator("[data-live-selection-count]").textContent())
                    .isEqualTo((ceiling + 1) + " rencontres sélectionnées");
            assertThat(page.locator("[data-live-prepare]").isEnabled()).isTrue();
            inputs.first().uncheck();
            assertThat(inputs.nth(ceiling).isEnabled()).isTrue();
            assertThat(page.locator("[data-live-eligible-count]").textContent())
                    .isEqualTo((ceiling - 1) + " rencontres sélectionnées admissibles / " + ceiling);
            inputs.nth(ceiling).check();
            assertThat(inputs.first().isDisabled()).isTrue();
            assertThat(page.locator("[data-live-eligible-count]").textContent())
                    .isEqualTo(ceiling + " rencontres sélectionnées admissibles / " + ceiling);
            assertThat(inputs.nth(ceiling + 1).isChecked()).isTrue();
            if (ceiling == 7) {
                for (int i = 1; i <= ceiling; i++) inputs.nth(i).uncheck();
                assertThat(page.locator("[data-live-selection-count]").textContent())
                        .isEqualTo("1 rencontre sélectionnée");
                assertThat(page.locator("[data-live-eligible-count]").textContent())
                        .isEqualTo("0 rencontre sélectionnée admissible / 7");
                assertThat(page.locator("[data-live-prepare]").isEnabled()).isTrue();
                inputs.last().uncheck();
                assertThat(page.locator("[data-live-prepare]").isDisabled()).isTrue();
            }
        }
    }

    private static CanonicalEventObservationView observation(String status) {
        return new CanonicalEventObservationView(1L, CanonicalEventIdentity.sofascore(900001L), NOW,
                new ScheduledTeam(9101L, "Synthetic Home FC"), new ScheduledTeam(9202L, "Synthetic Away FC"),
                new ScheduledEventStatus(status, Optional.empty()),
                Optional.of(new ScheduledTournament(9303L, "Synthetic League")),
                EventSourceTrace.providerSnapshot(1L, HASH, "event-details-v2", NOW), HASH, 1L);
    }

    private void showEvent(CanonicalEventObservationView event) {
        var zone = ZoneId.of("Europe/Paris");
        when(query.search(LocalDate.of(2026, 9, 7), "Europe/Paris")).thenReturn(new J4EventSearchResult(
                LocalDate.of(2026, 9, 7), zone, NOW.minusSeconds(3600), NOW.plusSeconds(3600),
                List.of(new J4EventSearchItem(event, NOW.atZone(zone)))));
        when(events.findByObservationId(any(), anyLong())).thenReturn(Optional.of(event));
    }

    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }

    private static Manifest manifest() {
        return new Manifest(CAMPAIGN_ID, HASH, "live-v1", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, 1_000_000, 1, List.of(new Target(EVENT_ID, 900001L, 1, 1)));
    }

    private static CampaignView campaign(String state) {
        return new CampaignView(manifest(), state, null, state.equals("PREPARED") ? null : NOW,
                state.equals("PREPARED") ? null : NOW.plusSeconds(14400), 0, 0, 1, null,
                List.of(new EventView(manifest().targets().getFirst(), "WAITING_START", null, 0, 0,
                        NOW, List.of())), List.of(), List.of());
    }

    private record ObservedPost(String path, String origin, int status) { }
}
