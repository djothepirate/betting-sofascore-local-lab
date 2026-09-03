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
    void keepsAllScriptsDisabledOutsideTheOfflineBatchRoute() throws Exception {
        MockHttpServletResponse response = filter("/events");

        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'none'")
                .doesNotContain("script-src 'self'");
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
        "/events",
        "/events/event-id/statistics",
        "/events/event-id/export",
        "/events/event-id/exports-adjacent",
        "/prefix/events/event-id/exports",
        "/j5-import-batches",
        "/benchmark"
    })
    void keepsNoReferrerOutsideTheJ7ExportSubtree(String path) throws Exception {
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
