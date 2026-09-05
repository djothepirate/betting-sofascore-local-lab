package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Minimized local read model; it never contains export bytes, ACK bodies or certificate data. */
public record J7DeliveryView(
        J7DeliveryPayloadClass payloadClass,
        List<String> policyBlockers,
        Optional<J7DeliveryLedgerStore.DeliverySnapshot> ledger,
        boolean preparationAllowed,
        boolean reconciliationAvailable) {

    public J7DeliveryView {
        payloadClass = Objects.requireNonNull(payloadClass, "payloadClass");
        policyBlockers = List.copyOf(Objects.requireNonNull(
                policyBlockers, "policyBlockers"));
        ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public String state() {
        return ledger.map(snapshot -> snapshot.state().name())
                .orElse("NOT_ATTEMPTED");
    }

    public int attemptCount() {
        return ledger.map(J7DeliveryLedgerStore.DeliverySnapshot::attemptCount)
                .orElse(0);
    }

    public boolean unknownOutcomeReconciliationRequired() {
        return ledger.map(snapshot -> snapshot.state()
                        == J7DeliveryLedgerStore.DeliveryState
                        .UNKNOWN_RECONCILIATION_REQUIRED)
                .orElse(false);
    }
}
