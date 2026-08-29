package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportFailure;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitIncident;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Persists bounded raw evidence and applies deterministic incident transitions.
 * It never performs transport and never schedules a retry.
 */
public final class J3ScheduledEventsOutcomeProcessor {

    public static final Duration DEFAULT_RATE_LIMIT_HOLD = Duration.ofMinutes(5);

    private final RawManualCallSnapshotStore snapshotStore;
    private final ScheduledEventsV1Parser parser;
    private final J3NetworkCircuit circuit;

    public J3ScheduledEventsOutcomeProcessor(
            RawManualCallSnapshotStore snapshotStore,
            ScheduledEventsV1Parser parser,
            J3NetworkCircuit circuit) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.circuit = Objects.requireNonNull(circuit, "circuit");
    }

    public J3ScheduledEventsOutcome processResponse(
            ScheduledEventsTransportResponse response) {
        return processResponse(response, ignored -> { });
    }

    public J3ScheduledEventsOutcome processResponse(
            ScheduledEventsTransportResponse response,
            Consumer<RawSnapshotPersistenceResult> snapshotPersisted) {
        Objects.requireNonNull(response, "response");
        Consumer<RawSnapshotPersistenceResult> persisted = Objects.requireNonNull(
                snapshotPersisted, "snapshotPersisted");
        if (response.httpStatus() >= 200 && response.httpStatus() < 300) {
            return processSuccessfulStatus(
                    response,
                    RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                    persisted);
        }

        J3CircuitReason reason = reasonForHttpStatus(response.httpStatus());
        RawSnapshotSchemaStatus schemaStatus = response.httpStatus() == 404
                ? RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE
                : RawSnapshotSchemaStatus.TRANSPORT_ERROR;
        RawSnapshotPersistenceResult persistence = persist(snapshot(
                response,
                schemaStatus,
                reason.name()));
        persisted.accept(persistence);
        J3CircuitSnapshot opened = recordHttpIncident(response, reason);
        return J3ScheduledEventsOutcome.recorded(persistence, opened);
    }

    public J3ScheduledEventsOutcome processImportedResponse(
            ScheduledEventsTransportResponse response) {
        return processImportedResponse(response, ignored -> { });
    }

    public J3ScheduledEventsOutcome processImportedResponse(
            ScheduledEventsTransportResponse response,
            Consumer<RawSnapshotPersistenceResult> snapshotPersisted) {
        Objects.requireNonNull(response, "response");
        Consumer<RawSnapshotPersistenceResult> persisted = Objects.requireNonNull(
                snapshotPersisted, "snapshotPersisted");
        if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
            throw new IllegalArgumentException(
                    "a local JSON import must model a successful response body");
        }
        return processSuccessfulStatus(
                response,
                RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT,
                persisted);
    }

    public J3ScheduledEventsOutcome processFailure(
            ScheduledEventsTransportException exception,
            Instant occurredAt) {
        Objects.requireNonNull(exception, "exception");
        Objects.requireNonNull(occurredAt, "occurredAt");
        J3CircuitReason reason = switch (exception.failure()) {
            case TIMEOUT -> J3CircuitReason.TIMEOUT;
            case IO_FAILURE -> J3CircuitReason.TRANSPORT_IO_FAILURE;
            case PAYLOAD_TOO_LARGE -> J3CircuitReason.PAYLOAD_TOO_LARGE;
            case SENSITIVE_CONTENT_REJECTED -> J3CircuitReason.SENSITIVE_CONTENT_REJECTED;
            case UNEXPECTED_CONTENT -> J3CircuitReason.UNEXPECTED_CONTENT;
            case OPERATOR_STOP -> J3CircuitReason.TRANSPORT_IO_FAILURE;
        };
        J3CircuitSnapshot opened = circuit.recordIncident(
                J3CircuitIncident.at(reason, occurredAt));
        return J3ScheduledEventsOutcome.failedBeforeSnapshot(opened);
    }

    private J3ScheduledEventsOutcome processSuccessfulStatus(
            ScheduledEventsTransportResponse response,
            RawSnapshotAcquisitionMode acquisitionMode,
            Consumer<RawSnapshotPersistenceResult> snapshotPersisted) {
        RawSnapshotPersistenceResult persistence = persist(snapshot(
                response,
                acquisitionMode,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        snapshotPersisted.accept(persistence);
        ScheduledEventsParseResult parsing = parser.parseTransportResponse(response);
        RawSnapshotSchemaStatus schemaStatus = switch (parsing.status()) {
            case PARSED -> RawSnapshotSchemaStatus.PARSED;
            case SCHEMA_INCOMPATIBLE -> RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
            case UNEXPECTED_CONTENT -> RawSnapshotSchemaStatus.UNEXPECTED_CONTENT;
        };
        String errorCode = parsing.status() == ScheduledEventsParseStatus.PARSED
                ? null
                : parsing.status().name();
        if (persistence.outcome() == RawSnapshotPersistenceOutcome.INSERTED) {
            classify(persistence.snapshotId(), schemaStatus, errorCode);
        }

        if (parsing.status() == ScheduledEventsParseStatus.PARSED) {
            return J3ScheduledEventsOutcome.parsed(
                    persistence,
                    parsing.page().orElseThrow().hasNextPage(),
                    circuit.snapshot());
        }
        J3CircuitReason reason = parsing.status() == ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE
                ? J3CircuitReason.SCHEMA_INCOMPATIBLE
                : J3CircuitReason.UNEXPECTED_CONTENT;
        J3CircuitSnapshot opened = circuit.recordIncident(
                J3CircuitIncident.at(reason, response.receivedAt()));
        return J3ScheduledEventsOutcome.recorded(persistence, opened);
    }

    private J3CircuitSnapshot recordHttpIncident(
            ScheduledEventsTransportResponse response,
            J3CircuitReason reason) {
        if (reason == J3CircuitReason.HTTP_TOO_MANY_REQUESTS) {
            Instant retryNotBefore = response.retryNotBefore() == null
                    ? response.receivedAt().plus(DEFAULT_RATE_LIMIT_HOLD)
                    : response.retryNotBefore();
            return circuit.recordIncident(J3CircuitIncident.rateLimited(
                    response.receivedAt(),
                    retryNotBefore));
        }
        return circuit.recordIncident(J3CircuitIncident.at(
                reason,
                response.receivedAt()));
    }

    private RawSnapshotPersistenceResult persist(RawManualCallSnapshot snapshot) {
        try {
            return Objects.requireNonNull(
                    snapshotStore.save(snapshot), "snapshotStore result");
        }
        catch (EvidencePersistenceException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new EvidencePersistenceException(
                    "RAW_PERSISTENCE_ERROR", exception);
        }
    }

    private void classify(
            long snapshotId,
            RawSnapshotSchemaStatus schemaStatus,
            String errorCode) {
        try {
            snapshotStore.classify(snapshotId, schemaStatus, errorCode);
        }
        catch (EvidencePersistenceException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new EvidencePersistenceException(
                    "RAW_CLASSIFICATION_ERROR", exception);
        }
    }

    private static J3CircuitReason reasonForHttpStatus(int status) {
        return switch (status) {
            case 400 -> J3CircuitReason.HTTP_BAD_REQUEST;
            case 401 -> J3CircuitReason.HTTP_UNAUTHORIZED;
            case 403 -> J3CircuitReason.HTTP_FORBIDDEN;
            case 404 -> J3CircuitReason.ENDPOINT_UNAVAILABLE;
            case 429 -> J3CircuitReason.HTTP_TOO_MANY_REQUESTS;
            default -> J3CircuitReason.SERVER_ERROR;
        };
    }

    private static RawManualCallSnapshot snapshot(
            ScheduledEventsTransportResponse response,
            RawSnapshotSchemaStatus schemaStatus,
            String errorCode) {
        return snapshot(
                response,
                RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                schemaStatus,
                errorCode);
    }

    private static RawManualCallSnapshot snapshot(
            ScheduledEventsTransportResponse response,
            RawSnapshotAcquisitionMode acquisitionMode,
            RawSnapshotSchemaStatus schemaStatus,
            String errorCode) {
        return new RawManualCallSnapshot(
                response.endpointType(),
                acquisitionMode,
                response.requestKey(),
                response.requestedAt(),
                response.receivedAt(),
                response.httpStatus(),
                response.contentType(),
                response.latency(),
                response.payload(),
                ScheduledEventsV1Parser.PARSER_VERSION,
                schemaStatus,
                errorCode);
    }

    static final class EvidencePersistenceException extends RuntimeException {

        private final String code;

        private EvidencePersistenceException(String code, RuntimeException cause) {
            super(code, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
