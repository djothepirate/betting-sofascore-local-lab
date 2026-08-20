package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.EventDetailsTransportException;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceResult;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceService;
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
import java.util.UUID;

/**
 * Executes exactly one provider transport for each separately confirmed phase-2
 * campaign. No cache is consulted: a repeated manual campaign is an explicit
 * refresh and persists its raw response before any parsing.
 */
@Service
public class J4RealEventDetailsPhase2Service {

    private final J4RealPhase2ControlService controlService;
    private final EventDetailsProviderTransport transport;
    private final RawManualCallSnapshotStore rawSnapshotStore;
    private final J4ParsedEventDetailsPersistenceService parsedPersistenceService;
    private final EventDetailsV2Parser parser;
    private final ManualProviderRequestCoordinator requestCoordinator;

    @Autowired
    public J4RealEventDetailsPhase2Service(
            J4RealPhase2ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            ManualProviderRequestCoordinator requestCoordinator) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                parsedPersistenceService,
                new EventDetailsV2Parser(),
                requestCoordinator);
    }

    J4RealEventDetailsPhase2Service(
            J4RealPhase2ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            EventDetailsV2Parser parser,
            Clock clock,
            Duration minimumDelay,
            Pause pause) {
        this(
                controlService,
                transport,
                rawSnapshotStore,
                parsedPersistenceService,
                parser,
                new ManualProviderRequestCoordinator(clock, minimumDelay, pause::pause));
    }

    private J4RealEventDetailsPhase2Service(
            J4RealPhase2ControlService controlService,
            EventDetailsProviderTransport transport,
            RawManualCallSnapshotStore rawSnapshotStore,
            J4ParsedEventDetailsPersistenceService parsedPersistenceService,
            EventDetailsV2Parser parser,
            ManualProviderRequestCoordinator requestCoordinator) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.rawSnapshotStore = Objects.requireNonNull(rawSnapshotStore, "rawSnapshotStore");
        this.parsedPersistenceService = Objects.requireNonNull(
                parsedPersistenceService, "parsedPersistenceService");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.requestCoordinator = Objects.requireNonNull(
                requestCoordinator, "requestCoordinator");
    }

    public J4RealEventDetailsPhase2Result execute(J4RealPhase2ExecutionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(claim, "OPERATOR_STOP", 0);
        }

        EventDetailsProviderRequest request = EventDetailsProviderRequest.phase2(
                claim.providerOrigin(), claim.eventId());
        EventDetailsTransportResponse response;
        try (var ignored = requestCoordinator.acquire()) {
            if (!controlService.executionMayContinue(claim.requestId())) {
                return failed(claim, "OPERATOR_STOP", 0);
            }
            response = transport.execute(request);
        }
        catch (ManualProviderRequestCoordinator.CoordinationException exception) {
            return failAndLock(claim, "MINIMUM_DELAY_INTERRUPTED", 0);
        }
        catch (EventDetailsTransportException exception) {
            return failAndLock(claim, "TRANSPORT_" + exception.failure().name(), 1);
        }
        catch (RuntimeException exception) {
            return failAndLock(claim, "TRANSPORT_IO_FAILURE", 1);
        }

        RawSnapshotPersistenceResult rawPersistence;
        try {
            rawPersistence = rawSnapshotStore.save(rawOnly(response));
        }
        catch (RuntimeException exception) {
            return failAndLock(claim, "RAW_PERSISTENCE_ERROR", 1);
        }

        if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
            String terminalCode = httpTerminalCode(response.httpStatus());
            if (!classifySafely(
                    rawPersistence.snapshotId(),
                    RawSnapshotSchemaStatus.TRANSPORT_ERROR,
                    terminalCode)) {
                terminalCode = "RAW_CLASSIFICATION_ERROR";
            }
            return failAndLock(claim, terminalCode, 1);
        }
        if (!isJsonContentType(response.contentType())) {
            if (!classifySafely(
                    rawPersistence.snapshotId(),
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
            if (!classifySafely(
                    rawPersistence.snapshotId(),
                    schemaStatus,
                    schemaStatus.name())) {
                terminalCode = "RAW_CLASSIFICATION_ERROR";
            }
            return failAndLock(claim, terminalCode, 1);
        }

        var details = parseResult.details().orElseThrow();
        if (details.providerEventId() != claim.eventId()) {
            classifySafely(
                    rawPersistence.snapshotId(),
                    RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                    RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE.name());
            return failAndLock(claim, "EVENT_ID_MISMATCH", 1);
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
                List.of(event));
    }

    private J4RealEventDetailsPhase2Result failAndLock(
            J4RealPhase2ExecutionClaim claim,
            String code,
            int providerCallAttempts) {
        if (controlService.executionMayContinue(claim.requestId())) {
            try {
                controlService.fail(claim.requestId(), code);
            }
            catch (J4RealPhase2ControlException exception) {
                return failed(claim, "OPERATOR_STOP", providerCallAttempts);
            }
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

    @FunctionalInterface
    interface Pause {
        void pause(Duration delay);
    }
}
