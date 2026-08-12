package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;

public interface RawManualCallSnapshotStore {

    RawSnapshotPersistenceResult save(RawManualCallSnapshot snapshot);
}
