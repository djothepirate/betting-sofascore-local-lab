package com.bettingproject.sofascorelocal.adapter.bettingproject.transport;

import com.bettingproject.sofascorelocal.config.OptionalLocalPushProperties;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFactory;
import com.bettingproject.sofascorelocal.port.OwnedJ7DeliveryTransport;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WindowsBettingProjectJ7DeliveryTransportFactoryTest {

    private static final String CERTIFICATE_SHA256 = "a".repeat(64);

    @Test
    void constructionPerformsNoCertificateStoreOrClientWork() {
        WindowsUserCertificateSslContextFactory sslContextFactory =
                mock(WindowsUserCertificateSslContextFactory.class);

        new WindowsBettingProjectJ7DeliveryTransportFactory(
                sslContextFactory, Clock.systemUTC());

        verifyNoInteractions(sslContextFactory);
    }

    @Test
    void opensOneOwnedClientOnlyForTheExactLocalReceiver() throws Exception {
        WindowsUserCertificateSslContextFactory sslContextFactory =
                mock(WindowsUserCertificateSslContextFactory.class);
        when(sslContextFactory.create(CERTIFICATE_SHA256))
                .thenReturn(SSLContext.getDefault());
        var factory = new WindowsBettingProjectJ7DeliveryTransportFactory(
                sslContextFactory, Clock.systemUTC());

        OwnedJ7DeliveryTransport transport = factory.open(configuration(
                OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN));
        try {
            assertThat(transport)
                    .isInstanceOf(BettingProjectJ7DeliveryHttpTransport.class);
        }
        finally {
            transport.close();
        }

        verify(sslContextFactory).create(CERTIFICATE_SHA256);
    }

    @Test
    void rejectsAnotherLoopbackPortBeforeCertificateStoreAccess() {
        WindowsUserCertificateSslContextFactory sslContextFactory =
                mock(WindowsUserCertificateSslContextFactory.class);
        var factory = new WindowsBettingProjectJ7DeliveryTransportFactory(
                sslContextFactory, Clock.systemUTC());

        assertThatThrownBy(() -> factory.open(configuration(
                "https://127.0.0.1:8445")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("receiverOrigin must be the exact local Betting Project receiver");
        verifyNoInteractions(sslContextFactory);
    }

    private static J7DeliveryTransportFactory.Configuration configuration(String origin) {
        return new J7DeliveryTransportFactory.Configuration(
                URI.create(origin),
                CERTIFICATE_SHA256,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10));
    }
}
