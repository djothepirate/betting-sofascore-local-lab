package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3LocalJsonImportExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
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
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
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
    private final J8BenchmarkAuditService benchmarkAuditService;

    @Autowired
    public J3LocalJsonImportService(
            J3ManualCallControlService controlService,
            RawManualCallSnapshotStore snapshotStore,
            J3SingleCallGuard callGuard,
            J3ManualCollectionEvidenceService evidenceService,
            SofascoreEndpointCatalog endpointCatalog,
            J8BenchmarkAuditService benchmarkAuditService) {
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
                endpointCatalog.get(SofascoreEndpointType.SCHEDULED_EVENTS).cacheTtl(),
                benchmarkAuditService);
    }

    J3LocalJsonImportService(
            J3ManualCallControlService controlService,
            J3ScheduledEventsOutcomeProcessor outcomeProcessor,
            ScheduledEventsV1Parser parser,
            J3SingleCallGuard callGuard,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            Duration cacheTtl) {
        this(
                controlService,
                outcomeProcessor,
                parser,
                callGuard,
                evidenceService,
                clock,
                cacheTtl,
                J8BenchmarkAuditService.disabled(clock));
    }

    J3LocalJsonImportService(
            J3ManualCallControlService controlService,
            J3ScheduledEventsOutcomeProcessor outcomeProcessor,
            ScheduledEventsV1Parser parser,
            J3SingleCallGuard callGuard,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            Duration cacheTtl,
            J8BenchmarkAuditService benchmarkAuditService) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.outcomeProcessor = Objects.requireNonNull(outcomeProcessor, "outcomeProcessor");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.callGuard = Objects.requireNonNull(callGuard, "callGuard");
        this.evidenceService = Objects.requireNonNull(evidenceService, "evidenceService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl");
        this.benchmarkAuditService = Objects.requireNonNull(
                benchmarkAuditService, "benchmarkAuditService");
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
        J3LocalJsonImportExecutionClaim claim = null;
        J8BenchmarkAuditService.Session audit = null;
        try (J3SingleCallGuard.Permit ignored = permit.orElseThrow()) {
            claim = controlService.claimLocalImportExecution(requestId);
            audit = benchmarkAuditService.start(
                    requestId,
                    J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                    J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT,
                    J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS.maximumUnits(),
                    Optional.of(claim.date()));
            for (int index = 0; index < validatedPayloads.size(); index++) {
                int page = index + 1;
                J8BenchmarkAuditService.Unit auditUnit = audit.declare(
                        page,
                        SofascoreEndpointType.SCHEDULED_EVENTS,
                        requestKey(claim.date(), page),
                        Optional.empty(),
                        OptionalLong.empty());
                if (!controlService.executionMayContinue(requestId)) {
                    audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                    J3ManualCallExecutionResult result = publishAlreadyLockedFailure(
                            claim.date(), completedPages, page, STOPPED, attempts);
                    finishAudit(audit, result);
                    return result;
                }

                audit.reach(auditUnit);
                Instant importedAt = clock.instant();
                ScheduledEventsTransportResponse response = new ScheduledEventsTransportResponse(
                        requestKey(claim.date(), page),
                        importedAt,
                        importedAt,
                        200,
                        CONTENT_TYPE,
                        Duration.ZERO,
                        validatedPayloads.get(index));
                J8BenchmarkAuditService.Session activeAudit = audit;
                J3ScheduledEventsOutcome outcome;
                try {
                    outcome = outcomeProcessor.processImportedResponse(
                            response,
                            persisted -> activeAudit.captureSnapshot(
                                    auditUnit,
                                    persisted,
                                    ScheduledEventsV1Parser.PARSER_VERSION));
                }
                catch (J3ScheduledEventsOutcomeProcessor.EvidencePersistenceException
                        exception) {
                    audit.resolveFailure(auditUnit, exception.code());
                    throw exception;
                }
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
                RawSnapshotSchemaStatus auditSchema = schemaStatus(outcome);
                J8BenchmarkOutcomeType auditOutcome = switch (auditSchema) {
                    case PARSED -> J8BenchmarkOutcomeType.PARSED;
                    case SCHEMA_INCOMPATIBLE -> J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE;
                    case UNEXPECTED_CONTENT -> J8BenchmarkOutcomeType.UNEXPECTED_CONTENT;
                    default -> J8BenchmarkOutcomeType.PROCESSING_FAILURE;
                };
                if (outcome.circuit().state() != J3CircuitState.CLOSED) {
                    audit.resolve(
                            auditUnit,
                            J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT,
                            auditOutcome,
                            auditOutcome == J8BenchmarkOutcomeType.PROCESSING_FAILURE
                                    ? Optional.empty()
                                    : Optional.of(auditSchema),
                            0,
                            Optional.empty(),
                            OptionalInt.empty(),
                            Optional.ofNullable(terminalCode));
                }

                if (!controlService.executionMayContinue(requestId)
                        || outcome.circuit().state() != J3CircuitState.CLOSED) {
                    if (outcome.circuit().state() == J3CircuitState.CLOSED) {
                        audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                    }
                    J3ManualCallControlSnapshot snapshot = controlService.snapshot();
                    if (snapshot.intent() != null
                            && snapshot.intent().state() == J3ManualCallIntentState.EXECUTING) {
                        String code = terminalCode == null ? STOPPED : terminalCode;
                        controlService.failExecution(requestId, page, code);
                        J3ManualCallExecutionResult result = publishAndLock(
                                requestId,
                                claim.date(),
                                J3ManualCallExecutionResult.failedLocalImport(
                                        completedPages, page, code, attempts.size()),
                                attempts);
                        finishAudit(audit, result);
                        return result;
                    }
                    J3ManualCallExecutionResult result = publishAlreadyLockedFailure(
                            claim.date(), completedPages, page, STOPPED, attempts);
                    finishAudit(audit, result);
                    return result;
                }

                controlService.recordPageCompleted(requestId, page);
                completedPages = page;
                audit.resolve(
                        auditUnit,
                        J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT,
                        J8BenchmarkOutcomeType.PARSED,
                        Optional.of(RawSnapshotSchemaStatus.PARSED),
                        0,
                        Optional.empty(),
                        OptionalInt.empty(),
                        Optional.empty());
            }

            controlService.completeExecution(requestId);
            J3ManualCallExecutionResult result = publishAndLock(
                    requestId,
                    claim.date(),
                    J3ManualCallExecutionResult.successfulLocalImport(completedPages),
                    attempts);
            finishAudit(audit, result);
            return result;
        }
        catch (J3ManualCallControlException exception) {
            finishAbortedAudit(
                    audit,
                    J8BenchmarkCampaignTerminalState.CANCELLED,
                    exception.error().name(),
                    exception);
            throw exception;
        }
        catch (J3LocalJsonImportException exception) {
            finishAbortedAudit(
                    audit,
                    J8BenchmarkCampaignTerminalState.FAILED,
                    exception.error().name(),
                    exception);
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
                J3ManualCallExecutionResult result = publishAndLock(
                        requestId,
                        claim.date(),
                        J3ManualCallExecutionResult.failedLocalImport(
                                completedPages,
                                failedPage,
                                PROCESSING_FAILURE,
                                attempts.size()),
                        attempts);
                if (audit != null) {
                    finishAudit(audit, result);
                }
                return result;
            }
            if (!snapshot.globalStopActive()) {
                controlService.stopGlobally();
            }
            finishAbortedAudit(
                    audit,
                    snapshot.globalStopActive()
                            ? J8BenchmarkCampaignTerminalState.CANCELLED
                            : J8BenchmarkCampaignTerminalState.FAILED,
                    snapshot.globalStopActive() ? "OPERATOR_STOP" : PROCESSING_FAILURE,
                    exception);
            throw exception;
        }
    }

    private static void finishAudit(
            J8BenchmarkAuditService.Session audit,
            J3ManualCallExecutionResult result) {
        J8BenchmarkCampaignTerminalState state = result.completed()
                ? J8BenchmarkCampaignTerminalState.COMPLETED
                : result.terminalCode().contains("STOP")
                        || result.terminalCode().contains("CIRCUIT_BLOCK")
                                ? J8BenchmarkCampaignTerminalState.CANCELLED
                                : J8BenchmarkCampaignTerminalState.FAILED;
        audit.finish(
                state,
                result.completed()
                        ? Optional.empty()
                        : Optional.of(result.terminalCode()));
    }

    private static void finishAbortedAudit(
            J8BenchmarkAuditService.Session audit,
            J8BenchmarkCampaignTerminalState state,
            String terminalCode,
            RuntimeException original) {
        if (audit == null) {
            return;
        }
        try {
            audit.finish(state, Optional.of(terminalCode));
        }
        catch (RuntimeException auditFailure) {
            if (auditFailure != original) {
                original.addSuppressed(auditFailure);
            }
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
