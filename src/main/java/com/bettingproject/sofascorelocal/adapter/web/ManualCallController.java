package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlError;
import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlException;
import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

@Controller
public class ManualCallController {

    private static final String REDIRECT_DASHBOARD = "redirect:/dashboard#manual-call-control";

    private final J3ManualCallControlService controlService;
    private final LocalFormTokenService formTokenService;

    public ManualCallController(
            J3ManualCallControlService controlService,
            LocalFormTokenService formTokenService) {
        this.controlService = controlService;
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
                "Circuit activé localement. Aucun transport fournisseur n’est autorisé.",
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
                "Intention préparée. Recopiez exactement la phrase affichée pour confirmer.",
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
                "Confirmation enregistrée. L’appel réel reste bloqué et aucun transport n’a été exécuté.",
                redirectAttributes);
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
        };
    }

    @FunctionalInterface
    private interface ControlAction {

        void run();
    }
}
