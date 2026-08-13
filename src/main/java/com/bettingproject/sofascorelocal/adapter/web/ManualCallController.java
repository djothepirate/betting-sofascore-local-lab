package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlError;
import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlException;
import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.application.network.J3FivePageManualCallService;
import com.bettingproject.sofascorelocal.application.network.J3QualificationEvidenceService;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionResult;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

@Controller
public class ManualCallController {

    private static final String REDIRECT_DASHBOARD = "redirect:/dashboard#manual-call-control";

    private final J3ManualCallControlService controlService;
    private final J3FivePageManualCallService fivePageManualCallService;
    private final J3QualificationEvidenceService qualificationEvidenceService;
    private final LocalFormTokenService formTokenService;

    public ManualCallController(
            J3ManualCallControlService controlService,
            J3FivePageManualCallService fivePageManualCallService,
            J3QualificationEvidenceService qualificationEvidenceService,
            LocalFormTokenService formTokenService) {
        this.controlService = controlService;
        this.fivePageManualCallService = fivePageManualCallService;
        this.qualificationEvidenceService = qualificationEvidenceService;
        this.formTokenService = formTokenService;
    }

    @PostMapping("/manual-call/rearm")
    public String rearm(
            @RequestParam("localFormToken") String localFormToken,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        return perform(
                controlService::rearmAfterGlobalStop,
                "Arrêt global levé. Le circuit reste verrouillé jusqu’à l’activation explicite.",
                redirectAttributes);
    }

    @PostMapping("/manual-call/activate")
    public String activate(
            @RequestParam("localFormToken") String localFormToken,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        return perform(
                controlService::activateByOperator,
                "Circuit activé localement. Aucun transport n’a encore été exécuté ; préparez et confirmez la reprise avant toute action fournisseur.",
                redirectAttributes);
    }

    @PostMapping("/manual-call/prepare")
    public String prepare(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        return perform(
                () -> controlService.prepare(date),
                "Intention de reprise préparée. Recopiez exactement la phrase affichée pour confirmer.",
                redirectAttributes);
    }

    @PostMapping("/manual-call/confirm")
    public String confirm(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("requestId") UUID requestId,
            @RequestParam("confirmationText") String confirmationText,
            @RequestParam(name = "acknowledged", defaultValue = "false") boolean acknowledged,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        return perform(
                () -> controlService.confirm(requestId, confirmationText, acknowledged),
                "Confirmation enregistrée. Aucun transport n’a été exécuté ; le déclenchement fournisseur reste une action distincte.",
                redirectAttributes);
    }

    @PostMapping("/manual-call/execute")
    public String execute(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("requestId") UUID requestId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        try {
            J3ManualCallExecutionResult result = fivePageManualCallService.execute(requestId);
            if (result.completed()) {
                redirectAttributes.addFlashAttribute(
                        "manualCallMessage",
                        "Reprise terminée : la page 1 conservée et les pages 2 à 5 nouvellement collectées forment le lot complet. L’arrêt global a été réappliqué et la preuve minimisée est prête.");
                redirectAttributes.addFlashAttribute("manualCallMessageKind", "safe");
            }
            else {
                redirectAttributes.addFlashAttribute(
                        "manualCallMessage",
                        "Reprise arrêtée avant la page " + result.failedPage()
                                + " (" + result.terminalCode() + "). Aucun retry n’a été lancé ; l’arrêt global est réappliqué et la preuve minimisée est prête.");
                redirectAttributes.addFlashAttribute("manualCallMessageKind", "danger");
            }
        }
        catch (J3ManualCallControlException exception) {
            redirectAttributes.addFlashAttribute(
                    "manualCallMessage",
                    messageFor(exception.error()));
            redirectAttributes.addFlashAttribute("manualCallMessageKind", "danger");
        }
        catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "manualCallMessage",
                    "Le lot fournisseur a été interrompu par une erreur locale sûre. L’arrêt global a été réappliqué et aucun retry n’a été lancé.");
            redirectAttributes.addFlashAttribute("manualCallMessageKind", "danger");
        }
        return REDIRECT_DASHBOARD;
    }

    @GetMapping(value = "/manual-call/evidence", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> downloadEvidence() {
        return qualificationEvidenceService.latestDocument()
                .map(document -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + document.filename() + "\"")
                        .body(document.reportText()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/manual-call/stop")
    public String stop(
            @RequestParam("localFormToken") String localFormToken,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        return perform(
                controlService::stopGlobally,
                "Arrêt global appliqué. Toute intention active a été annulée.",
                redirectAttributes);
    }

    private static String perform(
            ControlAction action,
            String successMessage,
            RedirectAttributes redirectAttributes) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("manualCallMessage", successMessage);
            redirectAttributes.addFlashAttribute("manualCallMessageKind", "safe");
        }
        catch (J3ManualCallControlException exception) {
            redirectAttributes.addFlashAttribute(
                    "manualCallMessage",
                    messageFor(exception.error()));
            redirectAttributes.addFlashAttribute("manualCallMessageKind", "danger");
        }
        return REDIRECT_DASHBOARD;
    }

    private static String messageFor(J3ManualCallControlError error) {
        return switch (error) {
            case GLOBAL_STOP_ALREADY_CLEARED -> "L’arrêt global est déjà levé.";
            case GLOBAL_STOP_ACTIVE -> "Levez d’abord l’arrêt global.";
            case CIRCUIT_ALREADY_ACTIVATED -> "Le circuit est déjà activé.";
            case CIRCUIT_NOT_CLOSED -> "Le circuit doit être activé et fermé avant cette action.";
            case ACTIVE_INTENT_ALREADY_EXISTS -> "Une intention active existe déjà.";
            case NO_PENDING_INTENT -> "Aucune intention en attente ne peut être confirmée.";
            case REQUEST_ID_MISMATCH -> "L’identifiant de confirmation ne correspond pas.";
            case CONFIRMATION_EXPIRED -> "La confirmation a expiré. Préparez une nouvelle intention.";
            case ACKNOWLEDGEMENT_REQUIRED -> "La case de confirmation explicite est obligatoire.";
            case CONFIRMATION_TEXT_MISMATCH -> "La phrase recopiée ne correspond pas exactement.";
            case INTENT_ALREADY_CONFIRMED -> "Cette intention a déjà été confirmée.";
            case PROVIDER_TRANSPORT_UNAVAILABLE -> "La reprise J3 n’est pas disponible : vérifiez la configuration et le checkpoint local de page 1.";
            case INTENT_NOT_READY -> "L’intention doit être confirmée et prête avant le déclenchement.";
            case EXECUTION_ALREADY_STARTED -> "Cette qualification a déjà été déclenchée.";
            case EXECUTION_NOT_ACTIVE -> "Aucun lot fournisseur actif ne correspond à cette intention.";
            case PAGE_SEQUENCE_INVALID -> "La reprise ne respecte pas l’ordre fixe des pages 2 à 5 après le checkpoint local de page 1.";
            case QUALIFICATION_ALREADY_CONSUMED -> "La reprise fournisseur unique a déjà été consommée depuis ce démarrage.";
            case DATE_NOT_AUTHORIZED -> "Seule la date de qualification 2026-08-13 est autorisée pour ce lot.";
        };
    }

    @FunctionalInterface
    private interface ControlAction {

        void run();
    }
}
