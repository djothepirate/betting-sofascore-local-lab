package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryState;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

public record J7DeliveryExecutionResult(
        UUID deliveryId,
        int attemptNumber,
        J7DeliveryState state,
        OptionalInt httpStatus,
        String safeResultCode) {

    public J7DeliveryExecutionResult {
        deliveryId = Objects.requireNonNull(deliveryId, "deliveryId");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        state = Objects.requireNonNull(state, "state");
        httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
        safeResultCode = Objects.requireNonNull(safeResultCode, "safeResultCode");
    }
}
