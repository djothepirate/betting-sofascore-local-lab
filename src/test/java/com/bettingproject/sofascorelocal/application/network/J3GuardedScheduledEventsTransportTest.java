package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3NetworkBlockReason;
import com.bettingproject.sofascorelocal.domain.provider.J3NetworkDecision;
import com.bettingproject.sofascorelocal.domain.provider.J3ScheduledEventsCallAuthorization;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.ScheduledEventsTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class J3GuardedScheduledEventsTransportTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private static final Duration MINIMUM_DELAY = Duration.ofSeconds(3);

    @Test
    void neverInvokesTransportWhenPolicyUsesCacheOrBlocks() {
        CountingTransport delegate = new CountingTransport();
        J3GuardedScheduledEventsTransport guarded = guarded(delegate);

        var cached = guarded.execute(authorization(false, true), request());
        var unconfirmed = guarded.execute(authorization(false, false), request());

        assertThat(cached.policy().decision()).isEqualTo(J3NetworkDecision.USE_CACHE);
        assertThat(cached.response()).isEmpty();
        assertThat(unconfirmed.policy().decision()).isEqualTo(J3NetworkDecision.BLOCKED);
        assertThat(unconfirmed.policy().reason())
                .isEqualTo(J3NetworkBlockReason.MANUAL_CONFIRMATION_REQUIRED);
        assertThat(unconfirmed.response()).isEmpty();
        assertThat(delegate.calls()).isZero();
    }

    @Test
    void invokesTransportOnceAfterEveryGuardAndAppliesTheMinimumDelay() {
        CountingTransport delegate = new CountingTransport();
        J3GuardedScheduledEventsTransport guarded = guarded(delegate);

        var first = guarded.execute(authorization(true, false), request());
        var immediateSecond = guarded.execute(authorization(true, false), request());

        assertThat(first.policy().decision()).isEqualTo(J3NetworkDecision.TRANSPORT_ELIGIBLE);
        assertThat(first.response()).isPresent();
        assertThat(guarded.lastTransportStartedAt()).isEqualTo(NOW);
        assertThat(immediateSecond.policy().decision()).isEqualTo(J3NetworkDecision.BLOCKED);
        assertThat(immediateSecond.policy().reason())
                .isEqualTo(J3NetworkBlockReason.MINIMUM_DELAY_NOT_ELAPSED);
        assertThat(immediateSecond.policy().nextEligibleAt()).isEqualTo(NOW.plus(MINIMUM_DELAY));
        assertThat(delegate.calls()).isEqualTo(1);
    }

    private static J3GuardedScheduledEventsTransport guarded(ScheduledEventsTransport delegate) {
        return new J3GuardedScheduledEventsTransport(
                new J3ManualCallPolicy(),
                new J3SingleCallGuard(),
                delegate,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static J3ScheduledEventsCallAuthorization authorization(
            boolean confirmed,
            boolean cacheHit) {
        return new J3ScheduledEventsCallAuthorization(
                true,
                false,
                true,
                true,
                true,
                confirmed,
                cacheHit,
                new J3CircuitSnapshot(
                        J3CircuitState.CLOSED,
                        J3CircuitReason.NONE,
                        NOW.minusSeconds(30),
                        null),
                MINIMUM_DELAY);
    }

    private static ScheduledEventsTransportRequest request() {
        return new ScheduledEventsTransportRequest(
                URI.create("http://127.0.0.1:18087"),
                LocalDate.parse("2026-08-12"));
    }

    private static final class CountingTransport implements ScheduledEventsTransport {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ScheduledEventsTransportResponse execute(ScheduledEventsTransportRequest request) {
            calls.incrementAndGet();
            byte[] payload = "{\"events\":[],\"hasNextPage\":false}"
                    .getBytes(StandardCharsets.UTF_8);
            return new ScheduledEventsTransportResponse(
                    request.requestKey(),
                    NOW,
                    NOW.plusMillis(20),
                    200,
                    MediaTypes.JSON,
                    Duration.ofMillis(20),
                    RawPayloadEvidence.capture(payload));
        }

        int calls() {
            return calls.get();
        }
    }

    private static final class MediaTypes {

        private static final String JSON = "application/json";

        private MediaTypes() {
        }
    }
}
