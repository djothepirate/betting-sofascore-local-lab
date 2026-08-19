package com.bettingproject.sofascorelocal.domain.export;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record J7ExportManifest(
        long databaseId,
        UUID exportId,
        UUID canonicalEventId,
        String schemaId,
        String schemaVersion,
        Instant generatedAt,
        String dataSha256,
        String sourceSetSha256,
        String candidateContentSha256,
        String currentContentSha256,
        long currentSizeBytes,
        String relativePath,
        String sourceObservationsJson,
        List<Long> sourceSnapshotIds,
        String warningsJson,
        J7ExportStatus status,
        Optional<Instant> decidedAt,
        Optional<String> decisionReason) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J7ExportManifest {
        if (databaseId < 1 || currentSizeBytes < 1) {
            throw new IllegalArgumentException("manifest identifiers and size must be positive");
        }
        exportId = Objects.requireNonNull(exportId, "exportId");
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        schemaId = Objects.requireNonNull(schemaId, "schemaId");
        schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        requireSha(dataSha256, "dataSha256");
        requireSha(sourceSetSha256, "sourceSetSha256");
        requireSha(candidateContentSha256, "candidateContentSha256");
        requireSha(currentContentSha256, "currentContentSha256");
        relativePath = Objects.requireNonNull(relativePath, "relativePath");
        sourceObservationsJson = Objects.requireNonNull(
                sourceObservationsJson, "sourceObservationsJson");
        sourceSnapshotIds = List.copyOf(Objects.requireNonNull(
                sourceSnapshotIds, "sourceSnapshotIds"));
        warningsJson = Objects.requireNonNull(warningsJson, "warningsJson");
        status = Objects.requireNonNull(status, "status");
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        decisionReason = Objects.requireNonNull(decisionReason, "decisionReason");
        if (status == J7ExportStatus.COHERENCE_CHECKED
                && (decidedAt.isPresent() || decisionReason.isPresent())) {
            throw new IllegalArgumentException("pending candidate cannot have a decision");
        }
        if (status == J7ExportStatus.HUMAN_VALIDATED
                && (decidedAt.isEmpty() || decisionReason.isPresent())) {
            throw new IllegalArgumentException("validated export decision is inconsistent");
        }
        if (status == J7ExportStatus.REJECTED
                && (decidedAt.isEmpty() || decisionReason.isEmpty())) {
            throw new IllegalArgumentException("rejected export decision is incomplete");
        }
    }

    private static void requireSha(String value, String name) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lower-case SHA-256");
        }
    }
}
