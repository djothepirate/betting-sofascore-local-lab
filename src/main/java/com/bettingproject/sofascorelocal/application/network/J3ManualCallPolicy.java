package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallPolicyInput;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallPolicyResult;
import com.bettingproject.sofascorelocal.domain.provider.J3NetworkBlockReason;

import java.time.Instant;
import java.util.Objects;

/**
 * Pure offline policy evaluation. TRANSPORT_ELIGIBLE is a decision only and never performs I/O.
 */
public final class J3ManualCallPolicy {

    public J3ManualCallPolicyResult evaluate(J3ManualCallPolicyInput input) {
        Objects.requireNonNull(input, "input");

        if (input.cacheHit()) {
            return J3ManualCallPolicyResult.useCache();
        }
        if (!input.featureEnabled()) {
            return J3ManualCallPolicyResult.blocked(J3NetworkBlockReason.FEATURE_DISABLED);
        }
        if (input.globalStopActive()) {
            return J3ManualCallPolicyResult.blocked(J3NetworkBlockReason.GLOBAL_STOP_ACTIVE);
        }
        if (!input.endpointAllowed()) {
            return J3ManualCallPolicyResult.blocked(J3NetworkBlockReason.ENDPOINT_NOT_ALLOWED);
        }
        if (!input.endpointConfigured()) {
            return J3ManualCallPolicyResult.blocked(J3NetworkBlockReason.ENDPOINT_NOT_CONFIGURED);
        }
        if (!input.operatorActivated()) {
            return J3ManualCallPolicyResult.blocked(
                    J3NetworkBlockReason.OPERATOR_ACTIVATION_REQUIRED);
        }
        if (!input.manualConfirmationPresent()) {
            return J3ManualCallPolicyResult.blocked(
                    J3NetworkBlockReason.MANUAL_CONFIRMATION_REQUIRED);
        }
        if (input.circuit().state() == J3CircuitState.LOCKED) {
            return J3ManualCallPolicyResult.blocked(J3NetworkBlockReason.CIRCUIT_LOCKED);
        }
        if (input.circuit().state() == J3CircuitState.OPEN) {
            return J3ManualCallPolicyResult.blocked(J3NetworkBlockReason.CIRCUIT_OPEN);
        }
        if (input.callInProgress()) {
            return J3ManualCallPolicyResult.blocked(J3NetworkBlockReason.CALL_ALREADY_IN_PROGRESS);
        }
        if (input.lastTransportStartedAt() != null) {
            Instant nextEligibleAt = input.lastTransportStartedAt().plus(input.minimumDelay());
            if (input.evaluatedAt().isBefore(nextEligibleAt)) {
                return J3ManualCallPolicyResult.delayedUntil(nextEligibleAt);
            }
        }
        return J3ManualCallPolicyResult.transportEligible();
    }
}
