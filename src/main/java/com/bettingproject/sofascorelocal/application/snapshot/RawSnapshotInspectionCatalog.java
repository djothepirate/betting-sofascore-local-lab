package com.bettingproject.sofascorelocal.application.snapshot;

import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;

import java.util.List;
import java.util.Objects;

public record RawSnapshotInspectionCatalog(
        boolean available,
        List<RawSnapshotInspectionSummary> snapshots) {

    public RawSnapshotInspectionCatalog {
        Objects.requireNonNull(snapshots, "snapshots");
        snapshots = List.copyOf(snapshots);
        if (!available && !snapshots.isEmpty()) {
            throw new IllegalArgumentException(
                    "an unavailable inspection catalog cannot expose snapshots");
        }
    }

    public static RawSnapshotInspectionCatalog available(
            List<RawSnapshotInspectionSummary> snapshots) {
        return new RawSnapshotInspectionCatalog(true, snapshots);
    }

    public static RawSnapshotInspectionCatalog unavailable() {
        return new RawSnapshotInspectionCatalog(false, List.of());
    }
}
