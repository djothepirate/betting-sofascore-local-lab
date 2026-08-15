package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.EventDetailsTransportException;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceResult;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceService;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
    private final Duration minimumDelay;
    private final Pause pause;

    @Autowired
    public J4RealEventDetailsPhase1Service(
            J4RealPhase1ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4EventDetailsCache cache,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            SofascoreEndpointCatalog endpointCatalog,
            SofascoreProperties properties) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                cache,
                parsedPersistenceService,
                new EventDetailsV2Parser(),
                Clock.systemUTC(),
                endpointCatalog.get(SofascoreEndpointType.EVENT_DETAILS).cacheTtl(),
                properties.getMinimumDelay(),
                J4RealEventDetailsPhase1Service::sleepSafely);
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
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.rawSnapshotStore = Objects.requireNonNull(rawSnapshotStore, "rawSnapshotStore");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.parsedPersistenceService = Objects.requireNonNull(
                parsedPersistenceService, "parsedPersistenceService");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cacheTtl = requirePositive(cacheTtl, "cacheTtl");
        this.minimumDelay = requireAtLeastThreeSeconds(minimumDelay);
        this.pause = Objects.requireNonNull(pause, "pause");
    }

    public J4RealEventDetailsPhase1Result execute(J4RealPhase1ExecutionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        List<J4RealEventDetailsEventResult> results = new ArrayList<>();
        Counters counters = new Counters();

        for (long eventId : EventDetailsProviderRequest.PHASE_1_EVENT_IDS) {
            if (!controlService.executionMayContinue(claim.requestId())) {
                return failed(claim.requestId(), "OPERATOR_STOP", counters, results);
            }
            EventDetailsProviderRequest request = new EventDetailsProviderRequest(
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
                        claim.requestId(), "CACHE_READ_ERROR", counters, results);
            }

            EventDetailsTransportResponse response;
            RawSnapshotPersistenceResult rawPersistence;
            J4RealEventDetailsResolutionSource resolutionSource;
            if (cached.isPresent()) {
                J4CachedEventDetails candidate = cached.orElseThrow();
                response = candidate.asTransportResponse();
                rawPersistence = candidate.asPersistenceResult();
                resolutionSource = J4RealEventDetailsResolutionSource.CACHE;
                counters.cacheHits++;
            }
            else {
                if (counters.providerCallAttempts > 0) {
                    try {
                        pause.pause(minimumDelay);
                    }
                    catch (RuntimeException exception) {
                        return failAndLock(
                                claim.requestId(),
                                "MINIMUM_DELAY_INTERRUPTED",
                                counters,
                                results);
                    }
                }
                if (!controlService.executionMayContinue(claim.requestId())) {
                    return failed(claim.requestId(), "OPERATOR_STOP", counters, results);
                }

                counters.providerCallAttempts++;
                try {
                    response = transport.execute(request);
                }
                catch (EventDetailsTransportException exception) {
                    return failAndLock(
                            claim.requestId(),
                            "TRANSPORT_" + exception.failure().name(),
                            counters,
                            results);
                }
                catch (RuntimeException exception) {
                    return failAndLock(
                            claim.requestId(),
                            "TRANSPORT_IO_FAILURE",
                            counters,
                            results);
                }

                try {
                    rawPersistence = rawSnapshotStore.save(rawOnly(response));
                }
                catch (RuntimeException exception) {
                    return failAndLock(
                            claim.requestId(),
                            "RAW_PERSISTENCE_ERROR",
                            counters,
                            results);
                }
                resolutionSource = J4RealEventDetailsResolutionSource.PROVIDER;

                if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
                    String terminalCode = httpTerminalCode(response.httpStatus());
                    if (!classifySafely(
                            rawPersistence.snapshotId(),
                            RawSnapshotSchemaStatus.TRANSPORT_ERROR,
                            terminalCode)) {
                        terminalCode = "RAW_CLASSIFICATION_ERROR";
                    }
                    return failAndLock(
                            claim.requestId(), terminalCode, counters, results);
                }
                if (!isJsonContentType(response.contentType())) {
                    if (!classifySafely(
                            rawPersistence.snapshotId(),
                            RawSnapshotSchemaStatus.UNEXPECTED_CONTENT,
                            RawSnapshotSchemaStatus.UNEXPECTED_CONTENT.name())) {
                        return failAndLock(
                                claim.requestId(),
                                "RAW_CLASSIFICATION_ERROR",
                                counters,
                                results);
                    }
                    return failAndLock(
                            claim.requestId(),
                            "UNEXPECTED_CONTENT",
                            counters,
                            results);
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
                        claim.requestId(), "PARSER_FAILURE", counters, results);
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
                    if (!classifySafely(
                            rawPersistence.snapshotId(),
                            schemaStatus,
                            schemaStatus.name())) {
                        terminalCode = "RAW_CLASSIFICATION_ERROR";
                    }
                }
                return failAndLock(
                        claim.requestId(), terminalCode, counters, results);
            }
            var details = parseResult.details().orElseThrow();
            if (details.providerEventId() != eventId) {
                if (resolutionSource == J4RealEventDetailsResolutionSource.PROVIDER) {
                    classifySafely(
                            rawPersistence.snapshotId(),
                            RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                            RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE.name());
                }
                return failAndLock(
                        claim.requestId(), "EVENT_ID_MISMATCH", counters, results);
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
                        claim.requestId(),
                        "NORMALIZATION_PERSISTENCE_ERROR",
                        counters,
                        results);
            }

            try {
                controlService.recordEventCompleted(claim.requestId(), eventId);
            }
            catch (J4RealPhase1ControlException exception) {
                return failed(claim.requestId(), "OPERATOR_STOP", counters, results);
            }
            results.add(new J4RealEventDetailsEventResult(
                    eventId,
                    resolutionSource,
                    rawPersistence.snapshotId(),
                    normalized.canonicalEventId(),
                    rawPersistence.payloadSha256(),
                    rawPersistence.payloadSizeBytes(),
                    RawSnapshotSchemaStatus.PARSED,
                    details,
                    parseResult.warnings().size()));
        }

        try {
            controlService.complete(claim.requestId());
        }
        catch (J4RealPhase1ControlException exception) {
            return failed(claim.requestId(), "OPERATOR_STOP", counters, results);
        }
        return new J4RealEventDetailsPhase1Result(
                claim.requestId(),
                true,
                "COMPLETED",
                counters.providerCallAttempts,
                counters.cacheHits,
                results);
    }

    private J4RealEventDetailsPhase1Result failAndLock(
            UUID requestId,
            String code,
            Counters counters,
            List<J4RealEventDetailsEventResult> results) {
        if (controlService.executionMayContinue(requestId)) {
            try {
                controlService.fail(requestId, code);
            }
            catch (J4RealPhase1ControlException exception) {
                return failed(requestId, "OPERATOR_STOP", counters, results);
            }
        }
        return failed(requestId, code, counters, results);
    }

    private static J4RealEventDetailsPhase1Result failed(
            UUID requestId,
            String code,
            Counters counters,
            List<J4RealEventDetailsEventResult> results) {
        return new J4RealEventDetailsPhase1Result(
                requestId,
                false,
                code,
                counters.providerCallAttempts,
                counters.cacheHits,
                results);
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

    private static Duration requireAtLeastThreeSeconds(Duration value) {
        Objects.requireNonNull(value, "minimumDelay");
        if (value.compareTo(Duration.ofSeconds(3)) < 0) {
            throw new IllegalArgumentException("minimumDelay must be at least three seconds");
        }
        return value;
    }

    private static void sleepSafely(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MinimumDelayInterruptedException();
        }
    }

    @FunctionalInterface
    interface Pause {
        void pause(Duration delay);
    }

    private static final class MinimumDelayInterruptedException extends RuntimeException {
    }

    private static final class Counters {
        private int providerCallAttempts;
        private int cacheHits;
    }
}
