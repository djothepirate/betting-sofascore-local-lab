package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportFailure;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(OutputCaptureExtension.class)
class J3TransportStopAndIncidentPoliciesTest {

    private static final Instant NOW = Instant.parse("2026-08-12T16:00:00Z");
    private static final String REQUEST_KEY = "SCHEDULED_EVENTS|date=2026-08-12";

    @Test
    void parsesAndDeduplicatesSuccessfulRawEvidenceWithoutOpeningTheCircuit() {
        RecordingStore store = new RecordingStore();
        J3ScheduledEventsOutcomeProcessor processor = processor(store);
        byte[] rawPayload = "{\"events\":[],\"hasNextPage\":false}"
                .getBytes(StandardCharsets.UTF_8);
        ScheduledEventsTransportResponse response = response(
                200,
                "application/json",
                rawPayload,
                null);

        var first = processor.processResponse(response);
        var duplicate = processor.processResponse(response);

        assertThat(first.persistence()).get()
                .extracting(RawSnapshotPersistenceResult::outcome)
                .isEqualTo(RawSnapshotPersistenceOutcome.INSERTED);
        assertThat(duplicate.persistence()).get()
                .extracting(RawSnapshotPersistenceResult::outcome)
                .isEqualTo(RawSnapshotPersistenceOutcome.DEDUPLICATED);
        assertThat(duplicate.persistence().orElseThrow().snapshotId())
                .isEqualTo(first.persistence().orElseThrow().snapshotId());
        assertThat(store.uniqueSnapshots()).hasSize(1);
        RawManualCallSnapshot persisted = store.uniqueSnapshots().getFirst();
        assertThat(persisted.payload().bytes()).isEqualTo(rawPayload);
        assertThat(persisted.payload().sha256()).isEqualTo(response.payload().sha256());
        assertThat(persisted.schemaStatus()).isEqualTo(RawSnapshotSchemaStatus.PARSED);
        assertThat(persisted.errorCode()).isNull();
        assertThat(store.transitions()).containsExactly(
                "SAVE:RAW_ONLY",
                "CLASSIFY:PARSED",
                "SAVE:RAW_ONLY",
                "CLASSIFY:PARSED");
        assertThat(first.circuit().state()).isEqualTo(J3CircuitState.CLOSED);
        assertThat(first.retryScheduled()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("stoppingHttpStatuses")
    void persistsHttpFailureAndOpensTheCircuitWithoutRetry(
            int status,
            J3CircuitReason expectedReason) {
        RecordingStore store = new RecordingStore();
        J3ScheduledEventsOutcomeProcessor processor = processor(store);
        byte[] rawPayload = ("{\"error\":\"simulated-" + status + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        var outcome = processor.processResponse(response(
                status,
                "application/json",
                rawPayload,
                null));

        assertThat(outcome.circuit().state()).isEqualTo(J3CircuitState.OPEN);
        assertThat(outcome.circuit().reason()).isEqualTo(expectedReason);
        assertThat(outcome.circuit().retryNotBefore()).isNull();
        assertThat(outcome.retryScheduled()).isFalse();
        assertThat(store.uniqueSnapshots()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.payload().bytes()).isEqualTo(rawPayload);
            assertThat(snapshot.schemaStatus())
                    .isEqualTo(RawSnapshotSchemaStatus.TRANSPORT_ERROR);
            assertThat(snapshot.errorCode()).isEqualTo(expectedReason.name());
        });
    }

    @Test
    void keepsRetryAfterAsABlockingBoundaryButNeverRetries() {
        RecordingStore store = new RecordingStore();
        J3ScheduledEventsOutcomeProcessor processor = processor(store);
        Instant retryNotBefore = NOW.plusSeconds(120);

        var outcome = processor.processResponse(response(
                429,
                "application/json",
                "{\"error\":\"rate-limited\"}".getBytes(StandardCharsets.UTF_8),
                retryNotBefore));

        assertThat(outcome.circuit().state()).isEqualTo(J3CircuitState.OPEN);
        assertThat(outcome.circuit().reason())
                .isEqualTo(J3CircuitReason.HTTP_TOO_MANY_REQUESTS);
        assertThat(outcome.circuit().retryNotBefore()).isEqualTo(retryNotBefore);
        assertThat(outcome.retryScheduled()).isFalse();
        assertThat(store.saveAttempts()).isEqualTo(1);
    }

    @Test
    void appliesASafeHoldWhenRateLimitHasNoUsableRetryAfter() {
        J3ScheduledEventsOutcomeProcessor processor = processor(new RecordingStore());

        var outcome = processor.processResponse(response(
                429,
                "application/json",
                "{\"error\":\"rate-limited\"}".getBytes(StandardCharsets.UTF_8),
                null));

        assertThat(outcome.circuit().retryNotBefore())
                .isEqualTo(NOW.plus(J3ScheduledEventsOutcomeProcessor.DEFAULT_RATE_LIMIT_HOLD));
        assertThat(outcome.retryScheduled()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("incompatiblePayloads")
    void preservesUnexpectedOrIncompatibleRawPayloadBeforeOpeningTheCircuit(
            String contentType,
            byte[] rawPayload,
            RawSnapshotSchemaStatus expectedSchemaStatus,
            J3CircuitReason expectedReason) {
        RecordingStore store = new RecordingStore();
        J3ScheduledEventsOutcomeProcessor processor = processor(store);

        var outcome = processor.processResponse(response(
                200,
                contentType,
                rawPayload,
                null));

        RawManualCallSnapshot persisted = store.uniqueSnapshots().getFirst();
        assertThat(persisted.payload().bytes()).isEqualTo(rawPayload);
        assertThat(persisted.schemaStatus()).isEqualTo(expectedSchemaStatus);
        assertThat(persisted.errorCode()).isEqualTo(expectedSchemaStatus.name());
        assertThat(store.transitions()).containsExactly(
                "SAVE:RAW_ONLY",
                "CLASSIFY:" + expectedSchemaStatus);
        assertThat(outcome.circuit().state()).isEqualTo(J3CircuitState.OPEN);
        assertThat(outcome.circuit().reason()).isEqualTo(expectedReason);
        assertThat(outcome.retryScheduled()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("transportFailures")
    void transportFailuresOpenTheCircuitWithoutSnapshotOrRetry(
            ScheduledEventsTransportFailure failure,
            J3CircuitReason expectedReason) {
        RecordingStore store = new RecordingStore();
        J3ScheduledEventsOutcomeProcessor processor = processor(store);

        var outcome = processor.processFailure(
                new ScheduledEventsTransportException(failure),
                NOW);

        assertThat(outcome.persistence()).isEmpty();
        assertThat(store.saveAttempts()).isZero();
        assertThat(outcome.circuit().state()).isEqualTo(J3CircuitState.OPEN);
        assertThat(outcome.circuit().reason()).isEqualTo(expectedReason);
        assertThat(outcome.retryScheduled()).isFalse();
    }

    @Test
    void sensitiveTransportFailureNeverWritesTheRejectedValueToOutput(CapturedOutput output) {
        String forbiddenValue = "j3-sensitive-value-that-must-never-be-logged";
        Throwable sensitiveCause = catchThrowable(() ->
                RawPayloadEvidence.capture(
                        ("{\"access_token\":\"" + forbiddenValue + "\"}")
                                .getBytes(StandardCharsets.UTF_8)));
        assertThat(sensitiveCause)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(forbiddenValue);
        ScheduledEventsTransportException safeFailure = new ScheduledEventsTransportException(
                ScheduledEventsTransportFailure.SENSITIVE_CONTENT_REJECTED);

        var outcome = processor(new RecordingStore()).processFailure(safeFailure, NOW);

        assertThat(outcome.circuit().reason())
                .isEqualTo(J3CircuitReason.SENSITIVE_CONTENT_REJECTED);
        assertThat(safeFailure.getMessage()).doesNotContain(forbiddenValue);
        assertThat(output.getAll()).doesNotContain(forbiddenValue);
    }

    @Test
    void outcomeModelRejectsAnyAttemptToScheduleARetry() {
        J3NetworkCircuit circuit = J3NetworkCircuit.lockedAt(NOW.minusSeconds(1));

        assertThatThrownBy(() -> new J3ScheduledEventsOutcome(
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                circuit.snapshot(),
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never schedule a retry");
    }

    private static Stream<Arguments> stoppingHttpStatuses() {
        return Stream.of(
                Arguments.of(400, J3CircuitReason.HTTP_BAD_REQUEST),
                Arguments.of(401, J3CircuitReason.HTTP_UNAUTHORIZED),
                Arguments.of(403, J3CircuitReason.HTTP_FORBIDDEN),
                Arguments.of(500, J3CircuitReason.SERVER_ERROR),
                Arguments.of(503, J3CircuitReason.SERVER_ERROR));
    }

    private static Stream<Arguments> incompatiblePayloads() {
        return Stream.of(
                Arguments.of(
                        "text/html",
                        "<!doctype html><html><body>simulated refusal</body></html>"
                                .getBytes(StandardCharsets.UTF_8),
                        RawSnapshotSchemaStatus.UNEXPECTED_CONTENT,
                        J3CircuitReason.UNEXPECTED_CONTENT),
                Arguments.of(
                        "application/json",
                        "{\"events\":[]}".getBytes(StandardCharsets.UTF_8),
                        RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                        J3CircuitReason.SCHEMA_INCOMPATIBLE));
    }

    private static Stream<Arguments> transportFailures() {
        return Stream.of(
                Arguments.of(
                        ScheduledEventsTransportFailure.TIMEOUT,
                        J3CircuitReason.TIMEOUT),
                Arguments.of(
                        ScheduledEventsTransportFailure.IO_FAILURE,
                        J3CircuitReason.TRANSPORT_IO_FAILURE),
                Arguments.of(
                        ScheduledEventsTransportFailure.PAYLOAD_TOO_LARGE,
                        J3CircuitReason.PAYLOAD_TOO_LARGE),
                Arguments.of(
                        ScheduledEventsTransportFailure.SENSITIVE_CONTENT_REJECTED,
                        J3CircuitReason.SENSITIVE_CONTENT_REJECTED));
    }

    private static J3ScheduledEventsOutcomeProcessor processor(RecordingStore store) {
        J3NetworkCircuit circuit = J3NetworkCircuit.lockedAt(NOW.minusSeconds(2));
        circuit.activateByOperator(NOW.minusSeconds(1));
        return new J3ScheduledEventsOutcomeProcessor(
                store,
                new ScheduledEventsV1Parser(),
                circuit);
    }

    private static ScheduledEventsTransportResponse response(
            int status,
            String contentType,
            byte[] rawPayload,
            Instant retryNotBefore) {
        return new ScheduledEventsTransportResponse(
                REQUEST_KEY,
                NOW.minusMillis(25),
                NOW,
                status,
                contentType,
                Duration.ofMillis(25),
                RawPayloadEvidence.capture(rawPayload),
                retryNotBefore);
    }

    private static final class RecordingStore implements RawManualCallSnapshotStore {

        private final Map<String, Stored> unique = new LinkedHashMap<>();
        private final java.util.ArrayList<String> transitions = new java.util.ArrayList<>();
        private int saveAttempts;

        @Override
        public RawSnapshotPersistenceResult save(RawManualCallSnapshot snapshot) {
            saveAttempts++;
            transitions.add("SAVE:" + snapshot.schemaStatus());
            String key = snapshot.endpointType() + "|" + snapshot.requestKey()
                    + "|" + snapshot.payload().sha256();
            Stored existing = unique.get(key);
            if (existing != null) {
                return result(existing.id(), RawSnapshotPersistenceOutcome.DEDUPLICATED, snapshot);
            }
            long id = unique.size() + 1L;
            unique.put(key, new Stored(id, snapshot));
            return result(id, RawSnapshotPersistenceOutcome.INSERTED, snapshot);
        }

        @Override
        public void classify(
                long snapshotId,
                RawSnapshotSchemaStatus schemaStatus,
                String errorCode) {
            transitions.add("CLASSIFY:" + schemaStatus);
            Map.Entry<String, Stored> matching = unique.entrySet().stream()
                    .filter(entry -> entry.getValue().id() == snapshotId)
                    .findFirst()
                    .orElseThrow();
            RawManualCallSnapshot current = matching.getValue().snapshot();
            if (current.schemaStatus() != RawSnapshotSchemaStatus.RAW_ONLY
                    && (current.schemaStatus() != schemaStatus
                    || !java.util.Objects.equals(current.errorCode(), errorCode))) {
                throw new IllegalStateException("incompatible repeated classification");
            }
            RawManualCallSnapshot classified = new RawManualCallSnapshot(
                    current.endpointType(),
                    current.requestKey(),
                    current.requestedAt(),
                    current.receivedAt(),
                    current.httpStatus(),
                    current.contentType(),
                    current.latency(),
                    current.payload(),
                    current.parserVersion(),
                    schemaStatus,
                    errorCode);
            matching.setValue(new Stored(snapshotId, classified));
        }

        int saveAttempts() {
            return saveAttempts;
        }

        java.util.List<RawManualCallSnapshot> uniqueSnapshots() {
            return unique.values().stream().map(Stored::snapshot).toList();
        }

        List<String> transitions() {
            return List.copyOf(transitions);
        }

        private static RawSnapshotPersistenceResult result(
                long id,
                RawSnapshotPersistenceOutcome outcome,
                RawManualCallSnapshot snapshot) {
            return new RawSnapshotPersistenceResult(
                    id,
                    outcome,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes());
        }

        private record Stored(long id, RawManualCallSnapshot snapshot) {
        }
    }
}
