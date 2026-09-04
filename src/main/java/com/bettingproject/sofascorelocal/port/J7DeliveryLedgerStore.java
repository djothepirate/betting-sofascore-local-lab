package com.bettingproject.sofascorelocal.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Separate persistence boundary for one manually initiated J7 delivery.
 *
 * <p>The ledger never stores export bytes, acknowledgement bodies, transport diagnostics or
 * certificate material. A retry from an unknown outcome is represented by a new append-only
 * attempt under the same immutable delivery identity and idempotency key. Every claim must carry
 * the exact next attempt ordinal confirmed by the operator boundary; the persistence adapter
 * revalidates it atomically before creating an attempt.</p>
 */
public interface J7DeliveryLedgerStore {

    Duration MINIMUM_STALE_IN_FLIGHT_AGE = Duration.ofSeconds(30);

    ClaimReceipt claim(
            UUID exportId,
            String fileSha256,
            String dataSha256,
            String idempotencyKey,
            int expectedAttemptNumber,
            Instant startedAt);

    DeliverySnapshot complete(
            UUID deliveryId,
            int attemptNumber,
            DeliveryState terminalState,
            OptionalInt httpStatus,
            String safeResultCode,
            Optional<String> acknowledgementSha256,
            Optional<UUID> remoteImportId,
            Optional<Instant> acknowledgementReceivedAt,
            Instant completedAt);

    DeliverySnapshot reconcileStaleInFlightAsUnknown(
            UUID deliveryId,
            int attemptNumber,
            Instant expectedStartedAt);

    Optional<DeliverySnapshot> find(UUID exportId, String fileSha256);

    enum DeliveryState {
        NOT_ATTEMPTED,
        IN_FLIGHT,
        DELIVERED,
        DUPLICATE_CONFIRMED,
        REJECTED_TERMINAL,
        UNKNOWN_RECONCILIATION_REQUIRED;

        public boolean isTerminalResult() {
            return this == DELIVERED
                    || this == DUPLICATE_CONFIRMED
                    || this == REJECTED_TERMINAL
                    || this == UNKNOWN_RECONCILIATION_REQUIRED;
        }

        public boolean forbidsAnotherClaim() {
            return this == IN_FLIGHT
                    || this == DELIVERED
                    || this == DUPLICATE_CONFIRMED
                    || this == REJECTED_TERMINAL;
        }
    }

    record ClaimReceipt(
            UUID deliveryId,
            int attemptNumber,
            DeliveryState state,
            String idempotencyKey,
            Instant startedAt) {

        public ClaimReceipt {
            deliveryId = requireUuid(deliveryId, "deliveryId");
            if (attemptNumber < 1) {
                throw new IllegalArgumentException("attemptNumber must be positive");
            }
            if (state != DeliveryState.IN_FLIGHT) {
                throw new IllegalArgumentException("a claim receipt must be IN_FLIGHT");
            }
            idempotencyKey = requireIdempotencyKey(idempotencyKey);
            startedAt = Objects.requireNonNull(startedAt, "startedAt");
            if (startedAt.getNano() % 1_000 != 0) {
                throw new IllegalArgumentException(
                        "startedAt must use PostgreSQL microsecond precision");
            }
        }
    }

    record DeliverySnapshot(
            UUID deliveryId,
            UUID exportId,
            String fileSha256,
            String dataSha256,
            long fileSizeBytes,
            String idempotencyKey,
            String protocolVersion,
            DeliveryState state,
            int attemptCount,
            Instant createdAt,
            Instant stateChangedAt) {

        public DeliverySnapshot {
            deliveryId = requireUuid(deliveryId, "deliveryId");
            exportId = requireUuid(exportId, "exportId");
            fileSha256 = requireSha256(fileSha256, "fileSha256");
            dataSha256 = requireSha256(dataSha256, "dataSha256");
            if (fileSizeBytes < 1 || fileSizeBytes > 5L * 1024L * 1024L) {
                throw new IllegalArgumentException("fileSizeBytes is outside the J7 boundary");
            }
            idempotencyKey = requireIdempotencyKey(idempotencyKey);
            String expectedIdempotencyKey = "j7:" + exportId
                    + ":sha256:" + fileSha256;
            if (!expectedIdempotencyKey.equals(idempotencyKey)) {
                throw new IllegalArgumentException(
                        "idempotencyKey does not match exportId and fileSha256");
            }
            if (!"1.0".equals(protocolVersion)) {
                throw new IllegalArgumentException("protocolVersion must be J7 delivery v1.0");
            }
            state = Objects.requireNonNull(state, "state");
            if (attemptCount < 0
                    || state == DeliveryState.NOT_ATTEMPTED && attemptCount != 0
                    || state != DeliveryState.NOT_ATTEMPTED && attemptCount < 1) {
                throw new IllegalArgumentException("attemptCount is inconsistent with state");
            }
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            stateChangedAt = Objects.requireNonNull(stateChangedAt, "stateChangedAt");
            if (stateChangedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("stateChangedAt precedes createdAt");
            }
        }
    }

    enum LedgerFailure {
        EXPORT_NOT_ELIGIBLE,
        IDENTITY_MISMATCH,
        DELIVERY_ALREADY_IN_FLIGHT,
        ANOTHER_DELIVERY_IN_FLIGHT,
        DELIVERY_TERMINAL,
        ATTEMPT_NOT_ACTIVE,
        ATTEMPT_ORDINAL_MISMATCH,
        ATTEMPT_NOT_STALE,
        INVALID_COMPLETION,
        STORAGE_UNAVAILABLE
    }

    final class LedgerException extends RuntimeException {

        private final LedgerFailure failure;

        public LedgerException(LedgerFailure failure) {
            super("J7_DELIVERY_LEDGER_" + Objects.requireNonNull(failure, "failure").name());
            this.failure = failure;
        }

        public LedgerFailure failure() {
            return failure;
        }
    }

    Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile(
            "j7:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}:sha256:[0-9a-f]{64}");

    private static String requireSha256(String value, String name) {
        if (value == null || !SHA_256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lower-case SHA-256");
        }
        return value;
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null || !IDEMPOTENCY_KEY_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("idempotencyKey does not match J7 delivery v1");
        }
        return value;
    }

    private static UUID requireUuid(UUID value, String name) {
        if (value == null
                || value.equals(new UUID(0, 0))
                || value.variant() != 2
                || value.version() < 1
                || value.version() > 5) {
            throw new IllegalArgumentException(name + " must be a non-nil RFC 4122 UUID");
        }
        return value;
    }
}
