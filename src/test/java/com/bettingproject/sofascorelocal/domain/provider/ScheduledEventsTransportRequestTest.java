package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledEventsTransportRequestTest {

    @Test
    void buildsOnlyTheFixedSimulatedRouteAndCanonicalDateKey() {
        ScheduledEventsTransportRequest request = new ScheduledEventsTransportRequest(
                URI.create("http://127.0.0.1:18087"),
                LocalDate.parse("2026-08-12"));

        assertThat(request.endpointType()).isEqualTo(SofascoreEndpointType.SCHEDULED_EVENTS);
        assertThat(request.requestKey()).isEqualTo("SCHEDULED_EVENTS|date=2026-08-12");
        assertThat(request.targetUri()).hasToString(
                "http://127.0.0.1:18087/simulated/scheduled-events?date=2026-08-12");
    }

    @Test
    void rejectsNonLoopbackOrDecoratedOrigins() {
        assertRejected("https://example.invalid:443");
        assertRejected("http://localhost:18087");
        assertRejected("http://127.0.0.1");
        assertRejected("http://user@127.0.0.1:18087");
        assertRejected("http://127.0.0.1:18087/path");
        assertRejected("http://127.0.0.1:18087?query=value");
        assertRejected("http://127.0.0.1:18087#fragment");
    }

    private static void assertRejected(String origin) {
        assertThatThrownBy(() -> new ScheduledEventsTransportRequest(
                URI.create(origin),
                LocalDate.parse("2026-08-12")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("127.0.0.1");
    }
}
