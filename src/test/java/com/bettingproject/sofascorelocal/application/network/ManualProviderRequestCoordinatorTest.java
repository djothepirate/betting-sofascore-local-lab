package com.bettingproject.sofascorelocal.application.network;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ManualProviderRequestCoordinatorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-18T11:00:00Z"), ZoneOffset.UTC);

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
}
