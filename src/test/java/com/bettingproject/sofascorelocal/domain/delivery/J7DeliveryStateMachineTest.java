package com.bettingproject.sofascorelocal.domain.delivery;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryStateMachineTest {

    private static final Set<Transition> ALLOWED = Set.of(
            new Transition(
                    J7DeliveryState.NOT_ATTEMPTED,
                    J7DeliveryState.IN_FLIGHT),
            new Transition(
                    J7DeliveryState.IN_FLIGHT,
                    J7DeliveryState.DELIVERED),
            new Transition(
                    J7DeliveryState.IN_FLIGHT,
                    J7DeliveryState.DUPLICATE_CONFIRMED),
            new Transition(
                    J7DeliveryState.IN_FLIGHT,
                    J7DeliveryState.REJECTED_TERMINAL),
            new Transition(
                    J7DeliveryState.IN_FLIGHT,
                    J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED),
            new Transition(
                    J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                    J7DeliveryState.IN_FLIGHT));

    @Test
    void exposesExactlyTheSixStatesFixedByAdrSs003() {
        assertThat(J7DeliveryState.values()).containsExactly(
                J7DeliveryState.NOT_ATTEMPTED,
                J7DeliveryState.IN_FLIGHT,
                J7DeliveryState.DELIVERED,
                J7DeliveryState.DUPLICATE_CONFIRMED,
                J7DeliveryState.REJECTED_TERMINAL,
                J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
    }

    @Test
    void implementsExactlyTheSixAllowedDirectedTransitions() {
        for (J7DeliveryState current : J7DeliveryState.values()) {
            for (J7DeliveryState target : J7DeliveryState.values()) {
                Transition transition = new Transition(current, target);
                boolean expected = ALLOWED.contains(transition);

                assertThat(J7DeliveryStateMachine.canTransition(current, target))
                        .as("%s -> %s", current, target)
                        .isEqualTo(expected);
                if (expected) {
                    assertThat(J7DeliveryStateMachine.transition(current, target))
                            .isEqualTo(target);
                }
                else {
                    assertInvalidTransition(() ->
                            J7DeliveryStateMachine.transition(current, target));
                }
            }
        }
    }

    @Test
    void refusesNullTransitionsWithTheSameSafeError() {
        assertThat(J7DeliveryStateMachine.canTransition(null, J7DeliveryState.IN_FLIGHT))
                .isFalse();
        assertThat(J7DeliveryStateMachine.canTransition(
                J7DeliveryState.NOT_ATTEMPTED, null)).isFalse();
        assertInvalidTransition(() -> J7DeliveryStateMachine.transition(
                null, J7DeliveryState.IN_FLIGHT));
        assertInvalidTransition(() -> J7DeliveryStateMachine.transition(
                J7DeliveryState.NOT_ATTEMPTED, null));
    }

    @Test
    void mapsTheOnlyTwoAcknowledgementsDeterministically() {
        assertThat(J7DeliveryStateMachine.completeWithAcknowledgement(
                J7DeliveryState.IN_FLIGHT,
                J7DeliveryAcknowledgementStatus.IMPORTED))
                .isEqualTo(J7DeliveryState.DELIVERED);
        assertThat(J7DeliveryStateMachine.completeWithAcknowledgement(
                J7DeliveryState.IN_FLIGHT,
                J7DeliveryAcknowledgementStatus.DUPLICATE))
                .isEqualTo(J7DeliveryState.DUPLICATE_CONFIRMED);

        assertInvalidTransition(() ->
                J7DeliveryStateMachine.completeWithAcknowledgement(
                        J7DeliveryState.NOT_ATTEMPTED,
                        J7DeliveryAcknowledgementStatus.IMPORTED));
        assertThatThrownBy(() -> J7DeliveryStateMachine.completeWithAcknowledgement(
                J7DeliveryState.IN_FLIGHT, null))
                .isInstanceOfSatisfying(J7DeliveryException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(
                            J7DeliveryError.INVALID_ACKNOWLEDGEMENT_STATUS);
                    assertThat(exception.getMessage()).isEqualTo(
                            J7DeliveryError.INVALID_ACKNOWLEDGEMENT_STATUS.name());
                });
    }

    @Test
    void identifiesTerminalAndManualAttemptStatesWithoutAmbiguity() {
        assertThat(J7DeliveryState.values())
                .filteredOn(J7DeliveryState::isTerminal)
                .containsExactly(
                        J7DeliveryState.DELIVERED,
                        J7DeliveryState.DUPLICATE_CONFIRMED,
                        J7DeliveryState.REJECTED_TERMINAL);
        assertThat(J7DeliveryState.values())
                .filteredOn(J7DeliveryState::canStartManualAttempt)
                .containsExactly(
                        J7DeliveryState.NOT_ATTEMPTED,
                        J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
    }

    private static void assertInvalidTransition(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(J7DeliveryException.class, exception -> {
                    assertThat(exception.error())
                            .isEqualTo(J7DeliveryError.INVALID_TRANSITION);
                    assertThat(exception.getMessage())
                            .isEqualTo(J7DeliveryError.INVALID_TRANSITION.name());
                });
    }

    private record Transition(J7DeliveryState current, J7DeliveryState target) {
    }
}
