package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventSearchItem;
import com.bettingproject.sofascorelocal.application.event.J5EventDataPage;
import com.bettingproject.sofascorelocal.application.event.J5EventDataQueryService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportService;
import com.bettingproject.sofascorelocal.application.network.J5ProviderCampaignStopService;
import com.bettingproject.sofascorelocal.application.network.J5RealControlService;
import com.bettingproject.sofascorelocal.application.network.J5RealEventDataService;
import com.bettingproject.sofascorelocal.config.LiveCampaignWebMvcConfiguration;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlState;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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

/** Explicit offline Chromium proof of the production live and manual J5 MVC views.
 * Every browser GET is fulfilled in memory by MockMvc: no HTTP listener, PostgreSQL,
 * provider session or external navigation. Optional screenshots contain synthetic data only.
 */
@WebMvcTest({LiveCampaignController.class, J5EventDataController.class})
@Import({LiveCampaignPresentation.class, LocalFormTokenService.class, LiveCampaignWebMvcConfiguration.class})
class LiveCampaignLineupsBrowserQualificationIT {
    private static final long PROVIDER_EVENT_ID = 900058L;
    private static final UUID EVENT_ID = CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID).value();
    private static final UUID CAMPAIGN_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final String ORIGIN = "http://127.0.0.1:8087";
    private static final String LIVE_PAGE = ORIGIN + "/live-campaigns/" + CAMPAIGN_ID;
    private static final String MANUAL_PAGE = ORIGIN + "/events/" + EVENT_ID + "/statistics";
    private static final String HOME_NAME = "Équipe domicile · synthétique";
    private static final String AWAY_NAME = "Équipe extérieure · synthétique";
    private static final String UNSAFE_NAME = "<img src=x onerror=window.__lineupsInjected=true>";
    private static final String UNSAFE_AWAY = "<script>window.__lineupsInjected=true</script>";

    @Autowired private MockMvc mvc;
    @MockitoBean private LiveCampaignService campaigns;
    @MockitoBean private CanonicalEventStore events;
    @MockitoBean private EventDetailsStore details;
    @MockitoBean private J5EventDataStore data;
    @MockitoBean private CacheManager cacheManager;
    @MockitoBean private J5EventDataQueryService queryService;
    @MockitoBean private J5OfflineFixtureImportService fixtureImportService;
    @MockitoBean private J5RealControlService realControl;
    @MockitoBean private J5RealEventDataService realData;
    @MockitoBean private J5LocalJsonImportService localImport;
    @MockitoBean private J5ProviderCampaignStopService providerStop;

    @Test
    @Timeout(120)
    void lineupsRenderWithoutJavaScriptAndPreserveDisclosuresAndFocusDuringLiveUpdates() throws Exception {
        String configured = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(configured).as("explicit browser cache opt-in").isNotBlank();
        assertThat(Path.of(configured).toRealPath()).isEqualTo(
                Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
        AtomicInteger revision = new AtomicInteger(1);
        when(campaigns.state(CAMPAIGN_ID)).thenAnswer(ignored -> campaign(revision.get()));
        when(campaigns.runtimeStatus(CAMPAIGN_ID)).thenReturn(Optional.empty());
        when(events.findByObservationId(eq(EVENT_ID), anyLong())).thenReturn(Optional.of(identity()));
        when(events.findLatestByCanonicalId(EVENT_ID)).thenReturn(Optional.of(identity()));
        when(data.findByObservationId(eq(EVENT_ID), eq(SofascoreEndpointType.EVENT_LINEUPS), anyLong()))
                .thenAnswer(ignored -> Optional.of(observation(revision.get())));
        when(queryService.find(eq(EVENT_ID), any())).thenAnswer(ignored -> Optional.of(manualPage(revision.get())));
        when(realControl.snapshot()).thenReturn(new J5RealControlSnapshot(J5RealControlState.LOCKED,
                NOW, null, null, null, null, null, null, List.of(), null, false, false,
                List.of("J5_EVENT_DATA_QUALIFICATION_DISABLED")));
        AtomicInteger posts = new AtomicInteger(), external = new AtomicInteger(), stateReads = new AtomicInteger();
        AtomicReference<Throwable> bridgeFailure = new AtomicReference<>();
        List<String> scriptErrors = new ArrayList<>(), policyErrors = new ArrayList<>();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            try (BrowserContext context = browser.newContext(options(false, 1440, 1000))) {
                bridge(context, posts, external, stateReads, bridgeFailure);
                Page page = checkedPage(context, scriptErrors, policyErrors);
                for (String url : List.of(LIVE_PAGE, MANUAL_PAGE)) {
                    Response response = page.navigate(url);
                    assertThat(response.status()).isEqualTo(200);
                    assertThat(response.headerValue("Content-Security-Policy"))
                            .contains("style-src 'self'").doesNotContain("unsafe-inline");
                    Locator host = page.locator("[data-lineups]");
                    assertThat(host.count()).isOne();
                    assertInitialRoster(host);
                    for (Locator disclosure : host.locator("details").all()) assertOpen(disclosure, true);
                    Locator home = team(host, "HOME");
                    Locator starters = section(home, "starters");
                    summary(starters).click();
                    assertOpen(starters, false);
                    assertThat(player(home, 1).isHidden()).isTrue();
                    assertThat(player(home, 6).isVisible()).isTrue();
                    summary(starters).press("Enter");
                    assertOpen(starters, true);
                    summary(team(host, "AWAY")).click();
                    assertOpen(team(host, "AWAY"), false);
                    assertThat(player(team(host, "AWAY"), 11).isHidden()).isTrue();
                    summary(team(host, "AWAY")).press("Space");
                    assertOpen(team(host, "AWAY"), true);
                    assertThat(host.locator("[style]").count()).isZero();
                    page.setViewportSize(390, 844);
                    assertNoHorizontalOverflow(page, host);
                    capture(host, url.equals(LIVE_PAGE) ? "lineups-live-no-js-mobile.png" : "lineups-manual-no-js-mobile.png");
                    page.setViewportSize(1440, 1000);
                }
            }

            try (BrowserContext context = browser.newContext(options(true, 1440, 1000))) {
                bridge(context, posts, external, stateReads, bridgeFailure);
                Page page = checkedPage(context, scriptErrors, policyErrors);
                assertThat(page.navigate(LIVE_PAGE).status()).isEqualTo(200);
                page.waitForFunction("() => typeof window.LineupsView?.update === 'function'");
                Locator host = page.locator("[data-lineups]");
                Locator family = page.locator("[data-live-family='EVENT_LINEUPS']");
                assertInitialRoster(host);
                page.waitForCondition(() -> stateReads.get() > 0);
                assertInertMarkup(page, host);
                capture(host, "lineups-live-desktop.png");

                Locator home = team(host, "HOME"), away = team(host, "AWAY");
                Locator homeStarters = section(home, "starters"), homeSubstitutes = section(home, "substitutes");
                summary(homeSubstitutes).click();
                summary(home).click();
                summary(home).focus();
                assertOpen(home, false);
                assertOpen(homeSubstitutes, false);
                assertOpen(away, true);
                host.evaluate("""
                        root => {
                          window.__lineupsOriginalHost = root;
                          window.__lineupsOriginalDetails = [...root.querySelectorAll('details')];
                          window.__lineupsOriginalPlayers = [...root.querySelectorAll('[data-lineups-player]')]
                            .map(node => [node.getAttribute('data-lineups-player'), node]);
                        }
                        """);

                revision.set(2);
                page.waitForCondition(() -> player(home, 2).locator("[data-lineups-name]").textContent().contains("actualisée"));
                assertThat(host.locator("[data-lineups-confirmation]").textContent()).contains("Confirmée");
                assertOpen(home, false);
                assertOpen(homeSubstitutes, false);
                assertOpen(away, true);
                assertFocused(summary(home));
                assertStableNodes(host);
                assertThat(homeStarters.locator("[data-lineups-player='HOME:6']").count()).isOne();
                assertThat(homeSubstitutes.locator("[data-lineups-player='HOME:4']").count()).isOne();
                assertThat(player(home, 6).locator("[data-lineups-role]").textContent()).isEqualTo("Titulaire");
                assertThat(player(home, 4).locator("[data-lineups-role]").textContent()).isEqualTo("Remplaçant");
                assertInertMarkup(page, host);

                summary(home).press("Enter");
                assertOpen(home, true);
                assertOpen(homeSubstitutes, false);
                summary(homeSubstitutes).focus();
                String unchangedHash = family.locator("[data-live-hash]").textContent();
                String unchangedAt = family.locator("[data-live-changed]").textContent();
                // Observe only the component. Receipt metadata outside it must still advance.
                host.evaluate("""
                        root => {
                          window.__lineupsMutations = [];
                          window.__lineupsObserver = new MutationObserver(records => window.__lineupsMutations.push(...records));
                          window.__lineupsObserver.observe(root, {attributes:true,childList:true,subtree:true,characterData:true});
                        }
                        """);
                revision.set(3); // New receipt and occurrence, identical normalized roster/hash.
                page.waitForCondition(() -> family.locator("[data-live-received]").textContent().equals(NOW.plusSeconds(3).toString()));
                assertThat(family.locator("[data-live-hash]").textContent()).isEqualTo(unchangedHash);
                assertThat(family.locator("[data-live-changed]").textContent()).isEqualTo(unchangedAt);
                assertThat(family.locator("[data-live-occurrence]").textContent()).isEqualTo("1003");
                assertThat(page.evaluate("window.__lineupsMutations.length")).isEqualTo(0);
                page.evaluate("window.__lineupsObserver.disconnect()");
                assertOpen(homeSubstitutes, false);
                assertFocused(summary(homeSubstitutes));
                assertStableNodes(host);
                summary(homeSubstitutes).press("Space");
                assertOpen(homeSubstitutes, true);
                page.setViewportSize(390, 844);
                assertNoHorizontalOverflow(page, host);
                assertInertMarkup(page, host);
                capture(host, "lineups-live-refreshed-mobile.png");

                // An explicitly empty observation is different from a missing family.
                revision.set(4);
                page.waitForCondition(() -> host.locator("[data-lineups-player]").count() == 0);
                assertThat(host.locator("[data-lineups-team]").count()).isEqualTo(2);
                assertThat(host.locator("[data-lineups-confirmation]").textContent()).contains("Provisoire");
                assertThat(team(host, "HOME").locator("[data-lineups-formation]").textContent())
                        .contains("Formation non renseignée");
                assertThat(team(host, "AWAY").locator("[data-lineups-formation]").textContent())
                        .contains("Formation non renseignée");
                assertEmptyRoster(host);
                assertNoHorizontalOverflow(page, host);
                capture(host, "lineups-empty-mobile.png");
            }
            try (BrowserContext context = browser.newContext(options(false, 390, 844))) {
                bridge(context, posts, external, stateReads, bridgeFailure);
                Page page = checkedPage(context, scriptErrors, policyErrors);
                assertThat(page.navigate(MANUAL_PAGE).status()).isEqualTo(200);
                Locator host = page.locator("[data-lineups]");
                assertThat(host.count()).isOne();
                assertEmptyRoster(host);
                assertNoHorizontalOverflow(page, host);
            }
        }
        assertThat(bridgeFailure.get()).isNull();
        assertThat(scriptErrors).isEmpty();
        assertThat(policyErrors).isEmpty();
        assertThat(posts.get()).isZero();
        assertThat(external.get()).isZero();
        assertThat(stateReads.get()).isGreaterThanOrEqualTo(4);
        verify(campaigns, never()).launch(any(), any());
        verify(campaigns, never()).prepareSelection(any());
        verify(campaigns, never()).stop(any(), any());
        verifyNoInteractions(fixtureImportService, realData, localImport, providerStop);
        System.out.println("WO058_LINEUPS_UI=PASS;SSR_LIVE_AND_MANUAL=PASS;NO_JS=PASS;TEAMS=2;"
                + "DISCLOSURE_FOCUS_CHANGED_AND_IDENTICAL=PASS;TEXT_ESCAPING=PASS;EMPTY=PASS;"
                + "VIEWPORT_390=PASS;REAL_PROVIDER_CALLS=0;HTTP_POSTS=0;DATABASE_USED=false");
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
                        AtomicInteger stateReads, AtomicReference<Throwable> failure) {
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
                if (uri.getPath().endsWith("/state")) stateReads.incrementAndGet();
            } catch (Throwable problem) {
                failure.compareAndSet(null, problem); route.abort();
            }
        });
    }

    private static void assertInitialRoster(Locator host) {
        assertThat(host.locator("[data-lineups-team]").count()).isEqualTo(2);
        assertThat(host.locator("[data-lineups-confirmation]").textContent()).contains("Provisoire");
        Locator home = team(host, "HOME"), away = team(host, "AWAY");
        assertThat(home.locator("[data-lineups-team-name]").textContent()).isEqualTo(HOME_NAME);
        assertThat(away.locator("[data-lineups-team-name]").textContent()).isEqualTo(AWAY_NAME);
        assertThat(home.locator("[data-lineups-formation]").textContent()).contains("4-3-3");
        assertThat(away.locator("[data-lineups-formation]").textContent()).contains("Formation non renseignée");
        assertThat(section(home, "starters").locator("[data-lineups-player]").count()).isEqualTo(11);
        assertThat(section(home, "substitutes").locator("[data-lineups-player]").count()).isEqualTo(2);
        assertThat(section(away, "starters").locator("[data-lineups-player]").count()).isEqualTo(11);
        assertThat(section(away, "substitutes").locator("[data-lineups-player]").count()).isOne();
        assertGroupCounts(home, List.of("G", "D", "M", "F", "UNKNOWN"), List.of(1, 4, 3, 2, 1));
        assertGroupCounts(away, List.of("G", "D", "M", "F"), List.of(1, 4, 3, 3));
        assertThat(player(home, 5).locator("[data-lineups-number]").textContent()).isEqualTo("—");
        assertThat(player(home, 5).locator("[data-lineups-position]").textContent()).isEqualTo("Poste non renseigné");
        assertThat(player(home, 7).locator("[data-lineups-number]").textContent()).isEqualTo("—");
        assertThat(player(home, 7).locator("[data-lineups-position]").textContent()).isEqualTo("Poste non renseigné");
        assertThat(host.textContent()).contains(UNSAFE_NAME, UNSAFE_AWAY);
        assertThat(host.locator("img, script, iframe, [onerror], [onclick]").count()).isZero();
    }

    private static void assertInertMarkup(Page page, Locator host) {
        assertThat(host.textContent()).contains(UNSAFE_NAME, UNSAFE_AWAY);
        assertThat(host.locator("img, script, iframe, [onerror], [onclick]").count()).isZero();
        assertThat(page.evaluate("window.__lineupsInjected === undefined")).isEqualTo(true);
    }

    private static void assertGroupCounts(Locator team, List<String> positions, List<Integer> counts) {
        for (int index = 0; index < positions.size(); index++)
            assertThat(team.locator("[data-lineups-group='" + positions.get(index) + "'] [data-lineups-player]").count())
                    .as("roster grouping by the observed position only").isEqualTo(counts.get(index));
    }

    private static void assertEmptyRoster(Locator host) {
        assertThat(host.locator("[data-lineups-player], [data-lineups-group]").count()).isZero();
        assertThat(host.locator("[data-lineups-team]").count()).isEqualTo(2);
        for (String side : List.of("HOME", "AWAY")) {
            Locator team = team(host, side);
            assertThat(team.locator("[data-lineups-starters-empty]").isVisible()).isTrue();
            assertThat(team.locator("[data-lineups-starters-empty]").textContent()).isEqualTo("Aucun titulaire renseigné.");
            assertThat(team.locator("[data-lineups-substitutes-empty]").isVisible()).isTrue();
            assertThat(team.locator("[data-lineups-substitutes-empty]").textContent()).isEqualTo("Aucun remplaçant renseigné.");
            assertThat(team.locator("[data-lineups-count]").allTextContents()).containsExactly("0", "0");
        }
    }

    private static void assertStableNodes(Locator host) {
        assertThat(host.evaluate("root => root === window.__lineupsOriginalHost")).isEqualTo(true);
        assertThat(host.evaluate("""
                root => window.__lineupsOriginalDetails.every(node => root.contains(node))
                  && window.__lineupsOriginalPlayers.every(([key, node]) =>
                    [...root.querySelectorAll('[data-lineups-player]')].find(candidate =>
                      candidate.getAttribute('data-lineups-player') === key) === node)
                """)).isEqualTo(true);
    }

    private static void assertNoHorizontalOverflow(Page page, Locator host) {
        assertThat(((Number) host.evaluate("element => element.getBoundingClientRect().width")).doubleValue())
                .isLessThanOrEqualTo(390);
        assertThat(host.evaluate("element => element.scrollWidth <= element.clientWidth + 1")).isEqualTo(true);
        assertThat(page.evaluate("document.documentElement.scrollWidth <= innerWidth + 1")).isEqualTo(true);
    }

    private static Locator team(Locator host, String side) { return host.locator("[data-lineups-team='" + side + "']"); }
    private static Locator section(Locator team, String kind) { return team.locator("[data-lineups-section='" + kind + "']"); }
    private static Locator player(Locator team, long id) {
        return team.locator("[data-lineups-player='" + team.getAttribute("data-lineups-team") + ":" + id + "']");
    }
    private static Locator summary(Locator details) { return details.locator(":scope > summary"); }
    private static void assertFocused(Locator locator) {
        assertThat(locator.evaluate("element => document.activeElement === element")).isEqualTo(true);
    }
    private static void assertOpen(Locator details, boolean open) {
        assertThat(details.count()).isOne();
        assertThat(details.evaluate("element => element.tagName")).isEqualTo("DETAILS");
        assertThat(details.evaluate("element => element.open")).isEqualTo(open);
    }
    private static void capture(Locator host, String name) throws Exception {
        Path directory = Path.of(".tmp", "lineups-ui-qualification").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        host.screenshot(new Locator.ScreenshotOptions().setPath(directory.resolve(name)));
    }

    private static Manifest manifest() {
        return new Manifest(CAMPAIGN_ID, HASH, "live-v3", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, 1_000_000, 1, List.of(new Target(EVENT_ID, PROVIDER_EVENT_ID, 1, 1)));
    }
    private static UUID attemptId(int revision) { return new UUID(0, 5900L + revision); }
    private static int contentRevision(int revision) { return revision == 3 ? 2 : revision; }
    private static String revisionHash(int revision) { return Integer.toHexString(contentRevision(revision)).repeat(64); }

    private static CampaignView campaign(int revision) {
        Instant received = NOW.plusSeconds(revision), changed = NOW.plusSeconds(contentRevision(revision));
        UUID attemptId = attemptId(revision);
        var refs = new NormalizedReferences(null, null, 10L + contentRevision(revision), revisionHash(revision));
        var result = new Result(attemptId, new Publication("PARSED", "EVENT", "OK", received,
                "event-lineups-v1", true, "COLLECTING", null, null, null,
                revision == 4 ? "EMPTY_VALID" : "PARTIAL", revision == 4 ? 100 : 95), refs);
        var family = new FamilyCursor(SofascoreEndpointType.EVENT_LINEUPS, attemptId, attemptId,
                attemptId, attemptId(contentRevision(revision)), received, received, changed, refs, result, result);
        var attempt = new AttemptView(new ReservedAttempt(attemptId, EVENT_ID, PROVIDER_EVENT_ID,
                SofascoreEndpointType.EVENT_LINEUPS, revision, "J5_NORMAL", NOW, NOW, false), received,
                100L + contentRevision(revision), 1000L + revision, received, result);
        return new CampaignView(manifest(), "RUNNING", null, NOW, NOW.plusSeconds(14400), revision, 100, revision, null,
                List.of(new EventView(manifest().targets().getFirst(), "COLLECTING", null,
                        revision, 100, NOW.plusSeconds(60), List.of(family))), List.of(attempt), List.of());
    }

    private static CanonicalEventObservationView identity() {
        return new CanonicalEventObservationView(1, CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID), NOW,
                new ScheduledTeam(11, HOME_NAME), new ScheduledTeam(22, AWAY_NAME),
                new ScheduledEventStatus("inprogress", Optional.of("2nd half")), Optional.empty(),
                EventSourceTrace.providerSnapshot(1, HASH, "event-details-v2", NOW), HASH, 1);
    }

    private static J5EventDataPage manualPage(int revision) {
        return new J5EventDataPage(ZoneId.of("Europe/Paris"),
                new J4EventSearchItem(identity(), NOW.atZone(ZoneId.of("Europe/Paris"))),
                new J5EventDataBundle(Optional.empty(), Optional.empty(), Optional.of(observation(revision))));
    }

    private static J5EventDataObservationView observation(int revision) {
        int content = contentRevision(revision);
        EventLineups lineups = content == 4
                ? new EventLineups(PROVIDER_EVENT_ID, false,
                    new TeamLineup(LineupSide.HOME, Optional.empty(), List.of()),
                    new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of()))
                : new EventLineups(PROVIDER_EVENT_ID, content > 1,
                    new TeamLineup(LineupSide.HOME, Optional.of("4-3-3"), List.of(
                            lineupPlayer(1, "Malo Gardien synthétique", 1, "G", true),
                            lineupPlayer(2, content > 1 ? "Inès Défense actualisée" : "Inès Défense synthétique", 4, "D", true),
                            lineupPlayer(3, "Alex Milieu synthétique", 8, "M", true),
                            lineupPlayer(4, "Sam Attaque synthétique", 9, "F", content == 1),
                            lineupPlayer(5, UNSAFE_NAME, null, null, true),
                            lineupPlayer(6, "Noé Banc synthétique", 19, "F", content > 1),
                            lineupPlayer(7, "Lou Données absentes", null, null, false),
                            lineupPlayer(8, "Julien Arrière synthétique", 5, "D", true),
                            lineupPlayer(9, "Ari Latéral synthétique", 2, "D", true),
                            lineupPlayer(10, "Claude Défense synthétique", 3, "D", true),
                            lineupPlayer(16, "Max Récupération synthétique", 6, "M", true),
                            lineupPlayer(17, "Morgan Création synthétique", 10, "M", true),
                            lineupPlayer(18, "Andrea Ailier synthétique", 11, "F", true))),
                    new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of(
                            lineupPlayer(11, "Charlie Gardien extérieur", 1, "G", true),
                            lineupPlayer(12, "Robin Défense extérieure", 5, "D", true),
                            lineupPlayer(13, "Camille Milieu extérieur", 6, "M", true),
                            lineupPlayer(14, UNSAFE_AWAY, 10, "F", true),
                            lineupPlayer(15, "Sacha Banc extérieur", 20, "D", false),
                            lineupPlayer(21, "Élie Latéral extérieur", 2, "D", true),
                            lineupPlayer(22, "Jo Arrière extérieur", 3, "D", true),
                            lineupPlayer(23, "Dominique Défense extérieure", 4, "D", true),
                            lineupPlayer(24, "Yan Milieu extérieur", 8, "M", true),
                            lineupPlayer(25, "Léon Création extérieure", 7, "M", true),
                            lineupPlayer(26, "Alix Attaque extérieure", 9, "F", true),
                            lineupPlayer(27, "Louison Ailier extérieur", 11, "F", true))));
        J5CompletenessReport completeness = content == 4 ? J5CompletenessReport.emptyValid()
                : J5CompletenessReport.measured(19, 20, List.of("$.synthetic.home.players[4].position"));
        return new J5EventDataObservationView(10L + content, CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID), lineups,
                EventSourceTrace.providerSnapshot(100L + content, revisionHash(revision), "event-lineups-v1", NOW.plusSeconds(content)),
                completeness, revisionHash(revision));
    }

    private static EventLineupPlayer lineupPlayer(long id, String name, Integer number, String position, boolean starter) {
        return new EventLineupPlayer(id, name, Optional.ofNullable(number), Optional.ofNullable(position), starter);
    }
}
