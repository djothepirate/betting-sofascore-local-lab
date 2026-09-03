package com.bettingproject.sofascorelocal.qualification;

import com.bettingproject.sofascorelocal.adapter.web.J7DeliveryController;
import com.bettingproject.sofascorelocal.adapter.web.J7ExportController;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryExecutionGate;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryPayloadClass;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryQueryService;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryRuntimeService;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryView;
import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.application.export.J7ExportPreview;
import com.bettingproject.sofascorelocal.config.J7DeliveryWebMvcConfiguration;
import com.bettingproject.sofascorelocal.config.SecurityHeadersFilter;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.security.J7DeliveryConfirmationService;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Explicit, opt-in browser qualification for the WO-037 response-policy correction.
 *
 * <p>The embedded application imports the production filter, interceptor, controllers and HTML
 * template. Every service below the web boundary is a test double: no database, delivery claim,
 * certificate store, receiver transport or provider component exists in this application.</p>
 */
class J7BrowserOriginLoopbackQualificationIT {

    private static final int LOCAL_PORT = 8_087;
    private static final int RECEIVER_PORT = 8_444;
    private static final String LOCAL_ORIGIN = "http://127.0.0.1:8087";
    private static final UUID EVENT_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID EXPORT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final String FILE_SHA256 = "a".repeat(64);
    private static final String DATA_SHA256 = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-03T09:00:00Z");
    private static final String PREVIEW_PATH =
            "/events/" + EVENT_ID + "/exports/" + EXPORT_ID;
    private static final String PREPARE_PATH = PREVIEW_PATH + "/delivery/prepare";

    @Test
    @Timeout(90)
    void nativeChromiumUsesExactLoopbackOriginAndOpaqueOriginRemainsForbidden()
            throws Throwable {
        Path browserCache = requiredBrowserCache();
        assertPortFree(LOCAL_PORT);
        assertPortFree(RECEIVER_PORT);

        ConfigurableApplicationContext application = null;
        CopyOnWriteArrayList<ProcessIdentity> browserProcesses =
                new CopyOnWriteArrayList<>();
        AtomicInteger nonLoopbackRequests = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();
        Throwable primaryFailure = null;
        try {
            application = startMinimalApplication();
            RequestObservationFilter observer = application.getBean(
                    RequestObservationFilter.class);
            J7CanonicalExportService exportService = application.getBean(
                    J7CanonicalExportService.class);
            J7DeliveryRuntimeService runtimeService = application.getBean(
                    J7DeliveryRuntimeService.class);
            J7DeliveryLedgerStore ledgerStore = application.getBean(
                    J7DeliveryLedgerStore.class);

            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(
                         new BrowserType.LaunchOptions().setHeadless(true))) {
                browserProcesses.addAll(captureDescendants());
                assertThat(browserProcesses).isNotEmpty();
                try {
                    try (BrowserContext context = browser.newContext(
                            new Browser.NewContextOptions()
                                    .setAcceptDownloads(false)
                                    .setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
                        context.route("**/*", route -> {
                            if (isExactAllowedBrowserRequest(route.request().url())) {
                                route.resume();
                            }
                            else {
                                nonLoopbackRequests.incrementAndGet();
                                route.abort();
                            }
                        });

                        Page localPage = context.newPage();
                        localPage.onDownload(ignored -> downloads.incrementAndGet());
                        Response previewResponse = localPage.navigate(
                                LOCAL_ORIGIN + PREVIEW_PATH);
                        assertThat(previewResponse).isNotNull();
                        assertThat(previewResponse.status()).isEqualTo(200);
                        assertJ7SecurityHeaders(previewResponse);

                        var postForms = localPage.locator("form[method='post']");
                        assertThat(postForms.count()).isEqualTo(1);
                        var prepareForm = postForms.first();
                        assertThat(isExpectedPrepareAction(
                                prepareForm.getAttribute("action"))).isTrue();
                        Response prepareResponse = localPage.waitForNavigation(() ->
                                prepareForm.locator("button[type='submit']").click());
                        assertThat(prepareResponse).isNotNull();
                        assertThat(prepareResponse.status()).isEqualTo(200);
                        assertJ7SecurityHeaders(prepareResponse);
                        assertThat(localPage.locator(
                                "form[action$='/delivery/execute']").count()).isEqualTo(1);
                        assertThat(localPage.locator("code.confirmation-phrase").first()
                                .textContent()).startsWith("LIVRER J7 " + EXPORT_ID);

                        String unconsumedFormToken = localPage.locator(
                                        "form[action$='/delivery/execute'] "
                                                + "input[name='localFormToken']")
                                .inputValue();
                        Page opaquePage = context.newPage();
                        opaquePage.onDownload(ignored -> downloads.incrementAndGet());
                        opaquePage.setContent(opaquePrepareForm(unconsumedFormToken));
                        assertThat(opaquePage.evaluate("location.origin")).isEqualTo("null");
                        Response deniedResponse = opaquePage.waitForNavigation(() ->
                                opaquePage.locator("#opaque-prepare").evaluate(
                                        "form => form.submit()"));
                        assertThat(deniedResponse).isNotNull();
                        assertThat(deniedResponse.status()).isEqualTo(403);
                        assertThat(deniedResponse.body()).isEmpty();
                        opaquePage.close();
                    }
                }
                finally {
                    browserProcesses.addAll(captureDescendants());
                }

                assertThat(nonLoopbackRequests).hasValue(0);
                assertThat(downloads).hasValue(0);

                assertThat(observer.prepareRequests()).containsExactly(
                        new ObservedPrepareRequest(
                                "POST",
                                PREPARE_PATH,
                                List.of("127.0.0.1:8087"),
                                List.of(LOCAL_ORIGIN),
                                false,
                                200),
                        new ObservedPrepareRequest(
                                "POST",
                                PREPARE_PATH,
                                List.of("127.0.0.1:8087"),
                                List.of("null"),
                                false,
                                403));
                assertThat(observer.deliveryExecuteRequestCount()).isZero();
                assertThat(observer.reconciliationRequestCount()).isZero();
                verify(exportService, org.mockito.Mockito.times(2))
                        .preview(EVENT_ID, EXPORT_ID);
                verify(runtimeService, never()).deliver(any(), any(), any(), any(Integer.class));
                verify(ledgerStore, never()).claim(
                        any(), any(), any(), any(), any(Integer.class), any());
            }
        }
        catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        }
        finally {
            Throwable cleanupFailure = null;
            try {
                if (application != null) {
                    application.close();
                }
            }
            catch (Throwable failure) {
                cleanupFailure = failure;
            }
            try {
                assertOwnedProcessesExited(browserProcesses);
                assertPortEventuallyFree(LOCAL_PORT);
                assertPortEventuallyFree(RECEIVER_PORT);
            }
            catch (Throwable failure) {
                if (cleanupFailure == null) {
                    cleanupFailure = failure;
                }
                else {
                    cleanupFailure.addSuppressed(failure);
                }
            }
            if (cleanupFailure != null) {
                if (primaryFailure == null) {
                    throw cleanupFailure;
                }
                primaryFailure.addSuppressed(cleanupFailure);
            }
        }

        assertThat(nonLoopbackRequests).hasValue(0);
        assertThat(downloads).hasValue(0);
        assertThat(Files.isDirectory(browserCache)).isTrue();
    }

    private static void assertJ7SecurityHeaders(Response response) {
        assertThat(response.headerValue("Referrer-Policy"))
                .isEqualTo("same-origin");
        assertThat(response.headerValue("Cache-Control"))
                .isEqualTo("no-store, no-cache, must-revalidate, max-age=0");
        assertThat(response.headerValue("Pragma")).isEqualTo("no-cache");
        assertThat(response.headerValue("Expires")).isEqualTo("0");
        assertThat(response.headerValue("X-Robots-Tag"))
                .isEqualTo("noindex, nofollow, noarchive");
        assertThat(response.headerValue("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.headerValue("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(response.headerValue("Content-Security-Policy"))
                .contains(
                        "default-src 'self'",
                        "script-src 'none'",
                        "frame-ancestors 'none'",
                        "form-action 'self'");
    }

    private static ConfigurableApplicationContext startMinimalApplication() {
        return new SpringApplicationBuilder(QualificationApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(Map.of(
                        "server.address", "127.0.0.1",
                        "server.port", Integer.toString(LOCAL_PORT),
                        "spring.main.banner-mode", "off",
                        "spring.jmx.enabled", "false",
                        "logging.level.root", "OFF"))
                .registerShutdownHook(false)
                .run();
    }

    private static Path requiredBrowserCache() throws IOException {
        String configured = System.getProperty("j7.browser.cache", "");
        if (configured.isBlank()) {
            throw new IllegalStateException("j7.browser.cache is required");
        }
        Path cache = Path.of(configured).toRealPath();
        Path environmentCache = Path.of(requiredEnvironment(
                "PLAYWRIGHT_BROWSERS_PATH")).toRealPath();
        assertThat(environmentCache).isEqualTo(cache);
        assertThat(Files.isDirectory(cache)).isTrue();
        return cache;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private static boolean isExactAllowedBrowserRequest(String value) {
        try {
            URI uri = URI.create(value);
            return "http".equals(uri.getScheme())
                    && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() == LOCAL_PORT
                    && uri.getRawUserInfo() == null;
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String opaquePrepareForm(String token) {
        return "<!doctype html><meta charset=\"utf-8\"><form id=\"opaque-prepare\""
                + " method=\"post\" action=\"" + LOCAL_ORIGIN + PREPARE_PATH + "\">"
                + "<input type=\"hidden\" name=\"localFormToken\" value=\""
                + htmlAttribute(token) + "\"><button type=\"submit\">submit</button></form>";
    }

    private static boolean isExpectedPrepareAction(String action) {
        if (PREPARE_PATH.equals(action)) {
            return true;
        }
        if (action == null || !action.startsWith(PREPARE_PATH + ";jsessionid=")) {
            return false;
        }
        String sessionId = action.substring((PREPARE_PATH + ";jsessionid=").length());
        return sessionId.matches("[A-Za-z0-9._-]{16,128}");
    }

    private static String htmlAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static void assertPortFree(int port) throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"), port));
        }
    }

    private static void assertPortEventuallyFree(int port) throws Exception {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                assertPortFree(port);
                return;
            }
            catch (IOException exception) {
                lastFailure = exception;
                Thread.sleep(100);
            }
        }
        throw new AssertionError("loopback port was not released", lastFailure);
    }

    private static List<ProcessIdentity> captureDescendants() {
        try (Stream<ProcessHandle> descendants = ProcessHandle.current().descendants()) {
            return descendants
                    .map(ProcessIdentity::capture)
                    .flatMap(Optional::stream)
                    .toList();
        }
    }

    private static void assertOwnedProcessesExited(List<ProcessIdentity> identities)
            throws InterruptedException {
        List<ProcessIdentity> distinctIdentities = identities.stream()
                .distinct()
                .toList();
        for (int attempt = 0; attempt < 50
                && distinctIdentities.stream().anyMatch(ProcessIdentity::isSameProcessAlive);
             attempt++) {
            Thread.sleep(100);
        }
        assertThat(distinctIdentities)
                .allMatch(identity -> !identity.isSameProcessAlive());
    }

    private static J7ExportPreview preview() {
        J7ExportManifest manifest = new J7ExportManifest(
                1,
                EXPORT_ID,
                EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                NOW,
                DATA_SHA256,
                "c".repeat(64),
                "d".repeat(64),
                FILE_SHA256,
                1_024,
                "wo037-synthetic.validated.json",
                "[]",
                List.of(),
                "[]",
                J7ExportStatus.HUMAN_VALIDATED,
                Optional.of(NOW),
                Optional.empty());
        return new J7ExportPreview(
                manifest,
                "{}",
                "VALIDER",
                "REJETER",
                J7DeliveryPayloadClass.SYNTHETIC_ONLY);
    }

    private record ProcessIdentity(long pid, Instant startedAt) {

        private static Optional<ProcessIdentity> capture(ProcessHandle process) {
            return process.info().startInstant()
                    .map(startedAt -> new ProcessIdentity(process.pid(), startedAt));
        }

        private boolean isSameProcessAlive() {
            return ProcessHandle.of(pid)
                    .filter(ProcessHandle::isAlive)
                    .flatMap(process -> process.info().startInstant())
                    .map(startedAt::equals)
                    .orElse(false);
        }
    }

    private record ObservedPrepareRequest(
            String method,
            String path,
            List<String> host,
            List<String> origin,
            boolean forwardedHeadersPresent,
            int responseStatus) {
    }

    static final class RequestObservationFilter extends OncePerRequestFilter {

        private final CopyOnWriteArrayList<ObservedPrepareRequest> requests =
                new CopyOnWriteArrayList<>();
        private final AtomicInteger deliveryExecuteRequests = new AtomicInteger();
        private final AtomicInteger reconciliationRequests = new AtomicInteger();

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            String requestUri = request.getRequestURI();
            boolean observed = PREPARE_PATH.equals(requestUri)
                    || requestUri.startsWith(PREPARE_PATH + ";jsessionid=");
            if (requestUri.startsWith(PREVIEW_PATH + "/delivery/execute")) {
                deliveryExecuteRequests.incrementAndGet();
            }
            if (requestUri.startsWith(PREVIEW_PATH + "/delivery/reconciliation/")) {
                reconciliationRequests.incrementAndGet();
            }
            List<String> host = observed
                    ? Collections.list(request.getHeaders("Host"))
                    : List.of();
            List<String> origin = observed
                    ? Collections.list(request.getHeaders("Origin"))
                    : List.of();
            boolean forwarded = observed
                    && (request.getHeaders("Forwarded").hasMoreElements()
                    || request.getHeaders("X-Forwarded-Host").hasMoreElements()
                    || request.getHeaders("X-Forwarded-Proto").hasMoreElements());
            filterChain.doFilter(request, response);
            if (observed) {
                requests.add(new ObservedPrepareRequest(
                        request.getMethod(),
                        PREPARE_PATH,
                        List.copyOf(host),
                        List.copyOf(origin),
                        forwarded,
                        response.getStatus()));
            }
        }

        private List<ObservedPrepareRequest> prepareRequests() {
            return List.copyOf(requests);
        }

        private int deliveryExecuteRequestCount() {
            return deliveryExecuteRequests.get();
        }

        private int reconciliationRequestCount() {
            return reconciliationRequests.get();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
    })
    @Import({
        SecurityHeadersFilter.class,
        J7DeliveryWebMvcConfiguration.class,
        J7ExportController.class,
        J7DeliveryController.class,
        LocalFormTokenService.class,
        J7DeliveryConfirmationService.class,
        J7DeliveryExecutionGate.class
    })
    static class QualificationApplication {

        @Bean
        J7CanonicalExportService exportService() {
            J7CanonicalExportService service = mock(J7CanonicalExportService.class);
            when(service.preview(EVENT_ID, EXPORT_ID)).thenReturn(preview());
            return service;
        }

        @Bean
        J7DeliveryQueryService deliveryQueryService() {
            J7DeliveryQueryService service = mock(J7DeliveryQueryService.class);
            when(service.view(any())).thenReturn(new J7DeliveryView(
                    J7DeliveryPayloadClass.SYNTHETIC_ONLY,
                    List.of(),
                    Optional.empty(),
                    true,
                    false));
            return service;
        }

        @Bean
        J7DeliveryRuntimeService deliveryRuntimeService() {
            return mock(J7DeliveryRuntimeService.class);
        }

        @Bean
        J7DeliveryLedgerStore deliveryLedgerStore() {
            return mock(J7DeliveryLedgerStore.class);
        }

        @Bean
        RequestObservationFilter requestObservationFilter() {
            return new RequestObservationFilter();
        }
    }
}
