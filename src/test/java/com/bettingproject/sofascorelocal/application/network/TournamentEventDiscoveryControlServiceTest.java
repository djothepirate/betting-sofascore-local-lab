package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
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

            var stop = executor.submit(control::stop);
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
