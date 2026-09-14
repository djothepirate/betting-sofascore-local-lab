package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ProviderCampaignStopException;
import com.bettingproject.sofascorelocal.application.network.J3ProviderCampaignStopService;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryControlException;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryControlService;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryResult;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryService;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Controller
public class TournamentEventDiscoveryController {

    private static final String REDIRECT =
            "redirect:/dashboard#tournament-event-discovery";

    private final TournamentEventDiscoveryControlService controlService;
    private final TournamentEventDiscoveryService discoveryService;
    private final J3ProviderCampaignStopService providerCampaignStopService;
    private final LocalFormTokenService formTokenService;

    public TournamentEventDiscoveryController(
            TournamentEventDiscoveryControlService controlService,
            TournamentEventDiscoveryService discoveryService,
            J3ProviderCampaignStopService providerCampaignStopService,
            LocalFormTokenService formTokenService) {
        this.controlService = controlService;
        this.discoveryService = discoveryService;
        this.providerCampaignStopService = providerCampaignStopService;
        this.formTokenService = formTokenService;
    }

    @PostMapping({"/tournament-event-discovery/prepare", "/tournament-event-discovery/execute",
            "/tournament-event-discovery/import-json"})
    @ResponseStatus(value = HttpStatus.GONE, reason = "Ancien formulaire : rechargez le tableau de bord.")
    public void retiredConfirmationForms() {
        // Old pages must be reloaded; stale intentions never become direct actions.
    }

    @PostMapping("/tournament-event-discovery/collect")
    public String collect(
            @RequestParam String localFormToken,
            @RequestParam UUID collectionId,
            @RequestParam LocalDate date,
            @RequestParam long tournamentId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        UUID claimedId = null;
        try {
            var claim = controlService.claimDirect(collectionId, date, tournamentId);
            claimedId = claim.requestId();
            addResult(redirectAttributes, discoveryService.execute(claim));
        }
        catch (TournamentEventDiscoveryControlException | IllegalArgumentException exception) {
            failOwnedClaim(claimedId, "LOCAL_EXECUTION_FAILURE");
            addError(redirectAttributes, safeCode(exception));
        }
        catch (RuntimeException exception) {
            failOwnedClaim(claimedId, "LOCAL_EXECUTION_FAILURE");
            addError(redirectAttributes, "LOCAL_EXECUTION_FAILURE");
        }
        return dateRedirect(date);
    }

    @PostMapping(value = "/tournament-event-discovery/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String importJson(
            @RequestParam String localFormToken,
            @RequestParam UUID collectionId,
            @RequestParam LocalDate date,
            @RequestParam long tournamentId,
            @RequestParam("jsonFile") MultipartFile jsonFile,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        RawPayloadEvidence payload = readLocalJson(jsonFile, redirectAttributes);
        if (payload == null) {
            return dateRedirect(date);
        }
        UUID claimedId = null;
        try {
            var claim = controlService.claimDirectLocalImport(collectionId, date, tournamentId);
            claimedId = claim.requestId();
            addResult(redirectAttributes, discoveryService.importLocalJson(claim, payload));
        }
        catch (TournamentEventDiscoveryControlException | IllegalArgumentException exception) {
            failOwnedClaim(claimedId, "LOCAL_IMPORT_FAILURE");
            addError(redirectAttributes, safeCode(exception));
        }
        catch (RuntimeException exception) {
            failOwnedClaim(claimedId, "LOCAL_IMPORT_FAILURE");
            addError(redirectAttributes, "LOCAL_IMPORT_FAILURE");
        }
        return dateRedirect(date);
    }

    private void failOwnedClaim(UUID claimedId, String code) {
        if (claimedId != null && controlService.executionMayContinue(claimedId)) {
            try {
                controlService.fail(claimedId, code);
            }
            catch (TournamentEventDiscoveryControlException stopped) {
                // The operator stop won the race; never overwrite its terminal state.
            }
        }
    }

    private static String dateRedirect(LocalDate date) {
        return "redirect:/dashboard?j3Date=" + date + "#tournament-event-discovery";
    }

    @PostMapping("/tournament-event-discovery/stop")
    public String stop(
            @RequestParam("localFormToken") String localFormToken,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            providerCampaignStopService.stopTournamentDiscovery();
            redirectAttributes.addFlashAttribute(
                    "tournamentDiscoveryMessage",
                    "Arrêt global appliqué à la découverte de rencontres. "
                            + "Aucun nouvel appel n’est autorisé avant redémarrage.");
            redirectAttributes.addFlashAttribute(
                    "tournamentDiscoveryMessageKind", "danger");
        }
        catch (J3ProviderCampaignStopException exception) {
            redirectAttributes.addFlashAttribute(
                    "tournamentDiscoveryMessage",
                    "L’arrêt métier a été appliqué, mais le nettoyage du worker Playwright n’a pas pu être confirmé. Aucun nouvel appel n’est autorisé.");
            redirectAttributes.addFlashAttribute(
                    "tournamentDiscoveryMessageKind", "danger");
        }
        return REDIRECT;
    }

    private static String safeCode(RuntimeException exception) {
        if (exception instanceof TournamentEventDiscoveryControlException control) {
            return control.error().name();
        }
        return "INVALID_TOURNAMENT_SELECTION";
    }

    private static RawPayloadEvidence readLocalJson(
            MultipartFile jsonFile,
            RedirectAttributes redirectAttributes) {
        if (jsonFile == null || jsonFile.isEmpty()) {
            addError(redirectAttributes, "LOCAL_IMPORT_EMPTY");
            return null;
        }
        if (jsonFile.getSize() > RawPayloadEvidence.MAXIMUM_BYTES) {
            addError(redirectAttributes, "LOCAL_IMPORT_TOO_LARGE");
            return null;
        }
        try {
            byte[] bytes = jsonFile.getBytes();
            if (bytes.length == 0) {
                addError(redirectAttributes, "LOCAL_IMPORT_EMPTY");
                return null;
            }
            if (bytes.length > RawPayloadEvidence.MAXIMUM_BYTES) {
                addError(redirectAttributes, "LOCAL_IMPORT_TOO_LARGE");
                return null;
            }
            return RawPayloadEvidence.capture(bytes);
        }
        catch (IOException exception) {
            addError(redirectAttributes, "LOCAL_IMPORT_READ_FAILURE");
            return null;
        }
        catch (IllegalArgumentException exception) {
            addError(redirectAttributes, "LOCAL_IMPORT_SENSITIVE_CONTENT");
            return null;
        }
    }

    private static void addResult(
            RedirectAttributes redirectAttributes,
            TournamentEventDiscoveryResult result) {
        redirectAttributes.addFlashAttribute("tournamentDiscoveryResult", result);
        if (!result.completed()) {
            addError(redirectAttributes, result.terminalCode());
            return;
        }
        String sourceDescription = switch (result.source()) {
            case CACHE -> "cache local utilisé";
            case PROVIDER -> "réponse fournisseur conservée";
            case LOCAL_JSON_IMPORT -> "JSON local importé sans réseau";
        };
        redirectAttributes.addFlashAttribute(
                "tournamentDiscoveryMessage",
                "Rencontres validées et reliées à J5. "
                        + result.providerCallAttempts() + " appel(s) fournisseur, "
                        + sourceDescription + ", "
                        + result.events().size() + " rencontre(s) canonique(s). "
                        + "Vous pouvez sélectionner un autre tournoi et lancer une nouvelle collecte.");
        redirectAttributes.addFlashAttribute("tournamentDiscoveryMessageKind", "safe");
    }

    private static void addError(
            RedirectAttributes redirectAttributes,
            String code) {
        redirectAttributes.addFlashAttribute(
                "tournamentDiscoveryMessage",
                "Découverte arrêtée sans retry : " + code);
        redirectAttributes.addFlashAttribute("tournamentDiscoveryMessageKind", "danger");
        redirectAttributes.addFlashAttribute("tournamentDiscoveryErrorCode", code);
    }
}
