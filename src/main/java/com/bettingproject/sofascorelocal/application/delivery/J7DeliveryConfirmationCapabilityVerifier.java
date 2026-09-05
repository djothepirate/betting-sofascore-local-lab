package com.bettingproject.sofascorelocal.application.delivery;

import java.time.Instant;

/**
 * Consumes the opaque, server-issued capability attached to a confirmed delivery receipt.
 *
 * <p>A receipt's public delivery identity is insufficient proof of the session-bound human
 * confirmation. Implementations must therefore recognize the exact receipt instance issued by
 * the confirmation boundary, enforce its expiry and consume it at most once.</p>
 */
public interface J7DeliveryConfirmationCapabilityVerifier {

    void consumeRuntimeCapability(J7DeliveryConfirmationReceipt receipt, Instant verifiedAt);
}
