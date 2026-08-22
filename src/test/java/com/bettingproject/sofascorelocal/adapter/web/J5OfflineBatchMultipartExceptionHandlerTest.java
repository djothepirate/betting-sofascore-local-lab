package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchControlService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchControlSnapshot;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class J5OfflineBatchMultipartExceptionHandlerTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<J5OfflineBatchControlService> controlServiceProvider =
            mock(ObjectProvider.class);
    private final J5OfflineBatchControlService controlService =
            mock(J5OfflineBatchControlService.class);
    private J5OfflineBatchMultipartExceptionHandler handler;

    @BeforeEach
    void setUp() {
        when(controlServiceProvider.getIfAvailable()).thenReturn(controlService);
        handler = new J5OfflineBatchMultipartExceptionHandler(controlServiceProvider);
    }

    @Test
    void redirectsAnOversizedOfflineBatchWithOnlyBoundedFlashMetadata() {
        MockHttpServletRequest request = request("/j5-import-batches/execute");
        MockHttpServletResponse response = new MockHttpServletResponse();
        J5OfflineBatchControlSnapshot snapshot = mock(J5OfflineBatchControlSnapshot.class);
        J5OfflineBatchPlan plan = mock(J5OfflineBatchPlan.class);
        when(controlService.snapshot()).thenReturn(snapshot);
        when(snapshot.plan()).thenReturn(plan);
        when(plan.date()).thenReturn(LocalDate.of(2026, 10, 25));
        when(plan.zoneId()).thenReturn(ZoneId.of("America/New_York"));

        ModelAndView view = handler.multipartLimitExceeded(request, response);

        assertThat(view.getViewName()).isEqualTo("redirect:/j5-import-batches");
        assertThat(view.getModel().get("date")).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(view.getModel().get("zone")).isEqualTo("America/New_York");
        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store, no-cache, must-revalidate, max-age=0");
        assertThat(response.getHeader("X-Robots-Tag"))
                .isEqualTo("noindex, nofollow, noarchive");
        FlashMap flashMap = flashMap(request);
        assertThat(flashMap.size()).isEqualTo(3);
        assertThat(flashMap.get("batchErrorCode"))
                .isEqualTo("MULTIPART_LIMIT_EXCEEDED");
        assertThat(flashMap.get("batchMessage"))
                .isEqualTo("Le dépôt dépasse une limite multipart. Vérifiez 5 Mio maximum "
                        + "par fichier et 25 Mio maximum pour le lot.");
        assertThat(flashMap.get("batchMessageKind")).isEqualTo("danger");
    }

    @Test
    void returnsOnlyGenericPayloadTooLargeOutsideTheOfflineBatchRoute() {
        MockHttpServletRequest request = request("/manual-call/import-json-pages");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView view = handler.multipartLimitExceeded(request, response);

        assertThat(view).isNull();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(flashMap(request).size()).isZero();
        verifyNoInteractions(controlService);
    }

    private static MockHttpServletRequest request(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(requestUri);
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        return request;
    }

    private static FlashMap flashMap(MockHttpServletRequest request) {
        return (FlashMap) request.getAttribute(
                DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
    }
}
