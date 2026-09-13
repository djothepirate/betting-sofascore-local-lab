package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData;
import com.bettingproject.sofascorelocal.port.ProviderCampaignGuardStore;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.Clock;
import java.util.UUID;

/** Local provider protection controls only. No action probes, opens or resumes the provider. */
@Controller
public class ProviderAccessController {
    private final ProviderResilienceStore store;
    private final ProviderCampaignGuardStore guard;
    private final PlaywrightProviderSupervisor supervisor;
    private final ManualProviderRequestCoordinator coordinator;
    private final LiveCampaignService campaigns;
    private final LocalFormTokenService tokens;
    private final Clock clock=Clock.systemUTC();
    public ProviderAccessController(ProviderResilienceStore store, ProviderCampaignGuardStore guard,
            PlaywrightProviderSupervisor supervisor, ManualProviderRequestCoordinator coordinator,
            LiveCampaignService campaigns, LocalFormTokenService tokens) {
        this.store=store; this.guard=guard; this.supervisor=supervisor; this.coordinator=coordinator;
        this.campaigns=campaigns; this.tokens=tokens;
    }
    @GetMapping("/provider-access")
    public String view(HttpSession session,Model model) {
        var state=store.snapshot();
        model.addAttribute("access",state);
        model.addAttribute("decision",store.departureDecision(clock.instant()));
        model.addAttribute("suspended",state.state()==ProviderResilienceData.State.SUSPENDED);
        model.addAttribute("canRearm",state.state()==ProviderResilienceData.State.SUSPENDED
                && (state.retryNotBefore()==null || !clock.instant().isBefore(state.retryNotBefore())));
        model.addAttribute("orphanedManualGuard",campaigns.orphanedManualCleanupGuard().orElse(null));
        model.addAttribute("localFormToken",tokens.issue(session));
        return "provider-access";
    }

    @PostMapping("/provider-access/rearm")
    public String rearm(@RequestParam long version,@RequestParam(defaultValue="false") boolean confirmation,
            @RequestParam(name="localFormToken",required=false) String token,HttpSession session,RedirectAttributes redirect) {
        tokens.consume(session,token);
        if(!confirmation) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PROVIDER_CONFIRMATION_REQUIRED");
        coordinator.withExclusiveLocalCleanup(()->{
            requireIdle();
            if(store.snapshot().unresolvedDispatchId()!=null) throw new IllegalStateException("PROVIDER_DEPARTURE_UNRESOLVED");
            store.rearm(version,clock.instant());
        });
        redirect.addFlashAttribute("accessSuccess","La suspension a été levée localement. Aucune collecte n’a été lancée ; les budgets cumulés restent conservés.");
        return "redirect:/provider-access";
    }

    @PostMapping("/provider-access/finish-uncertain-departure")
    public String finishUncertain(@RequestParam UUID dispatchId,@RequestParam(defaultValue="false") boolean confirmation,
            @RequestParam(name="localFormToken",required=false) String token,HttpSession session,RedirectAttributes redirect) {
        tokens.consume(session,token);
        if(!confirmation) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PROVIDER_CONFIRMATION_REQUIRED");
        coordinator.withExclusiveLocalCleanup(()->{
            requireIdle();
            if(!dispatchId.equals(store.snapshot().unresolvedDispatchId())) throw new IllegalStateException("PROVIDER_REARM_STALE_VERSION");
            // FREE is published only after verified process cleanup, including recovery of an orphan.
            // This closes the pressure reservation, never creates a response or resets a refusal.
            store.markDepartureFinished(dispatchId,clock.instant());
        });
        redirect.addFlashAttribute("accessSuccess","Le départ incertain est clôturé après vérification locale du nettoyage. Son coût est conservé ; aucune réponse fournisseur n’est supposée.");
        return "redirect:/provider-access";
    }

    @PostMapping("/provider-access/release-orphaned-manual-guard")
    public String releaseOrphanedManualGuard(@RequestParam UUID campaignId,@RequestParam long generation,
            @RequestParam(defaultValue="false") boolean confirmation,
            @RequestParam(name="localFormToken",required=false) String token,HttpSession session,RedirectAttributes redirect) {
        tokens.consume(session,token);
        if(!confirmation) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PROVIDER_CONFIRMATION_REQUIRED");
        if(generation < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"LIVE_CLEANUP_STATE_CHANGED");
        campaigns.finalizeOrphanedManualCleanup(campaignId,generation);
        redirect.addFlashAttribute("accessSuccess","La garde manuelle orpheline a été libérée après vérification locale des processus. Aucun appel fournisseur, réarmement ni reprise n’a été effectué.");
        return "redirect:/provider-access";
    }

    private void requireIdle() {
        if(!"FREE".equals(guard.snapshot().state()) || supervisor.activeCampaignId().isPresent())
            throw new IllegalStateException("PROVIDER_CLEANUP_REQUIRED");
    }

    @ExceptionHandler(IllegalStateException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public String refused(IllegalStateException failure,Model model) {
        String message=switch(String.valueOf(failure.getMessage())) {
            case "PROVIDER_REARM_TOO_EARLY" -> "Le délai demandé par le fournisseur n’est pas écoulé. La suspension reste active.";
            case "PROVIDER_REARM_STALE_VERSION","PROVIDER_REARM_NOT_SUSPENDED" -> "L’état a changé. Actualiser la page avant une nouvelle décision.";
            case "PROVIDER_DEPARTURE_UNRESOLVED" -> "Un départ précédent reste incertain. Clôturer sa réservation après le nettoyage vérifié.";
            case "PROVIDER_CLEANUP_REQUIRED" -> "Une session possède encore l’accès fournisseur. Arrêter et clôturer cette session avant le réarmement.";
            case "LIVE_CLEANUP_STATE_CHANGED" -> "L’état de la garde a changé. Actualiser la page avant une nouvelle décision locale.";
            case "LIVE_CLEANUP_BUSY" -> "Une session locale est encore active. Attendre sa fin avant de reprendre le nettoyage.";
            case "LIVE_CLEANUP_OWNER_ACTIVE","LIVE_CLEANUP_PROCESS_ACTIVE","LIVE_CLEANUP_PROCESS_UNVERIFIED" ->
                    "Le nettoyage des processus ne peut pas encore être prouvé localement. La garde reste bloquante.";
            default -> "L’opération locale n’a pas pu être confirmée. La protection reste bloquante ; actualiser l’état avant de réessayer.";
        };
        model.addAttribute("liveError",message);
        model.addAttribute("liveErrorCode", "PROVIDER_LOCAL_OPERATION_REFUSED");
        return "live-campaign-error";
    }

    @ExceptionHandler(ManualProviderRequestCoordinator.CoordinationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String busy(Model model) {
        model.addAttribute("liveError", "Une session possède encore l’accès fournisseur. Clôturer cette session avant cette opération locale.");
        model.addAttribute("liveErrorCode", "PROVIDER_CLEANUP_REQUIRED");
        return "live-campaign-error";
    }
}
