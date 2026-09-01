package com.bettingproject.sofascorelocal.domain.delivery;

import java.util.UUID;

/**
 * Stable delivery identity. Reconciliation and any operator-authorized repeat
 * must reuse this exact export identifier and terminal file hash.
 */
public record J7DeliveryIdentity(
        UUID exportId,
        String fileSha256) {

    private static final String IDEMPOTENCY_PREFIX = "j7:";
    private static final String IDEMPOTENCY_HASH_MARKER = ":sha256:";

    public J7DeliveryIdentity {
        exportId = J7DeliveryValueGuard.requireUuid(
                exportId, J7DeliveryError.INVALID_EXPORT_ID);
        fileSha256 = J7DeliveryValueGuard.requireSha256(
                fileSha256, J7DeliveryError.INVALID_FILE_SHA256);
    }

    public String idempotencyKey() {
        return IDEMPOTENCY_PREFIX
                + exportId
                + IDEMPOTENCY_HASH_MARKER
                + fileSha256;
    }
}
