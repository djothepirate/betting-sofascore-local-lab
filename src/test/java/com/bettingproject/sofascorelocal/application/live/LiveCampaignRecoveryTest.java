package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.config.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.port.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local recovery commands, with a real in-process exclusion and no provider transport. */
class LiveCampaignRecoveryTest {
    private static final Instant NOW = Instant.parse("2026-09-08T15:00:00Z");
    private final UUID id = UUID.randomUUID();
    private final Owner former = new Owner(UUID.randomUUID(), 987654L, NOW.minusSeconds(3600));
    private final Guard expected = new Guard("CLEANUP_REQUIRED", id, former, 40, NOW.minusSeconds(60));
    private final AtomicReference<Guard> current = new AtomicReference<>(expected);
    private final LiveCampaignStore store = mock(LiveCampaignStore.class);
    private final ProviderCampaignGuardStore guard = mock(ProviderCampaignGuardStore.class);
    private final LiveOrphanProcessProbe probe = mock(LiveOrphanProcessProbe.class);
    private final PlaywrightProviderSupervisor supervisor = mock(PlaywrightProviderSupervisor.class);
    private final PlaywrightProviderCampaignFactory factory = mock(PlaywrightProviderCampaignFactory.class);
    private final ManualProviderRequestCoordinator coordinator = new ManualProviderRequestCoordinator(new SofascoreProperties());
    private final ProviderPlaywrightProperties playwright = new ProviderPlaywrightProperties();
    private final LiveCampaignService service;

    LiveCampaignRecoveryTest() {
        playwright.setWorkerJar(Path.of("worker-test.jar"));
        when(guard.snapshot()).thenAnswer(ignored -> current.get());
        when(supervisor.activeCampaignId()).thenReturn(Optional.empty());
        when(store.find(id)).thenReturn(Optional.of(campaign("INTERRUPTED", List.of())));
        service = new LiveCampaignService(new SofascoreProperties(), playwright, new LiveCampaignProperties(),
                mock(LiveAdmissionPolicy.class), store, mock(CanonicalEventStore.class), coordinator,
                guard, factory, supervisor, mock(LiveResponseProcessor.class), Clock.fixed(NOW, ZoneOffset.UTC), probe);
    }

    @Test
    void readsExposeTheDurableOwnerWithoutScanningOrMutatingAnything() {
        assertThat(service.orphanCleanupGuard(id)).contains(expected);
        assertThat(service.providerCleanupCampaignId()).contains(id);
        assertThat(service.orphanCleanupGuard(UUID.randomUUID())).isEmpty();
        verifyNoInteractions(probe, factory);
        verify(store, never()).completeOrphanCleanup(any(), any());
        verify(guard, never()).releaseAfterVerifiedCleanup(any(), any());
    }

    @Test
    void explicitClosureProvesProcessesAbsentBeforeAtomicStoreReconciliation() {
        service.finalizeInterruptedCleanup(id, 40);
        var order = inOrder(probe, store);
        order.verify(probe).requireAbsent(former, playwright.getWorkerJar());
        order.verify(store).completeOrphanCleanup(expected, NOW);
        verify(guard, never()).releaseAfterVerifiedCleanup(any(), any());
        verifyNoInteractions(factory);
        verify(supervisor, never()).stopCampaign(any(), any());
        assertThat(service.state(id).state()).isEqualTo("INTERRUPTED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIVE_CLEANUP_OWNER_ACTIVE", "LIVE_CLEANUP_PROCESS_ACTIVE", "LIVE_CLEANUP_PROCESS_UNVERIFIED"})
    void anUnprovenProcessClosureNeverReleasesOrRewritesTheLedger(String failure) {
        doThrow(new IllegalStateException(failure)).when(probe).requireAbsent(any(), any());
        assertThatThrownBy(() -> service.finalizeInterruptedCleanup(id, 40)).hasMessage(failure);
        verify(store, never()).completeOrphanCleanup(any(), any());
        verifyNoInteractions(factory);
    }

    @Test
    void staleGenerationAndWrongCampaignAreRefusedBeforeProcessInspection() {
        assertThatThrownBy(() -> service.finalizeInterruptedCleanup(id, 39)).hasMessage("LIVE_CLEANUP_STATE_CHANGED");
        current.set(new Guard("CLEANUP_REQUIRED", UUID.randomUUID(), former, 40, NOW));
        assertThatThrownBy(() -> service.finalizeInterruptedCleanup(id, 40)).hasMessage("LIVE_CLEANUP_STATE_CHANGED");
        verifyNoInteractions(probe, factory);
        verify(store, never()).completeOrphanCleanup(any(), any());
    }

    @Test
    void runningCampaignCannotBeRegularizedOrOfferedForOrphanCleanup() {
        when(store.find(id)).thenReturn(Optional.of(campaign("RUNNING", List.of())));
        assertThat(service.orphanCleanupGuard(id)).isEmpty();
        assertThatThrownBy(() -> service.finalizeInterruptedCleanup(id, 40)).hasMessage("LIVE_CLEANUP_STATE_CHANGED");
        verifyNoInteractions(probe, factory);
    }

    @Test
    void activeSupervisorAndAnExistingManualLeaseExcludeRecovery() {
        when(supervisor.activeCampaignId()).thenReturn(Optional.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.finalizeInterruptedCleanup(id, 40)).hasMessage("LIVE_CLEANUP_BUSY");
        when(supervisor.activeCampaignId()).thenReturn(Optional.empty());
        try (var ignored = coordinator.acquireCampaign(UUID.randomUUID())) {
            assertThatThrownBy(() -> service.finalizeInterruptedCleanup(id, 40)).hasMessage("LIVE_CLEANUP_BUSY");
        }
        verifyNoInteractions(probe, factory);
        verify(store, never()).completeOrphanCleanup(any(), any());
    }

    @Test
    void failedSqlClosureCanBeExplicitlyRetriedWithoutRestartingTransport() {
        doThrow(new IllegalStateException("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED"))
                .doNothing().when(store).completeOrphanCleanup(expected, NOW);
        assertThatThrownBy(() -> service.finalizeInterruptedCleanup(id, 40)).hasMessage("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
        service.finalizeInterruptedCleanup(id, 40);
        verify(probe, times(2)).requireAbsent(former, playwright.getWorkerJar());
        verifyNoInteractions(factory);
    }

    @Test
    void responseLostAfterCommitIsIdempotentOnlyWithTheSameGenerationAndDurableTrace() {
        current.set(new Guard("FREE", null, null, 40, NOW));
        assertThatThrownBy(() -> service.finalizeInterruptedCleanup(id, 40)).hasMessage("LIVE_CLEANUP_STATE_CHANGED");
        when(store.find(id)).thenReturn(Optional.of(campaign("INTERRUPTED",
                List.of(new Transition(8, null, "LOCAL_CLEANUP_VERIFIED", "GUARD_" + "b".repeat(64), NOW, null)))));
        service.finalizeInterruptedCleanup(id, 40);
        assertThat(service.orphanCleanupGuard(id)).isEmpty();
        current.set(new Guard("FREE", null, null, 41, NOW));
        assertThatThrownBy(() -> service.finalizeInterruptedCleanup(id, 40)).hasMessage("LIVE_CLEANUP_STATE_CHANGED");
        verifyNoInteractions(probe, factory);
        verify(store, never()).completeOrphanCleanup(any(), any());
    }

    private CampaignView campaign(String state, List<Transition> transitions) {
        return new CampaignView(mock(Manifest.class), state, "OWNER_PROCESS_ABSENT", NOW.minusSeconds(1800),
                NOW.plusSeconds(1800), 140, 2819899, 7, expected.ownership(), List.of(), List.of(), transitions);
    }
}
