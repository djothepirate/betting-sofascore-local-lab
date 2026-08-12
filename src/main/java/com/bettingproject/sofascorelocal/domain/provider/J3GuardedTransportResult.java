package com.bettingproject.sofascorelocal.domain.provider;

import java.util.Objects;
import java.util.Optional;

public record J3GuardedTransportResult(
        J3ManualCallPolicyResult policy,
        Optional<ScheduledEventsTransportResponse> response) {

    public J3GuardedTransportResult {
        Objects.requireNonNull(policy, "policy");
        response = Objects.requireNonNull(response, "response");
        boolean transported = policy.decision() == J3NetworkDecision.TRANSPORT_ELIGIBLE;
        if (transported != response.isPresent()) {
            throw new IllegalArgumentException(
                    "only a completed eligible transport can carry a response");
        }
    }

    public static J3GuardedTransportResult withoutTransport(J3ManualCallPolicyResult policy) {
        return new J3GuardedTransportResult(policy, Optional.empty());
    }

    public static J3GuardedTransportResult transported(
            ScheduledEventsTransportResponse response) {
        return new J3GuardedTransportResult(
                J3ManualCallPolicyResult.transportEligible(),
                Optional.of(Objects.requireNonNull(response, "response")));
    }
}
