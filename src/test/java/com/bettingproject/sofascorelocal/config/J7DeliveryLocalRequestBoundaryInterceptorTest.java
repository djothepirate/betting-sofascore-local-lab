package com.bettingproject.sofascorelocal.config;

import com.bettingproject.sofascorelocal.adapter.web.J7DeliveryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class J7DeliveryLocalRequestBoundaryInterceptorTest {

    private static final String LOCAL_HOST = "127.0.0.1:8087";
    private static final String LOCAL_ORIGIN = "http://127.0.0.1:8087";

    private final J7DeliveryLocalRequestBoundaryInterceptor interceptor =
            new J7DeliveryLocalRequestBoundaryInterceptor();

    @Test
    void refusesAHostileHostForTheResolvedControllerRegardlessOfTheRawUri()
            throws Exception {
        MockHttpServletRequest request = request(
                "/untrusted/delivery%3Bjsessionid%3DBYPASS/prepare");
        request.addHeader("Host", "attacker.invalid:8087");
        request.addHeader("Origin", LOCAL_ORIGIN);

        assertForbidden(request, deliveryHandler());
    }

    @Test
    void refusesAMissingHostForTheResolvedDeliveryController() throws Exception {
        MockHttpServletRequest request = request("/path-is-not-consulted");
        request.addHeader("Origin", LOCAL_ORIGIN);

        assertForbidden(request, deliveryHandler());
    }

    @Test
    void refusesDuplicateHostValuesEvenWhenBothAreLocallyExact() throws Exception {
        MockHttpServletRequest request = localRequest();
        request.addHeader("Host", LOCAL_HOST);

        assertForbidden(request, deliveryHandler());
    }

    @Test
    void refusesAHostileOriginForTheResolvedDeliveryController() throws Exception {
        MockHttpServletRequest request = request("/path-is-not-consulted");
        request.addHeader("Host", LOCAL_HOST);
        request.addHeader("Origin", "http://attacker.invalid:8087");

        assertForbidden(request, deliveryHandler());
    }

    @Test
    void refusesANullOpaqueOriginForTheResolvedDeliveryController()
            throws Exception {
        MockHttpServletRequest request = request("/path-is-not-consulted");
        request.addHeader("Host", LOCAL_HOST);
        request.addHeader("Origin", "null");

        assertForbidden(request, deliveryHandler());
    }

    @Test
    void refusesDuplicateOriginValuesEvenWhenBothAreLocallyExact() throws Exception {
        MockHttpServletRequest request = localRequest();
        request.addHeader("Origin", LOCAL_ORIGIN);

        assertForbidden(request, deliveryHandler());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Forwarded", "X-Forwarded-Host", "X-Forwarded-Proto"})
    void refusesEveryProxyForwardingHeaderForTheResolvedDeliveryController(
            String forwardedHeader) throws Exception {
        MockHttpServletRequest request = localRequest();
        request.addHeader(forwardedHeader, "untrusted");

        assertForbidden(request, deliveryHandler());
    }

    @Test
    void acceptsTheExactLocalHostAndOriginForTheResolvedDeliveryController()
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(
                localRequest(), response, deliveryHandler())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void acceptsAnAbsentOriginWhenTheHostIsExactlyLocal() throws Exception {
        MockHttpServletRequest request = request("/path-is-not-consulted");
        request.addHeader("Host", LOCAL_HOST);

        assertThat(interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                deliveryHandler())).isTrue();
    }

    @Test
    void doesNotProtectAnUnrelatedResolvedHandlerEvenForAHostilePost()
            throws Exception {
        MockHttpServletRequest request = request("/unrelated-post");
        request.addHeader("Host", "attacker.invalid:8087");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(
                request, response, unrelatedHandler())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void assertForbidden(
            MockHttpServletRequest request,
            HandlerMethod handler) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store, no-cache, must-revalidate, max-age=0");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    private static MockHttpServletRequest localRequest() {
        MockHttpServletRequest request = request("/path-is-not-consulted");
        request.addHeader("Host", LOCAL_HOST);
        request.addHeader("Origin", LOCAL_ORIGIN);
        return request;
    }

    private static MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("POST", uri);
    }

    private static HandlerMethod deliveryHandler() {
        return new HandlerMethod(
                mock(J7DeliveryController.class),
                declaredMethod(J7DeliveryController.class, "prepare"));
    }

    private static HandlerMethod unrelatedHandler() {
        return new HandlerMethod(
                new UnrelatedController(),
                declaredMethod(UnrelatedController.class, "post"));
    }

    private static Method declaredMethod(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static final class UnrelatedController {

        void post() {
            // Type identity alone is used by the interceptor.
        }
    }
}
