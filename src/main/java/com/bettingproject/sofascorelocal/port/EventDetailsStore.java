package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailPersistenceResult;

import java.util.Optional;
import java.util.UUID;

public interface EventDetailsStore {

    EventDetailPersistenceResult save(EventDetailObservation observation);

    Optional<EventDetailObservationView> findLatest(UUID canonicalEventId);
}
