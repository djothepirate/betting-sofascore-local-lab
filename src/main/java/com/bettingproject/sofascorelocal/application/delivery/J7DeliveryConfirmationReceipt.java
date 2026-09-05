package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server-side proof that one exact, session-bound operator confirmation was consumed.
 *
 * <p>The optional provider-owner-go reference is deliberately carried only between trusted
 * application boundaries. It is never accepted from an HTTP parameter and is omitted from
 * {@link #toString()} together with all export identity material.</p>
 */
public final class J7DeliveryConfirmationReceipt {

    private static final UUID NIL_UUID = new UUID(0, 0);
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final J7DeliveryConfirmationAction action;
    private final UUID canonicalEventId;
    private final UUID exportId;
    private final String fileSha256;
    private final int attemptNumber;
    private final Optional<J7ProviderDerivedOwnerGo.Reference> providerOwnerGoReference;

    public J7DeliveryConfirmationReceipt(
            J7DeliveryConfirmationAction action,
            UUID canonicalEventId,
            UUID exportId,
            String fileSha256,
            int attemptNumber,
            Optional<J7ProviderDerivedOwnerGo.Reference> providerOwnerGoReference) {
        this.action = Objects.requireNonNull(action, "action");
        this.canonicalEventId = requireUuid(canonicalEventId, "canonicalEventId");
        this.exportId = requireUuid(exportId, "exportId");
        if (fileSha256 == null || !SHA_256.matcher(fileSha256).matches()) {
            throw new IllegalArgumentException("fileSha256 must be a lower-case SHA-256");
        }
        this.fileSha256 = fileSha256;
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        this.attemptNumber = attemptNumber;
        this.providerOwnerGoReference = Objects.requireNonNull(
                providerOwnerGoReference, "providerOwnerGoReference");
        if (action == J7DeliveryConfirmationAction.RECONCILIATION
                && providerOwnerGoReference.isPresent()) {
            throw new IllegalArgumentException(
                    "reconciliation cannot carry a provider owner-go reference");
        }
    }

    public J7DeliveryConfirmationAction action() {
        return action;
    }

    public UUID canonicalEventId() {
        return canonicalEventId;
    }

    public UUID exportId() {
        return exportId;
    }

    public String fileSha256() {
        return fileSha256;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public Optional<J7ProviderDerivedOwnerGo.Reference> providerOwnerGoReference() {
        return providerOwnerGoReference;
    }

    @Override
    public String toString() {
        return "J7DeliveryConfirmationReceipt[action=" + action + "]";
    }

    private static UUID requireUuid(UUID value, String name) {
        if (value == null
                || NIL_UUID.equals(value)
                || value.variant() != 2
                || value.version() < 1
                || value.version() > 5) {
            throw new IllegalArgumentException(name + " must be a non-nil RFC 4122 UUID");
        }
        return value;
    }
}
