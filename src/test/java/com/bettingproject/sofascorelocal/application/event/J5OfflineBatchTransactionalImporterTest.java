package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessingPlan;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessor;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class J5OfflineBatchTransactionalImporterTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "51000000-0000-0000-0000-000000000010");

    private final J5OfflineBatchPlanService planService = mock(
            J5OfflineBatchPlanService.class);
    private final J5OfflineBatchPolicy policy = mock(J5OfflineBatchPolicy.class);
    private final J5LocalJsonImportProcessor processor = mock(
            J5LocalJsonImportProcessor.class);
    private final J5OfflineBatchTransactionalImporter importer =
            new J5OfflineBatchTransactionalImporter(planService, policy, processor);

    @Test
    void refusesCanonicalDriftAtTheTransactionalBoundaryBeforeAnyWrite() {
        J5OfflineBatchPlan plan = plan();
        J5OfflineBatchPreparedEvent prepared = prepared(plan.events().getFirst());
        doThrow(new J5OfflineBatchException(J5OfflineBatchError.PLAN_CHANGED))
                .when(planService).requireCurrent(plan);

        assertError(
                () -> importer.importAtomically(
                        new J5OfflineBatchExecutionClaim(REQUEST_ID, plan),
                        List.of(prepared),
                        6L),
                J5OfflineBatchError.PLAN_CHANGED);

        verify(processor, never()).execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rechecksTheStrictOfflinePolicyAtTheTransactionalBoundary() {
        J5OfflineBatchPlan plan = plan();
        J5OfflineBatchPreparedEvent prepared = prepared(plan.events().getFirst());
        doThrow(new J5OfflineBatchException(
                J5OfflineBatchError.OFFLINE_POLICY_UNAVAILABLE))
                .when(policy).requireAvailable();

        assertError(
                () -> importer.importAtomically(
                        new J5OfflineBatchExecutionClaim(REQUEST_ID, plan),
                        List.of(prepared),
                        6L),
                J5OfflineBatchError.OFFLINE_POLICY_UNAVAILABLE);

        verify(planService, never()).requireCurrent(plan);
        verify(processor, never()).execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static J5OfflineBatchPreparedEvent prepared(J5OfflineBatchPlanEvent event) {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{}".getBytes(StandardCharsets.UTF_8));
        return new J5OfflineBatchPreparedEvent(
                event,
                new J5LocalJsonImportProcessingPlan(
                        event.canonicalEventId(),
                        event.providerEventId(),
                        payload,
                        payload,
                        payload));
    }

    private static J5OfflineBatchPlan plan() {
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
                REQUEST_ID,
                LocalDate.parse("2026-08-22"),
                ZoneId.of("Europe/Paris"),
                Instant.parse("2026-08-21T22:00:00Z"),
                Instant.parse("2026-08-22T22:00:00Z"),
                Instant.parse("2026-08-22T08:00:00Z"),
                Instant.parse("2026-08-22T08:15:00Z"),
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
}
