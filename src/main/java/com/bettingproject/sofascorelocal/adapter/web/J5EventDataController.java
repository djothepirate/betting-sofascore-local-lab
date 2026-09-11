package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J5EventDataQueryService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineFixtureImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineImportException;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportError;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportException;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportService;
import com.bettingproject.sofascorelocal.application.network.J5LocalUnavailableEvidence;
import com.bettingproject.sofascorelocal.application.network.J5ProviderCampaignStopException;
import com.bettingproject.sofascorelocal.application.network.J5ProviderCampaignStopService;
import com.bettingproject.sofascorelocal.application.network.J5RealControlException;
import com.bettingproject.sofascorelocal.application.network.J5RealControlService;
import com.bettingproject.sofascorelocal.application.network.J5RealEventDataService;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.UUID;

@Controller
@RequestMapping("/events/{canonicalEventId}/statistics")
public class J5EventDataController {

    private final J5EventDataQueryService queryService;
    private final J5OfflineFixtureImportService fixtureImportService;
    private final J5RealControlService realControlService;
    private final J5RealEventDataService realEventDataService;
    private final J5LocalJsonImportService localJsonImportService;
    private final J5ProviderCampaignStopService providerCampaignStopService;
    private final LocalFormTokenService formTokenService;
    private final LineupCountryOverlayResolver lineupCountries;

    public J5EventDataController(
            J5EventDataQueryService queryService,
            J5OfflineFixtureImportService fixtureImportService,
            J5RealControlService realControlService,
            J5RealEventDataService realEventDataService,
            J5LocalJsonImportService localJsonImportService,
            J5ProviderCampaignStopService providerCampaignStopService,
            LocalFormTokenService formTokenService,
            LineupCountryOverlayResolver lineupCountries) {
        this.queryService = queryService;
        this.fixtureImportService = fixtureImportService;
        this.realControlService = realControlService;
        this.realEventDataService = realEventDataService;
        this.localJsonImportService = localJsonImportService;
        this.providerCampaignStopService = providerCampaignStopService;
        this.formTokenService = formTokenService;
        this.lineupCountries = lineupCountries == null ? LineupCountryOverlayResolver.none() : lineupCountries;
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
                    model.addAttribute("statisticsView", StatisticsPresentation.from((EventStatistics) value.data()));
                });
                page.data().incidents().ifPresent(value -> {
                    model.addAttribute("incidents", value);
                    model.addAttribute("incidentsData", (EventIncidents) value.data());
                    model.addAttribute("incidentMotifs", ((EventIncidents) value.data()).incidents().stream()
                            .map(IncidentPresentation::motifLabel).toList());
                    if (value.completeness().status() != J5CompletenessStatus.UNAVAILABLE) {
                        model.addAttribute("incidentsView", IncidentPresentation.from((EventIncidents) value.data(),
                                page.current().event().homeTeam().name(), page.current().event().awayTeam().name()));
                    }
                });
                LineupIncidentOverlay incidentOverlay = page.data().statistics()
                        .filter(value -> value.completeness().status() == J5CompletenessStatus.UNAVAILABLE)
                        .flatMap(ignored -> page.data().incidents())
                        .filter(value -> value.completeness().status() != J5CompletenessStatus.UNAVAILABLE)
                        .map(value -> LineupIncidentOverlay.from((EventIncidents) value.data()))
                        .orElse(LineupIncidentOverlay.empty());
                page.data().lineups().ifPresent(value -> {
                    model.addAttribute("lineups", value);
                    model.addAttribute("lineupsData", (EventLineups) value.data());
                    if (value.completeness().status() != J5CompletenessStatus.UNAVAILABLE) {
                        // J5EventDataPage carries a scheduled J4 observation, not the matching J4 detail
                        // projection that contains tournament.uniqueTournament.hasEventPlayerStatistics. Do not
                        // introduce an untraced latest-detail lookup merely to make a card interactive.
                        model.addAttribute("lineupsView", LineupsPresentation.from((EventLineups) value.data(),
                                page.current().event().homeTeam().name(), page.current().event().awayTeam().name(),
                                lineupCountries.resolve(value), incidentOverlay, false));
                    }
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
                    "Campagne préparée sans transport. Recopiez exactement la phrase affichée puis choisissez une seule voie : trois appels ordonnés ou l’import local de trois preuves JSON.");
            redirectAttributes.addFlashAttribute("j5RealMessageKind", "safe");
        }
        catch (J5RealControlException exception) {
            addRealError(redirectAttributes, exception.error().name());
        }
        redirectAttributes.addAttribute("zone", zone);
        return "redirect:/events/{canonicalEventId}/statistics";
    }

    @PostMapping(
            value = "/real/import-json",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String importRealCampaignJson(
            @PathVariable UUID canonicalEventId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("requestId") UUID requestId,
            @RequestParam("confirmationText") String confirmationText,
            @RequestParam(name = "acknowledged", defaultValue = "false") boolean acknowledged,
            @RequestParam(name = "statisticsFile", required = false)
                    MultipartFile statisticsFile,
            @RequestParam(name = "statisticsUnavailable404", defaultValue = "false")
                    boolean statisticsUnavailable404,
            @RequestParam(name = "incidentsFile", required = false)
                    MultipartFile incidentsFile,
            @RequestParam(name = "incidentsUnavailable404", defaultValue = "false")
                    boolean incidentsUnavailable404,
            @RequestParam(name = "lineupsFile", required = false)
                    MultipartFile lineupsFile,
            @RequestParam(name = "lineupsUnavailable404", defaultValue = "false")
                    boolean lineupsUnavailable404,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        RawPayloadEvidence statistics = readLocalEvidence(
                statisticsFile,
                statisticsUnavailable404,
                "statistiques",
                redirectAttributes);
        RawPayloadEvidence incidents = readLocalEvidence(
                incidentsFile,
                incidentsUnavailable404,
                "incidents",
                redirectAttributes);
        RawPayloadEvidence lineups = readLocalEvidence(
                lineupsFile,
                lineupsUnavailable404,
                "compositions",
                redirectAttributes);
        if (statistics != null && incidents != null && lineups != null) {
            try {
                var result = localJsonImportService.importCampaign(
                        canonicalEventId,
                        requestId,
                        confirmationText,
                        acknowledged,
                        statistics,
                        incidents,
                        lineups);
                redirectAttributes.addFlashAttribute("j5RealResult", result);
                if (result.completed()) {
                    int declarations = (statisticsUnavailable404 ? 1 : 0)
                            + (incidentsUnavailable404 ? 1 : 0)
                            + (lineupsUnavailable404 ? 1 : 0);
                    redirectAttributes.addFlashAttribute(
                            "j5RealMessage",
                            "Campagne J5 importée et validée : 0 appel fournisseur, trois preuves JSON locales dont "
                                    + declarations
                                    + " déclaration(s) 404, et trois observations ordonnées. Le circuit est reverrouillé.");
                    redirectAttributes.addFlashAttribute("j5RealMessageKind", "safe");
                }
                else {
                    addRealError(redirectAttributes, result.terminalCode());
                }
            }
            catch (J5LocalJsonImportException exception) {
                addLocalImportError(redirectAttributes, exception.error());
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
            var result = realEventDataService.execute(claim, canonicalEventId);
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
        try {
            providerCampaignStopService.stopAll();
            redirectAttributes.addFlashAttribute(
                    "j5RealMessage",
                    "Arrêt global J5 appliqué. Aucun nouvel appel n’est autorisé avant redémarrage.");
        }
        catch (J5ProviderCampaignStopException exception) {
            addRealError(redirectAttributes, "PLAYWRIGHT_STOP_UNCONFIRMED");
        }
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

    private static RawPayloadEvidence readLocalEvidence(
            MultipartFile file,
            boolean unavailable404,
            String familyLabel,
            RedirectAttributes redirectAttributes) {
        boolean fileSelected = file != null && !file.isEmpty();
        if (fileSelected && unavailable404) {
            addLocalImportError(
                    redirectAttributes,
                    "Choisissez pour les " + familyLabel
                            + " soit un fichier JSON, soit la déclaration 404, jamais les deux.");
            return null;
        }
        if (unavailable404) {
            return J5LocalUnavailableEvidence.declared404();
        }
        if (!fileSelected) {
            addLocalImportError(
                    redirectAttributes,
                    "Le fichier JSON des " + familyLabel
                            + " est obligatoire sauf si le 404 observé est explicitement déclaré.");
            return null;
        }
        if (file.getSize() > RawPayloadEvidence.MAXIMUM_BYTES) {
            addLocalImportError(
                    redirectAttributes,
                    "Le fichier JSON des " + familyLabel + " dépasse 5 Mio.");
            return null;
        }
        try {
            return RawPayloadEvidence.capture(file.getBytes());
        }
        catch (IOException exception) {
            addLocalImportError(
                    redirectAttributes,
                    "Le fichier JSON des " + familyLabel + " n’a pas pu être lu.");
            return null;
        }
        catch (IllegalArgumentException exception) {
            addLocalImportError(
                    redirectAttributes,
                    "Le fichier JSON des " + familyLabel
                            + " a été refusé : taille ou contenu sensible interdit.");
            return null;
        }
    }

    private static void addLocalImportError(
            RedirectAttributes redirectAttributes,
            J5LocalJsonImportError error) {
        String detail = switch (error) {
            case NO_PENDING_CAMPAIGN -> "aucune campagne en attente ne correspond à cet import";
            case REQUEST_ID_MISMATCH -> "l’identifiant de confirmation ne correspond pas";
            case CANONICAL_EVENT_MISMATCH -> "l’identité canonique ne correspond pas";
            case STATISTICS_PAYLOAD_INCOMPATIBLE -> "le corps des statistiques est incompatible";
            case INCIDENTS_PAYLOAD_INCOMPATIBLE -> "le corps des incidents est incompatible";
            case LINEUPS_PAYLOAD_INCOMPATIBLE -> "le corps des compositions est incompatible";
        };
        addLocalImportError(
                redirectAttributes,
                "Import J5 refusé avant consommation de la confirmation : " + detail + ".");
        redirectAttributes.addFlashAttribute("j5RealErrorCode", error.name());
    }

    private static void addLocalImportError(
            RedirectAttributes redirectAttributes,
            String message) {
        redirectAttributes.addFlashAttribute("j5RealMessage", message);
        redirectAttributes.addFlashAttribute("j5RealMessageKind", "danger");
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
