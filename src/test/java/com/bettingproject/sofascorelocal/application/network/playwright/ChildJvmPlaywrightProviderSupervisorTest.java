package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChildJvmPlaywrightProviderSupervisorTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void disabledRuntimeFailsBeforeStartingAnyProcess() {
        AtomicInteger starts = new AtomicInteger();
        var supervisor = supervisor(new ProviderPlaywrightProperties(), starts);

        assertThatThrownBy(() -> supervisor.open(
                UUID.randomUUID(), Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.DISABLED);
        assertThat(starts).hasValue(0);
        assertThat(supervisor.activeCampaignId()).isEmpty();
    }

    @Test
    void invalidWorkerArtifactFailsClosedBeforeStartingAnyProcess() throws Exception {
        Path notAJar = temporaryDirectory.resolve("worker.txt");
        Files.writeString(notAJar, "not executable");
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        properties.setWorkerJar(notAJar);
        AtomicInteger starts = new AtomicInteger();
        var supervisor = supervisor(properties, starts);

        assertThatThrownBy(() -> supervisor.open(
                UUID.randomUUID(), Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.WORKER_ARTIFACT_INVALID);
        assertThat(starts).hasValue(0);
    }

    @Test
    void workerAlwaysDisablesImplicitBrowserDownloads() throws Exception {
        ProviderPlaywrightProperties properties = enabledProperties("no-download.jar");
        AtomicReference<Map<String, String>> environment = new AtomicReference<>();
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    environment.set(Map.copyOf(builder.environment()));
                    throw new IOException("expected test launch failure");
                });

        assertThatThrownBy(() -> supervisor.open(
                UUID.randomUUID(), Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.STARTUP_FAILED);

        assertThat(environment.get())
                .containsEntry("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
    }

    @Test
    void stopWithoutAnActiveCampaignAcknowledgesImmediatelyAndChangesNoProcess() {
        AtomicInteger starts = new AtomicInteger();
        var supervisor = supervisor(new ProviderPlaywrightProperties(), starts);

        PlaywrightProviderStopReceipt receipt = supervisor.stopCampaign(
                UUID.randomUUID(),
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));

        assertThat(receipt.campaignId()).isNull();
        assertThat(receipt.activeCampaignSignalled()).isFalse();
        assertThat(receipt.acknowledgementLatency()).isBetween(
                java.time.Duration.ZERO,
                java.time.Duration.ofMillis(500));
        assertThat(starts).hasValue(0);
    }

    @Test
    void acknowledgedExactStopBeforeOpenBlocksBothJ3CampaignTypes() {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        AtomicInteger starts = new AtomicInteger();
        var supervisor = supervisor(properties, starts);

        for (SofascoreEndpointType endpoint : List.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS)) {
            UUID campaignId = UUID.randomUUID();

            PlaywrightProviderStopReceipt receipt = supervisor.stopCampaign(
                    campaignId, Set.of(endpoint));

            assertThat(receipt.activeCampaignSignalled()).isFalse();
            assertThat(receipt.campaignId()).isNull();
            assertThatThrownBy(() -> supervisor.open(campaignId, Set.of(endpoint)))
                    .isInstanceOf(PlaywrightProviderException.class)
                    .extracting("failure")
                    .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
        }
        assertThat(starts).hasValue(0);
    }

    @Test
    void saturatedStopTombstonesStayBoundedAndFailClosedWithoutStartingAWorker() {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        AtomicInteger starts = new AtomicInteger();
        var supervisor = supervisor(properties, starts);
        Set<SofascoreEndpointType> allowlist =
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS);

        for (int index = 0;
                index <= ChildJvmPlaywrightProviderSupervisor
                        .MAXIMUM_STOP_TOMBSTONES_PER_ALLOWLIST;
                index++) {
            supervisor.stopCampaign(UUID.randomUUID(), allowlist);
        }

        assertThatThrownBy(() -> supervisor.open(UUID.randomUUID(), allowlist))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
        assertThat(starts).hasValue(0);
    }

    @Test
    void rejectsAnEndpointOutsideTheImplementedWorkerAllowlistBeforeStartingAnyProcess() {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        AtomicInteger starts = new AtomicInteger();
        var supervisor = supervisor(properties, starts);

        assertThatThrownBy(() -> supervisor.open(
                UUID.randomUUID(), Set.of(SofascoreEndpointType.TOURNAMENT_STANDINGS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.INVALID_ENDPOINT);
        assertThat(starts).hasValue(0);
    }

    @Test
    void shutdownWaitsForAndCleansAStartingCampaignThenBlocksEveryLaterOpen()
            throws Exception {
        ProviderPlaywrightProperties properties = enabledProperties("shutdown-starting.jar");
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(31L, rootStartedAt, true, true);
        Process process = process(root.handle());
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.of(rootStartedAt));
        CountDownLatch launchEntered = new CountDownLatch(1);
        CountDownLatch releaseLaunch = new CountDownLatch(1);
        var processTreeAccess = new ShutdownAwareProcessTreeAccess(root.owned());
        AtomicInteger starts = new AtomicInteger();
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    starts.incrementAndGet();
                    launchEntered.countDown();
                    awaitLatch(releaseLaunch);
                    return process;
                },
                processTreeAccess);
        UUID campaignId = UUID.randomUUID();

        CompletableFuture<Throwable> opening = openFailure(
                supervisor,
                campaignId,
                SofascoreEndpointType.SCHEDULED_EVENTS);
        assertThat(launchEntered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(supervisor.activeCampaignId()).contains(campaignId);

        CompletableFuture<Void> shutdown = CompletableFuture.runAsync(supervisor::shutdown);
        assertThat(processTreeAccess.shutdownObserved().await(1, TimeUnit.SECONDS)).isTrue();
        releaseLaunch.countDown();

        assertThat(opening.get(2, TimeUnit.SECONDS))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
        shutdown.get(2, TimeUnit.SECONDS);
        assertThat(root.alive()).isFalse();
        assertThat(supervisor.activeCampaignId()).isEmpty();
        assertThatThrownBy(() -> supervisor.open(
                UUID.randomUUID(),
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
        assertThat(starts).hasValue(1);
    }

    @Test
    void exactStopDuringStartingIsAcknowledgedCleanedAndRetainedAsATombstone()
            throws Exception {
        ProviderPlaywrightProperties properties = enabledProperties("stop-starting.jar");
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z").plusSeconds(1);
        OwnedHandle root = ownedHandle(32L, rootStartedAt, true, true);
        Process process = process(root.handle());
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.of(rootStartedAt));
        CountDownLatch launchEntered = new CountDownLatch(1);
        CountDownLatch releaseLaunch = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    starts.incrementAndGet();
                    launchEntered.countDown();
                    awaitLatch(releaseLaunch);
                    return process;
                },
                new RealTimeProcessTreeAccess(root.owned()));
        UUID campaignId = UUID.randomUUID();
        Set<SofascoreEndpointType> allowlist =
                Set.of(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS);

        CompletableFuture<Throwable> opening = openFailure(
                supervisor,
                campaignId,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS);
        assertThat(launchEntered.await(1, TimeUnit.SECONDS)).isTrue();

        PlaywrightProviderStopReceipt receipt = supervisor.stopCampaign(
                campaignId,
                allowlist);
        releaseLaunch.countDown();

        assertThat(receipt.activeCampaignSignalled()).isTrue();
        assertThat(receipt.campaignId()).isEqualTo(campaignId);
        assertThat(opening.get(2, TimeUnit.SECONDS))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
        awaitNoActiveCampaign(supervisor);
        assertThat(root.alive()).isFalse();
        assertThatThrownBy(() -> supervisor.open(campaignId, allowlist))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
        assertThat(starts).hasValue(1);
    }

    @Test
    void missingRootStartInstantLeavesAnInconclusiveCampaignPublishedAndBlocking()
            throws Exception {
        ProviderPlaywrightProperties properties = enabledProperties("unknown-root.jar");
        Process process = mock(Process.class);
        ProcessHandle root = mock(ProcessHandle.class);
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.toHandle()).thenReturn(root);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.empty());
        UUID campaignId = UUID.randomUUID();
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> process);

        assertThatThrownBy(() -> supervisor.open(
                campaignId,
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.RUNTIME_FAILURE);

        verify(process, atLeastOnce()).destroyForcibly();
        assertThat(supervisor.activeCampaignId()).contains(campaignId);
        assertThatThrownBy(() -> supervisor.open(
                UUID.randomUUID(),
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.CAMPAIGN_ALREADY_ACTIVE);
    }

    @Test
    void repeatedlyInventoriesAndRemovesADescendantAppearingDuringShutdown() {
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(41L, rootStartedAt, true, true);
        OwnedHandle earlyChild = ownedHandle(
                42L, rootStartedAt.plusMillis(1), true, true);
        OwnedHandle lateChild = ownedHandle(
                43L, rootStartedAt.plusMillis(2), true, true);
        Process process = process(root.handle());
        SimulatedProcessTreeAccess access = new SimulatedProcessTreeAccess(List.of(
                List.of(root.owned(), earlyChild.owned()),
                List.of(root.owned(), earlyChild.owned(), lateChild.owned())));

        var outcome = ChildJvmPlaywrightProviderSupervisor.terminateOwnedProcessTree(
                process, rootStartedAt, access);

        assertThat(outcome.cancellationWithinBound()).isTrue();
        assertThat(outcome.cancellationLatency())
                .isLessThanOrEqualTo(
                        ChildJvmPlaywrightProviderSupervisor.IN_FLIGHT_CANCELLATION_MAX);
        assertThat(outcome.cleanupLatency())
                .isLessThanOrEqualTo(
                        ChildJvmPlaywrightProviderSupervisor.PROCESS_TREE_CLEANUP_MAX);
        assertThat(outcome.identityComplete()).isTrue();
        assertThat(outcome.unverifiedAliveProcessCount()).isZero();
        assertThat(outcome.residualOwnedProcessCount()).isZero();
        assertThat(outcome.capturePasses()).isGreaterThanOrEqualTo(3);
        assertThat(earlyChild.destroyCalls()).hasPositiveValue();
        assertThat(lateChild.destroyCalls()).hasPositiveValue();
        assertThat(root.alive()).isFalse();
        assertThat(earlyChild.alive()).isFalse();
        assertThat(lateChild.alive()).isFalse();
    }

    @Test
    void escalatesBeforeTwoSecondsAndLeavesNoOwnedResidue() {
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(51L, rootStartedAt, false, true);
        OwnedHandle child = ownedHandle(
                52L, rootStartedAt.plusMillis(1), false, true);
        Process process = process(root.handle());
        SimulatedProcessTreeAccess access = new SimulatedProcessTreeAccess(
                List.of(List.of(root.owned(), child.owned())));

        var outcome = ChildJvmPlaywrightProviderSupervisor.terminateOwnedProcessTree(
                process, rootStartedAt, access);

        assertThat(outcome.cancellationWithinBound()).isTrue();
        assertThat(outcome.cancellationLatency())
                .isBetween(
                        ChildJvmPlaywrightProviderSupervisor.SOFT_PROCESS_TERMINATION_MAX,
                        ChildJvmPlaywrightProviderSupervisor.IN_FLIGHT_CANCELLATION_MAX);
        assertThat(outcome.cleanupLatency())
                .isLessThanOrEqualTo(
                        ChildJvmPlaywrightProviderSupervisor.PROCESS_TREE_CLEANUP_MAX);
        assertThat(outcome.identityComplete()).isTrue();
        assertThat(outcome.unverifiedAliveProcessCount()).isZero();
        assertThat(outcome.residualOwnedProcessCount()).isZero();
        assertThat(root.forceCalls()).hasPositiveValue();
        assertThat(child.forceCalls()).hasPositiveValue();
    }

    @Test
    void reportsAResidualAndAMissedCancellationBoundWhenExactProcessesRefuseToExit() {
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(61L, rootStartedAt, false, false);
        OwnedHandle child = ownedHandle(
                62L, rootStartedAt.plusMillis(1), false, false);
        Process process = process(root.handle());
        SimulatedProcessTreeAccess access = new SimulatedProcessTreeAccess(
                List.of(List.of(root.owned(), child.owned())));

        var outcome = ChildJvmPlaywrightProviderSupervisor.terminateOwnedProcessTree(
                process, rootStartedAt, access);

        assertThat(outcome.cancellationWithinBound()).isFalse();
        assertThat(outcome.cleanupLatency())
                .isEqualTo(
                        ChildJvmPlaywrightProviderSupervisor.PROCESS_TREE_CLEANUP_MAX);
        assertThat(outcome.identityComplete()).isTrue();
        assertThat(outcome.unverifiedAliveProcessCount()).isZero();
        assertThat(outcome.residualOwnedProcessCount()).isEqualTo(2);
        assertThat(root.forceCalls()).hasPositiveValue();
        assertThat(child.forceCalls()).hasPositiveValue();
    }

    @Test
    void missingDescendantStartInstantMakesCleanupInconclusiveAndBlocking() {
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(63L, rootStartedAt, true, true);
        Process process = process(root.handle());
        var access = new UnverifiedProcessTreeAccess(root.owned());

        var outcome = ChildJvmPlaywrightProviderSupervisor.terminateOwnedProcessTree(
                process, rootStartedAt, access);

        assertThat(outcome.identityComplete()).isFalse();
        assertThat(outcome.unverifiedAliveProcessCount()).isEqualTo(1);
        assertThat(outcome.cancellationWithinBound()).isFalse();
        assertThat(outcome.cleanupLatency())
                .isEqualTo(
                        ChildJvmPlaywrightProviderSupervisor.PROCESS_TREE_CLEANUP_MAX);
        assertThat(outcome.residualOwnedProcessCount()).isZero();
        assertThat(root.alive()).isFalse();
    }

    @Test
    void systemInventoryNeverEnumeratesDescendantsBeforeExactRootAuthentication() {
        Instant expected = Instant.parse("2026-08-27T08:00:00Z");

        assertUnauthenticatedRootIsNotEnumerated(
                expected,
                true,
                Optional.of(expected.plusSeconds(1)),
                1);
        assertUnauthenticatedRootIsNotEnumerated(
                expected,
                false,
                Optional.of(expected),
                0);
    }

    @Test
    void systemInventoryRejectsEveryDescendantWhenRootIdentityChangesDuringEnumeration() {
        Instant expected = Instant.parse("2026-08-27T08:00:00Z");
        Process process = mock(Process.class);
        ProcessHandle root = mock(ProcessHandle.class);
        ProcessHandle.Info rootInfo = mock(ProcessHandle.Info.class);
        OwnedHandle foreignChild = ownedHandle(
                641L, expected.plusSeconds(2), true, true);
        when(process.toHandle()).thenReturn(root);
        when(root.isAlive()).thenReturn(true);
        when(root.info()).thenReturn(rootInfo);
        when(rootInfo.startInstant()).thenReturn(
                Optional.of(expected),
                Optional.of(expected.plusSeconds(1)));
        when(root.descendants()).thenReturn(java.util.stream.Stream.of(foreignChild.handle()));
        var access = new ChildJvmPlaywrightProviderSupervisor.SystemProcessTreeAccess();

        var snapshot = access.capture(process, expected);

        assertThat(snapshot.rootIdentityAuthenticated()).isFalse();
        assertThat(snapshot.unverifiedAliveProcessCount()).isEqualTo(1);
        assertThat(snapshot.ownedProcesses()).isEmpty();
        assertThat(foreignChild.destroyCalls()).hasValue(0);
        assertThat(foreignChild.forceCalls()).hasValue(0);
    }

    @Test
    void systemInventoryRejectsEveryDescendantWhenRootDisappearsDuringEnumeration() {
        Instant expected = Instant.parse("2026-08-27T08:00:00Z");
        Process process = mock(Process.class);
        ProcessHandle root = mock(ProcessHandle.class);
        ProcessHandle.Info rootInfo = mock(ProcessHandle.Info.class);
        OwnedHandle child = ownedHandle(642L, expected.plusSeconds(2), true, true);
        when(process.toHandle()).thenReturn(root);
        when(root.isAlive()).thenReturn(true, false);
        when(root.info()).thenReturn(rootInfo);
        when(rootInfo.startInstant()).thenReturn(Optional.of(expected));
        when(root.descendants()).thenReturn(java.util.stream.Stream.of(child.handle()));
        var access = new ChildJvmPlaywrightProviderSupervisor.SystemProcessTreeAccess();

        var snapshot = access.capture(process, expected);

        assertThat(snapshot.rootIdentityAuthenticated()).isFalse();
        assertThat(snapshot.unverifiedAliveProcessCount()).isZero();
        assertThat(snapshot.ownedProcesses()).isEmpty();
        assertThat(child.destroyCalls()).hasValue(0);
        assertThat(child.forceCalls()).hasValue(0);
    }

    @Test
    void reauthenticatesPidAndStartInstantImmediatelyBeforeDestroy() {
        Instant expected = Instant.parse("2026-08-27T08:00:00Z");
        ProcessHandle root = mock(ProcessHandle.class);
        ProcessHandle.Info rootInfo = mock(ProcessHandle.Info.class);
        AtomicInteger destroyCalls = new AtomicInteger();
        when(root.pid()).thenReturn(643L);
        when(root.isAlive()).thenReturn(true);
        when(root.info()).thenReturn(rootInfo);
        when(rootInfo.startInstant()).thenReturn(Optional.of(expected.plusSeconds(1)));
        when(root.destroy()).thenAnswer(ignored -> {
            destroyCalls.incrementAndGet();
            return true;
        });
        Process process = process(root);
        var inventory = ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot.exact(
                List.of(new ChildJvmPlaywrightProviderSupervisor.OwnedProcess(root, expected)));
        var access = new SimulatedProcessTreeAccess(List.of(inventory.ownedProcesses()));

        var outcome = ChildJvmPlaywrightProviderSupervisor.terminateOwnedProcessTree(
                process,
                expected,
                inventory,
                access);

        assertThat(destroyCalls).hasValue(0);
        assertThat(outcome.residualOwnedProcessCount()).isZero();
    }

    @Test
    void rootDisappearanceBeforeAnAuthenticatedSignalRemainsInconclusive() {
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(64L, rootStartedAt, true, true);
        OwnedHandle child = ownedHandle(
                65L, rootStartedAt.plusMillis(1), true, true);
        Process process = process(root.handle());
        var initialInventory =
                ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot.exact(
                        List.of(root.owned(), child.owned()));
        root.alive().set(false);
        var access = new RootGoneProcessTreeAccess();

        var outcome = ChildJvmPlaywrightProviderSupervisor.terminateOwnedProcessTree(
                process,
                rootStartedAt,
                initialInventory,
                access);

        assertThat(outcome.identityComplete()).isFalse();
        assertThat(outcome.cancellationWithinBound()).isTrue();
        assertThat(outcome.residualOwnedProcessCount()).isZero();
        assertThat(child.destroyCalls()).hasPositiveValue();
        assertThat(child.alive()).isFalse();
        assertThat(access.captureCalls()).hasPositiveValue();
    }

    @Test
    void authenticatedTerminalFrameMakesAnAlreadyExitedRootConclusive() {
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(651L, rootStartedAt, true, true);
        OwnedHandle child = ownedHandle(
                652L, rootStartedAt.plusMillis(1), true, true);
        Process process = process(root.handle());
        var initialInventory =
                ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot.exact(
                        List.of(root.owned(), child.owned()));
        root.alive().set(false);
        var access = new RootGoneProcessTreeAccess();

        var outcome = ChildJvmPlaywrightProviderSupervisor.terminateOwnedProcessTree(
                process,
                rootStartedAt,
                initialInventory,
                access,
                true);

        assertThat(outcome.identityComplete()).isTrue();
        assertThat(outcome.cancellationWithinBound()).isTrue();
        assertThat(outcome.residualOwnedProcessCount()).isZero();
        assertThat(child.destroyCalls()).hasPositiveValue();
        assertThat(child.alive()).isFalse();
    }

    @Test
    void rootExitBeforeTheFreshTerminationInventoryFailsClosedAndKeepsCampaignPublished()
            throws Exception {
        ProviderPlaywrightProperties properties = enabledProperties("retained-tree.jar");
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(66L, rootStartedAt, true, true);
        OwnedHandle child = ownedHandle(
                67L, rootStartedAt.plusMillis(1), true, true);
        Process process = process(root.handle());
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.of(rootStartedAt));
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        var processTreeAccess = new RootAwareProcessTreeAccess(root, child);
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    int port = Integer.parseInt(builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_PORT"));
                    String token = builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_TOKEN");
                    workerThread.set(Thread.ofPlatform()
                            .name("fake-playwright-retained-tree")
                            .start(() -> runIdleWorker(
                                    port, token, () -> { }, workerFailure)));
                    return process;
                },
                processTreeAccess);
        UUID campaignId = UUID.randomUUID();
        Set<SofascoreEndpointType> allowlist =
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS);

        supervisor.open(campaignId, allowlist);
        assertThat(processTreeAccess.captureCalls()).hasValue(1);
        root.alive().set(false);

        PlaywrightProviderStopReceipt receipt = supervisor.stopCampaign(
                campaignId, allowlist);
        awaitValueAtLeast(processTreeAccess.captureCalls(), 2);

        assertThat(receipt.activeCampaignSignalled()).isTrue();
        assertThat(supervisor.activeCampaignId()).contains(campaignId);
        assertThat(child.destroyCalls()).hasValue(0);
        assertThat(child.alive()).isTrue();
        assertThat(workerThread.get().isAlive()).isTrue();

        root.alive().set(true);
        supervisor.stopCampaign(campaignId, allowlist);
        awaitNoActiveCampaign(supervisor);
        workerThread.get().join(2_000);
        assertThat(workerThread.get().isAlive()).isFalse();
        assertThat(child.alive()).isFalse();
        assertThat(workerFailure.get()).isNull();
    }

    @Test
    void postClosedInventoryRetainsAChildThatIsThenReparentedByRootExit()
            throws Exception {
        ProviderPlaywrightProperties properties = enabledProperties("late-child-tree.jar");
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(661L, rootStartedAt, true, true);
        OwnedHandle lateChild = ownedHandle(
                662L, rootStartedAt.plusMillis(1), true, true);
        Process process = process(root.handle());
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.of(rootStartedAt));
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        var processTreeAccess = new LateChildProcessTreeAccess(root, lateChild);
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    int port = Integer.parseInt(builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_PORT"));
                    String token = builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_TOKEN");
                    workerThread.set(Thread.ofPlatform()
                            .name("fake-playwright-late-child-tree")
                            .start(() -> runGracefullyClosingWorker(
                                    port,
                                    token,
                                    () -> { },
                                    workerFailure)));
                    return process;
                },
                processTreeAccess);
        UUID campaignId = UUID.randomUUID();
        Set<SofascoreEndpointType> allowlist =
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS);

        PlaywrightProviderCampaign campaign = supervisor.open(campaignId, allowlist);
        assertThat(processTreeAccess.captureCalls()).hasValue(1);

        campaign.close();

        assertThat(processTreeAccess.captureCalls()).hasValueGreaterThanOrEqualTo(2);
        assertThat(lateChild.destroyCalls()).hasPositiveValue();
        assertThat(lateChild.alive()).isFalse();
        assertThat(supervisor.activeCampaignId()).isEmpty();
        workerThread.get().join(2_000);
        assertThat(workerThread.get().isAlive()).isFalse();
        assertThat(workerFailure.get()).isNull();
    }

    @Test
    void captureFailureBeforeCloseLeavesTheChannelOpenAndTheCleanupRetryable()
            throws Exception {
        ProviderPlaywrightProperties properties = enabledProperties("retryable-capture.jar");
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(663L, rootStartedAt, true, true);
        OwnedHandle child = ownedHandle(
                664L, rootStartedAt.plusMillis(1), true, true);
        Process process = process(root.handle());
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.of(rootStartedAt));
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        var processTreeAccess = new FailingProcessTreeAccess(root, child, 2);
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    int port = Integer.parseInt(builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_PORT"));
                    String token = builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_TOKEN");
                    workerThread.set(Thread.ofPlatform()
                            .name("fake-playwright-retryable-capture")
                            .start(() -> runGracefullyClosingWorker(
                                    port, token, () -> { }, workerFailure)));
                    return process;
                },
                processTreeAccess);
        UUID campaignId = UUID.randomUUID();
        Set<SofascoreEndpointType> allowlist =
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS);
        PlaywrightProviderCampaign campaign = supervisor.open(campaignId, allowlist);

        assertThatThrownBy(campaign::close)
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.RUNTIME_FAILURE);

        assertThat(supervisor.activeCampaignId()).contains(campaignId);
        assertThat(workerThread.get().isAlive()).isTrue();
        assertThat(root.destroyCalls()).hasValue(0);
        assertThat(child.destroyCalls()).hasValue(0);

        assertThatThrownBy(() -> campaign.execute(
                PlaywrightProviderRequest.scheduledEvents(
                        java.time.LocalDate.of(2026, 8, 28), 1)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
        campaign.close();
        campaign.close();

        assertThat(supervisor.activeCampaignId()).isEmpty();
        workerThread.get().join(2_000);
        assertThat(workerThread.get().isAlive()).isFalse();
        assertThat(root.alive()).isFalse();
        assertThat(child.alive()).isFalse();
        assertThat(workerFailure.get()).isNull();
    }

    @Test
    void captureFailureAfterRootSignalKillsRetainedInventoryAndKeepsCampaignPublished()
            throws Exception {
        ProviderPlaywrightProperties properties = enabledProperties("post-signal-capture.jar");
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(665L, rootStartedAt, true, true);
        OwnedHandle child = ownedHandle(
                666L, rootStartedAt.plusMillis(1), false, true);
        Process process = process(root.handle());
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.of(rootStartedAt));
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        var processTreeAccess = new FailingProcessTreeAccess(root, child, 4);
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    int port = Integer.parseInt(builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_PORT"));
                    String token = builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_TOKEN");
                    workerThread.set(Thread.ofPlatform()
                            .name("fake-playwright-post-signal-capture")
                            .start(() -> runIdleWorker(
                                    port, token, () -> { }, workerFailure)));
                    return process;
                },
                processTreeAccess);
        UUID campaignId = UUID.randomUUID();
        Set<SofascoreEndpointType> allowlist =
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS);
        supervisor.open(campaignId, allowlist);

        PlaywrightProviderStopReceipt receipt = supervisor.stopCampaign(campaignId, allowlist);

        assertThat(receipt.activeCampaignSignalled()).isTrue();
        workerThread.get().join(2_000);
        assertThat(workerThread.get().isAlive()).isFalse();
        assertThat(root.destroyCalls()).hasPositiveValue();
        assertThat(child.forceCalls()).hasPositiveValue();
        assertThat(child.alive()).isFalse();
        assertThat(supervisor.activeCampaignId()).contains(campaignId);
        assertThat(workerFailure.get()).isNull();
    }

    @Test
    void rootDeathBeforeFirstPostReadyInventoryIsInconclusiveAndRemainsPublished()
            throws Exception {
        ProviderPlaywrightProperties properties = enabledProperties("dead-after-ready.jar");
        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(68L, rootStartedAt, true, true);
        Process process = process(root.handle());
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.of(rootStartedAt));
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    int port = Integer.parseInt(builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_PORT"));
                    String token = builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_TOKEN");
                    workerThread.set(Thread.ofPlatform()
                            .name("fake-playwright-dead-after-ready")
                            .start(() -> runIdleWorker(
                                    port,
                                    token,
                                    () -> root.alive().set(false),
                                    workerFailure)));
                    return process;
                },
                new ChildJvmPlaywrightProviderSupervisor.SystemProcessTreeAccess());
        UUID campaignId = UUID.randomUUID();

        assertThatThrownBy(() -> supervisor.open(
                campaignId,
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure")
                .isEqualTo(PlaywrightProviderFailure.RUNTIME_FAILURE);

        assertThat(supervisor.activeCampaignId()).contains(campaignId);
        assertThat(workerThread.get().isAlive()).isTrue();

        root.alive().set(true);
        supervisor.stopCampaign(
                campaignId,
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        awaitNoActiveCampaign(supervisor);
        workerThread.get().join(2_000);
        assertThat(workerThread.get().isAlive()).isFalse();
        assertThat(workerFailure.get()).isNull();
    }

    @Test
    void activeStopIsAcknowledgedAndCancelsAnInFlightRequestBeforeCleanupCompletes()
            throws Exception {
        Path workerJar = temporaryDirectory.resolve("test-worker.jar");
        Files.write(workerJar, new byte[0]);
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        properties.setWorkerJar(workerJar);
        properties.setStartupTimeout(Duration.ofSeconds(2));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setGracefulCloseTimeout(Duration.ofSeconds(1));

        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z");
        OwnedHandle root = ownedHandle(71L, rootStartedAt, true, true);
        Process process = process(root.handle());
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.of(rootStartedAt));
        CountDownLatch requestReceived = new CountDownLatch(1);
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        AtomicInteger starts = new AtomicInteger();
        var processTreeAccess = new RealTimeProcessTreeAccess(root.owned());
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    starts.incrementAndGet();
                    int port = Integer.parseInt(builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_PORT"));
                    String token = builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_TOKEN");
                    Thread thread = Thread.ofPlatform()
                            .name("fake-playwright-worker")
                            .start(() -> runBlockingWorker(
                                    port, token, requestReceived, workerFailure));
                    workerThread.set(thread);
                    return process;
                },
                processTreeAccess);

        UUID campaignId = UUID.randomUUID();
        PlaywrightProviderCampaign campaign = supervisor.open(
                campaignId, Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        CompletableFuture<Throwable> execution = CompletableFuture.supplyAsync(() -> {
            try {
                campaign.execute(PlaywrightProviderRequest.scheduledEvents(
                        LocalDate.of(2026, 8, 27), 1));
                return null;
            }
            catch (Throwable failure) {
                return failure;
            }
        });
        assertThat(requestReceived.await(1, TimeUnit.SECONDS)).isTrue();

        long stopStartedAt = System.nanoTime();
        PlaywrightProviderStopReceipt receipt = supervisor.stopCampaign(
                campaignId,
                Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        Throwable executionFailure = execution.get(
                ChildJvmPlaywrightProviderSupervisor.IN_FLIGHT_CANCELLATION_MAX.toMillis(),
                TimeUnit.MILLISECONDS);
        Duration cancellationLatency = Duration.ofNanos(System.nanoTime() - stopStartedAt);

        assertThat(receipt.activeCampaignSignalled()).isTrue();
        assertThat(receipt.acknowledgementLatency())
                .isLessThanOrEqualTo(
                        ChildJvmPlaywrightProviderSupervisor.STOP_ACKNOWLEDGEMENT_MAX);
        assertThat(executionFailure).isInstanceOf(PlaywrightProviderException.class);
        assertThat(((PlaywrightProviderException) executionFailure).failure())
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
        assertThat(cancellationLatency)
                .isLessThanOrEqualTo(
                        ChildJvmPlaywrightProviderSupervisor.IN_FLIGHT_CANCELLATION_MAX);
        awaitNoActiveCampaign(supervisor);
        assertThat(root.alive()).isFalse();
        assertThat(processTreeAccess.captureCalls()).hasPositiveValue();
        assertThat(starts).hasValue(1);
        workerThread.get().join(2_000);
        assertThat(workerThread.get().isAlive()).isFalse();
        assertThat(workerFailure.get()).isNull();
    }

    @Test
    void stopAttributionIsStrictAcrossEveryImplementedEndpointCampaign()
            throws Exception {
        assertCrossEndpointStopIgnored(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                81L);
        assertCrossEndpointStopIgnored(
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                82L);
        assertCrossEndpointStopIgnored(
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                83L);
        assertCrossEndpointStopIgnored(
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                84L);
        assertCrossEndpointStopIgnored(
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                85L);
        assertCrossEndpointStopIgnored(
                SofascoreEndpointType.EVENT_LINEUPS,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                86L);
    }

    @Test
    void j5StopRequiresTheExactThreeEndpointAllowlist() throws Exception {
        Set<SofascoreEndpointType> j5Allowlist = Set.of(
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS);

        assertStopAttributionIsStrict(
                j5Allowlist,
                PlaywrightProviderRequest.eventStatistics(16_386_245L),
                Set.of(SofascoreEndpointType.EVENT_STATISTICS),
                "J5_EVENT_DATA",
                87L);
    }

    private void assertCrossEndpointStopIgnored(
            SofascoreEndpointType activeEndpoint,
            SofascoreEndpointType attemptedStopEndpoint,
            long processId) throws Exception {
        PlaywrightProviderRequest request = switch (activeEndpoint) {
            case SCHEDULED_EVENTS -> PlaywrightProviderRequest.scheduledEvents(
                    LocalDate.of(2026, 8, 27), 1);
            case TOURNAMENT_SCHEDULED_EVENTS ->
                    PlaywrightProviderRequest.tournamentScheduledEvents(
                            LocalDate.of(2026, 8, 27), 119_880L);
            case EVENT_DETAILS -> PlaywrightProviderRequest.eventDetails(16_386_245L);
            case EVENT_STATISTICS -> PlaywrightProviderRequest.eventStatistics(16_386_245L);
            case EVENT_INCIDENTS -> PlaywrightProviderRequest.eventIncidents(16_386_245L);
            case EVENT_LINEUPS -> PlaywrightProviderRequest.eventLineups(16_386_245L);
            default -> throw new IllegalArgumentException("unsupported test endpoint");
        };
        assertStopAttributionIsStrict(
                Set.of(activeEndpoint),
                request,
                Set.of(attemptedStopEndpoint),
                activeEndpoint.name(),
                processId);
    }

    private void assertStopAttributionIsStrict(
            Set<SofascoreEndpointType> activeAllowlist,
            PlaywrightProviderRequest request,
            Set<SofascoreEndpointType> attemptedStopAllowlist,
            String campaignLabel,
            long processId) throws Exception {
        Path workerJar = temporaryDirectory.resolve(campaignLabel + "-worker.jar");
        Files.write(workerJar, new byte[0]);
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        properties.setWorkerJar(workerJar);
        properties.setStartupTimeout(Duration.ofSeconds(2));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setGracefulCloseTimeout(Duration.ofSeconds(1));

        Instant rootStartedAt = Instant.parse("2026-08-27T08:00:00Z")
                .plusSeconds(processId);
        OwnedHandle root = ownedHandle(processId, rootStartedAt, true, true);
        Process process = process(root.handle());
        ProcessHandle.Info processInfo = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(processInfo);
        when(processInfo.startInstant()).thenReturn(Optional.of(rootStartedAt));
        CountDownLatch requestReceived = new CountDownLatch(1);
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        var processTreeAccess = new RealTimeProcessTreeAccess(root.owned());
        var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    int port = Integer.parseInt(builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_PORT"));
                    String token = builder.environment().get(
                            "SOFASCORE_PLAYWRIGHT_IPC_TOKEN");
                    Thread thread = Thread.ofPlatform()
                            .name("fake-playwright-worker-" + campaignLabel)
                            .start(() -> runBlockingWorker(
                                    port, token, requestReceived, workerFailure));
                    workerThread.set(thread);
                    return process;
                },
                processTreeAccess);

        UUID campaignId = UUID.randomUUID();
        PlaywrightProviderCampaign campaign = supervisor.open(
                campaignId, activeAllowlist);
        CompletableFuture<Throwable> execution = CompletableFuture.supplyAsync(() -> {
            try {
                campaign.execute(request);
                return null;
            }
            catch (Throwable failure) {
                return failure;
            }
        });
        assertThat(requestReceived.await(1, TimeUnit.SECONDS)).isTrue();

        PlaywrightProviderStopReceipt wrongRequest = supervisor.stopCampaign(
                UUID.randomUUID(), activeAllowlist);
        PlaywrightProviderStopReceipt ignored = supervisor.stopCampaign(
                campaignId, attemptedStopAllowlist);

        assertThat(wrongRequest.activeCampaignSignalled()).isFalse();
        assertThat(wrongRequest.campaignId()).isNull();
        assertThat(ignored.activeCampaignSignalled()).isFalse();
        assertThat(ignored.campaignId()).isNull();
        assertThat(supervisor.activeCampaignId()).contains(campaignId);
        assertThat(execution.isDone()).isFalse();
        assertThat(root.alive()).isTrue();

        PlaywrightProviderStopReceipt exact = supervisor.stopCampaign(
                campaignId, activeAllowlist);
        Throwable executionFailure = execution.get(
                ChildJvmPlaywrightProviderSupervisor.IN_FLIGHT_CANCELLATION_MAX.toMillis(),
                TimeUnit.MILLISECONDS);

        assertThat(exact.activeCampaignSignalled()).isTrue();
        assertThat(exact.campaignId()).isEqualTo(campaignId);
        assertThat(executionFailure).isInstanceOf(PlaywrightProviderException.class);
        assertThat(((PlaywrightProviderException) executionFailure).failure())
                .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
        awaitNoActiveCampaign(supervisor);
        assertThat(root.alive()).isFalse();
        workerThread.get().join(2_000);
        assertThat(workerThread.get().isAlive()).isFalse();
        assertThat(workerFailure.get()).isNull();
    }

    private static ChildJvmPlaywrightProviderSupervisor supervisor(
            ProviderPlaywrightProperties properties,
            AtomicInteger starts) {
        return new ChildJvmPlaywrightProviderSupervisor(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                builder -> {
                    starts.incrementAndGet();
                    throw new AssertionError("a standard test must never start the worker");
                });
    }

    private ProviderPlaywrightProperties enabledProperties(String workerName)
            throws Exception {
        Path workerJar = temporaryDirectory.resolve(workerName);
        Files.write(workerJar, new byte[0]);
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        properties.setWorkerJar(workerJar);
        properties.setStartupTimeout(Duration.ofSeconds(2));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setGracefulCloseTimeout(Duration.ofSeconds(1));
        return properties;
    }

    private static CompletableFuture<Throwable> openFailure(
            ChildJvmPlaywrightProviderSupervisor supervisor,
            UUID campaignId,
            SofascoreEndpointType endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                supervisor.open(campaignId, Set.of(endpoint));
                return null;
            }
            catch (Throwable failure) {
                return failure;
            }
        });
    }

    private static void awaitLatch(CountDownLatch latch) throws java.io.IOException {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new java.io.IOException("test launch latch timed out");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException("test launch interrupted", exception);
        }
    }

    private static Process process(ProcessHandle root) {
        Process process = mock(Process.class);
        when(process.toHandle()).thenReturn(root);
        return process;
    }

    private static void runBlockingWorker(
            int port,
            String token,
            CountDownLatch requestReceived,
            AtomicReference<Throwable> failure) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"), port), 1_000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(ChildJvmPlaywrightProviderSupervisor.MAGIC);
            output.writeInt(ChildJvmPlaywrightProviderSupervisor.VERSION);
            output.writeUTF(token);
            output.flush();
            try (DataInputStream input = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()))) {
                assertThat(input.readUnsignedByte())
                        .isEqualTo(ChildJvmPlaywrightProviderSupervisor.START);
                output.writeByte(ChildJvmPlaywrightProviderSupervisor.READY);
                output.flush();
                assertThat(input.readUnsignedByte())
                        .isEqualTo(ChildJvmPlaywrightProviderSupervisor.GET);
                String endpoint = input.readUTF();
                switch (SofascoreEndpointType.valueOf(endpoint)) {
                    case SCHEDULED_EVENTS -> {
                        assertThat(input.readUTF()).isEqualTo("2026-08-27");
                        assertThat(input.readInt()).isEqualTo(1);
                    }
                    case TOURNAMENT_SCHEDULED_EVENTS -> {
                        assertThat(input.readUTF()).isEqualTo("2026-08-27");
                        assertThat(input.readLong()).isEqualTo(119_880L);
                    }
                    case EVENT_DETAILS, EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS ->
                            assertThat(input.readLong()).isEqualTo(16_386_245L);
                    default -> throw new AssertionError("unexpected endpoint: " + endpoint);
                }
                assertThat(input.readInt()).isEqualTo(2_000);
                requestReceived.countDown();
                assertThat(input.read()).isEqualTo(-1);
            }
        }
        catch (Throwable exception) {
            failure.set(exception);
            requestReceived.countDown();
        }
    }

    private static void runIdleWorker(
            int port,
            String token,
            Runnable beforeReady,
            AtomicReference<Throwable> failure) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"), port), 1_000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(ChildJvmPlaywrightProviderSupervisor.MAGIC);
            output.writeInt(ChildJvmPlaywrightProviderSupervisor.VERSION);
            output.writeUTF(token);
            output.flush();
            try (DataInputStream input = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()))) {
                assertThat(input.readUnsignedByte())
                        .isEqualTo(ChildJvmPlaywrightProviderSupervisor.START);
                beforeReady.run();
                output.writeByte(ChildJvmPlaywrightProviderSupervisor.READY);
                output.flush();
                assertThat(input.read()).isEqualTo(-1);
            }
        }
        catch (Throwable exception) {
            failure.set(exception);
        }
    }

    private static void runGracefullyClosingWorker(
            int port,
            String token,
            Runnable beforeClosed,
            AtomicReference<Throwable> failure) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"), port), 1_000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(ChildJvmPlaywrightProviderSupervisor.MAGIC);
            output.writeInt(ChildJvmPlaywrightProviderSupervisor.VERSION);
            output.writeUTF(token);
            output.flush();
            try (DataInputStream input = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()))) {
                assertThat(input.readUnsignedByte())
                        .isEqualTo(ChildJvmPlaywrightProviderSupervisor.START);
                output.writeByte(ChildJvmPlaywrightProviderSupervisor.READY);
                output.flush();
                assertThat(input.readUnsignedByte())
                        .isEqualTo(ChildJvmPlaywrightProviderSupervisor.CLOSE);
                beforeClosed.run();
                output.writeByte(ChildJvmPlaywrightProviderSupervisor.CLOSED);
                output.flush();
                assertThat(input.read()).isEqualTo(-1);
            }
        }
        catch (Throwable exception) {
            failure.set(exception);
        }
    }

    private static void assertUnauthenticatedRootIsNotEnumerated(
            Instant expected,
            boolean alive,
            Optional<Instant> observedStart,
            int expectedUnverifiedCount) {
        Process process = mock(Process.class);
        ProcessHandle root = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(process.toHandle()).thenReturn(root);
        when(root.isAlive()).thenReturn(alive);
        when(root.info()).thenReturn(info);
        when(info.startInstant()).thenReturn(observedStart);
        var access = new ChildJvmPlaywrightProviderSupervisor.SystemProcessTreeAccess();

        var snapshot = access.capture(process, expected);

        assertThat(snapshot.rootIdentityAuthenticated()).isFalse();
        assertThat(snapshot.unverifiedAliveProcessCount())
                .isEqualTo(expectedUnverifiedCount);
        assertThat(snapshot.ownedProcesses()).isEmpty();
        verify(root, never()).descendants();
    }

    private static void awaitNoActiveCampaign(
            ChildJvmPlaywrightProviderSupervisor supervisor) throws InterruptedException {
        long deadline = System.nanoTime()
                + ChildJvmPlaywrightProviderSupervisor.PROCESS_TREE_CLEANUP_MAX.toNanos();
        while (supervisor.activeCampaignId().isPresent() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(supervisor.activeCampaignId()).isEmpty();
    }

    private static void awaitValueAtLeast(AtomicInteger value, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (value.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(value).hasValueGreaterThanOrEqualTo(expected);
    }

    private static OwnedHandle ownedHandle(
            long pid,
            Instant startedAt,
            boolean exitsOnDestroy,
            boolean exitsOnDestroyForcibly) {
        AtomicBoolean alive = new AtomicBoolean(true);
        AtomicInteger destroyCalls = new AtomicInteger();
        AtomicInteger forceCalls = new AtomicInteger();
        ProcessHandle handle = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(handle.pid()).thenReturn(pid);
        when(handle.info()).thenReturn(info);
        when(info.startInstant()).thenReturn(Optional.of(startedAt));
        when(handle.isAlive()).thenAnswer(ignored -> alive.get());
        when(handle.destroy()).thenAnswer(ignored -> {
            destroyCalls.incrementAndGet();
            if (exitsOnDestroy) {
                alive.set(false);
            }
            return exitsOnDestroy;
        });
        when(handle.destroyForcibly()).thenAnswer(ignored -> {
            forceCalls.incrementAndGet();
            if (exitsOnDestroyForcibly) {
                alive.set(false);
            }
            return exitsOnDestroyForcibly;
        });
        return new OwnedHandle(
                handle,
                new ChildJvmPlaywrightProviderSupervisor.OwnedProcess(handle, startedAt),
                alive,
                destroyCalls,
                forceCalls);
    }

    private record OwnedHandle(
            ProcessHandle handle,
            ChildJvmPlaywrightProviderSupervisor.OwnedProcess owned,
            AtomicBoolean alive,
            AtomicInteger destroyCalls,
            AtomicInteger forceCalls) {
    }

    private static final class SimulatedProcessTreeAccess
            implements ChildJvmPlaywrightProviderSupervisor.ProcessTreeAccess {

        private final List<List<ChildJvmPlaywrightProviderSupervisor.OwnedProcess>> snapshots;
        private final AtomicInteger captures = new AtomicInteger();
        private final AtomicLong nanoTime = new AtomicLong();

        private SimulatedProcessTreeAccess(
                List<List<ChildJvmPlaywrightProviderSupervisor.OwnedProcess>> snapshots) {
            this.snapshots = List.copyOf(snapshots);
        }

        @Override
        public long nanoTime() {
            return nanoTime.get();
        }

        @Override
        public ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot capture(
                Process process,
                Instant rootStartedAt) {
            int index = Math.min(captures.getAndIncrement(), snapshots.size() - 1);
            return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot.exact(
                    snapshots.get(index));
        }

        @Override
        public void pause(Duration duration) {
            nanoTime.addAndGet(duration.toNanos());
        }
    }

    private static final class RealTimeProcessTreeAccess
            implements ChildJvmPlaywrightProviderSupervisor.ProcessTreeAccess {

        private final ChildJvmPlaywrightProviderSupervisor.OwnedProcess root;
        private final AtomicInteger captureCalls = new AtomicInteger();

        private RealTimeProcessTreeAccess(
                ChildJvmPlaywrightProviderSupervisor.OwnedProcess root) {
            this.root = root;
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot capture(
                Process process,
                Instant rootStartedAt) {
            captureCalls.incrementAndGet();
            return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot.exact(
                    List.of(root));
        }

        @Override
        public void pause(Duration duration) {
            try {
                TimeUnit.NANOSECONDS.sleep(duration.toNanos());
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private AtomicInteger captureCalls() {
            return captureCalls;
        }
    }

    private static final class UnverifiedProcessTreeAccess
            implements ChildJvmPlaywrightProviderSupervisor.ProcessTreeAccess {

        private final ChildJvmPlaywrightProviderSupervisor.OwnedProcess root;
        private final AtomicLong nanoTime = new AtomicLong();

        private UnverifiedProcessTreeAccess(
                ChildJvmPlaywrightProviderSupervisor.OwnedProcess root) {
            this.root = root;
        }

        @Override
        public long nanoTime() {
            return nanoTime.get();
        }

        @Override
        public ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot capture(
                Process process,
                Instant rootStartedAt) {
            return new ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot(
                    List.of(root),
                    1,
                    true);
        }

        @Override
        public void pause(Duration duration) {
            nanoTime.addAndGet(duration.toNanos());
        }
    }

    private static final class RootGoneProcessTreeAccess
            implements ChildJvmPlaywrightProviderSupervisor.ProcessTreeAccess {

        private final AtomicInteger captureCalls = new AtomicInteger();
        private final AtomicLong nanoTime = new AtomicLong();

        @Override
        public long nanoTime() {
            return nanoTime.get();
        }

        @Override
        public ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot capture(
                Process process,
                Instant rootStartedAt) {
            captureCalls.incrementAndGet();
            return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot
                    .rootNotAuthenticated(0);
        }

        @Override
        public void pause(Duration duration) {
            nanoTime.addAndGet(duration.toNanos());
        }

        private AtomicInteger captureCalls() {
            return captureCalls;
        }
    }

    private static final class RootAwareProcessTreeAccess
            implements ChildJvmPlaywrightProviderSupervisor.ProcessTreeAccess {

        private final OwnedHandle root;
        private final OwnedHandle child;
        private final AtomicInteger captureCalls = new AtomicInteger();

        private RootAwareProcessTreeAccess(OwnedHandle root, OwnedHandle child) {
            this.root = root;
            this.child = child;
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot capture(
                Process process,
                Instant rootStartedAt) {
            captureCalls.incrementAndGet();
            if (!root.alive().get()) {
                return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot
                        .rootNotAuthenticated(0);
            }
            return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot.exact(
                    List.of(root.owned(), child.owned()));
        }

        @Override
        public void pause(Duration duration) {
            try {
                TimeUnit.NANOSECONDS.sleep(duration.toNanos());
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private AtomicInteger captureCalls() {
            return captureCalls;
        }
    }

    private static final class LateChildProcessTreeAccess
            implements ChildJvmPlaywrightProviderSupervisor.ProcessTreeAccess {

        private final OwnedHandle root;
        private final OwnedHandle lateChild;
        private final AtomicInteger captureCalls = new AtomicInteger();

        private LateChildProcessTreeAccess(OwnedHandle root, OwnedHandle lateChild) {
            this.root = root;
            this.lateChild = lateChild;
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot capture(
                Process process,
                Instant rootStartedAt) {
            int capture = captureCalls.incrementAndGet();
            if (!root.alive().get()) {
                return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot
                        .rootNotAuthenticated(0);
            }
            return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot.exact(
                    capture <= 2
                            ? List.of(root.owned())
                            : List.of(root.owned(), lateChild.owned()));
        }

        @Override
        public void pause(Duration duration) {
            try {
                TimeUnit.NANOSECONDS.sleep(duration.toNanos());
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private AtomicInteger captureCalls() {
            return captureCalls;
        }
    }

    private static final class FailingProcessTreeAccess
            implements ChildJvmPlaywrightProviderSupervisor.ProcessTreeAccess {

        private final OwnedHandle root;
        private final OwnedHandle child;
        private final int failingCapture;
        private final AtomicInteger captureCalls = new AtomicInteger();

        private FailingProcessTreeAccess(
                OwnedHandle root,
                OwnedHandle child,
                int failingCapture) {
            this.root = root;
            this.child = child;
            this.failingCapture = failingCapture;
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot capture(
                Process process,
                Instant rootStartedAt) {
            int capture = captureCalls.incrementAndGet();
            if (capture == failingCapture) {
                throw new IllegalStateException("deterministic process inventory failure");
            }
            if (!root.alive().get()) {
                return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot
                        .rootNotAuthenticated(0);
            }
            return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot.exact(
                    List.of(root.owned(), child.owned()));
        }

        @Override
        public void pause(Duration duration) {
            try {
                TimeUnit.NANOSECONDS.sleep(duration.toNanos());
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class ShutdownAwareProcessTreeAccess
            implements ChildJvmPlaywrightProviderSupervisor.ProcessTreeAccess {

        private final ChildJvmPlaywrightProviderSupervisor.OwnedProcess root;
        private final CountDownLatch shutdownObserved = new CountDownLatch(1);

        private ShutdownAwareProcessTreeAccess(
                ChildJvmPlaywrightProviderSupervisor.OwnedProcess root) {
            this.root = root;
        }

        @Override
        public long nanoTime() {
            shutdownObserved.countDown();
            return System.nanoTime();
        }

        @Override
        public ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot capture(
                Process process,
                Instant rootStartedAt) {
            return ChildJvmPlaywrightProviderSupervisor.ProcessTreeSnapshot.exact(
                    List.of(root));
        }

        @Override
        public void pause(Duration duration) {
            try {
                TimeUnit.NANOSECONDS.sleep(duration.toNanos());
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private CountDownLatch shutdownObserved() {
            return shutdownObserved;
        }
    }
}
