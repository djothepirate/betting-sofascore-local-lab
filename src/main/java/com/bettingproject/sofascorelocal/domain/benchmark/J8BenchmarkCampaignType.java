package com.bettingproject.sofascorelocal.domain.benchmark;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

public enum J8BenchmarkCampaignType {
    J3_SCHEDULED_EVENTS(25),
    J3_TOURNAMENT_DISCOVERY(1),
    J4_EVENT_DETAILS_PHASE1(2),
    J4_EVENT_DETAILS_PHASE2(1),
    J5_EVENT_DATA(3);

    private final int maximumUnits;

    J8BenchmarkCampaignType(int maximumUnits) {
        this.maximumUnits = maximumUnits;
    }

    public int maximumUnits() {
        return maximumUnits;
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
