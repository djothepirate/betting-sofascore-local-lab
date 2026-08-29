package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV6Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV14Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.J5EventDataTransportException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
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
import java.util.UUID;

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
    private final PlaywrightProviderSupervisor providerSupervisor;

    @Autowired
    public J5RealEventDataService(
            J5RealControlService controlService,
            J5EventDataProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore,
            ManualProviderRequestCoordinator requestCoordinator,
            PlaywrightProviderSupervisor providerSupervisor) {
        this(controlService, transport, rawSnapshotStore, canonicalEventStore, eventDataStore,
                new EventStatisticsV2Parser(), new EventIncidentsV14Parser(),
                new EventLineupsV2Parser(), requestCoordinator, providerSupervisor);
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
            Pause pause,
            PlaywrightProviderSupervisor providerSupervisor) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                canonicalEventStore,
                eventDataStore,
                statisticsParser,
                incidentsParser,
                lineupsParser,
                new ManualProviderRequestCoordinator(clock, minimumDelay, pause::pause),
                providerSupervisor);
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
            ManualProviderRequestCoordinator requestCoordinator,
            PlaywrightProviderSupervisor providerSupervisor) {
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
        this.providerSupervisor = Objects.requireNonNull(
                providerSupervisor, "providerSupervisor");
    }

    public synchronized J5RealCampaignResult execute(J5RealExecutionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        List<J5RealEndpointResult> results = new ArrayList<>();
        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(claim, "OPERATOR_STOP", 0, results);
        }
        CampaignResources resources = new CampaignResources();
        int attempts = 0;
        try {
            var canonical = canonicalEventStore.findLatestByCanonicalId(
                    claim.canonicalEventId());
            if (canonical.isEmpty()
                    || canonical.orElseThrow().identity().providerEventId() != claim.eventId()
                    || !canonical.orElseThrow().identity().value()
                            .equals(claim.canonicalEventId())) {
                return failAndLock(
                        claim, "EVENT_ID_MISMATCH", attempts, results, resources);
            }

            try {
                resources.lease = requestCoordinator.acquireCampaign(claim.requestId());
            }
            catch (ManualProviderRequestCoordinator.CoordinationException exception) {
                return failAndLock(
                        claim, "MINIMUM_DELAY_INTERRUPTED", attempts, results, resources);
            }
            if (!controlService.executionMayContinue(claim.requestId())) {
                return failAndLock(
                        claim, "OPERATOR_STOP", attempts, results, resources);
            }
            try {
                resources.campaign = transport.openCampaign(claim.requestId());
            }
            catch (J5EventDataTransportException exception) {
                retainLeaseIfPublished(claim.requestId(), resources);
                return failAndLock(
                        claim,
                        "TRANSPORT_" + exception.failure().name(),
                        attempts,
                        results,
                        resources);
            }
            catch (RuntimeException exception) {
                retainLeaseIfPublished(claim.requestId(), resources);
                return failAndLock(
                        claim, "TRANSPORT_IO_FAILURE", attempts, results, resources);
            }

            for (SofascoreEndpointType endpoint : J5RealControlService.ORDERED_ENDPOINTS) {
                if (!controlService.executionMayContinue(claim.requestId())) {
                    return failAndLock(
                            claim, "OPERATOR_STOP", attempts, results, resources);
                }
                J5EventDataProviderRequest request = new J5EventDataProviderRequest(
                        claim.providerOrigin(), claim.eventId(), endpoint);
                J5EventDataTransportResponse response;
                try {
                    resources.lease.beginRequest();
                    if (!controlService.executionMayContinue(claim.requestId())) {
                        return failAndLock(
                                claim, "OPERATOR_STOP", attempts, results, resources);
                    }
                    attempts++;
                    response = resources.campaign.execute(request);
                }
                catch (ManualProviderRequestCoordinator.CoordinationException exception) {
                    return failAndLock(
                            claim,
                            "MINIMUM_DELAY_INTERRUPTED",
                            attempts,
                            results,
                            resources);
                }
                catch (J5EventDataTransportException exception) {
                    String code = controlService.executionMayContinue(claim.requestId())
                            ? "TRANSPORT_" + exception.failure().name()
                            : "OPERATOR_STOP";
                    return failAndLock(claim, code, attempts, results, resources);
                }
                catch (RuntimeException exception) {
                    String code = controlService.executionMayContinue(claim.requestId())
                            ? "TRANSPORT_IO_FAILURE"
                            : "OPERATOR_STOP";
                    return failAndLock(claim, code, attempts, results, resources);
                }

                RawSnapshotPersistenceResult raw;
                try {
                    raw = rawSnapshotStore.save(rawOnly(response));
                }
                catch (RuntimeException exception) {
                    return failAndLock(
                            claim, "RAW_PERSISTENCE_ERROR", attempts, results, resources);
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
                                J5UnavailableFamily.emptyObservation(
                                        endpoint, claim.eventId()),
                                source,
                                completeness));
                    }
                    catch (RuntimeException exception) {
                        return failAndLock(
                                claim,
                                "NORMALIZATION_PERSISTENCE_ERROR",
                                attempts,
                                results,
                                resources);
                    }
                    if (!classifyInsertedSafely(
                            raw, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null)) {
                        return failAndLock(
                                claim,
                                "RAW_CLASSIFICATION_ERROR",
                                attempts,
                                results,
                                resources);
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
                        return failAndLock(
                                claim, "OPERATOR_STOP", attempts, results, resources);
                    }
                    continue;
                }
                if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
                    String code = httpTerminalCode(response.httpStatus());
                    if (!classifyInsertedSafely(
                            raw, RawSnapshotSchemaStatus.TRANSPORT_ERROR, code)) {
                        code = "RAW_CLASSIFICATION_ERROR";
                    }
                    return failAndLock(claim, code, attempts, results, resources);
                }
                if (!isJsonContentType(response.contentType())) {
                    if (!classifyInsertedSafely(raw,
                            RawSnapshotSchemaStatus.UNEXPECTED_CONTENT,
                            RawSnapshotSchemaStatus.UNEXPECTED_CONTENT.name())) {
                        return failAndLock(
                                claim,
                                "RAW_CLASSIFICATION_ERROR",
                                attempts,
                                results,
                                resources);
                    }
                    return failAndLock(
                            claim, "UNEXPECTED_CONTENT", attempts, results, resources);
                }

                J5ParseResult<? extends J5EventData> parsed;
                try {
                    parsed = parse(endpoint, raw.snapshotId(), claim.eventId(), response);
                }
                catch (RuntimeException exception) {
                    return failAndLock(
                            claim, "PARSER_FAILURE", attempts, results, resources);
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
                    return failAndLock(claim, code, attempts, results, resources);
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
                            claim,
                            "NORMALIZATION_PERSISTENCE_ERROR",
                            attempts,
                            results,
                            resources);
                }
                if (!classifyInsertedSafely(raw, RawSnapshotSchemaStatus.PARSED, null)) {
                    return failAndLock(
                            claim,
                            "RAW_CLASSIFICATION_ERROR",
                            attempts,
                            results,
                            resources);
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
                    return failAndLock(
                            claim, "OPERATOR_STOP", attempts, results, resources);
                }
            }

            RuntimeException cleanupFailure = resources.closeSafely();
            if (cleanupFailure != null) {
                return failAndLockAfterCleanup(
                        claim,
                        cleanupCode(cleanupFailure, "TRANSPORT_IO_FAILURE"),
                        attempts,
                        results,
                        true);
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
        finally {
            resources.closeSafely();
        }
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
            List<J5RealEndpointResult> results,
            CampaignResources resources) {
        RuntimeException cleanupFailure = resources.closeSafely();
        String terminalCode = cleanupCode(cleanupFailure, code);
        return failAndLockAfterCleanup(
                claim,
                terminalCode,
                attempts,
                results,
                cleanupFailure != null);
    }

    private J5RealCampaignResult failAndLockAfterCleanup(
            J5RealExecutionClaim claim,
            String terminalCode,
            int attempts,
            List<J5RealEndpointResult> results,
            boolean cleanupFailureKnown) {
        if (controlService.executionMayContinue(claim.requestId())) {
            try {
                controlService.fail(claim.requestId(), terminalCode);
            }
            catch (J5RealControlException exception) {
                return failed(
                        claim,
                        cleanupFailureKnown ? terminalCode : "OPERATOR_STOP",
                        attempts,
                        results);
            }
        }
        else {
            return failed(
                    claim,
                    cleanupFailureKnown ? terminalCode : "OPERATOR_STOP",
                    attempts,
                    results);
        }
        return failed(claim, terminalCode, attempts, results);
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
            case EVENT_INCIDENTS -> EventIncidentsV14Parser.PARSER_VERSION;
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

    private static String cleanupCode(RuntimeException cleanupFailure, String fallback) {
        if (cleanupFailure == null) {
            return fallback;
        }
        return cleanupFailure instanceof J5EventDataTransportException failure
                ? "TRANSPORT_" + failure.failure().name()
                : "TRANSPORT_IO_FAILURE";
    }

    private void retainLeaseIfPublished(UUID requestId, CampaignResources resources) {
        try {
            if (providerSupervisor.activeCampaignId().filter(requestId::equals).isPresent()) {
                resources.retainLease();
            }
        }
        catch (RuntimeException exception) {
            resources.retainLease();
        }
    }

    @FunctionalInterface
    interface Pause {
        void pause(Duration delay);
    }

    private static final class CampaignResources {

        private ManualProviderRequestCoordinator.CampaignLease lease;
        private J5EventDataProviderTransport.Campaign campaign;
        private boolean retainLease;

        private void retainLease() {
            if (lease != null) {
                retainLease = true;
            }
        }

        private RuntimeException closeSafely() {
            RuntimeException failure = null;
            try {
                if (campaign != null) {
                    campaign.close();
                    campaign = null;
                    retainLease = false;
                }
            }
            catch (RuntimeException exception) {
                failure = exception;
                retainLease();
            }
            finally {
                try {
                    if (lease != null && !retainLease) {
                        lease.close();
                        lease = null;
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
            }
            return failure;
        }
    }
}
