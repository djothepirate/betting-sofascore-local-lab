package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignDiagnostic;
import com.bettingproject.sofascorelocal.config.LiveCampaignWebMvcConfiguration;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Opt-in Chromium qualification of the actual controller, templates and polling asset.
 * Seventeen synthetic matches are served entirely by MockMvc through offline routes;
 * the virtual loopback origin does not open a socket or contact the operator's Lab.
 */
@WebMvcTest(LiveCampaignController.class)
@Import({LiveCampaignPresentation.class, LocalFormTokenService.class, LiveCampaignWebMvcConfiguration.class})
class LiveCampaignPaginationBrowserQualificationIT {
    private static final UUID CAMPAIGN_ID = UUID.fromString("00000000-0000-0000-0000-000000000058");
    private static final String HASH = "a".repeat(64);
    private static final String HOST = "127.0.0.1:8087";
    private static final String ORIGIN = "http://" + HOST;
    private static final String BASE = "/live-campaigns/" + CAMPAIGN_ID;
    private static final Instant NOW = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private static final List<Target> TARGETS = IntStream.range(0, 17).mapToObj(i -> new Target(
            CanonicalEventIdentity.sofascore(900001L + i).value(), 900001L + i, i + 1L, i + 1L)).toList();
    @Autowired private MockMvc mvc;
    @MockitoBean private LiveCampaignService campaigns;
    @MockitoBean private CanonicalEventStore events;
    @MockitoBean private EventDetailsStore details;
    @MockitoBean private J5EventDataStore data;
    @MockitoBean private CacheManager cacheManager;

    @Test
    @Timeout(90)
    void seventeenMatchesKeepPageTwoAcrossRefreshAndBothStopActionsWithoutLosingPreparationTargets() throws Exception {
        String cache = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(cache).as("explicit native browser opt-in").isNotBlank();
        assertThat(Path.of(cache).toRealPath()).isEqualTo(Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
        AtomicInteger revision = new AtomicInteger(1);
        AtomicReference<String> state = new AtomicReference<>("RUNNING");
        AtomicReference<LiveCampaignService.RuntimeStatus> runtime = new AtomicReference<>();
        Set<UUID> stopped = new HashSet<>();
        when(campaigns.state(CAMPAIGN_ID)).thenAnswer(ignored -> campaign(state.get(), revision.get(), stopped));
        when(campaigns.runtimeStatus(CAMPAIGN_ID)).thenAnswer(ignored -> Optional.ofNullable(runtime.get()));
        when(events.findByObservationId(any(), anyLong())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return TARGETS.stream().filter(t -> t.canonicalEventId().equals(id)).findFirst().map(this::identity);
        });
        doAnswer(invocation -> {
            UUID eventId = invocation.getArgument(1);
            if (eventId == null) state.set("STOPPED_OPERATOR"); else stopped.add(eventId);
            revision.incrementAndGet();
            return null;
        }).when(campaigns).stop(any(), any());
        var allIds = TARGETS.stream().map(Target::canonicalEventId).toList();
        when(campaigns.prepareSelection(allIds)).thenReturn(new LiveCampaignService.Preparation(manifest(), List.of()));
        AtomicInteger external = new AtomicInteger();
        AtomicReference<Throwable> bridgeFailure = new AtomicReference<>();
        List<String> polls = new ArrayList<>(), scriptErrors = new ArrayList<>();
        List<ObservedPost> posts = new ArrayList<>();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            try (BrowserContext context = browser.newContext(options(false))) {
                bridge(context, polls, posts, external, bridgeFailure);
                Page page = context.newPage();
                assertThat(page.navigate(ORIGIN + BASE).status()).isEqualTo(200);
                assertPage(page, 1);
                assertThat(page.locator("[data-live-diagnostics]").count()).isOne();
                assertThat(page.locator("[data-live-diagnostics]").isHidden()).isTrue();
                page.locator("[data-live-pagination]").first().locator("a[aria-label='Page 2']").click();
                page.waitForURL(ORIGIN + BASE + "?page=2#campaign-events");
                assertPage(page, 2);
                assertThat(polls).as("navigation works without JavaScript or polling").isEmpty();
                capture(page, "pagination-page-two-no-js.png");
                // An operator stop may have only a cleanup failure; verify native SSR hiding too.
                var cleanupOnly = new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_EXCLUSION,
                        "RUNTIME_OR_STORAGE_FAILURE", NOW.plusSeconds(2));
                runtime.set(new LiveCampaignService.RuntimeStatus("STOPPED_ERROR", "LOCAL_CLEANUP_PENDING", true, true, false,
                        null, cleanupOnly));
                assertThat(page.reload().status()).isEqualTo(200);
                Locator diagnostic = page.locator("[data-live-diagnostics]");
                assertThat(diagnostic.isVisible()).isTrue();
                diagnostic.locator(":scope > summary").click();
                assertThat(diagnostic.locator("[data-live-diagnostic='firstFailure']").isHidden()).isTrue();
                assertDiagnostic(diagnostic.locator("[data-live-diagnostic='cleanupFailure']"), cleanupOnly);
                assertThat(polls).isEmpty();
                runtime.set(null);
            }

            try (BrowserContext context = browser.newContext(options(true))) {
                bridge(context, polls, posts, external, bridgeFailure);
                Page page = context.newPage();
                page.onPageError(scriptErrors::add);
                String selected = TARGETS.get(10).canonicalEventId().toString();
                String incoming = ORIGIN + BASE + "?page=1&eventId=" + selected + "#live-event-" + selected;
                assertThat(page.navigate(incoming).status()).isEqualTo(200);
                assertPage(page, 2);
                assertThat(page.locator("#live-event-" + selected).isVisible()).isTrue();
                page.waitForCondition(() -> !polls.isEmpty());
                assertThat(polls).containsOnly("page=2");
                List<String> stableIds = cardIds(page);
                String originalReceived = firstJ4(page).locator("[data-live-received]").textContent();
                revision.set(2);
                page.waitForCondition(() -> "2 – 0".equals(page.locator("[data-live-event-id]").first()
                        .locator("[data-live-score]").textContent()));
                assertPage(page, 2);
                assertThat(cardIds(page)).containsExactlyElementsOf(stableIds);
                assertThat(firstJ4(page).locator("[data-live-received]").textContent()).isNotEqualTo(originalReceived);
                assertThat(page.locator("[data-live-calls]").textContent()).isEqualTo("125 / 20000");
                assertThat(polls).containsOnly("page=2");

                // An unchanged projection still publishes a newer receipt on the same page.
                String changedReceived = firstJ4(page).locator("[data-live-received]").textContent();
                revision.set(3);
                page.waitForCondition(() -> !changedReceived.equals(firstJ4(page).locator("[data-live-received]").textContent()));
                assertThat(page.locator("[data-live-event-id]").first().locator("[data-live-score]").textContent()).isEqualTo("2 – 0");
                assertThat(cardIds(page)).containsExactlyElementsOf(stableIds);
                assertThat(firstJ4(page).locator("[data-live-freshness]").textContent()).isEqualTo("Dans l’intervalle attendu");
                assertThat(page.locator("[data-live-autonomy]").textContent()).matches("[1-9][0-9]* minutes environ");

                // A process-only stop must be rendered even if the durable revision is unchanged.
                String lastReceived = firstJ4(page).locator("[data-live-received]").textContent();
                var firstFailure = new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.STORAGE_CHECK,
                        "LIVE_STORAGE_PROBE_TIMEOUT", NOW.plusSeconds(4));
                runtime.set(new LiveCampaignService.RuntimeStatus("STOPPED_ERROR", "LOCAL_CLEANUP_PENDING", true, true, false,
                        firstFailure, null));
                page.waitForCondition(() -> "Collecte arrêtée".equals(page.locator("[data-live-autonomy]").textContent()));
                assertThat(revision.get()).isEqualTo(3);
                assertThat(page.locator("[data-live-campaign-state]").textContent()).isEqualTo("RUNNING");
                assertThat(firstJ4(page).locator("[data-live-received]").textContent()).isEqualTo(lastReceived);
                assertThat(cardIds(page)).containsExactlyElementsOf(stableIds);
                assertThat(page.locator("[data-live-global-stop-form] button").textContent()).isEqualTo("Finaliser la clôture locale");
                Locator diagnostics = page.locator("[data-live-diagnostics]");
                assertThat(diagnostics.count()).isOne();
                assertThat(diagnostics.isVisible()).isTrue();
                assertThat(diagnostics.evaluate("element => element.open")).isEqualTo(false);
                Locator diagnosticSummary = diagnostics.locator(":scope > summary");
                assertThat(diagnosticSummary.textContent()).isEqualTo("Diagnostic de l’arrêt");
                diagnosticSummary.click();
                Locator firstDiagnostic = diagnostics.locator("[data-live-diagnostic='firstFailure']");
                Locator cleanupDiagnostic = diagnostics.locator("[data-live-diagnostic='cleanupFailure']");
                assertDiagnostic(firstDiagnostic, firstFailure);
                assertThat(cleanupDiagnostic.isHidden()).isTrue();

                var cleanupFailure = new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_EXCLUSION,
                        "RUNTIME_OR_STORAGE_FAILURE", NOW.plusSeconds(5));
                diagnosticSummary.focus();
                runtime.set(new LiveCampaignService.RuntimeStatus("STOPPED_ERROR", "LOCAL_CLEANUP_PENDING", true, true, false,
                        firstFailure, cleanupFailure));
                page.waitForCondition(() -> cleanupFailure.occurredAt().toString().equals(
                        cleanupDiagnostic.locator("[data-live-diagnostic-time]").textContent()));
                assertThat(revision.get()).isEqualTo(3);
                assertThat(diagnostics.evaluate("element => element.open")).isEqualTo(true);
                assertThat(diagnosticSummary.evaluate("element => document.activeElement === element")).isEqualTo(true);
                assertDiagnostic(firstDiagnostic, firstFailure);
                assertDiagnostic(cleanupDiagnostic, cleanupFailure);
                Path diagnosticCapture = Path.of(".tmp", "live-pagination-qualification", "live-stop-diagnostic.png").toAbsolutePath();
                Files.createDirectories(diagnosticCapture.getParent());
                diagnostics.screenshot(new Locator.ScreenshotOptions().setPath(diagnosticCapture));

                diagnosticSummary.click();
                var latestCleanup = new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_EXCLUSION,
                        "RUNTIME_OR_STORAGE_FAILURE", NOW.plusSeconds(6));
                runtime.set(new LiveCampaignService.RuntimeStatus("STOPPED_ERROR", "LOCAL_CLEANUP_PENDING", true, true, false,
                        firstFailure, latestCleanup));
                page.waitForCondition(() -> latestCleanup.occurredAt().toString().equals(
                        cleanupDiagnostic.locator("[data-live-diagnostic-time]").textContent()));
                assertThat(diagnostics.evaluate("element => element.open")).isEqualTo(false);
                assertThat(firstDiagnostic.locator("[data-live-diagnostic-code]").textContent()).isEqualTo(firstFailure.code());
                assertThat(firstDiagnostic.locator("[data-live-diagnostic-time]").textContent()).isEqualTo(firstFailure.occurredAt().toString());
                runtime.set(null);
                page.waitForCondition(() -> page.locator("[data-live-autonomy]").textContent().matches("[1-9][0-9]* minutes environ"));
                assertThat(revision.get()).isEqualTo(3);
                assertThat(page.locator("[data-live-campaign-state]").textContent()).isEqualTo("RUNNING");
                assertThat(firstJ4(page).locator("[data-live-received]").textContent()).isEqualTo(lastReceived);
                assertThat(page.locator("[data-live-global-stop-form] button").textContent()).isEqualTo("Arrêter toute la campagne");
                assertThat(diagnostics.isHidden()).isTrue();
                assertThat(firstDiagnostic.locator("[data-live-diagnostic-code]").textContent()).isEqualTo("—");
                assertThat(cleanupDiagnostic.locator("[data-live-diagnostic-code]").textContent()).isEqualTo("—");
                assertPage(page, 2);
                capture(page, "pagination-page-two-live.png");

                Locator individual = page.locator("[data-live-event-id]").first().locator("form[data-live-stop-form]");
                assertThat(individual.locator("input[name=page]").inputValue()).isEqualTo("2");
                assertThat(page.waitForNavigation(() -> individual.locator("button").click()).status()).isEqualTo(200);
                assertPage(page, 2);
                assertThat(page.locator("[data-live-event-id]").first().locator("[data-live-event-state]").textContent()).isEqualTo("STOPPED_OPERATOR");
                assertThat(page.locator("[data-live-event-id]").nth(1).locator("[data-live-event-state]").textContent()).isEqualTo("COLLECTING");
                assertThat(posts.getLast().redirect()).isEqualTo(BASE + "?page=2");
                verify(campaigns).stop(CAMPAIGN_ID, TARGETS.get(10).canonicalEventId());

                Locator global = page.locator("[data-live-global-stop-form]");
                assertThat(global.locator("input[name=page]").inputValue()).isEqualTo("2");
                assertThat(page.waitForNavigation(() -> global.locator("button").click()).status()).isEqualTo(200);
                assertPage(page, 2);
                assertThat(page.locator("[data-live-campaign-state]").textContent()).isEqualTo("STOPPED_OPERATOR");
                assertThat(posts.getLast().redirect()).isEqualTo(BASE + "?page=2");
                verify(campaigns).stop(CAMPAIGN_ID, null);
                assertThat(page.locator("[data-live-global-stop-form]").count()).isZero();

                page.locator("[data-live-pagination]").first().locator("a[aria-label='Page 1']").click();
                page.waitForURL(ORIGIN + BASE + "?page=1#campaign-events");
                assertPage(page, 1);
                page.locator("[data-live-pagination]").first().locator("a[aria-label='Page 2']").click();
                page.waitForURL(ORIGIN + BASE + "?page=2#campaign-events");
                assertPage(page, 2);
                Locator prepare = page.locator("form[action='/live-campaigns/prepare']");
                assertThat(prepare.locator("input[name=eventId]").evaluateAll("nodes => nodes.map(n => n.value)"))
                        .isEqualTo(allIds.stream().map(UUID::toString).toList());
                assertThat(page.waitForNavigation(() -> prepare.locator("button").click()).status()).isEqualTo(200);
                verify(campaigns).prepareSelection(allIds);
                verify(campaigns, never()).launch(any(), any());
            }
        }
        assertThat(bridgeFailure.get()).as("all browser requests served by production MVC in memory").isNull();
        assertThat(scriptErrors).isEmpty();
        assertThat(external.get()).as("zero provider or external requests").isZero();
        assertThat(posts).hasSize(3).allSatisfy(post -> {
            assertThat(post.origin()).isEqualTo(ORIGIN);
            assertThat(post.status()).isEqualTo(302);
        });
    }

    private static void assertDiagnostic(Locator section, LiveCampaignDiagnostic diagnostic) {
        assertThat(section.isVisible()).isTrue();
        assertThat(section.locator("[data-live-diagnostic-phase]").textContent()).isEqualTo(diagnostic.phase().name());
        assertThat(section.locator("[data-live-diagnostic-code]").textContent()).isEqualTo(diagnostic.code());
        assertThat(section.locator("[data-live-diagnostic-time]").textContent()).isEqualTo(diagnostic.occurredAt().toString());
    }

    private static Browser.NewContextOptions options(boolean javascript) {
        return new Browser.NewContextOptions().setJavaScriptEnabled(javascript).setOffline(true)
                .setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK).setViewportSize(1440, 1000);
    }

    private static void assertPage(Page page, int number) {
        int start = number == 1 ? 0 : 10, end = number == 1 ? 10 : 17;
        assertThat(cardIds(page)).containsExactlyElementsOf(TARGETS.subList(start, end).stream()
                .map(t -> t.canonicalEventId().toString()).toList());
        assertThat(page.locator("[data-live-pagination]").count()).isEqualTo(2);
        for (Locator nav : page.locator("[data-live-pagination]").all())
            assertThat(nav.locator("[aria-current=page]").textContent().trim()).isEqualTo(Integer.toString(number));
        assertThat(page.locator("[data-live-monitor]").getAttribute("data-live-state-url"))
                .isEqualTo(BASE + "/state?page=" + number);
        assertThat(page.locator("[data-live-calls]").textContent()).contains(" / 20000");
        assertThat(page.locator("main").textContent()).contains("100 secondes", "20 rencontres");
    }

    @SuppressWarnings("unchecked")
    private static List<String> cardIds(Page page) {
        return (List<String>) page.locator("[data-live-event-id]").evaluateAll("nodes => nodes.map(n => n.dataset.liveEventId)");
    }

    private static Locator firstJ4(Page page) {
        return page.locator("[data-live-event-id]").first().locator("[data-live-family='EVENT_DETAILS']");
    }

    private void bridge(BrowserContext context, List<String> polls, List<ObservedPost> posts,
                        AtomicInteger external, AtomicReference<Throwable> failure) {
        MockHttpSession session = new MockHttpSession();
        context.route("**/*", route -> {
            Request browserRequest = route.request();
            if (!browserRequest.url().startsWith(ORIGIN + "/")) { external.incrementAndGet(); route.abort(); return; }
            try {
                URI uri = URI.create(browserRequest.url());
                var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(browserRequest.method()), uri).session(session);
                browserRequest.allHeaders().forEach((name, value) -> {
                    if (!name.equalsIgnoreCase("host")) request.header(name, value);
                });
                request.header("Host", uri.getRawAuthority());
                if (browserRequest.postData() != null) for (String pair : browserRequest.postData().split("&")) {
                    String[] parts = pair.split("=", 2);
                    request.param(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                            parts.length == 1 ? "" : URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                }
                var response = mvc.perform(request).andReturn().getResponse();
                if (uri.getPath().equals(BASE + "/state")) polls.add(uri.getRawQuery());
                if (browserRequest.method().equals("POST")) posts.add(new ObservedPost(uri.getPath(),
                        browserRequest.headerValue("origin"), response.getStatus(), response.getRedirectedUrl()));
                // Redirect follow-ups are rendered in MockMvc too; no connection to an operator listener.
                if (response.getRedirectedUrl() != null) {
                    String location = response.getRedirectedUrl();
                    assertThat(location).startsWith("/live-campaigns/").doesNotContain(":", "//");
                    response = mvc.perform(MockMvcRequestBuilders.get(URI.create(ORIGIN + location)).header("Host", HOST)
                            .session(session)).andReturn().getResponse();
                }
                var headers = new LinkedHashMap<String, String>();
                for (String name : response.getHeaderNames()) headers.put(name, response.getHeader(name));
                route.fulfill(new Route.FulfillOptions().setStatus(response.getStatus()).setHeaders(headers)
                        .setBodyBytes(response.getContentAsByteArray()));
            } catch (Throwable problem) { failure.compareAndSet(null, problem); route.abort(); }
        });
    }

    private CanonicalEventObservationView identity(Target target) {
        int index = TARGETS.indexOf(target) + 1;
        return new CanonicalEventObservationView(index, CanonicalEventIdentity.sofascore(target.providerEventId()), NOW,
                new ScheduledTeam(100 + index, "Domicile synthétique " + index),
                new ScheduledTeam(200 + index, "Extérieur synthétique " + index),
                new ScheduledEventStatus("inprogress", Optional.of("2nd half")), Optional.empty(),
                EventSourceTrace.providerSnapshot(1, HASH, "event-details-v3", NOW), HASH, 1);
    }

    private static Manifest manifest() {
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var endpoint : List.of(SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS))
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(400), Duration.ofMillis(100)));
        return new Manifest(CAMPAIGN_ID, HASH, "live-v5", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                2500, 20000, 1_000_000, 20, TARGETS, new AdmissionProfile(Duration.ofSeconds(10), Duration.ofSeconds(1), "",
                new GroupedAdmissionProfile(envelopes, "b".repeat(64), "live-v5")), Duration.ofSeconds(100));
    }

    private static CampaignView campaign(String state, int revision, Set<UUID> stopped) {
        Instant received = NOW.plusSeconds(revision);
        int score = Math.min(revision, 2);
        String projection = "{\"homeScore\":{\"presence\":\"VALUE\",\"value\":{\"display\":{\"presence\":\"VALUE\",\"value\":"
                + score + "}}},\"awayScore\":{\"presence\":\"VALUE\",\"value\":{\"display\":{\"presence\":\"VALUE\",\"value\":0}}}}";
        List<EventView> views = TARGETS.stream().map(target -> {
            UUID attempt = UUID.nameUUIDFromBytes((target.canonicalEventId() + ":" + revision).getBytes(StandardCharsets.UTF_8));
            var refs = new NormalizedReferences(target.sourceObservationId(), null, null, String.format("%064x", score));
            var result = new Result(attempt, new Publication("PARSED", "EVENT", "NONE", received, "event-details-v3",
                    true, "COLLECTING", "inprogress", projection, "event-details-v3", null, null), refs);
            var cursor = new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS, attempt, attempt, attempt, attempt,
                    received, received, NOW.plusSeconds(score), refs, result, result,
                    new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS, received.plusSeconds(100), 100, 0));
            String eventState = !state.equals("RUNNING") || stopped.contains(target.canonicalEventId()) ? "STOPPED_OPERATOR" : "COLLECTING";
            return new EventView(target, eventState, null, 4, 1024, received.plusSeconds(100), List.of(cursor));
        }).toList();
        return new CampaignView(manifest(), state, null, NOW, NOW.plusSeconds(14400), 123 + revision, 45678, revision,
                null, views, List.of(), List.of());
    }

    private static void capture(Page page, String filename) throws Exception {
        Path directory = Path.of(".tmp", "live-pagination-qualification").toAbsolutePath();
        Files.createDirectories(directory);
        page.locator("[data-live-pagination]").first().scrollIntoViewIfNeeded();
        page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve(filename)));
    }

    private record ObservedPost(String path, String origin, int status, String redirect) { }
}
