package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J4EventDetailsQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1ControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1State;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Service
public class J4RealPhase1ControlService {

    public static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);

    private final Clock clock;
    private final Supplier<UUID> requestIdSupplier;
    private final IntSupplier confirmationCodeSupplier;
    private final Supplier<J4EventDetailsQualificationSnapshot> qualificationSupplier;

    private J4RealPhase1State state = J4RealPhase1State.LOCKED;
    private Instant changedAt;
    private UUID requestId;
    private String confirmationPhrase;
    private Instant preparedAt;
    private Instant expiresAt;
    private int completedEvents;
    private String terminalCode;

    @Autowired
    public J4RealPhase1ControlService(J4EventDetailsQualificationPolicy policy) {
        this(
                Clock.systemUTC(),
                UUID::randomUUID,
                new SecureRandom()::nextInt,
                Objects.requireNonNull(policy, "policy")::snapshot);
    }

    J4RealPhase1ControlService(
            Clock clock,
            Supplier<UUID> requestIdSupplier,
            IntSupplier confirmationCodeSupplier,
            Supplier<J4EventDetailsQualificationSnapshot> qualificationSupplier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier");
        this.confirmationCodeSupplier = Objects.requireNonNull(
                confirmationCodeSupplier, "confirmationCodeSupplier");
        this.qualificationSupplier = Objects.requireNonNull(
                qualificationSupplier, "qualificationSupplier");
        changedAt = clock.instant();
    }

    public synchronized J4RealPhase1ControlSnapshot snapshot() {
        expireIfNecessary(clock.instant());
        return toSnapshot();
    }

    public synchronized J4RealPhase1ControlSnapshot prepare() {
        Instant now = clock.instant();
        expireIfNecessary(now);
        if (state == J4RealPhase1State.AWAITING_CONFIRMATION
                || state == J4RealPhase1State.EXECUTING) {
            throw rejected(J4RealPhase1ControlError.ACTIVE_CAMPAIGN_EXISTS);
        }
        J4EventDetailsQualificationSnapshot qualification = qualificationSupplier.get();
        if (!qualification.available()) {
            throw rejected(J4RealPhase1ControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        requestId = Objects.requireNonNull(requestIdSupplier.get(), "requestId");
        int code = Math.floorMod(confirmationCodeSupplier.getAsInt(), 1_000_000);
        confirmationPhrase = "CONFIRMER EVENT_DETAILS 16386245 16421052 "
                + "%06d".formatted(code);
        preparedAt = now;
        expiresAt = now.plus(CONFIRMATION_TTL);
        completedEvents = 0;
        terminalCode = null;
        state = J4RealPhase1State.AWAITING_CONFIRMATION;
        changedAt = now;
        return toSnapshot();
    }

    public synchronized J4RealPhase1ExecutionClaim confirmAndClaim(
            UUID requestedId,
            String confirmationText,
            boolean acknowledged) {
        Objects.requireNonNull(requestedId, "requestedId");
        Instant now = clock.instant();
        expireIfNecessary(now);
        if (state == J4RealPhase1State.EXPIRED_LOCKED) {
            throw rejected(J4RealPhase1ControlError.CONFIRMATION_EXPIRED);
        }
        if (state != J4RealPhase1State.AWAITING_CONFIRMATION || requestId == null) {
            throw rejected(J4RealPhase1ControlError.NO_PENDING_CAMPAIGN);
        }
        if (!requestId.equals(requestedId)) {
            throw rejected(J4RealPhase1ControlError.REQUEST_ID_MISMATCH);
        }
        if (!acknowledged) {
            throw rejected(J4RealPhase1ControlError.ACKNOWLEDGEMENT_REQUIRED);
        }
        if (!constantTimeEquals(confirmationPhrase, confirmationText)) {
            throw rejected(J4RealPhase1ControlError.CONFIRMATION_TEXT_MISMATCH);
        }
        J4EventDetailsQualificationSnapshot qualification = qualificationSupplier.get();
        if (!qualification.available()) {
            lockFailed(now, "PROVIDER_TRANSPORT_UNAVAILABLE");
            throw rejected(J4RealPhase1ControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        confirmationPhrase = null;
        state = J4RealPhase1State.EXECUTING;
        changedAt = now;
        return new J4RealPhase1ExecutionClaim(requestId, qualification.providerOrigin());
    }

    public synchronized boolean executionMayContinue(UUID requestedId) {
        return state == J4RealPhase1State.EXECUTING
                && requestId != null
                && requestId.equals(requestedId);
    }

    public synchronized void recordEventCompleted(UUID requestedId, long eventId) {
        requireExecuting(requestedId);
        long expectedEventId = EventDetailsProviderRequest.PHASE_1_EVENT_IDS.get(completedEvents);
        if (eventId != expectedEventId) {
            throw rejected(J4RealPhase1ControlError.EVENT_SEQUENCE_INVALID);
        }
        completedEvents++;
        changedAt = clock.instant();
    }

    public synchronized J4RealPhase1ControlSnapshot complete(UUID requestedId) {
        requireExecuting(requestedId);
        if (completedEvents != EventDetailsProviderRequest.PHASE_1_EVENT_IDS.size()) {
            throw rejected(J4RealPhase1ControlError.EVENT_SEQUENCE_INVALID);
        }
        state = J4RealPhase1State.COMPLETED_LOCKED;
        terminalCode = "COMPLETED";
        changedAt = clock.instant();
        clearActiveConfirmation();
        return toSnapshot();
    }

    public synchronized J4RealPhase1ControlSnapshot fail(
            UUID requestedId,
            String code) {
        requireExecuting(requestedId);
        lockFailed(clock.instant(), requireSafeCode(code));
        return toSnapshot();
    }

    public synchronized J4RealPhase1ControlSnapshot stop() {
        state = J4RealPhase1State.STOPPED_LOCKED;
        terminalCode = "OPERATOR_STOP";
        changedAt = clock.instant();
        clearActiveConfirmation();
        return toSnapshot();
    }

    private void requireExecuting(UUID requestedId) {
        Objects.requireNonNull(requestedId, "requestedId");
        if (requestId == null || !requestId.equals(requestedId)) {
            throw rejected(J4RealPhase1ControlError.REQUEST_ID_MISMATCH);
        }
        if (state != J4RealPhase1State.EXECUTING) {
            throw rejected(J4RealPhase1ControlError.EXECUTION_NOT_ACTIVE);
        }
    }

    private void expireIfNecessary(Instant now) {
        if (state == J4RealPhase1State.AWAITING_CONFIRMATION
                && expiresAt != null
                && !now.isBefore(expiresAt)) {
            state = J4RealPhase1State.EXPIRED_LOCKED;
            terminalCode = "CONFIRMATION_EXPIRED";
            changedAt = now;
            clearActiveConfirmation();
        }
    }

    private void lockFailed(Instant now, String code) {
        state = J4RealPhase1State.FAILED_LOCKED;
        terminalCode = requireSafeCode(code);
        changedAt = now;
        clearActiveConfirmation();
    }

    private void clearActiveConfirmation() {
        confirmationPhrase = null;
        expiresAt = null;
    }

    private J4RealPhase1ControlSnapshot toSnapshot() {
        J4EventDetailsQualificationSnapshot qualification = qualificationSupplier.get();
        return new J4RealPhase1ControlSnapshot(
                state,
                changedAt,
                requestId,
                confirmationPhrase,
                preparedAt,
                expiresAt,
                completedEvents,
                terminalCode,
                qualification.available(),
                qualification.blockers());
    }

    private static String requireSafeCode(String value) {
        Objects.requireNonNull(value, "code");
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > 96
                || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("code must be a bounded safe identifier");
        }
        return normalized;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null || actual.length() > 128) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static J4RealPhase1ControlException rejected(J4RealPhase1ControlError error) {
        return new J4RealPhase1ControlException(error);
    }
}
