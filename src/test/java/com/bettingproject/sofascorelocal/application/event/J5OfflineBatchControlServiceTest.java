package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.application.network.J5RealEndpointResult;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J5OfflineBatchControlServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T08:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString(
            "51000000-0000-0000-0000-000000000010");
    private static final UUID SECOND_REQUEST_ID = UUID.fromString(
            "51000000-0000-0000-0000-000000000011");

    private final J5OfflineBatchPolicy policy = mock(J5OfflineBatchPolicy.class);
    private final J5OfflineBatchPlanService planService = mock(J5OfflineBatchPlanService.class);

    @Test
    void preparesWithTheCombinedProviderConfigurationAndTheConnectorEnabled() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setTournamentEventDiscoveryEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS));
        J5OfflineBatchPolicy combinedPolicy = new J5OfflineBatchPolicy(properties);
        J5OfflineBatchPlan preparedPlan = plan(REQUEST_ID, NOW);
        when(planService.create(any(), anyString(), anyList())).thenReturn(preparedPlan);
        J5OfflineBatchControlService control = new J5OfflineBatchControlService(
                combinedPolicy, planService, Clock.fixed(NOW, ZoneOffset.UTC));

        J5OfflineBatchControlSnapshot snapshot = control.prepare(
                preparedPlan.date(),
                preparedPlan.zoneId().getId(),
                List.of(preparedPlan.events().getFirst().canonicalEventId()));

        assertThat(snapshot.state()).isEqualTo(J5OfflineBatchState.AWAITING_CONFIRMATION);
        assertThat(snapshot.offlineAvailable()).isTrue();
        assertThat(snapshot.offlineBlockers()).isEmpty();
    }

    @Test
    void bindsOneExactConfirmationAndAllowsANewPlanAfterSuccess() {
        J5OfflineBatchPlan first = plan(REQUEST_ID, NOW);
        J5OfflineBatchPlan second = plan(SECOND_REQUEST_ID, NOW.plusSeconds(30));
        when(policy.snapshot()).thenReturn(
                new J5OfflineBatchPolicySnapshot(true, List.of()));
        when(planService.create(any(), anyString(), anyList()))
                .thenReturn(first, second);
        J5OfflineBatchControlService control = new J5OfflineBatchControlService(
                policy, planService, Clock.fixed(NOW, ZoneOffset.UTC));

        var prepared = control.prepare(
                first.date(), first.zoneId().getId(), List.of(first.events().getFirst().canonicalEventId()));
        assertThat(prepared.state()).isEqualTo(J5OfflineBatchState.AWAITING_CONFIRMATION);
        assertError(
                () -> control.confirmAndClaim(REQUEST_ID, first.confirmationPhrase(), false),
                J5OfflineBatchError.ACKNOWLEDGEMENT_REQUIRED);
        assertError(
                () -> control.confirmAndClaim(REQUEST_ID, "wrong", true),
                J5OfflineBatchError.CONFIRMATION_TEXT_MISMATCH);

        assertThat(control.confirmAndClaim(
                REQUEST_ID, first.confirmationPhrase(), true).plan()).isEqualTo(first);
        assertThat(control.executionMayContinue(REQUEST_ID)).isTrue();
        J5OfflineBatchResult result = completedResult(first);
        assertThat(control.complete(REQUEST_ID, result).state())
                .isEqualTo(J5OfflineBatchState.COMPLETED_LOCKED);
        assertThat(control.snapshot().preparationAllowed()).isTrue();

        var next = control.prepare(
                second.date(), second.zoneId().getId(),
                List.of(second.events().getFirst().canonicalEventId()));
        assertThat(next.plan().requestId()).isEqualTo(SECOND_REQUEST_ID);
        assertThat(next.result()).isNull();
    }

    @Test
    void expiresAtFifteenMinutesAndPermitsAnExplicitReplacement() {
        MutableClock clock = new MutableClock(NOW);
        J5OfflineBatchPlan first = plan(REQUEST_ID, NOW);
        J5OfflineBatchPlan second = plan(
                SECOND_REQUEST_ID, NOW.plus(J5OfflineBatchPlanService.CONFIRMATION_TTL));
        when(policy.snapshot()).thenReturn(
                new J5OfflineBatchPolicySnapshot(true, List.of()));
        when(planService.create(any(), anyString(), anyList()))
                .thenReturn(first, second);
        J5OfflineBatchControlService control = new J5OfflineBatchControlService(
                policy, planService, clock);
        control.prepare(first.date(), first.zoneId().getId(),
                List.of(first.events().getFirst().canonicalEventId()));

        clock.advance(J5OfflineBatchPlanService.CONFIRMATION_TTL);

        assertThat(control.snapshot().state()).isEqualTo(J5OfflineBatchState.EXPIRED_LOCKED);
        assertThat(control.snapshot().terminalCode())
                .isEqualTo(J5OfflineBatchError.CONFIRMATION_EXPIRED.name());
        assertThat(control.snapshot().preparationAllowed()).isTrue();
        assertError(
                () -> control.confirmAndClaim(REQUEST_ID, first.confirmationPhrase(), true),
                J5OfflineBatchError.CONFIRMATION_EXPIRED);
        assertThat(control.prepare(
                second.date(), second.zoneId().getId(),
                List.of(second.events().getFirst().canonicalEventId())).plan())
                .isEqualTo(second);
    }

    @Test
    void rejectsConcurrentPlansAndAllowsReplacementAfterTransactionalFailure() {
        J5OfflineBatchPlan first = plan(REQUEST_ID, NOW);
        J5OfflineBatchPlan second = plan(SECOND_REQUEST_ID, NOW.plusSeconds(30));
        when(policy.snapshot()).thenReturn(
                new J5OfflineBatchPolicySnapshot(true, List.of()));
        when(planService.create(any(), anyString(), anyList()))
                .thenReturn(first, second);
        J5OfflineBatchControlService control = new J5OfflineBatchControlService(
                policy, planService, Clock.fixed(NOW, ZoneOffset.UTC));
        control.prepare(first.date(), first.zoneId().getId(),
                List.of(first.events().getFirst().canonicalEventId()));

        assertError(
                () -> control.prepare(first.date(), first.zoneId().getId(),
                        List.of(first.events().getFirst().canonicalEventId())),
                J5OfflineBatchError.ACTIVE_BATCH_EXISTS);
        control.confirmAndClaim(REQUEST_ID, first.confirmationPhrase(), true);
        var failed = control.fail(
                REQUEST_ID, J5OfflineBatchError.STORAGE_UNAVAILABLE, 6L);

        assertThat(failed.state()).isEqualTo(J5OfflineBatchState.FAILED_LOCKED);
        assertThat(failed.result().localJsonImports()).isZero();
        assertThat(failed.preparationAllowed()).isTrue();
        assertThat(control.prepare(second.date(), second.zoneId().getId(),
                List.of(second.events().getFirst().canonicalEventId())).plan())
                .isEqualTo(second);
    }

    @Test
    void verifiesPolicyAndCanonicalStateBeforeAnyUploadCapture() {
        J5OfflineBatchPlan plan = plan(REQUEST_ID, NOW);
        when(policy.snapshot()).thenReturn(
                new J5OfflineBatchPolicySnapshot(true, List.of()));
        when(planService.create(any(), anyString(), anyList())).thenReturn(plan);
        J5OfflineBatchControlService control = new J5OfflineBatchControlService(
                policy, planService, Clock.fixed(NOW, ZoneOffset.UTC));
        control.prepare(
                plan.date(),
                plan.zoneId().getId(),
                List.of(plan.events().getFirst().canonicalEventId()));

        assertThat(control.requireReadyForUpload(REQUEST_ID)).isSameAs(plan);

        verify(planService).requireCurrent(plan);
    }

    @Test
    void allowsANewPlanAfterAnExplicitPreImportStop() {
        J5OfflineBatchPlan first = plan(REQUEST_ID, NOW);
        J5OfflineBatchPlan second = plan(SECOND_REQUEST_ID, NOW.plusSeconds(30));
        when(policy.snapshot()).thenReturn(
                new J5OfflineBatchPolicySnapshot(true, List.of()));
        when(planService.create(any(), anyString(), anyList()))
                .thenReturn(first, second);
        J5OfflineBatchControlService control = new J5OfflineBatchControlService(
                policy, planService, Clock.fixed(NOW, ZoneOffset.UTC));
        control.prepare(
                first.date(),
                first.zoneId().getId(),
                List.of(first.events().getFirst().canonicalEventId()));

        assertThat(control.stop(REQUEST_ID).state())
                .isEqualTo(J5OfflineBatchState.STOPPED_LOCKED);
        assertThat(control.snapshot().result().localJsonImports()).isZero();
        assertThat(control.prepare(
                second.date(),
                second.zoneId().getId(),
                List.of(second.events().getFirst().canonicalEventId())).plan())
                .isEqualTo(second);
    }

    @Test
    void refusesAStaleStopFormWithoutMutatingTheNewPendingPlan() {
        J5OfflineBatchPlan first = plan(REQUEST_ID, NOW);
        J5OfflineBatchPlan second = plan(SECOND_REQUEST_ID, NOW.plusSeconds(30));
        when(policy.snapshot()).thenReturn(
                new J5OfflineBatchPolicySnapshot(true, List.of()));
        when(planService.create(any(), anyString(), anyList()))
                .thenReturn(first, second);
        J5OfflineBatchControlService control = new J5OfflineBatchControlService(
                policy, planService, Clock.fixed(NOW, ZoneOffset.UTC));
        control.prepare(
                first.date(),
                first.zoneId().getId(),
                List.of(first.events().getFirst().canonicalEventId()));
        control.confirmAndClaim(REQUEST_ID, first.confirmationPhrase(), true);
        control.complete(REQUEST_ID, completedResult(first));
        control.prepare(
                second.date(),
                second.zoneId().getId(),
                List.of(second.events().getFirst().canonicalEventId()));

        assertError(
                () -> control.stop(REQUEST_ID),
                J5OfflineBatchError.REQUEST_ID_MISMATCH);
        assertThat(control.snapshot().state())
                .isEqualTo(J5OfflineBatchState.AWAITING_CONFIRMATION);
        assertThat(control.snapshot().plan()).isEqualTo(second);
    }

    @Test
    void allowsExactlyOneClaimAcrossConcurrentConfirmations() throws Exception {
        J5OfflineBatchPlan plan = plan(REQUEST_ID, NOW);
        when(policy.snapshot()).thenReturn(
                new J5OfflineBatchPolicySnapshot(true, List.of()));
        when(planService.create(any(), anyString(), anyList())).thenReturn(plan);
        J5OfflineBatchControlService control = new J5OfflineBatchControlService(
                policy, planService, Clock.fixed(NOW, ZoneOffset.UTC));
        control.prepare(
                plan.date(),
                plan.zoneId().getId(),
                List.of(plan.events().getFirst().canonicalEventId()));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Object> confirmation = () -> {
                start.await();
                try {
                    return control.confirmAndClaim(
                            REQUEST_ID, plan.confirmationPhrase(), true);
                }
                catch (J5OfflineBatchException exception) {
                    return exception.error();
                }
            };
            Future<Object> first = executor.submit(confirmation);
            Future<Object> second = executor.submit(confirmation);
            start.countDown();
            List<Object> outcomes = List.of(first.get(), second.get());

            assertThat(outcomes.stream()
                    .filter(J5OfflineBatchExecutionClaim.class::isInstance))
                    .hasSize(1);
            assertThat(outcomes).containsExactlyInAnyOrder(
                    outcomes.stream()
                            .filter(J5OfflineBatchExecutionClaim.class::isInstance)
                            .findFirst()
                            .orElseThrow(),
                    J5OfflineBatchError.NO_PENDING_BATCH);
        }
        finally {
            executor.shutdownNow();
        }
    }

    private static J5OfflineBatchResult completedResult(J5OfflineBatchPlan plan) {
        J5OfflineBatchPlanEvent event = plan.events().getFirst();
        List<J5RealEndpointResult> endpoints = List.of(
                endpoint(SofascoreEndpointType.EVENT_STATISTICS, 1L),
                endpoint(SofascoreEndpointType.EVENT_INCIDENTS, 2L),
                endpoint(SofascoreEndpointType.EVENT_LINEUPS, 3L));
        return new J5OfflineBatchResult(
                plan.requestId(),
                true,
                "COMPLETED",
                1,
                3,
                6L,
                List.of(new J5OfflineBatchEventResult(
                        event.canonicalEventId(),
                        event.providerEventId(),
                        event.homeTeamName(),
                        event.awayTeamName(),
                        endpoints)));
    }

    private static J5RealEndpointResult endpoint(
            SofascoreEndpointType endpoint,
            long id) {
        return new J5RealEndpointResult(
                endpoint,
                id,
                "%064x".formatted(id),
                2,
                id,
                true,
                J5CompletenessStatus.COMPLETE,
                100,
                0);
    }

    private static J5OfflineBatchPlan plan(UUID requestId, Instant preparedAt) {
        long providerEventId = 1001L;
        J5OfflineBatchPlanEvent event = new J5OfflineBatchPlanEvent(
                CanonicalEventIdentity.sofascore(providerEventId).value(),
                providerEventId,
                10L,
                Instant.parse("2026-08-22T14:00:00Z"),
                "Home",
                "Away",
                "b".repeat(64),
                J5OfflineBatchPlanEvent.expectedFileNames(providerEventId));
        return new J5OfflineBatchPlan(
                requestId,
                LocalDate.parse("2026-08-22"),
                ZoneId.of("Europe/Paris"),
                Instant.parse("2026-08-21T22:00:00Z"),
                Instant.parse("2026-08-22T22:00:00Z"),
                preparedAt,
                preparedAt.plus(J5OfflineBatchPlanService.CONFIRMATION_TTL),
                List.of(event),
                "a".repeat(64),
                "IMPORTER 1 MATCHS J5 HORS LIGNE " + "a".repeat(64));
    }

    private static void assertError(Runnable action, J5OfflineBatchError error) {
        assertThatThrownBy(action::run)
                .isInstanceOf(J5OfflineBatchException.class)
                .extracting(exception -> ((J5OfflineBatchException) exception).error())
                .isEqualTo(error);
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
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
            return current;
        }
    }
}
