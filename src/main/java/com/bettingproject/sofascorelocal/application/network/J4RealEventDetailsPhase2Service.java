package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV4Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.EventDetailsTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.EventDetailsTransportFailure;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceResult;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceService;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.EventDetailsProviderTransport;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** One explicitly confirmed provider refresh with no cache read and no retry. */
@Service
public class J4RealEventDetailsPhase2Service {

    private final J4RealPhase2ControlService controlService;
    private final EventDetailsProviderTransport transport;
    private final RawManualCallSnapshotStore rawSnapshotStore;
    private final J4ParsedEventDetailsPersistenceService parsedPersistenceService;
    private final EventDetailsV4Parser parser;
    private final ManualProviderRequestCoordinator requestCoordinator;
    private final J8BenchmarkAuditService benchmarkAuditService;

    @Autowired
    public J4RealEventDetailsPhase2Service(
            J4RealPhase2ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            ManualProviderRequestCoordinator requestCoordinator,
            J8BenchmarkAuditService benchmarkAuditService) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                parsedPersistenceService,
                new EventDetailsV4Parser(),
                requestCoordinator,
                benchmarkAuditService);
    }

    J4RealEventDetailsPhase2Service(
            J4RealPhase2ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            EventDetailsV4Parser parser,
            Clock clock,
            Duration minimumDelay,
            Pause pause) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                parsedPersistenceService,
                parser,
                new ManualProviderRequestCoordinator(clock, minimumDelay, pause::pause),
                J8BenchmarkAuditService.disabled(clock));
    }

    J4RealEventDetailsPhase2Service(
            J4RealPhase2ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            EventDetailsV4Parser parser,
            Clock clock,
            Duration minimumDelay,
            Pause pause,
            J8BenchmarkAuditService benchmarkAuditService) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                parsedPersistenceService,
                parser,
                new ManualProviderRequestCoordinator(clock, minimumDelay, pause::pause),
                benchmarkAuditService);
    }

    private J4RealEventDetailsPhase2Service(
            J4RealPhase2ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            EventDetailsV4Parser parser,
            ManualProviderRequestCoordinator requestCoordinator,
            J8BenchmarkAuditService benchmarkAuditService) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.rawSnapshotStore = Objects.requireNonNull(rawSnapshotStore, "rawSnapshotStore");
        this.parsedPersistenceService = Objects.requireNonNull(
                parsedPersistenceService, "parsedPersistenceService");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.requestCoordinator = Objects.requireNonNull(
                requestCoordinator, "requestCoordinator");
        this.benchmarkAuditService = Objects.requireNonNull(
                benchmarkAuditService, "benchmarkAuditService");
    }

    public J4RealEventDetailsPhase2Result execute(J4RealPhase2ExecutionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        J8BenchmarkAuditService.Session audit;
        J8BenchmarkAuditService.Unit unit;
        try {
            audit = benchmarkAuditService.start(
                    claim.requestId(),
                    J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                    J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                    J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2.maximumUnits(),
                    Optional.empty());
            unit = audit.declare(
                    1,
                    SofascoreEndpointType.EVENT_DETAILS,
                    EventDetailsProviderRequest.phase2(
                            claim.providerOrigin(), claim.eventId()).requestKey(),
                    Optional.of(claim.canonicalEventId()),
                    OptionalLong.of(claim.eventId()));
        }
        catch (RuntimeException exception) {
            return failAndLock(claim, "BENCHMARK_AUDIT_FAILURE", 0);
        }

        J4RealEventDetailsPhase2Result result = executeCampaign(claim, audit, unit);
        if ("BENCHMARK_AUDIT_FAILURE".equals(result.terminalCode())) {
            return result;
        }
        try {
            audit.resolveFailureIfPending(unit, result.terminalCode());
            J8BenchmarkCampaignTerminalState terminalState = result.completed()
                    ? J8BenchmarkCampaignTerminalState.COMPLETED
                    : result.terminalCode().contains("OPERATOR_STOP")
                            ? J8BenchmarkCampaignTerminalState.CANCELLED
                            : J8BenchmarkCampaignTerminalState.FAILED;
            audit.finish(terminalState, Optional.of(result.terminalCode()));
        }
        catch (RuntimeException exception) {
            return failAndLock(
                    claim,
                    "BENCHMARK_AUDIT_FAILURE",
                    result.providerCallAttempts());
        }
        return result;
    }

    private J4RealEventDetailsPhase2Result executeCampaign(
            J4RealPhase2ExecutionClaim claim,
            J8BenchmarkAuditService.Session audit,
            J8BenchmarkAuditService.Unit unit) {
        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(claim, "OPERATOR_STOP", 0);
        }
        try {
            audit.reach(unit);
        }
        catch (RuntimeException exception) {
            return failAndLock(claim, "BENCHMARK_AUDIT_FAILURE", 0);
        }

        EventDetailsProviderRequest request = EventDetailsProviderRequest.phase2(
                claim.providerOrigin(), claim.eventId());
        EventDetailsTransportResponse response;
        int providerCallAttempts = 0;
        CampaignResources resources = new CampaignResources();
        try {
            resources.lease = requestCoordinator.acquireCampaign(claim.requestId());
            if (!controlService.executionMayContinue(claim.requestId())) {
                throw new EventDetailsTransportException(
                        EventDetailsTransportFailure.OPERATOR_STOP);
            }
            resources.campaign = transport.openCampaign(claim.requestId());
            if (!controlService.executionMayContinue(claim.requestId())) {
                throw new EventDetailsTransportException(
                        EventDetailsTransportFailure.OPERATOR_STOP);
            }
            resources.lease.beginRequest();
            if (!controlService.executionMayContinue(claim.requestId())) {
                throw new EventDetailsTransportException(
                        EventDetailsTransportFailure.OPERATOR_STOP);
            }
            try {
                audit.startAttempt(unit);
            }
            catch (RuntimeException exception) {
                RuntimeException cleanupFailure = resources.closeSafely();
                return failAndLock(
                        claim,
                        "BENCHMARK_AUDIT_FAILURE",
                        providerCallAttempts,
                        cleanupFailure != null);
            }
            try {
                response = resources.campaign.execute(request);
            }
            finally {
                providerCallAttempts = 1;
            }
            try {
                audit.captureResponse(
                        unit, response.httpStatus(), response.latency().toMillis());
            }
            catch (RuntimeException exception) {
                RuntimeException cleanupFailure = resources.closeSafely();
                return failAndLock(
                        claim,
                        "BENCHMARK_AUDIT_FAILURE",
                        providerCallAttempts,
                        cleanupFailure != null);
            }
        }
        catch (ManualProviderRequestCoordinator.CoordinationException exception) {
            RuntimeException cleanupFailure = resources.closeSafely();
            String code = cleanupCode(cleanupFailure, "MINIMUM_DELAY_INTERRUPTED");
            return failAndLock(
                    claim, code, providerCallAttempts, cleanupFailure != null);
        }
        catch (EventDetailsTransportException exception) {
            String code = controlService.executionMayContinue(claim.requestId())
                    ? "TRANSPORT_" + exception.failure().name()
                    : "OPERATOR_STOP";
            RuntimeException cleanupFailure = resources.closeSafely();
            code = cleanupCode(cleanupFailure, code);
            return failAndLock(
                    claim, code, providerCallAttempts, cleanupFailure != null);
        }
        catch (RuntimeException exception) {
            String code = controlService.executionMayContinue(claim.requestId())
                    ? "TRANSPORT_IO_FAILURE"
                    : "OPERATOR_STOP";
            RuntimeException cleanupFailure = resources.closeSafely();
            code = cleanupCode(cleanupFailure, code);
            return failAndLock(
                    claim, code, providerCallAttempts, cleanupFailure != null);
        }

        RawSnapshotPersistenceResult rawPersistence;
        try {
            rawPersistence = rawSnapshotStore.save(rawOnly(response));
        }
        catch (RuntimeException exception) {
            RuntimeException cleanupFailure = resources.closeSafely();
            String code = cleanupCode(cleanupFailure, "RAW_PERSISTENCE_ERROR");
            return failAndLock(claim, code, 1, cleanupFailure != null);
        }
        try {
            audit.captureSnapshot(
                    unit, rawPersistence, EventDetailsV4Parser.PARSER_VERSION);
        }
        catch (RuntimeException exception) {
            RuntimeException cleanupFailure = resources.closeSafely();
            return failAndLock(
                    claim,
                    "BENCHMARK_AUDIT_FAILURE",
                    1,
                    cleanupFailure != null);
        }
        RuntimeException cleanupFailure = resources.closeSafely();
        if (cleanupFailure != null) {
            String code = cleanupCode(cleanupFailure, "TRANSPORT_IO_FAILURE");
            return failAndLock(claim, code, 1, true);
        }

        if (response.httpStatus() == 404) {
            if (!classifyInsertedSafely(
                    rawPersistence,
                    RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE,
                    null)) {
                return failAndLock(claim, "RAW_CLASSIFICATION_ERROR", 1);
            }
            try {
                audit.resolve(
                        unit,
                        J8BenchmarkResolutionSource.PROVIDER,
                        J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE,
                        Optional.of(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE),
                        0,
                        Optional.empty(),
                        OptionalInt.empty(),
                        Optional.of("HTTP_404"));
            }
            catch (RuntimeException exception) {
                return failAndLock(claim, "BENCHMARK_AUDIT_FAILURE", 1);
            }
            try {
                controlService.recordEventCompleted(claim.requestId(), claim.eventId());
                controlService.completeUnavailable(claim.requestId());
            }
            catch (J4RealPhase2ControlException exception) {
                return failed(claim, "OPERATOR_STOP", 1);
            }
            return new J4RealEventDetailsPhase2Result(
                    claim.requestId(),
                    claim.eventId(),
                    true,
                    "COMPLETED_UNAVAILABLE",
                    1,
                    List.of(),
                    List.of(new J4RealEventDetailsUnavailableResult(
                            claim.eventId(),
                            rawPersistence.snapshotId(),
                            404,
                            rawPersistence.payloadSha256(),
                            rawPersistence.payloadSizeBytes(),
                            RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE)));
        }
        if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
            String terminalCode = httpTerminalCode(response.httpStatus());
            if (!classifyInsertedSafely(
                    rawPersistence,
                    RawSnapshotSchemaStatus.TRANSPORT_ERROR,
                    terminalCode)) {
                terminalCode = "RAW_CLASSIFICATION_ERROR";
            }
            return failAndLock(claim, terminalCode, 1);
        }
        if (!isJsonContentType(response.contentType())) {
            if (!classifyInsertedSafely(
                    rawPersistence,
                    RawSnapshotSchemaStatus.UNEXPECTED_CONTENT,
                    RawSnapshotSchemaStatus.UNEXPECTED_CONTENT.name())) {
                return failAndLock(claim, "RAW_CLASSIFICATION_ERROR", 1);
            }
            return failAndLock(claim, "UNEXPECTED_CONTENT", 1);
        }

        EventDetailsParseResult parseResult;
        try {
            parseResult = parser.parse(
                    rawPersistence.snapshotId(),
                    response.payload(),
                    response.receivedAt());
        }
        catch (RuntimeException exception) {
            return failAndLock(claim, "PARSER_FAILURE", 1);
        }
        if (parseResult.status() != EventDetailsParseStatus.PARSED) {
            RawSnapshotSchemaStatus schemaStatus = parseResult.status()
                    == EventDetailsParseStatus.UNEXPECTED_CONTENT
                            ? RawSnapshotSchemaStatus.UNEXPECTED_CONTENT
                            : RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
            String terminalCode = parseResult.status().name();
            if (!classifyInsertedSafely(rawPersistence, schemaStatus, schemaStatus.name())) {
                terminalCode = "RAW_CLASSIFICATION_ERROR";
            }
            return failAndLock(claim, terminalCode, 1);
        }

        var details = parseResult.details().orElseThrow();
        if (details.providerEventId() != claim.eventId()) {
            String terminalCode = "EVENT_ID_MISMATCH";
            if (!classifyInsertedSafely(
                    rawPersistence,
                    RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                    RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE.name())) {
                terminalCode = "RAW_CLASSIFICATION_ERROR";
            }
            return failAndLock(claim, terminalCode, 1);
        }

        J4ParsedEventDetailsPersistenceResult normalized;
        try {
            normalized = parsedPersistenceService.persistParsed(
                    request,
                    response,
                    rawPersistence,
                    details);
        }
        catch (RuntimeException exception) {
            return failAndLock(claim, "NORMALIZATION_PERSISTENCE_ERROR", 1);
        }
        if (!normalized.canonicalEventId().equals(claim.canonicalEventId())) {
            return failAndLock(claim, "CANONICAL_EVENT_MISMATCH", 1);
        }

        J4RealEventDetailsEventResult event = new J4RealEventDetailsEventResult(
                claim.eventId(),
                J4RealEventDetailsResolutionSource.PROVIDER,
                rawPersistence.snapshotId(),
                normalized.canonicalEventId(),
                rawPersistence.payloadSha256(),
                rawPersistence.payloadSizeBytes(),
                RawSnapshotSchemaStatus.PARSED,
                details,
                normalized.eventObservationInserted(),
                normalized.detailObservationInserted(),
                parseResult.warnings().size());
        try {
            audit.resolve(
                    unit,
                    J8BenchmarkResolutionSource.PROVIDER,
                    J8BenchmarkOutcomeType.PARSED,
                    Optional.of(RawSnapshotSchemaStatus.PARSED),
                    event.warningCount(),
                    Optional.empty(),
                    OptionalInt.empty(),
                    Optional.empty());
        }
        catch (RuntimeException exception) {
            return failAndLock(claim, "BENCHMARK_AUDIT_FAILURE", 1);
        }
        try {
            controlService.recordEventCompleted(claim.requestId(), claim.eventId());
            controlService.complete(claim.requestId());
        }
        catch (J4RealPhase2ControlException exception) {
            return failed(claim, "OPERATOR_STOP", 1);
        }
        return new J4RealEventDetailsPhase2Result(
                claim.requestId(),
                claim.eventId(),
                true,
                "COMPLETED",
                1,
                List.of(event),
                List.of());
    }

    private J4RealEventDetailsPhase2Result failAndLock(
            J4RealPhase2ExecutionClaim claim,
            String code,
            int providerCallAttempts) {
        return failAndLock(claim, code, providerCallAttempts, false);
    }

    private J4RealEventDetailsPhase2Result failAndLock(
            J4RealPhase2ExecutionClaim claim,
            String code,
            int providerCallAttempts,
            boolean cleanupFailureKnown) {
        if (controlService.executionMayContinue(claim.requestId())) {
            try {
                controlService.fail(claim.requestId(), code);
            }
            catch (J4RealPhase2ControlException exception) {
                return failed(
                        claim,
                        cleanupFailureKnown ? code : "OPERATOR_STOP",
                        providerCallAttempts);
            }
        }
        else {
            return failed(
                    claim,
                    cleanupFailureKnown ? code : "OPERATOR_STOP",
                    providerCallAttempts);
        }
        return failed(claim, code, providerCallAttempts);
    }

    private static J4RealEventDetailsPhase2Result failed(
            J4RealPhase2ExecutionClaim claim,
            String code,
            int providerCallAttempts) {
        return new J4RealEventDetailsPhase2Result(
                claim.requestId(),
                claim.eventId(),
                false,
                code,
                providerCallAttempts,
                List.of(),
                List.of());
    }

    private static RawManualCallSnapshot rawOnly(EventDetailsTransportResponse response) {
        return new RawManualCallSnapshot(
                SofascoreEndpointType.EVENT_DETAILS,
                response.requestKey(),
                response.requestedAt(),
                response.receivedAt(),
                response.httpStatus(),
                response.contentType(),
                response.latency(),
                response.payload(),
                EventDetailsV4Parser.PARSER_VERSION,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null);
    }

    private boolean classifySafely(
            long snapshotId,
            RawSnapshotSchemaStatus status,
            String errorCode) {
        try {
            rawSnapshotStore.classify(snapshotId, status, errorCode);
            return true;
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean classifyInsertedSafely(
            RawSnapshotPersistenceResult raw,
            RawSnapshotSchemaStatus status,
            String errorCode) {
        return switch (raw.outcome()) {
            case INSERTED -> classifySafely(
                    raw.snapshotId(), status, errorCode);
            case DEDUPLICATED -> true;
            case CACHE_HIT -> false;
        };
    }

    private static String cleanupCode(RuntimeException cleanupFailure, String fallback) {
        if (cleanupFailure == null) {
            return fallback;
        }
        return cleanupFailure instanceof EventDetailsTransportException failure
                ? "TRANSPORT_" + failure.failure().name()
                : "TRANSPORT_IO_FAILURE";
    }

    private static boolean isJsonContentType(String contentType) {
        String normalized = Objects.requireNonNull(contentType, "contentType")
                .toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        String mediaType = parameters < 0
                ? normalized.trim()
                : normalized.substring(0, parameters).trim();
        return mediaType.equals("application/json") || mediaType.endsWith("+json");
    }

    private static String httpTerminalCode(int httpStatus) {
        if (httpStatus == 403) {
            return "HTTP_403";
        }
        if (httpStatus == 429) {
            return "HTTP_429";
        }
        if (httpStatus == 408) {
            return "HTTP_TIMEOUT";
        }
        if (httpStatus >= 500) {
            return "HTTP_5XX";
        }
        return "HTTP_STATUS_" + httpStatus;
    }

    @FunctionalInterface
    interface Pause {
        void pause(Duration delay);
    }

    private static final class CampaignResources {
        private ManualProviderRequestCoordinator.CampaignLease lease;
        private EventDetailsProviderTransport.Campaign campaign;

        private RuntimeException closeSafely() {
            RuntimeException failure = null;
            try {
                if (campaign != null) {
                    campaign.close();
                }
            }
            catch (RuntimeException exception) {
                failure = exception;
            }
            finally {
                campaign = null;
                try {
                    if (lease != null) {
                        lease.close();
                    }
                }
                catch (RuntimeException exception) {
                    if (failure == null) {
                        failure = exception;
                    }
                    else {
                        failure.addSuppressed(exception);
                    }
                }
                finally {
                    lease = null;
                }
            }
            return failure;
        }
    }
}
