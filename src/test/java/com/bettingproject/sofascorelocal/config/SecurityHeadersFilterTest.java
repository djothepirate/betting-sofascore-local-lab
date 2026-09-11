package com.bettingproject.sofascorelocal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void allowsOnlySameOriginScriptsOnTheOfflineBatchRoute() throws Exception {
        MockHttpServletResponse response = filter("/j5-import-batches");

        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'self'")
                .doesNotContain("'unsafe-inline'", "'unsafe-eval'");
    }

    @Test
    void allowsSameOriginScriptsWhenTheOfflineBatchRouteContainsARewrittenSessionId()
            throws Exception {
        MockHttpServletResponse response = filter(
                "/j5-import-batches;jsessionid=LOCAL_TEST_SESSION");

        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'self'")
                .doesNotContain("'unsafe-inline'", "'unsafe-eval'");
    }

    @Test
    void keepsAllScriptsDisabledOutsideTheExplicitLocalInteractiveRoutes() throws Exception {
        MockHttpServletResponse response = filter("/events/event-id/exports");

        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'none'")
                .doesNotContain("script-src 'self'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/events", "/events/12345678-1234-1234-1234-123456789012",
            "/events/12345678-1234-1234-1234-123456789012/statistics",
            "/events/12345678-1234-1234-1234-123456789012/statistics;jsessionid=LOCAL_TEST_SESSION",
            "/live-campaigns/12345678-1234-1234-1234-123456789012"})
    void permitsOnlyLocalExternalScriptsForLiveObservationPages(String path) throws Exception {
        MockHttpServletResponse response = filter(path);
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'self'", "frame-ancestors 'none'", "form-action 'self'")
                .doesNotContain("'unsafe-inline'", "'unsafe-eval'");
        assertThat(response.getHeader("Cache-Control")).contains("no-store", "no-cache");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/events;jsessionid=LOCAL_TEST_SESSION",
        "/live-campaigns/12345678-1234-1234-1234-123456789012;jsessionid=LOCAL_TEST_SESSION",
        "/live-campaigns/prepare"
    })
    void keepsAnExactOriginWhenLiveFormsAreRenderedAfterSessionRewritingOrAnError(String path)
            throws Exception {
        assertThat(filter(path).getHeader("Referrer-Policy")).isEqualTo("same-origin");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/provider-access",
        "/provider-access;jsessionid=LOCAL_TEST_SESSION"
    })
    void keepsAnExactOriginWhenTheProviderAccessRearmFormIsRendered(String path)
            throws Exception {
        MockHttpServletResponse response = filter(path);

        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store, no-cache, must-revalidate, max-age=0");
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'none'", "form-action 'self'");
    }

    @Test
    void appliesStrictReadOnlyHeadersToTheBenchmarkRoute() throws Exception {
        MockHttpServletResponse response = filter("/benchmark");

        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store, no-cache, must-revalidate, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getHeader("Expires")).isEqualTo("0");
        assertThat(response.getHeader("X-Robots-Tag"))
                .isEqualTo("noindex, nofollow, noarchive");
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'none'")
                .doesNotContain("script-src 'self'");
    }

    @Test
    void appliesStrictReadOnlyHeadersToARewrittenBenchmarkRoute() throws Exception {
        MockHttpServletResponse response = filter(
                "/benchmark;jsessionid=LOCAL_TEST_SESSION");

        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store, no-cache, must-revalidate, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getHeader("Expires")).isEqualTo("0");
        assertThat(response.getHeader("X-Robots-Tag"))
                .isEqualTo("noindex, nofollow, noarchive");
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'none'");
    }

    @Test
    void deniesFramingThroughBothLegacyAndCspHeaders() throws Exception {
        MockHttpServletResponse response = filter("/events");

        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("frame-ancestors 'none'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/events/event-id/exports",
        "/events/event-id/exports/",
        "/events/event-id/exports/export-id",
        "/events/event-id/exports/export-id/delivery/prepare",
        "/events/event-id/exports/export-id/delivery/execute",
        "/events/event-id/exports/export-id/delivery/reconciliation/prepare",
        "/events/event-id/exports/export-id/delivery/reconciliation/execute"
    })
    void usesSameOriginReferrerPolicyOnTheJ7ExportSubtree(String path)
            throws Exception {
        MockHttpServletResponse response = filter(path);

        assertThat(response.getHeader("Referrer-Policy"))
                .isEqualTo("same-origin");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/events/event-id/statistics",
        "/events/event-id/export",
        "/events/event-id/exports-adjacent",
        "/prefix/events/event-id/exports",
        "/j5-import-batches",
        "/benchmark",
        "/events/state",
        "/events/12345678-1234-1234-1234-123456789012/state",
        "/live-campaigns/12345678-1234-1234-1234-123456789012/state",
        "/live-campaigns-adjacent",
        "/prefix/live-campaigns/12345678-1234-1234-1234-123456789012"
    })
    void keepsNoReferrerOutsideJ7ExportsAndExplicitLivePages(String path) throws Exception {
        MockHttpServletResponse response = filter(path);

        assertThat(response.getHeader("Referrer-Policy"))
                .isEqualTo("no-referrer");
    }

    @Test
    void usesTheApplicationPathAfterRemovingANonEmptyContextPath()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/local-lab/events/event-id/exports/export-id");
        request.setContextPath("/local-lab");
        MockHttpServletResponse response = filter(request, new MockFilterChain());

        assertThat(response.getHeader("Referrer-Policy"))
                .isEqualTo("same-origin");
    }

    @Test
    void preservesADownstreamForbiddenStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/events/event-id/exports/export-id/delivery/prepare");
        FilterChain rejectingChain = (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(403);

        MockHttpServletResponse response = filter(request, rejectingChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Referrer-Policy"))
                .isEqualTo("same-origin");
    }

    private MockHttpServletResponse filter(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        return filter(request, new MockFilterChain());
    }

    private MockHttpServletResponse filter(
            MockHttpServletRequest request,
            FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
