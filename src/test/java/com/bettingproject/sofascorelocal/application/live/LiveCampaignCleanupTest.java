package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.*;
import com.bettingproject.sofascorelocal.config.*;
import com.bettingproject.sofascorelocal.domain.event.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.port.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Persistence outage recovery uses the real coordinator and its thread-owned lock, without a provider. */
class LiveCampaignCleanupTest {
    @Test
    void cleanupRequiredBeforeLaunchIsReportedWithoutAcquisitionOrTransport() throws Exception {
        try (Harness h = new Harness()) {
            when(h.guard.snapshot()).thenReturn(new Guard("CLEANUP_REQUIRED", h.id(), h.coordinator.instanceOwner(), 1, Instant.now()));
            assertThatThrownBy(() -> h.service.launch(h.id(), h.manifest.manifestSha256()))
                    .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_PROVIDER_CLEANUP_REQUIRED");
            verify(h.guard, never()).tryAcquire(any(), any(), any());
            verifyNoInteractions(h.factory, h.campaign);
        }
    }

    @Test
    void aCleanupRequiredAcquisitionRaceRetainsItsSpecificLaunchError() throws Exception {
        try (Harness h = new Harness()) {
            when(h.guard.snapshot()).thenReturn(new Guard("FREE", null, null, 1, Instant.now()),
                    new Guard("CLEANUP_REQUIRED", h.id(), h.coordinator.instanceOwner(), 1, Instant.now()));
            when(h.guard.tryAcquire(eq(h.id()), any(), any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> h.service.launch(h.id(), h.manifest.manifestSha256()))
                    .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_PROVIDER_CLEANUP_REQUIRED");
            verify(h.guard, times(2)).snapshot();
            verify(h.guard, never()).releaseAfterVerifiedCleanup(any(), any());
            verifyNoInteractions(h.factory, h.campaign);
        }
    }

    @Test
    void aLaunchFailureBeforeCommitReleasesOnlyTheGuardAfterProvingPreparationWasUntouched() throws Exception {
        try (Harness h = new Harness()) {
            h.failLaunchBeforeCommit.set(true);
            assertThatThrownBy(() -> h.service.launch(h.id(), h.manifest.manifestSha256()))
                    .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_LAUNCH_FAILED");
            await(h::pending);
            assertThat(h.campaignState.get()).isEqualTo("PREPARED");
            h.databaseUnavailable.set(false);
            clearInvocations(h.store);
            h.service.stop(h.id(), null);
            h.awaitReleased();
            assertThat(h.service.state(h.id())).satisfies(view -> {
                assertThat(view.state()).isEqualTo("PREPARED");
                assertThat(view.ownership()).isNull();
                assertThat(view.startedAt()).isNull();
                assertThat(view.endsAt()).isNull();
                assertThat(view.attempts()).isEmpty();
                assertThat(view.reservedCalls()).isZero();
                assertThat(view.receivedBytes()).isZero();
                assertThat(view.events()).extracting(EventView::state).containsExactly("PREPARED");
            });
            verify(h.store, never()).transition(any(), any(), any(), any(), any(), any());
            verify(h.store, never()).updateScheduleMetrics(any(), any(), any(), anyLong(), anyBoolean(), any());
            verify(h.store, never()).publishResult(any(), any(), any(), any());
            assertThat(h.guardState.get()).isEqualTo("FREE");
            verifyNoInteractions(h.factory, h.campaign);
        }
    }

    @ParameterizedTest @ValueSource(strings={"live-v3","live-v4"})
    void cancellationWinningAfterLeaseAcquisitionReleasesOnlyTheGuardAndNeverOpensABrowser(String policyVersion) throws Exception {
        try (Harness h = new Harness(policyVersion)) {
            h.cancelBeforeLaunchCommit.set(true);
            assertThatThrownBy(() -> h.service.launch(h.id(), h.manifest.manifestSha256()))
                    .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_LAUNCH_FAILED");
            h.awaitReleased();
            assertThat(h.service.state(h.id())).satisfies(view->{
                assertThat(view.state()).isEqualTo("STOPPED_OPERATOR");
                assertThat(view.reason()).isEqualTo("PREPARATION_CANCELLED");
                assertThat(view.ownership()).isNull();assertThat(view.startedAt()).isNull();assertThat(view.endsAt()).isNull();
                assertThat(view.reservedCalls()).isZero();assertThat(view.receivedBytes()).isZero();
                assertThat(view.attempts()).isEmpty();
                assertThat(view.events()).singleElement().satisfies(event->{
                    assertThat(event.state()).isEqualTo("STOPPED_OPERATOR");
                    assertThat(event.reason()).isEqualTo("PREPARATION_CANCELLED");assertThat(event.nextDueAt()).isNull();
                });
            });
            assertThat(h.guardState.get()).isEqualTo("FREE");
            assertThat(h.terminalTransitions).hasValue(0);
            verify(h.store,never()).transition(any(),any(),any(),any(),any(),any());
            verify(h.store,never()).updateFamilySchedule(any(),any(),any(),any());
            verifyNoInteractions(h.factory,h.campaign);
        }
    }

    @ParameterizedTest @ValueSource(strings={"live-v3","live-v4"})
    void aCommittedLaunchWithALostReplyTerminatesItsDurableEventsWithoutCreatingAScheduleOrTransport(String policyVersion) throws Exception {
        try (Harness h = new Harness(policyVersion)) {
            h.failLaunchAfterCommit.set(true);
            assertThatThrownBy(() -> h.service.launch(h.id(), h.manifest.manifestSha256()))
                    .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_LAUNCH_FAILED");
            await(h::pending);
            assertThat(h.campaignState.get()).isEqualTo("RUNNING");
            assertThat(h.eventState.get()).isEqualTo("INITIAL_CHECK");
            h.databaseUnavailable.set(false);
            h.service.stop(h.id(), null);
            h.awaitReleased();
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.eventState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.nextDue.get()).isNull();
            assertThat(h.dispatches).hasValue(0);
            assertThat(h.guardState.get()).isEqualTo("FREE");
            verify(h.store,never()).updateFamilySchedule(any(),any(),any(),any());
            verifyNoInteractions(h.factory, h.campaign);
        }
    }

    @Test
    void cleanupWaitsForAnUncertainLaunchCommitBeforeUsingPreparationAsEvidence() throws Exception {
        try (Harness h = new Harness()) {
            h.failLaunchWithCommitPending.set(true);
            assertThatThrownBy(() -> h.service.launch(h.id(), h.manifest.manifestSha256()))
                    .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_LAUNCH_FAILED");
            assertThat(h.launchBarrierEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(h.campaignState.get()).isEqualTo("PREPARED");
            assertThat(h.pendingLaunchCommit.get()).isTrue();
            verify(h.guard, never()).releaseAfterVerifiedCleanup(any(), any());
            verify(h.store, never()).transition(any(), any(), any(), any(), any(), any());
            // SQL now completes the launch which the client could no longer observe. The guard
            // barrier must settle that transaction before the service chooses a cleanup path.
            h.releaseLaunchCommit.countDown();
            h.awaitReleased();
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.eventState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.durableOwner.get()).isEqualTo(h.ownership());
            assertThat(h.terminalTransitions).hasValue(1);
            assertThat(h.guardState.get()).isEqualTo("FREE");
            verifyNoInteractions(h.factory, h.campaign);
        }
    }

    @Test
    void aFailedGuardBarrierCannotAuthorizePreparationEvidenceOrLeaseRelease() throws Exception {
        try (Harness h = new Harness()) {
            h.failLaunchBeforeCommit.set(true);
            assertThatThrownBy(() -> h.service.launch(h.id(), h.manifest.manifestSha256()))
                    .hasMessage("LIVE_LAUNCH_FAILED");
            await(h::pending);
            h.databaseUnavailable.set(false);
            h.failCleanupBarrier.set(true);
            clearInvocations(h.store);
            int markers = h.cleanupMarkers.get();
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 1 && h.pending());
            verify(h.store, never()).find(any());
            verify(h.guard, never()).releaseAfterVerifiedCleanup(any(), any());
            assertThat(h.campaignState.get()).isEqualTo("PREPARED");
            assertThat(h.guardState.get()).isEqualTo("OWNED");
            h.failCleanupBarrier.set(false);
        }
    }

    @Test
    void aPreparationWithExecutionMetadataCannotAuthorizeTheGuardOnlyCleanupPath() throws Exception {
        try (Harness h = new Harness()) {
            h.failLaunchBeforeCommit.set(true);
            assertThatThrownBy(() -> h.service.launch(h.id(), h.manifest.manifestSha256()))
                    .hasMessage("LIVE_LAUNCH_FAILED");
            await(h::pending);
            h.databaseUnavailable.set(false);
            h.durableStartedAt.set(Instant.now());
            int markers = h.cleanupMarkers.get();
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 1 && h.pending());
            verify(h.guard, never()).releaseAfterVerifiedCleanup(any(), any());
            assertThat(h.campaignState.get()).isEqualTo("PREPARED");
            assertThat(h.guardState.get()).isEqualTo("CLEANUP_REQUIRED");
            h.durableStartedAt.set(null);
        }
    }

    @Test
    void runtimeEvidenceRemainsSeparateFromSqlAndExplicitCleanupReleasesTheOriginalOwner() throws Exception {
        try (Harness h = new Harness()) {
            h.failDuringFirstReceipt();
            assertThat(h.service.runtimeStatus(h.id())).get().satisfies(runtime -> {
                assertThat(runtime.state()).isEqualTo("STOPPED_ERROR");
                assertThat(runtime.reason()).isEqualTo("LOCAL_CLEANUP_PENDING");
                assertThat(runtime.collectionStopped()).isTrue();
                assertThat(runtime.cleanupPending()).isTrue();
                assertThat(runtime.cleanupInProgress()).isFalse();
            });
            assertThat(h.service.runtimeStatus(UUID.randomUUID())).isEmpty();
            assertThat(h.guardState.get()).isEqualTo("OWNED");
            assertThatThrownBy(() -> h.coordinator.acquireLiveCampaign(UUID.randomUUID()))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            assertThatThrownBy(() -> h.service.state(h.id())).hasMessage("database unavailable");

            h.databaseUnavailable.set(false);
            int writes = h.writes.get();
            assertThat(h.service.state(h.id()).state()).isEqualTo("RUNNING");
            for (int i = 0; i < 10; i++) h.service.runtimeStatus(h.id());
            Thread.sleep(120);
            assertThat(h.writes).hasValue(writes);
            assertThat(h.dispatches).hasValue(1);
            assertThat(h.result.get()).isNull();
            verify(h.campaign).close();
            verify(h.guard, never()).releaseAfterVerifiedCleanup(any(), any());

            h.service.stop(h.id(), null);
            h.awaitReleased();
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.eventState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.nextDue.get()).isNull();
            assertThat(h.guardState.get()).isEqualTo("FREE");
            assertThat(h.releaseThreads).containsExactly(h.owner.get());
            verify(h.guard).releaseAfterVerifiedCleanup(eq(h.ownership()), any());
            verify(h.factory).open(h.id(), LiveProviderSession.ENDPOINTS);
            verify(h.campaign).close();
            assertThat(h.dispatches).hasValue(1);
            assertThat(h.result.get().publication()).satisfies(result -> {
                assertThat(result.outcome()).isEqualTo("UNKNOWN");
                assertThat(result.code()).isEqualTo("LOCAL_CLEANUP_UNRESOLVED");
                assertThat(result.successful()).isFalse();
            });
            assertThat(h.receipt.get()).isNull();

            // The real in-process ReentrantLock was released by its owning thread as well.
            try (var reacquired = h.coordinator.acquireLiveCampaign(h.id())) {
                assertThat(reacquired.ownership()).isEqualTo(h.ownership());
            }
        }
    }

    @Test
    void repeatedFailureKeepsExclusionAndConcurrentStopCommandsDoNotQueueSqlRetries() throws Exception {
        try (Harness h = new Harness()) {
            h.failDuringFirstReceipt();
            int markers = h.cleanupMarkers.get();
            h.pauseNextCleanup.set(true);
            long before = System.nanoTime();
            h.service.stop(h.id(), null);
            assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(1));
            assertThat(h.cleanupEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(h.service.runtimeStatus(h.id()).orElseThrow().cleanupInProgress()).isTrue();
            var commands = new ArrayList<Thread>();
            for (int i = 0; i < 8; i++) commands.add(Thread.ofPlatform().start(() -> h.service.stop(h.id(), null)));
            for (Thread command : commands) {
                command.join(1000);
                assertThat(command.isAlive()).isFalse();
            }
            h.releaseCleanup.countDown();
            await(() -> h.cleanupMarkers.get() == markers + 1 && h.pending());
            int writes = h.writes.get();
            Thread.sleep(120);
            assertThat(h.writes).hasValue(writes);
            assertThat(h.guardState.get()).isEqualTo("OWNED");
            assertThat(h.campaignState.get()).isEqualTo("RUNNING");
            verify(h.guard, never()).releaseAfterVerifiedCleanup(any(), any());
            assertThat(h.owner.get().isAlive()).isTrue();

            h.databaseUnavailable.set(false);
            h.service.stop(h.id(), null);
            h.awaitReleased();
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.dispatches).hasValue(1);
        }
    }

    @Test
    void anUnverifiedSupervisorPreventsPublicationAndReleaseUntilAnotherExplicitCommand() throws Exception {
        try (Harness h = new Harness()) {
            h.failDuringFirstReceipt();
            h.databaseUnavailable.set(false);
            h.supervisedCampaign.set(Optional.of(h.id()));
            int markers = h.cleanupMarkers.get();
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 1 && h.pending());
            assertThat(h.campaignState.get()).isEqualTo("RUNNING");
            assertThat(h.guardState.get()).isEqualTo("CLEANUP_REQUIRED");
            verify(h.supervisor).stopCampaign(h.id(), LiveProviderSession.ENDPOINTS);
            verify(h.guard, never()).releaseAfterVerifiedCleanup(any(), any());
            h.supervisedCampaign.set(Optional.empty());
            Thread.sleep(120);
            verify(h.guard, never()).releaseAfterVerifiedCleanup(any(), any());

            h.service.stop(h.id(), null);
            h.awaitReleased();
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.dispatches).hasValue(1);
        }
    }

    @Test
    void failedMetricsAndLeaseReleaseCanBeRetriedWithoutDuplicatingCommittedTerminalEvidence() throws Exception {
        try (Harness h = new Harness()) {
            h.failDuringFirstReceipt();
            h.databaseUnavailable.set(false);
            h.failMetricsOnce.set(true);
            int markers = h.cleanupMarkers.get();
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 1 && h.pending());
            assertThat(h.eventState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.campaignState.get()).isEqualTo("RUNNING");

            h.failRelease.set(true);
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 2 && h.pending());
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.terminalTransitions).hasValue(1);
            assertThat(h.guardState.get()).isEqualTo("CLEANUP_REQUIRED");
            assertThatThrownBy(() -> h.coordinator.acquireLiveCampaign(UUID.randomUUID()))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);

            h.failRelease.set(false);
            h.service.stop(h.id(), null);
            h.awaitReleased();
            assertThat(h.terminalTransitions).hasValue(1);
            assertThat(h.releaseThreads).containsExactly(h.owner.get());
            assertThat(h.nextDue.get()).isNull();
        }
    }

    @Test
    void shutdownTerminatesTheLocalWaitWithoutSqlRetryOrFalseRelease() throws Exception {
        try (Harness h = new Harness()) {
            h.failDuringFirstReceipt();
            int writes = h.writes.get();
            h.abandonOnShutdown = true;
            h.service.shutdown();
            h.owner.get().join(1000);
            assertThat(h.owner.get().isAlive()).isFalse();
            assertThat(h.writes).hasValue(writes);
            assertThat(h.guardState.get()).isEqualTo("OWNED");
            assertThat(h.campaignState.get()).isEqualTo("RUNNING");
            verify(h.guard, never()).releaseAfterVerifiedCleanup(any(), any());
            assertThat(h.dispatches).hasValue(1);
        }
    }

    @Test
    void aCommittedFreeGuardAfterALostReplyRequiresExplicitSameGenerationReconciliation() throws Exception {
        try (Harness h = new Harness()) {
            h.failDuringFirstReceipt();
            h.databaseUnavailable.set(false); h.failReleaseAfterCommit.set(true);
            int markers = h.cleanupMarkers.get();
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 1 && h.pending());
            assertThat(h.guardState.get()).isEqualTo("FREE");
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.owner.get().isAlive()).isTrue();
            assertThatThrownBy(() -> h.coordinator.acquireLiveCampaign(UUID.randomUUID()))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            h.databaseUnavailable.set(true);
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 2 && h.pending());
            assertThatThrownBy(() -> h.coordinator.acquireLiveCampaign(UUID.randomUUID()))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            h.databaseUnavailable.set(false);
            h.service.stop(h.id(), null);
            await(() -> h.service.runtimeStatus(h.id()).isEmpty());
            h.owner.get().join(1000);
            assertThat(h.owner.get().isAlive()).isFalse();
            verify(h.guard, times(1)).releaseAfterVerifiedCleanup(any(), any());
            assertThat(h.terminalTransitions).hasValue(1);
            assertThat(h.dispatches).hasValue(1);
            try (var reacquired = h.coordinator.acquireLiveCampaign(h.id())) {
                assertThat(reacquired.ownership()).isEqualTo(h.ownership());
            }
        }
    }

    @Test
    void reconciliationCannotReleaseMemoryAfterTheDurableGenerationChanged() throws Exception {
        try (Harness h = new Harness()) {
            h.failDuringFirstReceipt();
            h.databaseUnavailable.set(false); h.failReleaseAfterCommit.set(true);
            int markers = h.cleanupMarkers.get();
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 1 && h.pending());
            h.guardGeneration.set(2);
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 2 && h.pending());
            verify(h.guard, times(1)).releaseAfterVerifiedCleanup(any(), any());
            assertThatThrownBy(() -> h.coordinator.acquireLiveCampaign(UUID.randomUUID()))
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class);
            assertThat(h.owner.get().isAlive()).isTrue();
            h.abandonOnShutdown = true;
            h.service.shutdown(); h.owner.get().join(1000);
            assertThat(h.owner.get().isAlive()).isFalse();
        }
    }

    @Test
    void leaseRecoveryRequiresAPreviousCloseAndItsOriginalThread() throws Exception {
        try (Harness h = new Harness(); var lease = h.coordinator.acquireLiveCampaign(h.id())) {
            assertThatThrownBy(lease::retryCloseAfterVerifiedCleanup)
                    .isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class)
                    .hasMessage("provider close has not been attempted");
            AtomicReference<Throwable> rejection = new AtomicReference<>();
            Thread other = Thread.ofPlatform().start(() -> {
                try { lease.retryCloseAfterVerifiedCleanup(); }
                catch (RuntimeException failure) { rejection.set(failure); }
            });
            other.join(1000);
            assertThat(rejection.get()).isInstanceOf(ManualProviderRequestCoordinator.CoordinationException.class)
                    .hasMessage("provider campaign lease belongs to another thread");
            assertThat(h.guardState.get()).isEqualTo("OWNED");
        }
    }

    @Test
    void committedUnknownAfterAnUncertainSqlResponseIsNotDuplicatedAndItsReceiptIsPreserved() throws Exception {
        try (Harness h = new Harness()) {
            h.failAfterReceipt.set(true);
            h.service.launch(h.id(), h.manifest.manifestSha256());
            assertThat(h.inFlight.await(3, TimeUnit.SECONDS)).isTrue();
            h.releaseResponse.countDown();
            await(h::pending);
            RawManualCallSnapshot received = h.receipt.get();
            assertThat(received).isNotNull();
            h.databaseUnavailable.set(false);
            h.failUnknownAfterCommit.set(true);
            int markers = h.cleanupMarkers.get();
            h.service.stop(h.id(), null);
            await(() -> h.cleanupMarkers.get() == markers + 1 && h.pending());
            assertThat(h.unknownPublications).hasValue(1);
            Result committed = h.result.get();
            assertThat(committed.publication().outcome()).isEqualTo("UNKNOWN");
            assertThat(committed.normalized()).isEqualTo(NormalizedReferences.none());
            assertThat(h.campaignState.get()).isEqualTo("RUNNING");

            h.service.stop(h.id(), null);
            h.awaitReleased();
            assertThat(h.result.get()).isSameAs(committed);
            assertThat(h.unknownPublications).hasValue(1);
            assertThat(h.receipt.get()).isSameAs(received);
            assertThat(h.service.state(h.id()).attempts()).singleElement().satisfies(attempt -> {
                assertThat(attempt.snapshotId()).isEqualTo(1L);
                assertThat(attempt.occurrenceId()).isEqualTo(1L);
                assertThat(attempt.receivedAt()).isEqualTo(received.receivedAt());
            });
            assertThat(h.dispatches).hasValue(1);
        }
    }

    @Test
    void aReservationWithoutDispatchIsClosedAsUnknownWithoutInventingATransportOutcome() throws Exception {
        try (Harness h = new Harness()) {
            h.failDispatchAuthorization.set(true);
            h.service.launch(h.id(), h.manifest.manifestSha256());
            assertThat(h.beforeDispatch.await(3, TimeUnit.SECONDS)).isTrue();
            h.releaseDispatch.countDown();
            await(h::pending);
            assertThat(h.dispatches).hasValue(0);
            assertThat(h.reserved.get()).isNotNull();
            h.databaseUnavailable.set(false);
            h.service.stop(h.id(), null);
            h.awaitReleased();
            assertThat(h.service.state(h.id()).attempts()).singleElement().satisfies(attempt -> {
                assertThat(attempt.dispatchAuthorizedAt()).isNull();
                assertThat(attempt.receivedAt()).isNull();
                assertThat(attempt.snapshotId()).isNull();
                assertThat(attempt.result().publication().outcome()).isEqualTo("UNKNOWN");
            });
            assertThat(h.dispatches).hasValue(0);
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static final class Harness implements AutoCloseable {
        final LiveCampaignStore store = mock(LiveCampaignStore.class);
        final ProviderCampaignGuardStore guard = mock(ProviderCampaignGuardStore.class);
        final PlaywrightProviderSupervisor supervisor = mock(PlaywrightProviderSupervisor.class);
        final PlaywrightProviderCampaignFactory factory = mock(PlaywrightProviderCampaignFactory.class);
        final PlaywrightProviderCampaign campaign = mock(PlaywrightProviderCampaign.class);
        final AtomicBoolean databaseUnavailable = new AtomicBoolean(), failRelease = new AtomicBoolean();
        final AtomicBoolean failReleaseAfterCommit = new AtomicBoolean();
        final AtomicLong guardGeneration = new AtomicLong(1);
        final AtomicBoolean failMetricsOnce = new AtomicBoolean(), pauseNextCleanup = new AtomicBoolean();
        final AtomicBoolean failLaunchBeforeCommit = new AtomicBoolean(), failLaunchAfterCommit = new AtomicBoolean();
        final AtomicBoolean cancelBeforeLaunchCommit = new AtomicBoolean();
        final AtomicBoolean failLaunchWithCommitPending = new AtomicBoolean(), pendingLaunchCommit = new AtomicBoolean();
        final AtomicBoolean failCleanupBarrier = new AtomicBoolean();
        final AtomicBoolean failAfterReceipt = new AtomicBoolean(), failUnknownAfterCommit = new AtomicBoolean();
        final AtomicBoolean failDispatchAuthorization = new AtomicBoolean();
        final AtomicInteger writes = new AtomicInteger(), cleanupMarkers = new AtomicInteger();
        final AtomicInteger dispatches = new AtomicInteger(), terminalTransitions = new AtomicInteger();
        final AtomicInteger unknownPublications = new AtomicInteger();
        final AtomicReference<ReservedAttempt> reserved = new AtomicReference<>();
        final AtomicReference<Result> result = new AtomicReference<>();
        final AtomicReference<RawManualCallSnapshot> receipt = new AtomicReference<>();
        final AtomicReference<Instant> dispatchAuthorizedAt = new AtomicReference<>();
        final AtomicReference<String> campaignState = new AtomicReference<>("PREPARED");
        final AtomicReference<String> eventState = new AtomicReference<>("PREPARED");
        final AtomicReference<String> guardState = new AtomicReference<>("FREE");
        final AtomicReference<Ownership> durableOwner = new AtomicReference<>();
        final AtomicReference<Instant> durableStartedAt = new AtomicReference<>();
        final AtomicReference<Instant> nextDue = new AtomicReference<>();
        final AtomicReference<Thread> owner = new AtomicReference<>();
        final List<Thread> releaseThreads = new CopyOnWriteArrayList<>();
        final AtomicReference<Optional<UUID>> supervisedCampaign = new AtomicReference<>(Optional.empty());
        final CountDownLatch inFlight = new CountDownLatch(1), releaseResponse = new CountDownLatch(1);
        final CountDownLatch beforeDispatch = new CountDownLatch(1), releaseDispatch = new CountDownLatch(1);
        final CountDownLatch cleanupEntered = new CountDownLatch(1), releaseCleanup = new CountDownLatch(1);
        final CountDownLatch launchBarrierEntered = new CountDownLatch(1), releaseLaunchCommit = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        final Manifest manifest;
        final ManualProviderRequestCoordinator coordinator;
        final LiveCampaignService service;
        boolean abandonOnShutdown;

        Harness() { this("live-v3"); }
        Harness(String policyVersion) {
            var provider = new SofascoreProperties(); provider.setEnabled(true);
            var playwright = new ProviderPlaywrightProperties(); playwright.setEnabled(true);
            var properties = new LiveCampaignProperties(); properties.setEnabled(true);
            properties.setDuration(Duration.ofMinutes(5)); properties.setQualifiedMatchCapacity(1);
            properties.setQualificationSha256("a".repeat(64));
            if ("live-v4".equals(policyVersion)) {
                // Synthetic qualification lets this test reach the uncertain launch;
                // the conservative production envelope does not qualify fixed-minute traffic.
                properties.getGrouped().setQualificationSha256("c".repeat(64));
                properties.getGrouped().getEndpoints().values().forEach(budget->{
                    budget.setRequestEnvelope(Duration.ofMillis(500));
                    budget.setProcessingEnvelope(Duration.ofMillis(100));
                });
            }
            Instant now = Instant.now();
            var identity = CanonicalEventIdentity.sofascore(17000001L);
            manifest = new Manifest(UUID.randomUUID(), "b".repeat(64), policyVersion, now, now.plusSeconds(300),
                    properties.getDuration(), 1000, 3000, 20_000_000, 1,
                    List.of(new Target(identity.value(), identity.providerEventId(), 1, 1)),
                    new AdmissionProfile(properties.getRequestEnvelope(), properties.getProcessingEnvelope(),
                            properties.getQualificationSha256(),"live-v4".equals(policyVersion) ? properties.groupedAdmissionProfile() : null));
            coordinator = new ManualProviderRequestCoordinator(provider, provided(guard), provided(supervisor));
            when(guard.snapshot()).thenAnswer(call -> {
                database(); boolean free = "FREE".equals(guardState.get());
                return new Guard(guardState.get(), free ? null : id(), free ? null : coordinator.instanceOwner(),
                        guardGeneration.get(), Instant.now());
            });
            when(guard.tryAcquire(eq(id()), any(), any())).thenAnswer(call -> {
                database();
                if (!guardState.compareAndSet("FREE", "OWNED")) return Optional.empty();
                return Optional.of(new Guard("OWNED", id(), call.getArgument(1), 1, call.getArgument(2)));
            });
            when(guard.isOwned(any())).thenAnswer(call -> ownership().equals(call.getArgument(0)) && "OWNED".equals(guardState.get()));
            doAnswer(call -> {
                writes.incrementAndGet(); cleanupMarkers.incrementAndGet();
                if (pauseNextCleanup.compareAndSet(true, false)) {
                    cleanupEntered.countDown();
                    if (!releaseCleanup.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test cleanup timeout");
                }
                database(); requireOwner(call.getArgument(0));
                if (failCleanupBarrier.get()) throw new IllegalStateException("guard barrier failed");
                if (pendingLaunchCommit.get()) {
                    launchBarrierEntered.countDown();
                    if (!releaseLaunchCommit.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test launch timeout");
                    commitPendingLaunch();
                }
                guardState.set("CLEANUP_REQUIRED"); return null;
            }).when(guard).requireCleanup(any(), any());
            doAnswer(call -> {
                writes.incrementAndGet(); database(); requireOwner(call.getArgument(0));
                if (failRelease.get()) throw new IllegalStateException("release transaction failed");
                // A close also waits behind an earlier launch transaction. This models the
                // unsafe old path: observing PREPARED first would then release a RUNNING owner.
                commitPendingLaunch();
                releaseThreads.add(Thread.currentThread()); guardState.set("FREE");
                if (failReleaseAfterCommit.compareAndSet(true, false)) throw new IllegalStateException("release committed but reply lost");
                released.countDown(); return null;
            }).when(guard).releaseAfterVerifiedCleanup(any(), any());
            when(supervisor.activeCampaignId()).thenAnswer(call -> supervisedCampaign.get());
            when(factory.open(id(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(store.find(id())).thenAnswer(call -> {
                database(); return Optional.of(view());
            });
            when(store.dispatchBudget(any(),any())).thenAnswer(call -> {
                database(); requireOwner(call.getArgument(0));
                return new DispatchBudget(dispatches.get(),0,0,eventState.get());
            });
            when(store.launch(eq(id()), eq(manifest.manifestSha256()), any(), any())).thenAnswer(call -> {
                database(); requireOwner(call.getArgument(2)); owner.set(Thread.currentThread());
                if (cancelBeforeLaunchCommit.get()) {
                    // The competing local cancellation committed after the service's first read
                    // and lease acquisition, before the SQL launch locked this preparation.
                    campaignState.set("STOPPED_OPERATOR");eventState.set("STOPPED_OPERATOR");
                    throw new IllegalStateException("live preparation is expired or closed");
                }
                if (failLaunchBeforeCommit.get()) {
                    databaseUnavailable.set(true); throw new IllegalStateException("launch transaction rolled back");
                }
                if (failLaunchWithCommitPending.get()) {
                    pendingLaunchCommit.set(true);
                    throw new IllegalStateException("launch commit reply lost while transaction remains pending");
                }
                Instant start = call.getArgument(3);
                campaignState.set("RUNNING"); durableOwner.set(ownership()); durableStartedAt.set(start);
                eventState.set("INITIAL_CHECK"); nextDue.set(start);
                if (failLaunchAfterCommit.get()) {
                    databaseUnavailable.set(true); throw new IllegalStateException("launch committed but reply lost");
                }
                return new Launch(ownership(), start, start.plus(manifest.duration()), true);
            });
            doAnswer(call -> {
                writes.incrementAndGet();
                database(); requireOwner(call.getArgument(0));
                if (call.getArgument(1) == null) {
                    campaignState.set(call.getArgument(2)); terminalTransitions.incrementAndGet();
                } else eventState.set(call.getArgument(2));
                return null;
            }).when(store).transition(any(), any(), any(), any(), any(), any());
            doAnswer(call -> {
                writes.incrementAndGet(); database(); requireOwner(call.getArgument(0));
                if (failMetricsOnce.compareAndSet(true, false)) throw new IllegalStateException("metrics transaction failed");
                nextDue.set(call.getArgument(2)); return null;
            }).when(store).updateScheduleMetrics(any(), any(), any(), anyLong(), anyBoolean(), any());
            when(store.reserveAttempt(any())).thenAnswer(call -> {
                database(); AttemptRequest request = call.getArgument(0);
                ReservedAttempt attempt = new ReservedAttempt(request.attemptId(), identity.value(), identity.providerEventId(),
                        request.endpoint(), request.cycleNumber(), request.kind(), request.dueAt(), request.reservedAt(), request.finalCycle());
                reserved.set(attempt); return Optional.of(attempt);
            });
            doAnswer(call -> {
                writes.incrementAndGet();
                if (failDispatchAuthorization.get()) databaseUnavailable.set(true);
                database(); dispatchAuthorizedAt.set(call.getArgument(2)); return null;
            }).when(store).recordDispatch(any(), any(), any());
            when(store.saveReceipt(any(), any(), any())).thenAnswer(call -> {
                writes.incrementAndGet(); database(); RawManualCallSnapshot raw = call.getArgument(2);
                receipt.set(raw);
                if (failAfterReceipt.get()) databaseUnavailable.set(true);
                return new RawSnapshotPersistenceResult(1, RawSnapshotPersistenceOutcome.INSERTED,
                        raw.payload().sha256(), raw.payload().sizeBytes(), OptionalLong.of(1));
            });
            when(store.publishResult(any(), any(), any(), any())).thenAnswer(call -> {
                writes.incrementAndGet(); database(); requireOwner(call.getArgument(0));
                Publication publication = call.getArgument(2);
                assertThat(publication.outcome()).isEqualTo("UNKNOWN");
                assertThat(publication.scope()).isEqualTo("CAMPAIGN");
                java.util.function.Supplier<NormalizedReferences> normalization = call.getArgument(3);
                Result saved = new Result(call.getArgument(1), publication, normalization.get());
                assertThat(result.compareAndSet(null, saved)).isTrue(); unknownPublications.incrementAndGet();
                if (failUnknownAfterCommit.compareAndSet(true, false)) throw new IllegalStateException("connection lost after commit");
                return saved;
            });
            when(campaign.execute(any(), any())).thenAnswer(call -> {
                if (failDispatchAuthorization.get()) {
                    beforeDispatch.countDown();
                    if (!releaseDispatch.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test dispatch timeout");
                }
                PlaywrightDispatchAdmission dispatch = call.getArgument(1); dispatch.check();
                try (var permit = dispatch.acquireDispatchPermit()) { dispatches.incrementAndGet(); }
                inFlight.countDown();
                if (!releaseResponse.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test response timeout");
                Instant received = Instant.now();
                return new PlaywrightProviderResponse(received, received, 200, "application/json", Duration.ZERO,
                        RawPayloadEvidence.capture("{}".getBytes(StandardCharsets.UTF_8)));
            });
            var events = mock(CanonicalEventStore.class);
            var event = mock(CanonicalEventObservationView.class);
            when(event.identity()).thenReturn(identity);
            when(event.status()).thenReturn(new ScheduledEventStatus("notstarted", Optional.empty()));
            when(event.source()).thenReturn(EventSourceTrace.providerSnapshot(1, "a".repeat(64), "event-details-v2", now));
            when(events.findLatestByCanonicalId(identity.value())).thenReturn(Optional.of(event));
            service = new LiveCampaignService(provider, playwright, properties, mock(LiveAdmissionPolicy.class),
                    store, events, coordinator, guard, factory, supervisor, mock(LiveResponseProcessor.class), Clock.systemUTC());
        }

        UUID id() { return manifest.campaignId(); }
        Ownership ownership() { return new Ownership(id(), coordinator.instanceOwner().instanceId(), 1); }
        void database() { if (databaseUnavailable.get()) throw new IllegalStateException("database unavailable"); }
        void requireOwner(Ownership actual) {
            if (!ownership().equals(actual) || "FREE".equals(guardState.get()) || actual.generation() != guardGeneration.get())
                throw new IllegalStateException("stale ownership");
        }
        void commitPendingLaunch() {
            if (pendingLaunchCommit.compareAndSet(true, false)) {
                Instant start = Instant.now();
                campaignState.set("RUNNING"); durableOwner.set(ownership()); durableStartedAt.set(start);
                eventState.set("INITIAL_CHECK"); nextDue.set(start);
            }
        }
        CampaignView view() {
            RawManualCallSnapshot raw = receipt.get();
            List<AttemptView> attempts = reserved.get() == null ? List.of() : List.of(new AttemptView(reserved.get(),
                    dispatchAuthorizedAt.get(), raw == null ? null : 1L, raw == null ? null : 1L,
                    raw == null ? null : raw.receivedAt(), result.get()));
            Instant start = durableStartedAt.get();
            String cancellationReason=cancelBeforeLaunchCommit.get() && "STOPPED_OPERATOR".equals(campaignState.get())
                    ? "PREPARATION_CANCELLED" : null;
            return new CampaignView(manifest, campaignState.get(), cancellationReason, start, start == null ? null : start.plus(manifest.duration()),
                    dispatches.get(), 0, 0, durableOwner.get(),
                    List.of(new EventView(manifest.targets().getFirst(), eventState.get(), cancellationReason, 0, 0, nextDue.get(), List.of())),
                    attempts, List.of());
        }
        void failDuringFirstReceipt() throws InterruptedException {
            service.launch(id(), manifest.manifestSha256());
            assertThat(inFlight.await(3, TimeUnit.SECONDS)).isTrue();
            databaseUnavailable.set(true); releaseResponse.countDown(); await(this::pending);
        }
        boolean pending() { return service.runtimeStatus(id()).filter(s -> s.cleanupPending() && !s.cleanupInProgress()).isPresent(); }
        void awaitReleased() throws InterruptedException {
            assertThat(released.await(4, TimeUnit.SECONDS)).isTrue();
            owner.get().join(1000);
            assertThat(owner.get().isAlive()).isFalse();
            assertThat(service.runtimeStatus(id())).isEmpty();
        }
        @SuppressWarnings("unchecked")
        private static <T> ObjectProvider<T> provided(T bean) {
            ObjectProvider<T> source = mock(ObjectProvider.class); when(source.getIfAvailable()).thenReturn(bean); return source;
        }
        @Override public void close() throws InterruptedException {
            releaseResponse.countDown(); releaseCleanup.countDown(); releaseDispatch.countDown(); releaseLaunchCommit.countDown();
            if (!abandonOnShutdown && pending()) {
                databaseUnavailable.set(false); failRelease.set(false); supervisedCampaign.set(Optional.empty());
                service.stop(id(), null); awaitReleased();
            }
            service.shutdown();
        }
    }
}
