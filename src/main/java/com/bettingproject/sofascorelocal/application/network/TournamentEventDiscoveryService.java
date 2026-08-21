package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsParseEvidence;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.application.event.TournamentCanonicalEventPersistenceService;
import com.bettingproject.sofascorelocal.application.event.TournamentCanonicalizationResult;
import com.bettingproject.sofascorelocal.domain.provider.CachedTournamentScheduledEventsResponse;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryExecutionClaim;
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

    @Autowired
    public TournamentEventDiscoveryService(
            TournamentEventDiscoveryControlService controlService,
            TournamentScheduledEventsProviderTransport transport,
            TournamentScheduledEventsCache cache,
            RawManualCallSnapshotStore rawSnapshotStore,
            TournamentScheduledEventsProjectionService projectionService,
            TournamentCanonicalEventPersistenceService persistenceService,
            ManualProviderRequestCoordinator requestCoordinator) {
        this(
                controlService,
                transport,
                cache,
                rawSnapshotStore,
                projectionService,
                persistenceService,
                new TournamentScheduledEventsV1Parser(),
                requestCoordinator,
                Clock.systemUTC());
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
    }

    public TournamentEventDiscoveryResult execute(
            TournamentEventDiscoveryExecutionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        TournamentScheduledEventsProviderRequest request =
                new TournamentScheduledEventsProviderRequest(
                        claim.providerOrigin(),
                        claim.collectionDate(),
                        claim.selection().uniqueTournamentId());
        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(claim.requestId(), "OPERATOR_STOP", 0, false, null, null, 0);
        }

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

        TournamentScheduledEventsTransportResponse response;
        RawSnapshotPersistenceResult rawPersistence;
        boolean cacheHit = cached != null;
        int providerCalls = 0;
        if (cached != null) {
            response = cached.asTransportResponse();
            rawPersistence = cached.asPersistenceResult();
        }
        else {
            try (var ignored = requestCoordinator.acquire()) {
                if (!controlService.executionMayContinue(claim.requestId())) {
                    return failAndLock(
                            claim.requestId(), "OPERATOR_STOP", 0, false, null, null, 0);
                }
                providerCalls = 1;
                response = transport.execute(request);
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
                return failAndLock(
                        claim.requestId(),
                        "TRANSPORT_" + exception.failure().name(),
                        1,
                        false,
                        null,
                        null,
                        0);
            }
            catch (RuntimeException exception) {
                return failAndLock(
                        claim.requestId(),
                        "TRANSPORT_IO_FAILURE",
                        1,
                        false,
                        null,
                        null,
                        0);
            }

            try {
                rawPersistence = rawSnapshotStore.save(rawOnly(response));
            }
            catch (RuntimeException exception) {
                return failAndLock(
                        claim.requestId(),
                        "RAW_PERSISTENCE_ERROR",
                        1,
                        false,
                        null,
                        response,
                        0);
            }
        }

        TournamentEventDiscoverySource source = cacheHit
                ? TournamentEventDiscoverySource.CACHE
                : TournamentEventDiscoverySource.PROVIDER;
        return processResponse(
                claim,
                request,
                response,
                rawPersistence,
                providerCalls,
                cacheHit,
                !cacheHit,
                source);
    }

    public TournamentEventDiscoveryResult importLocalJson(
            TournamentEventDiscoveryExecutionClaim claim,
            RawPayloadEvidence payload) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(payload, "payload");
        TournamentScheduledEventsProviderRequest request =
                new TournamentScheduledEventsProviderRequest(
                        claim.providerOrigin(),
                        claim.collectionDate(),
                        claim.selection().uniqueTournamentId());
        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(
                    claim.requestId(),
                    "OPERATOR_STOP",
                    0,
                    false,
                    null,
                    null,
                    0,
                    TournamentEventDiscoverySource.LOCAL_JSON_IMPORT);
        }

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
        }
        catch (RuntimeException exception) {
            return failAndLock(
                    claim.requestId(),
                    "LOCAL_IMPORT_PERSISTENCE_ERROR",
                    0,
                    false,
                    null,
                    response,
                    0,
                    TournamentEventDiscoverySource.LOCAL_JSON_IMPORT);
        }
        return processResponse(
                claim,
                request,
                response,
                rawPersistence,
                0,
                false,
                false,
                TournamentEventDiscoverySource.LOCAL_JSON_IMPORT);
    }

    private TournamentEventDiscoveryResult processResponse(
            TournamentEventDiscoveryExecutionClaim claim,
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
            if (!classifySafely(
                    rawPersistence.snapshotId(),
                    RawSnapshotSchemaStatus.TRANSPORT_ERROR,
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
            classifySchemaIncompatibleSafely(rawPersistence.snapshotId());
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
            if (!classifySafely(
                    rawPersistence.snapshotId(),
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
            classifySchemaIncompatibleSafely(rawPersistence.snapshotId());
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
            classifySchemaIncompatibleSafely(rawPersistence.snapshotId());
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
            if (!classifySafely(
                    rawPersistence.snapshotId(),
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

    private boolean classifySchemaIncompatibleSafely(long snapshotId) {
        return classifySafely(
                snapshotId,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE.name());
    }

    private static Integer boxed(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
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
}
