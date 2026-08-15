package com.bettingproject.sofascorelocal.domain.eventdata;

import java.util.Objects;
import java.util.Optional;

public record J5EventDataBundle(
        Optional<J5EventDataObservationView> statistics,
        Optional<J5EventDataObservationView> incidents,
        Optional<J5EventDataObservationView> lineups) {

    public J5EventDataBundle {
        statistics = Objects.requireNonNull(statistics, "statistics");
        incidents = Objects.requireNonNull(incidents, "incidents");
        lineups = Objects.requireNonNull(lineups, "lineups");
        statistics.ifPresent(value -> requireType(value, EventStatistics.class));
        incidents.ifPresent(value -> requireType(value, EventIncidents.class));
        lineups.ifPresent(value -> requireType(value, EventLineups.class));
    }

    public static J5EventDataBundle empty() {
        return new J5EventDataBundle(Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static void requireType(
            J5EventDataObservationView value,
            Class<? extends J5EventData> expectedType) {
        if (!expectedType.isInstance(value.data())) {
            throw new IllegalArgumentException("J5 bundle family does not match its slot");
        }
    }
}
