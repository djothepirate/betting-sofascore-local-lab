package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;

public interface RawManualCallSnapshotStore {

    RawSnapshotPersistenceResult save(RawManualCallSnapshot snapshot);

    void classify(
            long snapshotId,
            RawSnapshotSchemaStatus schemaStatus,
            String errorCode);
}
