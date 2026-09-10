package com.bettingproject.sofascorelocal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final Pattern J7_EXPORT_PATH = Pattern.compile(
            "^/events/[^/]+/exports(?:/.*)?$");
    private static final Pattern J5_OFFLINE_BATCH_PATH = Pattern.compile(
            "^/j5-import-batches(?:;[^/]*)?(?:/.*)?$");
    private static final Pattern J8_BENCHMARK_PATH = Pattern.compile(
            "^/benchmark(?:;[^/]*)?/?$");
    private static final Pattern LIVE_PAGE_PATH = Pattern.compile(
            "^/(?:live-campaigns(?:/[^/;]+)?|events(?:/[0-9a-fA-F-]{36})?)(?:;[^/]*)?/?$");
    /** A local form page needs its exact loopback origin preserved for Chromium navigation POSTs. */
    private static final Pattern PROVIDER_ACCESS_PAGE_PATH = Pattern.compile(
            "^/provider-access(?:;[^/]*)?/?$");
    private static final Pattern LIVE_STATE_PATH = Pattern.compile(
            "^/(?:live-campaigns(?:/.*)?|events/(?:[0-9a-fA-F-]{36}/)?state)(?:;[^/]*)?/?$");
    private static final String STRICT_CACHE_CONTROL =
            "no-store, no-cache, must-revalidate, max-age=0";
    private static final String DEFAULT_REFERRER_POLICY = "no-referrer";
    private static final String LOCAL_FORM_REFERRER_POLICY = "same-origin";
    private static final String CONTENT_SECURITY_POLICY_PREFIX =
            "default-src 'self'; style-src 'self'; img-src 'self'; ";
    private static final String CONTENT_SECURITY_POLICY_SUFFIX =
            "; frame-src 'none'; frame-ancestors 'none'; object-src 'none'; "
                    + "base-uri 'none'; form-action 'self'";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader(
                "Referrer-Policy",
                // Chromium sends Origin: null for navigation POSTs under no-referrer.
                // Preserve an exact origin for local forms without disclosing cross-origin referrers.
                isJ7ExportRequest(request) || isLocalFormPageRequest(request)
                        ? LOCAL_FORM_REFERRER_POLICY
                        : DEFAULT_REFERRER_POLICY);
        response.setHeader(
                "Content-Security-Policy",
                CONTENT_SECURITY_POLICY_PREFIX
                        + (isJ5OfflineBatchRequest(request) || isLivePageRequest(request)
                                ? "script-src 'self'"
                                : "script-src 'none'")
                        + CONTENT_SECURITY_POLICY_SUFFIX);
        if (requiresStrictLocalHeaders(request)) {
            response.setHeader("Cache-Control", STRICT_CACHE_CONTROL);
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            response.setHeader(
                    "X-Robots-Tag", "noindex, nofollow, noarchive");
        }
        filterChain.doFilter(request, response);
    }

    private static boolean requiresStrictLocalHeaders(HttpServletRequest request) {
        String applicationPath = applicationPath(request);
        return J7_EXPORT_PATH.matcher(applicationPath).matches()
                || J5_OFFLINE_BATCH_PATH.matcher(applicationPath).matches()
                || J8_BENCHMARK_PATH.matcher(applicationPath).matches()
                || LIVE_PAGE_PATH.matcher(applicationPath).matches()
                || PROVIDER_ACCESS_PAGE_PATH.matcher(applicationPath).matches()
                || LIVE_STATE_PATH.matcher(applicationPath).matches();
    }

    private static boolean isLocalFormPageRequest(HttpServletRequest request) {
        String applicationPath = applicationPath(request);
        return LIVE_PAGE_PATH.matcher(applicationPath).matches()
                || PROVIDER_ACCESS_PAGE_PATH.matcher(applicationPath).matches();
    }

    private static boolean isLivePageRequest(HttpServletRequest request) {
        return LIVE_PAGE_PATH.matcher(applicationPath(request)).matches();
    }

    private static boolean isJ7ExportRequest(HttpServletRequest request) {
        return J7_EXPORT_PATH.matcher(applicationPath(request)).matches();
    }

    private static boolean isJ5OfflineBatchRequest(HttpServletRequest request) {
        return J5_OFFLINE_BATCH_PATH.matcher(applicationPath(request)).matches();
    }

    private static String applicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return requestUri.substring(contextPath.length());
    }
}
