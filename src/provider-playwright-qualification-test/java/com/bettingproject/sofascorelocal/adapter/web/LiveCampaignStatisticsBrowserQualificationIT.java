package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.config.LiveCampaignWebMvcConfiguration;
import com.bettingproject.sofascorelocal.domain.event.*;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Explicit offline Chromium qualification of production MVC, templates and assets.
 * All data is synthetic. Every GET is fulfilled in memory by MockMvc; no HTTP listener,
 * operator database, provider session or external browser navigation is used.
 */
@WebMvcTest(LiveCampaignController.class)
@Import({LiveCampaignPresentation.class, LocalFormTokenService.class, LiveCampaignWebMvcConfiguration.class})
class LiveCampaignStatisticsBrowserQualificationIT {
    private static final long PROVIDER_EVENT_ID = 900001L;
    private static final UUID EVENT_ID = CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID).value();
    private static final UUID CAMPAIGN_ID = UUID.fromString("00000000-0000-0000-0000-000000000058");
    private static final UUID ATTEMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000059");
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final String ORIGIN = "http://127.0.0.1:8087";
    private static final String PAGE = ORIGIN + "/live-campaigns/" + CAMPAIGN_ID;
    private static final String INERT_MARKUP = "<img src=x onerror=alert(1)>";
    @Autowired private MockMvc mvc;
    @MockitoBean private LiveCampaignService campaigns;
    @MockitoBean private CanonicalEventStore events;
    @MockitoBean private J5EventDataStore data;
    @MockitoBean private CacheManager cacheManager;

    @Test
    @Timeout(120)
    void actualStatisticsRenderWithoutScriptsAndKeepPeriodFocusAcrossOfflineRefreshes() throws Exception {
        String configured = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(configured).as("explicit browser cache opt-in").isNotBlank();
        assertThat(Path.of(configured).toRealPath()).isEqualTo(
                Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
        AtomicInteger revision = new AtomicInteger(1);
        when(campaigns.state(CAMPAIGN_ID)).thenAnswer(ignored -> campaign(revision.get()));
        when(campaigns.runtimeStatus(CAMPAIGN_ID)).thenReturn(Optional.empty());
        when(events.findByObservationId(any(), anyLong())).thenReturn(Optional.of(identity()));
        when(data.findByObservationId(eq(EVENT_ID), eq(SofascoreEndpointType.EVENT_STATISTICS), anyLong()))
                .thenAnswer(ignored -> Optional.of(statistics(revision.get())));
        AtomicInteger posts = new AtomicInteger(), external = new AtomicInteger();
        AtomicReference<Throwable> bridgeFailure = new AtomicReference<>();
        List<String> scriptErrors = new ArrayList<>(), policyErrors = new ArrayList<>();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            try (BrowserContext context = browser.newContext(options(false, 1440, 1000))) {
                bridge(context, posts, external, bridgeFailure);
                Page page = checkedPage(context, scriptErrors, policyErrors);
                Response response = page.navigate(PAGE);
                assertThat(response.status()).isEqualTo(200);
                assertThat(response.headerValue("Content-Security-Policy"))
                        .contains("style-src 'self'").doesNotContain("unsafe-inline");
                Locator host = page.locator("[data-statistics]");
                assertThat(host.count()).isOne();
                assertThat(host.locator("[data-stat-control]").isHidden()).isTrue();
                for (String period : List.of("ALL", "1ST", "2ND"))
                    assertThat(period(host, period).isVisible()).isTrue();
                assertThat(metric(period(host, "ALL"), "Possession").locator(".statistics-home strong").textContent()).isEqualTo("50%");
                assertMeterValue(metric(period(host, "ALL"), "Possession").locator("meter.statistics-possession"), 50);
                assertThat(host.locator("[style]").count()).as("SSR remains compatible with style-src self").isZero();
                assertSpecialValues(period(host, "1ST"));
                capture(host, "statistics-runtime-ssr-desktop.png");
            }

            try (BrowserContext context = browser.newContext(options(true, 1440, 1000))) {
                bridge(context, posts, external, bridgeFailure);
                Page page = checkedPage(context, scriptErrors, policyErrors);
                assertThat(page.navigate(PAGE).status()).isEqualTo(200);
                Locator host = page.locator("[data-statistics]");
                Locator selector = host.locator("[data-stat-select]");
                page.waitForCondition(() -> selector.isVisible());
                assertThat(selector.locator("option").allTextContents())
                        .containsExactly("ALL · Ensemble du match", "1ST · Première période", "2ND · Seconde période");
                assertThat(selector.inputValue()).isEqualTo("ALL");
                assertThat(period(host, "ALL").isVisible()).isTrue();
                assertThat(period(host, "1ST").isHidden()).isTrue();
                assertThat(period(host, "2ND").isHidden()).isTrue();
                assertThat(metric(period(host, "ALL"), "Buts attendus").locator(".statistics-home strong").textContent())
                        .isEqualTo("0.00");
                assertThat(metric(period(host, "ALL"), "Buts attendus").locator(".statistics-away strong").textContent())
                        .isEqualTo("0.16");
                capture(host, "statistics-runtime-desktop.png");

                selector.selectOption("1ST");
                selector.focus();
                assertSpecialValues(period(host, "1ST"));
                assertThat(metric(period(host, "1ST"), "Dribbles").locator("meter.statistics-track").count()).isEqualTo(2);
                assertMeterValue(metric(period(host, "1ST"), "Dribbles").locator(".statistics-home meter"), 44.444444);
                assertMeterValue(metric(period(host, "1ST"), "Dribbles").locator(".statistics-away meter"), 75);
                revision.set(2);
                page.waitForCondition(() -> "2".equals(metric(period(host, "1ST"), "Tirs").locator(".statistics-home strong").textContent()));
                assertThat(selector.inputValue()).isEqualTo("1ST");
                assertThat(selector.evaluate("element => document.activeElement === element")).isEqualTo(true);
                assertSpecialValues(period(host, "1ST"));

                page.setViewportSize(390, 844);
                assertThat(((Number) host.evaluate("element => element.getBoundingClientRect().width")).doubleValue()).isLessThanOrEqualTo(390);
                assertThat(host.evaluate("element => element.scrollWidth <= element.clientWidth + 1")).isEqualTo(true);
                assertThat(page.evaluate("document.documentElement.scrollWidth <= innerWidth + 1")).isEqualTo(true);
                capture(host, "statistics-runtime-mobile.png");

                selector.selectOption("2ND");
                Locator incompletePossession = metric(period(host, "2ND"), "Possession");
                assertThat(incompletePossession.locator(".statistics-home strong").textContent()).isEqualTo("—");
                assertThat(incompletePossession.locator("meter").count()).isZero();
                revision.set(3);
                page.waitForCondition(() -> selector.locator("option").count() == 2);
                assertThat(selector.inputValue()).isEqualTo("ALL");
                assertThat(period(host, "ALL").isVisible()).isTrue();
                assertThat(host.locator("[data-stat-period='2ND']").count()).isZero();
            }
        }
        assertThat(bridgeFailure.get()).isNull();
        assertThat(scriptErrors).isEmpty();
        assertThat(policyErrors).isEmpty();
        assertThat(posts.get()).isZero();
        assertThat(external.get()).isZero();
        verify(campaigns, never()).launch(any(), any());
        verify(campaigns, never()).prepareSelection(any());
        verify(campaigns, never()).stop(any(), any());
    }

    private static Browser.NewContextOptions options(boolean javaScript, int width, int height) {
        return new Browser.NewContextOptions().setOffline(true).setAcceptDownloads(false)
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK).setJavaScriptEnabled(javaScript).setViewportSize(width, height);
    }

    private static Page checkedPage(BrowserContext context, List<String> scriptErrors, List<String> policyErrors) {
        Page page = context.newPage();
        page.onPageError(scriptErrors::add);
        page.onConsoleMessage(message -> {
            if (message.type().equals("error") && message.text().contains("Content Security Policy"))
                policyErrors.add(message.text());
        });
        return page;
    }

    private void bridge(BrowserContext context, AtomicInteger posts, AtomicInteger external,
                        AtomicReference<Throwable> failure) {
        MockHttpSession session = new MockHttpSession();
        context.route("**/*", route -> {
            Request browserRequest = route.request();
            if (!browserRequest.url().startsWith(ORIGIN + "/")) {
                external.incrementAndGet(); route.abort(); return;
            }
            if (!browserRequest.method().equals("GET")) {
                posts.incrementAndGet(); route.abort(); return;
            }
            try {
                URI uri = URI.create(browserRequest.url());
                var request = MockMvcRequestBuilders.request(HttpMethod.GET, uri).session(session);
                browserRequest.allHeaders().forEach((name, value) -> {
                    if (!name.equalsIgnoreCase("host")) request.header(name, value);
                });
                request.header("Host", uri.getRawAuthority());
                var response = mvc.perform(request).andReturn().getResponse();
                var headers = new LinkedHashMap<String, String>();
                for (String name : response.getHeaderNames()) headers.put(name, response.getHeader(name));
                route.fulfill(new Route.FulfillOptions().setStatus(response.getStatus()).setHeaders(headers)
                        .setBodyBytes(response.getContentAsByteArray()));
            } catch (Throwable problem) {
                failure.compareAndSet(null, problem); route.abort();
            }
        });
    }

    private static Locator period(Locator host, String code) { return host.locator("[data-stat-period='" + code + "']"); }
    private static Locator metric(Locator period, String label) {
        return period.locator(".statistics-metric").filter(new Locator.FilterOptions().setHasText(label));
    }

    private static void assertSpecialValues(Locator period) {
        Locator missing = metric(period, "Corners");
        assertThat(missing.locator(".statistics-home strong").textContent()).isEqualTo("—");
        assertThat(missing.locator(".statistics-home").getAttribute("data-stat-value-state")).isEqualTo("MISSING");
        assertThat(missing.locator(".statistics-away strong").textContent()).isEqualTo("0");
        assertThat(missing.locator(".statistics-away").getAttribute("data-stat-value-state")).isEqualTo("VALUE");
        Locator undefined = metric(period, "Tacles");
        assertThat(undefined.locator(".statistics-home strong").textContent()).isEqualTo("0/0");
        assertThat(undefined.locator(".statistics-home").getAttribute("data-stat-value-state")).isEqualTo("UNDEFINED");
        assertThat(undefined.locator(".statistics-home meter").count()).isZero();
        assertMeterValue(undefined.locator(".statistics-away meter"), 0);
        assertThat(metric(period, "Ratio incohérent").locator("meter").count()).isZero();
        assertThat(period.textContent()).contains(INERT_MARKUP, "<script>alert(1)</script>");
        assertThat(period.locator("img,script").count()).isZero();
    }

    private static void assertMeterValue(Locator meter, double expected) {
        assertThat(meter.count()).isOne();
        assertThat(Double.parseDouble(meter.getAttribute("value"))).isEqualTo(expected);
        assertThat(meter.isVisible()).isTrue();
    }

    private static void capture(Locator host, String file) throws Exception {
        Path directory = Path.of("target", "ui-qualification").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        host.screenshot(new Locator.ScreenshotOptions().setPath(directory.resolve(file)));
    }

    private static Manifest manifest() {
        return new Manifest(CAMPAIGN_ID, HASH, "live-v3", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, 1_000_000, 1, List.of(new Target(EVENT_ID, PROVIDER_EVENT_ID, 1, 1)));
    }

    private static CampaignView campaign(int revision) {
        Instant received = NOW.plusSeconds(revision);
        var refs = new NormalizedReferences(null, null, 10L + revision, revisionHash(revision));
        var result = new Result(ATTEMPT_ID, new Publication("PARSED", "EVENT", "OK", received,
                "event-statistics-v2", true, "COLLECTING", null, null, null, "PARTIAL", 95), refs);
        var family = new FamilyCursor(SofascoreEndpointType.EVENT_STATISTICS, ATTEMPT_ID, ATTEMPT_ID,
                ATTEMPT_ID, ATTEMPT_ID, received, received, received, refs, result, result);
        var attempt = new AttemptView(new ReservedAttempt(ATTEMPT_ID, EVENT_ID, PROVIDER_EVENT_ID,
                SofascoreEndpointType.EVENT_STATISTICS, 1, "J5_NORMAL", NOW, NOW, false), received, 2L + revision, 4L + revision, received, result);
        return new CampaignView(manifest(), "RUNNING", null, NOW, NOW.plusSeconds(14400), 1, 100, revision, null,
                List.of(new EventView(manifest().targets().getFirst(), "COLLECTING", null,
                        1, 100, NOW.plusSeconds(60), List.of(family))), List.of(attempt), List.of());
    }

    private static CanonicalEventObservationView identity() {
        return new CanonicalEventObservationView(1, CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID), NOW,
                new ScheduledTeam(11, "Équipe domicile · synthétique"), new ScheduledTeam(22, "Équipe extérieure · synthétique"),
                new ScheduledEventStatus("inprogress", Optional.of("2nd half")), Optional.empty(),
                EventSourceTrace.providerSnapshot(1, HASH, "event-details-v2", NOW), HASH, 1);
    }

    private static J5EventDataObservationView statistics(int revision) {
        List<EventStatisticMetric> metrics = new ArrayList<>(List.of(
                value("ALL", "ballPossession", "Possession", "50%", "50%"),
                value("ALL", "expectedGoals", "Buts attendus", "0.00", "0.16"),
                value("ALL", "bigChanceCreated", "Grandes occasions", "0", "1"),
                value("ALL", "totalShotsOnGoal", "Tirs", "0", "5"),
                value("ALL", "goalkeeperSaves", "Arrêts", "1", "0"),
                value("ALL", "cornerKicks", "Corners", "0", "2"),
                value("ALL", "fouls", "Fautes", "1", "3"),
                value("ALL", "passes", "Passes", "78", "73"),
                value("ALL", "totalTackles", "Tacles", "1", "5"),
                value("ALL", "freeKicks", "Coups francs", "3", "1"),
                value("ALL", "yellowCards", "Cartons jaunes", "0", "1"),
                value("1ST", "ballPossession", "Possession", "60%", "40%"),
                value("1ST", "dribbles", "Dribbles", "4/9 (44%)", "3/4 (75%)"),
                value("1ST", "cornerKicks", "Corners", null, "0"),
                value("1ST", "totalTackles", "Tacles", "0/0", "0/4"),
                value("1ST", "totalShotsOnGoal", "Tirs", revision > 1 ? "2" : "1", "0"),
                value("1ST", "invalidRatio", "Ratio incohérent", "5/4", "1/2 (90%)"),
                value("1ST", "inertMarkup", INERT_MARKUP, "<script>alert(1)</script>", "3")));
        if (revision < 3) {
            metrics.add(value("2ND", "ballPossession", "Possession", null, "50%"));
            metrics.add(value("2ND", "totalShotsOnGoal", "Tirs", "3", "5"));
        }
        int missing = revision < 3 ? 2 : 1;
        List<String> paths = revision < 3 ? List.of("$.synthetic.firstHalf.corners.home", "$.synthetic.secondHalf.possession.home")
                : List.of("$.synthetic.firstHalf.corners.home");
        return new J5EventDataObservationView(10L + revision, CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID),
                new EventStatistics(PROVIDER_EVENT_ID, metrics),
                EventSourceTrace.providerSnapshot(2L + revision, revisionHash(revision), "event-statistics-v2", NOW.plusSeconds(revision)),
                J5CompletenessReport.measured(metrics.size() * 2 - missing, metrics.size() * 2, paths), revisionHash(revision));
    }

    private static String revisionHash(int revision) { return Integer.toHexString(revision).repeat(64); }

    private static EventStatisticMetric value(String period, String code, String label, String home, String away) {
        return new EventStatisticMetric(period, "Données synthétiques · vérification de l’interface", code, label,
                Optional.ofNullable(home), Optional.ofNullable(away));
    }
}
