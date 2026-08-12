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
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

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
        Objects.requireNonNull(response, "response");
        if (response.httpStatus() >= 200 && response.httpStatus() < 300) {
            return processSuccessfulStatus(response);
        }

        J3CircuitReason reason = reasonForHttpStatus(response.httpStatus());
        RawSnapshotPersistenceResult persistence = snapshotStore.save(snapshot(
                response,
                RawSnapshotSchemaStatus.TRANSPORT_ERROR,
                reason.name()));
        J3CircuitSnapshot opened = recordHttpIncident(response, reason);
        return J3ScheduledEventsOutcome.recorded(persistence, opened);
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
        };
        J3CircuitSnapshot opened = circuit.recordIncident(
                J3CircuitIncident.at(reason, occurredAt));
        return J3ScheduledEventsOutcome.failedBeforeSnapshot(opened);
    }

    private J3ScheduledEventsOutcome processSuccessfulStatus(
            ScheduledEventsTransportResponse response) {
        RawSnapshotPersistenceResult persistence = snapshotStore.save(snapshot(
                response,
                RawSnapshotSchemaStatus.RAW_ONLY,
                null));
        ScheduledEventsParseResult parsing = parser.parseTransportResponse(response);
        RawSnapshotSchemaStatus schemaStatus = switch (parsing.status()) {
            case PARSED -> RawSnapshotSchemaStatus.PARSED;
            case SCHEMA_INCOMPATIBLE -> RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
            case UNEXPECTED_CONTENT -> RawSnapshotSchemaStatus.UNEXPECTED_CONTENT;
        };
        String errorCode = parsing.status() == ScheduledEventsParseStatus.PARSED
                ? null
                : parsing.status().name();
        snapshotStore.classify(persistence.snapshotId(), schemaStatus, errorCode);

        if (parsing.status() == ScheduledEventsParseStatus.PARSED) {
            return J3ScheduledEventsOutcome.recorded(persistence, circuit.snapshot());
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

    private static J3CircuitReason reasonForHttpStatus(int status) {
        return switch (status) {
            case 400 -> J3CircuitReason.HTTP_BAD_REQUEST;
            case 401 -> J3CircuitReason.HTTP_UNAUTHORIZED;
            case 403 -> J3CircuitReason.HTTP_FORBIDDEN;
            case 429 -> J3CircuitReason.HTTP_TOO_MANY_REQUESTS;
            default -> J3CircuitReason.SERVER_ERROR;
        };
    }

    private static RawManualCallSnapshot snapshot(
            ScheduledEventsTransportResponse response,
            RawSnapshotSchemaStatus schemaStatus,
            String errorCode) {
        return new RawManualCallSnapshot(
                response.endpointType(),
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
}
