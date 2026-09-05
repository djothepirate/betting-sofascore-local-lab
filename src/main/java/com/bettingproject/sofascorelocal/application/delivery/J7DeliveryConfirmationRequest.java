package com.bettingproject.sofascorelocal.application.delivery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque one-time request returned to the local web boundary.
 *
 * <p>The confirmation text is intentionally separate from the random request identifier. The
 * object omits both values from {@link #toString()} to avoid accidental disclosure in logs.</p>
 */
public final class J7DeliveryConfirmationRequest {

    private static final UUID NIL_UUID = new UUID(0, 0);

    private final UUID requestId;
    private final String confirmationText;
    private final Instant expiresAt;

    public J7DeliveryConfirmationRequest(
            UUID requestId,
            String confirmationText,
            Instant expiresAt) {
        if (requestId == null
                || NIL_UUID.equals(requestId)
                || requestId.variant() != 2
                || requestId.version() != 4) {
            throw new IllegalArgumentException("requestId must be a non-nil UUID v4");
        }
        if (confirmationText == null
                || confirmationText.isEmpty()
                || confirmationText.length() > 256) {
            throw new IllegalArgumentException("confirmationText is invalid");
        }
        this.requestId = requestId;
        this.confirmationText = confirmationText;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public UUID requestId() {
        return requestId;
    }

    public String confirmationText() {
        return confirmationText;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "J7DeliveryConfirmationRequest[expiresAt=" + expiresAt + "]";
    }
}
