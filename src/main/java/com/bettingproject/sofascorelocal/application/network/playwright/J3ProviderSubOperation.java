package com.bettingproject.sofascorelocal.application.network.playwright;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Server-issued J3 scope inside one existing live runtime; no URI or session data. */
public record J3ProviderSubOperation(UUID runId,LocalDate date,Instant deadline) {
    public J3ProviderSubOperation {Objects.requireNonNull(runId);Objects.requireNonNull(date);Objects.requireNonNull(deadline);}
}
