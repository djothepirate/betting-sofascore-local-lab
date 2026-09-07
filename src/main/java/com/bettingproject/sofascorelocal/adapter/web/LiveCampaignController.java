package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** POSTs control the manually launched session. All GETs only read local persisted evidence. */
@Controller
public class LiveCampaignController {
    private final LiveCampaignService campaigns;
    private final LiveCampaignPresentation presentation;
    private final LocalFormTokenService tokens;

    public LiveCampaignController(LiveCampaignService campaigns, LiveCampaignPresentation presentation,
                                  LocalFormTokenService tokens) {
        this.campaigns = campaigns;
        this.presentation = presentation;
        this.tokens = tokens;
    }

    @PostMapping("/live-campaigns/prepare")
    public String prepare(@RequestParam(name = "eventId", required = false) List<UUID> eventIds,
                          @RequestParam(name = "localFormToken", required = false) String token,
                          HttpSession session, Model model, RedirectAttributes redirect) {
        tokens.consume(session, token);
        var preparation = campaigns.prepareSelection(eventIds == null ? List.of() : eventIds);
        if (preparation.manifest() == null) {
            model.addAttribute("excludedFinished", preparation.excludedFinished());
            return "live-campaign-ineligible";
        }
        if (!preparation.excludedFinished().isEmpty())
            redirect.addFlashAttribute("excludedFinished", preparation.excludedFinished());
        return "redirect:/live-campaigns/" + preparation.manifest().campaignId();
    }

    @GetMapping("/live-campaigns/{campaignId}")
    public String view(@PathVariable UUID campaignId, HttpSession session, Model model) {
        var campaign = campaigns.state(campaignId);
        if (campaign == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("campaign", presentation.state(campaign));
        model.addAttribute("manifest", campaign.manifest());
        model.addAttribute("localFormToken", tokens.issue(session));
        return "live-campaign";
    }

    @PostMapping("/live-campaigns/{campaignId}/launch")
    public String launch(@PathVariable UUID campaignId,
                         @RequestParam(name = "manifestHash") String manifestHash,
                         @RequestParam(name = "confirmation", defaultValue = "false") boolean confirmation,
                         @RequestParam(name = "localFormToken", required = false) String token,
                         HttpSession session) {
        tokens.consume(session, token);
        if (!confirmation) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LIVE_CONFIRMATION_REQUIRED");
        campaigns.launch(campaignId, manifestHash);
        return "redirect:/live-campaigns/" + campaignId;
    }

    @PostMapping("/live-campaigns/{campaignId}/stop")
    public String stop(@PathVariable UUID campaignId,
                       @RequestParam(name = "eventId", required = false) UUID eventId,
                       @RequestParam(name = "localFormToken", required = false) String token,
                       HttpSession session) {
        tokens.consume(session, token);
        campaigns.stop(campaignId, eventId);
        return "redirect:/live-campaigns/" + campaignId;
    }

    @PostMapping("/live-campaigns/{campaignId}/events/{canonicalEventId}/stop")
    public String stopEvent(@PathVariable UUID campaignId, @PathVariable UUID canonicalEventId,
                            @RequestParam(name = "localFormToken", required = false) String token,
                            HttpSession session) {
        tokens.consume(session, token);
        campaigns.stop(campaignId, canonicalEventId);
        return "redirect:/live-campaigns/" + campaignId;
    }

    @GetMapping("/live-campaigns/{campaignId}/state")
    @ResponseBody
    public LiveCampaignPresentation.Campaign state(@PathVariable UUID campaignId) {
        var campaign = campaigns.state(campaignId);
        if (campaign == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return presentation.state(campaign);
    }

    @GetMapping("/events/state")
    @ResponseBody
    public List<LiveCampaignPresentation.Campaign> eventStates(
            @RequestParam(name = "eventId", required = false) List<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return List.of();
        if (eventIds.size() > 100 || eventIds.stream().distinct().count() != eventIds.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_LIVE_SELECTION");
        return campaigns.eventStates(eventIds).stream().map(presentation::state).toList();
    }

    @GetMapping("/events/{canonicalEventId}/state")
    @ResponseBody
    public List<LiveCampaignPresentation.Campaign> eventState(@PathVariable UUID canonicalEventId) {
        return campaigns.eventStates(List.of(canonicalEventId)).stream().map(presentation::state).toList();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(IllegalArgumentException exception, Model model) {
        String code = switch (String.valueOf(exception.getMessage())) {
            case "LIVE_ALL_EVENTS_FINISHED", "LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY",
                 "LIVE_CAPACITY_REFUSED_REDUCE_SELECTION" -> exception.getMessage();
            default -> "LIVE_SELECTION_INVALID";
        };
        String message = switch (code) {
            case "LIVE_ALL_EVENTS_FINISHED" -> "Ces rencontres sont déjà terminées (finished). Aucun lancement live ni appel fournisseur n’a été effectué. Revenir aux rencontres pour préparer une autre sélection.";
            case "LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY" ->
                    "Le nombre de rencontres éligibles dépasse la capacité du profil configuré. Réduire la sélection ou configurer le palier qualifié de deux ou trois rencontres selon le runbook. Les rencontres déjà finished ne comptent pas dans cette limite.";
            case "LIVE_CAPACITY_REFUSED_REDUCE_SELECTION" ->
                    "Le profil de charge configuré ne permet pas de servir cette sélection à la minute. Réduire la sélection ou utiliser le profil de charge associé à la qualification du palier ; relever seulement le nombre de rencontres ne suffit pas.";
            default -> "La sélection ou le manifeste est invalide. Préparer une nouvelle campagne.";
        };
        model.addAttribute("liveError", message);
        model.addAttribute("liveErrorCode", code);
        return "live-campaign-error";
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(Model model) {
        model.addAttribute("liveError", "Cette campagne n’existe pas dans la base locale.");
        return "live-campaign-error";
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String rejected(IllegalStateException exception, Model model) {
        String code = switch (String.valueOf(exception.getMessage())) {
            case "LIVE_DISABLED", "LIVE_PROVIDER_BUSY", "LIVE_STORAGE_PROBE_NOT_CONFIGURED",
                 "LIVE_STORAGE_PROBE_TIMEOUT", "LIVE_STORAGE_PROBE_FAILED", "LIVE_STORAGE_PROBE_INVALID",
                 "LIVE_STORAGE_PROBE_INTERRUPTED", "LIVE_STORAGE_CAPACITY_REFUSED", "LIVE_POLICY_INVALID",
                 "LIVE_CAPACITY_QUALIFICATION_REQUIRED", "LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY" -> exception.getMessage();
            default -> "LIVE_REQUEST_REJECTED";
        };
        String message = switch (code) {
            case "LIVE_STORAGE_PROBE_NOT_CONFIGURED" -> "Le contrôle d’espace PostgreSQL n’est pas configuré : renseigner SOFASCORE_LIVE_DOCKER_EXECUTABLE avec le chemin absolu de docker.exe dans le lanceur Eclipse, puis redémarrer l’application. Vérifier aussi SOFASCORE_LIVE_POSTGRES_CONTAINER. Aucun appel fournisseur n’a été effectué par cette demande.";
            case "LIVE_STORAGE_PROBE_TIMEOUT" -> "Le contrôle d’espace PostgreSQL n’a pas répondu dans le délai prévu. Vérifier Docker Desktop et le conteneur configuré, puis préparer à nouveau la campagne. Aucun appel fournisseur n’a été effectué par cette demande.";
            case "LIVE_STORAGE_PROBE_FAILED", "LIVE_STORAGE_PROBE_INVALID" -> "L’espace du volume PostgreSQL ne peut pas être mesuré. Vérifier le chemin Docker, Docker Desktop et le nom du conteneur SOFASCORE_LIVE_POSTGRES_CONTAINER. Aucun appel fournisseur n’a été effectué par cette demande.";
            case "LIVE_STORAGE_PROBE_INTERRUPTED" -> "Le contrôle local d’espace PostgreSQL a été interrompu. Préparer à nouveau la campagne. Aucun appel fournisseur n’a été effectué par cette demande.";
            case "LIVE_STORAGE_CAPACITY_REFUSED" -> "L’espace libre du volume PostgreSQL est insuffisant pour le budget de cette sélection et sa réserve de sécurité. Prévoir davantage d’espace ou réduire la sélection avant une nouvelle préparation. Aucun appel fournisseur n’a été effectué par cette demande.";
            case "LIVE_POLICY_INVALID" -> "Les limites de la campagne locale sont invalides. Vérifier la durée, la capacité, les délais et la réserve de stockage dans la configuration live avant une nouvelle préparation.";
            case "LIVE_CAPACITY_QUALIFICATION_REQUIRED" -> "Cette capacité ou cette cadence exige une preuve de qualification. Pour le pilote initial, conserver une rencontre et l’enveloppe de requête de dix secondes.";
            case "LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY" -> "Le délai maximal d’une requête live doit être compris entre zéro exclu et dix secondes. Corriger le délai Playwright avant le lancement.";
            case "LIVE_DISABLED" -> "Le lancement live est désactivé. Activer l’opt-in local dédié avant de lancer une campagne préparée.";
            case "LIVE_PROVIDER_BUSY" -> "Une collecte fournisseur occupe déjà la session locale. Attendre sa fin avant de lancer cette campagne.";
            default -> "La demande locale a été refusée. Revenir aux rencontres et préparer à nouveau la sélection ; si le refus persiste, conserver ce code pour le diagnostic.";
        };
        model.addAttribute("liveError", message);
        model.addAttribute("liveErrorCode", code);
        return "live-campaign-error";
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String unavailable(Model model) {
        model.addAttribute("liveError", "Les observations locales sont momentanément indisponibles. Aucun lancement supplémentaire n’a été demandé.");
        return "live-campaign-error";
    }
}
