package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes every explicitly confirmed J3, J4 and J5 provider transport and
 * applies one shared delay between request starts. The lease covers the HTTP
 * exchange so separate browser sessions cannot create concurrent provider calls.
 */
@Component
public final class ManualProviderRequestCoordinator {

    private final ReentrantLock requestLock = new ReentrantLock(true);
    private final Clock clock;
    private final Duration minimumDelay;
    private final Pause pause;
    private Instant lastStartedAt;

    @Autowired
    public ManualProviderRequestCoordinator(SofascoreProperties properties) {
        this(
                Clock.systemUTC(),
                Objects.requireNonNull(properties, "properties").getMinimumDelay(),
                ManualProviderRequestCoordinator::sleepSafely);
    }

    ManualProviderRequestCoordinator(Clock clock, Duration minimumDelay, Pause pause) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.minimumDelay = requireAtLeastThreeSeconds(minimumDelay);
        this.pause = Objects.requireNonNull(pause, "pause");
    }

    public Lease acquire() {
        boolean acquired = false;
        try {
            requestLock.lockInterruptibly();
            acquired = true;
            Instant now = clock.instant();
            if (lastStartedAt != null) {
                Instant earliest = lastStartedAt.plus(minimumDelay);
                if (now.isBefore(earliest)) {
                    pause.pause(Duration.between(now, earliest));
                    now = clock.instant();
                    if (now.isBefore(earliest)) {
                        now = earliest;
                    }
                }
            }
            lastStartedAt = now;
            return new Lease(this);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (acquired) {
                requestLock.unlock();
            }
            throw new CoordinationException(exception);
        }
        catch (RuntimeException exception) {
            if (acquired) {
                requestLock.unlock();
            }
            throw exception instanceof CoordinationException
                    ? exception
                    : new CoordinationException(exception);
        }
    }

    private void release() {
        requestLock.unlock();
    }

    private static Duration requireAtLeastThreeSeconds(Duration value) {
        Objects.requireNonNull(value, "minimumDelay");
        if (value.compareTo(Duration.ofSeconds(3)) < 0) {
            throw new IllegalArgumentException("minimumDelay must be at least three seconds");
        }
        return value;
    }

    private static void sleepSafely(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CoordinationException(exception);
        }
    }

    @FunctionalInterface
    interface Pause {
        void pause(Duration delay);
    }

    public static final class Lease implements AutoCloseable {

        private ManualProviderRequestCoordinator owner;

        private Lease(ManualProviderRequestCoordinator owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            ManualProviderRequestCoordinator current = owner;
            if (current != null) {
                owner = null;
                current.release();
            }
        }
    }

    public static final class CoordinationException extends RuntimeException {

        private CoordinationException(Throwable cause) {
            super("provider request coordination interrupted", cause);
        }
    }
}
