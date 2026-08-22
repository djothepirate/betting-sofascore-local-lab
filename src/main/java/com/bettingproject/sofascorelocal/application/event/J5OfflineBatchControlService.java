package com.bettingproject.sofascorelocal.application.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class J5OfflineBatchControlService {

    private final J5OfflineBatchPolicy policy;
    private final J5OfflineBatchPlanService planService;
    private final Clock clock;

    private J5OfflineBatchState state = J5OfflineBatchState.LOCKED;
    private Instant changedAt;
    private J5OfflineBatchPlan plan;
    private String terminalCode;
    private J5OfflineBatchResult result;

    @Autowired
    public J5OfflineBatchControlService(
            J5OfflineBatchPolicy policy,
            J5OfflineBatchPlanService planService) {
        this(policy, planService, Clock.systemUTC());
    }

    J5OfflineBatchControlService(
            J5OfflineBatchPolicy policy,
            J5OfflineBatchPlanService planService,
            Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.planService = Objects.requireNonNull(planService, "planService");
        this.clock = Objects.requireNonNull(clock, "clock");
        changedAt = clock.instant();
    }

    public synchronized J5OfflineBatchControlSnapshot snapshot() {
        expireIfNecessary(clock.instant());
        return toSnapshot();
    }

    public synchronized J5OfflineBatchControlSnapshot prepare(
            LocalDate date,
            String zoneId,
            List<UUID> selectedCanonicalEventIds) {
        Instant now = clock.instant();
        expireIfNecessary(now);
        if (state == J5OfflineBatchState.AWAITING_CONFIRMATION
                || state == J5OfflineBatchState.EXECUTING) {
            throw rejected(J5OfflineBatchError.ACTIVE_BATCH_EXISTS);
        }
        policy.requireAvailable();
        plan = planService.create(date, zoneId, selectedCanonicalEventIds);
        state = J5OfflineBatchState.AWAITING_CONFIRMATION;
        terminalCode = null;
        result = null;
        changedAt = now;
        return toSnapshot();
    }

    public synchronized J5OfflineBatchPlan requirePending(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        expireIfNecessary(clock.instant());
        if (state == J5OfflineBatchState.EXPIRED_LOCKED) {
            throw rejected(J5OfflineBatchError.CONFIRMATION_EXPIRED);
        }
        if (state != J5OfflineBatchState.AWAITING_CONFIRMATION || plan == null) {
            throw rejected(J5OfflineBatchError.NO_PENDING_BATCH);
        }
        if (!plan.requestId().equals(requestId)) {
            throw rejected(J5OfflineBatchError.REQUEST_ID_MISMATCH);
        }
        return plan;
    }

    public synchronized J5OfflineBatchPlan requireReadyForUpload(UUID requestId) {
        J5OfflineBatchPlan pending = requirePending(requestId);
        policy.requireAvailable();
        planService.requireCurrent(pending);
        return pending;
    }

    public synchronized J5OfflineBatchExecutionClaim confirmAndClaim(
            UUID requestId,
            String confirmationText,
            boolean acknowledged) {
        J5OfflineBatchPlan pending = requirePending(requestId);
        if (!acknowledged) {
            throw rejected(J5OfflineBatchError.ACKNOWLEDGEMENT_REQUIRED);
        }
        if (!constantTimeEquals(pending.confirmationPhrase(), confirmationText)) {
            throw rejected(J5OfflineBatchError.CONFIRMATION_TEXT_MISMATCH);
        }
        policy.requireAvailable();
        state = J5OfflineBatchState.EXECUTING;
        changedAt = clock.instant();
        return new J5OfflineBatchExecutionClaim(requestId, pending);
    }

    public synchronized boolean executionMayContinue(UUID requestId) {
        return state == J5OfflineBatchState.EXECUTING
                && plan != null
                && plan.requestId().equals(requestId);
    }

    public synchronized J5OfflineBatchControlSnapshot complete(
            UUID requestId,
            J5OfflineBatchResult completedResult) {
        requireExecuting(requestId);
        Objects.requireNonNull(completedResult, "completedResult");
        if (!completedResult.completed()
                || !completedResult.requestId().equals(requestId)) {
            throw new IllegalArgumentException("completed result does not match active batch");
        }
        result = completedResult;
        terminalCode = completedResult.terminalCode();
        state = J5OfflineBatchState.COMPLETED_LOCKED;
        changedAt = clock.instant();
        return toSnapshot();
    }

    public synchronized J5OfflineBatchControlSnapshot fail(
            UUID requestId,
            J5OfflineBatchError error,
            long totalBytes) {
        requireExecuting(requestId);
        result = J5OfflineBatchResult.failed(plan, error, totalBytes);
        terminalCode = error.name();
        state = J5OfflineBatchState.FAILED_LOCKED;
        changedAt = clock.instant();
        return toSnapshot();
    }

    public synchronized J5OfflineBatchControlSnapshot stop(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        expireIfNecessary(clock.instant());
        if (state == J5OfflineBatchState.AWAITING_CONFIRMATION) {
            if (plan == null || !plan.requestId().equals(requestId)) {
                throw rejected(J5OfflineBatchError.REQUEST_ID_MISMATCH);
            }
            state = J5OfflineBatchState.STOPPED_LOCKED;
            terminalCode = J5OfflineBatchError.OPERATOR_STOP.name();
            result = J5OfflineBatchResult.failed(plan, J5OfflineBatchError.OPERATOR_STOP, 0);
            changedAt = clock.instant();
        }
        return toSnapshot();
    }

    private void requireExecuting(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        if (state != J5OfflineBatchState.EXECUTING || plan == null) {
            throw rejected(J5OfflineBatchError.NO_PENDING_BATCH);
        }
        if (!plan.requestId().equals(requestId)) {
            throw rejected(J5OfflineBatchError.REQUEST_ID_MISMATCH);
        }
    }

    private void expireIfNecessary(Instant now) {
        if (state == J5OfflineBatchState.AWAITING_CONFIRMATION
                && plan != null
                && !now.isBefore(plan.expiresAt())) {
            state = J5OfflineBatchState.EXPIRED_LOCKED;
            terminalCode = J5OfflineBatchError.CONFIRMATION_EXPIRED.name();
            result = J5OfflineBatchResult.failed(
                    plan, J5OfflineBatchError.CONFIRMATION_EXPIRED, 0);
            changedAt = now;
        }
    }

    private J5OfflineBatchControlSnapshot toSnapshot() {
        J5OfflineBatchPolicySnapshot policySnapshot = policy.snapshot();
        return new J5OfflineBatchControlSnapshot(
                state,
                changedAt,
                plan,
                terminalCode,
                result,
                policySnapshot.available(),
                policySnapshot.blockers());
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null || actual.length() > 160) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static J5OfflineBatchException rejected(J5OfflineBatchError error) {
        return new J5OfflineBatchException(error);
    }
}
