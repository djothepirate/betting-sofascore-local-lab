package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide non-blocking exclusion between a delivery and local reconciliation actions.
 * A failed transport cleanup poisons the gate until the application is restarted.
 */
@Component
public final class J7DeliveryExecutionGate {

    private final AtomicReference<State> state =
            new AtomicReference<>(State.IDLE);

    public Lease acquire() {
        while (true) {
            State observed = state.get();
            if (observed == State.ACTIVE) {
                throw new J7DeliveryException(J7DeliveryError.DELIVERY_IN_PROGRESS);
            }
            if (observed == State.POISONED) {
                throw new J7DeliveryException(
                        J7DeliveryError.DELIVERY_RUNTIME_POISONED);
            }
            if (state.compareAndSet(State.IDLE, State.ACTIVE)) {
                return new Lease();
            }
        }
    }

    State state() {
        return state.get();
    }

    enum State {
        IDLE,
        ACTIVE,
        POISONED
    }

    public final class Lease implements AutoCloseable {

        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean poisoned = new AtomicBoolean();

        private Lease() {
        }

        public void poison() {
            if (closed.get()) {
                throw new IllegalStateException("delivery execution lease is closed");
            }
            poisoned.set(true);
            state.set(State.POISONED);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (poisoned.get() || state.get() == State.POISONED) {
                state.set(State.POISONED);
                return;
            }
            if (!state.compareAndSet(State.ACTIVE, State.IDLE)) {
                state.set(State.POISONED);
            }
        }
    }
}
