package com.bettingproject.sofascorelocal.port;

import java.time.Instant;
import java.util.Objects;

public record J7DeliveryTransportResponse(
        int httpStatus,
        String contentType,
        byte[] acknowledgement,
        Instant receivedAt) {

    public J7DeliveryTransportResponse {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("httpStatus is invalid");
        }
        contentType = Objects.requireNonNull(contentType, "contentType");
        if (contentType.length() > 128) {
            throw new IllegalArgumentException("contentType is too long");
        }
        acknowledgement = Objects.requireNonNull(
                acknowledgement, "acknowledgement").clone();
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
    }

    @Override
    public byte[] acknowledgement() {
        return acknowledgement.clone();
    }
}
