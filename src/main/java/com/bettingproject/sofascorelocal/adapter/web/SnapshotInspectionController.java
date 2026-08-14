package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotInspectionError;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotInspectionException;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspectionService;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SnapshotInspectionController {

    private final RawSnapshotJsonInspectionService inspectionService;
    private final LocalFormTokenService formTokenService;

    public SnapshotInspectionController(
            RawSnapshotJsonInspectionService inspectionService,
            LocalFormTokenService formTokenService) {
        this.inspectionService = inspectionService;
        this.formTokenService = formTokenService;
    }

    @PostMapping("/snapshot-inspection")
    public String inspect(
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("snapshotId") long snapshotId,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try {
            model.addAttribute("inspection", inspectionService.inspect(snapshotId));
        }
        catch (RawSnapshotInspectionException exception) {
            response.setStatus(statusFor(exception.error()).value());
            model.addAttribute("inspectionErrorCode", exception.error().name());
            model.addAttribute("inspectionErrorMessage", messageFor(exception.error()));
        }
        return "snapshot-inspection";
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                CacheControl.noStore().getHeaderValue());
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }

    private static HttpStatus statusFor(RawSnapshotInspectionError error) {
        return switch (error) {
            case INVALID_SELECTION -> HttpStatus.BAD_REQUEST;
            case SNAPSHOT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case LOCAL_DATABASE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case PAYLOAD_INTEGRITY_FAILURE,
                    SENSITIVE_CONTENT_BLOCKED,
                    INVALID_JSON -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    private static String messageFor(RawSnapshotInspectionError error) {
        return switch (error) {
            case INVALID_SELECTION ->
                    "La sélection locale du snapshot est invalide.";
            case SNAPSHOT_NOT_FOUND ->
                    "Le snapshot brut demandé n’est pas disponible localement.";
            case LOCAL_DATABASE_UNAVAILABLE ->
                    "La base PostgreSQL locale n’est pas disponible pour cette inspection.";
            case PAYLOAD_INTEGRITY_FAILURE ->
                    "L’inspection est bloquée : la taille ou l’empreinte du payload ne correspond pas aux métadonnées persistées.";
            case SENSITIVE_CONTENT_BLOCKED ->
                    "L’inspection est bloquée par le détecteur local de contenu sensible.";
            case INVALID_JSON ->
                    "Le snapshot sélectionné ne contient pas un document JSON strictement valide.";
        };
    }
}
