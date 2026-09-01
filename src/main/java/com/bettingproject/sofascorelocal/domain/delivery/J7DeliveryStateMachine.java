package com.bettingproject.sofascorelocal.domain.delivery;

/**
 * Closed deterministic state machine for the ADR-SS-003 delivery ledger.
 */
public final class J7DeliveryStateMachine {

    private J7DeliveryStateMachine() {
    }

    public static boolean canTransition(
            J7DeliveryState current,
            J7DeliveryState target) {
        if (current == null || target == null) {
            return false;
        }
        return switch (current) {
            case NOT_ATTEMPTED -> target == J7DeliveryState.IN_FLIGHT;
            case IN_FLIGHT -> target == J7DeliveryState.DELIVERED
                    || target == J7DeliveryState.DUPLICATE_CONFIRMED
                    || target == J7DeliveryState.REJECTED_TERMINAL
                    || target == J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED;
            case UNKNOWN_RECONCILIATION_REQUIRED ->
                    target == J7DeliveryState.IN_FLIGHT;
            case DELIVERED, DUPLICATE_CONFIRMED, REJECTED_TERMINAL -> false;
        };
    }

    public static J7DeliveryState transition(
            J7DeliveryState current,
            J7DeliveryState target) {
        if (!canTransition(current, target)) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_TRANSITION);
        }
        return target;
    }

    public static J7DeliveryState completeWithAcknowledgement(
            J7DeliveryState current,
            J7DeliveryAcknowledgementStatus acknowledgementStatus) {
        if (acknowledgementStatus == null) {
            throw new J7DeliveryException(
                    J7DeliveryError.INVALID_ACKNOWLEDGEMENT_STATUS);
        }
        J7DeliveryState target = switch (acknowledgementStatus) {
            case IMPORTED -> J7DeliveryState.DELIVERED;
            case DUPLICATE -> J7DeliveryState.DUPLICATE_CONFIRMED;
        };
        return transition(current, target);
    }
}
