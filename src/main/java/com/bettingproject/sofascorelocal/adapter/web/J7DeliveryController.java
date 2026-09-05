package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryConfirmationAction;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryConfirmationRequest;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryConfirmationReceipt;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryExecutionGate;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryPreparation;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryQueryService;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryRuntimeService;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryView;
import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7DeliveryCandidate;
import com.bettingproject.sofascorelocal.application.export.J7ExportPreview;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.security.J7DeliveryConfirmationException;
import com.bettingproject.sofascorelocal.security.J7DeliveryConfirmationService;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Local, no-store mutation boundary for manually prepared J7 delivery actions. */
@Controller
@RequestMapping("/events/{canonicalEventId}/exports/{exportId}/delivery")
public final class J7DeliveryController {

    private static final String CACHE_CONTROL_VALUE =
            "no-store, no-cache, must-revalidate, max-age=0";

    private final J7CanonicalExportService exportService;
    private final J7DeliveryQueryService queryService;
    private final J7DeliveryRuntimeService runtimeService;
    private final J7DeliveryLedgerStore ledgerStore;
    private final J7DeliveryExecutionGate executionGate;
    private final J7DeliveryConfirmationService confirmationService;
    private final LocalFormTokenService formTokenService;

    public J7DeliveryController(
            J7CanonicalExportService exportService,
            J7DeliveryQueryService queryService,
            J7DeliveryRuntimeService runtimeService,
            J7DeliveryLedgerStore ledgerStore,
            J7DeliveryExecutionGate executionGate,
            J7DeliveryConfirmationService confirmationService,
            LocalFormTokenService formTokenService) {
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.ledgerStore = Objects.requireNonNull(ledgerStore, "ledgerStore");
        this.executionGate = Objects.requireNonNull(
                executionGate, "executionGate");
        this.confirmationService = Objects.requireNonNull(
                confirmationService, "confirmationService");
        this.formTokenService = Objects.requireNonNull(
                formTokenService, "formTokenService");
    }

    @PostMapping(value = "/prepare", produces = MediaType.TEXT_HTML_VALUE)
    public String prepare(
            @PathVariable UUID canonicalEventId,
            @PathVariable UUID exportId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam(value = "unknownOutcomeReconciled", defaultValue = "false")
            boolean unknownOutcomeReconciled,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try {
            var preview = exportService.preview(canonicalEventId, exportId);
            J7DeliveryPreparation preparation = queryService.preparation(preview);
            J7DeliveryView view = preparation.view();
            if (!view.preparationAllowed()) {
                throw new J7DeliveryException(blockedPreparationError(view));
            }
            if (view.unknownOutcomeReconciliationRequired()
                    && !unknownOutcomeReconciled) {
                throw new J7DeliveryException(
                        J7DeliveryError.RECONCILIATION_NOT_ALLOWED);
            }
            J7DeliveryConfirmationRequest confirmationRequest =
                    confirmationService.prepare(
                            session.getId(),
                            J7DeliveryConfirmationAction.DELIVERY,
                            canonicalEventId,
                            exportId,
                            preview.manifest().currentContentSha256(),
                            nextDeliveryAttempt(view.ledger()),
                            preparation.providerOwnerGoReference());
            populatePreview(model, canonicalEventId, exportId, session, preview, view);
            model.addAttribute("deliveryConfirmationRequest", confirmationRequest);
            return "event-export-preview";
        }
        catch (J7DeliveryException | J7DeliveryConfirmationException
                | J7DeliveryLedgerStore.LedgerException
                | J7ExportException exception) {
            return renderError(canonicalEventId, exportId, exception, response, model);
        }
    }

    @PostMapping(value = "/execute", produces = MediaType.TEXT_HTML_VALUE)
    public String execute(
            @PathVariable UUID canonicalEventId,
            @PathVariable UUID exportId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("deliveryRequestId") UUID deliveryRequestId,
            @RequestParam("confirmationText") String confirmationText,
            @RequestParam(value = "acknowledged", defaultValue = "false") boolean acknowledged,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try {
            J7DeliveryCandidate candidate = exportService.deliveryCandidate(
                    canonicalEventId, exportId);
            OptionalInt nextAttempt = nextDeliveryAttempt(ledgerStore.find(
                    exportId, candidate.fileSha256()));
            J7DeliveryConfirmationReceipt confirmationReceipt =
                    confirmationService.consume(
                    session.getId(),
                    J7DeliveryConfirmationAction.DELIVERY,
                    canonicalEventId,
                    exportId,
                    candidate.fileSha256(),
                    nextAttempt,
                    deliveryRequestId,
                    confirmationText,
                    acknowledged,
                    Clock.systemUTC());
            runtimeService.deliver(
                    confirmationReceipt,
                    confirmationText);
            return "redirect:/events/{canonicalEventId}/exports/{exportId}";
        }
        catch (J7DeliveryException | J7DeliveryConfirmationException
                | J7DeliveryLedgerStore.LedgerException
                | J7ExportException exception) {
            return renderError(canonicalEventId, exportId, exception, response, model);
        }
    }

    @PostMapping(value = "/reconciliation/prepare", produces = MediaType.TEXT_HTML_VALUE)
    public String prepareReconciliation(
            @PathVariable UUID canonicalEventId,
            @PathVariable UUID exportId,
            @RequestParam("localFormToken") String localFormToken,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try (J7DeliveryExecutionGate.Lease ignored = executionGate.acquire()) {
            J7DeliveryCandidate candidate = exportService.deliveryCandidate(
                    canonicalEventId, exportId);
            J7DeliveryLedgerStore.DeliverySnapshot snapshot = ledgerStore.find(
                            exportId,
                            candidate.fileSha256())
                    .filter(value -> value.state()
                            == J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT)
                    .orElseThrow(() -> new J7DeliveryException(
                            J7DeliveryError.RECONCILIATION_NOT_ALLOWED));
            J7DeliveryConfirmationRequest confirmationRequest =
                    confirmationService.prepare(
                    session.getId(),
                    J7DeliveryConfirmationAction.RECONCILIATION,
                    canonicalEventId,
                    exportId,
                    candidate.fileSha256(),
                    OptionalInt.of(snapshot.attemptCount()));
            model.addAttribute("canonicalEventId", canonicalEventId);
            model.addAttribute("exportId", exportId);
            model.addAttribute("deliveryCandidate", candidate);
            model.addAttribute("deliverySnapshot", snapshot);
            model.addAttribute("localFormToken", formTokenService.issue(session));
            model.addAttribute("reconciliationConfirmationRequest", confirmationRequest);
            return "event-delivery-reconciliation";
        }
        catch (J7DeliveryException | J7DeliveryConfirmationException
                | J7DeliveryLedgerStore.LedgerException
                | J7ExportException exception) {
            return renderError(canonicalEventId, exportId, exception, response, model);
        }
    }

    @PostMapping(value = "/reconciliation/execute", produces = MediaType.TEXT_HTML_VALUE)
    public String executeReconciliation(
            @PathVariable UUID canonicalEventId,
            @PathVariable UUID exportId,
            @RequestParam("localFormToken") String localFormToken,
            @RequestParam("reconciliationRequestId") UUID reconciliationRequestId,
            @RequestParam("confirmationText") String confirmationText,
            @RequestParam(value = "acknowledged", defaultValue = "false") boolean acknowledged,
            HttpSession session,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        formTokenService.consume(session, localFormToken);
        try (J7DeliveryExecutionGate.Lease ignored = executionGate.acquire()) {
            J7DeliveryCandidate candidate = exportService.deliveryCandidate(
                    canonicalEventId, exportId);
            J7DeliveryLedgerStore.DeliverySnapshot snapshot = ledgerStore.find(
                            exportId,
                            candidate.fileSha256())
                    .filter(value -> value.state()
                            == J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT)
                    .orElseThrow(() -> new J7DeliveryException(
                            J7DeliveryError.RECONCILIATION_NOT_ALLOWED));
            confirmationService.consume(
                    session.getId(),
                    J7DeliveryConfirmationAction.RECONCILIATION,
                    canonicalEventId,
                    exportId,
                    candidate.fileSha256(),
                    OptionalInt.of(snapshot.attemptCount()),
                    reconciliationRequestId,
                    confirmationText,
                    acknowledged,
                    Clock.systemUTC());
            ledgerStore.reconcileStaleInFlightAsUnknown(
                    snapshot.deliveryId(),
                    snapshot.attemptCount(),
                    snapshot.stateChangedAt());
            return "redirect:/events/{canonicalEventId}/exports/{exportId}";
        }
        catch (J7DeliveryException | J7DeliveryConfirmationException
                | J7DeliveryLedgerStore.LedgerException
                | J7ExportException exception) {
            return renderError(canonicalEventId, exportId, exception, response, model);
        }
    }

    private void populatePreview(
            Model model,
            UUID canonicalEventId,
            UUID exportId,
            HttpSession session,
            J7ExportPreview preview,
            J7DeliveryView view) {
        model.addAttribute("canonicalEventId", canonicalEventId);
        model.addAttribute("exportId", exportId);
        model.addAttribute("exportPreview", preview);
        model.addAttribute("deliveryView", view);
        model.addAttribute("localFormToken", formTokenService.issue(session));
    }

    private static String renderError(
            UUID canonicalEventId,
            UUID exportId,
            RuntimeException exception,
            HttpServletResponse response,
            Model model) {
        response.setStatus(HttpStatus.CONFLICT.value());
        model.addAttribute("canonicalEventId", canonicalEventId);
        model.addAttribute("exportId", exportId);
        model.addAttribute("deliveryErrorCode", safeCode(exception));
        return "event-delivery-error";
    }

    private static OptionalInt nextDeliveryAttempt(
            Optional<J7DeliveryLedgerStore.DeliverySnapshot> ledger) {
        Objects.requireNonNull(ledger, "ledger");
        if (ledger.isEmpty()) {
            return OptionalInt.of(1);
        }
        J7DeliveryLedgerStore.DeliverySnapshot snapshot = ledger.orElseThrow();
        if (snapshot.state() == J7DeliveryLedgerStore.DeliveryState.NOT_ATTEMPTED) {
            return OptionalInt.of(1);
        }
        if (snapshot.state()
                != J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED
                || snapshot.attemptCount() == Integer.MAX_VALUE) {
            throw new J7DeliveryException(J7DeliveryError.RECONCILIATION_NOT_ALLOWED);
        }
        return OptionalInt.of(snapshot.attemptCount() + 1);
    }

    private static String safeCode(RuntimeException exception) {
        if (exception instanceof J7DeliveryException deliveryException) {
            return deliveryException.error().name();
        }
        if (exception instanceof J7DeliveryConfirmationException confirmationException) {
            return confirmationException.error().name();
        }
        if (exception instanceof J7ExportException exportException) {
            return "EXPORT_" + exportException.error().name();
        }
        if (exception instanceof J7DeliveryLedgerStore.LedgerException ledgerException) {
            return "LEDGER_" + ledgerException.failure().name();
        }
        return "DELIVERY_REFUSED";
    }

    private static J7DeliveryError blockedPreparationError(J7DeliveryView view) {
        if (view.policyBlockers().contains(
                J7DeliveryError.OFFICIAL_PERMISSION_EVIDENCED_INCOMPATIBLE.name())) {
            return J7DeliveryError.OFFICIAL_PERMISSION_EVIDENCED_INCOMPATIBLE;
        }
        if (view.policyBlockers().contains(
                J7DeliveryError.OFFICIAL_PERMISSION_STATUS_INVALID.name())) {
            return J7DeliveryError.OFFICIAL_PERMISSION_STATUS_INVALID;
        }
        return J7DeliveryError.DELIVERY_DISABLED;
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
}
