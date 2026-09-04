package com.bettingproject.sofascorelocal.domain.delivery;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Persistence boundary for the receiver-authored durable receipt time.
 *
 * <p>The value is never ordered against the Local Lab clock. This policy only guarantees that the
 * canonical value can be stored and read back byte-semantically unchanged through PostgreSQL's
 * microsecond-resolution {@code timestamptz} mapping.</p>
 */
public final class J7DeliveryReceivedAtPolicy {

    private static final Instant MINIMUM = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAXIMUM = Instant.parse("9999-12-31T23:59:59.999999Z");

    private J7DeliveryReceivedAtPolicy() {
    }

    public static boolean isExactlyPersistable(Instant value) {
        return value != null
                && value.equals(value.truncatedTo(ChronoUnit.MICROS))
                && !value.isBefore(MINIMUM)
                && !value.isAfter(MAXIMUM);
    }

    static Instant requireExactlyPersistable(Instant value, J7DeliveryError error) {
        if (!isExactlyPersistable(value)) {
            throw new J7DeliveryException(error);
        }
        return value;
    }
}
