package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventDetailResult;
import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportException;
import com.bettingproject.sofascorelocal.application.event.J4OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J4ScheduledEventsSnapshotNormalizationService;
import com.bettingproject.sofascorelocal.application.event.J4SnapshotNormalizationException;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsPhase1Service;
import com.bettingproject.sofascorelocal.application.network.J4RealEventDetailsPhase2Service;
import com.bettingproject.sofascorelocal.application.network.J4ProviderCampaignStopException;
import com.bettingproject.sofascorelocal.application.network.J4ProviderCampaignStopService;
import com.bettingproject.sofascorelocal.application.network.J4RealPhase1ControlException;
import com.bettingproject.sofascorelocal.application.network.J4RealPhase1ControlService;
import com.bettingproject.sofascorelocal.application.network.J4RealPhase2ControlException;
import com.bettingproject.sofascorelocal.application.network.J4RealPhase2ControlService;
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
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/events")
public class EventExplorerController {

    private final J4EventQueryService queryService;
    private final J4OfflineFixtureImportService fixtureImportService;
    private final J4ScheduledEventsSnapshotNormalizationService normalizationService;
    private final J4RealPhase1ControlService realPhase1ControlService;
    private final J4RealEventDetailsPhase1Service realPhase1Service;
    private final J4RealPhase2ControlService realPhase2ControlService;
    private final J4RealEventDetailsPhase2Service realPhase2Service;
    private final J4ProviderCampaignStopService providerCampaignStopService;
    private final LocalFormTokenService formTokenService;
    private final LiveCampaignService liveCampaigns;

    public EventExplorerController(
            J4EventQueryService queryService,
            J4OfflineFixtureImportService fixtureImportService,
            J4ScheduledEventsSnapshotNormalizationService normalizationService,
            J4RealPhase1ControlService realPhase1ControlService,
            J4RealEventDetailsPhase1Service realPhase1Service,
            J4RealPhase2ControlService realPhase2ControlService,
            J4RealEventDetailsPhase2Service realPhase2Service,
            J4ProviderCampaignStopService providerCampaignStopService,
            LocalFormTokenService formTokenService,
            LiveCampaignService liveCampaigns) {
        this.queryService = queryService;
        this.fixtureImportService = fixtureImportService;
        this.normalizationService = normalizationService;
        this.realPhase1ControlService = realPhase1ControlService;
        this.realPhase1Service = realPhase1Service;
        this.realPhase2ControlService = realPhase2ControlService;
        this.realPhase2Service = realPhase2Service;
        this.providerCampaignStopService = providerCampaignStopService;
        this.formTokenService = formTokenService;
        this.liveCampaigns = liveCampaigns;
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
        model.addAttribute("realPhase2Control", realPhase2ControlService.snapshot());
        model.addAttribute(
                "realPhase1EventIds",
                EventDetailsProviderRequest.PHASE_1_EVENT_IDS);
        model.addAttribute("realPhase2CanonicalSelections", List.of());
        model.addAttribute("liveSelectionMaximum", liveCampaigns.selectionMaximum());
        try {
            var search = queryService.search(selectedDate, zone);
            model.addAttribute("blockedLiveEventIds", liveCampaigns.selectionBlockedEvents(search.events().stream()
                    .map(item -> item.event().identity().value()).toList()));
            model.addAttribute("search", search);
            model.addAttribute("realPhase2CanonicalSelections", search.events());
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
                model.addAttribute("eventInformation", detail.offlineDetail()
                        .map(observation -> EventDetailsPresentation.from(observation.details())).orElse(null));
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

    @PostMapping("/real-phase2/prepare")
    public String prepareRealPhase2(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("canonicalEventId") UUID canonicalEventId,
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            queryService.resolveZone(zone);
            LocalDate selectedDate = date == null
                    ? LocalDate.now(ZoneId.of(J4EventQueryService.DEFAULT_ZONE_ID))
                    : date;
            var identity = queryService.findSofascoreIdentityInSelection(
                    canonicalEventId, selectedDate, zone).orElse(null);
            if (identity == null) {
                redirectAttributes.addFlashAttribute(
                        "realPhase2ErrorCode", "CANONICAL_EVENT_NOT_IN_SELECTION");
            }
            else {
                redirectAttributes.addFlashAttribute(
                        "realPhase2Prepared",
                        realPhase2ControlService.prepare(identity));
            }
        }
        catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase2ErrorCode", "INVALID_DATE_OR_ZONE");
        }
        catch (DataAccessException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase2ErrorCode", "LOCAL_DATABASE_UNAVAILABLE");
        }
        catch (J4RealPhase2ControlException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase2ErrorCode", exception.error().name());
        }
        addSearchRedirectAttributes(date, zone, redirectAttributes);
        return "redirect:/events";
    }

    @PostMapping("/real-phase2/execute")
    public String executeRealPhase2(
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
            var claim = realPhase2ControlService.confirmAndClaim(
                    requestId,
                    confirmationText,
                    acknowledged);
            var result = realPhase2Service.execute(claim);
            redirectAttributes.addFlashAttribute("realPhase2Result", result);
            if (!result.completed()) {
                redirectAttributes.addFlashAttribute(
                        "realPhase2ErrorCode", result.terminalCode());
            }
        }
        catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase2ErrorCode", "INVALID_DATE_OR_ZONE");
        }
        catch (J4RealPhase2ControlException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase2ErrorCode", exception.error().name());
        }
        addSearchRedirectAttributes(date, zone, redirectAttributes);
        return "redirect:/events";
    }

    @PostMapping({"/real/stop", "/real-phase1/stop"})
    public String stopRealJ4(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            providerCampaignStopService.stopAll();
            redirectAttributes.addFlashAttribute("realPhase1Stopped", true);
        }
        catch (J4ProviderCampaignStopException exception) {
            redirectAttributes.addFlashAttribute(
                    "realPhase1ErrorCode", "PLAYWRIGHT_STOP_UNCONFIRMED");
        }
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
