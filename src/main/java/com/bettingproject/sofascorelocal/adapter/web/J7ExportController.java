package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7ExportDownload;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryQueryService;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/events/{canonicalEventId}/exports")
public class J7ExportController {

    private static final String CACHE_CONTROL_VALUE =
            "no-store, no-cache, must-revalidate, max-age=0";

    private final J7CanonicalExportService exportService;
    private final J7DeliveryQueryService deliveryQueryService;
    private final LocalFormTokenService formTokenService;

    public J7ExportController(
            J7CanonicalExportService exportService,
            J7DeliveryQueryService deliveryQueryService,
            LocalFormTokenService formTokenService) {
        this.exportService = exportService;
        this.deliveryQueryService = deliveryQueryService;
        this.formTokenService = formTokenService;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String history(
            @PathVariable UUID canonicalEventId,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        try {
            var history = exportService.history(canonicalEventId);
            model.addAttribute("canonicalEventId", canonicalEventId);
            model.addAttribute("exportHistory", history);
            model.addAttribute(
                    "candidatePending",
                    history.exports().stream().anyMatch(export ->
                            export.status() == J7ExportStatus.COHERENCE_CHECKED));
            model.addAttribute("localFormToken", formTokenService.issue(session));
            return "event-exports";
        }
        catch (J7ExportException exception) {
            return renderError(
                    canonicalEventId, null, exception, response, model);
        }
    }

    @PostMapping(value = "/candidates", produces = MediaType.TEXT_HTML_VALUE)
    public String createCandidate(
            @PathVariable UUID canonicalEventId,
            @RequestParam("localFormToken") String localFormToken,
            HttpSession session,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try {
            var manifest = exportService.createCandidate(canonicalEventId);
            redirectAttributes.addAttribute("exportId", manifest.exportId());
            return "redirect:/events/{canonicalEventId}/exports/{exportId}";
        }
        catch (J7ExportException exception) {
            return renderError(
                    canonicalEventId, null, exception, response, model);
        }
    }

    @GetMapping(value = "/{exportId}", produces = MediaType.TEXT_HTML_VALUE)
    public String preview(
            @PathVariable UUID canonicalEventId,
            @PathVariable UUID exportId,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        try {
            var preview = exportService.preview(canonicalEventId, exportId);
            model.addAttribute("canonicalEventId", canonicalEventId);
            model.addAttribute("exportId", exportId);
            model.addAttribute("exportPreview", preview);
            model.addAttribute("deliveryView", deliveryQueryService.view(preview));
            model.addAttribute("localFormToken", formTokenService.issue(session));
            return "event-export-preview";
        }
        catch (J7ExportException exception) {
            return renderError(
                    canonicalEventId, exportId, exception, response, model);
        }
    }

    @PostMapping(value = "/{exportId}/validate", produces = MediaType.TEXT_HTML_VALUE)
    public String validate(
            @PathVariable UUID canonicalEventId,
            @PathVariable UUID exportId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("confirmationText") String confirmationText,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try {
            exportService.validate(canonicalEventId, exportId, confirmationText);
            return "redirect:/events/{canonicalEventId}/exports/{exportId}";
        }
        catch (J7ExportException exception) {
            return renderError(
                    canonicalEventId, exportId, exception, response, model);
        }
    }

    @PostMapping(value = "/{exportId}/reject", produces = MediaType.TEXT_HTML_VALUE)
    public String reject(
            @PathVariable UUID canonicalEventId,
            @PathVariable UUID exportId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("confirmationText") String confirmationText,
            @RequestParam("reason") String reason,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try {
            exportService.reject(
                    canonicalEventId, exportId, confirmationText, reason);
            return "redirect:/events/{canonicalEventId}/exports/{exportId}";
        }
        catch (J7ExportException exception) {
            return renderError(
                    canonicalEventId, exportId, exception, response, model);
        }
    }

    @GetMapping(value = "/{exportId}/download", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> download(
            @PathVariable UUID canonicalEventId,
            @PathVariable UUID exportId) {
        try {
            J7ExportDownload download = exportService.download(
                    canonicalEventId, exportId);
            HttpHeaders headers = noStoreHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentLength(download.sizeBytes());
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(download.fileName())
                    .build());
            return new ResponseEntity<>(download.content(), headers, HttpStatus.OK);
        }
        catch (J7ExportException exception) {
            HttpStatus status = exception.error() == J7ExportError.NOT_DOWNLOADABLE
                    ? nonDownloadableStatus(canonicalEventId, exportId)
                    : statusFor(exception.error());
            return new ResponseEntity<>(null, noStoreHeaders(), status);
        }
    }

    private HttpStatus nonDownloadableStatus(
            UUID canonicalEventId,
            UUID exportId) {
        try {
            J7ExportStatus status = exportService.history(canonicalEventId)
                    .exports()
                    .stream()
                    .filter(export -> export.exportId().equals(exportId))
                    .findFirst()
                    .orElseThrow(() -> new J7ExportException(
                            J7ExportError.EXPORT_NOT_FOUND))
                    .status();
            return status == J7ExportStatus.REJECTED
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.CONFLICT;
        }
        catch (J7ExportException exception) {
            return statusFor(exception.error());
        }
    }

    private static String renderError(
            UUID canonicalEventId,
            UUID exportId,
            J7ExportException exception,
            HttpServletResponse response,
            Model model) {
        HttpStatus status = statusFor(exception.error());
        response.setStatus(status.value());
        model.addAttribute("canonicalEventId", canonicalEventId);
        model.addAttribute("exportId", exportId);
        model.addAttribute("exportErrorCode", exception.error().name());
        model.addAttribute("exportErrorMessage", messageFor(exception.error()));
        return "event-export-error";
    }

    private static HttpStatus statusFor(J7ExportError error) {
        return switch (error) {
            case INVALID_CONFIRMATION,
                    INVALID_REJECTION_REASON -> HttpStatus.BAD_REQUEST;
            case EVENT_NOT_FOUND,
                    EXPORT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CANDIDATE_ALREADY_PENDING,
                    IDENTICAL_EXPORT_ALREADY_VALIDATED,
                    SOURCE_SET_CHANGED,
                    INVALID_TRANSITION,
                    NOT_DOWNLOADABLE -> HttpStatus.CONFLICT;
            case INVALID_SCHEMA,
                    SENSITIVE_CONTENT,
                    FILE_TOO_LARGE,
                    INCOHERENT_SOURCE,
                    INVALID_HASH,
                    IDENTITY_MISMATCH,
                    FILE_TAMPERED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case STORAGE_UNAVAILABLE,
                    DATABASE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static String messageFor(J7ExportError error) {
        return switch (error) {
            case EVENT_NOT_FOUND ->
                    "Cette identité canonique n’existe pas dans la base locale.";
            case EXPORT_NOT_FOUND ->
                    "Cet export J7 n’existe pas pour cette identité canonique.";
            case CANDIDATE_ALREADY_PENDING ->
                    "Un candidat J7 différent attend déjà une décision humaine.";
            case IDENTICAL_EXPORT_ALREADY_VALIDATED ->
                    "Un export validé portant les mêmes données existe déjà.";
            case INVALID_CONFIRMATION ->
                    "La phrase de confirmation ne correspond pas exactement.";
            case INVALID_REJECTION_REASON ->
                    "Le motif de rejet doit contenir entre 1 et 500 caractères sûrs.";
            case INVALID_SCHEMA ->
                    "Le document ne respecte pas le contrat JSON J7 versionné.";
            case SENSITIVE_CONTENT ->
                    "Le contrôle local a détecté un contenu sensible interdit.";
            case FILE_TOO_LARGE ->
                    "Le document dépasse la taille maximale autorisée de 5 Mio.";
            case INCOHERENT_SOURCE ->
                    "La provenance locale d’un composant est incohérente.";
            case INVALID_HASH ->
                    "Une empreinte attendue ne correspond pas au document contrôlé.";
            case IDENTITY_MISMATCH ->
                    "Le document ne correspond pas à l’identité canonique demandée.";
            case SOURCE_SET_CHANGED ->
                    "Les observations courantes ont changé. Créez un nouveau candidat avant de valider.";
            case INVALID_TRANSITION ->
                    "Cet export a déjà reçu une décision terminale incompatible.";
            case FILE_TAMPERED ->
                    "Le fichier local ne correspond plus à ses preuves persistées.";
            case NOT_DOWNLOADABLE ->
                    "Seul un export validé humainement peut être téléchargé.";
            case STORAGE_UNAVAILABLE ->
                    "Le stockage local des exports n’est pas disponible.";
            case DATABASE_UNAVAILABLE ->
                    "La base PostgreSQL locale n’est pas disponible.";
        };
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }

    private static HttpHeaders noStoreHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.EXPIRES, "0");
        headers.set("X-Robots-Tag", "noindex, nofollow, noarchive");
        return headers;
    }
}
