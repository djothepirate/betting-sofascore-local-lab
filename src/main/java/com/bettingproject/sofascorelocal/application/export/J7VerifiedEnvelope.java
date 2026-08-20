package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;
import java.util.UUID;

public record J7VerifiedEnvelope(
        UUID exportId,
        UUID canonicalEventId,
        J7ExportStatus status,
        String dataSha256,
        String sourceSetSha256,
        String contentSha256,
        long contentSizeBytes,
        ObjectNode envelope) {

    public J7VerifiedEnvelope {
        exportId = Objects.requireNonNull(exportId, "exportId");
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        status = Objects.requireNonNull(status, "status");
        dataSha256 = Objects.requireNonNull(dataSha256, "dataSha256");
        sourceSetSha256 = Objects.requireNonNull(sourceSetSha256, "sourceSetSha256");
        contentSha256 = Objects.requireNonNull(contentSha256, "contentSha256");
        if (contentSizeBytes < 1 || contentSizeBytes > J7ExportContract.MAXIMUM_BYTES) {
            throw new IllegalArgumentException("contentSizeBytes is outside the J7 limit");
        }
        envelope = Objects.requireNonNull(envelope, "envelope").deepCopy();
    }

    @Override
    public ObjectNode envelope() {
        return envelope.deepCopy();
    }
}
