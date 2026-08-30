package com.bettingproject.sofascorelocal.domain.benchmark;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record J8BenchmarkCampaign(
        UUID campaignId,
        J8BenchmarkCampaignType campaignType,
        J8BenchmarkExecutionMode executionMode,
        Instant startedAt,
        int maximumUnits,
        Optional<LocalDate> collectionDate) {

    public J8BenchmarkCampaign {
        campaignId = Objects.requireNonNull(campaignId, "campaignId");
        campaignType = Objects.requireNonNull(campaignType, "campaignType");
        executionMode = Objects.requireNonNull(executionMode, "executionMode");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        collectionDate = Objects.requireNonNull(collectionDate, "collectionDate");

        if (maximumUnits != campaignType.maximumUnits()) {
            throw new IllegalArgumentException(
                    "maximumUnits must match the bounded campaign type");
        }
        boolean j3DatedCampaign = campaignType == J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS
                || campaignType == J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY;
        if (j3DatedCampaign != collectionDate.isPresent()) {
            throw new IllegalArgumentException(
                    "J3 campaigns require a collection date and J4/J5 campaigns forbid it");
        }
        if (executionMode == J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT
                && !campaignType.acceptsManualImport()) {
            throw new IllegalArgumentException(
                    "manual local import is outside this benchmark campaign type");
        }
    }
}
