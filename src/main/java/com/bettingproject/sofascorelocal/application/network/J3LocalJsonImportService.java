package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionResult;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedCollectionEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class J3LocalJsonImportService {

    public static final int MAXIMUM_TOTAL_BYTES = 25 * 1024 * 1024;

    private static final String CONTENT_TYPE = "application/json";
    private static final String PROCESSING_FAILURE = "LOCAL_IMPORT_PROCESSING_FAILURE";
    private static final String STOPPED = "GLOBAL_STOP_OR_CIRCUIT_BLOCK";

    private final J3ManualCallControlService controlService;
    private final J3ScheduledEventsOutcomeProcessor outcomeProcessor;
    private final ScheduledEventsV1Parser parser;
    private final J3SingleCallGuard callGuard;
    private final J3ManualCollectionEvidenceService evidenceService;
    private final Clock clock;
    private final Duration cacheTtl;

    @Autowired
    public J3LocalJsonImportService(
            J3ManualCallControlService controlService,
            RawManualCallSnapshotStore snapshotStore,
            J3SingleCallGuard callGuard,
            J3ManualCollectionEvidenceService evidenceService,
            SofascoreEndpointCatalog endpointCatalog) {
        this(
                controlService,
                new J3ScheduledEventsOutcomeProcessor(
                        snapshotStore,
                        new ScheduledEventsV1Parser(),
                        controlService.circuit()),
                new ScheduledEventsV1Parser(),
                callGuard,
                evidenceService,
                Clock.systemUTC(),
                endpointCatalog.get(SofascoreEndpointType.SCHEDULED_EVENTS).cacheTtl());
    }

    J3LocalJsonImportService(
            J3ManualCallControlService controlService,
            J3ScheduledEventsOutcomeProcessor outcomeProcessor,
            ScheduledEventsV1Parser parser,
            J3SingleCallGuard callGuard,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            Duration cacheTtl) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.outcomeProcessor = Objects.requireNonNull(outcomeProcessor, "outcomeProcessor");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.callGuard = Objects.requireNonNull(callGuard, "callGuard");
        this.evidenceService = Objects.requireNonNull(evidenceService, "evidenceService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl");
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("cacheTtl must be positive");
        }
    }

    public J3ManualCallExecutionResult importPages(
            UUID requestId,
            List<RawPayloadEvidence> pagePayloads) {
        Objects.requireNonNull(requestId, "requestId");
        List<RawPayloadEvidence> validatedPayloads = validateBatch(pagePayloads);

        var permit = callGuard.tryAcquire();
        if (permit.isEmpty()) {
            throw new J3ManualCallControlException(
                    J3ManualCallControlError.EXECUTION_ALREADY_STARTED);
        }

        List<J3MinimizedPageEvidence> attempts = new ArrayList<>();
        int completedPages = 0;
        J3ManualCallExecutionClaim claim = null;
        try (J3SingleCallGuard.Permit ignored = permit.orElseThrow()) {
            claim = controlService.claimExecution(requestId);
            for (int index = 0; index < validatedPayloads.size(); index++) {
                int page = index + 1;
                if (!controlService.executionMayContinue(requestId)) {
                    return publishAlreadyLockedFailure(
                            claim.date(), completedPages, page, STOPPED, attempts);
                }

                Instant importedAt = clock.instant();
                ScheduledEventsTransportResponse response = new ScheduledEventsTransportResponse(
                        requestKey(claim.date(), page),
                        importedAt,
                        importedAt,
                        200,
                        CONTENT_TYPE,
                        Duration.ZERO,
                        validatedPayloads.get(index));
                J3ScheduledEventsOutcome outcome =
                        outcomeProcessor.processImportedResponse(response);
                String terminalCode = outcome.circuit().state() == J3CircuitState.CLOSED
                        ? null
                        : outcome.circuit().reason().name();
                attempts.add(J3MinimizedPageEvidence.imported(
                        page,
                        response,
                        outcome.persistence().orElseThrow(),
                        schemaStatus(outcome),
                        outcome.hasNextPage().orElse(null),
                        terminalCode));

                if (!controlService.executionMayContinue(requestId)
                        || outcome.circuit().state() != J3CircuitState.CLOSED) {
                    J3ManualCallControlSnapshot snapshot = controlService.snapshot();
                    if (snapshot.intent() != null
                            && snapshot.intent().state() == J3ManualCallIntentState.EXECUTING) {
                        String code = terminalCode == null ? STOPPED : terminalCode;
                        controlService.failExecution(requestId, page, code);
                        return publishAndLock(
                                requestId,
                                claim.date(),
                                J3ManualCallExecutionResult.failedLocalImport(
                                        completedPages, page, code, attempts.size()),
                                attempts);
                    }
                    return publishAlreadyLockedFailure(
                            claim.date(), completedPages, page, STOPPED, attempts);
                }

                controlService.recordPageCompleted(requestId, page);
                completedPages = page;
            }

            controlService.completeExecution(requestId);
            return publishAndLock(
                    requestId,
                    claim.date(),
                    J3ManualCallExecutionResult.successfulLocalImport(completedPages),
                    attempts);
        }
        catch (J3ManualCallControlException | J3LocalJsonImportException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            if (claim == null) {
                throw exception;
            }
            J3ManualCallControlSnapshot snapshot = controlService.snapshot();
            if (snapshot.intent() != null
                    && snapshot.intent().state() == J3ManualCallIntentState.EXECUTING) {
                int failedPage = completedPages + 1;
                controlService.failExecution(requestId, failedPage, PROCESSING_FAILURE);
                return publishAndLock(
                        requestId,
                        claim.date(),
                        J3ManualCallExecutionResult.failedLocalImport(
                                completedPages,
                                failedPage,
                                PROCESSING_FAILURE,
                                attempts.size()),
                        attempts);
            }
            if (!snapshot.globalStopActive()) {
                controlService.stopGlobally();
            }
            throw exception;
        }
    }

    private List<RawPayloadEvidence> validateBatch(
            List<RawPayloadEvidence> pagePayloads) {
        if (pagePayloads == null || pagePayloads.isEmpty()) {
            throw rejected(J3LocalJsonImportError.EMPTY_BATCH);
        }
        if (pagePayloads.size()
                > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
            throw rejected(J3LocalJsonImportError.TOO_MANY_PAGES);
        }
        List<RawPayloadEvidence> payloads = List.copyOf(pagePayloads);
        long totalBytes = 0;
        for (int index = 0; index < payloads.size(); index++) {
            RawPayloadEvidence payload = Objects.requireNonNull(
                    payloads.get(index), "pagePayload");
            totalBytes += payload.sizeBytes();
            if (totalBytes > MAXIMUM_TOTAL_BYTES) {
                throw rejected(J3LocalJsonImportError.TOTAL_SIZE_EXCEEDED);
            }

            int page = index + 1;
            Instant inspectedAt = Instant.EPOCH.plusSeconds(page);
            var parsing = parser.parseTransportResponse(new ScheduledEventsTransportResponse(
                    requestKey(LocalDate.of(2000, 1, 1), page),
                    inspectedAt,
                    inspectedAt,
                    200,
                    CONTENT_TYPE,
                    Duration.ZERO,
                    payload));
            if (parsing.status() == ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE) {
                throw rejected(J3LocalJsonImportError.SCHEMA_INCOMPATIBLE);
            }
            if (parsing.status() != ScheduledEventsParseStatus.PARSED) {
                throw rejected(J3LocalJsonImportError.UNEXPECTED_CONTENT);
            }
            ScheduledEventsPage parsedPage = parsing.page().orElseThrow();
            if (parsedPage.payloadShape()
                    != ScheduledEventsPage.PayloadShape.SCHEDULED_TOURNAMENT_LIST) {
                throw rejected(J3LocalJsonImportError.PAYLOAD_SHAPE_INCOMPATIBLE);
            }
            boolean expectedHasNextPage = index < payloads.size() - 1;
            if (parsedPage.hasNextPage() != expectedHasNextPage) {
                throw rejected(J3LocalJsonImportError.PAGINATION_SEQUENCE_INVALID);
            }
        }
        return payloads;
    }

    private J3ManualCallExecutionResult publishAndLock(
            UUID requestId,
            LocalDate collectionDate,
            J3ManualCallExecutionResult result,
            List<J3MinimizedPageEvidence> attempts) {
        J3ManualCallControlSnapshot locked = controlService.lockAfterCollection(requestId);
        publishEvidence(collectionDate, result, attempts, locked);
        return result;
    }

    private J3ManualCallExecutionResult publishAlreadyLockedFailure(
            LocalDate collectionDate,
            int completedPages,
            int failedPage,
            String terminalCode,
            List<J3MinimizedPageEvidence> attempts) {
        J3ManualCallExecutionResult result = J3ManualCallExecutionResult.failedLocalImport(
                completedPages, failedPage, terminalCode, attempts.size());
        publishEvidence(collectionDate, result, attempts, controlService.snapshot());
        return result;
    }

    private void publishEvidence(
            LocalDate collectionDate,
            J3ManualCallExecutionResult result,
            List<J3MinimizedPageEvidence> attempts,
            J3ManualCallControlSnapshot terminalSnapshot) {
        evidenceService.publish(new J3MinimizedCollectionEvidence(
                collectionDate,
                terminalSnapshot.intent().state(),
                0,
                result.completedPages(),
                result.failedPage(),
                result.completed() ? "NONE" : result.terminalCode(),
                clock.instant(),
                terminalSnapshot.globalStopActive(),
                terminalSnapshot.circuitState(),
                terminalSnapshot.circuitReason(),
                cacheTtl,
                attempts));
    }

    private static RawSnapshotSchemaStatus schemaStatus(
            J3ScheduledEventsOutcome outcome) {
        if (outcome.circuit().state() == J3CircuitState.CLOSED) {
            return RawSnapshotSchemaStatus.PARSED;
        }
        return switch (outcome.circuit().reason()) {
            case SCHEMA_INCOMPATIBLE -> RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
            case UNEXPECTED_CONTENT -> RawSnapshotSchemaStatus.UNEXPECTED_CONTENT;
            default -> RawSnapshotSchemaStatus.TRANSPORT_ERROR;
        };
    }

    private static String requestKey(LocalDate date, int page) {
        return SofascoreEndpointType.SCHEDULED_EVENTS.name()
                + "|date=" + date + "|page=" + page;
    }

    private static J3LocalJsonImportException rejected(
            J3LocalJsonImportError error) {
        return new J3LocalJsonImportException(error);
    }
}
