package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryExecutionGateTest {

    @Test
    void activeLeaseRejectsConcurrentWorkAndSuccessfulCloseAllowsReentry() {
        J7DeliveryExecutionGate gate = new J7DeliveryExecutionGate();

        J7DeliveryExecutionGate.Lease first = gate.acquire();
        assertThat(gate.state()).isEqualTo(J7DeliveryExecutionGate.State.ACTIVE);
        assertError(gate::acquire, J7DeliveryError.DELIVERY_IN_PROGRESS);

        first.close();
        first.close();
        assertThat(gate.state()).isEqualTo(J7DeliveryExecutionGate.State.IDLE);

        try (J7DeliveryExecutionGate.Lease ignored = gate.acquire()) {
            assertThat(gate.state()).isEqualTo(J7DeliveryExecutionGate.State.ACTIVE);
        }
        assertThat(gate.state()).isEqualTo(J7DeliveryExecutionGate.State.IDLE);
    }

    @Test
    void poisonedLeaseRemainsFailClosedAfterCloseUntilProcessRestart() {
        J7DeliveryExecutionGate gate = new J7DeliveryExecutionGate();
        J7DeliveryExecutionGate.Lease lease = gate.acquire();

        lease.poison();
        assertThat(gate.state()).isEqualTo(J7DeliveryExecutionGate.State.POISONED);
        assertError(gate::acquire, J7DeliveryError.DELIVERY_RUNTIME_POISONED);

        lease.close();
        assertThat(gate.state()).isEqualTo(J7DeliveryExecutionGate.State.POISONED);
        assertError(gate::acquire, J7DeliveryError.DELIVERY_RUNTIME_POISONED);
    }

    private static void assertError(
            Runnable action,
            J7DeliveryError expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(J7DeliveryException.class)
                .hasMessage(expected.name())
                .extracting(exception -> ((J7DeliveryException) exception).error())
                .isEqualTo(expected);
    }
}
