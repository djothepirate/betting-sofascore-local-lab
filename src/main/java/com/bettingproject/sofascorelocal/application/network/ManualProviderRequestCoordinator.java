package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * Serializes every explicitly confirmed J3, J4 and J5 provider transport and
 * applies one shared delay between request starts. The lease covers the HTTP
 * exchange so separate browser sessions cannot create concurrent provider calls.
 */
@Component
public final class ManualProviderRequestCoordinator {

    private final ReentrantLock requestLock = new ReentrantLock(true);
    private final LongSupplier nanoTime;
    private final long minimumDelayNanos;
    private final Pause pause;
    private boolean started;
    private long lastStartedAtNanos;

    @Autowired
    public ManualProviderRequestCoordinator(SofascoreProperties properties) {
        this(
                System::nanoTime,
                Objects.requireNonNull(properties, "properties").getMinimumDelay(),
                ManualProviderRequestCoordinator::sleepSafely);
    }

    ManualProviderRequestCoordinator(Clock clock, Duration minimumDelay, Pause pause) {
        this(() -> epochNanos(Objects.requireNonNull(clock, "clock").instant()),
                minimumDelay,
                pause);
    }

    ManualProviderRequestCoordinator(
            LongSupplier nanoTime,
            Duration minimumDelay,
            Pause pause) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.minimumDelayNanos = requireAtLeastThreeSeconds(minimumDelay).toNanos();
        this.pause = Objects.requireNonNull(pause, "pause");
    }

    public Lease acquire() {
        CampaignLease campaign = acquireCampaign(UUID.randomUUID());
        try {
            campaign.beginRequest();
            return new Lease(campaign);
        }
        catch (RuntimeException exception) {
            campaign.close();
            throw exception;
        }
    }

    public CampaignLease acquireCampaign(UUID campaignId) {
        Objects.requireNonNull(campaignId, "campaignId");
        if (requestLock.isHeldByCurrentThread()) {
            throw new CoordinationException("nested provider campaign acquisition is forbidden");
        }
        try {
            requestLock.lockInterruptibly();
            return new CampaignLease(this, campaignId, Thread.currentThread());
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CoordinationException(exception);
        }
    }

    private void beginRequest() {
        try {
            long now = nanoTime.getAsLong();
            if (started) {
                long elapsed = now - lastStartedAtNanos;
                while (elapsed < minimumDelayNanos) {
                    pause.pause(Duration.ofNanos(minimumDelayNanos - elapsed));
                    now = nanoTime.getAsLong();
                    elapsed = now - lastStartedAtNanos;
                }
            }
            lastStartedAtNanos = now;
            started = true;
        }
        catch (RuntimeException exception) {
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
            TimeUnit.NANOSECONDS.sleep(delay.toNanos());
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CoordinationException(exception);
        }
    }

    private static long epochNanos(java.time.Instant instant) {
        return Math.addExact(
                Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L),
                instant.getNano());
    }

    @FunctionalInterface
    interface Pause {
        void pause(Duration delay);
    }

    public static final class Lease implements AutoCloseable {

        private CampaignLease campaign;

        private Lease(CampaignLease campaign) {
            this.campaign = campaign;
        }

        @Override
        public void close() {
            CampaignLease current = campaign;
            if (current != null) {
                current.close();
                campaign = null;
            }
        }
    }

    public static final class CampaignLease implements AutoCloseable {

        private ManualProviderRequestCoordinator owner;
        private final UUID campaignId;
        private final Thread ownerThread;

        private CampaignLease(
                ManualProviderRequestCoordinator owner,
                UUID campaignId,
                Thread ownerThread) {
            this.owner = owner;
            this.campaignId = campaignId;
            this.ownerThread = ownerThread;
        }

        public UUID campaignId() {
            return campaignId;
        }

        public void beginRequest() {
            ManualProviderRequestCoordinator current = requireOpenOnOwnerThread();
            current.beginRequest();
        }

        @Override
        public void close() {
            ManualProviderRequestCoordinator current = owner;
            if (current == null) {
                return;
            }
            requireOwnerThread();
            owner = null;
            current.release();
        }

        private ManualProviderRequestCoordinator requireOpenOnOwnerThread() {
            requireOwnerThread();
            ManualProviderRequestCoordinator current = owner;
            if (current == null) {
                throw new CoordinationException("provider campaign lease is closed");
            }
            return current;
        }

        private void requireOwnerThread() {
            if (Thread.currentThread() != ownerThread) {
                throw new CoordinationException(
                        "provider campaign lease belongs to another thread");
            }
        }
    }

    public static final class CoordinationException extends RuntimeException {

        private CoordinationException(Throwable cause) {
            super("provider request coordination interrupted", cause);
        }

        private CoordinationException(String message) {
            super(message);
        }
    }
}
