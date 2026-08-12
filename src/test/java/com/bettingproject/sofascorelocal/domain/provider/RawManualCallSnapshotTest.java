package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawManualCallSnapshotTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void acceptsBoundedCanonicalScheduledEventsMetadata() {
        RawManualCallSnapshot snapshot = snapshot(
                "SCHEDULED_EVENTS|date=2026-08-12",
                REQUESTED_AT.plusMillis(250),
                Duration.ofMillis(250));

        assertThat(snapshot.endpointType()).isEqualTo(SofascoreEndpointType.SCHEDULED_EVENTS);
        assertThat(snapshot.requestKey()).isEqualTo("SCHEDULED_EVENTS|date=2026-08-12");
        assertThat(snapshot.latencyMillis()).isEqualTo(250);
        assertThat(snapshot.schemaStatus()).isEqualTo(RawSnapshotSchemaStatus.PARSED);
    }

    @Test
    void rejectsUrisMismatchedEndpointsAndControlCharactersInMetadata() {
        assertThatThrownBy(() -> snapshot(
                "https://example.invalid/path",
                REQUESTED_AT.plusMillis(250),
                Duration.ofMillis(250)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical key");

        assertThatThrownBy(() -> snapshot(
                "EVENT_DETAILS|eventId=123",
                REQUESTED_AT.plusMillis(250),
                Duration.ofMillis(250)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("logical endpoint");

        assertThatThrownBy(() -> new RawManualCallSnapshot(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                "SCHEDULED_EVENTS|date=2026-08-12",
                REQUESTED_AT,
                REQUESTED_AT.plusMillis(250),
                200,
                "application/json\r\nX-Unsafe: value",
                Duration.ofMillis(250),
                payload(),
                "scheduled-events-v1",
                RawSnapshotSchemaStatus.PARSED,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");
    }

    @Test
    void rejectsInvalidStatusChronologyLatencyAndErrorCode() {
        assertThatThrownBy(() -> new RawManualCallSnapshot(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                "SCHEDULED_EVENTS|date=2026-08-12",
                REQUESTED_AT,
                REQUESTED_AT.plusMillis(250),
                99,
                "application/json",
                Duration.ofMillis(250),
                payload(),
                "scheduled-events-v1",
                RawSnapshotSchemaStatus.PARSED,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("httpStatus");

        assertThatThrownBy(() -> snapshot(
                "SCHEDULED_EVENTS|date=2026-08-12",
                REQUESTED_AT.minusMillis(1),
                Duration.ofMillis(250)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receivedAt");

        assertThatThrownBy(() -> snapshot(
                "SCHEDULED_EVENTS|date=2026-08-12",
                REQUESTED_AT.plusMillis(250),
                Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latency");

        assertThatThrownBy(() -> new RawManualCallSnapshot(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                "SCHEDULED_EVENTS|date=2026-08-12",
                REQUESTED_AT,
                REQUESTED_AT.plusMillis(250),
                403,
                "application/json",
                Duration.ofMillis(250),
                payload(),
                "scheduled-events-v1",
                RawSnapshotSchemaStatus.TRANSPORT_ERROR,
                "unsafe error"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe characters");
    }

    private static RawManualCallSnapshot snapshot(
            String requestKey,
            Instant receivedAt,
            Duration latency) {
        return new RawManualCallSnapshot(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                requestKey,
                REQUESTED_AT,
                receivedAt,
                200,
                "application/json; charset=utf-8",
                latency,
                payload(),
                "scheduled-events-v1",
                RawSnapshotSchemaStatus.PARSED,
                null);
    }

    private static RawPayloadEvidence payload() {
        return RawPayloadEvidence.capture(
                "{\"events\":[],\"hasNextPage\":false}"
                        .getBytes(StandardCharsets.UTF_8));
    }
}
