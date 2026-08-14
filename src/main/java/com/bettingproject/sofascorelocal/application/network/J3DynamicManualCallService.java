package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionResult;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedCollectionEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.ScheduledEventsProviderPageTransport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class J3DynamicManualCallService {

    private final J3ManualCallControlService controlService;
    private final ScheduledEventsProviderPageTransport transport;
    private final J3ScheduledEventsOutcomeProcessor outcomeProcessor;
    private final J3SingleCallGuard callGuard;
    private final J3ManualCollectionEvidenceService evidenceService;
    private final Clock clock;
    private final Duration minimumDelay;
    private final InterPageDelay interPageDelay;

    @Autowired
    public J3DynamicManualCallService(
            J3ManualCallControlService controlService,
            ScheduledEventsProviderPageTransport transport,
            RawManualCallSnapshotStore snapshotStore,
            J3ManualCollectionEvidenceService evidenceService,
            SofascoreProperties properties) {
        this(
                controlService,
                transport,
                new J3ScheduledEventsOutcomeProcessor(
                        snapshotStore,
                        new ScheduledEventsV1Parser(),
                        controlService.circuit()),
                new J3SingleCallGuard(),
                evidenceService,
                Clock.systemUTC(),
                properties.getMinimumDelay(),
                duration -> Thread.sleep(duration));
    }

    J3DynamicManualCallService(
            J3ManualCallControlService controlService,
            ScheduledEventsProviderPageTransport transport,
            J3ScheduledEventsOutcomeProcessor outcomeProcessor,
            J3SingleCallGuard callGuard,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            Duration minimumDelay,
            InterPageDelay interPageDelay) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.outcomeProcessor = Objects.requireNonNull(outcomeProcessor, "outcomeProcessor");
        this.callGuard = Objects.requireNonNull(callGuard, "callGuard");
        this.evidenceService = Objects.requireNonNull(evidenceService, "evidenceService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.minimumDelay = Objects.requireNonNull(minimumDelay, "minimumDelay");
        this.interPageDelay = Objects.requireNonNull(interPageDelay, "interPageDelay");
        if (minimumDelay.compareTo(Duration.ofSeconds(3)) < 0) {
            throw new IllegalArgumentException("minimumDelay must be at least three seconds");
        }
    }

    public J3ManualCallExecutionResult execute(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        var permit = callGuard.tryAcquire();
        if (permit.isEmpty()) {
            throw new J3ManualCallControlException(
                    J3ManualCallControlError.EXECUTION_ALREADY_STARTED);
        }

        try (J3SingleCallGuard.Permit ignored = permit.orElseThrow()) {
            J3ManualCallExecutionClaim claim = controlService.claimExecution(requestId);
            List<J3MinimizedPageEvidence> pageAttempts = new ArrayList<>();
            Instant lastStartedAt = null;
            int initialCompletedPages = 0;
            int completedPages = initialCompletedPages;

            for (int page = claim.firstPage();
                    page <= ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE;
                    page++) {
                if (!controlService.executionMayContinue(requestId)) {
                    return publishAlreadyLockedFailure(
                            claim.date(), initialCompletedPages, completedPages, page,
                            "GLOBAL_STOP_OR_CIRCUIT_BLOCK", pageAttempts);
                }
                if (!awaitMinimumDelay(lastStartedAt)) {
                    controlService.stopGlobally();
                    return publishAlreadyLockedFailure(
                            claim.date(), initialCompletedPages, completedPages, page,
                            "EXECUTION_INTERRUPTED", pageAttempts);
                }
                if (!controlService.executionMayContinue(requestId)) {
                    return publishAlreadyLockedFailure(
                            claim.date(), initialCompletedPages, completedPages, page,
                            "GLOBAL_STOP_OR_CIRCUIT_BLOCK", pageAttempts);
                }

                ScheduledEventsProviderPageRequest request =
                        new ScheduledEventsProviderPageRequest(
                                claim.providerOrigin(), claim.date(), page);
                Instant attemptedAt = clock.instant();
                try {
                    ScheduledEventsTransportResponse response = transport.execute(request);
                    lastStartedAt = response.requestedAt();
                    J3ScheduledEventsOutcome outcome = outcomeProcessor.processResponse(response);
                    String pageTerminalCode = outcome.circuit().state() == J3CircuitState.CLOSED
                            ? null
                            : outcome.circuit().reason().name();
                    pageAttempts.add(J3MinimizedPageEvidence.recorded(
                            page,
                            response,
                            outcome.persistence().orElseThrow(),
                            schemaStatus(outcome),
                            outcome.hasNextPage().orElse(null),
                            pageTerminalCode));
                    if (controlService.snapshot().globalStopActive()) {
                        return publishAlreadyLockedFailure(
                                claim.date(),
                                initialCompletedPages,
                                completedPages,
                                page,
                                "GLOBAL_STOP_OR_CIRCUIT_BLOCK",
                                pageAttempts);
                    }
                    if (outcome.circuit().state() != J3CircuitState.CLOSED) {
                        String terminalCode = outcome.circuit().reason().name();
                        controlService.failExecution(requestId, page, terminalCode);
                        return publishAndLock(
                                requestId,
                                claim.date(),
                                initialCompletedPages,
                                J3ManualCallExecutionResult.failed(
                                        completedPages, page, terminalCode),
                                pageAttempts);
                    }
                    controlService.recordPageCompleted(requestId, page);
                    completedPages = page;
                    boolean hasNextPage = outcome.hasNextPage().orElseThrow(
                            () -> new IllegalStateException(
                                    "a parsed page must expose hasNextPage"));
                    if (!hasNextPage) {
                        controlService.completeExecution(requestId);
                        return publishAndLock(
                                requestId,
                                claim.date(),
                                initialCompletedPages,
                                J3ManualCallExecutionResult.successful(completedPages),
                                pageAttempts);
                    }
                    if (page == ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
                        int blockedNextPage = page + 1;
                        String terminalCode = "PAGINATION_LIMIT_REACHED";
                        controlService.failExecution(
                                requestId, blockedNextPage, terminalCode);
                        return publishAndLock(
                                requestId,
                                claim.date(),
                                initialCompletedPages,
                                J3ManualCallExecutionResult.failed(
                                        completedPages,
                                        blockedNextPage,
                                        terminalCode),
                                pageAttempts);
                    }
                }
                catch (ScheduledEventsTransportException exception) {
                    J3ScheduledEventsOutcome outcome = outcomeProcessor.processFailure(
                            exception, clock.instant());
                    String terminalCode = outcome.circuit().reason().name();
                    pageAttempts.add(J3MinimizedPageEvidence.failedBeforeSnapshot(
                            page, attemptedAt, terminalCode));
                    controlService.failExecution(requestId, page, terminalCode);
                    return publishAndLock(
                            requestId,
                            claim.date(),
                            initialCompletedPages,
                            J3ManualCallExecutionResult.failed(
                                    completedPages, page, terminalCode),
                            pageAttempts);
                }
            }

            throw new IllegalStateException("dynamic pagination terminated unexpectedly");
        }
        catch (J3ManualCallControlException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            if (!controlService.snapshot().globalStopActive()) {
                controlService.stopGlobally();
            }
            throw exception;
        }
    }

    private J3ManualCallExecutionResult publishAndLock(
            UUID requestId,
            LocalDate collectionDate,
            int initialCompletedPages,
            J3ManualCallExecutionResult result,
            List<J3MinimizedPageEvidence> pageAttempts) {
        J3ManualCallControlSnapshot locked = controlService.lockAfterCollection(requestId);
        publishEvidence(
                collectionDate,
                initialCompletedPages,
                result,
                pageAttempts,
                locked);
        return result;
    }

    private J3ManualCallExecutionResult publishAlreadyLockedFailure(
            LocalDate collectionDate,
            int initialCompletedPages,
            int completedPages,
            int failedPage,
            String terminalCode,
            List<J3MinimizedPageEvidence> pageAttempts) {
        J3ManualCallExecutionResult result = J3ManualCallExecutionResult.failed(
                completedPages, failedPage, terminalCode);
        publishEvidence(
                collectionDate,
                initialCompletedPages,
                result,
                pageAttempts,
                controlService.snapshot());
        return result;
    }

    private void publishEvidence(
            LocalDate collectionDate,
            int initialCompletedPages,
            J3ManualCallExecutionResult result,
            List<J3MinimizedPageEvidence> pageAttempts,
            J3ManualCallControlSnapshot terminalSnapshot) {
        evidenceService.publish(new J3MinimizedCollectionEvidence(
                collectionDate,
                terminalSnapshot.intent().state(),
                initialCompletedPages,
                result.completedPages(),
                result.failedPage(),
                result.completed() ? "NONE" : result.terminalCode(),
                clock.instant(),
                terminalSnapshot.globalStopActive(),
                terminalSnapshot.circuitState(),
                terminalSnapshot.circuitReason(),
                pageAttempts));
    }

    private static RawSnapshotSchemaStatus schemaStatus(J3ScheduledEventsOutcome outcome) {
        if (outcome.circuit().state() == J3CircuitState.CLOSED) {
            return RawSnapshotSchemaStatus.PARSED;
        }
        return switch (outcome.circuit().reason()) {
            case SCHEMA_INCOMPATIBLE -> RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
            case UNEXPECTED_CONTENT -> RawSnapshotSchemaStatus.UNEXPECTED_CONTENT;
            default -> RawSnapshotSchemaStatus.TRANSPORT_ERROR;
        };
    }

    private boolean awaitMinimumDelay(Instant lastStartedAt) {
        if (lastStartedAt == null) {
            return true;
        }
        Instant nextEligibleAt = lastStartedAt.plus(minimumDelay);
        Duration remaining = Duration.between(clock.instant(), nextEligibleAt);
        if (remaining.isZero() || remaining.isNegative()) {
            return true;
        }
        try {
            interPageDelay.await(remaining);
            return true;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    interface InterPageDelay {

        void await(Duration duration) throws InterruptedException;
    }
}
