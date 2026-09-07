package com.bettingproject.sofascorelocal.adapter.web;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit native-browser qualification. The server is loopback only and contains no provider. */
class LiveCampaignBrowserQualificationIT {
    private static final String EVENT = "00000000-0000-0000-0000-000000000001";
    private static final String CAMPAIGN = "00000000-0000-0000-0000-000000000058";

    @Test
    @Timeout(90)
    void nativeChromiumPreservesSelectionFocusAndFreshnessAcrossLocalRefreshes() throws Exception {
        String configured = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(configured).as("explicit browser cache opt-in").isNotBlank();
        assertThat(Path.of(configured).toRealPath()).isEqualTo(
                Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
        AtomicInteger revision = new AtomicInteger(10);
        AtomicInteger gets = new AtomicInteger();
        AtomicInteger posts = new AtomicInteger();
        AtomicInteger externalRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                posts.incrementAndGet(); send(exchange, 405, "text/plain", ""); return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/events/state")) {
                gets.incrementAndGet();
                send(exchange, 200, "application/json", state(revision.get()));
            } else if (path.equals("/js/live-campaign.js")) {
                try (var script = getClass().getResourceAsStream("/static/js/live-campaign.js")) {
                    if (script == null) throw new IOException("live script missing");
                    send(exchange, 200, "text/javascript", new String(script.readAllBytes(), StandardCharsets.UTF_8));
                }
            } else if (path.equals("/events")) {
                send(exchange, 200, "text/html", page());
            } else send(exchange, 404, "text/plain", "");
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
            context.route("**/*", route -> {
                if (route.request().url().startsWith(origin + "/")) route.resume();
                else { externalRequests.incrementAndGet(); route.abort(); }
            });
            Page page = context.newPage();
            page.navigate(origin + "/events");
            page.waitForCondition(() -> "10 – 0".equals(page.locator("[data-live-score]").textContent()));
            page.locator("input[name=eventId]").check();
            page.locator("input[name=eventId]").focus();
            assertThat(page.locator("[data-live-selection-count]").textContent()).contains("1 rencontre sélectionnée");
            assertThat(page.locator("[data-live-table-body]").textContent()).contains("<img src=x onerror=alert(1)>");
            assertThat(page.locator("[data-live-table-body] img").count()).isZero();
            assertThat(page.locator("[data-live-freshness]").textContent()).contains("périmée");
            assertThat(page.locator("[data-live-freshness]").getAttribute("class")).contains("notice-warning");

            revision.set(11);
            page.waitForCondition(() -> "11 – 0".equals(page.locator("[data-live-score]").textContent()));
            assertThat(page.locator("input[name=eventId]").isChecked()).isTrue();
            assertThat(page.evaluate("document.activeElement === document.querySelector('input[name=eventId]')")).isEqualTo(true);
            assertThat(page.locator("[data-live-age]").textContent()).isEqualTo("90 s");

            int beforeOldResponse = gets.get();
            revision.set(9);
            page.waitForCondition(() -> gets.get() > beforeOldResponse);
            page.waitForTimeout(150);
            assertThat(page.locator("[data-live-score]").textContent()).isEqualTo("11 – 0");
            assertThat(page.locator("input[name=eventId]").isChecked()).isTrue();

            // Control the browser visibility signal without opening an interactive desktop window.
            page.evaluate("Object.defineProperty(document, 'hidden', {configurable: true, get: () => true});"
                    + "document.dispatchEvent(new Event('visibilitychange'))");
            int hiddenCount = gets.get();
            page.waitForTimeout(5500);
            assertThat(gets.get()).isEqualTo(hiddenCount);
            assertThat(page.locator("[data-live-refresh-status]").textContent()).contains("suspendue");
            revision.set(12);
            page.evaluate("Object.defineProperty(document, 'hidden', {configurable: true, get: () => false});"
                    + "document.dispatchEvent(new Event('visibilitychange'))");
            page.waitForCondition(() -> "12 – 0".equals(page.locator("[data-live-score]").textContent()));
            assertThat(page.locator("input[name=eventId]").isChecked()).isTrue();
            assertThat(page.evaluate("document.activeElement === document.querySelector('input[name=eventId]')")).isEqualTo(true);
            assertThat(page.locator("[data-live-link]").getAttribute("href")).isEqualTo("/live-campaigns/" + CAMPAIGN);
            assertThat(page.locator("[data-live-age]").textContent()).isEqualTo("90 s");
            // A newer canonical observation already visible in this document must also win.
            page.evaluate("document.querySelector('[data-live-event-id]').dataset.liveCanonicalReceivedAt = '2026-09-08T12:00:00Z'");
            int beforeCanonicalProtection = gets.get();
            revision.set(13);
            page.waitForCondition(() -> gets.get() > beforeCanonicalProtection);
            page.waitForTimeout(150);
            assertThat(page.locator("[data-live-score]").textContent()).isEqualTo("12 – 0");
            // A newly launched older preparation outranks a later preparation that never started.
            revision.set(20);
            page.reload();
            page.waitForCondition(() -> "20 – 0".equals(page.locator("[data-live-score]").textContent()));
            revision.set(21);
            page.waitForCondition(() -> "21 – 0".equals(page.locator("[data-live-score]").textContent()));
            assertThat(page.locator("[data-live-link]").getAttribute("href")).isEqualTo("/live-campaigns/" + CAMPAIGN);
            int beforeObsoletePreparation = gets.get();
            revision.set(22);
            page.waitForCondition(() -> gets.get() > beforeObsoletePreparation);
            page.waitForTimeout(150);
            assertThat(page.locator("[data-live-score]").textContent()).isEqualTo("21 – 0");
            page.locator("input[name=eventId]").check();
            revision.set(30);
            page.waitForCondition(() -> page.locator("input[name=eventId]").isDisabled());
            assertThat(page.locator("input[name=eventId]").isChecked()).isFalse();
            assertThat(page.locator("[data-live-selection-count]").textContent()).contains("0 rencontre sélectionnée");
            assertThat(page.locator("[data-live-prepare]").isDisabled()).isTrue();
            assertThat(page.locator("[data-live-selection-blocked]").isVisible()).isTrue();
            revision.set(31);
            page.waitForCondition(() -> !page.locator("input[name=eventId]").isDisabled());
            assertThat(page.locator("input[name=eventId]").isChecked()).isFalse();
            assertThat(page.locator("[data-live-selection-blocked]").isHidden()).isTrue();
            page.locator("input[name=eventId]").check();
            assertThat(page.locator("[data-live-prepare]").isDisabled()).isFalse();
            revision.set(32);
            page.waitForCondition(() -> page.locator("input[name=eventId]").isDisabled());
            int beforeObsoleteError = gets.get();
            revision.set(31);
            page.waitForCondition(() -> gets.get() > beforeObsoleteError);
            page.waitForTimeout(150);
            assertThat(page.locator("input[name=eventId]").isDisabled()).isTrue();
            assertThat(page.locator("input[name=eventId]").isChecked()).isFalse();
            assertThat(posts.get()).isZero();
            assertThat(externalRequests.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; frame-ancestors 'none'; form-action 'self'");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static String page() {
        return """
                <!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8">
                <script defer src="/js/live-campaign.js"></script></head><body>
                <section data-live-monitor data-live-state-url="/events/state">
                  <form id="live-selection" action="/live-campaigns/prepare" method="post" data-live-selection-form>
                    <button data-live-prepare>Préparer</button><span data-live-selection-count></span>
                  </form><p data-live-refresh-status></p>
                  <div data-live-event-id="%s" data-live-mirror-canonical
                    data-live-canonical-received-at="2026-09-07T12:00:00Z"><label><input form="live-selection" type="checkbox"
                    name="eventId" value="%s" data-live-provider-eligible="true">Sélectionner</label>
                    <span data-live-selection-blocked hidden>Déjà dans une campagne en cours</span>
                    <span data-live-sport-status></span><span data-live-event-state></span><span data-live-score></span>
                    <a data-live-link hidden>Campagne</a><div data-live-families></div>
                  </div>
                </section></body></html>
                """.formatted(EVENT, EVENT);
    }

    private static String state(int revision) {
        boolean laterPreparation = revision == 20 || revision == 22;
        return """
                [{"campaignId":"%s","revision":%d,"state":"%s","preparedAt":"%s","startedAt":%s,
                "events":[{"canonicalEventId":"%s","sportStatus":"inprogress","state":"%s","selectionBlocked":%s,
                "score":"%d – 0","canonicalCurrent":true,"sourceReceivedAt":"2026-09-07T12:00:00Z",
                "reservedCalls":3,"maximumCalls":1000,"families":[
                {"endpoint":"EVENT_STATISTICS","label":"Statistiques","outcome":"PARSED","code":null,
                "scope":"EVENT","lastReceivedAt":"2026-09-07T12:00:00Z","lastSuccessfulAt":"2026-09-07T12:00:00Z",
                "lastChangedAt":"2026-09-07T12:00:00Z","receivedSnapshotId":1,"dataSnapshotId":1,
                "parserVersion":"statistics-v2","normalizedSha256":"abc","completeness":"COMPLETE",
                "completenessScore":100,"previousData":false,"freshness":{"state":"%s","label":"%s",
                "frozen":%s,"ageAsOf":"2026-09-07T12:01:30Z"},"table":{"columns":["Domicile","Extérieur"],
                "rows":[["<img src=x onerror=alert(1)>","10"]]}}]}]}]
                """.formatted(laterPreparation ? "00000000-0000-0000-0000-000000000059" : CAMPAIGN,
                revision, laterPreparation ? "PREPARED" : revision == 31 ? "STOPPED_ERROR" : "RUNNING",
                laterPreparation ? "2026-09-07T12:00:10Z" : "2026-09-07T12:00:00Z",
                laterPreparation ? "null" : "\"2026-09-07T12:00:20Z\"", EVENT,
                revision == 31 ? "STOPPED_ERROR" : "COLLECTING", revision == 30 || revision == 32, revision,
                revision <= 10 ? "STALE" : "FROZEN", revision <= 10 ? "En retard / périmée" : "Âge figé",
                revision > 10);
    }
}
