package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;

import java.util.UUID;

public interface J5EventDataStore {

    J5EventDataPersistenceResult save(J5EventDataObservation observation);

    J5EventDataBundle findLatest(UUID canonicalEventId);
}
