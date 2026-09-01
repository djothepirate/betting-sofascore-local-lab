package com.bettingproject.sofascorelocal.application.network.playwright;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Holds the next worker dispatch behind a monotonic fence measured after the
 * previous dispatched exchange has finished in the parent process.
 *
 * <p>The fence is deliberately conservative: the previous provider start and
 * loopback arrival necessarily precede the point at which the parent finishes
 * reading the response. Waiting the full minimum delay after that point proves
 * the same lower bound between consecutive starts and arrivals without adding
 * a new IPC frame.</p>
 */
final class ProviderNetworkStartDelayGate {

    static final Duration MAXIMUM_PAUSE_SLICE = Duration.ofMillis(20);

    private final long minimumDelayNanos;
    private final LongSupplier nanoTime;
    private final Pause pause;
    private boolean fenced;
    private long previousDispatchFinishedAtNanos;
    private boolean timingEvidenceLost;

    ProviderNetworkStartDelayGate(
            Duration minimumDelay,
            LongSupplier nanoTime,
            Pause pause) {
        Objects.requireNonNull(minimumDelay, "minimumDelay");
        if (minimumDelay.compareTo(Duration.ofSeconds(3)) < 0) {
            throw new IllegalArgumentException("minimumDelay must be at least three seconds");
        }
        this.minimumDelayNanos = minimumDelay.toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.pause = Objects.requireNonNull(pause, "pause");
    }

    void awaitNextDispatch(Runnable continuationGuard) {
        Objects.requireNonNull(continuationGuard, "continuationGuard");
        while (true) {
            requireUninterrupted();
            continuationGuard.run();
            long observedBeforePause;
            long remaining;
            synchronized (this) {
                requireTimingEvidence();
                if (!fenced) {
                    return;
                }
                observedBeforePause = readNanoTime();
                long elapsed = observedBeforePause - previousDispatchFinishedAtNanos;
                if (elapsed < 0) {
                    timingEvidenceLost = true;
                    throw new TimingEvidenceException();
                }
                remaining = minimumDelayNanos - elapsed;
            }
            if (remaining <= 0) {
                requireUninterrupted();
                continuationGuard.run();
                requireUninterrupted();
                return;
            }
            try {
                pause.pause(Duration.ofNanos(Math.min(
                        remaining, MAXIMUM_PAUSE_SLICE.toNanos())));
            }
            catch (RuntimeException exception) {
                synchronized (this) {
                    timingEvidenceLost = true;
                }
                throw new TimingEvidenceException(exception);
            }
            requireUninterrupted();
            continuationGuard.run();
            synchronized (this) {
                requireTimingEvidence();
                long observedAfterPause = readNanoTime();
                if (observedAfterPause - observedBeforePause <= 0) {
                    timingEvidenceLost = true;
                    throw new TimingEvidenceException();
                }
            }
        }
    }

    synchronized void recordDispatchFinished(boolean usableResponseEvidence) {
        if (!usableResponseEvidence) {
            timingEvidenceLost = true;
        }
        long observed;
        try {
            observed = nanoTime.getAsLong();
        }
        catch (RuntimeException exception) {
            timingEvidenceLost = true;
            return;
        }
        if (fenced && observed - previousDispatchFinishedAtNanos < 0) {
            timingEvidenceLost = true;
            return;
        }
        previousDispatchFinishedAtNanos = observed;
        fenced = true;
    }

    synchronized boolean timingEvidenceLost() {
        return timingEvidenceLost;
    }

    private long readNanoTime() {
        try {
            return nanoTime.getAsLong();
        }
        catch (RuntimeException exception) {
            timingEvidenceLost = true;
            throw new TimingEvidenceException(exception);
        }
    }

    private void requireTimingEvidence() {
        if (timingEvidenceLost) {
            throw new TimingEvidenceException();
        }
    }

    private synchronized void requireUninterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            timingEvidenceLost = true;
            throw new TimingEvidenceException();
        }
    }

    @FunctionalInterface
    interface Pause {
        void pause(Duration duration);
    }

    static final class TimingEvidenceException extends RuntimeException {

        private TimingEvidenceException() {
            super("provider network start timing evidence is unavailable");
        }

        private TimingEvidenceException(RuntimeException cause) {
            super("provider network start timing evidence is unavailable", cause);
        }
    }
}
