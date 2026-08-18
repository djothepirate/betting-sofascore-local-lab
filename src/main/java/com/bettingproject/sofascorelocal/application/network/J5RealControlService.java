package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlState;
import com.bettingproject.sofascorelocal.domain.provider.J5RealExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J5RealQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Service
public class J5RealControlService {

    public static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);
    public static final List<SofascoreEndpointType> ORDERED_ENDPOINTS = List.of(
            SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);

    private final Clock clock;
    private final Supplier<UUID> requestIdSupplier;
    private final IntSupplier confirmationCodeSupplier;
    private final Supplier<J5RealQualificationSnapshot> qualificationSupplier;

    private J5RealControlState state = J5RealControlState.LOCKED;
    private Instant changedAt;
    private UUID requestId;
    private String confirmationPhrase;
    private Instant preparedAt;
    private Instant expiresAt;
    private UUID canonicalEventId;
    private Long eventId;
    private final List<SofascoreEndpointType> completedEndpoints = new ArrayList<>();
    private String terminalCode;

    @Autowired
    public J5RealControlService(J5RealQualificationPolicy policy) {
        this(Clock.systemUTC(), UUID::randomUUID, new SecureRandom()::nextInt,
                Objects.requireNonNull(policy, "policy")::snapshot);
    }

    J5RealControlService(
            Clock clock,
            Supplier<UUID> requestIdSupplier,
            IntSupplier confirmationCodeSupplier,
            Supplier<J5RealQualificationSnapshot> qualificationSupplier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier");
        this.confirmationCodeSupplier = Objects.requireNonNull(
                confirmationCodeSupplier, "confirmationCodeSupplier");
        this.qualificationSupplier = Objects.requireNonNull(
                qualificationSupplier, "qualificationSupplier");
        changedAt = clock.instant();
    }

    public synchronized J5RealControlSnapshot snapshot() {
        expireIfNecessary(clock.instant());
        return toSnapshot();
    }

    public synchronized J5RealControlSnapshot prepare(
            UUID requestedCanonicalEventId,
            long requestedEventId) {
        Objects.requireNonNull(requestedCanonicalEventId, "requestedCanonicalEventId");
        Instant now = clock.instant();
        expireIfNecessary(now);
        if (state == J5RealControlState.AWAITING_CONFIRMATION
                || state == J5RealControlState.EXECUTING) {
            throw rejected(J5RealControlError.ACTIVE_CAMPAIGN_EXISTS);
        }
        if (state != J5RealControlState.LOCKED
                && state != J5RealControlState.COMPLETED_LOCKED) {
            throw rejected(J5RealControlError.TERMINAL_LOCK_REQUIRES_RESTART);
        }
        J5RealQualificationSnapshot qualification = qualificationSupplier.get();
        if (!qualification.available()) {
            throw rejected(J5RealControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        try {
            J5EventDataProviderRequest.requireEventId(requestedEventId);
        }
        catch (IllegalArgumentException exception) {
            throw rejected(J5RealControlError.EVENT_ID_NOT_ALLOWED);
        }
        if (!CanonicalEventIdentity.sofascore(requestedEventId).value()
                .equals(requestedCanonicalEventId)) {
            throw rejected(J5RealControlError.EVENT_ID_MISMATCH);
        }
        requestId = Objects.requireNonNull(requestIdSupplier.get(), "requestId");
        int code = Math.floorMod(confirmationCodeSupplier.getAsInt(), 1_000_000);
        confirmationPhrase = "CONFIRMER J5 REAL " + requestedEventId
                + " STATISTICS INCIDENTS LINEUPS " + "%06d".formatted(code);
        preparedAt = now;
        expiresAt = now.plus(CONFIRMATION_TTL);
        canonicalEventId = requestedCanonicalEventId;
        eventId = requestedEventId;
        completedEndpoints.clear();
        terminalCode = null;
        state = J5RealControlState.AWAITING_CONFIRMATION;
        changedAt = now;
        return toSnapshot();
    }

    public synchronized J5RealExecutionClaim confirmAndClaim(
            UUID requestedId,
            String confirmationText,
            boolean acknowledged) {
        Objects.requireNonNull(requestedId, "requestedId");
        Instant now = clock.instant();
        expireIfNecessary(now);
        if (state == J5RealControlState.EXPIRED_LOCKED) {
            throw rejected(J5RealControlError.CONFIRMATION_EXPIRED);
        }
        if (state != J5RealControlState.AWAITING_CONFIRMATION
                || requestId == null
                || canonicalEventId == null
                || eventId == null) {
            throw rejected(J5RealControlError.NO_PENDING_CAMPAIGN);
        }
        if (!requestId.equals(requestedId)) {
            throw rejected(J5RealControlError.REQUEST_ID_MISMATCH);
        }
        if (!acknowledged) {
            throw rejected(J5RealControlError.ACKNOWLEDGEMENT_REQUIRED);
        }
        if (!constantTimeEquals(confirmationPhrase, confirmationText)) {
            throw rejected(J5RealControlError.CONFIRMATION_TEXT_MISMATCH);
        }
        J5RealQualificationSnapshot qualification = qualificationSupplier.get();
        if (!qualification.available()) {
            lockFailed(now, "PROVIDER_TRANSPORT_UNAVAILABLE");
            throw rejected(J5RealControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        confirmationPhrase = null;
        expiresAt = null;
        state = J5RealControlState.EXECUTING;
        changedAt = now;
        return new J5RealExecutionClaim(
                requestId,
                qualification.providerOrigin(),
                canonicalEventId,
                eventId);
    }

    public synchronized boolean executionMayContinue(UUID requestedId) {
        return state == J5RealControlState.EXECUTING
                && requestId != null
                && requestId.equals(requestedId);
    }

    public synchronized void recordEndpointCompleted(
            UUID requestedId,
            SofascoreEndpointType endpointType) {
        requireExecuting(requestedId);
        Objects.requireNonNull(endpointType, "endpointType");
        if (completedEndpoints.contains(endpointType)) {
            throw rejected(J5RealControlError.ENDPOINT_ALREADY_COMPLETED);
        }
        if (completedEndpoints.size() >= ORDERED_ENDPOINTS.size()
                || ORDERED_ENDPOINTS.get(completedEndpoints.size()) != endpointType) {
            throw rejected(J5RealControlError.ENDPOINT_ORDER_INVALID);
        }
        completedEndpoints.add(endpointType);
        changedAt = clock.instant();
    }

    public synchronized J5RealControlSnapshot complete(UUID requestedId) {
        requireExecuting(requestedId);
        if (!completedEndpoints.equals(ORDERED_ENDPOINTS)) {
            throw rejected(J5RealControlError.ENDPOINT_ORDER_INVALID);
        }
        state = J5RealControlState.COMPLETED_LOCKED;
        terminalCode = "COMPLETED";
        changedAt = clock.instant();
        return toSnapshot();
    }

    public synchronized J5RealControlSnapshot fail(UUID requestedId, String code) {
        requireExecuting(requestedId);
        lockFailed(clock.instant(), requireSafeCode(code));
        return toSnapshot();
    }

    public synchronized J5RealControlSnapshot stop() {
        if (toSnapshot().terminal()) {
            return toSnapshot();
        }
        state = J5RealControlState.STOPPED_LOCKED;
        terminalCode = "OPERATOR_STOP";
        confirmationPhrase = null;
        expiresAt = null;
        changedAt = clock.instant();
        return toSnapshot();
    }

    private void requireExecuting(UUID requestedId) {
        Objects.requireNonNull(requestedId, "requestedId");
        if (requestId == null || !requestId.equals(requestedId)) {
            throw rejected(J5RealControlError.REQUEST_ID_MISMATCH);
        }
        if (state != J5RealControlState.EXECUTING) {
            throw rejected(J5RealControlError.EXECUTION_NOT_ACTIVE);
        }
    }

    private void expireIfNecessary(Instant now) {
        if (state == J5RealControlState.AWAITING_CONFIRMATION
                && expiresAt != null
                && !now.isBefore(expiresAt)) {
            state = J5RealControlState.EXPIRED_LOCKED;
            terminalCode = "CONFIRMATION_EXPIRED";
            confirmationPhrase = null;
            expiresAt = null;
            changedAt = now;
        }
    }

    private void lockFailed(Instant now, String code) {
        state = J5RealControlState.FAILED_LOCKED;
        terminalCode = requireSafeCode(code);
        confirmationPhrase = null;
        expiresAt = null;
        changedAt = now;
    }

    private J5RealControlSnapshot toSnapshot() {
        J5RealQualificationSnapshot qualification = qualificationSupplier.get();
        return new J5RealControlSnapshot(
                state,
                changedAt,
                requestId,
                confirmationPhrase,
                preparedAt,
                expiresAt,
                canonicalEventId,
                eventId,
                completedEndpoints,
                terminalCode,
                qualification.available(),
                qualification.blockers());
    }

    private static String requireSafeCode(String value) {
        String normalized = Objects.requireNonNull(value, "code").trim();
        if (normalized.isEmpty()
                || normalized.length() > 96
                || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("code must be a bounded safe identifier");
        }
        return normalized;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null || actual.length() > 160) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static J5RealControlException rejected(J5RealControlError error) {
        return new J5RealControlException(error);
    }
}
