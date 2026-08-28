package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlError;
import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlException;
import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.application.network.J3DynamicManualCallService;
import com.bettingproject.sofascorelocal.application.network.J3LocalJsonImportError;
import com.bettingproject.sofascorelocal.application.network.J3LocalJsonImportException;
import com.bettingproject.sofascorelocal.application.network.J3LocalJsonImportService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import com.bettingproject.sofascorelocal.application.network.J3ProviderCampaignStopException;
import com.bettingproject.sofascorelocal.application.network.J3ProviderCampaignStopService;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionResult;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class ManualCallController {

    private static final String REDIRECT_DASHBOARD = "redirect:/dashboard#manual-call-control";
    private static final Pattern SIMPLE_PAGE_FILENAME = Pattern.compile(
            "(?i)^(?:page[-_ ]?)?([1-9]|1[0-9]|2[0-5])\\.json$");
    private static final Pattern SUFFIX_PAGE_FILENAME = Pattern.compile(
            "(?i)^.*[-_ ]page[-_ ]?([1-9]|1[0-9]|2[0-5])\\.json$");

    private final J3ManualCallControlService controlService;
    private final J3DynamicManualCallService dynamicManualCallService;
    private final J3LocalJsonImportService localJsonImportService;
    private final J3ManualCollectionEvidenceService collectionEvidenceService;
    private final J3ProviderCampaignStopService providerCampaignStopService;
    private final LocalFormTokenService formTokenService;

    public ManualCallController(
            J3ManualCallControlService controlService,
            J3DynamicManualCallService dynamicManualCallService,
            J3LocalJsonImportService localJsonImportService,
            J3ManualCollectionEvidenceService collectionEvidenceService,
            J3ProviderCampaignStopService providerCampaignStopService,
            LocalFormTokenService formTokenService) {
        this.controlService = controlService;
        this.dynamicManualCallService = dynamicManualCallService;
        this.localJsonImportService = localJsonImportService;
        this.collectionEvidenceService = collectionEvidenceService;
        this.providerCampaignStopService = providerCampaignStopService;
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
                "Circuit activé localement. Aucun transport n’a encore été exécuté ; préparez et confirmez la collecte avant toute action fournisseur.",
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
                "Intention de collecte préparée. Recopiez exactement la phrase affichée pour confirmer.",
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
                "Confirmation enregistrée. Aucun transport n’a été exécuté ; choisissez une seule action distincte : collecte directe ou import J3 sans réseau.",
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
            J3ManualCallExecutionResult result = dynamicManualCallService.execute(requestId);
            if (result.completed()) {
                redirectAttributes.addFlashAttribute(
                        "manualCallMessage",
                        "Collecte terminée : " + result.completedPages()
                                + " page(s) ont été résolues dans l’ordre jusqu’à hasNextPage=false ("
                                + result.providerRequests() + " transport(s) fournisseur, "
                                + result.cacheHits() + " cache hit(s) local(aux)). L’arrêt global a été réappliqué et la preuve minimisée est prête.");
                redirectAttributes.addFlashAttribute("manualCallMessageKind", "safe");
            }
            else {
                redirectAttributes.addFlashAttribute(
                        "manualCallMessage",
                        "Collecte arrêtée avant la page " + result.failedPage()
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
                    "La collecte fournisseur a été interrompue par une erreur locale sûre. L’arrêt global a été réappliqué et aucun retry n’a été lancé.");
            redirectAttributes.addFlashAttribute("manualCallMessageKind", "danger");
        }
        return REDIRECT_DASHBOARD;
    }

    @PostMapping(
            value = "/manual-call/import-json-pages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String importJsonPages(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("requestId") UUID requestId,
            @RequestParam("pageFiles") List<MultipartFile> pageFiles,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        formTokenService.consume(session, localFormToken);
        List<RawPayloadEvidence> payloads = readPageFiles(pageFiles, redirectAttributes);
        if (payloads == null) {
            return REDIRECT_DASHBOARD;
        }
        try {
            J3ManualCallExecutionResult result =
                    localJsonImportService.importPages(requestId, payloads);
            if (result.completed()) {
                redirectAttributes.addFlashAttribute(
                        "manualCallMessage",
                        "Collecte J3 importée et validée : " + result.completedPages()
                                + " page(s) JSON 1 à N, 0 appel fournisseur et 0 accès au cache. "
                                + "L’arrêt global a été réappliqué et le catalogue de tournois peut être reconstruit.");
                redirectAttributes.addFlashAttribute("manualCallMessageKind", "safe");
            }
            else {
                redirectAttributes.addFlashAttribute(
                        "manualCallMessage",
                        "Import J3 arrêté avant la page " + result.failedPage()
                                + " (" + result.terminalCode()
                                + "). Aucun appel fournisseur ni retry n’a été exécuté.");
                redirectAttributes.addFlashAttribute("manualCallMessageKind", "danger");
            }
        }
        catch (J3LocalJsonImportException exception) {
            addImportError(redirectAttributes, messageFor(exception.error()));
        }
        catch (J3ManualCallControlException exception) {
            addImportError(redirectAttributes, messageFor(exception.error()));
        }
        catch (RuntimeException exception) {
            addImportError(
                    redirectAttributes,
                    "L’import J3 a été interrompu par une erreur locale sûre. Aucun appel fournisseur n’a été exécuté.");
        }
        return REDIRECT_DASHBOARD;
    }

    @GetMapping(value = "/manual-call/evidence", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> downloadEvidence() {
        return collectionEvidenceService.latestDocument()
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
                providerCampaignStopService::stopScheduledEvents,
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
        catch (J3ProviderCampaignStopException exception) {
            redirectAttributes.addFlashAttribute(
                    "manualCallMessage",
                    "L’arrêt métier a été appliqué, mais le nettoyage du worker Playwright n’a pas pu être confirmé. Aucun nouvel appel n’est autorisé.");
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
            case PROVIDER_TRANSPORT_UNAVAILABLE -> "La collecte manuelle J3 n’est pas disponible : vérifiez la configuration fournisseur locale.";
            case INTENT_NOT_READY -> "L’intention doit être confirmée et prête avant le déclenchement.";
            case EXECUTION_ALREADY_STARTED -> "Cette collecte a déjà été déclenchée. Réarmez ensuite une nouvelle séquence explicite.";
            case EXECUTION_NOT_ACTIVE -> "Aucun lot fournisseur actif ne correspond à cette intention.";
            case PAGE_SEQUENCE_INVALID -> "La collecte ne respecte pas l’ordre dynamique des pages à partir de la page 1.";
        };
    }

    private static String messageFor(J3LocalJsonImportError error) {
        return switch (error) {
            case EMPTY_BATCH -> "Sélectionnez au moins le fichier page-1.json.";
            case TOO_MANY_PAGES -> "L’import J3 est limité aux pages 1 à 25.";
            case TOTAL_SIZE_EXCEEDED -> "Le lot JSON J3 dépasse la limite totale de 25 Mio.";
            case SCHEMA_INCOMPATIBLE -> "Une page JSON J3 est incompatible avec le schéma attendu.";
            case UNEXPECTED_CONTENT -> "Une page J3 ne contient pas un document JSON fournisseur exploitable.";
            case PAYLOAD_SHAPE_INCOMPATIBLE -> "Une page ne contient pas la liste scheduled attendue pour J3.";
            case PAGINATION_SEQUENCE_INVALID -> "Le lot doit contenir toutes les pages 1 à N : hasNextPage=true avant N, puis false à la dernière page.";
        };
    }

    private static List<RawPayloadEvidence> readPageFiles(
            List<MultipartFile> pageFiles,
            RedirectAttributes redirectAttributes) {
        if (pageFiles == null || pageFiles.isEmpty()) {
            addImportError(redirectAttributes, "Sélectionnez au moins le fichier page-1.json.");
            return null;
        }
        TreeMap<Integer, RawPayloadEvidence> payloadsByPage = new TreeMap<>();
        long totalBytes = 0;
        for (MultipartFile pageFile : pageFiles) {
            if (pageFile == null || pageFile.isEmpty()) {
                addImportError(redirectAttributes, "Chaque fichier de page doit contenir un corps JSON non vide.");
                return null;
            }
            Integer page = pageNumber(pageFile.getOriginalFilename());
            if (page == null) {
                addImportError(redirectAttributes, "Nommez les fichiers page-1.json, page-2.json, …, sans trou ni doublon.");
                return null;
            }
            if (payloadsByPage.containsKey(page)) {
                addImportError(redirectAttributes, "Le lot contient deux fichiers pour la même page J3.");
                return null;
            }
            if (pageFile.getSize() > RawPayloadEvidence.MAXIMUM_BYTES) {
                addImportError(redirectAttributes, "Une page JSON dépasse la limite de 5 Mio.");
                return null;
            }
            try {
                byte[] bytes = pageFile.getBytes();
                totalBytes += bytes.length;
                if (totalBytes > J3LocalJsonImportService.MAXIMUM_TOTAL_BYTES) {
                    addImportError(redirectAttributes, "Le lot JSON J3 dépasse la limite totale de 25 Mio.");
                    return null;
                }
                payloadsByPage.put(page, RawPayloadEvidence.capture(bytes));
            }
            catch (IOException exception) {
                addImportError(redirectAttributes, "Un fichier de page J3 n’a pas pu être lu.");
                return null;
            }
            catch (IllegalArgumentException exception) {
                addImportError(redirectAttributes, "Un fichier a été refusé car il contient des données sensibles ou dépasse les limites autorisées.");
                return null;
            }
        }
        if (payloadsByPage.size() > 25
                || payloadsByPage.firstKey() != 1
                || payloadsByPage.lastKey() != payloadsByPage.size()) {
            addImportError(redirectAttributes, "Le lot doit contenir exactement les pages contiguës 1 à N.");
            return null;
        }
        List<RawPayloadEvidence> ordered = new ArrayList<>(payloadsByPage.size());
        for (int page = 1; page <= payloadsByPage.size(); page++) {
            RawPayloadEvidence payload = payloadsByPage.get(page);
            if (payload == null) {
                addImportError(redirectAttributes, "Le lot doit contenir exactement les pages contiguës 1 à N.");
                return null;
            }
            ordered.add(payload);
        }
        return List.copyOf(ordered);
    }

    private static Integer pageNumber(String originalFilename) {
        if (originalFilename == null
                || originalFilename.isBlank()
                || originalFilename.length() > 180
                || originalFilename.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        int lastSeparator = Math.max(
                originalFilename.lastIndexOf('/'),
                originalFilename.lastIndexOf('\\'));
        String filename = originalFilename.substring(lastSeparator + 1);
        Matcher simple = SIMPLE_PAGE_FILENAME.matcher(filename);
        if (simple.matches()) {
            return Integer.parseInt(simple.group(1));
        }
        Matcher suffix = SUFFIX_PAGE_FILENAME.matcher(filename);
        if (suffix.matches()) {
            return Integer.parseInt(suffix.group(1));
        }
        return null;
    }

    private static void addImportError(
            RedirectAttributes redirectAttributes,
            String message) {
        redirectAttributes.addFlashAttribute("manualCallMessage", message);
        redirectAttributes.addFlashAttribute("manualCallMessageKind", "danger");
    }

    @FunctionalInterface
    private interface ControlAction {

        void run();
    }
}
