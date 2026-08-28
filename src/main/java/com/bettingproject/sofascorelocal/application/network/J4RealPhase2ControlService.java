package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.J4EventDetailsQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2ControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2State;
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
import java.util.function.Consumer;

@Service
public class J4RealPhase2ControlService {

    public static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);

    private final Clock clock;
    private final Supplier<UUID> requestIdSupplier;
    private final IntSupplier confirmationCodeSupplier;
    private final Supplier<J4EventDetailsQualificationSnapshot> qualificationSupplier;

    private J4RealPhase2State state = J4RealPhase2State.LOCKED;
    private Instant changedAt;
    private UUID requestId;
    private String confirmationPhrase;
    private Instant preparedAt;
    private Instant expiresAt;
    private UUID canonicalEventId;
    private Long eventId;
    private boolean eventCompleted;
    private String terminalCode;

    @Autowired
    public J4RealPhase2ControlService(J4EventDetailsPhase2QualificationPolicy policy) {
        this(
                Clock.systemUTC(),
                UUID::randomUUID,
                new SecureRandom()::nextInt,
                Objects.requireNonNull(policy, "policy")::snapshot);
    }

    J4RealPhase2ControlService(
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

    public synchronized J4RealPhase2ControlSnapshot snapshot() {
        expireIfNecessary(clock.instant());
        return toSnapshot();
    }

    public synchronized J4RealPhase2ControlSnapshot prepare(
            CanonicalEventIdentity selection) {
        Objects.requireNonNull(selection, "selection");
        Instant now = clock.instant();
        expireIfNecessary(now);
        if (state == J4RealPhase2State.AWAITING_CONFIRMATION
                || state == J4RealPhase2State.EXECUTING) {
            throw rejected(J4RealPhase2ControlError.ACTIVE_CAMPAIGN_EXISTS);
        }
        if (state == J4RealPhase2State.FAILED_LOCKED
                || state == J4RealPhase2State.STOPPED_LOCKED) {
            throw rejected(J4RealPhase2ControlError.TERMINAL_LOCK_REQUIRES_RESTART);
        }
        J4EventDetailsQualificationSnapshot qualification = qualificationSupplier.get();
        if (!qualification.available()) {
            throw rejected(J4RealPhase2ControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        try {
            if (!CanonicalEventIdentity.SOFASCORE.equals(selection.provider())) {
                throw new IllegalArgumentException("selection must belong to SOFASCORE");
            }
            EventDetailsProviderRequest.requirePhase2EventId(selection.providerEventId());
        }
        catch (IllegalArgumentException exception) {
            throw rejected(J4RealPhase2ControlError.EVENT_ID_NOT_ALLOWED);
        }
        requestId = Objects.requireNonNull(requestIdSupplier.get(), "requestId");
        int code = Math.floorMod(confirmationCodeSupplier.getAsInt(), 1_000_000);
        confirmationPhrase = "CONFIRMER EVENT_DETAILS " + selection.value() + " "
                + selection.providerEventId() + " "
                + "%06d".formatted(code);
        preparedAt = now;
        expiresAt = now.plus(CONFIRMATION_TTL);
        canonicalEventId = selection.value();
        eventId = selection.providerEventId();
        eventCompleted = false;
        terminalCode = null;
        state = J4RealPhase2State.AWAITING_CONFIRMATION;
        changedAt = now;
        return toSnapshot();
    }

    public synchronized J4RealPhase2ExecutionClaim confirmAndClaim(
            UUID requestedId,
            String confirmationText,
            boolean acknowledged) {
        Objects.requireNonNull(requestedId, "requestedId");
        Instant now = clock.instant();
        expireIfNecessary(now);
        if (state == J4RealPhase2State.EXPIRED_LOCKED) {
            throw rejected(J4RealPhase2ControlError.CONFIRMATION_EXPIRED);
        }
        if (state != J4RealPhase2State.AWAITING_CONFIRMATION
                || requestId == null
                || canonicalEventId == null
                || eventId == null) {
            throw rejected(J4RealPhase2ControlError.NO_PENDING_CAMPAIGN);
        }
        if (!requestId.equals(requestedId)) {
            throw rejected(J4RealPhase2ControlError.REQUEST_ID_MISMATCH);
        }
        if (!acknowledged) {
            throw rejected(J4RealPhase2ControlError.ACKNOWLEDGEMENT_REQUIRED);
        }
        if (!constantTimeEquals(confirmationPhrase, confirmationText)) {
            throw rejected(J4RealPhase2ControlError.CONFIRMATION_TEXT_MISMATCH);
        }
        J4EventDetailsQualificationSnapshot qualification = qualificationSupplier.get();
        if (!qualification.available()) {
            lockFailed(now, "PROVIDER_TRANSPORT_UNAVAILABLE");
            throw rejected(J4RealPhase2ControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        confirmationPhrase = null;
        state = J4RealPhase2State.EXECUTING;
        changedAt = now;
        return new J4RealPhase2ExecutionClaim(
                requestId,
                qualification.providerOrigin(),
                canonicalEventId,
                eventId);
    }

    public synchronized boolean executionMayContinue(UUID requestedId) {
        return state == J4RealPhase2State.EXECUTING
                && requestId != null
                && requestId.equals(requestedId);
    }

    public synchronized void recordEventCompleted(UUID requestedId, long completedEventId) {
        requireExecuting(requestedId);
        if (eventId == null || eventId != completedEventId) {
            throw rejected(J4RealPhase2ControlError.EVENT_ID_MISMATCH);
        }
        if (eventCompleted) {
            throw rejected(J4RealPhase2ControlError.EVENT_ALREADY_COMPLETED);
        }
        eventCompleted = true;
        changedAt = clock.instant();
    }

    public synchronized J4RealPhase2ControlSnapshot complete(UUID requestedId) {
        requireExecuting(requestedId);
        if (!eventCompleted) {
            throw rejected(J4RealPhase2ControlError.EVENT_ID_MISMATCH);
        }
        state = J4RealPhase2State.COMPLETED_LOCKED;
        terminalCode = "COMPLETED";
        changedAt = clock.instant();
        clearActiveConfirmation();
        return toSnapshot();
    }

    public synchronized J4RealPhase2ControlSnapshot completeUnavailable(UUID requestedId) {
        requireExecuting(requestedId);
        if (!eventCompleted) {
            throw rejected(J4RealPhase2ControlError.EVENT_ID_MISMATCH);
        }
        state = J4RealPhase2State.COMPLETED_LOCKED;
        terminalCode = "COMPLETED_UNAVAILABLE";
        changedAt = clock.instant();
        clearActiveConfirmation();
        return toSnapshot();
    }

    public synchronized J4RealPhase2ControlSnapshot fail(UUID requestedId, String code) {
        requireExecuting(requestedId);
        lockFailed(clock.instant(), requireSafeCode(code));
        return toSnapshot();
    }

    public synchronized J4RealPhase2ControlSnapshot stop() {
        return stop(ignored -> { });
    }

    public synchronized J4RealPhase2ControlSnapshot stop(Consumer<UUID> beforeLock) {
        Objects.requireNonNull(beforeLock, "beforeLock");
        try {
            if (requestId != null
                    && (state == J4RealPhase2State.AWAITING_CONFIRMATION
                            || state == J4RealPhase2State.EXECUTING)) {
                beforeLock.accept(requestId);
            }
        }
        finally {
            state = J4RealPhase2State.STOPPED_LOCKED;
            terminalCode = "OPERATOR_STOP";
            changedAt = clock.instant();
            clearActiveConfirmation();
        }
        return toSnapshot();
    }

    private void requireExecuting(UUID requestedId) {
        Objects.requireNonNull(requestedId, "requestedId");
        if (requestId == null || !requestId.equals(requestedId)) {
            throw rejected(J4RealPhase2ControlError.REQUEST_ID_MISMATCH);
        }
        if (state != J4RealPhase2State.EXECUTING) {
            throw rejected(J4RealPhase2ControlError.EXECUTION_NOT_ACTIVE);
        }
    }

    private void expireIfNecessary(Instant now) {
        if (state == J4RealPhase2State.AWAITING_CONFIRMATION
                && expiresAt != null
                && !now.isBefore(expiresAt)) {
            state = J4RealPhase2State.EXPIRED_LOCKED;
            terminalCode = "CONFIRMATION_EXPIRED";
            changedAt = now;
            clearActiveConfirmation();
        }
    }

    private void lockFailed(Instant now, String code) {
        state = J4RealPhase2State.FAILED_LOCKED;
        terminalCode = requireSafeCode(code);
        changedAt = now;
        clearActiveConfirmation();
    }

    private void clearActiveConfirmation() {
        confirmationPhrase = null;
        expiresAt = null;
    }

    private J4RealPhase2ControlSnapshot toSnapshot() {
        J4EventDetailsQualificationSnapshot qualification = qualificationSupplier.get();
        return new J4RealPhase2ControlSnapshot(
                state,
                changedAt,
                requestId,
                confirmationPhrase,
                preparedAt,
                expiresAt,
                canonicalEventId,
                eventId,
                eventCompleted,
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

    private static J4RealPhase2ControlException rejected(J4RealPhase2ControlError error) {
        return new J4RealPhase2ControlException(error);
    }
}
