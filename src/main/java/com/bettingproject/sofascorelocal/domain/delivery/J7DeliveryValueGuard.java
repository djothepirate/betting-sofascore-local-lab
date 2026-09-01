package com.bettingproject.sofascorelocal.domain.delivery;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

final class J7DeliveryValueGuard {

    private static final UUID NIL_UUID = new UUID(0, 0);
    private static final Pattern LOWER_CASE_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private J7DeliveryValueGuard() {
    }

    static UUID requireUuid(UUID value, J7DeliveryError error) {
        if (value == null
                || NIL_UUID.equals(value)
                || value.variant() != 2
                || value.version() < 1
                || value.version() > 5) {
            throw new J7DeliveryException(error);
        }
        return value;
    }

    static String requireSha256(String value, J7DeliveryError error) {
        if (value == null || !LOWER_CASE_SHA_256.matcher(value).matches()) {
            throw new J7DeliveryException(error);
        }
        return value;
    }

    static Instant requireInstant(Instant value, J7DeliveryError error) {
        if (value == null) {
            throw new J7DeliveryException(error);
        }
        return value;
    }
}
