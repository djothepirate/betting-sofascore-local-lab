package com.bettingproject.sofascorelocal.domain.export;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J7ExportManifestDraft(
        UUID exportId,
        UUID canonicalEventId,
        String schemaId,
        String schemaVersion,
        Instant generatedAt,
        String dataSha256,
        String sourceSetSha256,
        String candidateContentSha256,
        long contentSizeBytes,
        String relativePath,
        String sourceObservationsJson,
        List<Long> sourceSnapshotIds,
        String warningsJson) {

    public J7ExportManifestDraft {
        exportId = Objects.requireNonNull(exportId, "exportId");
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        schemaId = Objects.requireNonNull(schemaId, "schemaId");
        schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        dataSha256 = Objects.requireNonNull(dataSha256, "dataSha256");
        sourceSetSha256 = Objects.requireNonNull(sourceSetSha256, "sourceSetSha256");
        candidateContentSha256 = Objects.requireNonNull(
                candidateContentSha256, "candidateContentSha256");
        if (contentSizeBytes < 1) {
            throw new IllegalArgumentException("contentSizeBytes must be positive");
        }
        relativePath = Objects.requireNonNull(relativePath, "relativePath");
        sourceObservationsJson = Objects.requireNonNull(
                sourceObservationsJson, "sourceObservationsJson");
        sourceSnapshotIds = List.copyOf(Objects.requireNonNull(
                sourceSnapshotIds, "sourceSnapshotIds"));
        warningsJson = Objects.requireNonNull(warningsJson, "warningsJson");
    }
}
