package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV2Parser;
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
import com.bettingproject.sofascorelocal.domain.provider.J4CachedEventDetails;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.EventDetailsProviderTransport;
import com.bettingproject.sofascorelocal.port.J4EventDetailsCache;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

@Service
public class J4RealEventDetailsPhase1Service {

    private final J4RealPhase1ControlService controlService;
    private final EventDetailsProviderTransport transport;
    private final RawManualCallSnapshotStore rawSnapshotStore;
    private final J4EventDetailsCache cache;
    private final J4ParsedEventDetailsPersistenceService parsedPersistenceService;
    private final EventDetailsV2Parser parser;
    private final Clock clock;
    private final Duration cacheTtl;
    private final ManualProviderRequestCoordinator requestCoordinator;
    private final J8BenchmarkAuditService benchmarkAuditService;

    @Autowired
    public J4RealEventDetailsPhase1Service(
            J4RealPhase1ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4EventDetailsCache cache,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            SofascoreEndpointCatalog endpointCatalog,
            ManualProviderRequestCoordinator requestCoordinator,
            J8BenchmarkAuditService benchmarkAuditService) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                cache,
                parsedPersistenceService,
                new EventDetailsV2Parser(),
                Clock.systemUTC(),
                endpointCatalog.get(SofascoreEndpointType.EVENT_DETAILS).cacheTtl(),
                requestCoordinator,
                benchmarkAuditService);
    }

    J4RealEventDetailsPhase1Service(
            J4RealPhase1ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4EventDetailsCache cache,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            SofascoreEndpointCatalog endpointCatalog,
            ManualProviderRequestCoordinator requestCoordinator) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                cache,
                parsedPersistenceService,
                new EventDetailsV2Parser(),
                Clock.systemUTC(),
                endpointCatalog.get(SofascoreEndpointType.EVENT_DETAILS).cacheTtl(),
                requestCoordinator,
                J8BenchmarkAuditService.disabled(Clock.systemUTC()));
    }

    J4RealEventDetailsPhase1Service(
            J4RealPhase1ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4EventDetailsCache cache,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            EventDetailsV2Parser parser,
            Clock clock,
            Duration cacheTtl,
            Duration minimumDelay,
            Pause pause) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                cache,
                parsedPersistenceService,
                parser,
                clock,
                cacheTtl,
                new ManualProviderRequestCoordinator(clock, minimumDelay, pause::pause),
                J8BenchmarkAuditService.disabled(clock));
    }

    J4RealEventDetailsPhase1Service(
            J4RealPhase1ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4EventDetailsCache cache,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            EventDetailsV2Parser parser,
            Clock clock,
            Duration cacheTtl,
            Duration minimumDelay,
            Pause pause,
            J8BenchmarkAuditService benchmarkAuditService) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                cache,
                parsedPersistenceService,
                parser,
                clock,
                cacheTtl,
                new ManualProviderRequestCoordinator(clock, minimumDelay, pause::pause),
                benchmarkAuditService);
    }

    private J4RealEventDetailsPhase1Service(
            J4RealPhase1ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4EventDetailsCache cache,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            EventDetailsV2Parser parser,
            Clock clock,
            Duration cacheTtl,
            ManualProviderRequestCoordinator requestCoordinator,
            J8BenchmarkAuditService benchmarkAuditService) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.rawSnapshotStore = Objects.requireNonNull(rawSnapshotStore, "rawSnapshotStore");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.parsedPersistenceService = Objects.requireNonNull(
                parsedPersistenceService, "parsedPersistenceService");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cacheTtl = requirePositive(cacheTtl, "cacheTtl");
        this.requestCoordinator = Objects.requireNonNull(
                requestCoordinator, "requestCoordinator");
        this.benchmarkAuditService = Objects.requireNonNull(
                benchmarkAuditService, "benchmarkAuditService");
    }

    public J4RealEventDetailsPhase1Result execute(J4RealPhase1ExecutionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        J8BenchmarkAuditService.Session audit;
        Map<Long, J8BenchmarkAuditService.Unit> auditUnits = new LinkedHashMap<>();
        try {
            audit = benchmarkAuditService.start(
                    claim.requestId(),
                    J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1,
                    J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                    J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1.maximumUnits(),
                    Optional.empty());
            int ordinal = 0;
            for (long eventId : EventDetailsProviderRequest.PHASE_1_EVENT_IDS) {
                EventDetailsProviderRequest request = EventDetailsProviderRequest.phase1(
                        claim.providerOrigin(), eventId);
                auditUnits.put(eventId, audit.declare(
                        ++ordinal,
                        SofascoreEndpointType.EVENT_DETAILS,
                        request.requestKey(),
                        Optional.empty(),
                        OptionalLong.of(eventId)));
            }
        }
        catch (RuntimeException exception) {
            return failAndLock(
                    claim.requestId(),
                    "BENCHMARK_AUDIT_FAILURE",
                    new Counters(),
                    List.of(),
                    List.of(),
                    new CampaignResources());
        }

        J4RealEventDetailsPhase1Result result = executeCampaign(
                claim, audit, auditUnits);
        if (!result.completed()) {
            for (J8BenchmarkAuditService.Unit unit : auditUnits.values()) {
                audit.resolveFailureIfPending(unit, result.terminalCode());
            }
        }
        J8BenchmarkCampaignTerminalState terminalState = result.completed()
                ? J8BenchmarkCampaignTerminalState.COMPLETED
                : result.terminalCode().contains("OPERATOR_STOP")
                        ? J8BenchmarkCampaignTerminalState.CANCELLED
                        : J8BenchmarkCampaignTerminalState.FAILED;
        audit.finish(terminalState, Optional.of(result.terminalCode()));
        return result;
    }

    private J4RealEventDetailsPhase1Result executeCampaign(
            J4RealPhase1ExecutionClaim claim,
            J8BenchmarkAuditService.Session audit,
            Map<Long, J8BenchmarkAuditService.Unit> auditUnits) {
        List<J4RealEventDetailsEventResult> results = new ArrayList<>();
        List<J4RealEventDetailsUnavailableResult> unavailableResults = new ArrayList<>();
        Counters counters = new Counters();
        CampaignResources resources = new CampaignResources();

        try {
            if (!controlService.executionMayContinue(claim.requestId())) {
                return failed(
                        claim.requestId(), "OPERATOR_STOP", counters, results,
                        unavailableResults);
            }
            try {
                resources.lease = requestCoordinator.acquireCampaign(claim.requestId());
            }
            catch (ManualProviderRequestCoordinator.CoordinationException exception) {
                return failAndLock(
                        claim.requestId(), "MINIMUM_DELAY_INTERRUPTED", counters, results,
                        unavailableResults, resources);
            }
            for (long eventId : EventDetailsProviderRequest.PHASE_1_EVENT_IDS) {
                if (!controlService.executionMayContinue(claim.requestId())) {
                    return failAndLock(
                            claim.requestId(), "OPERATOR_STOP", counters, results,
                            unavailableResults, resources);
                }
                try {
                    audit.reach(auditUnits.get(eventId));
                }
                catch (RuntimeException exception) {
                    return failAndLock(
                            claim.requestId(), "BENCHMARK_AUDIT_FAILURE", counters, results,
                            unavailableResults, resources);
                }
                EventDetailsProviderRequest request = EventDetailsProviderRequest.phase1(
                        claim.providerOrigin(), eventId);

                Optional<J4CachedEventDetails> cached;
                try {
                    cached = cache.findFreshParsed(
                            request,
                            clock.instant(),
                            cacheTtl,
                            EventDetailsV2Parser.PARSER_VERSION);
                }
                catch (RuntimeException exception) {
                    return failAndLock(
                            claim.requestId(), "CACHE_READ_ERROR", counters, results,
                            unavailableResults, resources);
                }

                EventDetailsTransportResponse response;
                RawSnapshotPersistenceResult rawPersistence;
                J4RealEventDetailsResolutionSource resolutionSource;
                if (cached.isPresent()) {
                    J4CachedEventDetails candidate = cached.orElseThrow();
                    response = candidate.asTransportResponse();
                    rawPersistence = candidate.asPersistenceResult();
                    try {
                        audit.captureSnapshot(
                                auditUnits.get(eventId),
                                rawPersistence,
                                EventDetailsV2Parser.PARSER_VERSION);
                    }
                    catch (RuntimeException exception) {
                        return failAndLock(
                                claim.requestId(), "BENCHMARK_AUDIT_FAILURE", counters,
                                results, unavailableResults, resources);
                    }
                    resolutionSource = J4RealEventDetailsResolutionSource.CACHE;
                    counters.cacheHits++;
                }
                else {
                    try {
                        response = executeProvider(
                                claim,
                                request,
                                resources,
                                counters,
                                audit,
                                auditUnits.get(eventId));
                    }
                    catch (ManualProviderRequestCoordinator.CoordinationException exception) {
                        return failAndLock(
                                claim.requestId(), "MINIMUM_DELAY_INTERRUPTED", counters,
                                results, unavailableResults, resources);
                    }
                    catch (EventDetailsTransportException exception) {
                        String code = controlService.executionMayContinue(claim.requestId())
                                ? "TRANSPORT_" + exception.failure().name()
                                : "OPERATOR_STOP";
                        return failAndLock(
                                claim.requestId(), code, counters, results, unavailableResults,
                                resources);
                    }
                    catch (BenchmarkAuditException exception) {
                        return failAndLock(
                                claim.requestId(), "BENCHMARK_AUDIT_FAILURE", counters,
                                results, unavailableResults, resources);
                    }
                    catch (RuntimeException exception) {
                        String code = controlService.executionMayContinue(claim.requestId())
                                ? "TRANSPORT_IO_FAILURE"
                                : "OPERATOR_STOP";
                        return failAndLock(
                                claim.requestId(), code, counters, results, unavailableResults,
                                resources);
                    }

                    try {
                        rawPersistence = rawSnapshotStore.save(rawOnly(response));
                    }
                    catch (RuntimeException exception) {
                        return failAndLock(
                                claim.requestId(), "RAW_PERSISTENCE_ERROR", counters, results,
                                unavailableResults, resources);
                    }
                    try {
                        audit.captureSnapshot(
                                auditUnits.get(eventId),
                                rawPersistence,
                                EventDetailsV2Parser.PARSER_VERSION);
                    }
                    catch (RuntimeException exception) {
                        return failAndLock(
                                claim.requestId(), "BENCHMARK_AUDIT_FAILURE", counters,
                                results, unavailableResults, resources);
                    }
                    resolutionSource = J4RealEventDetailsResolutionSource.PROVIDER;

                    if (response.httpStatus() == 404) {
                        if (!classifyInsertedSafely(
                                rawPersistence,
                                RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE,
                                null)) {
                            return failAndLock(
                                    claim.requestId(), "RAW_CLASSIFICATION_ERROR", counters,
                                    results, unavailableResults, resources);
                        }
                        unavailableResults.add(unavailable(eventId, rawPersistence));
                        recordAuditOrFailClosed(
                                claim.requestId(),
                                counters,
                                results,
                                unavailableResults,
                                resources,
                                () -> audit.resolve(
                                        auditUnits.get(eventId),
                                        J8BenchmarkResolutionSource.PROVIDER,
                                        J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE,
                                        Optional.of(
                                                RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE),
                                        0,
                                        Optional.empty(),
                                        OptionalInt.empty(),
                                        Optional.of("HTTP_404")));
                        try {
                            controlService.recordEventCompleted(claim.requestId(), eventId);
                        }
                        catch (J4RealPhase1ControlException exception) {
                            return failAndLock(
                                    claim.requestId(), "OPERATOR_STOP", counters, results,
                                    unavailableResults, resources);
                        }
                        continue;
                    }
                    if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
                        String terminalCode = httpTerminalCode(response.httpStatus());
                        if (!classifyInsertedSafely(
                                rawPersistence,
                                RawSnapshotSchemaStatus.TRANSPORT_ERROR,
                                terminalCode)) {
                            terminalCode = "RAW_CLASSIFICATION_ERROR";
                        }
                        return failAndLock(
                                claim.requestId(), terminalCode, counters, results,
                                unavailableResults, resources);
                    }
                    if (!isJsonContentType(response.contentType())) {
                        if (!classifyInsertedSafely(
                                rawPersistence,
                                RawSnapshotSchemaStatus.UNEXPECTED_CONTENT,
                                RawSnapshotSchemaStatus.UNEXPECTED_CONTENT.name())) {
                            return failAndLock(
                                    claim.requestId(), "RAW_CLASSIFICATION_ERROR", counters,
                                    results, unavailableResults, resources);
                        }
                        return failAndLock(
                                claim.requestId(), "UNEXPECTED_CONTENT", counters, results,
                                unavailableResults, resources);
                    }
                }

                EventDetailsParseResult parseResult;
                try {
                    parseResult = parser.parse(
                            rawPersistence.snapshotId(),
                            response.payload(),
                            response.receivedAt());
                }
                catch (RuntimeException exception) {
                    return failAndLock(
                            claim.requestId(), "PARSER_FAILURE", counters, results,
                            unavailableResults, resources);
                }
                if (parseResult.status() != EventDetailsParseStatus.PARSED) {
                    String terminalCode = resolutionSource == J4RealEventDetailsResolutionSource.CACHE
                            ? "CACHE_REPARSE_INCOMPATIBLE"
                            : parseResult.status().name();
                    if (resolutionSource == J4RealEventDetailsResolutionSource.PROVIDER) {
                        RawSnapshotSchemaStatus schemaStatus = parseResult.status()
                                == EventDetailsParseStatus.UNEXPECTED_CONTENT
                                        ? RawSnapshotSchemaStatus.UNEXPECTED_CONTENT
                                        : RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
                        if (!classifyInsertedSafely(
                                rawPersistence, schemaStatus, schemaStatus.name())) {
                            terminalCode = "RAW_CLASSIFICATION_ERROR";
                        }
                    }
                    return failAndLock(
                            claim.requestId(), terminalCode, counters, results,
                            unavailableResults, resources);
                }
                var details = parseResult.details().orElseThrow();
                if (details.providerEventId() != eventId) {
                    String terminalCode = "EVENT_ID_MISMATCH";
                    if (resolutionSource == J4RealEventDetailsResolutionSource.PROVIDER) {
                        if (!classifyInsertedSafely(
                                rawPersistence,
                                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE.name())) {
                            terminalCode = "RAW_CLASSIFICATION_ERROR";
                        }
                    }
                    return failAndLock(
                            claim.requestId(), terminalCode, counters, results,
                            unavailableResults, resources);
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
                    return failAndLock(
                            claim.requestId(), "NORMALIZATION_PERSISTENCE_ERROR", counters,
                            results, unavailableResults, resources);
                }

                J4RealEventDetailsEventResult eventResult =
                        new J4RealEventDetailsEventResult(
                        eventId,
                        resolutionSource,
                        rawPersistence.snapshotId(),
                        normalized.canonicalEventId(),
                        rawPersistence.payloadSha256(),
                        rawPersistence.payloadSizeBytes(),
                        RawSnapshotSchemaStatus.PARSED,
                        details,
                        normalized.eventObservationInserted(),
                        normalized.detailObservationInserted(),
                        parseResult.warnings().size());
                recordAuditOrFailClosed(
                        claim.requestId(),
                        counters,
                        results,
                        unavailableResults,
                        resources,
                        () -> audit.resolve(
                                auditUnits.get(eventId),
                                resolutionSource == J4RealEventDetailsResolutionSource.CACHE
                                        ? J8BenchmarkResolutionSource.CACHE
                                        : J8BenchmarkResolutionSource.PROVIDER,
                                J8BenchmarkOutcomeType.PARSED,
                                Optional.of(RawSnapshotSchemaStatus.PARSED),
                                eventResult.warningCount(),
                                Optional.empty(),
                                OptionalInt.empty(),
                                Optional.empty()));
                try {
                    controlService.recordEventCompleted(claim.requestId(), eventId);
                }
                catch (J4RealPhase1ControlException exception) {
                    return failAndLock(
                            claim.requestId(), "OPERATOR_STOP", counters, results,
                            unavailableResults, resources);
                }
                results.add(eventResult);
            }

            RuntimeException cleanupFailure = resources.closeSafely();
            if (cleanupFailure != null) {
                return failAndLockAfterCleanup(
                        claim.requestId(), cleanupCode(cleanupFailure, "TRANSPORT_IO_FAILURE"),
                        counters, results, unavailableResults, true);
            }
            try {
                controlService.complete(claim.requestId());
            }
            catch (J4RealPhase1ControlException exception) {
                return failed(
                        claim.requestId(), "OPERATOR_STOP", counters, results,
                        unavailableResults);
            }
            return new J4RealEventDetailsPhase1Result(
                    claim.requestId(),
                    true,
                    "COMPLETED",
                    counters.providerCallAttempts,
                    counters.cacheHits,
                    results,
                    unavailableResults);
        }
        finally {
            resources.closeSafely();
        }
    }

    private EventDetailsTransportResponse executeProvider(
            J4RealPhase1ExecutionClaim claim,
            EventDetailsProviderRequest request,
            CampaignResources resources,
            Counters counters,
            J8BenchmarkAuditService.Session audit,
            J8BenchmarkAuditService.Unit unit) {
        if (resources.campaign == null) {
            if (!controlService.executionMayContinue(claim.requestId())) {
                throw new EventDetailsTransportException(
                        EventDetailsTransportFailure.OPERATOR_STOP);
            }
            resources.campaign = transport.openCampaign(claim.requestId());
        }
        resources.lease.beginRequest();
        if (!controlService.executionMayContinue(claim.requestId())) {
            throw new EventDetailsTransportException(EventDetailsTransportFailure.OPERATOR_STOP);
        }
        try {
            audit.startAttempt(unit);
        }
        catch (RuntimeException exception) {
            throw new BenchmarkAuditException(exception);
        }
        EventDetailsTransportResponse response;
        try {
            response = resources.campaign.execute(request);
        }
        finally {
            counters.providerCallAttempts++;
        }
        try {
            audit.captureResponse(unit, response.httpStatus(), response.latency().toMillis());
        }
        catch (RuntimeException exception) {
            throw new BenchmarkAuditException(exception);
        }
        return response;
    }

    private J4RealEventDetailsPhase1Result failAndLock(
            UUID requestId,
            String code,
            Counters counters,
            List<J4RealEventDetailsEventResult> results,
            List<J4RealEventDetailsUnavailableResult> unavailableResults,
            CampaignResources resources) {
        RuntimeException cleanupFailure = resources.closeSafely();
        String terminalCode = cleanupCode(cleanupFailure, code);
        return failAndLockAfterCleanup(
                requestId,
                terminalCode,
                counters,
                results,
                unavailableResults,
                cleanupFailure != null);
    }

    private void recordAuditOrFailClosed(
            UUID requestId,
            Counters counters,
            List<J4RealEventDetailsEventResult> results,
            List<J4RealEventDetailsUnavailableResult> unavailableResults,
            CampaignResources resources,
            Runnable auditWrite) {
        try {
            auditWrite.run();
        }
        catch (RuntimeException auditFailure) {
            try {
                failAndLock(
                        requestId,
                        "BENCHMARK_AUDIT_FAILURE",
                        counters,
                        results,
                        unavailableResults,
                        resources);
            }
            catch (RuntimeException lockFailure) {
                if (lockFailure != auditFailure) {
                    auditFailure.addSuppressed(lockFailure);
                }
            }
            throw auditFailure;
        }
    }

    private J4RealEventDetailsPhase1Result failAndLockAfterCleanup(
            UUID requestId,
            String terminalCode,
            Counters counters,
            List<J4RealEventDetailsEventResult> results,
            List<J4RealEventDetailsUnavailableResult> unavailableResults,
            boolean cleanupFailureKnown) {
        if (controlService.executionMayContinue(requestId)) {
            try {
                controlService.fail(requestId, terminalCode);
            }
            catch (J4RealPhase1ControlException exception) {
                return failed(
                        requestId,
                        cleanupFailureKnown ? terminalCode : "OPERATOR_STOP",
                        counters,
                        results,
                        unavailableResults);
            }
        }
        else {
            return failed(
                    requestId,
                    cleanupFailureKnown ? terminalCode : "OPERATOR_STOP",
                    counters,
                    results,
                    unavailableResults);
        }
        return failed(requestId, terminalCode, counters, results, unavailableResults);
    }

    private static J4RealEventDetailsPhase1Result failed(
            UUID requestId,
            String code,
            Counters counters,
            List<J4RealEventDetailsEventResult> results,
            List<J4RealEventDetailsUnavailableResult> unavailableResults) {
        return new J4RealEventDetailsPhase1Result(
                requestId,
                false,
                code,
                counters.providerCallAttempts,
                counters.cacheHits,
                results,
                unavailableResults);
    }

    private static J4RealEventDetailsUnavailableResult unavailable(
            long eventId,
            RawSnapshotPersistenceResult rawPersistence) {
        return new J4RealEventDetailsUnavailableResult(
                eventId,
                rawPersistence.snapshotId(),
                404,
                rawPersistence.payloadSha256(),
                rawPersistence.payloadSizeBytes(),
                RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE);
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
                EventDetailsV2Parser.PARSER_VERSION,
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

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    @FunctionalInterface
    interface Pause {
        void pause(Duration delay);
    }

    private static final class Counters {
        private int providerCallAttempts;
        private int cacheHits;
    }

    private static final class BenchmarkAuditException extends RuntimeException {

        private BenchmarkAuditException(RuntimeException cause) {
            super(cause);
        }
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
