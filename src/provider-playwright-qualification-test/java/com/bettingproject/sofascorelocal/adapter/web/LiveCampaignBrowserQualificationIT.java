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
    @Timeout(60)
    void blockedLocalReadTimesOutWithoutErasingDataAndPollingRecovers() throws Exception {
        assertThat(System.getProperty("provider.playwright.browser-cache", "")).isNotBlank();
        AtomicInteger gets = new AtomicInteger();
        AtomicInteger revision = new AtomicInteger(10);
        AtomicInteger external = new AtomicInteger();
        var release = new java.util.concurrent.CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equals(exchange.getRequestMethod())) { send(exchange, 405, "text/plain", ""); return; }
            if (path.equals("/events/state")) {
                int number = gets.incrementAndGet();
                if (number == 2) {
                    try { release.await(40, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                try { send(exchange, 200, "application/json", state(revision.get())); }
                catch (IOException cancelledClient) { exchange.close(); }
            } else if (path.equals("/js/live-campaign.js")) {
                try (var script = getClass().getResourceAsStream("/static/js/live-campaign.js")) {
                    send(exchange, 200, "text/javascript", new String(script.readAllBytes(), StandardCharsets.UTF_8));
                }
            } else if (path.equals("/events")) send(exchange, 200, "text/html", page());
            else send(exchange, 404, "text/plain", "");
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
            context.route("**/*", route -> {
                if (route.request().url().startsWith(origin + "/")) route.resume();
                else { external.incrementAndGet(); route.abort(); }
            });
            Page page = context.newPage();
            page.navigate(origin + "/events");
            page.waitForCondition(() -> "10 – 0".equals(page.locator("[data-live-score]").textContent()));
            page.locator("input[name=eventId]").check();
            page.waitForCondition(() -> gets.get() == 2);
            long blockedAt = System.nanoTime();
            page.waitForCondition(() -> page.locator("[data-live-refresh-status]").textContent().contains("dix secondes"),
                    new Page.WaitForConditionOptions().setTimeout(12500));
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - blockedAt).toMillis()).isBetween(8500L, 12000L);
            assertThat(page.locator("[data-live-score]").textContent()).isEqualTo("10 – 0");
            assertThat(page.locator("input[name=eventId]").isChecked()).isTrue();
            revision.set(11);
            long publishedAt = System.nanoTime();
            page.waitForCondition(() -> "11 – 0".equals(page.locator("[data-live-score]").textContent()),
                    new Page.WaitForConditionOptions().setTimeout(10000));
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - publishedAt).toMillis()).isLessThan(10000);
            assertThat(gets.get()).isGreaterThanOrEqualTo(3);
            assertThat(page.locator("input[name=eventId]").isChecked()).isTrue();
            assertThat(external.get()).isZero();
        } finally { release.countDown(); server.stop(0); }
    }

    @Test
    @Timeout(120)
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
            assertThat(page.locator("[data-live-campaign-score]").textContent()).isEqualTo("10 – 0");
            page.locator("input[name=eventId]").check();
            page.locator("input[name=eventId]").focus();
            assertThat(page.locator("[data-live-selection-count]").textContent()).contains("1 rencontre sélectionnée");
            assertThat(page.locator("[data-live-table-body]").textContent()).contains("<img src=x onerror=alert(1)>");
            assertThat(page.locator("[data-live-table-body] img").count()).isZero();
            assertThat(page.locator("[data-live-cache-revalidated]").textContent())
                    .isEqualTo("2026-09-07T12:01:00Z");
            assertThat(page.locator("[data-live-families] dt").allTextContents())
                    .contains("Dernière revalidation du cache (304)");
            assertThat(page.locator("[data-live-freshness]").textContent()).contains("périmée");
            assertThat(page.locator("[data-live-freshness]").getAttribute("class")).contains("notice-warning");

            revision.set(11);
            page.waitForCondition(() -> "11 – 0".equals(page.locator("[data-live-score]").textContent()));
            assertThat(page.locator("[data-live-campaign-score]").textContent()).isEqualTo("11 – 0");
            assertThat(page.locator("input[name=eventId]").isChecked()).isTrue();
            assertThat(page.evaluate("document.activeElement === document.querySelector('input[name=eventId]')")).isEqualTo(true);
            assertThat(page.locator("[data-live-age]").textContent()).isEqualTo("90 s");

            int beforeOldResponse = gets.get();
            revision.set(9);
            page.waitForCondition(() -> gets.get() > beforeOldResponse);
            page.waitForTimeout(150);
            assertThat(page.locator("[data-live-score]").textContent()).isEqualTo("11 – 0");
            assertThat(page.locator("[data-live-campaign-score]").textContent()).isEqualTo("11 – 0");
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
            assertThat(page.locator("[data-live-link]").getAttribute("href")).isEqualTo(campaignEventLink());
            assertThat(page.locator("[data-live-age]").textContent()).isEqualTo("90 s");
            // The canonical header and campaign panel have distinct provenance and score targets.
            // A newer manual J4 result must survive an older live result, which still updates its own panel.
            page.evaluate("document.querySelector('[data-live-event-id]').dataset.liveCanonicalReceivedAt = '2026-09-08T12:00:00Z';"
                    + "document.querySelector('[data-live-score]').textContent = '3 – 0';"
                    + "document.querySelector('[data-live-sport-status]').textContent = 'Victoire sur tapis vert'");
            revision.set(13);
            page.waitForCondition(() -> "13 – 0".equals(page.locator("[data-live-campaign-score]").textContent()));
            assertThat(page.locator("[data-live-score]").textContent()).isEqualTo("3 – 0");
            assertThat(page.locator("[data-live-sport-status]").textContent()).isEqualTo("Victoire sur tapis vert");
            assertThat(page.locator("[data-live-event-id]").getAttribute("data-live-canonical-received-at"))
                    .isEqualTo("2026-09-08T12:00:00Z");
            // A newly launched older preparation outranks a later preparation that never started.
            revision.set(20);
            page.reload();
            page.waitForCondition(() -> "20 – 0".equals(page.locator("[data-live-score]").textContent()));
            revision.set(21);
            page.waitForCondition(() -> "21 – 0".equals(page.locator("[data-live-score]").textContent()));
            assertThat(page.locator("[data-live-link]").getAttribute("href")).isEqualTo(campaignEventLink());
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

            // Canceled retains its existing selection behavior; postponed is excluded from capacity.
            revision.set(33);
            page.waitForCondition(() -> "canceled".equals(page.locator("[data-live-sport-status]").textContent()));
            assertThat(page.locator("input[name=eventId]").isDisabled()).isFalse();
            page.locator("input[name=eventId]").check();
            assertThat(page.locator("[data-live-eligible-count]").textContent())
                    .isEqualTo("1 rencontre sélectionnée admissible / 100");
            assertThat(page.locator("[data-live-prepare]").isDisabled()).isFalse();
            revision.set(34);
            page.waitForCondition(() -> "postponed".equals(page.locator("[data-live-sport-status]").textContent()));
            assertThat(page.locator("[data-live-event-state]").textContent()).isEqualTo("STOPPED_POSTPONED");
            assertThat(page.locator("input[name=eventId]").getAttribute("data-live-finished")).isEqualTo("true");
            assertThat(page.locator("input[name=eventId]").isChecked()).isTrue();
            assertThat(page.locator("[data-live-selection-count]").textContent()).isEqualTo("1 rencontre sélectionnée");
            assertThat(page.locator("[data-live-eligible-count]").textContent())
                    .isEqualTo("0 rencontre sélectionnée admissible / 100");
            assertThat(page.locator("[data-live-sport-context]").textContent()).contains("reportée", "suivi est arrêté");
            assertThat(posts.get()).isZero();
            assertThat(externalRequests.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Timeout(45)
    void runtimeWarningStaysInsideItsTableCellAndHiddenStillRemovesItsLayout() throws Exception {
        String configured = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(configured).as("explicit browser cache opt-in").isNotBlank();
        assertThat(Path.of(configured).toRealPath()).isEqualTo(
                Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
        String html = runtimeWarningPage();
        String css = resource("/static/css/app.css");
        String script = resource("/static/js/live-campaign.js");
        String origin = "http://127.0.0.1:8087";
        AtomicInteger revision = new AtomicInteger(10);
        AtomicInteger external = new AtomicInteger();
        AtomicInteger mutations = new AtomicInteger();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
            // All routes are fulfilled in memory. The operator's listener on 8087 is never contacted.
            context.route("**/*", route -> {
                if (!route.request().url().startsWith(origin + "/")) {
                    external.incrementAndGet(); route.abort(); return;
                }
                if (!"GET".equals(route.request().method())) {
                    mutations.incrementAndGet(); route.abort(); return;
                }
                String path = java.net.URI.create(route.request().url()).getPath();
                String body;
                String type;
                switch (path) {
                    case "/events" -> { body = html; type = "text/html"; }
                    case "/css/app.css" -> { body = css; type = "text/css"; }
                    case "/js/live-campaign.js" -> { body = script; type = "text/javascript"; }
                    case "/events/state" -> { body = runtimeWarningState(revision.get()); type = "application/json"; }
                    default -> { route.fulfill(new Route.FulfillOptions().setStatus(404).setBody("")); return; }
                }
                route.fulfill(new Route.FulfillOptions().setStatus(200).setContentType(type + "; charset=UTF-8")
                        .setHeaders(java.util.Map.of("Cache-Control", "no-store", "Content-Security-Policy",
                                "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; form-action 'self'"))
                        .setBody(body));
            });
            Page page = context.newPage();
            page.navigate(origin + "/events");
            Locator notice = page.locator("[data-live-runtime-status]");
            page.waitForCondition(notice::isVisible);
            assertThat(notice.textContent()).isEqualTo("Collecte arrêtée / clôture locale requise.");
            assertThat(notice.getAttribute("role")).isEqualTo("status");
            for (int width : new int[] {1440, 1024, 390}) {
                page.setViewportSize(width, 900);
                var warning = notice.boundingBox();
                var status = page.locator("[data-live-event-state]").boundingBox();
                var cell = page.locator("[data-live-event-id] > td").first().boundingBox();
                var adjacent = page.locator("[data-live-event-id] > td").nth(1).boundingBox();
                var nextRow = page.locator("[data-notice-next-row]").boundingBox();
                assertThat(warning).as("warning is laid out at viewport %s", width).isNotNull();
                assertThat(warning.y).as("warning starts below the status at viewport %s", width)
                        .isGreaterThanOrEqualTo(status.y + status.height);
                assertThat(warning.x).isGreaterThanOrEqualTo(cell.x);
                assertThat(warning.x + warning.width).isLessThanOrEqualTo(adjacent.x + 0.5);
                assertThat(warning.y + warning.height).isLessThanOrEqualTo(cell.y + cell.height);
                assertThat(warning.y + warning.height).isLessThanOrEqualTo(nextRow.y);
            }
            double nextRowWithWarning = page.locator("[data-notice-next-row]").boundingBox().y;
            revision.set(11);
            page.waitForCondition(notice::isHidden);
            assertThat(notice.getAttribute("hidden")).isNotNull();
            assertThat(notice.boundingBox()).isNull();
            assertThat(page.locator("[data-notice-next-row]").boundingBox().y).isLessThan(nextRowWithWarning);
            assertThat(page.locator("[data-live-event-state]").textContent()).isEqualTo("COLLECTING");
            assertThat(external.get()).isZero();
            assertThat(mutations.get()).isZero();
        }
    }

    @Test
    @Timeout(45)
    void suspensionReasonIsRemovedFromLayoutUntilJ4ReportsASuspension() throws Exception {
        String configured = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(configured).as("explicit browser cache opt-in").isNotBlank();
        assertThat(Path.of(configured).toRealPath()).isEqualTo(
                Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());
        String html = statusReasonPage();
        String css = resource("/static/css/app.css");
        String script = resource("/static/js/live-campaign.js");
        String origin = "http://127.0.0.1:8087";
        AtomicInteger revision = new AtomicInteger(10);
        AtomicInteger external = new AtomicInteger();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
            context.route("**/*", route -> {
                if (!route.request().url().startsWith(origin + "/")) {
                    external.incrementAndGet(); route.abort(); return;
                }
                String path = java.net.URI.create(route.request().url()).getPath();
                String body;
                String type;
                switch (path) {
                    case "/events" -> { body = html; type = "text/html"; }
                    case "/css/app.css" -> { body = css; type = "text/css"; }
                    case "/js/live-campaign.js" -> { body = script; type = "text/javascript"; }
                    case "/events/state" -> { body = statusReasonState(revision.get()); type = "application/json"; }
                    default -> { route.fulfill(new Route.FulfillOptions().setStatus(404).setBody("")); return; }
                }
                route.fulfill(new Route.FulfillOptions().setStatus(200).setContentType(type + "; charset=UTF-8")
                        .setHeaders(java.util.Map.of("Cache-Control", "no-store", "Content-Security-Policy",
                                "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; form-action 'self'"))
                        .setBody(body));
            });
            Page page = context.newPage();
            page.navigate(origin + "/events");
            page.waitForCondition(() -> "inprogress".equals(page.locator("[data-live-sport-status]").textContent()));
            Locator reasonRow = page.locator("[data-live-status-reason-row]");
            assertThat(reasonRow.getAttribute("hidden")).isNotNull();
            assertThat(reasonRow.boundingBox()).isNull();

            revision.set(11);
            page.reload();
            page.waitForCondition(reasonRow::isVisible);
            assertThat(reasonRow.getAttribute("hidden")).isNull();
            assertThat(page.locator("[data-live-status-reason]").textContent())
                    .isEqualTo("Suspension temporaire décidée par l’arbitre");
            assertThat(external.get()).isZero();
        }
    }

    private static String campaignEventLink() {
        return "/live-campaigns/" + CAMPAIGN + "?eventId=" + EVENT + "#live-event-" + EVENT;
    }

    private static String resource(String name) throws IOException {
        try (var input = LiveCampaignBrowserQualificationIT.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("required qualification resource missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String runtimeWarningPage() throws IOException {
        String template = resource("/templates/events.html");
        int row = template.indexOf("<tr th:each=\"item : ${search.events}\" data-live-mirror-canonical");
        assertThat(row).as("production events row exists").isGreaterThanOrEqualTo(0);
        int start = template.indexOf("<td>", row);
        int end = template.indexOf("</td>", start) + "</td>".length();
        // Keep the production cell's real tag hierarchy, classes and hidden attribute. Only
        // server expressions are removed; other cells provide representative layout neighbours.
        String cell = template.substring(start, end)
                .replaceAll("\\s+th:[\\w-]+=\"[^\"]*\"", "")
                .replace("name=\"eventId\"", "name=\"eventId\" value=\"" + EVENT
                        + "\" data-live-provider-eligible=\"true\"");
        return """
                <!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8">
                <link rel="stylesheet" href="/css/app.css"><script defer src="/js/live-campaign.js"></script>
                </head><body><main class="shell event-page"><section class="panel" data-live-monitor data-live-state-url="/events/state">
                <form id="live-selection" data-live-selection-form></form><p data-live-refresh-status></p>
                <div class="table-scroll"><table><thead><tr><th>Campagne live</th><th>Heure locale</th><th>Rencontre</th>
                <th>Compétition</th><th>Statut</th><th>Identité locale</th><th>Provenance</th><th>Détail</th></tr></thead><tbody>
                <tr data-live-event-id="%s" data-live-mirror-canonical>%s
                <td class="mono">2026-09-08T21:00+02:00[Europe/Paris]</td><td><strong>Équipe domicile — Équipe extérieure</strong></td>
                <td>Compétition locale</td><td>2nd half<br>1 – 0</td><td class="mono">%s</td><td>PROVIDER_SNAPSHOT<br>snapshot:42</td>
                <td><a class="table-link" href="/events/%s">Ouvrir</a></td></tr>
                <tr data-notice-next-row><td>Sélectionner</td><td>21:15</td><td>Rencontre suivante</td><td>Compétition locale</td>
                <td>notstarted</td><td>Identité</td><td>snapshot:43</td><td>Ouvrir</td></tr>
                </tbody></table></div></section></main></body></html>
                """.formatted(EVENT, cell, EVENT, EVENT);
    }

    private static String statusReasonPage() {
        return """
                <!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8">
                <link rel="stylesheet" href="/css/app.css"><script defer src="/js/live-campaign.js"></script>
                </head><body><main class="shell"><section class="panel" data-live-monitor data-live-state-url="/events/state">
                <p data-live-refresh-status role="status"></p><div data-live-event-id="%s">
                <span data-live-sport-status></span><dl class="detail-grid">
                <div><dt>Score courant J4</dt><dd data-live-score>—</dd></div>
                <div data-live-status-reason-row hidden><dt>Raison de suspension J4</dt>
                <dd data-live-status-reason>—</dd></div></dl></div></section></main></body></html>
                """.formatted(EVENT);
    }

    private static String runtimeWarningState(int revision) {
        String runtime = revision == 10 ? """
                {"state":"STOPPED_ERROR","reason":"LOCAL_CLEANUP_PENDING","collectionStopped":true,
                "cleanupPending":true,"cleanupInProgress":false,"label":"Collecte arrêtée / clôture locale requise."}
                """ : "null";
        return state(revision).replace("\"events\":[", "\"runtimeStatus\":" + runtime + ",\"events\":[");
    }

    private static String statusReasonState(int revision) {
        boolean suspended = revision == 11;
        return """
                [{"campaignId":"%s","revision":%d,"state":"RUNNING","preparedAt":"2026-09-11T19:00:00Z",
                "startedAt":"2026-09-11T19:00:01Z","events":[{"canonicalEventId":"%s","sportStatus":"%s",
                "statusReason":%s,"state":"%s","selectionBlocked":false,"score":"0 – 1","canonicalCurrent":true,
                "sourceReceivedAt":"2026-09-11T19:00:00Z"}]}]
                """.formatted(CAMPAIGN, revision, EVENT, suspended ? "suspended" : "inprogress",
                suspended ? "\"Suspension temporaire décidée par l’arbitre\"" : "null",
                suspended ? "WAITING_SUSPENDED_RECHECK" : "COLLECTING");
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
                    <span data-live-eligible-count></span>
                  </form><p data-live-refresh-status></p>
                  <div data-live-event-id="%s" data-live-mirror-canonical
                    data-live-canonical-received-at="2026-09-07T12:00:00Z"><label><input form="live-selection" type="checkbox"
                    name="eventId" value="%s" data-live-provider-eligible="true">Sélectionner</label>
                    <span data-live-selection-blocked hidden>Déjà dans une campagne en cours</span>
                    <span data-live-sport-status></span><span data-live-event-state></span><span data-live-score></span>
                    <span data-live-campaign-score>—</span><p data-live-sport-context></p>
                    <a data-live-link hidden>Campagne</a><div data-live-families></div>
                  </div>
                </section></body></html>
                """.formatted(EVENT, EVENT);
    }

    private static String state(int revision) {
        boolean laterPreparation = revision == 20 || revision == 22;
        return """
                [{"campaignId":"%s","revision":%d,"state":"%s","preparedAt":"%s","startedAt":%s,
                "events":[{"canonicalEventId":"%s","sportStatus":"%s","state":"%s","selectionBlocked":%s,
                "score":"%d – 0","canonicalCurrent":true,"sourceReceivedAt":"2026-09-07T12:00:00Z",
                "reservedCalls":3,"maximumCalls":1000,"families":[
                {"endpoint":"EVENT_STATISTICS","label":"Statistiques","outcome":"PARSED","code":null,
                "scope":"EVENT","lastReceivedAt":"2026-09-07T12:00:00Z","lastSuccessfulAt":"2026-09-07T12:00:00Z",
                "lastCacheRevalidatedAt":"2026-09-07T12:01:00Z",
                "lastChangedAt":"2026-09-07T12:00:00Z","receivedSnapshotId":1,"dataSnapshotId":1,
                "parserVersion":"statistics-v2","normalizedSha256":"abc","completeness":"COMPLETE",
                "completenessScore":100,"previousData":false,"freshness":{"state":"%s","label":"%s",
                "frozen":%s,"ageAsOf":"2026-09-07T12:01:30Z"},"table":{"columns":["Domicile","Extérieur"],
                "rows":[["<img src=x onerror=alert(1)>","10"]]}}]}]}]
                """.formatted(laterPreparation ? "00000000-0000-0000-0000-000000000059" : CAMPAIGN,
                revision, laterPreparation ? "PREPARED" : revision == 31 ? "STOPPED_ERROR" : "RUNNING",
                laterPreparation ? "2026-09-07T12:00:10Z" : "2026-09-07T12:00:00Z",
                laterPreparation ? "null" : "\"2026-09-07T12:00:20Z\"", EVENT,
                revision == 33 ? "canceled" : revision == 34 ? "postponed" : "inprogress",
                revision == 31 ? "STOPPED_ERROR" : revision == 33 ? "STOPPED_REVIEW_REQUIRED"
                        : revision == 34 ? "STOPPED_POSTPONED" : "COLLECTING",
                revision == 30 || revision == 32, revision,
                revision <= 10 ? "STALE" : "FROZEN", revision <= 10 ? "En retard / périmée" : "Âge figé",
                revision > 10);
    }
}
