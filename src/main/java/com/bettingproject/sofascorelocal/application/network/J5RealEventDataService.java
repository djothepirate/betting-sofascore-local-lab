package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV6Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV13Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.J5EventDataTransportException;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdata.J5UnavailableFamily;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J5RealExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataProviderTransport;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Executes one terminal, ordered, no-retry J5 provider campaign. */
@Service
public class J5RealEventDataService {

    private final J5RealControlService controlService;
    private final J5EventDataProviderTransport transport;
    private final RawManualCallSnapshotStore rawSnapshotStore;
    private final CanonicalEventStore canonicalEventStore;
    private final J5EventDataStore eventDataStore;
    private final EventStatisticsV2Parser statisticsParser;
    private final EventIncidentsV6Parser incidentsParser;
    private final EventLineupsV2Parser lineupsParser;
    private final ManualProviderRequestCoordinator requestCoordinator;

    @Autowired
    public J5RealEventDataService(
            J5RealControlService controlService,
            J5EventDataProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore,
            ManualProviderRequestCoordinator requestCoordinator) {
        this(controlService, transport, rawSnapshotStore, canonicalEventStore, eventDataStore,
                new EventStatisticsV2Parser(), new EventIncidentsV13Parser(),
                new EventLineupsV2Parser(), requestCoordinator);
    }

    J5RealEventDataService(
            J5RealControlService controlService,
            J5EventDataProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore,
            EventStatisticsV2Parser statisticsParser,
            EventIncidentsV6Parser incidentsParser,
            EventLineupsV2Parser lineupsParser,
            Clock clock,
            Duration minimumDelay,
            Pause pause) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                canonicalEventStore,
                eventDataStore,
                statisticsParser,
                incidentsParser,
                lineupsParser,
                new ManualProviderRequestCoordinator(clock, minimumDelay, pause::pause));
    }

    private J5RealEventDataService(
            J5RealControlService controlService,
            J5EventDataProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore,
            EventStatisticsV2Parser statisticsParser,
            EventIncidentsV6Parser incidentsParser,
            EventLineupsV2Parser lineupsParser,
            ManualProviderRequestCoordinator requestCoordinator) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.rawSnapshotStore = Objects.requireNonNull(rawSnapshotStore, "rawSnapshotStore");
        this.canonicalEventStore = Objects.requireNonNull(canonicalEventStore, "canonicalEventStore");
        this.eventDataStore = Objects.requireNonNull(eventDataStore, "eventDataStore");
        this.statisticsParser = Objects.requireNonNull(statisticsParser, "statisticsParser");
        this.incidentsParser = Objects.requireNonNull(incidentsParser, "incidentsParser");
        this.lineupsParser = Objects.requireNonNull(lineupsParser, "lineupsParser");
        this.requestCoordinator = Objects.requireNonNull(
                requestCoordinator, "requestCoordinator");
    }

    public synchronized J5RealCampaignResult execute(J5RealExecutionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        List<J5RealEndpointResult> results = new ArrayList<>();
        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(claim, "OPERATOR_STOP", 0, results);
        }
        var canonical = canonicalEventStore.findLatestByCanonicalId(claim.canonicalEventId());
        if (canonical.isEmpty()
                || canonical.orElseThrow().identity().providerEventId() != claim.eventId()
                || !canonical.orElseThrow().identity().value().equals(claim.canonicalEventId())) {
            return failAndLock(claim, "EVENT_ID_MISMATCH", 0, results);
        }

        int attempts = 0;
        for (SofascoreEndpointType endpoint : J5RealControlService.ORDERED_ENDPOINTS) {
            if (!controlService.executionMayContinue(claim.requestId())) {
                return failed(claim, "OPERATOR_STOP", attempts, results);
            }
            J5EventDataProviderRequest request = new J5EventDataProviderRequest(
                    claim.providerOrigin(), claim.eventId(), endpoint);
            J5EventDataTransportResponse response;
            try (var ignored = requestCoordinator.acquire()) {
                if (!controlService.executionMayContinue(claim.requestId())) {
                    return failed(claim, "OPERATOR_STOP", attempts, results);
                }
                response = transport.execute(request);
                attempts++;
            }
            catch (ManualProviderRequestCoordinator.CoordinationException exception) {
                return failAndLock(
                        claim, "MINIMUM_DELAY_INTERRUPTED", attempts, results);
            }
            catch (J5EventDataTransportException exception) {
                return failAndLock(claim,
                        "TRANSPORT_" + exception.failure().name(), attempts + 1, results);
            }
            catch (RuntimeException exception) {
                return failAndLock(claim, "TRANSPORT_IO_FAILURE", attempts + 1, results);
            }

            RawSnapshotPersistenceResult raw;
            try {
                raw = rawSnapshotStore.save(rawOnly(response));
            }
            catch (RuntimeException exception) {
                return failAndLock(claim, "RAW_PERSISTENCE_ERROR", attempts, results);
            }
            if (response.httpStatus() == 404) {
                J5CompletenessReport completeness = J5CompletenessReport.unavailable();
                EventSourceTrace source = EventSourceTrace.providerSnapshot(
                        raw.snapshotId(), raw.payloadSha256(),
                        J5UnavailableFamily.normalizerVersion(endpoint),
                        response.receivedAt());
                J5EventDataPersistenceResult persisted;
                try {
                    persisted = eventDataStore.save(J5EventDataObservation.from(
                            canonical.orElseThrow().identity(),
                            J5UnavailableFamily.emptyObservation(endpoint, claim.eventId()),
                            source,
                            completeness));
                }
                catch (RuntimeException exception) {
                    return failAndLock(
                            claim, "NORMALIZATION_PERSISTENCE_ERROR", attempts, results);
                }
                if (!classifyInsertedSafely(
                        raw, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null)) {
                    return failAndLock(claim, "RAW_CLASSIFICATION_ERROR", attempts, results);
                }
                results.add(new J5RealEndpointResult(
                        endpoint,
                        raw.snapshotId(),
                        raw.payloadSha256(),
                        raw.payloadSizeBytes(),
                        persisted.observationId(),
                        persisted.inserted(),
                        completeness.status(),
                        completeness.scorePercent(),
                        0));
                try {
                    controlService.recordEndpointCompleted(claim.requestId(), endpoint);
                }
                catch (J5RealControlException exception) {
                    return failed(claim, "OPERATOR_STOP", attempts, results);
                }
                continue;
            }
            if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
                String code = httpTerminalCode(response.httpStatus());
                if (!classifyInsertedSafely(
                        raw, RawSnapshotSchemaStatus.TRANSPORT_ERROR, code)) {
                    code = "RAW_CLASSIFICATION_ERROR";
                }
                return failAndLock(claim, code, attempts, results);
            }
            if (!isJsonContentType(response.contentType())) {
                if (!classifyInsertedSafely(raw,
                        RawSnapshotSchemaStatus.UNEXPECTED_CONTENT,
                        RawSnapshotSchemaStatus.UNEXPECTED_CONTENT.name())) {
                    return failAndLock(
                            claim, "RAW_CLASSIFICATION_ERROR", attempts, results);
                }
                return failAndLock(claim, "UNEXPECTED_CONTENT", attempts, results);
            }

            J5ParseResult<? extends J5EventData> parsed;
            try {
                parsed = parse(endpoint, raw.snapshotId(), claim.eventId(), response);
            }
            catch (RuntimeException exception) {
                return failAndLock(claim, "PARSER_FAILURE", attempts, results);
            }
            if (parsed.status() != J5ParseStatus.PARSED) {
                RawSnapshotSchemaStatus status = parsed.status()
                        == J5ParseStatus.UNEXPECTED_CONTENT
                                ? RawSnapshotSchemaStatus.UNEXPECTED_CONTENT
                                : RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
                String code = status.name();
                if (!classifyInsertedSafely(raw, status, code)) {
                    code = "RAW_CLASSIFICATION_ERROR";
                }
                return failAndLock(claim, code, attempts, results);
            }

            J5EventData data = parsed.data().orElseThrow();
            var completeness = parsed.completeness().orElseThrow();
            EventSourceTrace source = EventSourceTrace.providerSnapshot(
                    raw.snapshotId(), raw.payloadSha256(), parserVersion(endpoint),
                    response.receivedAt());
            J5EventDataPersistenceResult persisted;
            try {
                persisted = eventDataStore.save(J5EventDataObservation.from(
                        canonical.orElseThrow().identity(), data, source, completeness));
            }
            catch (RuntimeException exception) {
                return failAndLock(
                        claim, "NORMALIZATION_PERSISTENCE_ERROR", attempts, results);
            }
            if (!classifyInsertedSafely(raw, RawSnapshotSchemaStatus.PARSED, null)) {
                return failAndLock(claim, "RAW_CLASSIFICATION_ERROR", attempts, results);
            }
            results.add(new J5RealEndpointResult(
                    endpoint,
                    raw.snapshotId(),
                    raw.payloadSha256(),
                    raw.payloadSizeBytes(),
                    persisted.observationId(),
                    persisted.inserted(),
                    completeness.status(),
                    completeness.scorePercent(),
                    parsed.warnings().size()));
            try {
                controlService.recordEndpointCompleted(claim.requestId(), endpoint);
            }
            catch (J5RealControlException exception) {
                return failed(claim, "OPERATOR_STOP", attempts, results);
            }
        }

        try {
            controlService.complete(claim.requestId());
        }
        catch (J5RealControlException exception) {
            return failed(claim, "OPERATOR_STOP", attempts, results);
        }
        return new J5RealCampaignResult(
                claim.requestId(), claim.canonicalEventId(), claim.eventId(), true,
                "COMPLETED", attempts, 0, results);
    }

    private J5ParseResult<? extends J5EventData> parse(
            SofascoreEndpointType endpoint,
            long snapshotId,
            long eventId,
            J5EventDataTransportResponse response) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> statisticsParser.parse(
                    snapshotId, eventId, response.payload(), response.receivedAt());
            case EVENT_INCIDENTS -> incidentsParser.parse(
                    snapshotId, eventId, response.payload(), response.receivedAt());
            case EVENT_LINEUPS -> lineupsParser.parse(
                    snapshotId, eventId, response.payload(), response.receivedAt());
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
    }

    private J5RealCampaignResult failAndLock(
            J5RealExecutionClaim claim,
            String code,
            int attempts,
            List<J5RealEndpointResult> results) {
        if (controlService.executionMayContinue(claim.requestId())) {
            try {
                controlService.fail(claim.requestId(), code);
            }
            catch (J5RealControlException exception) {
                return failed(claim, "OPERATOR_STOP", attempts, results);
            }
        }
        return failed(claim, code, attempts, results);
    }

    private static J5RealCampaignResult failed(
            J5RealExecutionClaim claim,
            String code,
            int attempts,
            List<J5RealEndpointResult> results) {
        return new J5RealCampaignResult(
                claim.requestId(), claim.canonicalEventId(), claim.eventId(), false,
                code, attempts, 0, results);
    }

    private static RawManualCallSnapshot rawOnly(J5EventDataTransportResponse response) {
        return new RawManualCallSnapshot(
                response.endpointType(), response.requestKey(), response.requestedAt(),
                response.receivedAt(), response.httpStatus(), response.contentType(),
                response.latency(), response.payload(), parserVersion(response.endpointType()),
                RawSnapshotSchemaStatus.RAW_ONLY, null);
    }

    private static String parserVersion(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> EventStatisticsV2Parser.PARSER_VERSION;
            case EVENT_INCIDENTS -> EventIncidentsV13Parser.PARSER_VERSION;
            case EVENT_LINEUPS -> EventLineupsV2Parser.PARSER_VERSION;
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
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
        // A deduplicated result points to immutable historical evidence. The current parser
        // outcome belongs to the new normalized observation and must not rewrite that evidence.
        return switch (raw.outcome()) {
            case INSERTED -> classifySafely(raw.snapshotId(), status, errorCode);
            case DEDUPLICATED -> true;
            case CACHE_HIT -> false;
        };
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

    private static String httpTerminalCode(int status) {
        if (status == 403) {
            return "HTTP_403";
        }
        if (status == 429) {
            return "HTTP_429";
        }
        if (status == 408) {
            return "HTTP_TIMEOUT";
        }
        if (status >= 500) {
            return "HTTP_5XX";
        }
        return "HTTP_STATUS_" + status;
    }

    @FunctionalInterface
    interface Pause {
        void pause(Duration delay);
    }
}
