package com.bettingproject.sofascorelocal.domain.benchmark;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;

public enum J8BenchmarkCampaignType {
    J3_SCHEDULED_EVENTS(ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE),
    J3_TOURNAMENT_DISCOVERY(1),
    J4_EVENT_DETAILS_PHASE1(2),
    J4_EVENT_DETAILS_PHASE2(1),
    J5_EVENT_DATA(3);

    /** Largest unit count understood by the J8 domain boundary. */
    public static final int MAXIMUM_SUPPORTED_UNITS =
            ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE;
    /** Immutable J3 campaigns recorded before the cap was raised to 35 pages. */
    public static final int HISTORICAL_J3_SCHEDULED_EVENTS_MAXIMUM_UNITS = 25;

    private final int maximumUnits;

    J8BenchmarkCampaignType(int maximumUnits) {
        this.maximumUnits = maximumUnits;
    }

    public int maximumUnits() {
        return maximumUnits;
    }

    /**
     * Accepts the current bound for new campaigns and the documented historical J3 bound only
     * when reading append-only evidence written before the cap increase.
     */
    public boolean acceptsStoredMaximumUnits(int candidate) {
        return candidate == maximumUnits
                || (this == J3_SCHEDULED_EVENTS
                        && candidate == HISTORICAL_J3_SCHEDULED_EVENTS_MAXIMUM_UNITS);
    }

    public boolean accepts(SofascoreEndpointType endpointType) {
        return switch (this) {
            case J3_SCHEDULED_EVENTS -> endpointType == SofascoreEndpointType.SCHEDULED_EVENTS;
            case J3_TOURNAMENT_DISCOVERY ->
                    endpointType == SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS;
            case J4_EVENT_DETAILS_PHASE1, J4_EVENT_DETAILS_PHASE2 ->
                    endpointType == SofascoreEndpointType.EVENT_DETAILS;
            case J5_EVENT_DATA -> endpointType == SofascoreEndpointType.EVENT_STATISTICS
                    || endpointType == SofascoreEndpointType.EVENT_INCIDENTS
                    || endpointType == SofascoreEndpointType.EVENT_LINEUPS;
        };
    }

    public boolean acceptsManualImport() {
        return this == J3_SCHEDULED_EVENTS
                || this == J3_TOURNAMENT_DISCOVERY
                || this == J5_EVENT_DATA;
    }
}
