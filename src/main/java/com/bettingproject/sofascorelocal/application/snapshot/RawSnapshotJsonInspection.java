package com.bettingproject.sofascorelocal.application.snapshot;

import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;

import java.time.Instant;
import java.util.Objects;

public record RawSnapshotJsonInspection(
        RawSnapshotInspectionSummary summary,
        Instant inspectedAt,
        String formattedJson) {

    public RawSnapshotJsonInspection {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(inspectedAt, "inspectedAt");
        Objects.requireNonNull(formattedJson, "formattedJson");
        if (formattedJson.isBlank()) {
            throw new IllegalArgumentException("formattedJson must not be blank");
        }
    }
}
