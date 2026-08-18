package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventObservationPersistenceResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CanonicalEventStore {

    EventObservationPersistenceResult save(CanonicalEventObservation observation);

    List<CanonicalEventObservationView> findLatestStartingBetween(
            Instant fromInclusive,
            Instant toExclusive);

    Optional<CanonicalEventObservationView> findLatestByCanonicalId(UUID canonicalEventId);

    List<CanonicalEventObservationView> findHistory(UUID canonicalEventId);

    Optional<CanonicalEventObservationView> findByObservationId(
            UUID canonicalEventId,
            long observationId);
}
