package com.bettingproject.sofascorelocal.domain.event;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record J4ScheduledEventsSnapshotSource(
        long snapshotId,
        String requestKey,
        Instant requestedAt,
        Instant receivedAt,
        int httpStatus,
        String contentType,
        Duration latency,
        byte[] payloadRaw,
        String payloadSha256,
        String historicalParserVersion,
        RawSnapshotSchemaStatus historicalSchemaStatus) {

    public J4ScheduledEventsSnapshotSource {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        requestKey = Objects.requireNonNull(requestKey, "requestKey");
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = Objects.requireNonNull(contentType, "contentType");
        latency = Objects.requireNonNull(latency, "latency");
        payloadRaw = Arrays.copyOf(
                Objects.requireNonNull(payloadRaw, "payloadRaw"),
                payloadRaw.length);
        payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256");
        historicalParserVersion = Objects.requireNonNull(
                historicalParserVersion,
                "historicalParserVersion");
        historicalSchemaStatus = Objects.requireNonNull(
                historicalSchemaStatus,
                "historicalSchemaStatus");
    }

    @Override
    public byte[] payloadRaw() {
        return Arrays.copyOf(payloadRaw, payloadRaw.length);
    }

    public ScheduledEventsTransportResponse asTransportResponse() {
        return new ScheduledEventsTransportResponse(
                requestKey,
                requestedAt,
                receivedAt,
                httpStatus,
                contentType,
                latency,
                RawPayloadEvidence.capture(payloadRaw));
    }
}
