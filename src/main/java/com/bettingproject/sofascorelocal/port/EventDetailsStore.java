package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailPersistenceResult;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface EventDetailsStore {

    EventDetailPersistenceResult save(EventDetailObservation observation);

    Optional<EventDetailObservationView> findLatest(UUID canonicalEventId);

    List<EventDetailObservationView> findHistory(UUID canonicalEventId);

    Optional<EventDetailObservationView> findByObservationId(
            UUID canonicalEventId,
            long observationId);
}
