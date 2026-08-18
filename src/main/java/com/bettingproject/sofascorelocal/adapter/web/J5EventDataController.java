package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J5EventDataQueryService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineImportException;
import com.bettingproject.sofascorelocal.application.network.J5RealControlException;
import com.bettingproject.sofascorelocal.application.network.J5RealControlService;
import com.bettingproject.sofascorelocal.application.network.J5RealEventDataService;
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
    private final J5RealControlService realControlService;
    private final J5RealEventDataService realEventDataService;
    private final LocalFormTokenService formTokenService;

    public J5EventDataController(
            J5EventDataQueryService queryService,
            J5OfflineFixtureImportService fixtureImportService,
            J5RealControlService realControlService,
            J5RealEventDataService realEventDataService,
            LocalFormTokenService formTokenService) {
        this.queryService = queryService;
        this.fixtureImportService = fixtureImportService;
        this.realControlService = realControlService;
        this.realEventDataService = realEventDataService;
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
                model.addAttribute("j5RealControl", realControlService.snapshot());
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

    @PostMapping("/real/prepare")
    public String prepareRealCampaign(
            @PathVariable UUID canonicalEventId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("eventId") long eventId,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            realControlService.prepare(canonicalEventId, eventId);
            redirectAttributes.addFlashAttribute(
                    "j5RealMessage",
                    "Campagne préparée sans transport. Recopiez exactement la phrase affichée pour autoriser les trois appels ordonnés.");
            redirectAttributes.addFlashAttribute("j5RealMessageKind", "safe");
        }
        catch (J5RealControlException exception) {
            addRealError(redirectAttributes, exception.error().name());
        }
        redirectAttributes.addAttribute("zone", zone);
        return "redirect:/events/{canonicalEventId}/statistics";
    }

    @PostMapping("/real/execute")
    public String executeRealCampaign(
            @PathVariable UUID canonicalEventId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("requestId") UUID requestId,
            @RequestParam("confirmationText") String confirmationText,
            @RequestParam(name = "acknowledged", defaultValue = "false") boolean acknowledged,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            var claim = realControlService.confirmAndClaim(
                    requestId, confirmationText, acknowledged);
            if (!claim.canonicalEventId().equals(canonicalEventId)) {
                realControlService.fail(requestId, "EVENT_ID_MISMATCH");
                addRealError(redirectAttributes, "EVENT_ID_MISMATCH");
            }
            else {
                var result = realEventDataService.execute(claim);
                redirectAttributes.addFlashAttribute("j5RealResult", result);
                if (result.completed()) {
                    redirectAttributes.addFlashAttribute(
                            "j5RealMessage",
                            "Campagne J5 terminée : trois appels fournisseur ordonnés, trois snapshots bruts et trois observations locales, y compris toute indisponibilité explicite. Le circuit est reverrouillé ; une nouvelle préparation explicite créera une campagne et une confirmation distinctes.");
                    redirectAttributes.addFlashAttribute("j5RealMessageKind", "safe");
                }
                else {
                    addRealError(redirectAttributes, result.terminalCode());
                }
            }
        }
        catch (J5RealControlException exception) {
            addRealError(redirectAttributes, exception.error().name());
        }
        catch (RuntimeException exception) {
            if (realControlService.executionMayContinue(requestId)) {
                realControlService.fail(requestId, "LOCAL_EXECUTION_FAILURE");
            }
            addRealError(redirectAttributes, "LOCAL_EXECUTION_FAILURE");
        }
        redirectAttributes.addAttribute("zone", zone);
        return "redirect:/events/{canonicalEventId}/statistics";
    }

    @PostMapping("/real/stop")
    public String stopRealCampaign(
            @PathVariable UUID canonicalEventId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        realControlService.stop();
        redirectAttributes.addFlashAttribute(
                "j5RealMessage",
                "Arrêt global J5 appliqué. Aucun nouvel appel n’est autorisé avant redémarrage.");
        redirectAttributes.addFlashAttribute("j5RealMessageKind", "danger");
        redirectAttributes.addAttribute("zone", zone);
        return "redirect:/events/{canonicalEventId}/statistics";
    }

    private static void addRealError(
            RedirectAttributes redirectAttributes,
            String code) {
        redirectAttributes.addFlashAttribute(
                "j5RealMessage", "Campagne J5 arrêtée sans retry : " + code);
        redirectAttributes.addFlashAttribute("j5RealMessageKind", "danger");
        redirectAttributes.addFlashAttribute("j5RealErrorCode", code);
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
