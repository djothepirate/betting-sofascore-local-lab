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
    void v8SameSessionUsesTheQualifiedHalfSecondFence() {
        var session = session(LiveProviderGroupTracker.Authority.LIVE_V8);
        var clock = new AtomicLong();
        List<Duration> pauses = new ArrayList<>();
        var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), clock::get,
                delay -> { pauses.add(delay); clock.addAndGet(delay.toNanos()); });
        gate.recordDispatchFinished(true, session);
        clock.set(Duration.ofMillis(499).toNanos());
        gate.awaitNextGroupDispatch(session, () -> { });
        assertThat(pauses).containsExactly(Duration.ofMillis(1));
        assertThat(clock).hasValue(Duration.ofMillis(500).toNanos());
    }

    @Test
    void authorityAndSessionTransitionsRetainTheGlobalFenceUnlessTheSameQualifiedSessionContinues() {
        var v4 = session(LiveProviderGroupTracker.Authority.LIVE_V4);
        var v5 = session(LiveProviderGroupTracker.Authority.LIVE_V5);
        var v6 = session(LiveProviderGroupTracker.Authority.LIVE_V6);
        // Same campaign UUID, different worker/session: no inherited short fence.
        var reopenedV5 = session(LiveProviderGroupTracker.Authority.LIVE_V5);
        var manual = session(LiveProviderGroupTracker.Authority.MANUAL_J5);
        var reopenedV6 = session(LiveProviderGroupTracker.Authority.LIVE_V6);
        var v7 = session(LiveProviderGroupTracker.Authority.LIVE_V7);
        var reopenedV7 = session(LiveProviderGroupTracker.Authority.LIVE_V7);
        var v8 = session(LiveProviderGroupTracker.Authority.LIVE_V8);
        var reopenedV8 = session(LiveProviderGroupTracker.Authority.LIVE_V8);
        LiveProviderGroupTracker[] sessions = {null, v4, v5, reopenedV5, v6, reopenedV6, v7, reopenedV7, v8, reopenedV8, manual};
        for (var previous : sessions) for (var next : sessions) {
            var clock = new AtomicLong();
            var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), clock::get,
                    delay -> clock.addAndGet(delay.toNanos()));
            gate.recordDispatchFinished(true, previous);
            gate.awaitNextGroupDispatch(next, () -> {});
            Duration expected = next != null && next.interGroupMinimumDelay() != null && previous == next
                    ? next.interGroupMinimumDelay() : Duration.ofSeconds(3);
            assertThat(clock.get()).as("previous=%s next=%s", previous, next)
                    .isEqualTo(expected.toNanos());
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
    void provenV6TimeoutEndRetainsThreeSecondsAndCannotRestoreLostTimingEvidence() {
        var v6 = session(LiveProviderGroupTracker.Authority.LIVE_V6);
        var clock = new AtomicLong();
        var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), clock::get,
                delay -> clock.addAndGet(delay.toNanos()));
        gate.recordRecoverableTimeoutFinished(v6);
        gate.awaitNextGroupDispatch(v6, () -> {});
        assertThat(clock).hasValue(Duration.ofSeconds(3).toNanos());
        assertThat(gate.timingEvidenceLost()).isFalse();
        gate.recordDispatchFinished(false, v6);
        gate.recordRecoverableTimeoutFinished(v6);
        assertThatThrownBy(() -> gate.awaitNextGroupDispatch(v6, () -> {}))
                .isInstanceOf(ProviderNetworkStartDelayGate.TimingEvidenceException.class);
    }

    @Test
    void timeoutExceptionCannotRelaxHistoricalAuthoritiesOrARegressingClock() {
        for (var authority : List.of(LiveProviderGroupTracker.Authority.LIVE_V4,
                LiveProviderGroupTracker.Authority.LIVE_V5, LiveProviderGroupTracker.Authority.MANUAL_J5)) {
            var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), () -> 0L, delay -> {});
            assertThatThrownBy(() -> gate.recordRecoverableTimeoutFinished(session(authority)))
                    .isInstanceOf(ProviderNetworkStartDelayGate.TimingEvidenceException.class);
            assertThat(gate.timingEvidenceLost()).isTrue();
        }
        var v6 = session(LiveProviderGroupTracker.Authority.LIVE_V6);
        var clock = new AtomicLong(1);
        var gate = new ProviderNetworkStartDelayGate(Duration.ofSeconds(3), clock::get, delay -> {});
        gate.recordDispatchFinished(true, v6);
        clock.set(0);
        gate.recordRecoverableTimeoutFinished(v6);
        assertThatThrownBy(() -> gate.awaitNextGroupDispatch(v6, () -> {}))
                .isInstanceOf(ProviderNetworkStartDelayGate.TimingEvidenceException.class);
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
