package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
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
