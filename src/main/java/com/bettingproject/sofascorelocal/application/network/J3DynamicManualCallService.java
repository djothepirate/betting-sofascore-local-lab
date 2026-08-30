package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3CachedScheduledEventsPage;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionResult;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedCollectionEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.J3ScheduledEventsPageCache;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.ScheduledEventsProviderPageTransport;

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
public class J3DynamicManualCallService {

    private final J3ManualCallControlService controlService;
    private final ScheduledEventsProviderPageTransport transport;
    private final J3ScheduledEventsPageCache pageCache;
    private final ScheduledEventsV1Parser parser;
    private final J3ScheduledEventsOutcomeProcessor outcomeProcessor;
    private final J3SingleCallGuard callGuard;
    private final J3ManualCollectionEvidenceService evidenceService;
    private final Clock clock;
    private final Duration cacheTtl;
    private final ManualProviderRequestCoordinator requestCoordinator;
    private final J8BenchmarkAuditService benchmarkAudit;

    @Autowired
    public J3DynamicManualCallService(
            J3ManualCallControlService controlService,
            ScheduledEventsProviderPageTransport transport,
            RawManualCallSnapshotStore snapshotStore,
            J3ScheduledEventsPageCache pageCache,
            J3ManualCollectionEvidenceService evidenceService,
            SofascoreEndpointCatalog endpointCatalog,
            SofascoreProperties properties,
            J3SingleCallGuard callGuard,
            ManualProviderRequestCoordinator requestCoordinator,
            J8BenchmarkAuditService benchmarkAudit) {
        this(
                controlService,
                transport,
                new J3ScheduledEventsOutcomeProcessor(
                        snapshotStore,
                        new ScheduledEventsV1Parser(),
                        controlService.circuit()),
                pageCache,
                new ScheduledEventsV1Parser(),
                callGuard,
                evidenceService,
                Clock.systemUTC(),
                endpointCatalog.get(SofascoreEndpointType.SCHEDULED_EVENTS).cacheTtl(),
                properties.getMinimumDelay(),
                duration -> Thread.sleep(duration),
                requestCoordinator,
                benchmarkAudit);
    }

    J3DynamicManualCallService(
            J3ManualCallControlService controlService,
            ScheduledEventsProviderPageTransport transport,
            J3ScheduledEventsOutcomeProcessor outcomeProcessor,
            J3ScheduledEventsPageCache pageCache,
            ScheduledEventsV1Parser parser,
            J3SingleCallGuard callGuard,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            Duration cacheTtl,
            Duration minimumDelay,
            InterPageDelay interPageDelay) {
        this(
                controlService,
                transport,
                outcomeProcessor,
                pageCache,
                parser,
                callGuard,
                evidenceService,
                clock,
                cacheTtl,
                minimumDelay,
                interPageDelay,
                new ManualProviderRequestCoordinator(
                        clock,
                        minimumDelay,
                        duration -> awaitForCoordinator(interPageDelay, duration)),
                J8BenchmarkAuditService.disabled(clock));
    }

    J3DynamicManualCallService(
            J3ManualCallControlService controlService,
            ScheduledEventsProviderPageTransport transport,
            J3ScheduledEventsOutcomeProcessor outcomeProcessor,
            J3ScheduledEventsPageCache pageCache,
            ScheduledEventsV1Parser parser,
            J3SingleCallGuard callGuard,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            Duration cacheTtl,
            Duration minimumDelay,
            InterPageDelay interPageDelay,
            ManualProviderRequestCoordinator requestCoordinator) {
        this(
                controlService,
                transport,
                outcomeProcessor,
                pageCache,
                parser,
                callGuard,
                evidenceService,
                clock,
                cacheTtl,
                minimumDelay,
                interPageDelay,
                requestCoordinator,
                J8BenchmarkAuditService.disabled(clock));
    }

    J3DynamicManualCallService(
            J3ManualCallControlService controlService,
            ScheduledEventsProviderPageTransport transport,
            J3ScheduledEventsOutcomeProcessor outcomeProcessor,
            J3ScheduledEventsPageCache pageCache,
            ScheduledEventsV1Parser parser,
            J3SingleCallGuard callGuard,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            Duration cacheTtl,
            Duration minimumDelay,
            InterPageDelay interPageDelay,
            ManualProviderRequestCoordinator requestCoordinator,
            J8BenchmarkAuditService benchmarkAudit) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.outcomeProcessor = Objects.requireNonNull(outcomeProcessor, "outcomeProcessor");
        this.pageCache = Objects.requireNonNull(pageCache, "pageCache");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.callGuard = Objects.requireNonNull(callGuard, "callGuard");
        this.evidenceService = Objects.requireNonNull(evidenceService, "evidenceService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl");
        Objects.requireNonNull(minimumDelay, "minimumDelay");
        Objects.requireNonNull(interPageDelay, "interPageDelay");
        this.requestCoordinator = Objects.requireNonNull(
                requestCoordinator, "requestCoordinator");
        this.benchmarkAudit = Objects.requireNonNull(benchmarkAudit, "benchmarkAudit");
        if (minimumDelay.compareTo(Duration.ofSeconds(3)) < 0) {
            throw new IllegalArgumentException("minimumDelay must be at least three seconds");
        }
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("cacheTtl must be positive");
        }
    }

    public J3ManualCallExecutionResult execute(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        var permit = callGuard.tryAcquire();
        if (permit.isEmpty()) {
            throw new J3ManualCallControlException(
                    J3ManualCallControlError.EXECUTION_ALREADY_STARTED);
        }

        CampaignResources resources = new CampaignResources();
        J8BenchmarkAuditService.Session audit = null;
        try (J3SingleCallGuard.Permit ignored = permit.orElseThrow()) {
            J3ManualCallExecutionClaim claim = controlService.claimExecution(requestId);
            audit = benchmarkAudit.start(
                    requestId,
                    J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                    J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                    J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS.maximumUnits(),
                    Optional.of(claim.date()));
            List<J3MinimizedPageEvidence> pageAttempts = new ArrayList<>();
            int initialCompletedPages = 0;
            int completedPages = initialCompletedPages;
            int providerRequests = 0;
            int cacheHits = 0;

            for (int page = claim.firstPage();
                    page <= ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE;
                    page++) {
                ScheduledEventsProviderPageRequest request =
                        new ScheduledEventsProviderPageRequest(
                                claim.providerOrigin(), claim.date(), page);
                J8BenchmarkAuditService.Unit auditUnit = audit.declare(
                        page,
                        SofascoreEndpointType.SCHEDULED_EVENTS,
                        request.requestKey(),
                        Optional.empty(),
                        OptionalLong.empty());
                if (!controlService.executionMayContinue(requestId)) {
                    audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                    return publishAlreadyLockedFailure(
                            audit,
                            resources,
                            claim.date(), initialCompletedPages, completedPages, page,
                            "GLOBAL_STOP_OR_CIRCUIT_BLOCK", providerRequests, cacheHits,
                            pageAttempts);
                }
                audit.reach(auditUnit);

                Instant cacheEvaluatedAt = clock.instant();
                var cachedPage = pageCache.findFreshParsed(
                        request,
                        cacheEvaluatedAt,
                        cacheTtl,
                        ScheduledEventsV1Parser.PARSER_VERSION);
                if (cachedPage.isPresent()) {
                    J3CachedScheduledEventsPage cached = cachedPage.orElseThrow();
                    audit.captureSnapshot(
                            auditUnit,
                            new RawSnapshotPersistenceResult(
                                    cached.snapshotId(),
                                    RawSnapshotPersistenceOutcome.CACHE_HIT,
                                    cached.payload().sha256(),
                                    cached.payload().sizeBytes(),
                                    OptionalLong.empty()),
                            ScheduledEventsV1Parser.PARSER_VERSION);
                    boolean hasNextPage;
                    try {
                        hasNextPage = reparseCachedPage(cached);
                    }
                    catch (RuntimeException exception) {
                        audit.resolveFailure(auditUnit, "CACHE_REPARSE_INCOMPATIBLE");
                        throw exception;
                    }
                    cacheHits++;
                    pageAttempts.add(J3MinimizedPageEvidence.cached(
                            page,
                            cachedPage.orElseThrow(),
                            hasNextPage,
                            clock.instant()));
                    controlService.recordPageCompleted(requestId, page);
                    completedPages = page;
                    audit.resolve(
                            auditUnit,
                            J8BenchmarkResolutionSource.CACHE,
                            J8BenchmarkOutcomeType.PARSED,
                            Optional.of(RawSnapshotSchemaStatus.PARSED),
                            0,
                            Optional.empty(),
                            OptionalInt.empty(),
                            Optional.empty());
                    if (!hasNextPage) {
                        controlService.completeExecution(requestId);
                        return publishAndLock(
                                audit,
                                resources,
                                requestId,
                                claim.date(),
                                initialCompletedPages,
                                J3ManualCallExecutionResult.successful(
                                        completedPages, providerRequests, cacheHits),
                                pageAttempts);
                    }
                    if (page == ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
                        int blockedNextPage = page + 1;
                        String terminalCode = "PAGINATION_LIMIT_REACHED";
                        controlService.failExecution(
                                requestId, blockedNextPage, terminalCode);
                        return publishAndLock(
                                audit,
                                resources,
                                requestId,
                                claim.date(),
                                initialCompletedPages,
                                J3ManualCallExecutionResult.failed(
                                        completedPages,
                                        blockedNextPage,
                                        terminalCode,
                                        providerRequests,
                                        cacheHits),
                                pageAttempts);
                    }
                    continue;
                }

                if (!controlService.executionMayContinue(requestId)) {
                    audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                    return publishAlreadyLockedFailure(
                            audit,
                            resources,
                            claim.date(), initialCompletedPages, completedPages, page,
                            "GLOBAL_STOP_OR_CIRCUIT_BLOCK", providerRequests, cacheHits,
                            pageAttempts);
                }

                Instant attemptedAt = clock.instant();
                boolean providerRequestExecuted = false;
                try {
                    ScheduledEventsTransportResponse response;
                    if (resources.lease == null) {
                        resources.lease = requestCoordinator.acquireCampaign(requestId);
                        if (!controlService.executionMayContinue(requestId)) {
                            audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                            return publishAlreadyLockedFailure(
                                    audit,
                                    resources,
                                    claim.date(),
                                    initialCompletedPages,
                                    completedPages,
                                    page,
                                    "GLOBAL_STOP_OR_CIRCUIT_BLOCK",
                                    providerRequests,
                                    cacheHits,
                                    pageAttempts);
                        }
                        resources.campaign = transport.openCampaign(requestId);
                    }
                    resources.lease.beginRequest();
                    if (!controlService.executionMayContinue(requestId)) {
                        audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                        return publishAlreadyLockedFailure(
                                audit,
                                resources,
                                claim.date(),
                                initialCompletedPages,
                                completedPages,
                                page,
                                "GLOBAL_STOP_OR_CIRCUIT_BLOCK",
                                providerRequests,
                                cacheHits,
                                pageAttempts);
                    }
                    attemptedAt = clock.instant();
                    audit.startAttempt(auditUnit);
                    try {
                        response = resources.campaign.execute(request);
                    }
                    finally {
                        providerRequests++;
                        providerRequestExecuted = true;
                    }
                    audit.captureResponse(
                            auditUnit,
                            response.httpStatus(),
                            response.latency().toMillis());
                    J8BenchmarkAuditService.Session activeAudit = audit;
                    J3ScheduledEventsOutcome outcome;
                    try {
                        outcome = outcomeProcessor.processResponse(
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
                    if (outcome.circuit().state() == J3CircuitState.CLOSED) {
                        try {
                            pageCache.recordParsed(
                                    request,
                                    response,
                                    outcome.persistence().orElseThrow(),
                                    ScheduledEventsV1Parser.PARSER_VERSION);
                        }
                        catch (RuntimeException exception) {
                            audit.resolveFailure(auditUnit, "CACHE_WRITE_ERROR");
                            throw exception;
                        }
                    }
                    else {
                        audit.resolveFailure(
                                auditUnit,
                                outcome.circuit().reason().name());
                    }
                    String pageTerminalCode = outcome.circuit().state() == J3CircuitState.CLOSED
                            ? null
                            : outcome.circuit().reason().name();
                    pageAttempts.add(J3MinimizedPageEvidence.recorded(
                            page,
                            response,
                            outcome.persistence().orElseThrow(),
                            schemaStatus(outcome),
                            outcome.hasNextPage().orElse(null),
                            pageTerminalCode));
                    if (controlService.snapshot().globalStopActive()) {
                        if (outcome.circuit().state() == J3CircuitState.CLOSED) {
                            audit.resolveFailure(auditUnit, "OPERATOR_STOP");
                        }
                        return publishAlreadyLockedFailure(
                                audit,
                                resources,
                                claim.date(),
                                initialCompletedPages,
                                completedPages,
                                page,
                                "GLOBAL_STOP_OR_CIRCUIT_BLOCK",
                                providerRequests,
                                cacheHits,
                                pageAttempts);
                    }
                    if (outcome.circuit().state() != J3CircuitState.CLOSED) {
                        String terminalCode = outcome.circuit().reason().name();
                        controlService.failExecution(requestId, page, terminalCode);
                        return publishAndLock(
                                audit,
                                resources,
                                requestId,
                                claim.date(),
                                initialCompletedPages,
                                J3ManualCallExecutionResult.failed(
                                        completedPages,
                                        page,
                                        terminalCode,
                                        providerRequests,
                                        cacheHits),
                                pageAttempts);
                    }
                    controlService.recordPageCompleted(requestId, page);
                    completedPages = page;
                    audit.resolve(
                            auditUnit,
                            J8BenchmarkResolutionSource.PROVIDER,
                            J8BenchmarkOutcomeType.PARSED,
                            Optional.of(RawSnapshotSchemaStatus.PARSED),
                            0,
                            Optional.empty(),
                            OptionalInt.empty(),
                            Optional.empty());
                    boolean hasNextPage = outcome.hasNextPage().orElseThrow(
                            () -> new IllegalStateException(
                                    "a parsed page must expose hasNextPage"));
                    if (!hasNextPage) {
                        controlService.completeExecution(requestId);
                        return publishAndLock(
                                audit,
                                resources,
                                requestId,
                                claim.date(),
                                initialCompletedPages,
                                J3ManualCallExecutionResult.successful(
                                        completedPages, providerRequests, cacheHits),
                                pageAttempts);
                    }
                    if (page == ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
                        int blockedNextPage = page + 1;
                        String terminalCode = "PAGINATION_LIMIT_REACHED";
                        controlService.failExecution(
                                requestId, blockedNextPage, terminalCode);
                        return publishAndLock(
                                audit,
                                resources,
                                requestId,
                                claim.date(),
                                initialCompletedPages,
                                J3ManualCallExecutionResult.failed(
                                        completedPages,
                                        blockedNextPage,
                                        terminalCode,
                                        providerRequests,
                                        cacheHits),
                                pageAttempts);
                    }
                }
                catch (ManualProviderRequestCoordinator.CoordinationException exception) {
                    controlService.stopGlobally();
                    return publishAlreadyLockedFailure(
                            audit,
                            resources,
                            claim.date(), initialCompletedPages, completedPages, page,
                            "MINIMUM_DELAY_INTERRUPTED", providerRequests, cacheHits,
                            pageAttempts);
                }
                catch (ScheduledEventsTransportException exception) {
                    if (!controlService.executionMayContinue(requestId)) {
                        String terminalCode = "OPERATOR_STOP";
                        audit.resolveFailure(auditUnit, terminalCode);
                        pageAttempts.add(J3MinimizedPageEvidence.failedBeforeSnapshot(
                                page, attemptedAt, terminalCode, providerRequestExecuted));
                        return publishAlreadyLockedFailure(
                                audit,
                                resources,
                                claim.date(),
                                initialCompletedPages,
                                completedPages,
                                page,
                                terminalCode,
                                providerRequests,
                                cacheHits,
                                pageAttempts);
                    }
                    J3ScheduledEventsOutcome outcome = outcomeProcessor.processFailure(
                            exception, clock.instant());
                    String terminalCode = outcome.circuit().reason().name();
                    audit.resolveFailure(auditUnit, terminalCode);
                    pageAttempts.add(J3MinimizedPageEvidence.failedBeforeSnapshot(
                            page, attemptedAt, terminalCode, providerRequestExecuted));
                    controlService.failExecution(requestId, page, terminalCode);
                    return publishAndLock(
                            audit,
                            resources,
                            requestId,
                            claim.date(),
                            initialCompletedPages,
                            J3ManualCallExecutionResult.failed(
                                    completedPages,
                                    page,
                                    terminalCode,
                                    providerRequests,
                                    cacheHits),
                            pageAttempts);
                }
            }

            throw new IllegalStateException("dynamic pagination terminated unexpectedly");
        }
        catch (J3ManualCallControlException exception) {
            RuntimeException cleanupFailure = resources.closeSafely();
            String terminalCode = cleanupFailure == null
                    ? exception.error().name()
                    : "PROCESSING_FAILURE";
            suppressCleanupFailure(exception, cleanupFailure);
            finishFailedAudit(audit, terminalCode, exception);
            throw exception;
        }
        catch (RuntimeException exception) {
            if (!controlService.snapshot().globalStopActive()) {
                controlService.stopGlobally();
            }
            RuntimeException cleanupFailure = resources.closeSafely();
            suppressCleanupFailure(exception, cleanupFailure);
            finishFailedAudit(audit, "PROCESSING_FAILURE", exception);
            throw exception;
        }
        finally {
            resources.close();
        }
    }

    private boolean reparseCachedPage(J3CachedScheduledEventsPage cachedPage) {
        var parseResult = parser.parseTransportResponse(cachedPage.asTransportResponse());
        if (parseResult.status() != ScheduledEventsParseStatus.PARSED) {
            throw new IllegalStateException(
                    "a fresh PARSED cache entry must remain compatible with its parser version");
        }
        return parseResult.page().orElseThrow().hasNextPage();
    }

    private J3ManualCallExecutionResult publishAndLock(
            J8BenchmarkAuditService.Session audit,
            CampaignResources resources,
            UUID requestId,
            LocalDate collectionDate,
            int initialCompletedPages,
            J3ManualCallExecutionResult result,
            List<J3MinimizedPageEvidence> pageAttempts) {
        J3ManualCallControlSnapshot locked = controlService.lockAfterCollection(requestId);
        publishEvidence(
                collectionDate,
                initialCompletedPages,
                result,
                pageAttempts,
                locked);
        resources.close();
        finishAudit(audit, result);
        return result;
    }

    private J3ManualCallExecutionResult publishAlreadyLockedFailure(
            J8BenchmarkAuditService.Session audit,
            CampaignResources resources,
            LocalDate collectionDate,
            int initialCompletedPages,
            int completedPages,
            int failedPage,
            String terminalCode,
            int providerRequests,
            int cacheHits,
            List<J3MinimizedPageEvidence> pageAttempts) {
        J3ManualCallExecutionResult result = J3ManualCallExecutionResult.failed(
                completedPages,
                failedPage,
                terminalCode,
                providerRequests,
                cacheHits);
        publishEvidence(
                collectionDate,
                initialCompletedPages,
                result,
                pageAttempts,
                controlService.snapshot());
        resources.close();
        finishAudit(audit, result);
        return result;
    }

    private static void finishAudit(
            J8BenchmarkAuditService.Session audit,
            J3ManualCallExecutionResult result) {
        if (result.completed()) {
            audit.finish(
                    J8BenchmarkCampaignTerminalState.COMPLETED,
                    Optional.empty());
            return;
        }
        String terminalCode = result.terminalCode();
        J8BenchmarkCampaignTerminalState state =
                terminalCode.contains("OPERATOR_STOP")
                                || terminalCode.contains("GLOBAL_STOP")
                        ? J8BenchmarkCampaignTerminalState.CANCELLED
                        : J8BenchmarkCampaignTerminalState.FAILED;
        audit.finish(state, Optional.of(terminalCode));
    }

    private static void finishFailedAudit(
            J8BenchmarkAuditService.Session audit,
            String terminalCode,
            RuntimeException original) {
        if (audit == null) {
            return;
        }
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

    private void publishEvidence(
            LocalDate collectionDate,
            int initialCompletedPages,
            J3ManualCallExecutionResult result,
            List<J3MinimizedPageEvidence> pageAttempts,
            J3ManualCallControlSnapshot terminalSnapshot) {
        evidenceService.publish(new J3MinimizedCollectionEvidence(
                collectionDate,
                terminalSnapshot.intent().state(),
                initialCompletedPages,
                result.completedPages(),
                result.failedPage(),
                result.completed() ? "NONE" : result.terminalCode(),
                clock.instant(),
                terminalSnapshot.globalStopActive(),
                terminalSnapshot.circuitState(),
                terminalSnapshot.circuitReason(),
                cacheTtl,
                pageAttempts));
    }

    private static RawSnapshotSchemaStatus schemaStatus(J3ScheduledEventsOutcome outcome) {
        if (outcome.circuit().state() == J3CircuitState.CLOSED) {
            return RawSnapshotSchemaStatus.PARSED;
        }
        return switch (outcome.circuit().reason()) {
            case ENDPOINT_UNAVAILABLE -> RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE;
            case SCHEMA_INCOMPATIBLE -> RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
            case UNEXPECTED_CONTENT -> RawSnapshotSchemaStatus.UNEXPECTED_CONTENT;
            default -> RawSnapshotSchemaStatus.TRANSPORT_ERROR;
        };
    }

    private static void awaitForCoordinator(
            InterPageDelay delay,
            Duration duration) {
        try {
            delay.await(duration);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("provider request delay interrupted", exception);
        }
    }

    private static void suppressCleanupFailure(
            RuntimeException original,
            RuntimeException cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != original) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private static final class CampaignResources {

        private ManualProviderRequestCoordinator.CampaignLease lease;
        private ScheduledEventsProviderPageTransport.Campaign campaign;

        private RuntimeException closeSafely() {
            ScheduledEventsProviderPageTransport.Campaign campaignToClose = campaign;
            ManualProviderRequestCoordinator.CampaignLease leaseToClose = lease;
            campaign = null;
            lease = null;

            RuntimeException failure = null;
            try {
                if (campaignToClose != null) {
                    campaignToClose.close();
                }
            }
            catch (RuntimeException exception) {
                failure = exception;
            }
            try {
                if (leaseToClose != null) {
                    leaseToClose.close();
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
            return failure;
        }

        private void close() {
            RuntimeException failure = closeSafely();
            if (failure != null) {
                throw new CampaignCleanupException(failure);
            }
        }
    }

    private static final class CampaignCleanupException extends RuntimeException {

        private CampaignCleanupException(RuntimeException cause) {
            super("provider campaign cleanup failed", cause);
        }
    }

    @FunctionalInterface
    interface InterPageDelay {

        void await(Duration duration) throws InterruptedException;
    }
}
