package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Instant;
import java.util.Objects;

public record J3ManualCallPolicyResult(
        J3NetworkDecision decision,
        J3NetworkBlockReason reason,
        Instant nextEligibleAt) {

    public J3ManualCallPolicyResult {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
        if (decision == J3NetworkDecision.TRANSPORT_ELIGIBLE) {
            if (reason != J3NetworkBlockReason.NONE || nextEligibleAt != null) {
                throw new IllegalArgumentException(
                        "transport eligibility cannot carry a block reason or retry boundary");
            }
        } else if (decision == J3NetworkDecision.USE_CACHE) {
            if (reason != J3NetworkBlockReason.CACHE_AVAILABLE || nextEligibleAt != null) {
                throw new IllegalArgumentException("cache decisions require CACHE_AVAILABLE");
            }
        } else if (reason == J3NetworkBlockReason.NONE
                || reason == J3NetworkBlockReason.CACHE_AVAILABLE) {
            throw new IllegalArgumentException("blocked decisions require a blocking reason");
        }
        if ((reason == J3NetworkBlockReason.MINIMUM_DELAY_NOT_ELAPSED)
                != (nextEligibleAt != null)) {
            throw new IllegalArgumentException(
                    "only a minimum-delay block carries nextEligibleAt");
        }
    }

    public static J3ManualCallPolicyResult useCache() {
        return new J3ManualCallPolicyResult(
                J3NetworkDecision.USE_CACHE,
                J3NetworkBlockReason.CACHE_AVAILABLE,
                null);
    }

    public static J3ManualCallPolicyResult blocked(J3NetworkBlockReason reason) {
        return new J3ManualCallPolicyResult(J3NetworkDecision.BLOCKED, reason, null);
    }

    public static J3ManualCallPolicyResult delayedUntil(Instant nextEligibleAt) {
        return new J3ManualCallPolicyResult(
                J3NetworkDecision.BLOCKED,
                J3NetworkBlockReason.MINIMUM_DELAY_NOT_ELAPSED,
                Objects.requireNonNull(nextEligibleAt, "nextEligibleAt"));
    }

    public static J3ManualCallPolicyResult transportEligible() {
        return new J3ManualCallPolicyResult(
                J3NetworkDecision.TRANSPORT_ELIGIBLE,
                J3NetworkBlockReason.NONE,
                null);
    }
}
