package com.bettingproject.sofascorelocal.application.network;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ManualProviderRequestCoordinatorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-18T11:00:00Z"), ZoneOffset.UTC);
    private static final UUID CAMPAIGN_ID = UUID.fromString(
            "c2925098-6f3b-4b19-8d0d-59cc5ef62a2a");

    @Test
    void sharesTheMinimumDelayAcrossSuccessiveJ3J4AndJ5RequestLeases() {
        List<Duration> pauses = new ArrayList<>();
        var coordinator = new ManualProviderRequestCoordinator(
                CLOCK, Duration.ofSeconds(3), pauses::add);

        try (var ignored = coordinator.acquire()) {
            assertThat(pauses).isEmpty();
        }
        try (var ignored = coordinator.acquire()) {
            assertThat(pauses).containsExactly(Duration.ofSeconds(3));
        }
        try (var ignored = coordinator.acquire()) {
            assertThat(pauses).containsExactly(
                    Duration.ofSeconds(3), Duration.ofSeconds(6));
        }
    }

    @Test
    void keepsASecondCampaignOutsideTheProviderSectionUntilRelease() throws Exception {
        var coordinator = new ManualProviderRequestCoordinator(
                CLOCK, Duration.ofSeconds(3), ignored -> { });
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
        List<Duration> pauses = new ArrayList<>();
        var coordinator = new ManualProviderRequestCoordinator(
                CLOCK, Duration.ofSeconds(3), pauses::add);

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
                Duration.ofSeconds(6));
    }

    @Test
    void blocksAnotherThreadForTheWholeCampaignAndClosesIdempotently()
            throws Exception {
        var coordinator = new ManualProviderRequestCoordinator(
                CLOCK, Duration.ofSeconds(3), ignored -> { });
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
        var coordinator = new ManualProviderRequestCoordinator(
                CLOCK, Duration.ofSeconds(3), ignored -> { });
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
}
