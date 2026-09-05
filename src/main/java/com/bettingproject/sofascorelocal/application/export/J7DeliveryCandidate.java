package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryPayloadClass;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Minimal persisted identity used to evaluate delivery gates before any J7 file bytes are read.
 */
public record J7DeliveryCandidate(
        UUID exportId,
        UUID canonicalEventId,
        String fileSha256,
        J7DeliveryPayloadClass payloadClass) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final UUID NIL_UUID = new UUID(0, 0);

    public J7DeliveryCandidate {
        exportId = requireUuid(exportId, "exportId");
        canonicalEventId = requireUuid(canonicalEventId, "canonicalEventId");
        if (fileSha256 == null || !SHA_256.matcher(fileSha256).matches()) {
            throw new IllegalArgumentException("fileSha256 must be a lower-case SHA-256");
        }
        payloadClass = Objects.requireNonNull(payloadClass, "payloadClass");
    }

    private static UUID requireUuid(UUID value, String name) {
        if (value == null || NIL_UUID.equals(value)) {
            throw new IllegalArgumentException(name + " must be a non-nil UUID");
        }
        return value;
    }
}
