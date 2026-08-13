package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionResult;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.ScheduledEventsProviderPageTransport;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class J3FivePageManualCallService {

    private final J3ManualCallControlService controlService;
    private final ScheduledEventsProviderPageTransport transport;
    private final J3ScheduledEventsOutcomeProcessor outcomeProcessor;
    private final J3SingleCallGuard callGuard;
    private final Clock clock;
    private final Duration minimumDelay;
    private final InterPageDelay interPageDelay;

    public J3FivePageManualCallService(
            J3ManualCallControlService controlService,
            ScheduledEventsProviderPageTransport transport,
            RawManualCallSnapshotStore snapshotStore,
            SofascoreProperties properties) {
        this(
                controlService,
                transport,
                new J3ScheduledEventsOutcomeProcessor(
                        snapshotStore,
                        new ScheduledEventsV1Parser(),
                        controlService.circuit()),
                new J3SingleCallGuard(),
                Clock.systemUTC(),
                properties.getMinimumDelay(),
                duration -> Thread.sleep(duration));
    }

    J3FivePageManualCallService(
            J3ManualCallControlService controlService,
            ScheduledEventsProviderPageTransport transport,
            J3ScheduledEventsOutcomeProcessor outcomeProcessor,
            J3SingleCallGuard callGuard,
            Clock clock,
            Duration minimumDelay,
            InterPageDelay interPageDelay) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.outcomeProcessor = Objects.requireNonNull(outcomeProcessor, "outcomeProcessor");
        this.callGuard = Objects.requireNonNull(callGuard, "callGuard");
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
            Instant lastStartedAt = null;
            int completedPages = 0;

            for (int page = ScheduledEventsProviderPageRequest.FIRST_PAGE;
                    page <= ScheduledEventsProviderPageRequest.LAST_PAGE;
                    page++) {
                if (!controlService.executionMayContinue(requestId)) {
                    return J3ManualCallExecutionResult.failed(
                            completedPages, page, "GLOBAL_STOP_OR_CIRCUIT_BLOCK");
                }
                if (!awaitMinimumDelay(lastStartedAt)) {
                    controlService.stopGlobally();
                    return J3ManualCallExecutionResult.failed(
                            completedPages, page, "EXECUTION_INTERRUPTED");
                }
                if (!controlService.executionMayContinue(requestId)) {
                    return J3ManualCallExecutionResult.failed(
                            completedPages, page, "GLOBAL_STOP_OR_CIRCUIT_BLOCK");
                }

                ScheduledEventsProviderPageRequest request =
                        new ScheduledEventsProviderPageRequest(
                                claim.providerOrigin(), claim.date(), page);
                try {
                    ScheduledEventsTransportResponse response = transport.execute(request);
                    lastStartedAt = response.requestedAt();
                    J3ScheduledEventsOutcome outcome = outcomeProcessor.processResponse(response);
                    if (outcome.circuit().state() != J3CircuitState.CLOSED) {
                        String terminalCode = outcome.circuit().reason().name();
                        controlService.failExecution(requestId, page, terminalCode);
                        return J3ManualCallExecutionResult.failed(
                                completedPages, page, terminalCode);
                    }
                    controlService.recordPageCompleted(requestId, page);
                    completedPages = page;
                }
                catch (ScheduledEventsTransportException exception) {
                    J3ScheduledEventsOutcome outcome = outcomeProcessor.processFailure(
                            exception, clock.instant());
                    String terminalCode = outcome.circuit().reason().name();
                    controlService.failExecution(requestId, page, terminalCode);
                    return J3ManualCallExecutionResult.failed(
                            completedPages, page, terminalCode);
                }
            }

            controlService.completeExecution(requestId);
            return J3ManualCallExecutionResult.successful();
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
