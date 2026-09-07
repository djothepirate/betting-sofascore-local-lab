package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import com.bettingproject.sofascorelocal.port.ProviderCampaignGuardStore;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
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
    private ProviderCampaignGuardStore durableGuard;
    private PlaywrightProviderSupervisor supervisor;
    private final LiveCampaignData.Owner instanceOwner = new LiveCampaignData.Owner(UUID.randomUUID(),
            ProcessHandle.current().pid(), ProcessHandle.current().info().startInstant().orElseThrow());
    private volatile boolean liveCampaign;

    public ManualProviderRequestCoordinator(SofascoreProperties properties) {
        this(
                System::nanoTime,
                Objects.requireNonNull(properties, "properties").getMinimumDelay(),
                ManualProviderRequestCoordinator::sleepSafely);
    }

    @Autowired
    public ManualProviderRequestCoordinator(SofascoreProperties properties,
            ObjectProvider<ProviderCampaignGuardStore> guard,
            ObjectProvider<PlaywrightProviderSupervisor> supervisor) {
        this(properties);
        this.durableGuard = guard.getIfAvailable();
        this.supervisor = supervisor.getIfAvailable();
    }

    public LiveCampaignData.Owner instanceOwner() { return instanceOwner; }

    public CampaignLease acquireLiveCampaign(UUID campaignId) { return acquireCampaign(campaignId, true); }

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
        return acquireCampaign(campaignId, false);
    }

    private CampaignLease acquireCampaign(UUID campaignId, boolean live) {
        Objects.requireNonNull(campaignId, "campaignId");
        if (liveCampaign) throw new CoordinationException("live provider campaign is active");
        if (requestLock.isHeldByCurrentThread()) {
            throw new CoordinationException("nested provider campaign acquisition is forbidden");
        }
        try {
            if (live) {
                if (!requestLock.tryLock()) throw new CoordinationException("provider campaign is active");
            } else requestLock.lockInterruptibly();
            try {
                LiveCampaignData.Ownership ownership = durableGuard == null ? null
                        : durableGuard.tryAcquire(campaignId, instanceOwner, java.time.Instant.now())
                            .orElseThrow(() -> new CoordinationException("durable provider guard is occupied")).ownership();
                liveCampaign = live;
                return new CampaignLease(this, campaignId, Thread.currentThread(), ownership);
            } catch (RuntimeException failure) { requestLock.unlock(); throw failure; }
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

    private void release(LiveCampaignData.Ownership ownership) {
        if (ownership != null) {
            if (supervisor != null && supervisor.activeCampaignId().filter(ownership.campaignId()::equals).isPresent()) {
                durableGuard.requireCleanup(ownership, java.time.Instant.now());
                throw new CoordinationException("provider cleanup is not verified");
            }
            durableGuard.releaseAfterVerifiedCleanup(ownership, java.time.Instant.now());
        }
        liveCampaign = false;
        requestLock.unlock();
    }

    private void releaseAfterFailedClose(LiveCampaignData.Ownership ownership) {
        if (ownership == null || durableGuard == null) {
            release(ownership);
            return;
        }
        LiveCampaignData.Guard current = durableGuard.snapshot();
        if (current == null || current.generation() != ownership.generation())
            throw new CoordinationException("provider cleanup generation cannot be verified");
        if ("FREE".equals(current.state())) {
            // A previous SQL release may have committed before its response was lost. Only the
            // unchanged generation, cleared ownership and absent supervisor prove this close.
            if (current.campaignId() != null || current.owner() != null || supervisor == null
                    || supervisor.activeCampaignId().isPresent())
                throw new CoordinationException("provider cleanup cannot be verified");
            liveCampaign = false;
            requestLock.unlock();
            return;
        }
        if (!ownership.campaignId().equals(current.campaignId()) || current.owner() == null
                || !ownership.instanceId().equals(current.owner().instanceId()))
            throw new CoordinationException("provider cleanup ownership changed");
        release(ownership);
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
        private final LiveCampaignData.Ownership ownership;
        private boolean closeAttempted;

        private CampaignLease(
                ManualProviderRequestCoordinator owner,
                UUID campaignId,
                Thread ownerThread, LiveCampaignData.Ownership ownership) {
            this.owner = owner;
            this.campaignId = campaignId;
            this.ownerThread = ownerThread;
            this.ownership = ownership;
        }

        public LiveCampaignData.Ownership ownership() {
            if (ownership == null) throw new IllegalStateException("durable provider guard required for live");
            return ownership;
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
            closeAttempted = true;
            current.release(ownership);
            owner = null;
        }

        /** Explicit local recovery only; never transfers ownership or starts a provider request. */
        public void retryCloseAfterVerifiedCleanup() {
            requireOwnerThread();
            ManualProviderRequestCoordinator current = owner;
            if (current == null) return;
            if (!closeAttempted) throw new CoordinationException("provider close has not been attempted");
            current.releaseAfterFailedClose(ownership);
            owner = null;
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
