package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface J5EventDataStore {

    J5EventDataPersistenceResult save(J5EventDataObservation observation);

    J5EventDataBundle findLatest(UUID canonicalEventId);

    List<J5EventDataObservationView> findHistory(
            UUID canonicalEventId,
            SofascoreEndpointType endpointType);

    Optional<J5EventDataObservationView> findByObservationId(
            UUID canonicalEventId,
            SofascoreEndpointType endpointType,
            long observationId);
}
