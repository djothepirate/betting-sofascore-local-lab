package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;

import java.util.Objects;
import java.util.regex.Pattern;

/** Minimized evidence for an exact provider 404; no event identity is fabricated. */
public record J4RealEventDetailsUnavailableResult(
        long eventId,
        long snapshotId,
        int httpStatus,
        String payloadSha256,
        int payloadSizeBytes,
        RawSnapshotSchemaStatus schemaStatus) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J4RealEventDetailsUnavailableResult {
        if (eventId < 1 || eventId > EventDetailsProviderRequest.MAXIMUM_PARAMETERIZED_EVENT_ID) {
            throw new IllegalArgumentException("unavailable event result is outside J4 bounds");
        }
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        if (httpStatus != 404) {
            throw new IllegalArgumentException("unavailable event result must preserve HTTP 404");
        }
        Objects.requireNonNull(payloadSha256, "payloadSha256");
        if (!SHA_256.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be lower-case SHA-256");
        }
        if (payloadSizeBytes < 0) {
            throw new IllegalArgumentException("payloadSizeBytes cannot be negative");
        }
        if (schemaStatus != RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE) {
            throw new IllegalArgumentException("a 404 result must be endpoint-unavailable");
        }
    }
}
