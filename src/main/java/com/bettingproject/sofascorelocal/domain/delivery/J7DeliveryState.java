package com.bettingproject.sofascorelocal.domain.delivery;

/**
 * Delivery states fixed by ADR-SS-003 v0.1. They are deliberately independent
 * from the local J7 validation status.
 */
public enum J7DeliveryState {
    NOT_ATTEMPTED,
    IN_FLIGHT,
    DELIVERED,
    DUPLICATE_CONFIRMED,
    REJECTED_TERMINAL,
    UNKNOWN_RECONCILIATION_REQUIRED;

    public boolean isTerminal() {
        return this == DELIVERED
                || this == DUPLICATE_CONFIRMED
                || this == REJECTED_TERMINAL;
    }

    public boolean canStartManualAttempt() {
        return this == NOT_ATTEMPTED
                || this == UNKNOWN_RECONCILIATION_REQUIRED;
    }
}
