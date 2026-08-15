package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.Objects;
import java.util.regex.Pattern;

/** Minimized result metadata; raw provider content is deliberately absent. */
public record J5RealEndpointResult(
        SofascoreEndpointType endpointType,
        long snapshotId,
        String payloadSha256,
        int payloadSizeBytes,
        long observationId,
        boolean observationInserted,
        J5CompletenessStatus completenessStatus,
        int completenessScore,
        int warningCount) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J5RealEndpointResult {
        endpointType = Objects.requireNonNull(endpointType, "endpointType");
        if (!J5EventDataProviderRequest.ALLOWED_ENDPOINTS.contains(endpointType)) {
            throw new IllegalArgumentException("endpoint result is outside J5 scope");
        }
        if (snapshotId < 1 || observationId < 1) {
            throw new IllegalArgumentException("persisted identifiers must be positive");
        }
        payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256");
        if (!SHA_256.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be lower-case SHA-256");
        }
        if (payloadSizeBytes < 0 || payloadSizeBytes > RawPayloadEvidence.MAXIMUM_BYTES) {
            throw new IllegalArgumentException("payload size is outside persistence bounds");
        }
        completenessStatus = Objects.requireNonNull(
                completenessStatus, "completenessStatus");
        if (completenessScore < 0 || completenessScore > 100 || warningCount < 0) {
            throw new IllegalArgumentException("result counters are outside bounds");
        }
    }
}
