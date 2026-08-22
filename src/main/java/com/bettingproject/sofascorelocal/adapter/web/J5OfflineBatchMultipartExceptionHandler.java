package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchControlService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchError;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchPlan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Objects;

@ControllerAdvice
public class J5OfflineBatchMultipartExceptionHandler {

    private static final String EXECUTE_PATH = "/j5-import-batches/execute";
    private static final String REDIRECT_VIEW = "redirect:/j5-import-batches";

    private final ObjectProvider<J5OfflineBatchControlService> controlServiceProvider;

    public J5OfflineBatchMultipartExceptionHandler(
            ObjectProvider<J5OfflineBatchControlService> controlServiceProvider) {
        this.controlServiceProvider = Objects.requireNonNull(
                controlServiceProvider, "controlServiceProvider");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView multipartLimitExceeded(
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!isOfflineBatchExecution(request)) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return null;
        }

        applyStrictLocalHeaders(response);
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        if (flashMap != null) {
            flashMap.put(
                    "batchErrorCode",
                    J5OfflineBatchError.MULTIPART_LIMIT_EXCEEDED.name());
            flashMap.put(
                    "batchMessage",
                    "Le dépôt dépasse une limite multipart. Vérifiez 5 Mio maximum par fichier "
                            + "et 25 Mio maximum pour le lot.");
            flashMap.put("batchMessageKind", "danger");
        }
        ModelAndView redirect = new ModelAndView(REDIRECT_VIEW);
        J5OfflineBatchControlService controlService = controlServiceProvider.getIfAvailable();
        J5OfflineBatchPlan plan = controlService == null ? null : controlService.snapshot().plan();
        if (plan != null) {
            redirect.addObject("date", plan.date());
            redirect.addObject("zone", plan.zoneId().getId());
        }
        return redirect;
    }

    private static boolean isOfflineBatchExecution(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return requestUri != null
                && contextPath != null
                && requestUri.startsWith(contextPath)
                && requestUri.substring(contextPath.length()).equals(EXECUTE_PATH);
    }

    private static void applyStrictLocalHeaders(HttpServletResponse response) {
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
}
