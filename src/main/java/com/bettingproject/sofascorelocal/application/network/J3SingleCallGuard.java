package com.bettingproject.sofascorelocal.application.network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Final in-memory exclusion guard for the J3 maximum concurrency of one.
 */
public final class J3SingleCallGuard {

    private final AtomicBoolean callInProgress = new AtomicBoolean();

    public Optional<Permit> tryAcquire() {
        if (!callInProgress.compareAndSet(false, true)) {
            return Optional.empty();
        }
        return Optional.of(new Permit(this));
    }

    public boolean isCallInProgress() {
        return callInProgress.get();
    }

    private void release() {
        if (!callInProgress.compareAndSet(true, false)) {
            throw new IllegalStateException("J3 call permit was not held");
        }
    }

    public static final class Permit implements AutoCloseable {

        private final J3SingleCallGuard owner;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(J3SingleCallGuard owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                owner.release();
            }
        }
    }
}
