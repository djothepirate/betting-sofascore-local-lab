package com.bettingproject.sofascorelocal.domain.benchmark;

import java.time.Instant;
import java.util.Objects;

public record J8ProviderCallAttempt(long unitId, Instant startedAt) {

    public J8ProviderCallAttempt {
        if (unitId < 1) {
            throw new IllegalArgumentException("unitId must be positive");
        }
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }
}
