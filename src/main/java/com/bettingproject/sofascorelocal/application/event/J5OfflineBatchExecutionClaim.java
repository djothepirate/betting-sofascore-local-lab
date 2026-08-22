package com.bettingproject.sofascorelocal.application.event;

import java.util.Objects;
import java.util.UUID;

public record J5OfflineBatchExecutionClaim(
        UUID requestId,
        J5OfflineBatchPlan plan) {

    public J5OfflineBatchExecutionClaim {
        requestId = Objects.requireNonNull(requestId, "requestId");
        plan = Objects.requireNonNull(plan, "plan");
        if (!requestId.equals(plan.requestId())) {
            throw new IllegalArgumentException("claim identity is inconsistent");
        }
    }
}
