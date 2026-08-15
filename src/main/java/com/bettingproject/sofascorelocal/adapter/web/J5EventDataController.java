package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J5EventDataQueryService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineImportException;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
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

import java.util.UUID;

@Controller
@RequestMapping("/events/{canonicalEventId}/statistics")
public class J5EventDataController {

    private final J5EventDataQueryService queryService;
    private final J5OfflineFixtureImportService fixtureImportService;
    private final LocalFormTokenService formTokenService;

    public J5EventDataController(
            J5EventDataQueryService queryService,
            J5OfflineFixtureImportService fixtureImportService,
            LocalFormTokenService formTokenService) {
        this.queryService = queryService;
        this.fixtureImportService = fixtureImportService;
        this.formTokenService = formTokenService;
    }

    @GetMapping
    public String view(
            @PathVariable UUID canonicalEventId,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        try {
            var page = queryService.find(canonicalEventId, zone).orElse(null);
            if (page == null) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("eventDataErrorCode", "EVENT_NOT_FOUND");
            }
            else {
                model.addAttribute("page", page);
                page.data().statistics().ifPresent(value -> {
                    model.addAttribute("statistics", value);
                    model.addAttribute("statisticsData", (EventStatistics) value.data());
                });
                page.data().incidents().ifPresent(value -> {
                    model.addAttribute("incidents", value);
                    model.addAttribute("incidentsData", (EventIncidents) value.data());
                });
                page.data().lineups().ifPresent(value -> {
                    model.addAttribute("lineups", value);
                    model.addAttribute("lineupsData", (EventLineups) value.data());
                });
                model.addAttribute("localFormToken", formTokenService.issue(session));
            }
        }
        catch (IllegalArgumentException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("eventDataErrorCode", "INVALID_ZONE");
        }
        catch (DataAccessException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("eventDataErrorCode", "LOCAL_DATABASE_UNAVAILABLE");
        }
        return "event-statistics";
    }

    @PostMapping("/offline-demo")
    public String importOfflineDemo(
            @PathVariable UUID canonicalEventId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            redirectAttributes.addFlashAttribute(
                    "j5FixtureImport",
                    fixtureImportService.importNominalCorpus(canonicalEventId));
        }
        catch (J5OfflineImportException exception) {
            redirectAttributes.addFlashAttribute(
                    "j5FixtureImportErrorCode",
                    exception.error().name());
        }
        redirectAttributes.addAttribute("zone", zone);
        return "redirect:/events/{canonicalEventId}/statistics";
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
}
