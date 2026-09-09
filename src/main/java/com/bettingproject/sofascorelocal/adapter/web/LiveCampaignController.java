package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.CampaignView;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** POSTs control the manually launched session. GETs read local evidence and process observations only. */
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
            model.addAttribute("excludedPostponed", preparation.excludedPostponed());
            return "live-campaign-ineligible";
        }
        if (!preparation.excludedFinished().isEmpty())
            redirect.addFlashAttribute("excludedFinished", preparation.excludedFinished());
        if (!preparation.excludedPostponed().isEmpty())
            redirect.addFlashAttribute("excludedPostponed", preparation.excludedPostponed());
        return "redirect:/live-campaigns/" + preparation.manifest().campaignId();
    }

    @GetMapping("/live-campaigns/{campaignId}")
    public String view(@PathVariable UUID campaignId,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       @RequestParam(name = "eventId", required = false) UUID eventId,
                       HttpSession session, Model model) {
        requirePage(page);
        var campaign = campaigns.state(campaignId);
        if (campaign == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (eventId != null) {
            int index = -1;
            for (int i = 0; i < campaign.events().size(); i++) {
                if (campaign.events().get(i).target().canonicalEventId().equals(eventId)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            page = index / LiveCampaignPresentation.EVENTS_PER_PAGE + 1;
        }
        model.addAttribute("campaign", presentPage(campaign, page));
        model.addAttribute("manifest", campaign.manifest());
        var orphanCleanup = campaigns.orphanCleanupGuard(campaignId);
        model.addAttribute("orphanCleanup", orphanCleanup.orElse(null));
        model.addAttribute("canPrepareAgain", orphanCleanup.isEmpty()
                && (campaign.state().equals("INTERRUPTED") || campaign.state().equals("COMPLETED")
                    || campaign.state().startsWith("STOPPED_")));
        model.addAttribute("localFormToken", tokens.issue(session));
        return "live-campaign";
    }

    @PostMapping("/live-campaigns/{campaignId}/finalize-interruption")
    public String finalizeInterruption(@PathVariable UUID campaignId,
                                       @RequestParam(name = "guardGeneration") long guardGeneration,
                                       @RequestParam(name = "localFormToken", required = false) String token,
                                       @RequestParam(name = "page", defaultValue = "1") int page,
                                       HttpSession session, RedirectAttributes redirect) {
        requirePage(page);
        tokens.consume(session, token);
        if (guardGeneration < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LIVE_CLEANUP_GENERATION_REQUIRED");
        campaigns.finalizeInterruptedCleanup(campaignId, guardGeneration);
        redirect.addFlashAttribute("liveSuccess", "La session interrompue est clôturée. Son historique est conservé ; aucune collecte n’a été lancée.");
        return campaignRedirect(campaignId, page);
    }

    @PostMapping("/live-campaigns/{campaignId}/launch")
    public String launch(@PathVariable UUID campaignId,
                         @RequestParam(name = "manifestHash") String manifestHash,
                         @RequestParam(name = "confirmation", defaultValue = "false") boolean confirmation,
                         @RequestParam(name = "localFormToken", required = false) String token,
                         @RequestParam(name = "page", defaultValue = "1") int page,
                         HttpSession session) {
        requirePage(page);
        tokens.consume(session, token);
        if (!confirmation) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LIVE_CONFIRMATION_REQUIRED");
        campaigns.launch(campaignId, manifestHash);
        return campaignRedirect(campaignId, page);
    }

    @PostMapping("/live-campaigns/{campaignId}/cancel-preparation")
    public String cancelPreparation(@PathVariable UUID campaignId,
                                    @RequestParam(name="manifestHash") String manifestHash,
                                    @RequestParam(name="localFormToken",required=false) String token,
                                    @RequestParam(name = "page", defaultValue = "1") int page,
                                    HttpSession session) {
        requirePage(page);
        tokens.consume(session,token);
        campaigns.cancelPreparation(campaignId,manifestHash);
        return campaignRedirect(campaignId, page);
    }

    @PostMapping("/live-campaigns/{campaignId}/stop")
    public String stop(@PathVariable UUID campaignId,
                       @RequestParam(name = "eventId", required = false) UUID eventId,
                       @RequestParam(name = "localFormToken", required = false) String token,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       HttpSession session) {
        requirePage(page);
        tokens.consume(session, token);
        campaigns.stop(campaignId, eventId);
        return campaignRedirect(campaignId, page);
    }

    @PostMapping("/live-campaigns/{campaignId}/events/{canonicalEventId}/stop")
    public String stopEvent(@PathVariable UUID campaignId, @PathVariable UUID canonicalEventId,
                            @RequestParam(name = "localFormToken", required = false) String token,
                            @RequestParam(name = "page", defaultValue = "1") int page,
                            HttpSession session) {
        requirePage(page);
        tokens.consume(session, token);
        campaigns.stop(campaignId, canonicalEventId);
        return campaignRedirect(campaignId, page);
    }

    @GetMapping("/live-campaigns/{campaignId}/state")
    @ResponseBody
    public LiveCampaignPresentation.Campaign state(@PathVariable UUID campaignId,
            @RequestParam(name = "page", required = false) Integer page) {
        if (page != null) requirePage(page);
        var campaign = campaigns.state(campaignId);
        if (campaign == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return page == null ? present(campaign) : presentPage(campaign, page);
    }

    @GetMapping("/events/state")
    @ResponseBody
    public List<LiveCampaignPresentation.Campaign> eventStates(
            @RequestParam(name = "eventId", required = false) List<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return List.of();
        if (eventIds.size() > 100 || eventIds.stream().distinct().count() != eventIds.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_LIVE_SELECTION");
        return campaigns.eventStates(eventIds).stream().map(this::present).toList();
    }

    @GetMapping("/events/{canonicalEventId}/state")
    @ResponseBody
    public List<LiveCampaignPresentation.Campaign> eventState(@PathVariable UUID canonicalEventId) {
        return campaigns.eventStates(List.of(canonicalEventId)).stream().map(this::present).toList();
    }

    private LiveCampaignPresentation.Campaign present(CampaignView campaign) {
        return presentation.state(campaign, campaigns.runtimeStatus(campaign.manifest().campaignId()).orElse(null));
    }

    private LiveCampaignPresentation.Campaign presentPage(CampaignView campaign, int page) {
        return presentation.page(campaign, campaigns.runtimeStatus(campaign.manifest().campaignId()).orElse(null), page);
    }

    private static void requirePage(int page) {
        if (page < 1) throw new IllegalArgumentException("LIVE_PAGE_INVALID");
    }

    private static String campaignRedirect(UUID campaignId, int page) {
        return "redirect:/live-campaigns/" + campaignId + (page == 1 ? "" : "?page=" + page);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(IllegalArgumentException exception, Model model, HttpServletRequest request) {
        String code = switch (String.valueOf(exception.getMessage())) {
            case "LIVE_ALL_EVENTS_FINISHED", "LIVE_ALL_EVENTS_INELIGIBLE", "LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY",
                 "LIVE_CAPACITY_REFUSED_REDUCE_SELECTION", "LIVE_ORPHAN_CLEANUP_INVALID_GUARD", "LIVE_PAGE_INVALID" -> exception.getMessage();
            default -> "LIVE_SELECTION_INVALID";
        };
        String message = switch (code) {
            case "LIVE_PAGE_INVALID" -> "Le numéro de page doit être un entier supérieur ou égal à 1.";
            case "LIVE_ALL_EVENTS_FINISHED" -> "Ces rencontres sont déjà terminées (finished). Aucun lancement live ni appel fournisseur n’a été effectué. Revenir aux rencontres pour préparer une autre sélection.";
            case "LIVE_ALL_EVENTS_INELIGIBLE" -> "Ces rencontres sont reportées (postponed) ou déjà terminées (finished). Aucun lancement live ni appel fournisseur n’a été effectué. Revenir aux rencontres pour préparer une autre sélection.";
            case "LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY" ->
                    "La sélection dépasse la capacité admise par le profil live-v6 : " + campaigns.selectionMaximum() + " rencontres admissibles, avec une cible nominale de 100 secondes. Réduire la sélection. Les rencontres terminées ou reportées sont exclues.";
            case "LIVE_CAPACITY_REFUSED_REDUCE_SELECTION" ->
                    "La sélection dépasse le budget du profil qualifié. Une nouvelle campagne live-v6 admet " + campaigns.selectionMaximum() + " rencontres au maximum. Réduire la sélection ; la cible de 100 secondes reste soumise au budget fournisseur partagé et à la disponibilité des données.";
            case "LIVE_ORPHAN_CLEANUP_INVALID_GUARD" -> "L’état de la session a changé depuis l’affichage du formulaire. Actualiser la page de la campagne avant de réessayer.";
            default -> "La sélection ou le manifeste est invalide. Préparer une nouvelle campagne.";
        };
        model.addAttribute("liveError", message);
        model.addAttribute("liveErrorCode", code);
        if (code.equals("LIVE_ORPHAN_CLEANUP_INVALID_GUARD")) addCleanupReturnLink(request, model);
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
    public String rejected(IllegalStateException exception, Model model, HttpServletRequest request) {
        String code = switch (String.valueOf(exception.getMessage())) {
            case "LIVE_DISABLED", "LIVE_PROVIDER_BUSY", "LIVE_PROVIDER_CLEANUP_REQUIRED", "LIVE_LAUNCH_FAILED",
                 "LIVE_EVENT_ALREADY_IN_CAMPAIGN", "LIVE_STORAGE_PROBE_NOT_CONFIGURED",
                 "LIVE_STORAGE_PROBE_TIMEOUT", "LIVE_STORAGE_PROBE_FAILED", "LIVE_STORAGE_PROBE_INVALID",
                 "LIVE_STORAGE_PROBE_INTERRUPTED", "LIVE_STORAGE_CAPACITY_REFUSED", "LIVE_POLICY_INVALID",
                 "LIVE_CAPACITY_QUALIFICATION_REQUIRED", "LIVE_GROUPED_QUALIFICATION_REQUIRED", "LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY",
                 "LIVE_PREPARATION_ALREADY_LAUNCHED", "LIVE_PREPARATION_NOT_CANCELABLE",
                 "LIVE_CLEANUP_OWNER_ACTIVE", "LIVE_CLEANUP_PROCESS_UNVERIFIED", "LIVE_CLEANUP_PROCESS_ACTIVE",
                 "LIVE_CLEANUP_STATE_CHANGED", "LIVE_CLEANUP_BUSY", "LIVE_ORPHAN_CLEANUP_GUARD_CHANGED",
                 "LIVE_ORPHAN_CLEANUP_INCOMPLETE", "PROVIDER_SUSPENDED", "PROVIDER_CLOCK_REGRESSION",
                 "PROVIDER_DEPARTURE_UNRESOLVED", "PROVIDER_DIAGNOSTIC_PERSISTENCE_FAILED",
                 "PROVIDER_HTTP_403", "PROVIDER_HTTP_429" -> exception.getMessage();
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
            case "LIVE_GROUPED_QUALIFICATION_REQUIRED" -> "La politique demandée exige une preuve de qualification dédiée aux groupes et un coût qualifié pour chaque famille. Les nouvelles campagnes live-v6 utilisent un profil indépendant des campagnes historiques. Configurer SOFASCORE_LIVE_GROUPED_V6_QUALIFICATION_SHA256 et les enveloppes qualifiées décrites dans le runbook, puis préparer une nouvelle sélection.";
            case "LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY" -> "Le délai Playwright doit être strictement positif et ne pas dépasser trente secondes pour live-v6, vingt secondes pour live-v5 ou dix secondes pour les campagnes antérieures. Corriger SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT avant le lancement.";
            case "PROVIDER_SUSPENDED", "PROVIDER_HTTP_403", "PROVIDER_HTTP_429" -> "L’accès fournisseur est suspendu après un refus HTTP 403 ou 429. Consulter l’état de l’accès fournisseur et son diagnostic avant tout réarmement manuel. Une nouvelle campagne ou un redémarrage ne lève pas cette suspension.";
            case "PROVIDER_CLOCK_REGRESSION" -> "L’horloge locale est antérieure au dernier départ enregistré. Le budget fournisseur reste bloquant. Vérifier l’heure du système avant de préparer un nouveau lancement.";
            case "PROVIDER_DEPARTURE_UNRESOLVED" -> "La fin d’un appel précédent n’est pas encore vérifiée. Finaliser la clôture locale, puis consulter l’état de l’accès fournisseur pour résoudre ce départ avant une nouvelle collecte.";
            case "PROVIDER_DIAGNOSTIC_PERSISTENCE_FAILED" -> "Le diagnostic fournisseur n’a pas pu être enregistré complètement. L’accès reste bloqué. Vérifier la disponibilité de PostgreSQL et finaliser la clôture locale avant de consulter l’état de l’accès fournisseur.";
            case "LIVE_PREPARATION_ALREADY_LAUNCHED" -> "Cette campagne a déjà été lancée. Sa préparation ne peut plus être annulée ; utiliser l’arrêt de la campagne si sa collecte est encore en cours.";
            case "LIVE_PREPARATION_NOT_CANCELABLE" -> "Cette campagne n’est plus en préparation. Actualiser sa page pour consulter son état actuel.";
            case "LIVE_DISABLED" -> "Le lancement live est désactivé. Activer l’opt-in local dédié avant de lancer une campagne préparée.";
            case "LIVE_PROVIDER_BUSY" -> "Une collecte fournisseur occupe déjà la session locale. Attendre sa fin avant de lancer cette campagne.";
            case "LIVE_PROVIDER_CLEANUP_REQUIRED" -> "La session fournisseur précédente reste verrouillée en attente de clôture locale. Ouvrir la campagne concernée et utiliser « Clôturer la session interrompue ». La clôture vérifie que les processus précédents sont arrêtés. Revenir ensuite à cette sélection pour préparer ou lancer la campagne. Aucun appel fournisseur n’a été effectué par cette demande.";
            case "LIVE_CLEANUP_OWNER_ACTIVE" -> "La session appartient encore à une application active. Arrêter la campagne depuis cette application avant de demander sa clôture.";
            case "LIVE_CLEANUP_PROCESS_UNVERIFIED" -> "L’arrêt des processus de la session précédente n’a pas pu être vérifié. La session reste verrouillée. Faire vérifier leur arrêt local avant de réessayer.";
            case "LIVE_CLEANUP_PROCESS_ACTIVE" -> "Un processus de la session précédente est encore actif. La session reste verrouillée. Attendre son arrêt avant de réessayer.";
            case "LIVE_CLEANUP_STATE_CHANGED", "LIVE_ORPHAN_CLEANUP_GUARD_CHANGED" -> "L’état de la session a changé depuis l’affichage du formulaire. Actualiser la page de la campagne avant de réessayer.";
            case "LIVE_ORPHAN_CLEANUP_INCOMPLETE" -> "La clôture de la session précédente n’a pas pu être vérifiée complètement. La session reste verrouillée. Actualiser la page de la campagne et faire vérifier l’arrêt des processus avant de réessayer.";
            case "LIVE_CLEANUP_BUSY" -> "Une clôture de session est déjà en cours. Attendre sa fin, puis actualiser la page de la campagne.";
            case "LIVE_LAUNCH_FAILED" -> "Le lancement de la campagne a échoué. Consulter son état local et finaliser sa clôture si elle est requise avant de préparer un nouveau lancement.";
            case "LIVE_EVENT_ALREADY_IN_CAMPAIGN" -> "Une rencontre sélectionnée appartient déjà à une campagne en cours. Suivre cette campagne ou retirer la rencontre de la sélection. Une rencontre en STOPPED_ERROR peut être sélectionnée à nouveau.";
            default -> "La demande locale a été refusée. Revenir aux rencontres et préparer à nouveau la sélection ; si le refus persiste, conserver ce code pour le diagnostic.";
        };
        model.addAttribute("liveError", message);
        model.addAttribute("liveErrorCode", code);
        if (code.equals("LIVE_PROVIDER_CLEANUP_REQUIRED"))
            campaigns.providerCleanupCampaignId().ifPresent(id -> model.addAttribute("cleanupCampaignId", id));
        if (code.startsWith("LIVE_CLEANUP_") || code.startsWith("LIVE_ORPHAN_CLEANUP_"))
            addCleanupReturnLink(request, model);
        return "live-campaign-error";
    }

    private static void addCleanupReturnLink(HttpServletRequest request, Model model) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> path && path.get("campaignId") instanceof String campaignId) {
            try {
                model.addAttribute("retryCampaignId", UUID.fromString(campaignId));
            } catch (IllegalArgumentException ignored) {
                // A return link can only point to a local campaign with a valid UUID.
            }
        }
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String unavailable(Model model) {
        model.addAttribute("liveError", "Les observations locales sont momentanément indisponibles. Aucun lancement supplémentaire n’a été demandé.");
        return "live-campaign-error";
    }
}
