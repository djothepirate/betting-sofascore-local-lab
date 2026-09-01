package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryTransportRequestTest {

    private static final UUID EXPORT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final byte[] CONTENT = new byte[] {'{', '}'};

    @Test
    void acceptsOnlyExactBoundedBytesAndDefensivelyCopiesThem() {
        var identity = new J7DeliveryIdentity(EXPORT_ID, Sha256.hex(CONTENT));
        byte[] mutable = CONTENT.clone();
        var request = new J7DeliveryTransportRequest(
                identity, "b".repeat(64), mutable);

        mutable[0] = '[';
        byte[] exposed = request.content();
        exposed[0] = '[';

        assertThat(request.content()).containsExactly((byte) '{', (byte) '}');
    }

    @Test
    void rejectsBytesThatDoNotMatchTheIdentityBeforeAnyTransport() {
        var identity = new J7DeliveryIdentity(EXPORT_ID, "a".repeat(64));

        assertThatThrownBy(() -> new J7DeliveryTransportRequest(
                identity, "b".repeat(64), CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content does not match the delivery identity");
    }
}
