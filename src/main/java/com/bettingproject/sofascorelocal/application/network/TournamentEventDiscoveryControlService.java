package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryLocalImportClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryState;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalog;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
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
public class TournamentEventDiscoveryControlService {

    public static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);

    private final Clock clock;
    private final Supplier<UUID> requestIdSupplier;
    private final IntSupplier confirmationCodeSupplier;
    private final Supplier<TournamentEventDiscoveryQualificationSnapshot> qualificationSupplier;
    private final Supplier<TournamentEventDiscoveryQualificationSnapshot>
            localImportQualificationSupplier;
    private final J3TournamentCatalogService catalogService;

    private TournamentEventDiscoveryState state = TournamentEventDiscoveryState.LOCKED;
    private Instant changedAt;
    private UUID requestId;
    private String confirmationPhrase;
    private Instant preparedAt;
    private Instant expiresAt;
    private LocalDate collectionDate;
    private UUID sourceCollectionId;
    private J3TournamentCatalogOption selection;
    private String terminalCode;

    @Autowired
    public TournamentEventDiscoveryControlService(
            TournamentEventDiscoveryQualificationPolicy policy,
            J3TournamentCatalogService catalogService) {
        this(
                Clock.systemUTC(),
                UUID::randomUUID,
                new SecureRandom()::nextInt,
                Objects.requireNonNull(policy, "policy")::snapshot,
                policy::localImportSnapshot,
                catalogService);
    }

    TournamentEventDiscoveryControlService(
            Clock clock,
            Supplier<UUID> requestIdSupplier,
            IntSupplier confirmationCodeSupplier,
            Supplier<TournamentEventDiscoveryQualificationSnapshot> qualificationSupplier,
            J3TournamentCatalogService catalogService) {
        this(
                clock,
                requestIdSupplier,
                confirmationCodeSupplier,
                qualificationSupplier,
                qualificationSupplier,
                catalogService);
    }

    TournamentEventDiscoveryControlService(
            Clock clock,
            Supplier<UUID> requestIdSupplier,
            IntSupplier confirmationCodeSupplier,
            Supplier<TournamentEventDiscoveryQualificationSnapshot> qualificationSupplier,
            Supplier<TournamentEventDiscoveryQualificationSnapshot>
                    localImportQualificationSupplier,
            J3TournamentCatalogService catalogService) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier");
        this.confirmationCodeSupplier = Objects.requireNonNull(
                confirmationCodeSupplier, "confirmationCodeSupplier");
        this.qualificationSupplier = Objects.requireNonNull(
                qualificationSupplier, "qualificationSupplier");
        this.localImportQualificationSupplier = Objects.requireNonNull(
                localImportQualificationSupplier, "localImportQualificationSupplier");
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService");
        changedAt = clock.instant();
    }

    public synchronized TournamentEventDiscoveryControlSnapshot snapshot() {
        expireIfNecessary(clock.instant());
        return toSnapshot();
    }

    public synchronized TournamentEventDiscoveryControlSnapshot prepare(long tournamentId) {
        return prepare(null,null,tournamentId);
    }
    public synchronized TournamentEventDiscoveryControlSnapshot prepare(UUID collectionId,LocalDate date,long tournamentId) {
        Instant now = clock.instant();
        expireIfNecessary(now);
        if (state == TournamentEventDiscoveryState.AWAITING_CONFIRMATION
                || state == TournamentEventDiscoveryState.EXECUTING) {
            throw rejected(TournamentEventDiscoveryControlError.ACTIVE_REQUEST_EXISTS);
        }
        if (state == TournamentEventDiscoveryState.FAILED_LOCKED
                || state == TournamentEventDiscoveryState.STOPPED_LOCKED
                || state == TournamentEventDiscoveryState.EXPIRED_LOCKED) {
            throw rejected(
                    TournamentEventDiscoveryControlError.TERMINAL_LOCK_REQUIRES_RESTART);
        }
        TournamentEventDiscoveryQualificationSnapshot qualification =
                localImportQualificationSupplier.get();
        if (!qualification.available()) {
            throw rejected(
                    TournamentEventDiscoveryControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        J3TournamentCatalog catalog = collectionId==null?catalogService.latest():catalogService.forCollection(collectionId,Objects.requireNonNull(date));
        if (!catalog.available() || catalog.collectionDate().isEmpty()) {
            throw rejected(TournamentEventDiscoveryControlError.CATALOG_UNAVAILABLE);
        }
        J3TournamentCatalogOption resolved = catalog.findByTournamentId(tournamentId)
                .orElseThrow(() -> rejected(
                        TournamentEventDiscoveryControlError.TOURNAMENT_SELECTION_NOT_ALLOWED));

        requestId = Objects.requireNonNull(requestIdSupplier.get(), "requestId");
        int code = Math.floorMod(confirmationCodeSupplier.getAsInt(), 1_000_000);
        collectionDate = catalog.collectionDate().orElseThrow();
        sourceCollectionId=collectionId;
        selection = resolved;
        confirmationPhrase = "CONFIRMER EVENEMENTS TOURNOI " + resolved.tournamentId()
                + " UNIQUE " + resolved.uniqueTournamentId()
                + " DATE " + collectionDate + " " + "%06d".formatted(code);
        preparedAt = now;
        expiresAt = now.plus(CONFIRMATION_TTL);
        terminalCode = null;
        state = TournamentEventDiscoveryState.AWAITING_CONFIRMATION;
        changedAt = now;
        return toSnapshot();
    }

    public synchronized TournamentEventDiscoveryExecutionClaim confirmAndClaim(
            UUID requestedId,
            String confirmationText,
            boolean acknowledged) {
        Instant now = clock.instant();
        validateLocalClaimPrerequisites(
                requestedId, confirmationText, acknowledged, now);
        TournamentEventDiscoveryQualificationSnapshot qualification =
                qualificationSupplier.get();
        if (!qualification.available()) {
            throw rejected(
                    TournamentEventDiscoveryControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        beginExecution(now);
        return new TournamentEventDiscoveryExecutionClaim(
                requestId,
                qualification.providerOrigin(),
                collectionDate,
                selection);
    }

    public synchronized TournamentEventDiscoveryLocalImportClaim
            confirmAndClaimLocalImport(
                    UUID requestedId,
                    String confirmationText,
                    boolean acknowledged) {
        Instant now = clock.instant();
        TournamentEventDiscoveryQualificationSnapshot qualification =
                validateLocalClaimPrerequisites(
                        requestedId, confirmationText, acknowledged, now);
        beginExecution(now);
        return new TournamentEventDiscoveryLocalImportClaim(
                requestId,
                qualification.providerOrigin(),
                collectionDate,
                selection);
    }

    private TournamentEventDiscoveryQualificationSnapshot validateLocalClaimPrerequisites(
            UUID requestedId,
            String confirmationText,
            boolean acknowledged,
            Instant now) {
        Objects.requireNonNull(requestedId, "requestedId");
        expireIfNecessary(now);
        if (state == TournamentEventDiscoveryState.EXPIRED_LOCKED) {
            throw rejected(TournamentEventDiscoveryControlError.CONFIRMATION_EXPIRED);
        }
        if (state != TournamentEventDiscoveryState.AWAITING_CONFIRMATION
                || requestId == null
                || collectionDate == null
                || selection == null) {
            throw rejected(TournamentEventDiscoveryControlError.NO_PENDING_REQUEST);
        }
        if (!requestId.equals(requestedId)) {
            throw rejected(TournamentEventDiscoveryControlError.REQUEST_ID_MISMATCH);
        }
        if (!acknowledged) {
            throw rejected(TournamentEventDiscoveryControlError.ACKNOWLEDGEMENT_REQUIRED);
        }
        if (!constantTimeEquals(confirmationPhrase, confirmationText)) {
            throw rejected(TournamentEventDiscoveryControlError.CONFIRMATION_TEXT_MISMATCH);
        }
        TournamentEventDiscoveryQualificationSnapshot qualification =
                localImportQualificationSupplier.get();
        if (!qualification.available()) {
            lockFailed(now, "PROVIDER_TRANSPORT_UNAVAILABLE");
            throw rejected(
                    TournamentEventDiscoveryControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        }
        J3TournamentCatalog current;
        try {
            current = sourceCollectionId==null?catalogService.latest():catalogService.forCollection(sourceCollectionId,collectionDate);
        }
        catch (RuntimeException exception) {
            lockFailed(now, "CATALOG_REVALIDATION_ERROR");
            throw rejected(
                    TournamentEventDiscoveryControlError.CATALOG_REVALIDATION_ERROR);
        }
        J3TournamentCatalogOption currentSelection = current
                .findByTournamentId(selection.tournamentId())
                .orElse(null);
        if (!current.available()
                || !current.collectionDate().equals(java.util.Optional.of(collectionDate))
                || !selection.equals(currentSelection)) {
            lockFailed(now, "CATALOG_CHANGED");
            throw rejected(TournamentEventDiscoveryControlError.CATALOG_CHANGED);
        }

        return qualification;
    }

    private void beginExecution(Instant now) {
        confirmationPhrase = null;
        expiresAt = null;
        state = TournamentEventDiscoveryState.EXECUTING;
        changedAt = now;
    }

    public synchronized boolean executionMayContinue(UUID requestedId) {
        return state == TournamentEventDiscoveryState.EXECUTING
                && requestId != null
                && requestId.equals(requestedId);
    }

    public synchronized TournamentEventDiscoveryControlSnapshot complete(UUID requestedId) {
        requireExecuting(requestedId);
        markCompleted();
        return toSnapshot();
    }

    /**
     * Runs a bounded local mutation only while this execution still owns the
     * control lock. An operator stop that wins the lock first prevents the
     * mutation; a stop arriving while it runs waits for its all-or-nothing end.
     */
    public synchronized <T> T executeWhileActive(
            UUID requestedId,
            Supplier<T> action) {
        requireExecuting(requestedId);
        return Objects.requireNonNull(action, "action").get();
    }

    /**
     * Couples the canonical transaction and the successful terminal transition
     * under the same monitor used by {@link #stop()}. This prevents a result
     * labelled OPERATOR_STOP after canonical data has already committed.
     */
    public synchronized <T> T executeAndComplete(
            UUID requestedId,
            Supplier<T> action) {
        requireExecuting(requestedId);
        T result = Objects.requireNonNull(action, "action").get();
        markCompleted();
        return result;
    }

    public synchronized TournamentEventDiscoveryControlSnapshot fail(
            UUID requestedId,
            String code) {
        requireExecuting(requestedId);
        lockFailed(clock.instant(), requireSafeCode(code));
        return toSnapshot();
    }

    public synchronized TournamentEventDiscoveryControlSnapshot stop() {
        return stop(ignored -> { });
    }

    public synchronized TournamentEventDiscoveryControlSnapshot stop(
            Consumer<UUID> beforeLock) {
        Objects.requireNonNull(beforeLock, "beforeLock");
        try {
            if (requestId != null) {
                beforeLock.accept(requestId);
            }
        }
        finally {
            lockByOperator();
        }
        return toSnapshot();
    }

    private void lockByOperator() {
        if (state == TournamentEventDiscoveryState.FAILED_LOCKED
                || state == TournamentEventDiscoveryState.STOPPED_LOCKED
                || state == TournamentEventDiscoveryState.EXPIRED_LOCKED) {
            return;
        }
        state = TournamentEventDiscoveryState.STOPPED_LOCKED;
        terminalCode = "OPERATOR_STOP";
        confirmationPhrase = null;
        expiresAt = null;
        changedAt = clock.instant();
    }

    private void requireExecuting(UUID requestedId) {
        Objects.requireNonNull(requestedId, "requestedId");
        if (requestId == null || !requestId.equals(requestedId)) {
            throw rejected(TournamentEventDiscoveryControlError.REQUEST_ID_MISMATCH);
        }
        if (state != TournamentEventDiscoveryState.EXECUTING) {
            throw rejected(TournamentEventDiscoveryControlError.EXECUTION_NOT_ACTIVE);
        }
    }

    private void expireIfNecessary(Instant now) {
        if (state == TournamentEventDiscoveryState.AWAITING_CONFIRMATION
                && expiresAt != null
                && !now.isBefore(expiresAt)) {
            state = TournamentEventDiscoveryState.EXPIRED_LOCKED;
            terminalCode = "CONFIRMATION_EXPIRED";
            confirmationPhrase = null;
            expiresAt = null;
            changedAt = now;
        }
    }

    private void lockFailed(Instant now, String code) {
        state = TournamentEventDiscoveryState.FAILED_LOCKED;
        terminalCode = requireSafeCode(code);
        confirmationPhrase = null;
        expiresAt = null;
        changedAt = now;
    }

    private void markCompleted() {
        state = TournamentEventDiscoveryState.COMPLETED;
        terminalCode = "COMPLETED";
        changedAt = clock.instant();
    }

    private TournamentEventDiscoveryControlSnapshot toSnapshot() {
        TournamentEventDiscoveryQualificationSnapshot qualification =
                qualificationSupplier.get();
        TournamentEventDiscoveryQualificationSnapshot localImportQualification =
                localImportQualificationSupplier.get();
        return new TournamentEventDiscoveryControlSnapshot(
                state,
                changedAt,
                requestId,
                confirmationPhrase,
                preparedAt,
                expiresAt,
                collectionDate,
                selection,
                terminalCode,
                qualification.available(),
                localImportQualification.available(),
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
        if (expected == null || actual == null || actual.length() > 200) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static TournamentEventDiscoveryControlException rejected(
            TournamentEventDiscoveryControlError error) {
        return new TournamentEventDiscoveryControlException(error);
    }
}
