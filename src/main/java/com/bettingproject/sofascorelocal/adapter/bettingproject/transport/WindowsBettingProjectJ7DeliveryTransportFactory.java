package com.bettingproject.sofascorelocal.adapter.bettingproject.transport;

import com.bettingproject.sofascorelocal.config.OptionalLocalPushProperties;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFactory;
import com.bettingproject.sofascorelocal.port.OwnedJ7DeliveryTransport;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.util.Objects;

/**
 * Opens one loopback sender from the Windows current-user certificate stores. Constructing this
 * Spring component performs no certificate-store access and starts no HTTP client.
 */
@Component
public final class WindowsBettingProjectJ7DeliveryTransportFactory
        implements J7DeliveryTransportFactory {

    private final WindowsUserCertificateSslContextFactory sslContextFactory;
    private final Clock clock;

    public WindowsBettingProjectJ7DeliveryTransportFactory() {
        this(new WindowsUserCertificateSslContextFactory(), Clock.systemUTC());
    }

    WindowsBettingProjectJ7DeliveryTransportFactory(
            WindowsUserCertificateSslContextFactory sslContextFactory,
            Clock clock) {
        this.sslContextFactory = Objects.requireNonNull(
                sslContextFactory, "sslContextFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OwnedJ7DeliveryTransport open(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        JdkHttpClientNoAutomaticRetryPolicy.requireSatisfiedFromStartupAndNow();
        URI receiverOrigin = configuration.receiverOrigin();
        if (!OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN
                .equals(receiverOrigin.toString())) {
            throw new IllegalArgumentException(
                    "receiverOrigin must be the exact local Betting Project receiver");
        }
        return BettingProjectJ7DeliveryHttpTransport.forLocalReceiver(
                receiverOrigin,
                sslContextFactory.create(configuration.clientCertificateSha256()),
                configuration.connectTimeout(),
                configuration.requestTimeout(),
                clock);
    }
}
