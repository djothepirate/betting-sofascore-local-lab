package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;

import java.util.List;
import java.util.Optional;

public interface RawSnapshotInspectionStore {

    List<RawSnapshotInspectionSummary> findRecent(int limit);

    Optional<RawSnapshotInspectionSource> findById(long snapshotId);
}
