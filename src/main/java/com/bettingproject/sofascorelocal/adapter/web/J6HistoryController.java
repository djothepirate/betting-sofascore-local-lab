package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.history.J6HistoryQueryService;
import com.bettingproject.sofascorelocal.application.history.J6OfflineHistoryDemoException;
import com.bettingproject.sofascorelocal.application.history.J6OfflineHistoryDemoService;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryClassification;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryStream;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/events")
public class J6HistoryController {

    private final J6HistoryQueryService historyQueryService;
    private final J6OfflineHistoryDemoService offlineHistoryDemoService;
    private final J4EventQueryService eventQueryService;
    private final LocalFormTokenService formTokenService;

    public J6HistoryController(
            J6HistoryQueryService historyQueryService,
            J6OfflineHistoryDemoService offlineHistoryDemoService,
            J4EventQueryService eventQueryService,
            LocalFormTokenService formTokenService) {
        this.historyQueryService = historyQueryService;
        this.offlineHistoryDemoService = offlineHistoryDemoService;
        this.eventQueryService = eventQueryService;
        this.formTokenService = formTokenService;
    }

    @GetMapping("/{canonicalEventId}/history")
    public String history(
            @PathVariable UUID canonicalEventId,
            @RequestParam(name = "stream", required = false) String stream,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(
                    name = "size",
                    defaultValue = "25") int size,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        try {
            ZoneId selectedZone = eventQueryService.resolveZone(zone);
            Optional<J6HistoryStream> selectedStream = optionalStream(stream);
            var history = historyQueryService.findHistory(
                    canonicalEventId,
                    selectedStream,
                    page,
                    size).orElse(null);
            if (history == null) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("historyErrorCode", "EVENT_NOT_FOUND");
            }
            else {
                model.addAttribute("historyPage", history);
                model.addAttribute("selectedZone", selectedZone);
                model.addAttribute("historyStreams", J6HistoryStream.values());
                model.addAttribute(
                        "historyClassifications",
                        J6HistoryClassification.values());
                model.addAttribute("localFormToken", formTokenService.issue(session));
            }
        }
        catch (IllegalArgumentException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("historyErrorCode", "INVALID_HISTORY_QUERY");
        }
        catch (DataAccessException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("historyErrorCode", "LOCAL_DATABASE_UNAVAILABLE");
        }
        return "event-history";
    }

    @GetMapping("/{canonicalEventId}/history/compare")
    public String compare(
            @PathVariable UUID canonicalEventId,
            @RequestParam("stream") String stream,
            @RequestParam("fromObservationId") long fromObservationId,
            @RequestParam("toObservationId") long toObservationId,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        try {
            ZoneId selectedZone = eventQueryService.resolveZone(zone);
            J6HistoryStream selectedStream = J6HistoryStream.parse(stream);
            var comparison = historyQueryService.compare(
                    canonicalEventId,
                    selectedStream,
                    fromObservationId,
                    toObservationId).orElse(null);
            if (comparison == null) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("comparisonErrorCode", "VERSION_NOT_FOUND");
            }
            else {
                model.addAttribute("canonicalEventId", canonicalEventId);
                model.addAttribute("selectedZone", selectedZone);
                model.addAttribute("comparison", comparison);
            }
        }
        catch (IllegalArgumentException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("comparisonErrorCode", "INVALID_COMPARISON");
        }
        catch (DataAccessException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("comparisonErrorCode", "LOCAL_DATABASE_UNAVAILABLE");
        }
        return "event-history-compare";
    }

    @PostMapping("/history/offline-demo")
    public String importOfflineDemo(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            eventQueryService.resolveZone(zone);
            var result = offlineHistoryDemoService.importCorpus();
            redirectAttributes.addFlashAttribute("j6HistoryImport", result);
            redirectAttributes.addAttribute("zone", zone);
            return "redirect:/events/" + result.canonicalEventId() + "/history";
        }
        catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "j6HistoryImportErrorCode",
                    "INVALID_ZONE");
        }
        catch (J6OfflineHistoryDemoException exception) {
            redirectAttributes.addFlashAttribute(
                    "j6HistoryImportErrorCode",
                    exception.error().name());
        }
        catch (DataAccessException exception) {
            redirectAttributes.addFlashAttribute(
                    "j6HistoryImportErrorCode",
                    "LOCAL_DATABASE_UNAVAILABLE");
        }
        redirectAttributes.addAttribute("zone", J4EventQueryService.DEFAULT_ZONE_ID);
        return "redirect:/events";
    }

    private static Optional<J6HistoryStream> optionalStream(String stream) {
        return stream == null || stream.isBlank()
                ? Optional.empty()
                : Optional.of(J6HistoryStream.parse(stream));
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
}
