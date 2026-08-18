package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.List;
import java.util.Optional;

/** Explicit local representation of an exact J5 endpoint returning HTTP 404. */
public final class J5UnavailableFamily {

    private J5UnavailableFamily() {
    }

    public static J5EventData emptyObservation(
            SofascoreEndpointType endpoint,
            long eventId) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> new EventStatistics(eventId, List.of());
            case EVENT_INCIDENTS -> new EventIncidents(eventId, List.of());
            case EVENT_LINEUPS -> new EventLineups(
                    eventId,
                    false,
                    new TeamLineup(LineupSide.HOME, Optional.empty(), List.of()),
                    new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of()));
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
    }

    public static String normalizerVersion(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> "event-statistics-unavailable-v1";
            case EVENT_INCIDENTS -> "event-incidents-unavailable-v1";
            case EVENT_LINEUPS -> "event-lineups-unavailable-v1";
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
    }

    static boolean matchesEmptyObservation(J5EventData data) {
        return switch (data) {
            case EventStatistics statistics -> statistics.metrics().isEmpty();
            case EventIncidents incidents -> incidents.incidents().isEmpty();
            case EventLineups lineups -> !lineups.confirmed()
                    && lineups.home().formation().isEmpty()
                    && lineups.home().players().isEmpty()
                    && lineups.away().formation().isEmpty()
                    && lineups.away().players().isEmpty();
        };
    }
}
