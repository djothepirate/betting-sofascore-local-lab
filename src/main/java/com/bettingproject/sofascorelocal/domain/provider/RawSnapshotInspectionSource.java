package com.bettingproject.sofascorelocal.domain.provider;

import java.util.Objects;

/**
 * Exact local bytes paired with their persisted metadata for read-only inspection.
 */
public record RawSnapshotInspectionSource(
        RawSnapshotInspectionSummary summary,
        byte[] payloadRaw) {

    public RawSnapshotInspectionSource {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(payloadRaw, "payloadRaw");
        if (payloadRaw.length > RawPayloadEvidence.MAXIMUM_BYTES) {
            throw new IllegalArgumentException("payloadRaw exceeds the local raw limit");
        }
        payloadRaw = payloadRaw.clone();
    }

    @Override
    public byte[] payloadRaw() {
        return payloadRaw.clone();
    }
}
