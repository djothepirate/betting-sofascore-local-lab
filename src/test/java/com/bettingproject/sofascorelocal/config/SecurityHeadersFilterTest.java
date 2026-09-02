package com.bettingproject.sofascorelocal.config;

import org.junit.jupiter.api.Test;
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

    private MockHttpServletResponse filter(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
