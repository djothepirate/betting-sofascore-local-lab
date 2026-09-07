package com.bettingproject.sofascorelocal.config;

import com.bettingproject.sofascorelocal.adapter.web.LiveCampaignController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;
import java.util.Set;

/** Resolves the handler before enforcing the local live control and observation boundary. */
public final class LiveCampaignLocalRequestBoundaryInterceptor implements HandlerInterceptor {
    private static final Set<String> HOSTS = Set.of("localhost:8087", "127.0.0.1:8087");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)
                || !LiveCampaignController.class.isAssignableFrom(method.getBeanType())) {
            return true;
        }
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        String host = singleHeader(request, "Host");
        boolean trusted = host != null && HOSTS.contains(host)
                && absentOrEqual(request, "Origin", "http://" + host)
                && absentOrMember(request, "Sec-Fetch-Site", Set.of("same-origin", "none"))
                && absentOrMember(request, "Sec-Fetch-Dest", Set.of("document", "empty"))
                && request.getHeader("Forwarded") == null
                && request.getHeader("X-Forwarded-Host") == null
                && request.getHeader("X-Forwarded-Proto") == null
                && request.getHeader("X-Forwarded-Port") == null
                && request.getHeader("X-Forwarded-For") == null
                && request.getHeader("X-Forwarded-Prefix") == null;
        if (trusted) return true;
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentLength(0);
        return false;
    }

    private static boolean absentOrEqual(HttpServletRequest request, String name, String expected) {
        return request.getHeader(name) == null || expected.equals(singleHeader(request, name));
    }

    private static boolean absentOrMember(HttpServletRequest request, String name, Set<String> values) {
        if (request.getHeader(name) == null) return true;
        String value = singleHeader(request, name);
        return value != null && values.contains(value);
    }

    private static String singleHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) return null;
        String value = values.nextElement();
        return values.hasMoreElements() ? null : value;
    }
}
