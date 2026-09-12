package com.bettingproject.sofascorelocal.domain.benchmark;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record J8BenchmarkCampaignResult(
        UUID campaignId,
        Instant finishedAt,
        J8BenchmarkCampaignTerminalState terminalState,
        Optional<String> terminalCode,
        int completedUnits) {

    public J8BenchmarkCampaignResult {
        campaignId = Objects.requireNonNull(campaignId, "campaignId");
        finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
        terminalState = Objects.requireNonNull(terminalState, "terminalState");
        terminalCode = J8BenchmarkValidation.optionalSafeCode(terminalCode, "terminalCode");
        if (completedUnits < 0
                || completedUnits > J8BenchmarkCampaignType.MAXIMUM_SUPPORTED_UNITS) {
            throw new IllegalArgumentException(
                    "completedUnits must be between 0 and "
                            + J8BenchmarkCampaignType.MAXIMUM_SUPPORTED_UNITS);
        }
        if (terminalState != J8BenchmarkCampaignTerminalState.COMPLETED
                && terminalCode.isEmpty()) {
            throw new IllegalArgumentException(
                    "a failed or cancelled campaign requires a terminalCode");
        }
    }
}
