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
    private static final String J7_CACHE_CONTROL =
            "no-store, no-cache, must-revalidate, max-age=0";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; style-src 'self'; img-src 'self'; "
                        + "script-src 'none'; frame-src 'none'; object-src 'none'; "
                        + "base-uri 'none'; form-action 'self'");
        if (isJ7ExportPath(request)) {
            response.setHeader("Cache-Control", J7_CACHE_CONTROL);
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            response.setHeader(
                    "X-Robots-Tag", "noindex, nofollow, noarchive");
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isJ7ExportPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String applicationPath = requestUri.substring(contextPath.length());
        return J7_EXPORT_PATH.matcher(applicationPath).matches();
    }
}
