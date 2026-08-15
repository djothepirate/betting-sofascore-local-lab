package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.List;
import java.util.Objects;

public record EventIncidents(
        long providerEventId,
        List<EventIncident> incidents) implements J5EventData {

    public EventIncidents {
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        incidents = List.copyOf(Objects.requireNonNull(incidents, "incidents"));
        for (int index = 0; index < incidents.size(); index++) {
            if (incidents.get(index).sequence() != index) {
                throw new IllegalArgumentException("incident sequence must match list order");
            }
        }
    }

    @Override
    public SofascoreEndpointType endpointType() {
        return SofascoreEndpointType.EVENT_INCIDENTS;
    }
}
