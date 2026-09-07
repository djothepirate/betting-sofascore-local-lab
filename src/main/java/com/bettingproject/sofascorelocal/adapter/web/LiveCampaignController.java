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
        String message = switch (String.valueOf(exception.getMessage())) {
            case "LIVE_ALL_EVENTS_FINISHED" -> "Ces rencontres sont déjà terminées (finished). Aucun lancement live ni appel fournisseur n’a été effectué. Revenir aux rencontres pour préparer une autre sélection.";
            case "LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY", "LIVE_CAPACITY_REFUSED_REDUCE_SELECTION" ->
                    "Cette sélection dépasse la capacité qualifiée ou la cadence admissible. Réduire la sélection ; le premier pilote porte sur une rencontre.";
            default -> "La sélection ou le manifeste est invalide. Préparer une nouvelle campagne.";
        };
        model.addAttribute("liveError", message);
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
        String message = switch (String.valueOf(exception.getMessage())) {
            case "LIVE_DISABLED" -> "Le lancement live est désactivé. Activer l’opt-in local dédié avant de lancer une campagne préparée.";
            case "LIVE_PROVIDER_BUSY" -> "Une collecte fournisseur occupe déjà la session locale. Attendre sa fin avant de lancer cette campagne.";
            default -> "La campagne ne peut pas démarrer dans son état actuel. Vérifier sa préparation, l’opt-in et la disponibilité de la session locale.";
        };
        model.addAttribute("liveError", message);
        return "live-campaign-error";
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String unavailable(Model model) {
        model.addAttribute("liveError", "Les observations locales sont momentanément indisponibles. Aucun lancement supplémentaire n’a été demandé.");
        return "live-campaign-error";
    }
}
