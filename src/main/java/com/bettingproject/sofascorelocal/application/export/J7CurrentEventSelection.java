package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotTrace;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One repeatable-read selection of the five current J7 component slots.
 *
 * <p>The record deliberately contains normalized views and snapshot metadata only. It never
 * contains, nor provides access to, provider payload bytes.</p>
 */
public record J7CurrentEventSelection(
        CanonicalEventObservationView eventState,
        Optional<EventDetailObservationView> eventDetails,
        J5EventDataBundle eventData,
        Map<Long, J6SnapshotTrace> snapshotTraces) {

    public J7CurrentEventSelection {
        eventState = Objects.requireNonNull(eventState, "eventState");
        eventDetails = Objects.requireNonNull(eventDetails, "eventDetails");
        eventData = Objects.requireNonNull(eventData, "eventData");
        snapshotTraces = Map.copyOf(Objects.requireNonNull(snapshotTraces, "snapshotTraces"));
    }
}
