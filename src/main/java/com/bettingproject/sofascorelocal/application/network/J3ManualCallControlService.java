package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3LocalJsonImportExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.J3ProviderQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.function.Consumer;
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
    private final Supplier<J3ProviderQualificationSnapshot> providerAvailabilitySupplier;
    private final Supplier<J3ProviderQualificationSnapshot> localImportAvailabilitySupplier;
    private final J3NetworkCircuit circuit;

    private boolean globalStopActive = true;
    private Intent intent;

    @Autowired
    public J3ManualCallControlService(J3ProviderQualificationPolicy providerPolicy) {
        this(
                Clock.systemUTC(),
                UUID::randomUUID,
                new SecureRandom()::nextInt,
                Objects.requireNonNull(providerPolicy, "providerPolicy")::snapshot,
                providerPolicy::localImportSnapshot);
    }

    J3ManualCallControlService(
            Clock clock,
            Supplier<UUID> requestIdSupplier,
            IntSupplier confirmationCodeSupplier) {
        this(
                clock,
                requestIdSupplier,
                confirmationCodeSupplier,
                () -> J3ProviderQualificationSnapshot.blocked(PROVIDER_BLOCKERS),
                () -> J3ProviderQualificationSnapshot.blocked(PROVIDER_BLOCKERS));
    }

    J3ManualCallControlService(
            Clock clock,
            Supplier<UUID> requestIdSupplier,
            IntSupplier confirmationCodeSupplier,
            Supplier<J3ProviderQualificationSnapshot> providerAvailabilitySupplier) {
        this(
                clock,
                requestIdSupplier,
                confirmationCodeSupplier,
                providerAvailabilitySupplier,
                providerAvailabilitySupplier);
    }

    J3ManualCallControlService(
            Clock clock,
            Supplier<UUID> requestIdSupplier,
            IntSupplier confirmationCodeSupplier,
            Supplier<J3ProviderQualificationSnapshot> providerAvailabilitySupplier,
            Supplier<J3ProviderQualificationSnapshot> localImportAvailabilitySupplier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier");
        this.confirmationCodeSupplier = Objects.requireNonNull(
                confirmationCodeSupplier,
                "confirmationCodeSupplier");
        this.providerAvailabilitySupplier = Objects.requireNonNull(
                providerAvailabilitySupplier,
                "providerAvailabilitySupplier");
        this.localImportAvailabilitySupplier = Objects.requireNonNull(
                localImportAvailabilitySupplier,
                "localImportAvailabilitySupplier");
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
        if (intent != null && !isActive(intent.state())) {
            intent = null;
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
        if (intent != null && isActive(intent.state())) {
            throw rejected(J3ManualCallControlError.ACTIVE_INTENT_ALREADY_EXISTS);
        }

        UUID requestId = Objects.requireNonNull(requestIdSupplier.get(), "requestId");
        int code = Math.floorMod(confirmationCodeSupplier.getAsInt(), 1_000_000);
        int firstPage = ScheduledEventsProviderPageRequest.FIRST_PAGE;
        String phrase = "CONFIRMER SCHEDULED_EVENTS " + date
                + " PAGINATION DYNAMIQUE MAX "
                + ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE
                + " " + "%06d".formatted(code);
        intent = new Intent(
                requestId,
                date,
                SofascoreEndpointType.SCHEDULED_EVENTS.name()
                        + "|date=" + date + "|pagination=has-next-page|max="
                        + ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE,
                firstPage,
                J3ManualCallIntentState.AWAITING_CONFIRMATION,
                phrase,
                now,
                now.plus(CONFIRMATION_TTL),
                null,
                0,
                null,
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

        J3ProviderQualificationSnapshot qualification = localImportAvailabilitySupplier.get();
        intent = new Intent(
                intent.requestId(),
                intent.date(),
                intent.requestKey(),
                intent.firstPage(),
                qualification.available()
                        ? J3ManualCallIntentState.CONFIRMED_READY
                        : J3ManualCallIntentState.CONFIRMED_BLOCKED,
                null,
                intent.preparedAt(),
                intent.expiresAt(),
                now,
                intent.completedPages(),
                null,
                null);
        return toSnapshot();
    }

    public synchronized J3ManualCallExecutionClaim claimExecution(UUID requestId) {
        requireClaimableIntent(requestId);
        J3ProviderQualificationSnapshot qualification = providerAvailabilitySupplier.get();
        if (!qualification.available()) {
            throw rejected(J3ManualCallControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        beginExecution();
        return new J3ManualCallExecutionClaim(
                intent.requestId(),
                intent.date(),
                qualification.providerOrigin(),
                intent.firstPage());
    }

    public synchronized J3LocalJsonImportExecutionClaim claimLocalImportExecution(
            UUID requestId) {
        requireClaimableIntent(requestId);
        J3ProviderQualificationSnapshot qualification = localImportAvailabilitySupplier.get();
        if (!qualification.available()) {
            throw rejected(J3ManualCallControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        beginExecution();
        return new J3LocalJsonImportExecutionClaim(
                intent.requestId(),
                intent.date(),
                intent.firstPage());
    }

    private void requireClaimableIntent(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        requireMatchingIntent(requestId);
        if (intent.state() == J3ManualCallIntentState.EXECUTING
                || intent.state() == J3ManualCallIntentState.COMPLETED
                || intent.state() == J3ManualCallIntentState.FAILED) {
            throw rejected(J3ManualCallControlError.EXECUTION_ALREADY_STARTED);
        }
        requireReadyForIntent();
        if (intent.state() != J3ManualCallIntentState.CONFIRMED_READY) {
            throw rejected(J3ManualCallControlError.INTENT_NOT_READY);
        }
    }

    private void beginExecution() {
        intent = copyWithState(
                intent,
                J3ManualCallIntentState.EXECUTING,
                intent.completedPages(),
                null,
                null);
    }

    public synchronized boolean executionMayContinue(UUID requestId) {
        return !globalStopActive
                && circuit.snapshot().state() == J3CircuitState.CLOSED
                && intent != null
                && intent.requestId().equals(requestId)
                && intent.state() == J3ManualCallIntentState.EXECUTING;
    }

    public synchronized void recordPageCompleted(UUID requestId, int page) {
        requireMatchingExecutingIntent(requestId);
        if (page != intent.completedPages() + 1
                || page > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
            throw rejected(J3ManualCallControlError.PAGE_SEQUENCE_INVALID);
        }
        intent = copyWithState(
                intent,
                J3ManualCallIntentState.EXECUTING,
                page,
                null,
                null);
    }

    public synchronized J3ManualCallControlSnapshot completeExecution(UUID requestId) {
        requireMatchingExecutingIntent(requestId);
        if (intent.completedPages() < ScheduledEventsProviderPageRequest.FIRST_PAGE) {
            throw rejected(J3ManualCallControlError.PAGE_SEQUENCE_INVALID);
        }
        intent = copyWithState(
                intent,
                J3ManualCallIntentState.COMPLETED,
                intent.completedPages(),
                null,
                null);
        return toSnapshot();
    }

    public synchronized J3ManualCallControlSnapshot failExecution(
            UUID requestId,
            int failedPage,
            String terminalCode) {
        requireMatchingExecutingIntent(requestId);
        if (failedPage != intent.completedPages() + 1
                || failedPage
                        > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE + 1) {
            throw rejected(J3ManualCallControlError.PAGE_SEQUENCE_INVALID);
        }
        intent = copyWithState(
                intent,
                J3ManualCallIntentState.FAILED,
                intent.completedPages(),
                failedPage,
                requireSafeCode(terminalCode));
        return toSnapshot();
    }

    public synchronized J3ManualCallControlSnapshot lockAfterCollection(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        requireMatchingIntent(requestId);
        if (intent.state() != J3ManualCallIntentState.COMPLETED
                && intent.state() != J3ManualCallIntentState.FAILED) {
            throw rejected(J3ManualCallControlError.EXECUTION_NOT_ACTIVE);
        }
        globalStopActive = true;
        circuit.lockAfterCollection(clock.instant());
        return toSnapshot();
    }

    public synchronized J3ManualCallControlSnapshot stopGlobally() {
        return stopGlobally(ignored -> { });
    }

    public synchronized J3ManualCallControlSnapshot stopGlobally(
            Consumer<UUID> beforeLock) {
        Objects.requireNonNull(beforeLock, "beforeLock");
        try {
            if (intent != null) {
                beforeLock.accept(intent.requestId());
            }
        }
        finally {
            lockByOperator();
        }
        return toSnapshot();
    }

    private void lockByOperator() {
        Instant now = clock.instant();
        globalStopActive = true;
        circuit.stopByOperator(now);
        if (intent != null && isActive(intent.state())) {
            intent = new Intent(
                    intent.requestId(),
                    intent.date(),
                    intent.requestKey(),
                    intent.firstPage(),
                    J3ManualCallIntentState.CANCELLED_BY_GLOBAL_STOP,
                    null,
                    intent.preparedAt(),
                    intent.expiresAt(),
                    null,
                    intent.completedPages(),
                    null,
                    null);
        }
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
                    intent.firstPage(),
                    J3ManualCallIntentState.EXPIRED,
                    null,
                    intent.preparedAt(),
                    intent.expiresAt(),
                    null,
                    intent.completedPages(),
                    null,
                    null);
        }
    }

    private J3ManualCallControlSnapshot toSnapshot() {
        J3CircuitSnapshot circuitSnapshot = circuit.snapshot();
        J3ProviderQualificationSnapshot qualification = providerAvailabilitySupplier.get();
        J3ProviderQualificationSnapshot localImportQualification =
                localImportAvailabilitySupplier.get();
        return new J3ManualCallControlSnapshot(
                globalStopActive,
                circuitSnapshot.state(),
                circuitSnapshot.reason(),
                circuitSnapshot.changedAt(),
                circuitSnapshot.retryNotBefore(),
                LocalDate.now(clock),
                intent == null ? null : intent.toSnapshot(),
                qualification.available(),
                localImportQualification.available(),
                qualification.blockers());
    }

    J3NetworkCircuit circuit() {
        return circuit;
    }

    private void requireMatchingIntent(UUID requestId) {
        if (intent == null) {
            throw rejected(J3ManualCallControlError.NO_PENDING_INTENT);
        }
        if (!intent.requestId().equals(requestId)) {
            throw rejected(J3ManualCallControlError.REQUEST_ID_MISMATCH);
        }
    }

    private void requireMatchingExecutingIntent(UUID requestId) {
        requireMatchingIntent(requestId);
        if (intent.state() != J3ManualCallIntentState.EXECUTING) {
            throw rejected(J3ManualCallControlError.EXECUTION_NOT_ACTIVE);
        }
    }

    private static boolean isActive(J3ManualCallIntentState state) {
        return state == J3ManualCallIntentState.AWAITING_CONFIRMATION
                || state == J3ManualCallIntentState.CONFIRMED_BLOCKED
                || state == J3ManualCallIntentState.CONFIRMED_READY
                || state == J3ManualCallIntentState.EXECUTING;
    }

    private static Intent copyWithState(
            Intent source,
            J3ManualCallIntentState state,
            int completedPages,
            Integer failedPage,
            String terminalCode) {
        return new Intent(
                source.requestId(),
                source.date(),
                source.requestKey(),
                source.firstPage(),
                state,
                null,
                source.preparedAt(),
                source.expiresAt(),
                source.confirmedAt(),
                completedPages,
                failedPage,
                terminalCode);
    }

    private static String requireSafeCode(String value) {
        Objects.requireNonNull(value, "terminalCode");
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > 96
                || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("terminalCode must be a safe code");
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

    private static J3ManualCallControlException rejected(J3ManualCallControlError error) {
        return new J3ManualCallControlException(error);
    }

    private record Intent(
            UUID requestId,
            LocalDate date,
            String requestKey,
            int firstPage,
            J3ManualCallIntentState state,
            String confirmationPhrase,
            Instant preparedAt,
            Instant expiresAt,
            Instant confirmedAt,
            int completedPages,
            Integer failedPage,
            String terminalCode) {

        private J3ManualCallIntentSnapshot toSnapshot() {
            return new J3ManualCallIntentSnapshot(
                    requestId,
                    date,
                    requestKey,
                    firstPage,
                    state,
                    confirmationPhrase,
                    preparedAt,
                    expiresAt,
                    confirmedAt,
                    completedPages,
                    failedPage,
                    terminalCode);
        }
    }
}
