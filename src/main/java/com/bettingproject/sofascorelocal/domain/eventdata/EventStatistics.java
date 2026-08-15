package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.List;
import java.util.Objects;

public record EventStatistics(
        long providerEventId,
        List<EventStatisticMetric> metrics) implements J5EventData {

    public EventStatistics {
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        metrics = List.copyOf(Objects.requireNonNull(metrics, "metrics"));
    }

    @Override
    public SofascoreEndpointType endpointType() {
        return SofascoreEndpointType.EVENT_STATISTICS;
    }
}
