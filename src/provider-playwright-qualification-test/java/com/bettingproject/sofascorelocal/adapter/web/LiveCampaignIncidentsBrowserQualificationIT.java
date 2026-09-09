package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import com.bettingproject.sofascorelocal.application.network.J3TournamentCatalogService;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryControlService;
import com.bettingproject.sofascorelocal.application.retention.J6RawPayloadRetentionService;
import com.bettingproject.sofascorelocal.application.retention.J6RetentionError;
import com.bettingproject.sofascorelocal.application.retention.J6RetentionException;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotInspectionCatalog;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspectionService;
import com.bettingproject.sofascorelocal.config.LiveCampaignWebMvcConfiguration;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalog;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.LiveDiagnosticStore;
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
import java.time.LocalDate;
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

/** Explicit Chromium qualification of production MVC/SSR and polling. Every GET is
 * intercepted and fulfilled by MockMvc in memory: no socket on 8087, database or provider.
 * All screenshots and incidents are synthetic; no browser traces or profiles are retained.
 */
@WebMvcTest({LiveCampaignController.class, DashboardController.class})
@Import({LiveCampaignPresentation.class, LocalFormTokenService.class, LiveCampaignWebMvcConfiguration.class})
class LiveCampaignIncidentsBrowserQualificationIT {
    private static final long PROVIDER_EVENT_ID = 900059L;
    private static final UUID EVENT_ID = CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID).value();
    private static final UUID CAMPAIGN_ID = UUID.fromString("00000000-0000-0000-0000-000000005859");
    private static final String HASH = "a".repeat(64);
    private static final String HOME_NAME = "Domicile synthétique", AWAY_NAME = "Extérieur synthétique";
    private static final String UNSAFE_TEXT = "<img src=x onerror=window.__incidentsInjected=true>";
    private static final String LONG_TEXT = "Observation fournisseur synthétique " + "x".repeat(150);
    private static final Instant NOW = Instant.now().minusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private static final String ORIGIN = "http://127.0.0.1:8087";
    private static final String PAGE_PATH = "/live-campaigns/" + CAMPAIGN_ID;

    @Autowired private MockMvc mvc;
    @MockitoBean private LiveCampaignService campaigns;
    @MockitoBean private CanonicalEventStore events;
    @MockitoBean private EventDetailsStore details;
    @MockitoBean private J5EventDataStore data;
    @MockitoBean private LiveDiagnosticStore diagnostics;
    @MockitoBean private CacheManager cacheManager;
    @MockitoBean private DashboardService dashboard;
    @MockitoBean private J3ManualCallControlService manualControl;
    @MockitoBean private J3ManualCollectionEvidenceService collectionEvidence;
    @MockitoBean private J3TournamentCatalogService tournamentCatalog;
    @MockitoBean private TournamentEventDiscoveryControlService discoveryControl;
    @MockitoBean private RawSnapshotJsonInspectionService snapshotInspection;
    @MockitoBean private J6RawPayloadRetentionService retention;

    @Test
    @Timeout(60)
    void dashboardRemainsReadableWhenProviderActivityPreventsRetentionPreview() throws Exception {
        requireBrowserOptIn();
        when(dashboard.load()).thenReturn(new DashboardView(NOW.toString(), "EXPERIMENTAL",
                "LOCKED_OFFLINE_J3_POLICY", false, false, "127.0.0.1:8087", "NON_CONFIGURED", 1, "3 s",
                "AVAILABLE", "40", 60, 0, new DashboardView.FixtureCorpusView("AVAILABLE_OFFLINE", "SCHEDULED_EVENTS",
                "SYNTHETIC", true, "scheduled-events-v1", 12, 12, 7, 4, 1, 0), null, List.of()));
        when(manualControl.snapshot()).thenReturn(new J3ManualCallControlSnapshot(true, J3CircuitState.LOCKED,
                J3CircuitReason.STARTUP_LOCK, NOW, null, LocalDate.parse("2026-09-08"), null, false, false,
                List.of("CONNECTOR_GATE_LOCKED")));
        when(collectionEvidence.latestDocument()).thenReturn(Optional.empty());
        when(tournamentCatalog.latest()).thenReturn(J3TournamentCatalog.unavailable(
                J3TournamentCatalogStatus.NO_COLLECTION_EVIDENCE, Optional.empty()));
        when(discoveryControl.snapshot()).thenReturn(new TournamentEventDiscoveryControlSnapshot(
                TournamentEventDiscoveryState.LOCKED, NOW, null, null, null, null, null, null, null,
                false, false, List.of("TOURNAMENT_EVENT_DISCOVERY_DISABLED")));
        when(snapshotInspection.loadCatalog()).thenReturn(RawSnapshotInspectionCatalog.unavailable());
        when(retention.preview()).thenThrow(new J6RetentionException(J6RetentionError.PROVIDER_CAMPAIGN_ACTIVE));
        AtomicInteger external = new AtomicInteger(), posts = new AtomicInteger(), stateReads = new AtomicInteger();
        AtomicReference<Throwable> bridgeFailure = new AtomicReference<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            for (int width : List.of(1440, 390)) {
                try (BrowserContext context = browser.newContext(options(false, width))) {
                    bridge(context, external, posts, stateReads, bridgeFailure);
                    Page page = context.newPage();
                    assertThat(page.navigate(ORIGIN + "/").status()).isEqualTo(200);
                    assertThat(page.locator("h1").textContent()).isEqualTo("Tableau de bord local");
                    Locator panel = page.locator("#j6-retention-preview");
                    assertThat(panel.isVisible()).isTrue();
                    assertThat(panel.textContent()).contains("une session fournisseur est active ou sa clôture reste à finaliser",
                            "Actualisez le tableau de bord une fois la session clôturée", "Aucun plan de rétention n’a été calculé")
                            .doesNotContain("Payloads éligibles", "Octets éligibles", "Plan SHA-256");
                    assertThat(panel.locator("button, form").count()).isZero();
                    assertThat(panel.evaluate("element => element.scrollWidth <= element.clientWidth + 1")).isEqualTo(true);
                    capture(panel, "dashboard-retention-provider-busy-" + width + ".png");
                }
            }
        }
        assertThat(bridgeFailure.get()).isNull();
        assertThat(external.get()).isZero();
        assertThat(posts.get()).isZero();
        assertThat(stateReads.get()).isZero();
        verify(retention, times(2)).preview();
        verifyNoMoreInteractions(retention);
        assertNoCampaignMutation();
        System.out.println("WO058_DASHBOARD_PROVIDER_BUSY=PASS;SSR_HTTP_200=PASS;RETENTION_NOT_ACTIONABLE=PASS;"
                + "VIEWPORTS_1440_390=PASS;REAL_PROVIDER_CALLS=0;HTTP_POSTS=0;DATABASE_USED=false");
    }

    @Test
    @Timeout(90)
    void unavailableIncidentsRemainDistinctFromAnEmptyListAndKeepTheLastReadableDataAfter404() throws Exception {
        requireBrowserOptIn();
        AtomicInteger revision = new AtomicInteger(1);
        AtomicReference<String> mode = new AtomicReference<>("UNAVAILABLE");
        when(campaigns.state(CAMPAIGN_ID)).thenAnswer(ignored -> availabilityCampaign(revision.get(), mode.get()));
        when(campaigns.runtimeStatus(CAMPAIGN_ID)).thenReturn(Optional.empty());
        when(events.findByObservationId(eq(EVENT_ID), anyLong())).thenReturn(Optional.of(identity()));
        when(data.findByObservationId(eq(EVENT_ID), any(), anyLong())).thenAnswer(invocation -> {
            SofascoreEndpointType endpoint = invocation.getArgument(1);
            long observationId = invocation.getArgument(2);
            if (endpoint != SofascoreEndpointType.EVENT_INCIDENTS) return Optional.of(observation(endpoint, 1));
            if (observationId == 102) return Optional.of(observation(endpoint, 2));
            return Optional.of(new J5EventDataObservationView(observationId,
                    CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID), new EventIncidents(PROVIDER_EVENT_ID, List.of()),
                    EventSourceTrace.providerSnapshot(observationId, HASH, "event-incidents-v17", NOW),
                    observationId == 104 ? J5CompletenessReport.emptyValid() : J5CompletenessReport.unavailable(), HASH));
        });
        AtomicInteger external = new AtomicInteger(), posts = new AtomicInteger(), stateReads = new AtomicInteger();
        AtomicReference<Throwable> bridgeFailure = new AtomicReference<>();
        List<String> scriptErrors = new ArrayList<>(), policyErrors = new ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            for (String value : List.of("EMPTY", "UNAVAILABLE")) {
                mode.set(value);
                try (BrowserContext context = browser.newContext(options(false, 390))) {
                    bridge(context, external, posts, stateReads, bridgeFailure);
                    Page page = checkedPage(context, scriptErrors, policyErrors);
                    assertThat(page.navigate(ORIGIN + PAGE_PATH).status()).isEqualTo(200);
                    assertAvailability(page, value);
                    capture(incidentFamily(page), "incidents-ssr-" + value.toLowerCase(java.util.Locale.ROOT) + "-390.png");
                }
            }
            assertThat(stateReads.get()).isZero();
            try (BrowserContext context = browser.newContext(options(true, 390))) {
                bridge(context, external, posts, stateReads, bridgeFailure);
                Page page = checkedPage(context, scriptErrors, policyErrors);
                assertThat(page.navigate(ORIGIN + PAGE_PATH).status()).isEqualTo(200);
                page.waitForCondition(() -> stateReads.get() > 0);
                assertAvailability(page, "UNAVAILABLE");
                for (String value : List.of("EMPTY", "FULL", "PREVIOUS_404", "UNAVAILABLE", "EMPTY")) {
                    mode.set(value);
                    int current = revision.incrementAndGet();
                    page.waitForCondition(() -> NOW.plusSeconds(current).toString().equals(
                            incidentFamily(page).locator("[data-live-received]").textContent()));
                    assertAvailability(page, value);
                    if (value.equals("FULL")) technical(page).locator(":scope > summary").click();
                    if (value.equals("PREVIOUS_404")) {
                        assertThat(technical(page).evaluate("element => element.open")).isEqualTo(true);
                        capture(incidents(page), "incidents-live-last-readable-after-404-390.png");
                    }
                }
                assertThat(technical(page).evaluate("element => element.open"))
                        .as("a removed technical panel is recreated closed for an identical valid empty list").isEqualTo(false);
            }
        }
        assertThat(bridgeFailure.get()).isNull();
        assertThat(scriptErrors).isEmpty();
        assertThat(policyErrors).isEmpty();
        assertThat(external.get()).isZero();
        assertThat(posts.get()).isZero();
        assertNoCampaignMutation();
        System.out.println("WO058_INCIDENT_AVAILABILITY=PASS;SSR_AND_POLLING=PASS;UNAVAILABLE_NOT_EMPTY=PASS;"
                + "PREVIOUS_READABLE_404=PASS;TECHNICAL_PANEL_RECREATED=PASS;REAL_PROVIDER_CALLS=0;HTTP_POSTS=0;DATABASE_USED=false");
    }

    @Test
    @Timeout(120)
    void incidentsRemainReadableWithoutJavaScriptAndRefreshWithoutLosingOccurrencesOrOtherFamilyFocus() throws Exception {
        requireBrowserOptIn();
        AtomicInteger revision = new AtomicInteger(1);
        when(campaigns.state(CAMPAIGN_ID)).thenAnswer(ignored -> campaign(revision.get()));
        when(campaigns.runtimeStatus(CAMPAIGN_ID)).thenReturn(Optional.empty());
        when(events.findByObservationId(eq(EVENT_ID), anyLong())).thenReturn(Optional.of(identity()));
        when(data.findByObservationId(eq(EVENT_ID), any(), anyLong())).thenAnswer(invocation -> {
            SofascoreEndpointType endpoint = invocation.getArgument(1);
            long observationId = invocation.getArgument(2);
            return Optional.of(observation(endpoint, endpoint == SofascoreEndpointType.EVENT_INCIDENTS
                    ? Math.toIntExact(observationId - 100) : 1));
        });
        AtomicInteger external = new AtomicInteger(), posts = new AtomicInteger(), stateReads = new AtomicInteger();
        AtomicReference<Throwable> bridgeFailure = new AtomicReference<>();
        List<String> scriptErrors = new ArrayList<>(), policyErrors = new ArrayList<>();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            for (int width : List.of(1440, 390)) {
                try (BrowserContext context = browser.newContext(options(false, width))) {
                    bridge(context, external, posts, stateReads, bridgeFailure);
                    Page page = checkedPage(context, scriptErrors, policyErrors);
                    assertThat(page.navigate(ORIGIN + PAGE_PATH).status()).isEqualTo(200);
                    assertThat(bridgeFailure.get()).isNull();
                    Locator host = incidents(page);
                    assertInitialIncidents(page, host);
                    assertThat(technical(page).evaluate("element => element.open")).isEqualTo(false);
                    assertNoOverflow(host, width);
                    capture(host, "incidents-ssr-" + width + ".png");
                    assertThat(stateReads.get()).as("SSR has no script-driven refresh").isZero();
                }
            }
            try (BrowserContext context = browser.newContext(options(true, 1440))) {
                bridge(context, external, posts, stateReads, bridgeFailure);
                Page page = checkedPage(context, scriptErrors, policyErrors);
                assertThat(page.navigate(ORIGIN + PAGE_PATH).status()).isEqualTo(200);
                page.waitForCondition(() -> stateReads.get() > 0);
                Locator host = incidents(page), technical = technical(page);
                assertInitialIncidents(page, host);
                assertThat(technical.evaluate("element => element.open")).isEqualTo(false);
                Locator statistics = page.locator("[data-live-family='EVENT_STATISTICS'] [data-statistics]");
                Locator otherFamilySummary = statistics.locator("[data-stat-group] > summary").first();
                assertThat(otherFamilySummary.count()).isOne();
                // Keep both the technical disclosure and another family's focus through real polling.
                technical.locator(":scope > summary").click();
                otherFamilySummary.focus();
                host.evaluate("root => window.__incidentsHost = root");
                technical.evaluate("root => window.__incidentsTechnical = root");
                otherFamilySummary.evaluate("root => window.__otherFamilyFocus = root");
                String firstReceipt = incidentFamily(page).locator("[data-live-received]").textContent();
                revision.set(2);
                page.waitForCondition(() -> host.locator("[data-incident-key]").count() == 11);
                assertThat(host.locator("[data-incident-label]").first().textContent()).isEqualTo("But sur penalty");
                assertThat(host.locator("[data-incident-score]").first().textContent().replaceAll("\\s", "")).isEqualTo("2–0");
                assertThat(host.locator("[data-incident-minute]").first().textContent()).contains("90+3");
                assertThat(page.locator("[data-live-score]").textContent())
                        .as("a J5 incident score cannot replace the still unchanged J4 score").isEqualTo("1 – 0");
                assertDuplicateGoals(host);
                assertThat(incidentFamily(page).locator("[data-live-received]").textContent()).isNotEqualTo(firstReceipt);
                assertStableDisclosuresAndFocus(page, host, technical, otherFamilySummary, true);
                assertInertMarkup(page, host);
                assertNoOverflow(host, 1440);
                capture(host, "incidents-live-updated-1440.png");

                // A new reception with unchanged normalized incidents must retain the closed panel too.
                technical.locator(":scope > summary").click();
                otherFamilySummary.focus();
                String changedReceipt = incidentFamily(page).locator("[data-live-received]").textContent();
                revision.set(3);
                page.waitForCondition(() -> !changedReceipt.equals(incidentFamily(page).locator("[data-live-received]").textContent()));
                assertThat(host.locator("[data-incident-key]").count()).isEqualTo(11);
                assertDuplicateGoals(host);
                assertStableDisclosuresAndFocus(page, host, technical, otherFamilySummary, false);
                assertThat(page.locator("[data-live-score]").textContent()).isEqualTo("2 – 0");
                page.setViewportSize(390, 844);
                assertNoOverflow(host, 390);
                assertInertMarkup(page, host);
                capture(host, "incidents-live-updated-390.png");
            }
        }
        assertThat(bridgeFailure.get()).as("all page, asset and state requests rendered by MockMvc").isNull();
        assertThat(scriptErrors).isEmpty();
        assertThat(policyErrors).isEmpty();
        assertThat(external.get()).as("zero provider/external requests").isZero();
        assertThat(posts.get()).as("no mutation endpoint was invoked").isZero();
        assertThat(stateReads.get()).isGreaterThanOrEqualTo(3);
        assertNoCampaignMutation();
        System.out.println("WO058_INCIDENT_GRAPHICS=PASS;SSR_NO_JS=PASS;DUPLICATE_OCCURRENCES=PASS;"
                + "POLLING_NEW_AND_UNCHANGED=PASS;J4_SCORE_SEPARATE=PASS;TECHNICAL_PANEL_AND_OTHER_FAMILY_FOCUS=PASS;"
                + "TEXT_ESCAPING=PASS;VIEWPORTS_1440_390=PASS;REAL_PROVIDER_CALLS=0;HTTP_POSTS=0;DATABASE_USED=false");
    }

    private void assertNoCampaignMutation() {
        // ApplicationReadyEvent may invoke the mocked startup observation. It is not a web mutation.
        verify(campaigns, never()).launch(any(), any());
        verify(campaigns, never()).prepare(any());
        verify(campaigns, never()).prepareSelection(any());
        verify(campaigns, never()).stop(any(), any());
        verify(campaigns, never()).cancelPreparation(any(), any());
        verify(campaigns, never()).finalizeInterruptedCleanup(any(), anyLong());
    }

    private static void assertAvailability(Page page, String mode) {
        Locator family = incidentFamily(page);
        if (mode.equals("UNAVAILABLE")) {
            assertThat(family.locator("[data-live-family-outcome]").textContent()).isEqualTo("UNAVAILABLE");
            assertThat(incidents(page).count()).isZero();
            assertThat(technical(page).count()).isZero();
            assertThat(family.textContent()).doesNotContain("La liste source est explicitement vide.", "Collection normalisée vide.");
            return;
        }
        assertThat(incidents(page).count()).isOne();
        assertThat(technical(page).count()).isOne();
        assertNoOverflow(incidents(page), 390);
        if (mode.equals("EMPTY")) {
            assertThat(family.locator("[data-live-family-outcome]").textContent()).isEqualTo("PARSED");
            assertThat(incidents(page).locator("[data-incident-key]").count()).isZero();
            assertThat(incidents(page).locator("[data-incidents-empty]").isVisible()).isTrue();
            assertThat(technical(page).locator("[data-live-table-body] tr").count()).isZero();
        } else {
            assertThat(incidents(page).locator("[data-incident-key]").count()).isEqualTo(11);
            assertDuplicateGoals(incidents(page));
            assertThat(technical(page).locator("[data-live-table-body] tr").count()).isEqualTo(11);
            if (mode.equals("PREVIOUS_404")) {
                assertThat(family.locator("[data-live-family-outcome]").textContent()).isEqualTo("UNAVAILABLE");
                assertThat(family.locator("[data-live-family-previous]").textContent()).contains("Dernière donnée lisible conservée");
            }
        }
    }

    private static void assertInitialIncidents(Page page, Locator host) {
        assertThat(host.count()).isOne();
        assertThat(host.isVisible()).isTrue();
        assertThat(host.locator("[data-incident-key]").count()).isEqualTo(10);
        assertThat(host.locator("[data-incident-label]").allTextContents()).containsExactly(
                "But", "But", "Carton jaune", "Carton rouge", "Second carton jaune · Expulsion",
                "Remplacement", "Penalty arrêté", "Penalty manqué", "Repère de période",
                "Incident non reconnu · providerSpecialIncident");
        assertThat(host.locator("[data-incident-tone='yellow'] [data-incident-icon]").textContent()).contains("🟨");
        assertThat(host.locator("[data-incident-tone='red'] [data-incident-icon]").allTextContents())
                .anySatisfy(value -> assertThat(value).contains("🟥"));
        assertThat(host.locator("[data-incident-tone='penalty'] [data-incident-icon]").allTextContents())
                .containsExactly("🧤", "❌");
        assertThat(host.locator("[data-incident-tone='substitution'] [data-incident-player-in]").textContent())
                .contains("Entrant synthétique");
        assertThat(host.locator("[data-incident-tone='substitution'] [data-incident-player-out]").textContent())
                .contains("Sortant synthétique");
        assertThat(host.textContent()).contains(HOME_NAME, AWAY_NAME, "Main volontaire", "Mi-temps (HT)",
                "Équipe non renseignée", LONG_TEXT);
        assertThat(page.locator("[data-live-score]").textContent()).isEqualTo("1 – 0");
        assertDuplicateGoals(host);
        assertInertMarkup(page, host);
    }

    private static void assertDuplicateGoals(Locator host) {
        Locator duplicatePlayers = host.locator("[data-incident-player]").filter(
                new Locator.FilterOptions().setHasText("Alex But synthétique"));
        assertThat(duplicatePlayers.count()).as("two identical source occurrences are not deduplicated").isEqualTo(2);
        assertThat(host.locator("[data-incident-key]").evaluateAll(
                "nodes => new Set(nodes.map(n => n.dataset.incidentKey)).size === nodes.length")).isEqualTo(true);
    }

    private static void assertInertMarkup(Page page, Locator host) {
        assertThat(host.textContent()).contains(UNSAFE_TEXT);
        assertThat(host.locator("img,script,iframe,[onerror],[onclick]").count()).isZero();
        assertThat(page.evaluate("window.__incidentsInjected === undefined")).isEqualTo(true);
    }

    private static void assertStableDisclosuresAndFocus(Page page, Locator host, Locator technical,
                                                        Locator otherFamilySummary, boolean open) {
        assertThat(host.evaluate("root => root === window.__incidentsHost")).isEqualTo(true);
        assertThat(technical.evaluate("root => root === window.__incidentsTechnical && root.open === " + open)).isEqualTo(true);
        assertThat(otherFamilySummary.evaluate("root => root === window.__otherFamilyFocus && document.activeElement === root"))
                .isEqualTo(true);
        assertThat(page.locator("[data-live-family='EVENT_STATISTICS'] [data-statistics]").count()).isOne();
    }

    private static void assertNoOverflow(Locator host, int width) {
        assertThat(((Number) host.evaluate("element => element.getBoundingClientRect().width")).doubleValue())
                .isLessThanOrEqualTo(width);
        assertThat(host.evaluate("element => element.scrollWidth <= element.clientWidth + 1")).isEqualTo(true);
        assertThat(host.locator("[data-incident-key]").evaluateAll(
                "nodes => nodes.every(n => n.scrollWidth <= n.clientWidth + 1)")).isEqualTo(true);
    }

    private static Browser.NewContextOptions options(boolean javascript, int width) {
        return new Browser.NewContextOptions().setOffline(true).setAcceptDownloads(false)
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK).setJavaScriptEnabled(javascript).setViewportSize(width, 1000);
    }

    private static void requireBrowserOptIn() throws Exception {
        String configured = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(configured).as("explicit native browser opt-in").isNotBlank();
        assertThat(Path.of(configured).toRealPath()).isEqualTo(
                Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
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

    private void bridge(BrowserContext context, AtomicInteger external, AtomicInteger posts,
                        AtomicInteger stateReads, AtomicReference<Throwable> failure) {
        MockHttpSession session = new MockHttpSession();
        context.route("**/*", route -> {
            Request browserRequest = route.request();
            if (!browserRequest.url().startsWith(ORIGIN + "/")) { external.incrementAndGet(); route.abort(); return; }
            if (!browserRequest.method().equals("GET")) { posts.incrementAndGet(); route.abort(); return; }
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
            } catch (Throwable problem) { failure.compareAndSet(null, problem); route.abort(); }
        });
    }

    private static Locator incidentFamily(Page page) { return page.locator("[data-live-family='EVENT_INCIDENTS']"); }
    private static Locator incidents(Page page) { return incidentFamily(page).locator("[data-incidents]"); }
    private static Locator technical(Page page) { return incidentFamily(page).locator("[data-incidents-technical]"); }
    private static void capture(Locator host, String filename) throws Exception {
        Path directory = Path.of(".tmp", "incident-graphics-qualification").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        host.screenshot(new Locator.ScreenshotOptions().setPath(directory.resolve(filename)));
    }

    private static Manifest manifest() {
        return new Manifest(CAMPAIGN_ID, HASH, "live-v3", NOW, NOW.plusSeconds(300), Duration.ofHours(4),
                1000, 3000, 1_000_000, 1, List.of(new Target(EVENT_ID, PROVIDER_EVENT_ID, 1, 1)));
    }

    private static CampaignView campaign(int revision) {
        Instant received = NOW.plusSeconds(revision);
        int content = Math.min(revision, 2);
        int canonicalScore = revision >= 3 ? 2 : 1;
        List<FamilyCursor> families = new ArrayList<>();
        for (var endpoint : List.of(SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS)) {
            UUID attempt = new UUID(endpoint.ordinal(), revision);
            Long observationId = endpoint == SofascoreEndpointType.EVENT_DETAILS ? null
                    : endpoint == SofascoreEndpointType.EVENT_INCIDENTS ? 100L + content : 201L;
            int familyContent = endpoint == SofascoreEndpointType.EVENT_STATISTICS ? 1
                    : endpoint == SofascoreEndpointType.EVENT_DETAILS ? canonicalScore : content;
            var refs = new NormalizedReferences(endpoint == SofascoreEndpointType.EVENT_DETAILS ? 1L : null,
                    null, observationId, String.valueOf(familyContent).repeat(64));
            String projection = endpoint == SofascoreEndpointType.EVENT_DETAILS
                    ? "{\"homeScore\":{\"presence\":\"VALUE\",\"value\":{\"display\":{\"presence\":\"VALUE\",\"value\":" + canonicalScore
                        + "}}},\"awayScore\":{\"presence\":\"VALUE\",\"value\":{\"display\":{\"presence\":\"VALUE\",\"value\":0}}}}"
                    : null;
            String parser = endpoint == SofascoreEndpointType.EVENT_DETAILS ? "event-details-v3"
                    : endpoint == SofascoreEndpointType.EVENT_INCIDENTS ? "event-incidents-v17" : "event-statistics-v2";
            var result = new Result(attempt, new Publication("PARSED", "EVENT", "NONE", received,
                    parser, true, "COLLECTING", endpoint == SofascoreEndpointType.EVENT_DETAILS ? "inprogress" : null,
                    projection, projection == null ? null : parser, "COMPLETE", 100), refs);
            families.add(new FamilyCursor(endpoint, attempt, attempt, attempt, attempt,
                    received, received, NOW.plusSeconds(familyContent), refs, result, result));
        }
        return new CampaignView(manifest(), "RUNNING", null, NOW, NOW.plusSeconds(14400), revision * 3, 1000,
                revision, null, List.of(new EventView(manifest().targets().getFirst(), "COLLECTING", null,
                        revision * 3, 1000, received.plusSeconds(60), families)), List.of(), List.of());
    }

    private static CampaignView availabilityCampaign(int revision, String mode) {
        CampaignView base = campaign(revision);
        boolean unavailable = mode.equals("UNAVAILABLE") || mode.equals("PREVIOUS_404");
        boolean previous = mode.equals("PREVIOUS_404");
        long observationId = mode.equals("EMPTY") ? 104 : mode.equals("UNAVAILABLE") ? 105 : 102;
        UUID attempt = new UUID(900, revision), readableAttempt = previous ? new UUID(900, 3) : attempt;
        Instant received = NOW.plusSeconds(revision), readableAt = previous ? NOW.plusSeconds(3) : received;
        var refs = new NormalizedReferences(null, null, observationId, HASH);
        var latest = new Result(attempt, new Publication(unavailable ? "UNAVAILABLE" : "PARSED", "EVENT",
                unavailable ? "HTTP_404" : "NONE", received, "event-incidents-v17", !unavailable,
                null, null, null, null, unavailable ? "UNAVAILABLE" : mode.equals("EMPTY") ? "EMPTY_VALID" : "COMPLETE",
                unavailable ? 0 : 100), previous ? NormalizedReferences.none() : refs);
        var readable = previous ? new Result(readableAttempt, new Publication("PARSED", "EVENT", "NONE", readableAt,
                "event-incidents-v17", true, null, null, null, null, "COMPLETE", 100), refs) : latest;
        var family = new FamilyCursor(SofascoreEndpointType.EVENT_INCIDENTS, attempt, attempt, readableAttempt,
                readableAttempt, received, readableAt, readableAt, refs, latest, readable);
        EventView target = base.events().getFirst();
        var families = target.families().stream().map(current -> current.endpoint() == SofascoreEndpointType.EVENT_INCIDENTS
                ? family : current).toList();
        return new CampaignView(base.manifest(), base.state(), base.reason(), base.startedAt(), base.endsAt(),
                base.reservedCalls(), base.receivedBytes(), base.revision(), base.ownership(),
                List.of(new EventView(target.target(), target.state(), target.reason(), target.reservedCalls(),
                        target.receivedBytes(), target.nextDueAt(), families)), base.attempts(), base.transitions());
    }

    private static CanonicalEventObservationView identity() {
        return new CanonicalEventObservationView(1, CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID), NOW,
                new ScheduledTeam(11, HOME_NAME), new ScheduledTeam(22, AWAY_NAME),
                new ScheduledEventStatus("inprogress", Optional.of("2nd half")), Optional.empty(),
                EventSourceTrace.providerSnapshot(1, HASH, "event-details-v3", NOW), HASH, 1);
    }

    private static J5EventDataObservationView observation(SofascoreEndpointType endpoint, int revision) {
        boolean isIncidents = endpoint == SofascoreEndpointType.EVENT_INCIDENTS;
        J5EventData value = isIncidents ? incidentData(revision) : new EventStatistics(PROVIDER_EVENT_ID,
                List.of(new EventStatisticMetric("ALL", "Match overview", "ballPossession", "Ball possession",
                        Optional.of("55%"), Optional.of("45%"))));
        String parser = isIncidents ? "event-incidents-v17" : "event-statistics-v2";
        return new J5EventDataObservationView(isIncidents ? 100L + revision : 201L,
                CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID), value,
                EventSourceTrace.providerSnapshot(isIncidents ? 100 + revision : 201, HASH, parser, NOW.plusSeconds(revision)),
                J5CompletenessReport.measured(10, 10, List.of()), String.valueOf(revision).repeat(64));
    }

    private static EventIncidents incidentData(int revision) {
        List<EventIncident> values = new ArrayList<>();
        if (revision > 1) add(values, "goal", 90, 3, true, "Nouveau buteur synthétique", "penalty", null, null, null, 2, null, null);
        add(values, "goal", 60, null, true, "Alex But synthétique", "regular", null, null, null, 1, null, null);
        add(values, "goal", 60, null, true, "Alex But synthétique", "regular", null, null, null, 1, null, null);
        add(values, "card", 55, null, true, "Jo Jaune", "yellow", null, null, null, null, null, null);
        add(values, "card", 54, null, false, "Sam Rouge", "red", "Professional handball", null, null, null, null, null);
        add(values, "card", 52, null, true, "Lee Expulsion", "yellowRed", null, null, null, null, null, null);
        add(values, "substitution", 50, null, true, null, null, null, null, null, null, "Entrant synthétique", "Sortant synthétique");
        add(values, "inGamePenalty", 47, null, false, "Tireur synthétique", "missed", "goalkeeperSave", null, null, null, null, null);
        add(values, "inGamePenalty", 46, null, true, "Autre tireur synthétique", "missed", "offTarget", null, null, null, null, null);
        add(values, "period", 45, null, null, null, null, null, "HT", null, null, null, null);
        add(values, "providerSpecialIncident", 44, null, null, UNSAFE_TEXT, null, null, null, LONG_TEXT, null, null, null);
        return new EventIncidents(PROVIDER_EVENT_ID, values);
    }

    private static void add(List<EventIncident> values, String type, int minute, Integer added, Boolean home,
                            String player, String kind, String reason, String period, String description,
                            Integer homeScore, String playerIn, String playerOut) {
        values.add(new EventIncident(values.size(), type, minute, Optional.ofNullable(added), Optional.ofNullable(home),
                Optional.empty(), Optional.empty(), Optional.ofNullable(player), Optional.empty(), Optional.ofNullable(playerIn),
                Optional.empty(), Optional.ofNullable(playerOut), Optional.ofNullable(homeScore),
                homeScore == null ? Optional.empty() : Optional.of(0), Optional.ofNullable(kind), Optional.ofNullable(reason),
                Optional.ofNullable(period), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.ofNullable(description), Optional.empty()));
    }
}
