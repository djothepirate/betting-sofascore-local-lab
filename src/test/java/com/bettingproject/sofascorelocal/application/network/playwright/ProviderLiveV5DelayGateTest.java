package com.bettingproject.sofascorelocal.application.network.playwright;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProviderLiveV5DelayGateTest {
    private static final UUID CAMPAIGN = UUID.randomUUID();

    @Test
    void oneSecondFenceWaitsTheResidualMillisecondAndRechecksAdmission() {
        var session = session(LiveProviderGroupTracker.Authority.LIVE_V5);
        var clock = new AtomicLong();
        List<Duration> pauses = new ArrayList<>();
        var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), clock::get,
                delay -> { pauses.add(delay); clock.addAndGet(delay.toNanos()); });
        gate.recordDispatchFinished(true, session);
        clock.set(Duration.ofMillis(999).toNanos());
        var checks = new AtomicLong();
        gate.awaitNextGroupDispatch(session, checks::incrementAndGet);
        assertThat(pauses).containsExactly(Duration.ofMillis(1));
        assertThat(clock).hasValue(Duration.ofSeconds(1).toNanos());
        assertThat(checks.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void authorityAndSessionTransitionsAlwaysRetainThreeSeconds() {
        var v4 = session(LiveProviderGroupTracker.Authority.LIVE_V4);
        var v5 = session(LiveProviderGroupTracker.Authority.LIVE_V5);
        // Same campaign UUID, different worker/session: no inherited short fence.
        var reopenedV5 = session(LiveProviderGroupTracker.Authority.LIVE_V5);
        var manual = session(LiveProviderGroupTracker.Authority.MANUAL_J5);
        LiveProviderGroupTracker[] sessions = {null, v4, v5, reopenedV5, manual};
        for (var previous : sessions) for (var next : sessions) {
            var clock = new AtomicLong();
            var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), clock::get,
                    delay -> clock.addAndGet(delay.toNanos()));
            gate.recordDispatchFinished(true, previous);
            gate.awaitNextGroupDispatch(next, () -> {});
            boolean sameV5 = next != null && next.isLiveV5() && previous == next;
            assertThat(clock.get()).as("previous=%s next=%s", previous, next)
                    .isEqualTo(Duration.ofSeconds(sameV5 ? 1 : 3).toNanos());
        }
    }

    @Test
    void noUsableResponseCannotAuthorizeTheOneSecondException() {
        var session = session(LiveProviderGroupTracker.Authority.LIVE_V5);
        var clock = new AtomicLong();
        var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), clock::get,
                delay -> clock.addAndGet(delay.toNanos()));
        gate.recordDispatchFinished(false, session);
        assertThatThrownBy(() -> gate.awaitNextGroupDispatch(session, () -> {}))
                .isInstanceOf(ProviderNetworkStartDelayGate.TimingEvidenceException.class);
        assertThat(clock).hasValue(0);
        assertThat(gate.timingEvidenceLost()).isTrue();
        assertThatThrownBy(() -> new ProviderNetworkStartDelayGate(Duration.ofSeconds(1), clock::get,
                delay -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancellationDuringTheShortFencePreventsAdmission() {
        var session = session(LiveProviderGroupTracker.Authority.LIVE_V5);
        var clock = new AtomicLong();
        var cancelled = new AtomicBoolean();
        var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), clock::get,
                delay -> { clock.addAndGet(delay.toNanos()); cancelled.set(true); });
        gate.recordDispatchFinished(true, session);
        assertThatThrownBy(() -> gate.awaitNextGroupDispatch(session, () -> {
            if (cancelled.get()) throw new PlaywrightDispatchCancelledException();
        })).isInstanceOf(PlaywrightDispatchCancelledException.class);
        assertThat(clock).hasValue(ProviderNetworkStartDelayGate.MAXIMUM_PAUSE_SLICE.toNanos());
    }

    @Test
    void interruptionDuringTheShortFenceFailsClosed() {
        var session = session(LiveProviderGroupTracker.Authority.LIVE_V5);
        var clock = new AtomicLong();
        var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), clock::get,
                delay -> { clock.addAndGet(delay.toNanos()); Thread.currentThread().interrupt(); });
        gate.recordDispatchFinished(true, session);
        try {
            assertThatThrownBy(() -> gate.awaitNextGroupDispatch(session, () -> {}))
                    .isInstanceOf(ProviderNetworkStartDelayGate.TimingEvidenceException.class);
        } finally { Thread.interrupted(); }
        assertThat(clock).hasValue(ProviderNetworkStartDelayGate.MAXIMUM_PAUSE_SLICE.toNanos());
        assertThat(gate.timingEvidenceLost()).isTrue();
    }

    private static LiveProviderGroupTracker session(LiveProviderGroupTracker.Authority authority) {
        return new LiveProviderGroupTracker(CAMPAIGN, authority);
    }
}
