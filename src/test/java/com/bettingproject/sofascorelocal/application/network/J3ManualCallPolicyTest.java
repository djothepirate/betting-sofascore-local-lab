package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallPolicyInput;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallPolicyResult;
import com.bettingproject.sofascorelocal.domain.provider.J3NetworkBlockReason;
import com.bettingproject.sofascorelocal.domain.provider.J3NetworkDecision;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J3ManualCallPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private static final Duration MINIMUM_DELAY = Duration.ofSeconds(3);

    private final J3ManualCallPolicy policy = new J3ManualCallPolicy();

    @Test
    void usesAnAvailableCacheEntryWithoutMakingTransportEligible() {
        J3ManualCallPolicyInput input = eligibleInput(true, null, false);

        J3ManualCallPolicyResult result = policy.evaluate(input);

        assertThat(result.decision()).isEqualTo(J3NetworkDecision.USE_CACHE);
        assertThat(result.reason()).isEqualTo(J3NetworkBlockReason.CACHE_AVAILABLE);
        assertThat(result.nextEligibleAt()).isNull();
    }

    @Test
    void blocksDisabledFeatureAndGlobalStopBeforeNetworkPrerequisites() {
        J3ManualCallPolicyInput disabled = input(
                false, false, true, true, true, true, false,
                closedCircuit(), false, null);
        J3ManualCallPolicyInput stopped = input(
                true, true, true, true, true, true, false,
                closedCircuit(), false, null);

        assertBlocked(disabled, J3NetworkBlockReason.FEATURE_DISABLED);
        assertBlocked(stopped, J3NetworkBlockReason.GLOBAL_STOP_ACTIVE);
    }

    @Test
    void requiresAnAllowedAndConfiguredLogicalEndpoint() {
        J3ManualCallPolicyInput disallowed = input(
                true, false, false, true, true, true, false,
                closedCircuit(), false, null);
        J3ManualCallPolicyInput unconfigured = input(
                true, false, true, false, true, true, false,
                closedCircuit(), false, null);

        assertBlocked(disallowed, J3NetworkBlockReason.ENDPOINT_NOT_ALLOWED);
        assertBlocked(unconfigured, J3NetworkBlockReason.ENDPOINT_NOT_CONFIGURED);
    }

    @Test
    void requiresSeparateOperatorActivationAndPerCallConfirmation() {
        J3ManualCallPolicyInput notActivated = input(
                true, false, true, true, false, true, false,
                closedCircuit(), false, null);
        J3ManualCallPolicyInput notConfirmed = input(
                true, false, true, true, true, false, false,
                closedCircuit(), false, null);

        assertBlocked(notActivated, J3NetworkBlockReason.OPERATOR_ACTIVATION_REQUIRED);
        assertBlocked(notConfirmed, J3NetworkBlockReason.MANUAL_CONFIRMATION_REQUIRED);
    }

    @Test
    void blocksLockedAndOpenCircuits() {
        J3ManualCallPolicyInput locked = input(
                true, false, true, true, true, true, false,
                lockedCircuit(), false, null);
        J3ManualCallPolicyInput open = input(
                true, false, true, true, true, true, false,
                openCircuit(), false, null);

        assertBlocked(locked, J3NetworkBlockReason.CIRCUIT_LOCKED);
        assertBlocked(open, J3NetworkBlockReason.CIRCUIT_OPEN);
    }

    @Test
    void enforcesAConcurrencyLimitOfOne() {
        J3ManualCallPolicyInput input = eligibleInput(false, null, true);

        assertBlocked(input, J3NetworkBlockReason.CALL_ALREADY_IN_PROGRESS);
    }

    @Test
    void blocksUntilTheMinimumDelayHasFullyElapsed() {
        Instant lastStartedAt = NOW.minusSeconds(2);

        J3ManualCallPolicyResult result = policy.evaluate(
                eligibleInput(false, lastStartedAt, false));

        assertThat(result.decision()).isEqualTo(J3NetworkDecision.BLOCKED);
        assertThat(result.reason()).isEqualTo(J3NetworkBlockReason.MINIMUM_DELAY_NOT_ELAPSED);
        assertThat(result.nextEligibleAt()).isEqualTo(lastStartedAt.plus(MINIMUM_DELAY));
    }

    @Test
    void considersTransportOnlyAfterEveryOfflineCheckPasses() {
        J3ManualCallPolicyResult firstCall = policy.evaluate(
                eligibleInput(false, null, false));
        J3ManualCallPolicyResult delayedCall = policy.evaluate(
                eligibleInput(false, NOW.minus(MINIMUM_DELAY), false));

        assertThat(firstCall).isEqualTo(J3ManualCallPolicyResult.transportEligible());
        assertThat(delayedCall).isEqualTo(J3ManualCallPolicyResult.transportEligible());
    }

    @Test
    void rejectsUnsafeTimeAndDelayInputs() {
        assertThatThrownBy(() -> new J3ManualCallPolicyInput(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                true,
                false,
                true,
                true,
                true,
                true,
                false,
                closedCircuit(),
                false,
                NOW.plusSeconds(1),
                NOW,
                MINIMUM_DELAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");

        assertThatThrownBy(() -> new J3ManualCallPolicyInput(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                true,
                false,
                true,
                true,
                true,
                true,
                false,
                closedCircuit(),
                false,
                null,
                NOW,
                Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("three seconds");

        assertThatThrownBy(() -> new J3ManualCallPolicyInput(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                true,
                false,
                true,
                true,
                true,
                true,
                false,
                new J3CircuitSnapshot(
                        J3CircuitState.CLOSED,
                        J3CircuitReason.NONE,
                        NOW.plusSeconds(1),
                        null),
                false,
                null,
                NOW,
                MINIMUM_DELAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("circuit state");
    }

    private void assertBlocked(
            J3ManualCallPolicyInput input,
            J3NetworkBlockReason expectedReason) {
        J3ManualCallPolicyResult result = policy.evaluate(input);

        assertThat(result.decision()).isEqualTo(J3NetworkDecision.BLOCKED);
        assertThat(result.reason()).isEqualTo(expectedReason);
        assertThat(result.nextEligibleAt()).isNull();
    }

    private static J3ManualCallPolicyInput eligibleInput(
            boolean cacheHit,
            Instant lastTransportStartedAt,
            boolean callInProgress) {
        return input(
                true,
                false,
                true,
                true,
                true,
                true,
                cacheHit,
                closedCircuit(),
                callInProgress,
                lastTransportStartedAt);
    }

    private static J3ManualCallPolicyInput input(
            boolean featureEnabled,
            boolean globalStopActive,
            boolean endpointAllowed,
            boolean endpointConfigured,
            boolean operatorActivated,
            boolean manualConfirmationPresent,
            boolean cacheHit,
            J3CircuitSnapshot circuit,
            boolean callInProgress,
            Instant lastTransportStartedAt) {
        return new J3ManualCallPolicyInput(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                featureEnabled,
                globalStopActive,
                endpointAllowed,
                endpointConfigured,
                operatorActivated,
                manualConfirmationPresent,
                cacheHit,
                circuit,
                callInProgress,
                lastTransportStartedAt,
                NOW,
                MINIMUM_DELAY);
    }

    private static J3CircuitSnapshot lockedCircuit() {
        return new J3CircuitSnapshot(
                J3CircuitState.LOCKED,
                J3CircuitReason.STARTUP_LOCK,
                NOW.minusSeconds(10),
                null);
    }

    private static J3CircuitSnapshot closedCircuit() {
        return new J3CircuitSnapshot(
                J3CircuitState.CLOSED,
                J3CircuitReason.NONE,
                NOW.minusSeconds(10),
                null);
    }

    private static J3CircuitSnapshot openCircuit() {
        return new J3CircuitSnapshot(
                J3CircuitState.OPEN,
                J3CircuitReason.HTTP_FORBIDDEN,
                NOW.minusSeconds(10),
                null);
    }
}
