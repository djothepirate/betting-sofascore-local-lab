package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV6Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J5RealExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Applies the guarded single-campaign control protocol to the reusable local J5 processor. */
@Service
public class J5LocalJsonImportService {

    public static final int MAXIMUM_TOTAL_BYTES =
            J5LocalJsonImportProcessor.MAXIMUM_TOTAL_BYTES;

    private final J5RealControlService controlService;
    private final J5LocalJsonImportProcessor processor;

    @Autowired
    public J5LocalJsonImportService(
            J5RealControlService controlService,
            J5LocalJsonImportProcessor processor) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.processor = Objects.requireNonNull(processor, "processor");
    }

    /** Retained for callers that construct the single-campaign service outside Spring. */
    public J5LocalJsonImportService(
            J5RealControlService controlService,
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore) {
        this(
                controlService,
                new J5LocalJsonImportProcessor(
                        rawSnapshotStore, canonicalEventStore, eventDataStore));
    }

    J5LocalJsonImportService(
            J5RealControlService controlService,
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore,
            EventStatisticsV2Parser statisticsParser,
            EventIncidentsV6Parser incidentsParser,
            EventLineupsV2Parser lineupsParser,
            Clock clock) {
        this(
                controlService,
                new J5LocalJsonImportProcessor(
                        rawSnapshotStore,
                        canonicalEventStore,
                        eventDataStore,
                        statisticsParser,
                        incidentsParser,
                        lineupsParser,
                        clock));
    }

    public synchronized J5RealCampaignResult importCampaign(
            UUID canonicalEventId,
            UUID requestId,
            String confirmationText,
            boolean acknowledged,
            RawPayloadEvidence statisticsPayload,
            RawPayloadEvidence incidentsPayload,
            RawPayloadEvidence lineupsPayload) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        Objects.requireNonNull(requestId, "requestId");
        PrevalidatedImport prevalidated = prevalidate(
                canonicalEventId,
                requestId,
                statisticsPayload,
                incidentsPayload,
                lineupsPayload);

        J5RealExecutionClaim claim = controlService.confirmAndClaim(
                requestId, confirmationText, acknowledged);
        if (!claim.canonicalEventId().equals(canonicalEventId)
                || claim.eventId() != prevalidated.plan().eventId()) {
            return failAndLock(claim, "EVENT_ID_MISMATCH", 0, List.of());
        }
        if (prevalidated.deferredFailureCode() != null) {
            return failAndLock(
                    claim, prevalidated.deferredFailureCode(), 0, List.of());
        }
        return execute(claim, prevalidated.plan());
    }

    private PrevalidatedImport prevalidate(
            UUID canonicalEventId,
            UUID requestId,
            RawPayloadEvidence statisticsPayload,
            RawPayloadEvidence incidentsPayload,
            RawPayloadEvidence lineupsPayload) {
        RawPayloadEvidence statistics = Objects.requireNonNull(
                statisticsPayload, "statisticsPayload");
        RawPayloadEvidence incidents = Objects.requireNonNull(
                incidentsPayload, "incidentsPayload");
        RawPayloadEvidence lineups = Objects.requireNonNull(
                lineupsPayload, "lineupsPayload");
        long totalBytes = (long) statistics.sizeBytes()
                + incidents.sizeBytes()
                + lineups.sizeBytes();
        if (totalBytes > MAXIMUM_TOTAL_BYTES) {
            throw rejected(J5LocalJsonImportError.LINEUPS_PAYLOAD_INCOMPATIBLE);
        }

        J5RealControlSnapshot pending = controlService.snapshot();
        if (!pending.awaitingConfirmation()
                || pending.requestId() == null
                || pending.canonicalEventId() == null
                || pending.eventId() == null) {
            throw rejected(J5LocalJsonImportError.NO_PENDING_CAMPAIGN);
        }
        if (!pending.requestId().equals(requestId)) {
            throw rejected(J5LocalJsonImportError.REQUEST_ID_MISMATCH);
        }
        if (!pending.canonicalEventId().equals(canonicalEventId)) {
            throw rejected(J5LocalJsonImportError.CANONICAL_EVENT_MISMATCH);
        }

        long eventId = pending.eventId();
        try {
            return new PrevalidatedImport(
                    processor.prepare(
                            canonicalEventId,
                            eventId,
                            statistics,
                            incidents,
                            lineups),
                    null);
        }
        catch (J5LocalJsonImportProcessingException exception) {
            J5LocalJsonImportError inputError = inputErrorFor(exception.code());
            if (inputError != null) {
                throw rejected(inputError);
            }
            if ("EVENT_ID_MISMATCH".equals(exception.code())) {
                return new PrevalidatedImport(
                        new J5LocalJsonImportProcessingPlan(
                                canonicalEventId,
                                eventId,
                                statistics,
                                incidents,
                                lineups),
                        exception.code());
            }
            throw exception;
        }
    }

    private J5RealCampaignResult execute(
            J5RealExecutionClaim claim,
            J5LocalJsonImportProcessingPlan prepared) {
        J5LocalJsonImportProcessingResult processed;
        try {
            processed = processor.execute(
                    prepared,
                    () -> controlService.executionMayContinue(claim.requestId()),
                    endpoint -> controlService.recordEndpointCompleted(
                            claim.requestId(), endpoint));
        }
        catch (J5LocalJsonImportProcessingException exception) {
            String code = "PROGRESS_CALLBACK_ERROR".equals(exception.code())
                    ? "OPERATOR_STOP"
                    : exception.code();
            if ("OPERATOR_STOP".equals(code)) {
                return failed(
                        claim,
                        code,
                        exception.localJsonImports(),
                        exception.endpoints());
            }
            return failAndLock(
                    claim,
                    code,
                    exception.localJsonImports(),
                    exception.endpoints());
        }

        try {
            controlService.complete(claim.requestId());
        }
        catch (J5RealControlException exception) {
            return failed(
                    claim,
                    "OPERATOR_STOP",
                    processed.localJsonImports(),
                    processed.endpoints());
        }
        return new J5RealCampaignResult(
                claim.requestId(),
                claim.canonicalEventId(),
                claim.eventId(),
                true,
                "COMPLETED",
                0,
                processed.localJsonImports(),
                processed.endpoints());
    }

    private J5RealCampaignResult failAndLock(
            J5RealExecutionClaim claim,
            String code,
            int imports,
            List<J5RealEndpointResult> results) {
        if (controlService.executionMayContinue(claim.requestId())) {
            try {
                controlService.fail(claim.requestId(), code);
            }
            catch (J5RealControlException exception) {
                return failed(claim, "OPERATOR_STOP", imports, results);
            }
        }
        return failed(claim, code, imports, results);
    }

    private static J5RealCampaignResult failed(
            J5RealExecutionClaim claim,
            String code,
            int imports,
            List<J5RealEndpointResult> results) {
        return new J5RealCampaignResult(
                claim.requestId(),
                claim.canonicalEventId(),
                claim.eventId(),
                false,
                code,
                0,
                imports,
                results);
    }

    private static J5LocalJsonImportError inputErrorFor(String code) {
        return switch (code) {
            case "TOTAL_PAYLOAD_TOO_LARGE" ->
                    J5LocalJsonImportError.LINEUPS_PAYLOAD_INCOMPATIBLE;
            case "STATISTICS_PAYLOAD_INCOMPATIBLE" ->
                    J5LocalJsonImportError.STATISTICS_PAYLOAD_INCOMPATIBLE;
            case "INCIDENTS_PAYLOAD_INCOMPATIBLE" ->
                    J5LocalJsonImportError.INCIDENTS_PAYLOAD_INCOMPATIBLE;
            case "LINEUPS_PAYLOAD_INCOMPATIBLE" ->
                    J5LocalJsonImportError.LINEUPS_PAYLOAD_INCOMPATIBLE;
            default -> null;
        };
    }

    private static J5LocalJsonImportException rejected(
            J5LocalJsonImportError error) {
        return new J5LocalJsonImportException(error);
    }

    private record PrevalidatedImport(
            J5LocalJsonImportProcessingPlan plan,
            String deferredFailureCode) {

        private PrevalidatedImport {
            Objects.requireNonNull(plan, "plan");
        }
    }
}
