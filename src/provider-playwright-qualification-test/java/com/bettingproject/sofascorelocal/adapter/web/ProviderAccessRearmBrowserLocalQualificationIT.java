package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.config.LiveCampaignWebMvcConfiguration;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.Guard;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureDecision;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureReason;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.Snapshot;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.State;
import com.bettingproject.sofascorelocal.port.ProviderCampaignGuardStore;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Chromium qualification of the provider-access rearm form.
 *
 * <p>Every request made by the offline browser is fulfilled directly by MockMvc. This neither
 * starts a local listener nor opens a Playwright provider session, and any URL outside the exact
 * loopback origin is aborted and counted as a qualification failure.</p>
 */
@WebMvcTest(ProviderAccessController.class)
@Import({LocalFormTokenService.class, LiveCampaignWebMvcConfiguration.class})
class ProviderAccessRearmBrowserLocalQualificationIT {
    private static final long VERSION = 7L;

    @Autowired private MockMvc mvc;
    @MockitoBean private ProviderResilienceStore store;
    @MockitoBean private ProviderCampaignGuardStore guard;
    @MockitoBean private PlaywrightProviderSupervisor supervisor;
    @MockitoBean private ManualProviderRequestCoordinator coordinator;
    @MockitoBean private CacheManager cacheManager;

    @ParameterizedTest
    @ValueSource(strings = {"localhost:8087", "127.0.0.1:8087"})
    @Timeout(120)
    void rearmFormKeepsItsExactLoopbackOriginAndNeverStartsProviderTraffic(String host) throws Exception {
        String configured = System.getProperty("provider.playwright.browser-cache", "");
        assertThat(configured).as("explicit browser cache opt-in").isNotBlank();
        assertThat(Path.of(configured).toRealPath()).isEqualTo(
                Path.of(System.getenv("PLAYWRIGHT_BROWSERS_PATH")).toRealPath());

        Snapshot suspended = suspendedSnapshot();
        when(store.snapshot()).thenReturn(suspended);
        when(store.departureDecision(any(Instant.class))).thenReturn(
                new DepartureDecision(false, DepartureReason.PROVIDER_SUSPENDED, null, suspended));
        when(guard.snapshot()).thenReturn(new Guard("FREE", null, null, 1, Instant.now()));
        when(supervisor.activeCampaignId()).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(coordinator).withExclusiveLocalCleanup(any());

        String origin = "http://" + host;
        MockHttpSession session = new MockHttpSession();
        List<ObservedPost> posts = new ArrayList<>();
        AtomicInteger externalRequests = new AtomicInteger();
        AtomicReference<Throwable> bridgeFailure = new AtomicReference<>();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setAcceptDownloads(false)
                     .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
                     .setOffline(true))) {
            context.route("**/*", route -> {
                Request browserRequest = route.request();
                if (!browserRequest.url().startsWith(origin + "/")) {
                    externalRequests.incrementAndGet();
                    route.abort();
                    return;
                }
                try {
                    URI uri = URI.create(browserRequest.url());
                    var request = MockMvcRequestBuilders.request(
                                    HttpMethod.valueOf(browserRequest.method()), uri)
                            .session(session);
                    browserRequest.allHeaders().forEach((name, value) -> {
                        if (!name.equalsIgnoreCase("host")) {
                            request.header(name, value);
                        }
                    });
                    // Keep Chromium's Origin and Fetch Metadata untouched; only synthesize the
                    // Host header that an actual request to this URL would carry.
                    request.header("Host", uri.getRawAuthority());
                    Map<String, String> form = formParameters(browserRequest.postData());
                    form.forEach(request::param);

                    var response = mvc.perform(request).andReturn().getResponse();
                    if (browserRequest.method().equals("POST")) {
                        posts.add(new ObservedPost(uri.getPath(), browserRequest.headerValue("origin"),
                                response.getStatus(), form));
                    }
                    // The route interception owns redirect delivery. Follow the controller's
                    // local relative redirect in MockMvc, rather than allowing Chromium to
                    // reach the operator listener. Preserve the original 302 above as evidence.
                    if (response.getRedirectedUrl() != null) {
                        String location = response.getRedirectedUrl();
                        assertThat(location).isEqualTo("/provider-access");
                        response = mvc.perform(MockMvcRequestBuilders.get(URI.create(origin + location))
                                .header("Host", host).session(session)).andReturn().getResponse();
                    }
                    Map<String, String> headers = new LinkedHashMap<>();
                    for (String name : response.getHeaderNames()) {
                        headers.put(name, response.getHeader(name));
                    }
                    route.fulfill(new Route.FulfillOptions()
                            .setStatus(response.getStatus())
                            .setHeaders(headers)
                            .setBodyBytes(response.getContentAsByteArray()));
                }
                catch (Throwable failure) {
                    bridgeFailure.compareAndSet(null, failure);
                    route.abort();
                }
            });

            Page page = context.newPage();
            Response rendered = page.navigate(origin + "/provider-access");
            assertThat(rendered.status()).isEqualTo(200);
            assertThat(rendered.headerValue("Referrer-Policy")).isEqualTo("same-origin");

            Locator form = page.locator("form[action='/provider-access/rearm']");
            assertThat(form.count()).isEqualTo(1);
            assertThat(form.locator("input[name=version]").inputValue()).isEqualTo(Long.toString(VERSION));
            assertThat(form.locator("input[name=localFormToken]").inputValue()).isNotBlank();
            form.locator("input[name=confirmation]").check();
            Response completed = page.waitForNavigation(() -> form.locator("button[type=submit]").click());
            assertThat(completed.status()).isEqualTo(200);
            assertThat(completed.headerValue("Referrer-Policy")).isEqualTo("same-origin");
        }

        assertThat(posts).hasSize(1);
        ObservedPost rearm = posts.getFirst();
        assertThat(rearm.path()).isEqualTo("/provider-access/rearm");
        assertThat(rearm.origin()).isEqualTo(origin);
        assertThat(rearm.status()).isEqualTo(302);
        assertThat(rearm.form()).containsEntry("version", Long.toString(VERSION))
                .containsEntry("confirmation", "true");
        assertThat(rearm.form().get("localFormToken")).isNotBlank();
        assertThat(bridgeFailure.get()).isNull();
        assertThat(externalRequests).hasValue(0);

        // snapshot/decision are read-only page rendering. The only state transition is the
        // versioned rearm; no departure, reservation, response suspension or worker lifecycle
        // action is permitted by this local form.
        verify(store, times(3)).snapshot();
        verify(store, times(2)).departureDecision(any(Instant.class));
        verify(store).rearm(eq(VERSION), any(Instant.class));
        verifyNoMoreInteractions(store);
        verify(guard).snapshot();
        verifyNoMoreInteractions(guard);
        verify(supervisor).activeCampaignId();
        verify(supervisor, never()).stopCampaign(any(), any());
        verifyNoMoreInteractions(supervisor);
        verify(coordinator).withExclusiveLocalCleanup(any());
        verifyNoMoreInteractions(coordinator);
    }

    private static Map<String, String> formParameters(String postData) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (postData == null || postData.isBlank()) {
            return parameters;
        }
        for (String pair : postData.split("&")) {
            String[] parts = pair.split("=", 2);
            parameters.put(decode(parts[0]), parts.length == 1 ? "" : decode(parts[1]));
        }
        return parameters;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static Snapshot suspendedSnapshot() {
        Instant observedAt = Instant.parse("2026-09-10T12:00:00Z");
        return new Snapshot(State.SUSPENDED, VERSION, observedAt, 403, observedAt,
                null, null, UUID.fromString("00000000-0000-0000-0000-000000000403"),
                UUID.fromString("00000000-0000-0000-0000-000000000058"), null, null);
    }

    private record ObservedPost(String path, String origin, int status, Map<String, String> form) { }
}
