package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record J4RealEventDetailsEventResult(
        long eventId,
        J4RealEventDetailsResolutionSource resolutionSource,
        long snapshotId,
        UUID canonicalEventId,
        String payloadSha256,
        int payloadSizeBytes,
        RawSnapshotSchemaStatus schemaStatus,
        EventDetails details,
        int warningCount) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J4RealEventDetailsEventResult {
        if (!EventDetailsProviderRequest.PHASE_1_EVENT_IDS.contains(eventId)) {
            throw new IllegalArgumentException("event result is outside J4 phase 1");
        }
        Objects.requireNonNull(resolutionSource, "resolutionSource");
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        Objects.requireNonNull(payloadSha256, "payloadSha256");
        if (!SHA_256.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be lower-case SHA-256");
        }
        if (payloadSizeBytes < 0) {
            throw new IllegalArgumentException("payloadSizeBytes cannot be negative");
        }
        Objects.requireNonNull(schemaStatus, "schemaStatus");
        if (schemaStatus != RawSnapshotSchemaStatus.PARSED) {
            throw new IllegalArgumentException("a displayed event result must be parsed");
        }
        Objects.requireNonNull(details, "details");
        if (details.providerEventId() != eventId || warningCount < 0) {
            throw new IllegalArgumentException("event result metadata is inconsistent");
        }
    }

}
