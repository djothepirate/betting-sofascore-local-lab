package com.bettingproject.sofascorelocal.application.network;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

class ManualProviderRequestCoordinatorTest {

    private static final UUID CAMPAIGN_ID = UUID.fromString(
            "c2925098-6f3b-4b19-8d0d-59cc5ef62a2a");

    @Test
    void singleEventManualJ5SkipsOnlyItsTwoInnerPausesAndPreservesEveryTransition() {
        MutableTicker ticker = new MutableTicker();
        List<Duration> pauses = new ArrayList<>();
        var coordinator = new ManualProviderRequestCoordinator(ticker::read, Duration.ofSeconds(3),
                delay -> { pauses.add(delay); ticker.advance(delay); });
        try (var j4 = coordinator.acquireCampaign(UUID.randomUUID())) {
            j4.beginRequest();
            j4.beginRequest();
        }
        try (var j5 = coordinator.acquireManualJ5Campaign(CAMPAIGN_ID, 17000001)) {
            for (var endpoint : List.of(SofascoreEndpointType.EVENT_STATISTICS,
                    SofascoreEndpointType.EVENT_INCIDENTS, SofascoreEndpointType.EVENT_LINEUPS)) {
                var request = manualRequest(endpoint, 17000001);
                j5.beginManualJ5Request(request);
                j5.checkManualJ5Request(request);
            }
            assertThat(pauses).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3));
        }
        try (var next = coordinator.acquireManualJ5Campaign(UUID.randomUUID(), 17000001)) {
            next.beginManualJ5Request(manualRequest(SofascoreEndpointType.EVENT_STATISTICS, 17000001));
        }
        try (var j4 = coordinator.acquire()) { }
        assertThat(pauses).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3),
                Duration.ofSeconds(3), Duration.ofSeconds(3));
    }

    @Test
    void manualJ5LeaseRejectsUnclaimedWrongEventSkippedRepeatedAndInterruptedCalls() {
        MutableTicker ticker = new MutableTicker();
        var coordinator = new ManualProviderRequestCoordinator(ticker::read, Duration.ofSeconds(3), ticker::advance);
        var statistics = manualRequest(SofascoreEndpointType.EVENT_STATISTICS, 17000001);
        try (var ordinary = coordinator.acquireCampaign(UUID.randomUUID())) {
            assertThatThrownBy(() -> ordinary.beginManualJ5Request(statistics))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
        }
        var j5 = coordinator.acquireManualJ5Campaign(CAMPAIGN_ID, 17000001);
        try (j5) {
            assertThatThrownBy(j5::beginRequest).isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            assertThatThrownBy(() -> j5.checkManualJ5Request(statistics))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            assertThatThrownBy(() -> j5.beginManualJ5Request(manualRequest(SofascoreEndpointType.EVENT_STATISTICS, 17000002)))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            j5.beginManualJ5Request(statistics);
            assertThatThrownBy(() -> j5.beginManualJ5Request(statistics))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            assertThatThrownBy(() -> j5.beginManualJ5Request(manualRequest(SofascoreEndpointType.EVENT_LINEUPS, 17000001)))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> j5.checkManualJ5Request(statistics))
                        .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            } finally { Thread.interrupted(); }
        }
        assertThatThrownBy(() -> j5.checkManualJ5Request(statistics))
                .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
    }

    private static J5EventDataProviderRequest manualRequest(SofascoreEndpointType endpoint, long event) {
        return new J5EventDataProviderRequest(java.net.URI.create("https://www.sofascore.com"), event, endpoint);
    }

    @Test
    void sharesTheMinimumDelayAcrossSuccessiveJ3J4AndJ5RequestLeases() {
        MutableTicker ticker = new MutableTicker();
        List<Duration> pauses = new ArrayList<>();
        var coordinator = new ManualProviderRequestCoordinator(
                ticker::read,
                Duration.ofSeconds(3),
                delay -> {
                    pauses.add(delay);
                    ticker.advance(delay);
                });

        try (var ignored = coordinator.acquire()) {
            assertThat(pauses).isEmpty();
        }
        try (var ignored = coordinator.acquire()) {
            assertThat(pauses).containsExactly(Duration.ofSeconds(3));
        }
        try (var ignored = coordinator.acquire()) {
            assertThat(pauses).containsExactly(
                    Duration.ofSeconds(3), Duration.ofSeconds(3));
        }
    }

    @Test
    void waitsForTheRemainingMillisecondAtTwoThousandNineHundredNinetyNineMilliseconds() {
        MutableTicker ticker = new MutableTicker();
        List<Duration> pauses = new ArrayList<>();
        var coordinator = new ManualProviderRequestCoordinator(
                ticker::read,
                Duration.ofSeconds(3),
                delay -> {
                    pauses.add(delay);
                    ticker.advance(delay);
                });

        try (var ignored = coordinator.acquire()) {
            // Establish the first monotonic start.
        }
        ticker.advance(Duration.ofMillis(2_999));
        try (var ignored = coordinator.acquire()) {
            // The exact missing millisecond must be observed, not assumed.
        }

        assertThat(pauses).containsExactly(Duration.ofMillis(1));
    }

    @Test
    void doesNotPauseAtTheExactThreeSecondBoundary() {
        MutableTicker ticker = new MutableTicker();
        List<Duration> pauses = new ArrayList<>();
        var coordinator = new ManualProviderRequestCoordinator(
                ticker::read, Duration.ofSeconds(3), pauses::add);

        try (var ignored = coordinator.acquire()) {
            // Establish the first monotonic start.
        }
        ticker.advance(Duration.ofSeconds(3));
        try (var ignored = coordinator.acquire()) {
            // The boundary itself is admissible.
        }

        assertThat(pauses).isEmpty();
    }

    @Test
    void rechecksMonotonicTimeAfterEveryEarlyWakeWithoutAdvancingItLogically() {
        MutableTicker ticker = new MutableTicker();
        List<Duration> pauses = new ArrayList<>();
        AtomicLong wake = new AtomicLong();
        var coordinator = new ManualProviderRequestCoordinator(
                ticker::read,
                Duration.ofSeconds(3),
                delay -> {
                    pauses.add(delay);
                    ticker.advance(wake.getAndIncrement() == 0
                            ? Duration.ofSeconds(1)
                            : delay);
                });

        try (var ignored = coordinator.acquire()) {
            // Establish the first monotonic start.
        }
        try (var ignored = coordinator.acquire()) {
            // The first pause wakes two seconds early and must be retried.
        }

        assertThat(pauses).containsExactly(
                Duration.ofSeconds(3), Duration.ofSeconds(2));
        assertThat(ticker.read()).isEqualTo(Duration.ofSeconds(3).toNanos());
    }

    @Test
    void keepsASecondCampaignOutsideTheProviderSectionUntilRelease() throws Exception {
        MutableTicker ticker = new MutableTicker();
        var coordinator = new ManualProviderRequestCoordinator(
                ticker::read,
                Duration.ofSeconds(3),
                ticker::advance);
        CountDownLatch attempting = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();

        try (var first = coordinator.acquire()) {
            executor.submit(() -> {
                attempting.countDown();
                try (var ignored = coordinator.acquire()) {
                    entered.countDown();
                }
            });
            assertThat(attempting.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(entered.await(100, TimeUnit.MILLISECONDS)).isFalse();
        }

        try {
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void holdsOneFairLeaseForTheCampaignAndAppliesTimingAtEachRequestStart() {
        MutableTicker ticker = new MutableTicker();
        List<Duration> pauses = new ArrayList<>();
        var coordinator = new ManualProviderRequestCoordinator(
                ticker::read,
                Duration.ofSeconds(3),
                delay -> {
                    pauses.add(delay);
                    ticker.advance(delay);
                });

        try (var campaign = coordinator.acquireCampaign(CAMPAIGN_ID)) {
            assertThat(campaign.campaignId()).isEqualTo(CAMPAIGN_ID);
            campaign.beginRequest();
            campaign.beginRequest();
            campaign.close();
        }
        try (var ignored = coordinator.acquire()) {
            // Historical one-request leases share the campaign timing state.
        }

        assertThat(pauses).containsExactly(
                Duration.ofSeconds(3),
                Duration.ofSeconds(3));
    }

    @Test
    void blocksAnotherThreadForTheWholeCampaignAndClosesIdempotently()
            throws Exception {
        MutableTicker ticker = new MutableTicker();
        var coordinator = new ManualProviderRequestCoordinator(
                ticker::read,
                Duration.ofSeconds(3),
                ticker::advance);
        CountDownLatch attempting = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var campaign = coordinator.acquireCampaign(CAMPAIGN_ID);

        try {
            executor.submit(() -> {
                attempting.countDown();
                try (var ignored = coordinator.acquireCampaign(UUID.randomUUID())) {
                    entered.countDown();
                }
            });
            assertThat(attempting.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(entered.await(100, TimeUnit.MILLISECONDS)).isFalse();

            campaign.close();
            campaign.close();
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            campaign.close();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void interruptedCampaignAcquisitionFailsClosedAndRestoresInterruptStatus()
            throws Exception {
        MutableTicker ticker = new MutableTicker();
        var coordinator = new ManualProviderRequestCoordinator(
                ticker::read,
                Duration.ofSeconds(3),
                ticker::advance);
        var owner = coordinator.acquireCampaign(CAMPAIGN_ID);
        CountDownLatch attempting = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread contender = Thread.ofPlatform().start(() -> {
            attempting.countDown();
            try (var ignored = coordinator.acquireCampaign(UUID.randomUUID())) {
                failure.set(new AssertionError("interrupted acquisition must not succeed"));
            }
            catch (Throwable exception) {
                failure.set(exception);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });

        try {
            assertThat(attempting.await(1, TimeUnit.SECONDS)).isTrue();
            contender.interrupt();
            contender.join(1_000);

            assertThat(contender.isAlive()).isFalse();
            assertThat(failure.get())
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            assertThat(interrupted).isTrue();
        }
        finally {
            owner.close();
        }

        try (var reusable = coordinator.acquireCampaign(UUID.randomUUID())) {
            assertThat(reusable).isNotNull();
        }
    }

    private static final class MutableTicker {

        private final AtomicLong nanos = new AtomicLong();

        private long read() {
            return nanos.get();
        }

        private void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
