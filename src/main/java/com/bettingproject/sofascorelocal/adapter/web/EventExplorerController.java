package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventDetailResult;
import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportException;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J4ScheduledEventsSnapshotNormalizationService;
import com.bettingproject.sofascorelocal.application.event.J4SnapshotNormalizationException;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsPhase1Service;
import com.bettingproject.sofascorelocal.application.network.J4RealPhase1ControlException;
import com.bettingproject.sofascorelocal.application.network.J4RealPhase1ControlService;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Controller
@RequestMapping("/events")
public class EventExplorerController {

    private final J4EventQueryService queryService;
    private final J4OfflineFixtureImportService fixtureImportService;
    private final J4ScheduledEventsSnapshotNormalizationService normalizationService;
    private final J4RealPhase1ControlService realPhase1ControlService;
    private final J4RealEventDetailsPhase1Service realPhase1Service;
    private final LocalFormTokenService formTokenService;

    public EventExplorerController(
            J4EventQueryService queryService,
            J4OfflineFixtureImportService fixtureImportService,
            J4ScheduledEventsSnapshotNormalizationService normalizationService,
            J4RealPhase1ControlService realPhase1ControlService,
            J4RealEventDetailsPhase1Service realPhase1Service,
            LocalFormTokenService formTokenService) {
        this.queryService = queryService;
        this.fixtureImportService = fixtureImportService;
        this.normalizationService = normalizationService;
        this.realPhase1ControlService = realPhase1ControlService;
        this.realPhase1Service = realPhase1Service;
        this.formTokenService = formTokenService;
    }

    @GetMapping
    public String search(
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        LocalDate selectedDate = date == null
                ? LocalDate.now(ZoneId.of(J4EventQueryService.DEFAULT_ZONE_ID))
                : date;
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("selectedZone", zone);
        model.addAttribute("localFormToken", formTokenService.issue(session));
        model.addAttribute("realPhase1Control", realPhase1ControlService.snapshot());
        model.addAttribute(
                "realPhase1EventIds",
                EventDetailsProviderRequest.PHASE_1_EVENT_IDS);
        try {
            model.addAttribute("search", queryService.search(selectedDate, zone));
        }
        catch (IllegalArgumentException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("eventErrorCode", "INVALID_DATE_OR_ZONE");
        }
        catch (DataAccessException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("eventErrorCode", "LOCAL_DATABASE_UNAVAILABLE");
        }
        return "events";
    }

    @GetMapping("/{canonicalEventId}")
    public String detail(
            @PathVariable UUID canonicalEventId,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        try {
            J4EventDetailResult detail = queryService.findDetail(canonicalEventId, zone)
                    .orElse(null);
            if (detail == null) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("eventErrorCode", "EVENT_NOT_FOUND");
            }
            else {
                model.addAttribute("detail", detail);
                model.addAttribute(
                        "detailSearchDate",
                        detail.current().startsAtInZone().toLocalDate());
                model.addAttribute("offlineDetail", detail.offlineDetail().orElse(null));
            }
        }
        catch (IllegalArgumentException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("eventErrorCode", "INVALID_ZONE");
        }
        catch (DataAccessException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("eventErrorCode", "LOCAL_DATABASE_UNAVAILABLE");
        }
        return "event-detail";
    }

    @PostMapping("/offline-demo")
    public String importOfflineDemo(
            @RequestParam("localFormToken") String localFormToken,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            var result = fixtureImportService.importNominalCorpus();
            LocalDate eventDate = LocalDate.ofInstant(
                    result.startsAt(),
                    ZoneId.of(J4EventQueryService.DEFAULT_ZONE_ID));
            redirectAttributes.addFlashAttribute("fixtureImport", result);
            redirectAttributes.addAttribute("date", eventDate.toString());
            redirectAttributes.addAttribute("zone", J4EventQueryService.DEFAULT_ZONE_ID);
        }
        catch (J4OfflineFixtureImportException exception) {
            redirectAttributes.addFlashAttribute(
                    "fixtureImportErrorCode",
                    exception.error().name());
        }
        return "redirect:/events";
    }

    @PostMapping("/normalize-snapshot")
    public String normalizeSnapshot(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("snapshotId") long snapshotId,
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            queryService.resolveZone(zone);
            redirectAttributes.addFlashAttribute(
                    "snapshotNormalization",
                    normalizationService.normalize(snapshotId));
            LocalDate selectedDate = date == null
                    ? LocalDate.now(ZoneId.of(J4EventQueryService.DEFAULT_ZONE_ID))
                    : date;
            redirectAttributes.addAttribute("date", selectedDate.toString());
            redirectAttributes.addAttribute("zone", zone);
        }
        catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "snapshotNormalizationErrorCode",
                    "INVALID_DATE_OR_ZONE");
        }
        catch (J4SnapshotNormalizationException exception) {
            redirectAttributes.addFlashAttribute(
                    "snapshotNormalizationErrorCode",
                    exception.error().name());
        }
        return "redirect:/events";
    }

    @PostMapping("/real-phase1/prepare")
    public String prepareRealPhase1(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            queryService.resolveZone(zone);
            redirectAttributes.addFlashAttribute(
                    "realPhase1Prepared",
                    realPhase1ControlService.prepare());
        }
        catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase1ErrorCode", "INVALID_DATE_OR_ZONE");
        }
        catch (J4RealPhase1ControlException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase1ErrorCode", exception.error().name());
        }
        addSearchRedirectAttributes(date, zone, redirectAttributes);
        return "redirect:/events";
    }

    @PostMapping("/real-phase1/execute")
    public String executeRealPhase1(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("requestId") UUID requestId,
            @RequestParam("confirmationText") String confirmationText,
            @RequestParam(name = "acknowledged", defaultValue = "false")
                    boolean acknowledged,
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            queryService.resolveZone(zone);
            var claim = realPhase1ControlService.confirmAndClaim(
                    requestId,
                    confirmationText,
                    acknowledged);
            var result = realPhase1Service.execute(claim);
            redirectAttributes.addFlashAttribute("realPhase1Result", result);
            if (!result.completed()) {
                redirectAttributes.addFlashAttribute(
                        "realPhase1ErrorCode", result.terminalCode());
            }
        }
        catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase1ErrorCode", "INVALID_DATE_OR_ZONE");
        }
        catch (J4RealPhase1ControlException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase1ErrorCode", exception.error().name());
        }
        addSearchRedirectAttributes(date, zone, redirectAttributes);
        return "redirect:/events";
    }

    @PostMapping("/real-phase1/stop")
    public String stopRealPhase1(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        realPhase1ControlService.stop();
        redirectAttributes.addFlashAttribute("realPhase1Stopped", true);
        addSearchRedirectAttributes(date, zone, redirectAttributes);
        return "redirect:/events";
    }

    private static void addSearchRedirectAttributes(
            LocalDate date,
            String zone,
            RedirectAttributes redirectAttributes) {
        LocalDate selectedDate = date == null
                ? LocalDate.now(ZoneId.of(J4EventQueryService.DEFAULT_ZONE_ID))
                : date;
        redirectAttributes.addAttribute("date", selectedDate.toString());
        redirectAttributes.addAttribute("zone", zone);
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
}
