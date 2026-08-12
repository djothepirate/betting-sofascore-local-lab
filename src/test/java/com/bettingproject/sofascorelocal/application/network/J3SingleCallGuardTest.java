package com.bettingproject.sofascorelocal.application.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class J3SingleCallGuardTest {

    @Test
    void allowsOnlyOnePermitUntilItIsReleased() {
        J3SingleCallGuard guard = new J3SingleCallGuard();

        Optional<J3SingleCallGuard.Permit> first = guard.tryAcquire();

        assertThat(first).isPresent();
        assertThat(guard.isCallInProgress()).isTrue();
        assertThat(guard.tryAcquire()).isEmpty();

        first.orElseThrow().close();

        assertThat(guard.isCallInProgress()).isFalse();
        assertThat(guard.tryAcquire()).isPresent().get().satisfies(J3SingleCallGuard.Permit::close);
    }

    @Test
    void remainsIdempotentWhenAPermitIsClosedTwice() {
        J3SingleCallGuard guard = new J3SingleCallGuard();
        J3SingleCallGuard.Permit permit = guard.tryAcquire().orElseThrow();

        permit.close();
        permit.close();

        assertThat(guard.isCallInProgress()).isFalse();
    }

    @Test
    void grantsExactlyOnePermitAcrossConcurrentAttempts() throws Exception {
        J3SingleCallGuard guard = new J3SingleCallGuard();
        CountDownLatch start = new CountDownLatch(1);
        List<Optional<J3SingleCallGuard.Permit>> attempts = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = java.util.stream.IntStream.range(0, 32)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        return guard.tryAcquire();
                    }))
                    .toList();
            start.countDown();
            for (var future : futures) {
                attempts.add(future.get());
            }
        }

        List<J3SingleCallGuard.Permit> granted = attempts.stream()
                .flatMap(Optional::stream)
                .toList();
        assertThat(granted).hasSize(1);
        assertThat(guard.isCallInProgress()).isTrue();

        granted.getFirst().close();
        assertThat(guard.isCallInProgress()).isFalse();
    }
}
