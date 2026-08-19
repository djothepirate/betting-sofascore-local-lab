package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable output of the pure J7 JSON assembler. */
public record J7AssembledEnvelope(
        UUID exportId,
        UUID canonicalEventId,
        J7ExportStatus status,
        Instant generatedAt,
        Optional<Instant> decidedAt,
        byte[] content,
        String dataSha256,
        String sourceSetSha256,
        List<Long> sourceSnapshotIds,
        String sourcesJson,
        String warningsJson) {

    public J7AssembledEnvelope {
        exportId = Objects.requireNonNull(exportId, "exportId");
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        status = Objects.requireNonNull(status, "status");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        content = Objects.requireNonNull(content, "content").clone();
        dataSha256 = Objects.requireNonNull(dataSha256, "dataSha256");
        sourceSetSha256 = Objects.requireNonNull(sourceSetSha256, "sourceSetSha256");
        sourceSnapshotIds = List.copyOf(Objects.requireNonNull(
                sourceSnapshotIds,
                "sourceSnapshotIds"));
        sourcesJson = Objects.requireNonNull(sourcesJson, "sourcesJson");
        warningsJson = Objects.requireNonNull(warningsJson, "warningsJson");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public long sizeBytes() {
        return content.length;
    }
}
