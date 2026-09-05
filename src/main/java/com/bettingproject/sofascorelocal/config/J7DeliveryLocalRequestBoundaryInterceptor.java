package com.bettingproject.sofascorelocal.config;

import com.bettingproject.sofascorelocal.adapter.web.J7DeliveryController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;

/** Enforces the exact loopback browser boundary on resolved J7 delivery handlers. */
public final class J7DeliveryLocalRequestBoundaryInterceptor implements HandlerInterceptor {

    private static final String LOCAL_HOST = "127.0.0.1:8087";
    private static final String LOCAL_ORIGIN = "http://127.0.0.1:8087";
    private static final String STRICT_CACHE_CONTROL =
            "no-store, no-cache, must-revalidate, max-age=0";

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if (!isJ7DeliveryHandler(handler)) {
            return true;
        }
        if (hasTrustedLocalRequestBoundary(request)) {
            return true;
        }
        response.setHeader("Cache-Control", STRICT_CACHE_CONTROL);
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentLength(0);
        return false;
    }

    private static boolean isJ7DeliveryHandler(Object handler) {
        return handler instanceof HandlerMethod handlerMethod
                && J7DeliveryController.class.isAssignableFrom(
                        handlerMethod.getBeanType());
    }

    private static boolean hasTrustedLocalRequestBoundary(HttpServletRequest request) {
        return hasExactlyOneHeaderValue(request, "Host", LOCAL_HOST)
                && hasAbsentOrExactlyOneHeaderValue(request, "Origin", LOCAL_ORIGIN)
                && !hasAnyHeaderValue(request, "Forwarded")
                && !hasAnyHeaderValue(request, "X-Forwarded-Host")
                && !hasAnyHeaderValue(request, "X-Forwarded-Proto");
    }

    private static boolean hasExactlyOneHeaderValue(
            HttpServletRequest request,
            String headerName,
            String expectedValue) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null || !values.hasMoreElements()) {
            return false;
        }
        String first = values.nextElement();
        return expectedValue.equals(first) && !values.hasMoreElements();
    }

    private static boolean hasAbsentOrExactlyOneHeaderValue(
            HttpServletRequest request,
            String headerName,
            String expectedValue) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null || !values.hasMoreElements()) {
            return true;
        }
        String first = values.nextElement();
        return expectedValue.equals(first) && !values.hasMoreElements();
    }

    private static boolean hasAnyHeaderValue(
            HttpServletRequest request,
            String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        return values != null && values.hasMoreElements();
    }
}
