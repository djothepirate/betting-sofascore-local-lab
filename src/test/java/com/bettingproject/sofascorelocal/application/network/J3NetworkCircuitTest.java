package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitIncident;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J3NetworkCircuitTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void startsLockedAndRequiresAnExplicitOperatorActivation() {
        J3NetworkCircuit circuit = J3NetworkCircuit.lockedAt(STARTED_AT);

        assertThat(circuit.snapshot().state()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(circuit.snapshot().reason()).isEqualTo(J3CircuitReason.STARTUP_LOCK);
        assertThat(circuit.snapshot().transportMayBeEvaluated()).isFalse();

        var activated = circuit.activateByOperator(STARTED_AT.plusSeconds(1));

        assertThat(activated.state()).isEqualTo(J3CircuitState.CLOSED);
        assertThat(activated.reason()).isEqualTo(J3CircuitReason.NONE);
        assertThat(activated.transportMayBeEvaluated()).isTrue();
    }

    @Test
    void globalOperatorStopLocksTheCircuitWithoutAutomaticRestart() {
        J3NetworkCircuit circuit = activeCircuit();

        var stopped = circuit.stopByOperator(STARTED_AT.plusSeconds(2));

        assertThat(stopped.state()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(stopped.reason()).isEqualTo(J3CircuitReason.OPERATOR_STOP);
        assertThat(stopped.transportMayBeEvaluated()).isFalse();
    }

    @Test
    void alreadyClosedCircuitCannotBeActivatedAgainImplicitly() {
        J3NetworkCircuit circuit = activeCircuit();

        assertThatThrownBy(() -> circuit.activateByOperator(STARTED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked circuit");
    }

    @ParameterizedTest
    @EnumSource(
            value = J3CircuitReason.class,
            names = {
                    "HTTP_BAD_REQUEST",
                    "HTTP_UNAUTHORIZED",
                    "HTTP_FORBIDDEN",
                    "TIMEOUT",
                    "UNEXPECTED_CONTENT",
                    "SCHEMA_INCOMPATIBLE",
                    "SERVER_ERROR"
            })
    void incidentsOpenTheCircuitWithoutRetry(J3CircuitReason reason) {
        J3NetworkCircuit circuit = activeCircuit();
        Instant incidentAt = STARTED_AT.plusSeconds(2);

        var opened = circuit.recordIncident(J3CircuitIncident.at(reason, incidentAt));

        assertThat(opened.state()).isEqualTo(J3CircuitState.OPEN);
        assertThat(opened.reason()).isEqualTo(reason);
        assertThat(opened.retryNotBefore()).isNull();
        assertThat(opened.transportMayBeEvaluated()).isFalse();
    }

    @Test
    void rateLimitOpensTheCircuitAndKeepsTheServerRetryBoundary() {
        J3NetworkCircuit circuit = activeCircuit();
        Instant incidentAt = STARTED_AT.plusSeconds(2);
        Instant retryNotBefore = incidentAt.plusSeconds(120);

        var opened = circuit.recordIncident(
                J3CircuitIncident.rateLimited(incidentAt, retryNotBefore));

        assertThat(opened.state()).isEqualTo(J3CircuitState.OPEN);
        assertThat(opened.reason()).isEqualTo(J3CircuitReason.HTTP_TOO_MANY_REQUESTS);
        assertThat(opened.retryNotBefore()).isEqualTo(retryNotBefore);
    }

    @Test
    void openCircuitCannotReactivateWithoutExplicitStopAndReview() {
        J3NetworkCircuit circuit = activeCircuit();
        circuit.recordIncident(J3CircuitIncident.at(
                J3CircuitReason.HTTP_FORBIDDEN,
                STARTED_AT.plusSeconds(2)));

        assertThatThrownBy(() -> circuit.activateByOperator(STARTED_AT.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stopped and reviewed");

        circuit.stopByOperator(STARTED_AT.plusSeconds(3));
        assertThat(circuit.activateByOperator(STARTED_AT.plusSeconds(4)).state())
                .isEqualTo(J3CircuitState.CLOSED);
    }

    @Test
    void incidentsCannotBeRecordedUnlessTheCircuitIsClosed() {
        J3NetworkCircuit circuit = J3NetworkCircuit.lockedAt(STARTED_AT);

        assertThatThrownBy(() -> circuit.recordIncident(J3CircuitIncident.at(
                J3CircuitReason.TIMEOUT,
                STARTED_AT.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void rejectsInvalidRateLimitAndNonChronologicalTransitions() {
        assertThatThrownBy(() -> J3CircuitIncident.rateLimited(
                STARTED_AT,
                STARTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryNotBefore");

        J3NetworkCircuit circuit = activeCircuit();
        assertThatThrownBy(() -> circuit.stopByOperator(STARTED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chronological");
    }

    private static J3NetworkCircuit activeCircuit() {
        J3NetworkCircuit circuit = J3NetworkCircuit.lockedAt(STARTED_AT);
        circuit.activateByOperator(STARTED_AT.plusSeconds(1));
        return circuit;
    }
}
