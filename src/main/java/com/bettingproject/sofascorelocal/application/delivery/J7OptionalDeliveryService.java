package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7ValidatedExportArtifact;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgement;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgementStatus;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryState;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransport;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportException;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportRequest;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Explicit one-shot orchestration shared by the synthetic qualification and the manually invoked
 * WO-035 runtime boundary. It remains a plain object: only {@link J7DeliveryRuntimeService} is
 * Spring-composed, after all policy and confirmation gates.
 */
public final class J7OptionalDeliveryService {

    private final J7CanonicalExportService exportService;
    private final J7DeliveryLedgerStore ledgerStore;
    private final J7DeliveryTransport transport;
    private final J7DeliveryAcknowledgementParser acknowledgementParser;
    private final J7DeliveryPolicy policy;
    private final Clock clock;

    public J7OptionalDeliveryService(
            J7CanonicalExportService exportService,
            J7DeliveryLedgerStore ledgerStore,
            J7DeliveryTransport transport,
            J7DeliveryAcknowledgementParser acknowledgementParser,
            J7DeliveryPolicy policy,
            Clock clock) {
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.ledgerStore = Objects.requireNonNull(ledgerStore, "ledgerStore");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.acknowledgementParser = Objects.requireNonNull(
                acknowledgementParser, "acknowledgementParser");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public J7DeliveryExecutionResult deliverSyntheticLoopback(
            UUID canonicalEventId,
            UUID exportId,
            String confirmation,
            int expectedAttemptNumber) {
        policy.requireSyntheticLoopbackQualification();
        return deliverPreparedTransport(
                canonicalEventId,
                exportId,
                confirmation,
                J7DeliveryPayloadClass.SYNTHETIC_ONLY,
                expectedAttemptNumber);
    }

    J7DeliveryExecutionResult deliverPreparedTransport(
            UUID canonicalEventId,
            UUID exportId,
            String confirmation,
            J7DeliveryPayloadClass expectedPayloadClass,
            int expectedAttemptNumber) {
        J7ValidatedExportArtifact artifact = exportService.loadHumanValidatedForDelivery(
                canonicalEventId, exportId);
        return deliverVerifiedArtifact(
                artifact,
                confirmation,
                expectedPayloadClass,
                expectedAttemptNumber);
    }

    J7DeliveryExecutionResult deliverVerifiedArtifact(
            J7ValidatedExportArtifact artifact,
            String confirmation,
            J7DeliveryPayloadClass expectedPayloadClass,
            int expectedAttemptNumber) {
        Objects.requireNonNull(artifact, "artifact");
        if (expectedPayloadClass != null
                && artifact.payloadClass() != expectedPayloadClass) {
            throw new J7DeliveryException(
                    J7DeliveryError.PAYLOAD_PROVENANCE_NOT_ELIGIBLE);
        }
        J7DeliveryIdentity identity = new J7DeliveryIdentity(
                artifact.exportId(), artifact.fileSha256());
        requireExactConfirmation(confirmation, confirmationFor(identity));

        Instant startedAt = now();
        J7DeliveryLedgerStore.ClaimReceipt claim = ledgerStore.claim(
                identity.exportId(),
                identity.fileSha256(),
                artifact.dataSha256(),
                identity.idempotencyKey(),
                expectedAttemptNumber,
                startedAt);
        Completion completion;
        try {
            J7DeliveryTransportResponse response = transport.execute(
                    new J7DeliveryTransportRequest(
                            identity, artifact.dataSha256(), artifact.content()));
            completion = classify(
                    identity, artifact.dataSha256(), response);
        }
        catch (J7DeliveryTransportException exception) {
            completion = Completion.unknown(
                    OptionalInt.empty(),
                    "TRANSPORT_" + exception.failure().name());
        }
        catch (RuntimeException exception) {
            completion = Completion.unknown(
                    OptionalInt.empty(),
                    "TRANSPORT_RUNTIME_FAILURE");
        }
        return complete(claim, completion, now());
    }

    public static String confirmationFor(J7DeliveryIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return "LIVRER J7 " + identity.exportId()
                + " SHA256 " + identity.fileSha256();
    }

    private Completion classify(
            J7DeliveryIdentity identity,
            String dataSha256,
            J7DeliveryTransportResponse response) {
        int status = response.httpStatus();
        if (status >= 200 && status <= 299) {
            try {
                if (!isAcknowledgementMediaType(response.contentType())) {
                    return Completion.unknown(
                            OptionalInt.of(status), "ACK_CONTENT_TYPE_INVALID");
                }
                J7DeliveryAcknowledgement acknowledgement = acknowledgementParser.parse(
                        response.acknowledgement());
                J7DeliveryContract.requireAcknowledgementMatches(
                        identity, dataSha256, acknowledgement);
                boolean imported = status == 201
                        && acknowledgement.status()
                        == J7DeliveryAcknowledgementStatus.IMPORTED;
                boolean duplicate = status == 200
                        && acknowledgement.status()
                        == J7DeliveryAcknowledgementStatus.DUPLICATE;
                if (!imported && !duplicate) {
                    return Completion.unknown(
                            OptionalInt.of(status), "ACK_HTTP_STATUS_MISMATCH");
                }
                // receivedAt is declared by the receiver's independent wall clock. In
                // particular, DUPLICATE reuses the first durable import time. Its canonical
                // value is retained verbatim but cannot be ordered against local attempt times.
                return new Completion(
                        imported
                                ? J7DeliveryState.DELIVERED
                                : J7DeliveryState.DUPLICATE_CONFIRMED,
                        OptionalInt.of(status),
                        imported ? "HTTP_201_IMPORTED" : "HTTP_DUPLICATE_CONFIRMED",
                        Optional.of(sha256(response.acknowledgement())),
                        Optional.of(acknowledgement.remoteImportId()),
                        Optional.of(acknowledgement.receivedAt()));
            }
            catch (J7DeliveryException exception) {
                return Completion.unknown(
                        OptionalInt.of(status), "ACK_INVALID_OR_MISMATCHED");
            }
        }
        if (status >= 400 && status <= 499) {
            return new Completion(
                    J7DeliveryState.REJECTED_TERMINAL,
                    OptionalInt.of(status),
                    "HTTP_4XX_REJECTED",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        }
        return Completion.unknown(
                OptionalInt.of(status),
                status >= 500 ? "HTTP_5XX_AMBIGUOUS" : "HTTP_AMBIGUOUS");
    }

    private J7DeliveryExecutionResult complete(
            J7DeliveryLedgerStore.ClaimReceipt claim,
            Completion completion,
            Instant completedAt) {
        J7DeliveryLedgerStore.DeliverySnapshot snapshot = ledgerStore.complete(
                claim.deliveryId(),
                claim.attemptNumber(),
                ledgerState(completion.state()),
                completion.httpStatus(),
                completion.safeResultCode(),
                completion.acknowledgementSha256(),
                completion.remoteImportId(),
                completion.acknowledgementReceivedAt(),
                completedAt);
        return new J7DeliveryExecutionResult(
                snapshot.deliveryId(),
                claim.attemptNumber(),
                completion.state(),
                completion.httpStatus(),
                completion.safeResultCode());
    }

    private static J7DeliveryLedgerStore.DeliveryState ledgerState(J7DeliveryState state) {
        return J7DeliveryLedgerStore.DeliveryState.valueOf(state.name());
    }

    private static boolean isAcknowledgementMediaType(String contentType) {
        return contentType != null
                && J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE
                .equalsIgnoreCase(contentType.trim());
    }

    static void requireExactConfirmation(String actual, String expected) {
        if (actual == null
                || actual.length() > 256
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        actual.getBytes(StandardCharsets.UTF_8))) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_CONFIRMATION);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Completion(
            J7DeliveryState state,
            OptionalInt httpStatus,
            String safeResultCode,
            Optional<String> acknowledgementSha256,
            Optional<UUID> remoteImportId,
            Optional<Instant> acknowledgementReceivedAt) {

        private Completion {
            state = Objects.requireNonNull(state, "state");
            httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
            safeResultCode = Objects.requireNonNull(safeResultCode, "safeResultCode");
            acknowledgementSha256 = Objects.requireNonNull(
                    acknowledgementSha256, "acknowledgementSha256");
            remoteImportId = Objects.requireNonNull(remoteImportId, "remoteImportId");
            acknowledgementReceivedAt = Objects.requireNonNull(
                    acknowledgementReceivedAt, "acknowledgementReceivedAt");
        }

        private static Completion unknown(OptionalInt httpStatus, String safeResultCode) {
            return new Completion(
                    J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                    httpStatus,
                    safeResultCode,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        }
    }
}
