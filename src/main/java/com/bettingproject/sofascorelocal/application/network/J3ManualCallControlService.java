package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Service
public class J3ManualCallControlService {

    public static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);

    private static final List<String> PROVIDER_BLOCKERS = List.of(
            "REAL_ENDPOINT_URI_ABSENT",
            "REAL_CALL_NOT_AUTHORIZED",
            "CONNECTOR_GATE_LOCKED",
            "CATALOG_NOT_CALLABLE",
            "LIVE_PROFILE_BLOCKED");

    private final Clock clock;
    private final Supplier<UUID> requestIdSupplier;
    private final IntSupplier confirmationCodeSupplier;
    private final J3NetworkCircuit circuit;

    private boolean globalStopActive = true;
    private Intent intent;

    public J3ManualCallControlService() {
        this(
                Clock.systemUTC(),
                UUID::randomUUID,
                new SecureRandom()::nextInt);
    }

    J3ManualCallControlService(
            Clock clock,
            Supplier<UUID> requestIdSupplier,
            IntSupplier confirmationCodeSupplier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier");
        this.confirmationCodeSupplier = Objects.requireNonNull(
                confirmationCodeSupplier,
                "confirmationCodeSupplier");
        circuit = J3NetworkCircuit.lockedAt(clock.instant());
    }

    public synchronized J3ManualCallControlSnapshot snapshot() {
        Instant now = clock.instant();
        expireIntentIfNecessary(now);
        return toSnapshot();
    }

    public synchronized J3ManualCallControlSnapshot rearmAfterGlobalStop() {
        if (!globalStopActive) {
            throw rejected(J3ManualCallControlError.GLOBAL_STOP_ALREADY_CLEARED);
        }
        globalStopActive = false;
        return toSnapshot();
    }

    public synchronized J3ManualCallControlSnapshot activateByOperator() {
        Instant now = clock.instant();
        if (globalStopActive) {
            throw rejected(J3ManualCallControlError.GLOBAL_STOP_ACTIVE);
        }
        if (circuit.snapshot().state() == J3CircuitState.CLOSED) {
            throw rejected(J3ManualCallControlError.CIRCUIT_ALREADY_ACTIVATED);
        }
        if (circuit.snapshot().state() != J3CircuitState.LOCKED) {
            throw rejected(J3ManualCallControlError.CIRCUIT_NOT_CLOSED);
        }
        circuit.activateByOperator(now);
        return toSnapshot();
    }

    public synchronized J3ManualCallControlSnapshot prepare(LocalDate date) {
        Objects.requireNonNull(date, "date");
        Instant now = clock.instant();
        expireIntentIfNecessary(now);
        requireReadyForIntent();
        if (intent != null
                && (intent.state() == J3ManualCallIntentState.AWAITING_CONFIRMATION
                || intent.state() == J3ManualCallIntentState.CONFIRMED_BLOCKED)) {
            throw rejected(J3ManualCallControlError.ACTIVE_INTENT_ALREADY_EXISTS);
        }

        UUID requestId = Objects.requireNonNull(requestIdSupplier.get(), "requestId");
        int code = Math.floorMod(confirmationCodeSupplier.getAsInt(), 1_000_000);
        String phrase = "CONFIRMER SCHEDULED_EVENTS " + date + " " + "%06d".formatted(code);
        intent = new Intent(
                requestId,
                date,
                SofascoreEndpointType.SCHEDULED_EVENTS.name() + "|date=" + date,
                J3ManualCallIntentState.AWAITING_CONFIRMATION,
                phrase,
                now,
                now.plus(CONFIRMATION_TTL),
                null);
        return toSnapshot();
    }

    public synchronized J3ManualCallControlSnapshot confirm(
            UUID requestId,
            String confirmationText,
            boolean acknowledged) {
        Objects.requireNonNull(requestId, "requestId");
        Instant now = clock.instant();
        expireIntentIfNecessary(now);
        requireReadyForIntent();
        if (intent == null) {
            throw rejected(J3ManualCallControlError.NO_PENDING_INTENT);
        }
        if (!intent.requestId().equals(requestId)) {
            throw rejected(J3ManualCallControlError.REQUEST_ID_MISMATCH);
        }
        if (intent.state() == J3ManualCallIntentState.EXPIRED) {
            throw rejected(J3ManualCallControlError.CONFIRMATION_EXPIRED);
        }
        if (intent.state() == J3ManualCallIntentState.CONFIRMED_BLOCKED) {
            throw rejected(J3ManualCallControlError.INTENT_ALREADY_CONFIRMED);
        }
        if (intent.state() != J3ManualCallIntentState.AWAITING_CONFIRMATION) {
            throw rejected(J3ManualCallControlError.NO_PENDING_INTENT);
        }
        if (!acknowledged) {
            throw rejected(J3ManualCallControlError.ACKNOWLEDGEMENT_REQUIRED);
        }
        if (!constantTimeEquals(intent.confirmationPhrase(), confirmationText)) {
            throw rejected(J3ManualCallControlError.CONFIRMATION_TEXT_MISMATCH);
        }

        intent = new Intent(
                intent.requestId(),
                intent.date(),
                intent.requestKey(),
                J3ManualCallIntentState.CONFIRMED_BLOCKED,
                null,
                intent.preparedAt(),
                intent.expiresAt(),
                now);
        return toSnapshot();
    }

    public synchronized J3ManualCallControlSnapshot stopGlobally() {
        Instant now = clock.instant();
        globalStopActive = true;
        circuit.stopByOperator(now);
        if (intent != null
                && (intent.state() == J3ManualCallIntentState.AWAITING_CONFIRMATION
                || intent.state() == J3ManualCallIntentState.CONFIRMED_BLOCKED)) {
            intent = new Intent(
                    intent.requestId(),
                    intent.date(),
                    intent.requestKey(),
                    J3ManualCallIntentState.CANCELLED_BY_GLOBAL_STOP,
                    null,
                    intent.preparedAt(),
                    intent.expiresAt(),
                    null);
        }
        return toSnapshot();
    }

    private void requireReadyForIntent() {
        if (globalStopActive) {
            throw rejected(J3ManualCallControlError.GLOBAL_STOP_ACTIVE);
        }
        if (circuit.snapshot().state() != J3CircuitState.CLOSED) {
            throw rejected(J3ManualCallControlError.CIRCUIT_NOT_CLOSED);
        }
    }

    private void expireIntentIfNecessary(Instant now) {
        if (intent != null
                && intent.state() == J3ManualCallIntentState.AWAITING_CONFIRMATION
                && !now.isBefore(intent.expiresAt())) {
            intent = new Intent(
                    intent.requestId(),
                    intent.date(),
                    intent.requestKey(),
                    J3ManualCallIntentState.EXPIRED,
                    null,
                    intent.preparedAt(),
                    intent.expiresAt(),
                    null);
        }
    }

    private J3ManualCallControlSnapshot toSnapshot() {
        J3CircuitSnapshot circuitSnapshot = circuit.snapshot();
        return new J3ManualCallControlSnapshot(
                globalStopActive,
                circuitSnapshot.state(),
                circuitSnapshot.reason(),
                circuitSnapshot.changedAt(),
                circuitSnapshot.retryNotBefore(),
                LocalDate.now(clock),
                intent == null ? null : intent.toSnapshot(),
                false,
                PROVIDER_BLOCKERS);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null || actual.length() > 128) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static J3ManualCallControlException rejected(J3ManualCallControlError error) {
        return new J3ManualCallControlException(error);
    }

    private record Intent(
            UUID requestId,
            LocalDate date,
            String requestKey,
            J3ManualCallIntentState state,
            String confirmationPhrase,
            Instant preparedAt,
            Instant expiresAt,
            Instant confirmedAt) {

        private J3ManualCallIntentSnapshot toSnapshot() {
            return new J3ManualCallIntentSnapshot(
                    requestId,
                    date,
                    requestKey,
                    state,
                    confirmationPhrase,
                    preparedAt,
                    expiresAt,
                    confirmedAt);
        }
    }
}
