package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsParseEvidence;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.application.event.TournamentCanonicalEventPersistenceService;
import com.bettingproject.sofascorelocal.application.event.TournamentCanonicalizationResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.provider.CachedTournamentScheduledEventsResponse;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryLocalImportClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoverySource;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentScheduledEventsProjection;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.TournamentScheduledEventsCache;
import com.bettingproject.sofascorelocal.port.TournamentScheduledEventsProviderTransport;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

@Service
public class TournamentEventDiscoveryService {

    public static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final TournamentEventDiscoveryControlService controlService;
    private final TournamentScheduledEventsProviderTransport transport;
    private final TournamentScheduledEventsCache cache;
    private final RawManualCallSnapshotStore rawSnapshotStore;
    private final TournamentScheduledEventsProjectionService projectionService;
    private final TournamentCanonicalEventPersistenceService persistenceService;
    private final TournamentScheduledEventsV1Parser parser;
    private final ManualProviderRequestCoordinator requestCoordinator;
    private final Clock clock;
    private final J8BenchmarkAuditService benchmarkAudit;

    @Autowired
    public TournamentEventDiscoveryService(
            TournamentEventDiscoveryControlService controlService,
            TournamentScheduledEventsProviderTransport transport,
            TournamentScheduledEventsCache cache,
            RawManualCallSnapshotStore rawSnapshotStore,
            TournamentScheduledEventsProjectionService projectionService,
            TournamentCanonicalEventPersistenceService persistenceService,
            ManualProviderRequestCoordinator requestCoordinator,
            J8BenchmarkAuditService benchmarkAudit) {
        this(
                controlService,
                transport,
                cache,
                rawSnapshotStore,
                projectionService,
                persistenceService,
                new TournamentScheduledEventsV1Parser(),
                requestCoordinator,
                Clock.systemUTC(),
                benchmarkAudit);
    }

    TournamentEventDiscoveryService(
            TournamentEventDiscoveryControlService controlService,
            TournamentScheduledEventsProviderTransport transport,
            TournamentScheduledEventsCache cache,
            RawManualCallSnapshotStore rawSnapshotStore,
            TournamentScheduledEventsProjectionService projectionService,
            TournamentCanonicalEventPersistenceService persistenceService,
            TournamentScheduledEventsV1Parser parser,
            ManualProviderRequestCoordinator requestCoordinator,
            Clock clock) {
        this(
                controlService,
                transport,
                cache,
                rawSnapshotStore,
                projectionService,
                persistenceService,
                parser,
                requestCoordinator,
                clock,
                J8BenchmarkAuditService.disabled(clock));
    }

    TournamentEventDiscoveryService(
            TournamentEventDiscoveryControlService controlService,
            TournamentScheduledEventsProviderTransport transport,
            TournamentScheduledEventsCache cache,
            RawManualCallSnapshotStore rawSnapshotStore,
            TournamentScheduledEventsProjectionService projectionService,
            TournamentCanonicalEventPersistenceService persistenceService,
            TournamentScheduledEventsV1Parser parser,
            ManualProviderRequestCoordinator requestCoordinator,
            Clock clock,
            J8BenchmarkAuditService benchmarkAudit) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.rawSnapshotStore = Objects.requireNonNull(rawSnapshotStore, "rawSnapshotStore");
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.requestCoordinator = Objects.requireNonNull(
                requestCoordinator, "requestCoordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.benchmarkAudit = Objects.requireNonNull(benchmarkAudit, "benchmarkAudit");
    }

    public TournamentEventDiscoveryResult execute(
            TournamentEventDiscoveryExecutionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        TournamentScheduledEventsProviderRequest request =
                new TournamentScheduledEventsProviderRequest(
                        claim.providerOrigin(),
                        claim.collectionDate(),
                        claim.selection().uniqueTournamentId());
        J8BenchmarkAuditService.Session audit = benchmarkAudit.start(
                claim.requestId(),
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY.maximumUnits(),
                Optional.of(claim.collectionDate()));
        J8BenchmarkAuditService.Unit auditUnit = audit.declare(
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                request.requestKey(),
                Optional.empty(),
                OptionalLong.empty());
        try {
            TournamentEventDiscoveryResult result = executeGuarded(
                    claim,
                    request,
                    audit,
                    auditUnit);
            return finishAudit(audit, result);
        }
        catch (BenchmarkAuditException exception) {
            throw exception.auditFailure();
        }
        catch (RuntimeException exception) {
            finishFailedAudit(audit, "PROCESSING_FAILURE", exception);
            throw exception;
        }
    }

    private TournamentEventDiscoveryResult executeGuarded(
            TournamentEventDiscoveryExecutionClaim claim,
            TournamentScheduledEventsProviderRequest request,
            J8BenchmarkAuditService.Session audit,
            J8BenchmarkAuditService.Unit auditUnit) {
        if (!controlService.executionMayContinue(claim.requestId())) {
            audit.resolveFailure(auditUnit, "OPERATOR_STOP");
            return failed(claim.requestId(), "OPERATOR_STOP", 0, false, null, null, 0);
        }
        audit.reach(auditUnit);

        CachedTournamentScheduledEventsResponse cached;
        try {
            cached = cache.findFreshParsed(
                    request,
                    clock.instant(),
                    CACHE_TTL,
                    TournamentScheduledEventsV1Parser.PARSER_VERSION).orElse(null);
        }
        catch (RuntimeException exception) {
            return failAndLock(
                    claim.requestId(), "CACHE_LOOKUP_ERROR", 0, false, null, null, 0);
        }

        TournamentScheduledEventsTransportResponse response = null;
        RawSnapshotPersistenceResult rawPersistence = null;
        boolean cacheHit = cached != null;
        int providerCalls = 0;
        if (cached != null) {
            response = cached.asTransportResponse();
            rawPersistence = cached.asPersistenceResult();
            audit.captureSnapshot(
                    auditUnit,
                    rawPersistence,
                    TournamentScheduledEventsV1Parser.PARSER_VERSION);
        }
        else {
            try (var providerLease = requestCoordinator.acquireCampaign(claim.requestId())) {
                if (!controlService.executionMayContinue(claim.requestId())) {
                    audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                    return failAndLock(
                            claim.requestId(), "OPERATOR_STOP", 0, false, null, null, 0);
                }
                TournamentScheduledEventsProviderTransport.Campaign providerCampaign = null;
                try {
                    providerCampaign = transport.openCampaign(claim.requestId());
                    if (!controlService.executionMayContinue(claim.requestId())) {
                        audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                        return failAndLock(
                                claim.requestId(),
                                "OPERATOR_STOP",
                                0,
                                false,
                                null,
                                null,
                                0);
                    }
                    providerLease.beginRequest();
                    if (!controlService.executionMayContinue(claim.requestId())) {
                        audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                        return failAndLock(
                                claim.requestId(),
                                "OPERATOR_STOP",
                                0,
                                false,
                                null,
                                null,
                                0);
                    }
                    try {
                        audit.startAttempt(auditUnit);
                    }
                    catch (RuntimeException auditFailure) {
                        failAndLock(
                                claim.requestId(),
                                "BENCHMARK_AUDIT_FAILURE",
                                providerCalls,
                                false,
                                null,
                                null,
                                0);
                        throw new BenchmarkAuditException(auditFailure);
                    }
                    try {
                        response = providerCampaign.execute(request);
                    }
                    finally {
                        providerCalls = 1;
                    }
                    audit.captureResponse(
                            auditUnit,
                            response.httpStatus(),
                            response.latency().toMillis());
                    try {
                        rawPersistence = rawSnapshotStore.save(rawOnly(response));
                        audit.captureSnapshot(
                                auditUnit,
                                rawPersistence,
                                TournamentScheduledEventsV1Parser.PARSER_VERSION);
                    }
                    catch (RuntimeException exception) {
                        TournamentEventDiscoveryResult result = failAndLock(
                                claim.requestId(),
                                "RAW_PERSISTENCE_ERROR",
                                1,
                                false,
                                null,
                                response,
                                0);
                        audit.resolveFailure(auditUnit, "RAW_PERSISTENCE_ERROR");
                        return result;
                    }
                }
                finally {
                    if (providerCampaign != null) {
                        providerCampaign.close();
                    }
                }
            }
            catch (ManualProviderRequestCoordinator.CoordinationException exception) {
                return failAndLock(
                        claim.requestId(),
                        "MINIMUM_DELAY_INTERRUPTED",
                        providerCalls,
                        false,
                        null,
                        null,
                        0);
            }
            catch (ScheduledEventsTransportException exception) {
                String code = controlService.executionMayContinue(claim.requestId())
                        ? "TRANSPORT_" + exception.failure().name()
                        : "OPERATOR_STOP";
                TournamentEventDiscoveryResult result = failAndLock(
                        claim.requestId(),
                        code,
                        providerCalls,
                        false,
                        rawPersistence,
                        response,
                        0);
                audit.resolveFailureIfPending(auditUnit, code);
                return result;
            }
            catch (BenchmarkAuditException exception) {
                throw exception;
            }
            catch (RuntimeException exception) {
                String code = controlService.executionMayContinue(claim.requestId())
                        ? "TRANSPORT_IO_FAILURE"
                        : "OPERATOR_STOP";
                TournamentEventDiscoveryResult result = failAndLock(
                        claim.requestId(),
                        code,
                        providerCalls,
                        false,
                        rawPersistence,
                        response,
                        0);
                audit.resolveFailureIfPending(auditUnit, code);
                return result;
            }
        }

        TournamentEventDiscoverySource source = cacheHit
                ? TournamentEventDiscoverySource.CACHE
                : TournamentEventDiscoverySource.PROVIDER;
        TournamentEventDiscoveryResult result = processResponse(
                claim,
                request,
                response,
                rawPersistence,
                providerCalls,
                cacheHit,
                !cacheHit,
                source);
        resolveAuditResult(audit, auditUnit, result);
        return result;
    }

    public TournamentEventDiscoveryResult importLocalJson(
            TournamentEventDiscoveryLocalImportClaim claim,
            RawPayloadEvidence payload) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(payload, "payload");
        TournamentScheduledEventsProviderRequest request =
                new TournamentScheduledEventsProviderRequest(
                        claim.providerOrigin(),
                        claim.collectionDate(),
                        claim.selection().uniqueTournamentId());
        J8BenchmarkAuditService.Session audit = benchmarkAudit.start(
                claim.requestId(),
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY,
                J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT,
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY.maximumUnits(),
                Optional.of(claim.collectionDate()));
        J8BenchmarkAuditService.Unit auditUnit = audit.declare(
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                request.requestKey(),
                Optional.empty(),
                OptionalLong.empty());
        if (!controlService.executionMayContinue(claim.requestId())) {
            audit.resolveFailure(auditUnit, "OPERATOR_STOP");
            return finishAudit(audit, failed(
                    claim.requestId(),
                    "OPERATOR_STOP",
                    0,
                    false,
                    null,
                    null,
                    0,
                    TournamentEventDiscoverySource.LOCAL_JSON_IMPORT));
        }
        audit.reach(auditUnit);

        Instant importedAt = clock.instant();
        TournamentScheduledEventsTransportResponse response =
                new TournamentScheduledEventsTransportResponse(
                        request.requestKey(),
                        importedAt,
                        importedAt,
                        200,
                        "application/json",
                        Duration.ZERO,
                        payload);
        RawSnapshotPersistenceResult rawPersistence;
        try {
            rawPersistence = rawSnapshotStore.save(rawOnly(
                    response,
                    RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT));
            audit.captureSnapshot(
                    auditUnit,
                    rawPersistence,
                    TournamentScheduledEventsV1Parser.PARSER_VERSION);
        }
        catch (RuntimeException exception) {
            TournamentEventDiscoveryResult result = failAndLock(
                    claim.requestId(),
                    "LOCAL_IMPORT_PERSISTENCE_ERROR",
                    0,
                    false,
                    null,
                    response,
                    0,
                    TournamentEventDiscoverySource.LOCAL_JSON_IMPORT);
            audit.resolveFailure(auditUnit, "LOCAL_IMPORT_PERSISTENCE_ERROR");
            try {
                return finishAudit(audit, result);
            }
            catch (RuntimeException auditFailure) {
                if (auditFailure != exception) {
                    exception.addSuppressed(auditFailure);
                }
                throw exception;
            }
        }
        try {
            TournamentEventDiscoveryResult result = processResponse(
                    claim,
                    request,
                    response,
                    rawPersistence,
                    0,
                    false,
                    false,
                    TournamentEventDiscoverySource.LOCAL_JSON_IMPORT);
            resolveAuditResult(audit, auditUnit, result);
            return finishAudit(audit, result);
        }
        catch (RuntimeException exception) {
            finishFailedAudit(audit, "PROCESSING_FAILURE", exception);
            throw exception;
        }
    }

    private TournamentEventDiscoveryResult processResponse(
            TournamentEventDiscoveryClaim claim,
            TournamentScheduledEventsProviderRequest request,
            TournamentScheduledEventsTransportResponse response,
            RawSnapshotPersistenceResult rawPersistence,
            int providerCalls,
            boolean cacheHit,
            boolean cacheWriteAllowed,
            TournamentEventDiscoverySource source) {

        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(
                    claim.requestId(), "OPERATOR_STOP", providerCalls, cacheHit,
                    rawPersistence, response, 0, source);
        }

        if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
            String code = httpTerminalCode(response.httpStatus());
            RawSnapshotSchemaStatus schemaStatus = response.httpStatus() == 404
                    ? RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE
                    : RawSnapshotSchemaStatus.TRANSPORT_ERROR;
            if (!classifyInsertedSafely(
                    rawPersistence,
                    schemaStatus,
                    code)) {
                code = "RAW_CLASSIFICATION_ERROR";
            }
            return failAndLock(
                    claim.requestId(), code, providerCalls, cacheHit,
                    rawPersistence, response, 0, source);
        }

        TournamentScheduledEventsParseResult parsed;
        try {
            parsed = parser.parse(
                    response.payload().bytes(),
                    response.contentType(),
                    new TournamentScheduledEventsParseEvidence(
                            "snapshot:" + rawPersistence.snapshotId(),
                            response.payload().sha256(),
                            Optional.empty(),
                            response.receivedAt(),
                            TournamentScheduledEventsV1Parser.PARSER_VERSION));
        }
        catch (RuntimeException exception) {
            if (!controlService.executionMayContinue(claim.requestId())) {
                return failed(
                        claim.requestId(), "OPERATOR_STOP", providerCalls, cacheHit,
                        rawPersistence, response, 0, source);
            }
            classifySchemaIncompatibleSafely(rawPersistence);
            return failAndLock(
                    claim.requestId(), "PARSER_FAILURE", providerCalls, cacheHit,
                    rawPersistence, response, 0, source);
        }
        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(
                    claim.requestId(), "OPERATOR_STOP", providerCalls, cacheHit,
                    rawPersistence, response, parsed.warnings().size(), source);
        }
        if (parsed.status() != TournamentScheduledEventsParseStatus.PARSED) {
            RawSnapshotSchemaStatus schemaStatus = parsed.status()
                    == TournamentScheduledEventsParseStatus.UNEXPECTED_CONTENT
                            ? RawSnapshotSchemaStatus.UNEXPECTED_CONTENT
                            : RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
            String code = parsed.status().name();
            if (!classifyInsertedSafely(
                    rawPersistence,
                    schemaStatus,
                    schemaStatus.name())) {
                code = "RAW_CLASSIFICATION_ERROR";
            }
            return failAndLock(
                    claim.requestId(), code, providerCalls, cacheHit,
                    rawPersistence, response, parsed.warnings().size(), source);
        }

        TournamentScheduledEventsProjection projection;
        try {
            projection = projectionService.project(
                    claim.collectionDate(),
                    claim.selection(),
                    parsed.candidates().orElseThrow());
        }
        catch (TournamentScheduledEventsProjectionException exception) {
            if (!controlService.executionMayContinue(claim.requestId())) {
                return failed(
                        claim.requestId(), "OPERATOR_STOP", providerCalls, cacheHit,
                        rawPersistence, response, parsed.warnings().size(), source);
            }
            String code = exception.error().name();
            classifySchemaIncompatibleSafely(rawPersistence);
            return failAndLock(
                    claim.requestId(), code, providerCalls, cacheHit,
                    rawPersistence, response, parsed.warnings().size(), source);
        }
        catch (RuntimeException exception) {
            if (!controlService.executionMayContinue(claim.requestId())) {
                return failed(
                        claim.requestId(), "OPERATOR_STOP", providerCalls, cacheHit,
                        rawPersistence, response, parsed.warnings().size(), source);
            }
            classifySchemaIncompatibleSafely(rawPersistence);
            return failAndLock(
                    claim.requestId(), "PROJECTION_FAILURE", providerCalls, cacheHit,
                    rawPersistence, response, parsed.warnings().size(), source);
        }

        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(
                    claim.requestId(), "OPERATOR_STOP", providerCalls, cacheHit,
                    rawPersistence, response, parsed.warnings().size(), source);
        }

        if (!cacheHit) {
            if (!classifyInsertedSafely(
                    rawPersistence,
                    RawSnapshotSchemaStatus.PARSED,
                    null)) {
                return failAndLock(
                        claim.requestId(), "RAW_CLASSIFICATION_ERROR", providerCalls,
                        false, rawPersistence, response, parsed.warnings().size(), source);
            }
        }
        if (cacheWriteAllowed) {
            try {
                controlService.executeWhileActive(claim.requestId(), () -> {
                    cache.recordParsed(
                            request,
                            response,
                            rawPersistence,
                            TournamentScheduledEventsV1Parser.PARSER_VERSION);
                    return Boolean.TRUE;
                });
            }
            catch (TournamentEventDiscoveryControlException exception) {
                return failed(
                        claim.requestId(), "OPERATOR_STOP", providerCalls, false,
                        rawPersistence, response, parsed.warnings().size(), source);
            }
            catch (RuntimeException exception) {
                return failAndLock(
                        claim.requestId(), "CACHE_WRITE_ERROR", providerCalls,
                        false, rawPersistence, response, parsed.warnings().size(), source);
            }
        }

        if (!projection.normalizationAllowed()) {
            return failWithProjection(
                    claim.requestId(),
                    "EVENT_COUNT_MISMATCH",
                    providerCalls,
                    cacheHit,
                    rawPersistence,
                    response,
                    projection,
                    parsed.warnings().size(),
                    source);
        }

        TournamentCanonicalizationResult canonicalization;
        try {
            canonicalization = controlService.executeAndComplete(
                    claim.requestId(),
                    () -> persistenceService.persist(
                            projection,
                            rawPersistence.snapshotId(),
                            rawPersistence.payloadSha256(),
                            response.receivedAt()));
        }
        catch (TournamentEventDiscoveryControlException exception) {
            return failed(
                    claim.requestId(), "OPERATOR_STOP", providerCalls, cacheHit,
                    rawPersistence, response, parsed.warnings().size(), source);
        }
        catch (RuntimeException exception) {
            return failWithProjection(
                    claim.requestId(),
                    "NORMALIZATION_PERSISTENCE_ERROR",
                    providerCalls,
                    cacheHit,
                    rawPersistence,
                    response,
                    projection,
                    parsed.warnings().size(),
                    source);
        }
        return new TournamentEventDiscoveryResult(
                claim.requestId(),
                true,
                "COMPLETED",
                providerCalls,
                cacheHit,
                source,
                rawPersistence.snapshotId(),
                rawPersistence.payloadSha256(),
                rawPersistence.payloadSizeBytes(),
                projection.countStatus(),
                boxed(projection.expectedCount()),
                projection.actualCount(),
                projection.exactDuplicateCount(),
                projection.excludedOtherTournamentCount(),
                projection.excludedOutsideDateCount(),
                canonicalization.insertedObservations(),
                canonicalization.deduplicatedObservations(),
                parsed.warnings().size(),
                canonicalization.events());
    }

    private TournamentEventDiscoveryResult failWithProjection(
            UUID requestId,
            String code,
            int providerCalls,
            boolean cacheHit,
            RawSnapshotPersistenceResult rawPersistence,
            TournamentScheduledEventsTransportResponse response,
            TournamentScheduledEventsProjection projection,
            int warningCount,
            TournamentEventDiscoverySource source) {
        lockIfExecuting(requestId, code);
        return new TournamentEventDiscoveryResult(
                requestId,
                false,
                code,
                providerCalls,
                cacheHit,
                source,
                rawPersistence.snapshotId(),
                rawPersistence.payloadSha256(),
                rawPersistence.payloadSizeBytes(),
                projection.countStatus(),
                boxed(projection.expectedCount()),
                projection.actualCount(),
                projection.exactDuplicateCount(),
                projection.excludedOtherTournamentCount(),
                projection.excludedOutsideDateCount(),
                0,
                0,
                warningCount,
                List.of());
    }

    private TournamentEventDiscoveryResult failAndLock(
            UUID requestId,
            String code,
            int providerCalls,
            boolean cacheHit,
            RawSnapshotPersistenceResult rawPersistence,
            TournamentScheduledEventsTransportResponse response,
            int warningCount) {
        TournamentEventDiscoverySource source = cacheHit
                ? TournamentEventDiscoverySource.CACHE
                : TournamentEventDiscoverySource.PROVIDER;
        return failAndLock(
                requestId,
                code,
                providerCalls,
                cacheHit,
                rawPersistence,
                response,
                warningCount,
                source);
    }

    private TournamentEventDiscoveryResult failAndLock(
            UUID requestId,
            String code,
            int providerCalls,
            boolean cacheHit,
            RawSnapshotPersistenceResult rawPersistence,
            TournamentScheduledEventsTransportResponse response,
            int warningCount,
            TournamentEventDiscoverySource source) {
        lockIfExecuting(requestId, code);
        return failed(
                requestId, code, providerCalls, cacheHit,
                rawPersistence, response, warningCount, source);
    }

    private void lockIfExecuting(UUID requestId, String code) {
        if (controlService.executionMayContinue(requestId)) {
            try {
                controlService.fail(requestId, code);
            }
            catch (TournamentEventDiscoveryControlException exception) {
                // An operator stop won the race; the minimized result remains failed.
            }
        }
    }

    private static TournamentEventDiscoveryResult failed(
            UUID requestId,
            String code,
            int providerCalls,
            boolean cacheHit,
            RawSnapshotPersistenceResult rawPersistence,
            TournamentScheduledEventsTransportResponse response,
            int warningCount) {
        TournamentEventDiscoverySource source = cacheHit
                ? TournamentEventDiscoverySource.CACHE
                : TournamentEventDiscoverySource.PROVIDER;
        return failed(
                requestId,
                code,
                providerCalls,
                cacheHit,
                rawPersistence,
                response,
                warningCount,
                source);
    }

    private static TournamentEventDiscoveryResult failed(
            UUID requestId,
            String code,
            int providerCalls,
            boolean cacheHit,
            RawSnapshotPersistenceResult rawPersistence,
            TournamentScheduledEventsTransportResponse response,
            int warningCount,
            TournamentEventDiscoverySource source) {
        return new TournamentEventDiscoveryResult(
                requestId,
                false,
                code,
                providerCalls,
                cacheHit,
                source,
                rawPersistence == null ? 0 : rawPersistence.snapshotId(),
                rawPersistence == null ? null : rawPersistence.payloadSha256(),
                rawPersistence == null ? 0 : rawPersistence.payloadSizeBytes(),
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                warningCount,
                List.of());
    }

    private static RawManualCallSnapshot rawOnly(
            TournamentScheduledEventsTransportResponse response) {
        return rawOnly(response, RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT);
    }

    private static RawManualCallSnapshot rawOnly(
            TournamentScheduledEventsTransportResponse response,
            RawSnapshotAcquisitionMode acquisitionMode) {
        return new RawManualCallSnapshot(
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                acquisitionMode,
                response.requestKey(),
                response.requestedAt(),
                response.receivedAt(),
                response.httpStatus(),
                response.contentType(),
                response.latency(),
                response.payload(),
                TournamentScheduledEventsV1Parser.PARSER_VERSION,
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
            RawSnapshotPersistenceResult persistence,
            RawSnapshotSchemaStatus status,
            String errorCode) {
        return switch (persistence.outcome()) {
            case INSERTED -> classifySafely(
                    persistence.snapshotId(), status, errorCode);
            case DEDUPLICATED, CACHE_HIT -> true;
        };
    }

    private boolean classifySchemaIncompatibleSafely(
            RawSnapshotPersistenceResult persistence) {
        return classifyInsertedSafely(
                persistence,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE.name());
    }

    private static Integer boxed(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static void resolveAuditResult(
            J8BenchmarkAuditService.Session audit,
            J8BenchmarkAuditService.Unit unit,
            TournamentEventDiscoveryResult result) {
        if (result.completed()) {
            audit.resolve(
                    unit,
                    benchmarkSource(result.source()),
                    J8BenchmarkOutcomeType.PARSED,
                    Optional.of(RawSnapshotSchemaStatus.PARSED),
                    result.parserWarningCount(),
                    Optional.empty(),
                    OptionalInt.empty(),
                    Optional.empty());
            return;
        }

        String terminalCode = result.terminalCode();
        if (terminalCode.equals("PARSER_FAILURE")
                || terminalCode.equals("PROJECTION_FAILURE")
                || terminalCode.equals("UNIQUE_TOURNAMENT_ID_MISMATCH")
                || terminalCode.equals("CONFLICTING_EVENT_DUPLICATE")
                || terminalCode.equals("EVENT_COUNT_MISMATCH")) {
            audit.resolve(
                    unit,
                    benchmarkSource(result.source()),
                    J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE,
                    Optional.of(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE),
                    result.parserWarningCount(),
                    Optional.empty(),
                    OptionalInt.empty(),
                    Optional.of(terminalCode));
            return;
        }
        audit.resolveFailure(unit, terminalCode);
    }

    private static J8BenchmarkResolutionSource benchmarkSource(
            TournamentEventDiscoverySource source) {
        return switch (source) {
            case PROVIDER -> J8BenchmarkResolutionSource.PROVIDER;
            case CACHE -> J8BenchmarkResolutionSource.CACHE;
            case LOCAL_JSON_IMPORT ->
                    J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT;
        };
    }

    private static TournamentEventDiscoveryResult finishAudit(
            J8BenchmarkAuditService.Session audit,
            TournamentEventDiscoveryResult result) {
        if (result.completed()) {
            audit.finish(
                    J8BenchmarkCampaignTerminalState.COMPLETED,
                    Optional.empty());
            return result;
        }
        String terminalCode = result.terminalCode();
        J8BenchmarkCampaignTerminalState state = terminalCode.contains("OPERATOR_STOP")
                ? J8BenchmarkCampaignTerminalState.CANCELLED
                : J8BenchmarkCampaignTerminalState.FAILED;
        audit.finish(state, Optional.of(terminalCode));
        return result;
    }

    private static void finishFailedAudit(
            J8BenchmarkAuditService.Session audit,
            String terminalCode,
            RuntimeException original) {
        try {
            audit.finish(
                    J8BenchmarkCampaignTerminalState.FAILED,
                    Optional.of(terminalCode));
        }
        catch (RuntimeException auditFailure) {
            if (auditFailure != original) {
                original.addSuppressed(auditFailure);
            }
        }
    }

    private static String httpTerminalCode(int httpStatus) {
        if (httpStatus == 404) {
            return "ENDPOINT_UNAVAILABLE";
        }
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

    private static final class BenchmarkAuditException extends RuntimeException {

        private final RuntimeException auditFailure;

        private BenchmarkAuditException(RuntimeException auditFailure) {
            super(auditFailure);
            this.auditFailure = auditFailure;
        }

        private RuntimeException auditFailure() {
            return auditFailure;
        }
    }
}
