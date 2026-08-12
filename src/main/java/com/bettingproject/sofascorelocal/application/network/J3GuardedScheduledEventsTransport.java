package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3GuardedTransportResult;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallPolicyInput;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallPolicyResult;
import com.bettingproject.sofascorelocal.domain.provider.J3NetworkBlockReason;
import com.bettingproject.sofascorelocal.domain.provider.J3NetworkDecision;
import com.bettingproject.sofascorelocal.domain.provider.J3ScheduledEventsCallAuthorization;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.ScheduledEventsTransport;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Composes the pure J3 policy and the atomic single-call permit before simulated transport I/O.
 */
public final class J3GuardedScheduledEventsTransport {

    private final J3ManualCallPolicy policy;
    private final J3SingleCallGuard callGuard;
    private final ScheduledEventsTransport transport;
    private final Clock clock;
    private final AtomicReference<Instant> lastTransportStartedAt = new AtomicReference<>();

    public J3GuardedScheduledEventsTransport(
            J3ManualCallPolicy policy,
            J3SingleCallGuard callGuard,
            ScheduledEventsTransport transport,
            Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.callGuard = Objects.requireNonNull(callGuard, "callGuard");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public J3GuardedTransportResult execute(
            J3ScheduledEventsCallAuthorization authorization,
            ScheduledEventsTransportRequest request) {
        Objects.requireNonNull(authorization, "authorization");
        Objects.requireNonNull(request, "request");
        Instant evaluatedAt = clock.instant();

        J3ManualCallPolicyResult decision = policy.evaluate(new J3ManualCallPolicyInput(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                authorization.featureEnabled(),
                authorization.globalStopActive(),
                authorization.endpointAllowed(),
                authorization.endpointConfigured(),
                authorization.operatorActivated(),
                authorization.manualConfirmationPresent(),
                authorization.cacheHit(),
                authorization.circuit(),
                callGuard.isCallInProgress(),
                lastTransportStartedAt.get(),
                evaluatedAt,
                authorization.minimumDelay()));
        if (decision.decision() != J3NetworkDecision.TRANSPORT_ELIGIBLE) {
            return J3GuardedTransportResult.withoutTransport(decision);
        }

        var permit = callGuard.tryAcquire();
        if (permit.isEmpty()) {
            return J3GuardedTransportResult.withoutTransport(
                    J3ManualCallPolicyResult.blocked(
                            J3NetworkBlockReason.CALL_ALREADY_IN_PROGRESS));
        }

        try (J3SingleCallGuard.Permit ignored = permit.orElseThrow()) {
            lastTransportStartedAt.set(evaluatedAt);
            return J3GuardedTransportResult.transported(transport.execute(request));
        }
    }

    public Instant lastTransportStartedAt() {
        return lastTransportStartedAt.get();
    }
}
