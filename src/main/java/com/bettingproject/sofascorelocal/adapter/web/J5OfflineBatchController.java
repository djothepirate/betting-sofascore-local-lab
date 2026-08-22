package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchControlService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchError;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchException;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchPlanService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchResult;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchState;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchUpload;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchUploadService;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/j5-import-batches")
public class J5OfflineBatchController {

    private static final String CACHE_CONTROL_VALUE =
            "no-store, no-cache, must-revalidate, max-age=0";
    private static final Pattern BATCH_FILE_DISPOSITION = Pattern.compile(
            "\\Aform-data; name=\"batchFiles\"; filename=\""
                    + "(event-[1-9][0-9]{0,18}-(?:statistics|incidents|lineups)\\.json)"
                    + "\"\\z");
    private static final Pattern EMPTY_BATCH_FILE_DISPOSITION = Pattern.compile(
            "\\Aform-data; name=\"batchFiles\"(?:; filename=\"\")?\\z");
    private static final String UNAVAILABLE_404_CONTROL = "unavailable404";
    private static final String UNAVAILABLE_404_DISPOSITION =
            "form-data; name=\"unavailable404\"";
    private static final int MAXIMUM_CONTROL_PART_BYTES = 512;

    private final J4EventQueryService eventQueryService;
    private final J5OfflineBatchControlService controlService;
    private final J5OfflineBatchImportService importService;
    private final J5OfflineBatchUploadService uploadService;
    private final LocalFormTokenService formTokenService;

    public J5OfflineBatchController(
            J4EventQueryService eventQueryService,
            J5OfflineBatchControlService controlService,
            J5OfflineBatchImportService importService,
            J5OfflineBatchUploadService uploadService,
            LocalFormTokenService formTokenService) {
        this.eventQueryService = eventQueryService;
        this.controlService = controlService;
        this.importService = importService;
        this.uploadService = uploadService;
        this.formTokenService = formTokenService;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String batches(
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        LocalDate selectedDate = date == null
                ? LocalDate.now(ZoneId.of(J4EventQueryService.DEFAULT_ZONE_ID))
                : date;
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("selectedZone", zone);
        model.addAttribute("control", controlService.snapshot());
        model.addAttribute("localFormToken", formTokenService.issue(session));
        model.addAttribute("maximumEvents", J5OfflineBatchPlanService.MAXIMUM_EVENTS);
        model.addAttribute("maximumFiles", J5OfflineBatchUploadService.MAXIMUM_FILES);
        model.addAttribute("maximumFileBytes", RawPayloadEvidence.MAXIMUM_BYTES);
        model.addAttribute(
                "maximumTotalBytes", J5OfflineBatchUploadService.MAXIMUM_TOTAL_BYTES);
        model.addAttribute(
                "maximumTotalMebibytes",
                J5OfflineBatchUploadService.MAXIMUM_TOTAL_BYTES / 1024 / 1024);
        try {
            model.addAttribute("search", eventQueryService.search(selectedDate, zone));
        }
        catch (IllegalArgumentException exception) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("batchErrorCode", "INVALID_DATE_OR_ZONE");
            model.addAttribute("batchMessage", messageForInvalidDateOrZone());
            model.addAttribute("batchMessageKind", "danger");
        }
        catch (DataAccessException exception) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            model.addAttribute("batchErrorCode", "STORAGE_UNAVAILABLE");
            model.addAttribute(
                    "batchMessage",
                    messageFor(J5OfflineBatchError.STORAGE_UNAVAILABLE));
            model.addAttribute("batchMessageKind", "danger");
        }
        return "j5-offline-batches";
    }

    @PostMapping(
            value = "/prepare",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public String prepare(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("date") LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            @RequestParam(name = "eventIds", required = false) List<UUID> eventIds,
            HttpSession session,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try {
            requireBoundedSelection(eventIds);
            var prepared = controlService.prepare(date, zone, eventIds);
            redirectAttributes.addFlashAttribute(
                    "batchMessage",
                    "Plan local J5 préparé pour " + prepared.plan().events().size()
                            + " rencontre(s). Contrôlez les noms puis recopiez la phrase exacte.");
            redirectAttributes.addFlashAttribute("batchMessageKind", "safe");
        }
        catch (J5OfflineBatchException exception) {
            addError(redirectAttributes, exception.error());
        }
        catch (IllegalArgumentException exception) {
            addInvalidDateOrZoneError(redirectAttributes);
        }
        catch (DataAccessException exception) {
            addError(redirectAttributes, J5OfflineBatchError.STORAGE_UNAVAILABLE);
        }
        addRedirectCoordinates(date, zone, redirectAttributes);
        return "redirect:/j5-import-batches";
    }

    @PostMapping(
            value = "/execute",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public String execute(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("requestId") UUID requestId,
            @RequestParam("confirmationText") String confirmationText,
            @RequestParam(name = "acknowledged", defaultValue = "false")
                    boolean acknowledged,
            @RequestParam("date") LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpServletRequest request,
            HttpSession session,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try {
            var pending = controlService.requireReadyForUpload(requestId);
            ValidatedBatchRequest validated = preflight(
                    request,
                    localFormToken,
                    requestId,
                    confirmationText,
                    acknowledged,
                    date,
                    zone);
            uploadService.validateManifest(
                    pending, validated.evidenceNames());
            List<J5OfflineBatchUpload> uploads = capture(validated);
            J5OfflineBatchResult result = importService.execute(
                    requestId,
                    confirmationText,
                    acknowledged,
                    uploads);
            redirectAttributes.addFlashAttribute("batchResult", result);
            if (result.completed()) {
                redirectAttributes.addFlashAttribute(
                        "batchMessage",
                        "Lot J5 importé localement : " + result.eventCount()
                                + " rencontre(s), " + result.localJsonImports()
                                + " preuves JSON locales dont "
                                + validated.unavailable404Names().size()
                                + " déclaration(s) 404, zéro appel fournisseur.");
                redirectAttributes.addFlashAttribute("batchMessageKind", "safe");
            }
            else {
                addResultError(redirectAttributes, result.terminalCode());
            }
        }
        catch (J5OfflineBatchException exception) {
            addError(redirectAttributes, exception.error());
        }
        catch (IOException exception) {
            addError(redirectAttributes, J5OfflineBatchError.LOCAL_EXECUTION_FAILURE);
        }
        catch (DataAccessException exception) {
            addError(redirectAttributes, J5OfflineBatchError.STORAGE_UNAVAILABLE);
        }
        addRedirectCoordinates(date, zone, redirectAttributes);
        return "redirect:/j5-import-batches";
    }

    @PostMapping(
            value = "/stop",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public String stop(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("requestId") UUID requestId,
            @RequestParam("date") LocalDate date,
            @RequestParam(
                    name = "zone",
                    defaultValue = J4EventQueryService.DEFAULT_ZONE_ID) String zone,
            HttpSession session,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try {
            var stopped = controlService.stop(requestId);
            if (stopped.state() == J5OfflineBatchState.STOPPED_LOCKED) {
                redirectAttributes.addFlashAttribute(
                        "batchMessage",
                        "Plan J5 annulé avant import. Aucun corps JSON n’a été persisté.");
                redirectAttributes.addFlashAttribute("batchMessageKind", "safe");
            }
            else {
                redirectAttributes.addFlashAttribute(
                        "batchMessage",
                        "Aucun plan J5 en attente ne pouvait être annulé.");
                redirectAttributes.addFlashAttribute("batchMessageKind", "warning");
            }
        }
        catch (J5OfflineBatchException exception) {
            addError(redirectAttributes, exception.error());
        }
        addRedirectCoordinates(date, zone, redirectAttributes);
        return "redirect:/j5-import-batches";
    }

    private static void requireBoundedSelection(List<UUID> eventIds) {
        if (eventIds == null
                || eventIds.isEmpty()
                || eventIds.size() > J5OfflineBatchPlanService.MAXIMUM_EVENTS) {
            throw new J5OfflineBatchException(J5OfflineBatchError.INVALID_SELECTION);
        }
    }

    private static ValidatedBatchRequest preflight(
            HttpServletRequest request,
            String localFormToken,
            UUID requestId,
            String confirmationText,
            boolean acknowledged,
            LocalDate date,
            String zone) {
        Map<String, String> expectedControls = Map.of(
                "localFormToken", localFormToken,
                "requestId", requestId.toString(),
                "confirmationText", confirmationText,
                "acknowledged", Boolean.toString(acknowledged),
                "date", date.toString(),
                "zone", zone);
        List<Part> parts;
        try {
            parts = List.copyOf(request.getParts());
        }
        catch (IOException | ServletException exception) {
            throw new J5OfflineBatchException(
                    J5OfflineBatchError.MULTIPART_LIMIT_EXCEEDED, exception);
        }

        List<ValidatedBatchPart> files = new ArrayList<>();
        List<String> unavailable404Names = new ArrayList<>();
        Set<String> receivedControls = new HashSet<>();
        long totalBytes = 0;
        boolean emptyFileSentinelSeen = false;
        for (Part part : parts) {
            if (part == null) {
                throw new J5OfflineBatchException(J5OfflineBatchError.FILE_SET_MISMATCH);
            }
            if (UNAVAILABLE_404_CONTROL.equals(part.getName())) {
                unavailable404Names.add(validateUnavailable404Part(part));
                continue;
            }
            if (!"batchFiles".equals(part.getName())) {
                validateControlPart(part, expectedControls, receivedControls);
                continue;
            }
            if (part.getSize() == 0) {
                if (isEmptyFileSentinel(part) && !emptyFileSentinelSeen) {
                    emptyFileSentinelSeen = true;
                    continue;
                }
                throw new J5OfflineBatchException(J5OfflineBatchError.EMPTY_PAYLOAD);
            }
            String fileName = requireCanonicalFileDisposition(part);
            long size = part.getSize();
            if (size > RawPayloadEvidence.MAXIMUM_BYTES) {
                throw new J5OfflineBatchException(J5OfflineBatchError.PAYLOAD_TOO_LARGE);
            }
            totalBytes += size;
            if (totalBytes > J5OfflineBatchUploadService.MAXIMUM_TOTAL_BYTES) {
                throw new J5OfflineBatchException(J5OfflineBatchError.BATCH_TOO_LARGE);
            }
            files.add(new ValidatedBatchPart(part, fileName));
        }
        if (!receivedControls.equals(expectedControls.keySet())
                || files.size() + unavailable404Names.size()
                        > J5OfflineBatchUploadService.MAXIMUM_FILES) {
            throw new J5OfflineBatchException(J5OfflineBatchError.FILE_SET_MISMATCH);
        }
        return new ValidatedBatchRequest(files, unavailable404Names);
    }

    private static void validateControlPart(
            Part part,
            Map<String, String> expectedControls,
            Set<String> receivedControls) {
        String name = part.getName();
        String expected = expectedControls.get(name);
        if (expected == null
                || part.getSubmittedFileName() != null
                || !receivedControls.add(name)
                || !("form-data; name=\"" + name + "\"").equals(
                        part.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                || part.getSize() < 0
                || part.getSize() > MAXIMUM_CONTROL_PART_BYTES) {
            throw new J5OfflineBatchException(J5OfflineBatchError.FILE_SET_MISMATCH);
        }
        byte[] actual;
        try (InputStream input = part.getInputStream()) {
            actual = input.readNBytes(MAXIMUM_CONTROL_PART_BYTES + 1);
        }
        catch (IOException exception) {
            throw new J5OfflineBatchException(
                    J5OfflineBatchError.LOCAL_EXECUTION_FAILURE, exception);
        }
        if (!Arrays.equals(actual, expected.getBytes(StandardCharsets.UTF_8))) {
            throw new J5OfflineBatchException(J5OfflineBatchError.FILE_SET_MISMATCH);
        }
    }

    private static String validateUnavailable404Part(Part part) {
        if (part.getSubmittedFileName() != null
                || !UNAVAILABLE_404_DISPOSITION.equals(
                        part.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                || part.getSize() < 1
                || part.getSize() > MAXIMUM_CONTROL_PART_BYTES) {
            throw new J5OfflineBatchException(J5OfflineBatchError.FILE_SET_MISMATCH);
        }
        byte[] value;
        try (InputStream input = part.getInputStream()) {
            value = input.readNBytes(MAXIMUM_CONTROL_PART_BYTES + 1);
        }
        catch (IOException exception) {
            throw new J5OfflineBatchException(
                    J5OfflineBatchError.LOCAL_EXECUTION_FAILURE, exception);
        }
        String fileName = new String(value, StandardCharsets.UTF_8);
        if (!Arrays.equals(value, fileName.getBytes(StandardCharsets.UTF_8))) {
            throw new J5OfflineBatchException(J5OfflineBatchError.FILE_NAME_INVALID);
        }
        return fileName;
    }

    private List<J5OfflineBatchUpload> capture(ValidatedBatchRequest validated)
            throws IOException {
        List<J5OfflineBatchUpload> uploads = new ArrayList<>(validated.evidenceNames().size());
        long totalBytes = 0;
        for (ValidatedBatchPart file : validated.files()) {
            byte[] content;
            try (InputStream input = file.part().getInputStream()) {
                content = input.readNBytes(RawPayloadEvidence.MAXIMUM_BYTES + 1);
            }
            if (content.length > RawPayloadEvidence.MAXIMUM_BYTES) {
                throw new J5OfflineBatchException(J5OfflineBatchError.PAYLOAD_TOO_LARGE);
            }
            totalBytes += content.length;
            if (totalBytes > J5OfflineBatchUploadService.MAXIMUM_TOTAL_BYTES) {
                throw new J5OfflineBatchException(J5OfflineBatchError.BATCH_TOO_LARGE);
            }
            uploads.add(uploadService.capture(
                    file.fileName(),
                    content));
        }
        for (String fileName : validated.unavailable404Names()) {
            uploads.add(uploadService.declareUnavailable404(fileName));
        }
        return List.copyOf(uploads);
    }

    private static boolean isEmptyFileSentinel(Part part) {
        String submittedFileName = part.getSubmittedFileName();
        String disposition = part.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        return "batchFiles".equals(part.getName())
                && (submittedFileName == null || submittedFileName.isEmpty())
                && disposition != null
                && EMPTY_BATCH_FILE_DISPOSITION.matcher(disposition).matches();
    }

    private static String requireCanonicalFileDisposition(Part file) {
        String disposition = file.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        Matcher matcher = disposition == null
                ? null
                : BATCH_FILE_DISPOSITION.matcher(disposition);
        if (matcher == null
                || !matcher.matches()
                || !"batchFiles".equals(file.getName())
                || !matcher.group(1).equals(file.getSubmittedFileName())) {
            throw new J5OfflineBatchException(J5OfflineBatchError.FILE_NAME_INVALID);
        }
        return matcher.group(1);
    }

    private record ValidatedBatchPart(Part part, String fileName) { }

    private record ValidatedBatchRequest(
            List<ValidatedBatchPart> files,
            List<String> unavailable404Names) {

        private ValidatedBatchRequest {
            files = List.copyOf(files);
            unavailable404Names = List.copyOf(unavailable404Names);
        }

        private List<String> evidenceNames() {
            List<String> names = new ArrayList<>(files.size() + unavailable404Names.size());
            names.addAll(files.stream().map(ValidatedBatchPart::fileName).toList());
            names.addAll(unavailable404Names);
            return List.copyOf(names);
        }
    }

    private static void addRedirectCoordinates(
            LocalDate date,
            String zone,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("date", date.toString());
        redirectAttributes.addAttribute("zone", zone);
    }

    private static void addInvalidDateOrZoneError(
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("batchErrorCode", "INVALID_DATE_OR_ZONE");
        redirectAttributes.addFlashAttribute("batchMessage", messageForInvalidDateOrZone());
        redirectAttributes.addFlashAttribute("batchMessageKind", "danger");
    }

    private static void addError(
            RedirectAttributes redirectAttributes,
            J5OfflineBatchError error) {
        redirectAttributes.addFlashAttribute("batchErrorCode", error.name());
        redirectAttributes.addFlashAttribute("batchMessage", messageFor(error));
        redirectAttributes.addFlashAttribute("batchMessageKind", "danger");
    }

    private static void addResultError(
            RedirectAttributes redirectAttributes,
            String terminalCode) {
        redirectAttributes.addFlashAttribute("batchErrorCode", terminalCode);
        redirectAttributes.addFlashAttribute(
                "batchMessage",
                "Le lot J5 s’est arrêté sans retry. Consultez le résultat minimisé.");
        redirectAttributes.addFlashAttribute("batchMessageKind", "danger");
    }

    private static String messageForInvalidDateOrZone() {
        return "Date ou zone invalide. Utilisez une date ISO et une zone IANA reconnue.";
    }

    private static String messageFor(J5OfflineBatchError error) {
        return switch (error) {
            case OFFLINE_POLICY_UNAVAILABLE ->
                    "L’import local multi-match est verrouillé par la politique hors ligne.";
            case INVALID_SELECTION ->
                    "Sélectionnez entre 1 et 25 rencontres distinctes dans la liste locale.";
            case EVENT_NOT_FOUND ->
                    "Une rencontre sélectionnée n’appartient plus au résultat local courant.";
            case ACTIVE_BATCH_EXISTS ->
                    "Un lot J5 attend déjà une décision ou une exécution.";
            case NO_PENDING_BATCH ->
                    "Aucun lot J5 en attente ne correspond à cette action.";
            case REQUEST_ID_MISMATCH ->
                    "L’identifiant du lot ne correspond pas au plan affiché.";
            case CONFIRMATION_EXPIRED ->
                    "Le plan J5 a expiré. Préparez un nouveau lot à partir des données courantes.";
            case ACKNOWLEDGEMENT_REQUIRED ->
                    "L’acquittement explicite du lot est obligatoire.";
            case CONFIRMATION_TEXT_MISMATCH ->
                    "La phrase de confirmation ne correspond pas exactement au plan.";
            case PLAN_CHANGED ->
                    "Une identité locale a changé depuis l’aperçu. Préparez un nouveau lot.";
            case FILE_NAME_INVALID ->
                    "Un nom de fichier ne respecte pas la convention J5 affichée.";
            case DUPLICATE_FILE ->
                    "La même preuve J5 apparaît plusieurs fois ou combine fichier et déclaration 404.";
            case FILE_SET_MISMATCH ->
                    "Le dépôt doit fournir exactement une preuve par famille : fichier JSON ou déclaration 404.";
            case EMPTY_PAYLOAD ->
                    "Un fichier JSON sélectionné est vide.";
            case PAYLOAD_TOO_LARGE ->
                    "Un fichier JSON dépasse la limite de 5 Mio.";
            case BATCH_TOO_LARGE ->
                    "Le lot JSON dépasse la limite cumulée de 25 Mio.";
            case MULTIPART_LIMIT_EXCEEDED ->
                    "Le dépôt dépasse une limite multipart. Vérifiez 5 Mio maximum par fichier "
                            + "et 25 Mio maximum pour le lot.";
            case SENSITIVE_CONTENT ->
                    "Un contenu sensible interdit a été détecté dans le lot.";
            case STATISTICS_PAYLOAD_INCOMPATIBLE ->
                    "Un corps de statistiques est incompatible avec le parseur local.";
            case INCIDENTS_PAYLOAD_INCOMPATIBLE ->
                    "Un corps d’incidents est incompatible avec le parseur local.";
            case LINEUPS_PAYLOAD_INCOMPATIBLE ->
                    "Un corps de compositions est incompatible avec le parseur local.";
            case OPERATOR_STOP ->
                    "Le plan a été annulé par l’opérateur avant l’import.";
            case STORAGE_UNAVAILABLE ->
                    "La base ou le stockage PostgreSQL local n’est pas disponible.";
            case LOCAL_EXECUTION_FAILURE ->
                    "L’exécution locale a échoué et le lot reste verrouillé sans retry.";
        };
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
}
