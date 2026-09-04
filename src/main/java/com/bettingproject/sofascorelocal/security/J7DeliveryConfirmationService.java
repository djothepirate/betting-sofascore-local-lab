package com.bettingproject.sofascorelocal.security;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryConfirmationAction;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryConfirmationRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * In-memory, session-bound and one-shot confirmation service for manual J7 delivery operations.
 *
 * <p>The service stores only a digest of the session key, a digest of the random request id, the
 * delivery identity, action, exact attempt ordinal and expiry. The delivery phrase deliberately
 * remains the public contract {@code LIVRER J7 <exportId> SHA256 <fileSha256>}, while its hidden
 * confirmation identity is also bound to the next expected ledger attempt. It never stores export
 * bytes, acknowledgement bodies, receiver responses or certificate material. Every consume call
 * removes the current request before validation, including invalid, expired and unacknowledged
 * calls.</p>
 */
@Component
public final class J7DeliveryConfirmationService {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private static final Duration MINIMUM_TTL = Duration.ofSeconds(1);
    private static final Duration MAXIMUM_TTL = Duration.ofMinutes(10);
    private static final int MAXIMUM_ACTIVE_SESSIONS = 256;
    private static final int MAXIMUM_SESSION_KEY_LENGTH = 256;
    private static final int MAXIMUM_CONFIRMATION_TEXT_LENGTH = 256;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final UUID NIL_UUID = new UUID(0, 0);

    private final Object lock = new Object();
    private final Map<String, StoredConfirmation> confirmations = new HashMap<>();
    private final SecureRandom secureRandom;
    private final Clock preparationClock;
    private final Duration ttl;

    public J7DeliveryConfirmationService() {
        this(new SecureRandom(), Clock.systemUTC(), DEFAULT_TTL);
    }

    J7DeliveryConfirmationService(
            SecureRandom secureRandom,
            Clock preparationClock,
            Duration ttl) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.preparationClock = Objects.requireNonNull(
                preparationClock, "preparationClock");
        this.ttl = requireBoundedTtl(ttl);
    }

    /**
     * Prepares one request and replaces any earlier request for the same session key.
     */
    public J7DeliveryConfirmationRequest prepare(
            String sessionKey,
            J7DeliveryConfirmationAction action,
            UUID canonicalEventId,
            UUID exportId,
            String fileSha256,
            OptionalInt attemptNumber) {
        String sessionDigest = sessionDigest(sessionKey);
        ConfirmationIdentity identity = requireIdentity(
                action,
                canonicalEventId,
                exportId,
                fileSha256,
                attemptNumber);
        Instant preparedAt = preparationClock.instant();
        Instant expiresAt;
        try {
            expiresAt = preparedAt.plus(ttl);
        }
        catch (ArithmeticException exception) {
            throw new J7DeliveryConfirmationException(
                    J7DeliveryConfirmationError.INVALID_ACTION_CONTEXT);
        }
        UUID requestId = randomUuidV4();
        byte[] requestDigest = digestRequestId(requestId);
        String confirmationText = confirmationText(identity);

        synchronized (lock) {
            confirmations.entrySet().removeIf(entry ->
                    !preparedAt.isBefore(entry.getValue().expiresAt()));
            if (!confirmations.containsKey(sessionDigest)
                    && confirmations.size() >= MAXIMUM_ACTIVE_SESSIONS) {
                throw new J7DeliveryConfirmationException(
                        J7DeliveryConfirmationError.CONFIRMATION_CAPACITY_EXCEEDED);
            }
            confirmations.put(
                    sessionDigest,
                    new StoredConfirmation(identity, requestDigest, expiresAt));
        }
        return new J7DeliveryConfirmationRequest(
                requestId, confirmationText, expiresAt);
    }

    /**
     * Atomically consumes the exact request. Every call burns the session's current request.
     */
    public void consume(
            String sessionKey,
            J7DeliveryConfirmationAction action,
            UUID canonicalEventId,
            UUID exportId,
            String fileSha256,
            OptionalInt attemptNumber,
            UUID requestId,
            String confirmationText,
            boolean acknowledged,
            Clock clock) {
        String sessionDigest = sessionDigestOrInvalidConfirmation(sessionKey);
        StoredConfirmation stored;
        synchronized (lock) {
            stored = confirmations.remove(sessionDigest);
        }
        if (stored == null || clock == null) {
            throw invalidConfirmation();
        }

        ConfirmationIdentity submittedIdentity = identityOrInvalidConfirmation(
                action,
                canonicalEventId,
                exportId,
                fileSha256,
                attemptNumber);
        byte[] submittedRequestDigest = requestIdDigestOrZero(requestId);
        String expectedText = confirmationText(stored.identity());
        Instant now = clock.instant();

        boolean valid = acknowledged
                && now.isBefore(stored.expiresAt())
                && stored.identity().equals(submittedIdentity)
                && MessageDigest.isEqual(
                        stored.requestDigest(), submittedRequestDigest)
                && constantTimeEquals(expectedText, confirmationText);
        if (!valid) {
            throw invalidConfirmation();
        }
    }

    private UUID randomUuidV4() {
        long mostSignificantBits = secureRandom.nextLong();
        long leastSignificantBits = secureRandom.nextLong();
        mostSignificantBits &= 0xffffffffffff0fffL;
        mostSignificantBits |= 0x0000000000004000L;
        leastSignificantBits &= 0x3fffffffffffffffL;
        leastSignificantBits |= 0x8000000000000000L;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    private static ConfirmationIdentity requireIdentity(
            J7DeliveryConfirmationAction action,
            UUID canonicalEventId,
            UUID exportId,
            String fileSha256,
            OptionalInt attemptNumber) {
        if (action == null || attemptNumber == null) {
            throw new J7DeliveryConfirmationException(
                    J7DeliveryConfirmationError.INVALID_ACTION_CONTEXT);
        }
        if (!validUuid(canonicalEventId)
                || !validUuid(exportId)
                || fileSha256 == null
                || !SHA_256.matcher(fileSha256).matches()) {
            throw new J7DeliveryConfirmationException(
                    J7DeliveryConfirmationError.INVALID_DELIVERY_IDENTITY);
        }
        if (attemptNumber.isEmpty() || attemptNumber.getAsInt() < 1) {
            throw new J7DeliveryConfirmationException(
                    J7DeliveryConfirmationError.INVALID_ACTION_CONTEXT);
        }
        return new ConfirmationIdentity(
                action,
                canonicalEventId,
                exportId,
                fileSha256,
                Integer.valueOf(attemptNumber.getAsInt()));
    }

    private static ConfirmationIdentity identityOrInvalidConfirmation(
            J7DeliveryConfirmationAction action,
            UUID canonicalEventId,
            UUID exportId,
            String fileSha256,
            OptionalInt attemptNumber) {
        try {
            return requireIdentity(
                    action,
                    canonicalEventId,
                    exportId,
                    fileSha256,
                    attemptNumber);
        }
        catch (J7DeliveryConfirmationException exception) {
            throw invalidConfirmation();
        }
    }

    private static String confirmationText(ConfirmationIdentity identity) {
        if (identity.action() == J7DeliveryConfirmationAction.DELIVERY) {
            return "LIVRER J7 " + identity.exportId()
                    + " SHA256 " + identity.fileSha256();
        }
        return "RECONCILIER J7 " + identity.exportId()
                + " TENTATIVE " + identity.attemptNumber()
                + " SHA256 " + identity.fileSha256();
    }

    private static String sessionDigest(String sessionKey) {
        if (sessionKey == null
                || sessionKey.isBlank()
                || sessionKey.length() > MAXIMUM_SESSION_KEY_LENGTH) {
            throw new J7DeliveryConfirmationException(
                    J7DeliveryConfirmationError.INVALID_SESSION_KEY);
        }
        return Sha256.hex(sessionKey.getBytes(StandardCharsets.UTF_8));
    }

    private static String sessionDigestOrInvalidConfirmation(String sessionKey) {
        try {
            return sessionDigest(sessionKey);
        }
        catch (J7DeliveryConfirmationException exception) {
            throw invalidConfirmation();
        }
    }

    private static byte[] digestRequestId(UUID requestId) {
        return sha256(requestId.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] requestIdDigestOrZero(UUID requestId) {
        if (requestId == null
                || NIL_UUID.equals(requestId)
                || requestId.variant() != 2
                || requestId.version() != 4) {
            return new byte[32];
        }
        return digestRequestId(requestId);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null || actual.length() > MAXIMUM_CONFIRMATION_TEXT_LENGTH) {
            actual = "";
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean validUuid(UUID value) {
        return value != null
                && !NIL_UUID.equals(value)
                && value.variant() == 2
                && value.version() >= 1
                && value.version() <= 5;
    }

    private static Duration requireBoundedTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.compareTo(MINIMUM_TTL) < 0 || ttl.compareTo(MAXIMUM_TTL) > 0) {
            throw new IllegalArgumentException(
                    "ttl must be between one second and ten minutes");
        }
        return ttl;
    }

    private static J7DeliveryConfirmationException invalidConfirmation() {
        return new J7DeliveryConfirmationException(
                J7DeliveryConfirmationError.INVALID_OR_EXPIRED_CONFIRMATION);
    }

    private record ConfirmationIdentity(
            J7DeliveryConfirmationAction action,
            UUID canonicalEventId,
            UUID exportId,
            String fileSha256,
            Integer attemptNumber) {
    }

    private static final class StoredConfirmation {

        private final ConfirmationIdentity identity;
        private final byte[] requestDigest;
        private final Instant expiresAt;

        private StoredConfirmation(
                ConfirmationIdentity identity,
                byte[] requestDigest,
                Instant expiresAt) {
            this.identity = identity;
            this.requestDigest = requestDigest.clone();
            this.expiresAt = expiresAt;
        }

        private ConfirmationIdentity identity() {
            return identity;
        }

        private byte[] requestDigest() {
            return requestDigest.clone();
        }

        private Instant expiresAt() {
            return expiresAt;
        }
    }
}
