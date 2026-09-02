package com.bettingproject.sofascorelocal.port;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Late transport boundary. Implementations may inspect a local certificate store, but must not
 * connect to the receiver before {@link J7DeliveryTransport#execute} is called.
 */
public interface J7DeliveryTransportFactory {

    OwnedJ7DeliveryTransport open(Configuration configuration);

    record Configuration(
            URI receiverOrigin,
            String clientCertificateSha256,
            Duration connectTimeout,
            Duration requestTimeout) {

        private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

        public Configuration {
            receiverOrigin = Objects.requireNonNull(receiverOrigin, "receiverOrigin");
            if (clientCertificateSha256 == null
                    || !SHA_256.matcher(clientCertificateSha256).matches()) {
                throw new IllegalArgumentException(
                        "clientCertificateSha256 must be a lower-case SHA-256");
            }
            connectTimeout = requireBounded(connectTimeout, "connectTimeout");
            requestTimeout = requireBounded(requestTimeout, "requestTimeout");
        }

        private static Duration requireBounded(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isZero()
                    || value.isNegative()
                    || value.compareTo(Duration.ofSeconds(10)) > 0) {
                throw new IllegalArgumentException(name + " is outside the delivery boundary");
            }
            return value;
        }
    }
}
