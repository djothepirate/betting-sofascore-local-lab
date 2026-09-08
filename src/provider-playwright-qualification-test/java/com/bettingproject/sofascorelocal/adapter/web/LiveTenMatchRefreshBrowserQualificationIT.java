package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Short, explicit Chromium qualification of ten-match rendering from local JSON publication.
 * Uses production JavaScript, CSS and statistics presentation. HTTP data is synthetic; this
 * complements the native JDBC qualification and does not measure provider freshness or SQL.
 */
class LiveTenMatchRefreshBrowserQualificationIT {
    private static final String CAMPAIGN = "00000000-0000-0000-0000-000000000058";
    private static final String PAGE_PATH = "/live-campaigns/" + CAMPAIGN;
    private static final String STATE_PATH = PAGE_PATH + "/state";
    private static final List<String> ENDPOINTS = List.of("EVENT_DETAILS", "EVENT_INCIDENTS", "EVENT_STATISTICS");
    private static final List<String> ASSETS = List.of("/js/live-campaign.js", "/js/statistics.js",
            "/css/app.css", "/css/statistics.css");

    @Test
    @Timeout(45)
    void tenMatchesRenderChangedAndRepeatedReceiptsWithinTenSecondsOfLocalPublication() throws Exception {
        String configured = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(configured).as("explicit native browser opt-in").isNotBlank();
        assertThat(Path.of(configured).toRealPath())
                .isEqualTo(Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
        Instant baseline = Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MILLIS);
        AtomicReference<Publication> current = new AtomicReference<>(publication(1, 1, baseline, baseline));
        AtomicInteger stateGets = new AtomicInteger(), posts = new AtomicInteger(), external = new AtomicInteger();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        var scriptErrors = new ArrayList<String>();
        var policyErrors = new ArrayList<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", exchange -> {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    posts.incrementAndGet(); send(exchange, 405, "text/plain", ""); return;
                }
                String path = exchange.getRequestURI().getPath();
                if (path.equals(STATE_PATH)) {
                    stateGets.incrementAndGet();
                    send(exchange, 200, "application/json", current.get().body());
                } else if (path.equals(PAGE_PATH)) {
                    send(exchange, 200, "text/html", pageHtml());
                } else if (ASSETS.contains(path)) {
                    try (var input = getClass().getResourceAsStream("/static" + path)) {
                        if (input == null) throw new IOException("Missing production browser asset");
                        send(exchange, 200, path.endsWith(".js") ? "text/javascript" : "text/css",
                                new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    }
                } else send(exchange, 404, "text/plain", "");
            } catch (Throwable failure) {
                serverFailure.compareAndSet(null, failure);
                exchange.close();
            }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        long changedDelayMillis;
        long repeatedDelayMillis;
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK).setViewportSize(1440, 1000))) {
            context.route("**/*", route -> {
                if (route.request().url().startsWith(origin + "/")) route.resume();
                else { external.incrementAndGet(); route.abort(); }
            });
            Page page = context.newPage();
            page.onPageError(scriptErrors::add);
            page.onConsoleMessage(message -> {
                if (message.type().equals("error") && message.text().contains("Content Security Policy"))
                    policyErrors.add(message.text());
            });
            // Browser startup is outside the measured local-publication/rendering window.
            current.set(publication(1, 1, baseline, baseline));
            assertThat(page.navigate(origin + PAGE_PATH).status()).isEqualTo(200);
            awaitRendered(page, current.get(), 1);
            assertThat(page.locator("[data-live-event-id]").count()).isEqualTo(10);
            assertThat(page.locator("[data-live-family]").count()).isEqualTo(30);
            assertThat(page.locator(".statistics-metric").count()).isEqualTo(1_350);
            assertThat(page.locator("[data-live-family='EVENT_INCIDENTS'] tbody tr").count()).isEqualTo(300);

            Instant changedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Publication changed = publication(2, 2, changedAt, changedAt);
            current.set(changed);
            changedDelayMillis = awaitRendered(page, changed, 2);
            assertFreshReceipts(page, changedAt, changedAt);
            page.evaluate("""
                    window.__qualificationContentNodes = Array.from(document.querySelectorAll(
                      "[data-live-family='EVENT_INCIDENTS'] tbody, .statistics-metric"));
                    window.__qualificationContentHashes = Array.from(document.querySelectorAll(
                      '[data-live-hash]'), node => node.textContent);
                    """);

            // No sporting or normalized content changes: only this new reception and
            // its occurrence advance. A table-cache hit must not freeze receipt freshness.
            Instant repeatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            assertThat(repeatedAt).isAfter(changedAt);
            Publication repeated = publication(3, 2, repeatedAt, changedAt);
            current.set(repeated);
            repeatedDelayMillis = awaitRendered(page, repeated, 2);
            assertFreshReceipts(page, repeatedAt, changedAt);
            assertThat(page.evaluate("""
                    (() => {
                      const nodes = Array.from(document.querySelectorAll(
                        "[data-live-family='EVENT_INCIDENTS'] tbody, .statistics-metric"));
                      const hashes = Array.from(document.querySelectorAll('[data-live-hash]'), node => node.textContent);
                      return nodes.length === window.__qualificationContentNodes.length
                        && nodes.every((node, index) => node === window.__qualificationContentNodes[index])
                        && hashes.every((hash, index) => hash === window.__qualificationContentHashes[index]);
                    })()
                    """)).as("unchanged content retains its nodes and hash while receipts advance").isEqualTo(true);
            assertThat(page.locator("[data-live-occurrence]").allTextContents())
                    .allMatch(value -> Long.parseLong(value) >= 30_000);
            assertThat(stateGets.get()).isGreaterThanOrEqualTo(3);
            assertThat(scriptErrors).isEmpty();
            assertThat(policyErrors).isEmpty();
            assertThat(posts.get()).isZero();
            assertThat(external.get()).isZero();
            assertThat(serverFailure.get()).isNull();
        } finally {
            server.stop(0);
        }
        System.out.printf("WO058_UI_TEN_MATCHES matches=10 families=30 statistics=1350 incidents=300 "
                        + "changedPublicationToRenderedMillis=%d repeatedPublicationToRenderedMillis=%d "
                        + "realProviderCalls=0 operatorDatabaseUsed=false%n", changedDelayMillis, repeatedDelayMillis);
    }

    private static long awaitRendered(Page page, Publication publication, int contentRevision) {
        page.waitForFunction("""
                expected => {
                  const nodes = Array.from(document.querySelectorAll('[data-live-event-id]'));
                  return nodes.length === 10 && nodes.every((node, index) => {
                    const score = `${index % 3 + expected.contentRevision} – ${index % 2}`;
                    const families = Array.from(node.querySelectorAll('[data-live-family]'));
                    const incidents = node.querySelector("[data-live-family='EVENT_INCIDENTS'] tbody");
                    const possession = node.querySelector("[data-stat-period='ALL'] .statistics-metric .statistics-home strong");
                    return node.querySelector('[data-live-score]')?.textContent === score
                      && node.querySelector('[data-live-sport-status]')?.textContent === '2nd half'
                      && families.length === 3 && families.every(family => family.checkVisibility()
                        && family.querySelector('[data-live-received]')?.textContent === expected.receivedAt
                        && family.querySelector('[data-live-family-outcome]')?.textContent === 'PARSED')
                      && incidents?.rows.length === 30
                      && incidents.rows[29].cells[5].textContent === `But synthétique ${index + 1} v${expected.contentRevision}`
                      && incidents.rows[29].checkVisibility()
                      && possession?.textContent === `${50 + index + expected.contentRevision}%`
                      && possession.checkVisibility();
                  });
                }
                """, Map.of("receivedAt", publication.receivedAt().toString(), "contentRevision", contentRevision),
                new Page.WaitForFunctionOptions().setTimeout(10_000));
        long elapsed = Duration.ofNanos(System.nanoTime() - publication.availableNanos()).toMillis();
        assertThat(elapsed).as("local JSON publication to all ten rendered matches").isLessThanOrEqualTo(10_000);
        return elapsed;
    }

    private static void assertFreshReceipts(Page page, Instant received, Instant changed) {
        assertThat(page.locator("[data-live-received]").allTextContents()).hasSize(30)
                .allMatch(received.toString()::equals);
        assertThat(page.locator("[data-live-changed]").allTextContents()).hasSize(30)
                .allMatch(changed.toString()::equals);
        assertThat(page.locator("[data-live-age]").allTextContents()).hasSize(30)
                .allMatch(value -> value.matches("\\d+ s") && Integer.parseInt(value.substring(0, value.length() - 2)) <= 10);
        assertThat(page.locator("[data-live-age]").evaluateAll("nodes => nodes.every(node => node.dataset.liveAgeFrozen === 'false')"))
                .isEqualTo(true);
    }

    private static Publication publication(int revision, int contentRevision, Instant received, Instant changed) {
        var eventViews = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < 10; index++) {
            var event = new LinkedHashMap<String, Object>();
            event.put("canonicalEventId", eventId(index));
            event.put("sportStatus", "inprogress");
            event.put("sportStatusLabel", "2nd half");
            event.put("state", "COLLECTING");
            event.put("score", (index % 3 + contentRevision) + " – " + (index % 2));
            event.put("sourceReceivedAt", received.toString());
            event.put("sourceSnapshotId", 1000L + index);
            var families = new ArrayList<Map<String, Object>>();
            for (String endpoint : ENDPOINTS) families.add(family(index, endpoint, revision, contentRevision, received, changed));
            event.put("families", families);
            eventViews.add(event);
        }
        var campaign = Map.of("campaignId", CAMPAIGN, "revision", revision, "state", "RUNNING",
                "preparedAt", received.minusSeconds(300).toString(), "startedAt", received.minusSeconds(240).toString(),
                "events", eventViews);
        String body = JsonMapper.builder().build().writeValueAsString(campaign);
        return new Publication(body, received, System.nanoTime());
    }

    private static Map<String, Object> family(int index, String endpoint, int revision, int contentRevision,
                                             Instant received, Instant changed) {
        var result = new LinkedHashMap<String, Object>();
        result.put("endpoint", endpoint);
        result.put("label", switch (endpoint) {
            case "EVENT_DETAILS" -> "Statut J4";
            case "EVENT_INCIDENTS" -> "Incidents";
            default -> "Statistiques";
        });
        result.put("outcome", "PARSED");
        result.put("scope", "EVENT");
        result.put("lastAttemptAt", received.minusMillis(300).toString());
        result.put("authorizationDelayMillis", 0);
        result.put("lastReceivedAt", received.toString());
        result.put("lastSuccessfulAt", received.toString());
        result.put("lastChangedAt", changed.toString());
        result.put("receivedSnapshotId", contentRevision * 1000L + index);
        result.put("dataSnapshotId", contentRevision * 1000L + index);
        result.put("receivedOccurrenceId", revision * 10_000L + index);
        result.put("parserVersion", "synthetic-qualification-v1");
        result.put("payloadSha256", Integer.toHexString(contentRevision).repeat(64));
        result.put("normalizedSha256", Integer.toHexString(contentRevision).repeat(64));
        result.put("completeness", "COMPLETE");
        result.put("completenessScore", 100);
        result.put("previousData", false);
        result.put("freshness", Map.of("state", "FRESH", "label", "Dernière réception disponible", "frozen", false));
        result.put("schedule", Map.of("intervalSeconds", 60, "nextDueAt", received.plusSeconds(60).toString(),
                "missedCycles", 0, "latenessMillis", 0));
        if (endpoint.equals("EVENT_STATISTICS")) {
            result.put("statistics", StatisticsPresentation.from(statistics(index, contentRevision)));
        } else if (endpoint.equals("EVENT_INCIDENTS")) {
            var rows = new ArrayList<List<String>>();
            for (int incident = 0; incident < 30; incident++) rows.add(List.of(Integer.toString(incident + 35),
                    incident == 29 ? "goal" : incident % 2 == 0 ? "card" : "substitution",
                    incident % 2 == 0 ? "HOME" : "AWAY", "Joueur synthétique " + (index + 1) + " / " + incident,
                    "1–1", incident == 29 ? "But synthétique " + (index + 1) + " v" + contentRevision : "Observation synthétique"));
            result.put("table", new LiveCampaignPresentation.Table(List.of("Minute", "Type", "Équipe", "Joueur", "Score", "Détail"), rows));
        }
        return result;
    }

    private static EventStatistics statistics(int index, int contentRevision) {
        var metrics = new ArrayList<EventStatisticMetric>();
        for (String period : List.of("ALL", "1ST", "2ND")) {
            for (int metric = 0; metric < 45; metric++) {
                String code = metric == 0 ? "ballPossession" : "syntheticMetric" + metric;
                String label = metric == 0 ? "Possession" : metric % 3 == 0 ? "Passes réussies " + metric : "Tirs " + metric;
                String home = metric == 0 ? (50 + index + contentRevision) + "%" : metric % 3 == 0 ? "25/40 (63%)"
                        : Integer.toString(metric + index + contentRevision);
                String away = metric == 0 ? (50 - index - contentRevision) + "%" : metric % 3 == 0 ? "18/30 (60%)"
                        : Integer.toString(metric + 1);
                metrics.add(new EventStatisticMetric(period, "Groupe synthétique " + metric / 15, code, label,
                        Optional.of(home), Optional.of(away)));
            }
        }
        return new EventStatistics(17_000_001L + index, metrics);
    }

    private static String pageHtml() {
        var html = new StringBuilder("""
                <!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8">
                <link rel="stylesheet" href="/css/app.css"><link rel="stylesheet" href="/css/statistics.css">
                <script defer src="/js/statistics.js"></script><script defer src="/js/live-campaign.js"></script>
                </head><body><main data-live-monitor data-live-state-url="%s"><p data-live-refresh-status></p>
                """.formatted(STATE_PATH));
        for (int index = 0; index < 10; index++) html.append("""
                <section class="card" data-live-event-id="%s"><h2>Rencontre synthétique %d</h2>
                <span data-live-sport-status></span><strong data-live-score></strong><span data-live-event-state></span>
                <div data-live-families></div></section>
                """.formatted(eventId(index), index + 1));
        return html.append("</main></body></html>").toString();
    }

    private static String eventId(int index) { return new UUID(0, index + 1).toString(); }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; form-action 'self'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private record Publication(String body, Instant receivedAt, long availableNanos) { }
}
