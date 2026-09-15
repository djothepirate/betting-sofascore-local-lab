package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryState;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalog;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TournamentEventDiscoveryControlServiceTest {

    private static final UUID REQUEST_ID =
            UUID.fromString("7618a727-9ab0-4e96-a2de-9cd4898db29e");

    @Test
    void directClickResolvesTheExactCollectionAndCreatesNoConfirmation() {
        var catalogs = mock(J3TournamentCatalogService.class);
        var date = LocalDate.of(2026, 8, 18);
        when(catalogs.forCollection(REQUEST_ID, date)).thenReturn(catalog(option(7)));
        var control = control(Clock.systemUTC(), catalogs);

        var claim = control.claimDirect(REQUEST_ID, date, 119_880);

        assertThat(claim.selection()).isEqualTo(option(7));
        assertThat(claim.collectionDate()).isEqualTo(date);
        assertThat(control.snapshot().state()).isEqualTo(TournamentEventDiscoveryState.EXECUTING);
        assertThat(control.snapshot().confirmationPhrase()).isNull();
        assertThat(control.snapshot().expiresAt()).isNull();
        org.mockito.Mockito.verify(catalogs, org.mockito.Mockito.never()).latest();
        assertThatThrownBy(() -> control.claimDirectLocalImport(REQUEST_ID, date, 119_880))
                .isInstanceOf(TournamentEventDiscoveryControlException.class);
        control.complete(claim.requestId());
        assertThat(control.claimDirectLocalImport(REQUEST_ID, date, 119_880).selection())
                .isEqualTo(option(7));
    }

    @Test
    void directClickRejectsUnknownPhaseAndWrongCollectionDateWithoutLeavingAnIntent() {
        var catalogs = mock(J3TournamentCatalogService.class);
        var date = LocalDate.of(2026, 8, 18);
        when(catalogs.forCollection(REQUEST_ID, date)).thenReturn(catalog(option(7)));
        when(catalogs.forCollection(REQUEST_ID, date.plusDays(1))).thenReturn(catalog(option(7)));
        var control = control(Clock.systemUTC(), catalogs);

        assertThatThrownBy(() -> control.claimDirect(REQUEST_ID, date, 123))
                .isInstanceOf(TournamentEventDiscoveryControlException.class);
        assertThatThrownBy(() -> control.claimDirect(REQUEST_ID, date.plusDays(1), 119_880))
                .isInstanceOf(TournamentEventDiscoveryControlException.class);
        assertThat(control.snapshot().state()).isEqualTo(TournamentEventDiscoveryState.LOCKED);
        assertThat(control.snapshot().requestId()).isNull();
    }

    @Test
    void directLocalImportRemainsAvailableWithoutProviderTransport() {
        var catalogs = mock(J3TournamentCatalogService.class);
        var date = LocalDate.of(2026, 8, 18);
        when(catalogs.forCollection(REQUEST_ID, date)).thenReturn(catalog(option(7)));
        var control = new TournamentEventDiscoveryControlService(Clock.systemUTC(),
                () -> REQUEST_ID, () -> { throw new AssertionError("No confirmation code expected"); },
                () -> TournamentEventDiscoveryQualificationSnapshot.blocked(List.of("TRANSPORT_DISABLED")),
                () -> TournamentEventDiscoveryQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)), catalogs);

        assertThatThrownBy(() -> control.claimDirect(REQUEST_ID, date, 119_880))
                .isInstanceOf(TournamentEventDiscoveryControlException.class);
        assertThat(control.snapshot().state()).isEqualTo(TournamentEventDiscoveryState.LOCKED);
        assertThat(control.claimDirectLocalImport(REQUEST_ID, date, 119_880).selection())
                .isEqualTo(option(7));
    }

    @Test
    void concurrentDirectProviderAndImportClicksHaveOnlyOneOwner() throws Exception {
        var catalogs = mock(J3TournamentCatalogService.class);
        var date = LocalDate.of(2026, 8, 18);
        when(catalogs.forCollection(REQUEST_ID, date)).thenReturn(catalog(option(7)));
        var control = control(Clock.systemUTC(), catalogs);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var provider = executor.submit(() -> {
                awaitLatch(start);
                try { control.claimDirect(REQUEST_ID, date, 119_880); return true; }
                catch (TournamentEventDiscoveryControlException rejected) {
                    assertThat(rejected.error()).isEqualTo(TournamentEventDiscoveryControlError.ACTIVE_REQUEST_EXISTS);
                    return false;
                }
            });
            var local = executor.submit(() -> {
                awaitLatch(start);
                try { control.claimDirectLocalImport(REQUEST_ID, date, 119_880); return true; }
                catch (TournamentEventDiscoveryControlException rejected) {
                    assertThat(rejected.error()).isEqualTo(TournamentEventDiscoveryControlError.ACTIVE_REQUEST_EXISTS);
                    return false;
                }
            });
            start.countDown();
            assertThat(List.of(provider.get(5, TimeUnit.SECONDS), local.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void directClicksPreserveFailureAndOperatorStopLocks() {
        var catalogs = mock(J3TournamentCatalogService.class);
        var date = LocalDate.of(2026, 8, 18);
        when(catalogs.forCollection(REQUEST_ID, date)).thenReturn(catalog(option(7)));
        for (boolean stop : List.of(true, false)) {
            var control = control(Clock.systemUTC(), catalogs);
            var claim = control.claimDirect(REQUEST_ID, date, 119_880);
            if (stop) control.stop(); else control.fail(claim.requestId(), "HTTP_FORBIDDEN");
            assertThatThrownBy(() -> control.claimDirectLocalImport(REQUEST_ID, date, 119_880))
                    .isInstanceOf(TournamentEventDiscoveryControlException.class);
            assertThatThrownBy(() -> control.claimDirect(REQUEST_ID, date, 119_880))
                    .isInstanceOf(TournamentEventDiscoveryControlException.class);
        }
    }

    @Test
    void preparesWithoutTransportAndClaimsOnlyTheExactServerResolvedSelection() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T08:00:00Z"));
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog(option(7)));
        var control = control(clock, catalogService);

        var prepared = control.prepare(119_880);

        assertThat(prepared.state())
                .isEqualTo(TournamentEventDiscoveryState.AWAITING_CONFIRMATION);
        assertThat(prepared.confirmationPhrase()).isEqualTo(
                "CONFIRMER EVENEMENTS TOURNOI 119880 UNIQUE 7 DATE 2026-08-18 000042");
        assertThat(prepared.selection().uniqueTournamentId()).isEqualTo(7);
        assertThat(prepared.expiresAt()).isEqualTo(clock.instant().plusSeconds(300));

        var claim = control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true);

        assertThat(claim.providerOrigin().toString())
                .isEqualTo(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        assertThat(claim.collectionDate()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(claim.selection()).isEqualTo(option(7));
        assertThat(control.executionMayContinue(REQUEST_ID)).isTrue();
    }

    @Test
    void rejectsAProviderClaimWithoutConsumingThePlaywrightIndependentLocalClaim() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T08:00:00Z"));
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog(option(7)));
        SofascoreProperties sofascore = new SofascoreProperties();
        sofascore.setEnabled(true);
        sofascore.setJ3QualificationEnabled(true);
        sofascore.setTournamentEventDiscoveryEnabled(true);
        sofascore.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        sofascore.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS));
        TournamentEventDiscoveryQualificationPolicy policy =
                new TournamentEventDiscoveryQualificationPolicy(
                        sofascore, new ProviderPlaywrightProperties());
        var control = new TournamentEventDiscoveryControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                policy::snapshot,
                policy::localImportSnapshot,
                catalogService);
        var prepared = control.prepare(119_880);

        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true))
                .isInstanceOf(TournamentEventDiscoveryControlException.class)
                .extracting(exception -> ((TournamentEventDiscoveryControlException) exception)
                        .error())
                .isEqualTo(
                        TournamentEventDiscoveryControlError.PROVIDER_TRANSPORT_UNAVAILABLE);

        var afterProviderRejection = control.snapshot();
        assertThat(afterProviderRejection.state())
                .isEqualTo(TournamentEventDiscoveryState.AWAITING_CONFIRMATION);
        assertThat(afterProviderRejection.confirmationPhrase())
                .isEqualTo(prepared.confirmationPhrase());
        assertThat(afterProviderRejection.providerTransportAvailable()).isFalse();
        assertThat(afterProviderRejection.localImportAvailable()).isTrue();
        assertThat(control.executionMayContinue(REQUEST_ID)).isFalse();

        var localClaim = control.confirmAndClaimLocalImport(
                REQUEST_ID, prepared.confirmationPhrase(), true);

        assertThat(localClaim.requestId()).isEqualTo(REQUEST_ID);
        assertThat(localClaim.selection()).isEqualTo(option(7));
        assertThat(control.executionMayContinue(REQUEST_ID)).isTrue();
    }

    @Test
    void rejectsTamperingAndLocksWhenTheCatalogChangesBeforeConfirmation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T08:00:00Z"));
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest())
                .thenReturn(catalog(option(7)))
                .thenReturn(catalog(option(8)));
        var control = control(clock, catalogService);
        var prepared = control.prepare(119_880);

        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true))
                .isInstanceOf(TournamentEventDiscoveryControlException.class)
                .extracting(exception -> ((TournamentEventDiscoveryControlException) exception)
                        .error())
                .isEqualTo(TournamentEventDiscoveryControlError.CATALOG_CHANGED);
        assertThat(control.snapshot().state())
                .isEqualTo(TournamentEventDiscoveryState.FAILED_LOCKED);
    }

    @Test
    void locksWhenCatalogRevalidationFailsInsteadOfLeavingAReusableIntent() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T08:00:00Z"));
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest())
                .thenReturn(catalog(option(7)))
                .thenThrow(new IllegalStateException("simulated snapshot read failure"));
        var control = control(clock, catalogService);
        var prepared = control.prepare(119_880);

        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true))
                .isInstanceOf(TournamentEventDiscoveryControlException.class)
                .extracting(exception -> ((TournamentEventDiscoveryControlException) exception)
                        .error())
                .isEqualTo(
                        TournamentEventDiscoveryControlError.CATALOG_REVALIDATION_ERROR);
        assertThat(control.snapshot().state())
                .isEqualTo(TournamentEventDiscoveryState.FAILED_LOCKED);
        assertThat(control.snapshot().terminalCode())
                .isEqualTo("CATALOG_REVALIDATION_ERROR");
        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true))
                .isInstanceOf(TournamentEventDiscoveryControlException.class)
                .extracting(exception -> ((TournamentEventDiscoveryControlException) exception)
                        .error())
                .isEqualTo(TournamentEventDiscoveryControlError.NO_PENDING_REQUEST);
    }

    @Test
    void completesTheCommitBeforeAConcurrentStopCanAcquireTheControlMonitor()
            throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T08:00:00Z"));
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog(option(7)));
        CountDownLatch persistenceEntered = new CountDownLatch(1);
        CountDownLatch releasePersistence = new CountDownLatch(1);
        CountDownLatch stopAttempted = new CountDownLatch(1);
        AtomicBoolean supplierExecuted = new AtomicBoolean();
        AtomicReference<TournamentEventDiscoveryState> stateSeenBeforeStop =
                new AtomicReference<>();
        var control = new TournamentEventDiscoveryControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> TournamentEventDiscoveryQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)),
                catalogService) {
            @Override
            public TournamentEventDiscoveryControlSnapshot stop() {
                stopAttempted.countDown();
                stateSeenBeforeStop.set(snapshot().state());
                return super.stop();
            }
        };
        var prepared = control.prepare(119_880);
        control.confirmAndClaim(REQUEST_ID, prepared.confirmationPhrase(), true);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var commit = executor.submit(() -> control.executeAndComplete(
                    REQUEST_ID,
                    () -> {
                        supplierExecuted.set(true);
                        persistenceEntered.countDown();
                        awaitLatch(releasePersistence);
                        return "COMMITTED";
                    }));
            assertThat(persistenceEntered.await(5, TimeUnit.SECONDS)).isTrue();

            var stop = executor.submit(() -> {
                return control.stop();
            });
            assertThat(stopAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(stop.isDone()).isFalse();

            releasePersistence.countDown();

            assertThat(commit.get(5, TimeUnit.SECONDS)).isEqualTo("COMMITTED");
            assertThat(stop.get(5, TimeUnit.SECONDS).state())
                    .isEqualTo(TournamentEventDiscoveryState.STOPPED_LOCKED);
            assertThat(supplierExecuted).isTrue();
            assertThat(stateSeenBeforeStop)
                    .hasValue(TournamentEventDiscoveryState.COMPLETED);
            assertThat(control.snapshot().state())
                    .isEqualTo(TournamentEventDiscoveryState.STOPPED_LOCKED);
        }
        finally {
            releasePersistence.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void signalsTheExactCampaignBeforeApplyingTheTournamentBusinessLock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T08:00:00Z"));
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog(option(7)));
        TournamentEventDiscoveryControlService control = control(clock, catalogService);
        var prepared = control.prepare(119_880);
        control.confirmAndClaim(REQUEST_ID, prepared.confirmationPhrase(), true);

        var stopped = control.stop(requestId -> {
            assertThat(requestId).isEqualTo(REQUEST_ID);
            assertThat(control.snapshot().state())
                    .isEqualTo(TournamentEventDiscoveryState.EXECUTING);
        });

        assertThat(stopped.state())
                .isEqualTo(TournamentEventDiscoveryState.STOPPED_LOCKED);
        assertThat(stopped.terminalCode()).isEqualTo("OPERATOR_STOP");
    }

    @Test
    void appliesTheTournamentBusinessLockEvenWhenTheCampaignSignalFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T08:00:00Z"));
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog(option(7)));
        TournamentEventDiscoveryControlService control = control(clock, catalogService);
        var prepared = control.prepare(119_880);
        control.confirmAndClaim(REQUEST_ID, prepared.confirmationPhrase(), true);

        assertThatThrownBy(() -> control.stop(requestId -> {
            assertThat(requestId).isEqualTo(REQUEST_ID);
            throw new IllegalStateException("signal failed");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("signal failed");

        assertThat(control.snapshot().state())
                .isEqualTo(TournamentEventDiscoveryState.STOPPED_LOCKED);
        assertThat(control.snapshot().terminalCode()).isEqualTo("OPERATOR_STOP");
    }

    @Test
    void requiresExactTextAcknowledgementAndASelectionFromTheCurrentCatalog() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T08:00:00Z"));
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog(option(7)));
        var control = control(clock, catalogService);

        assertThatThrownBy(() -> control.prepare(999))
                .isInstanceOf(TournamentEventDiscoveryControlException.class)
                .extracting(exception -> ((TournamentEventDiscoveryControlException) exception)
                        .error())
                .isEqualTo(
                        TournamentEventDiscoveryControlError.TOURNAMENT_SELECTION_NOT_ALLOWED);

        var prepared = control.prepare(119_880);
        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), false))
                .isInstanceOf(TournamentEventDiscoveryControlException.class)
                .extracting(exception -> ((TournamentEventDiscoveryControlException) exception)
                        .error())
                .isEqualTo(TournamentEventDiscoveryControlError.ACKNOWLEDGEMENT_REQUIRED);
        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase() + " ", true))
                .isInstanceOf(TournamentEventDiscoveryControlException.class)
                .extracting(exception -> ((TournamentEventDiscoveryControlException) exception)
                        .error())
                .isEqualTo(TournamentEventDiscoveryControlError.CONFIRMATION_TEXT_MISMATCH);
    }

    @Test
    void expiresAndLocksOrAllowsANewPreparationOnlyAfterSuccess() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T08:00:00Z"));
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog(option(7)));
        var expired = control(clock, catalogService);
        expired.prepare(119_880);
        clock.advanceSeconds(300);

        assertThat(expired.snapshot().state())
                .isEqualTo(TournamentEventDiscoveryState.EXPIRED_LOCKED);
        assertThatThrownBy(() -> expired.prepare(119_880))
                .isInstanceOf(TournamentEventDiscoveryControlException.class);

        MutableClock successClock = new MutableClock(
                Instant.parse("2026-08-20T09:00:00Z"));
        var successful = control(successClock, catalogService);
        var prepared = successful.prepare(119_880);
        successful.confirmAndClaim(REQUEST_ID, prepared.confirmationPhrase(), true);
        successful.complete(REQUEST_ID);

        assertThat(successful.prepare(119_880).state())
                .isEqualTo(TournamentEventDiscoveryState.AWAITING_CONFIRMATION);
    }

    private static TournamentEventDiscoveryControlService control(
            Clock clock,
            J3TournamentCatalogService catalogService) {
        return new TournamentEventDiscoveryControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> TournamentEventDiscoveryQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)),
                catalogService);
    }

    private static J3TournamentCatalog catalog(J3TournamentCatalogOption option) {
        return J3TournamentCatalog.available(
                LocalDate.of(2026, 8, 18),
                List.of(41L),
                List.of(option),
                0);
    }

    private static J3TournamentCatalogOption option(long uniqueTournamentId) {
        return new J3TournamentCatalogOption(
                119_880,
                "UEFA Champions League, Playoff Round",
                "Europe",
                uniqueTournamentId,
                "UEFA Champions League",
                Map.of(7200, 1),
                List.of(41L));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out while coordinating the concurrent control test");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent control test was interrupted", exception);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
