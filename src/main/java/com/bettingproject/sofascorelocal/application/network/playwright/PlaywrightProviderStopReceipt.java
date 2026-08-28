package com.bettingproject.sofascorelocal.application.network.playwright;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PlaywrightProviderStopReceipt(
        UUID campaignId,
        boolean activeCampaignSignalled,
        Instant acknowledgedAt,
        Duration acknowledgementLatency) {

    public PlaywrightProviderStopReceipt {
        Objects.requireNonNull(acknowledgedAt, "acknowledgedAt");
        Objects.requireNonNull(acknowledgementLatency, "acknowledgementLatency");
        if (activeCampaignSignalled != (campaignId != null)
                || acknowledgementLatency.isNegative()
                || acknowledgementLatency.compareTo(Duration.ofMillis(500)) > 0) {
            throw new IllegalArgumentException("invalid bounded Playwright stop receipt");
        }
    }
}
