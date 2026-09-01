package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryContract;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.security.Sha256;

import java.util.Objects;
import java.util.regex.Pattern;

public record J7DeliveryTransportRequest(
        J7DeliveryIdentity identity,
        String dataSha256,
        byte[] content) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J7DeliveryTransportRequest {
        identity = Objects.requireNonNull(identity, "identity");
        if (dataSha256 == null || !SHA_256.matcher(dataSha256).matches()) {
            throw new IllegalArgumentException("dataSha256 must be a lower-case SHA-256");
        }
        content = Objects.requireNonNull(content, "content").clone();
        J7DeliveryContract.requirePayloadSize(content.length);
        if (!identity.fileSha256().equals(Sha256.hex(content))) {
            throw new IllegalArgumentException("content does not match the delivery identity");
        }
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
