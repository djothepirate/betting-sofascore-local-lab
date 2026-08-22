package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessingException;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessingPlan;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessor;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J5OfflineBatchImportServiceTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "51000000-0000-0000-0000-000000000010");

    private final J5OfflineBatchControlService control = mock(
            J5OfflineBatchControlService.class);
    private final J5OfflineBatchPolicy policy = mock(J5OfflineBatchPolicy.class);
    private final J5OfflineBatchPlanService planService = mock(
            J5OfflineBatchPlanService.class);
    private final J5OfflineBatchUploadService uploadService = mock(
            J5OfflineBatchUploadService.class);
    private final J5LocalJsonImportProcessor processor = mock(
            J5LocalJsonImportProcessor.class);
    private final J5OfflineBatchTransactionalImporter importer = mock(
            J5OfflineBatchTransactionalImporter.class);
    private final J5OfflineBatchImportService service = new J5OfflineBatchImportService(
            control, policy, planService, uploadService, processor, importer);

    @Test
    void prevalidatesEveryEventBeforeClaimingAndUsesOneTransactionalImport() {
        J5OfflineBatchPlan plan = plan(1001L, 2002L);
        J5OfflineBatchUploadSet uploadSet = uploadSet(plan);
        List<J5OfflineBatchPreparedEvent> prepared = prepared(uploadSet);
        J5OfflineBatchExecutionClaim claim = new J5OfflineBatchExecutionClaim(
                REQUEST_ID, plan);
        J5OfflineBatchResult result = mock(J5OfflineBatchResult.class);
        when(result.completed()).thenReturn(true);
        when(result.requestId()).thenReturn(REQUEST_ID);
        when(control.requirePending(REQUEST_ID)).thenReturn(plan);
        when(uploadService.validate(plan, List.of())).thenReturn(uploadSet);
        for (int index = 0; index < uploadSet.events().size(); index++) {
            J5OfflineBatchEventPayloads payloads = uploadSet.events().get(index);
            when(processor.prepare(
                    payloads.event().canonicalEventId(),
                    payloads.event().providerEventId(),
                    payloads.statistics(),
                    payloads.incidents(),
                    payloads.lineups())).thenReturn(prepared.get(index).processingPlan());
        }
        when(control.confirmAndClaim(REQUEST_ID, "phrase", true)).thenReturn(claim);
        when(importer.importAtomically(claim, prepared, uploadSet.totalBytes()))
                .thenReturn(result);

        assertThat(service.execute(REQUEST_ID, "phrase", true, List.of()))
                .isSameAs(result);

        verify(processor).prepare(
                uploadSet.events().get(0).event().canonicalEventId(),
                1001L,
                uploadSet.events().get(0).statistics(),
                uploadSet.events().get(0).incidents(),
                uploadSet.events().get(0).lineups());
        verify(processor).prepare(
                uploadSet.events().get(1).event().canonicalEventId(),
                2002L,
                uploadSet.events().get(1).statistics(),
                uploadSet.events().get(1).incidents(),
                uploadSet.events().get(1).lineups());
        verify(control).confirmAndClaim(REQUEST_ID, "phrase", true);
        verify(importer).importAtomically(claim, prepared, uploadSet.totalBytes());
        verify(control).complete(REQUEST_ID, result);
        verify(planService, times(2)).requireCurrent(plan);
    }

    @Test
    void leavesThePlanPendingWhenTheLastEventFailsPrevalidation() {
        J5OfflineBatchPlan plan = plan(1001L, 2002L);
        J5OfflineBatchUploadSet uploadSet = uploadSet(plan);
        List<J5OfflineBatchPreparedEvent> prepared = prepared(uploadSet);
        J5OfflineBatchEventPayloads first = uploadSet.events().get(0);
        J5OfflineBatchEventPayloads second = uploadSet.events().get(1);
        when(control.requirePending(REQUEST_ID)).thenReturn(plan);
        when(uploadService.validate(plan, List.of())).thenReturn(uploadSet);
        when(processor.prepare(
                first.event().canonicalEventId(),
                first.event().providerEventId(),
                first.statistics(), first.incidents(), first.lineups()))
                .thenReturn(prepared.get(0).processingPlan());
        J5LocalJsonImportProcessingException processingFailure = mock(
                J5LocalJsonImportProcessingException.class);
        when(processingFailure.code()).thenReturn("LINEUPS_PAYLOAD_INCOMPATIBLE");
        when(processor.prepare(
                second.event().canonicalEventId(),
                second.event().providerEventId(),
                second.statistics(), second.incidents(), second.lineups()))
                .thenThrow(processingFailure);

        assertError(
                () -> service.execute(REQUEST_ID, "phrase", true, List.of()),
                J5OfflineBatchError.LINEUPS_PAYLOAD_INCOMPATIBLE);

        verify(control, never()).confirmAndClaim(any(), anyString(), anyBoolean());
        verify(importer, never()).importAtomically(any(), any(), anyLong());
        verify(control, never()).fail(any(), any(), anyLong());
    }

    @Test
    void locksWithZeroCommittedImportsWhenTheAtomicTransactionFails() {
        J5OfflineBatchPlan plan = plan(1001L);
        J5OfflineBatchUploadSet uploadSet = uploadSet(plan);
        List<J5OfflineBatchPreparedEvent> prepared = prepared(uploadSet);
        J5OfflineBatchEventPayloads payloads = uploadSet.events().getFirst();
        J5OfflineBatchExecutionClaim claim = new J5OfflineBatchExecutionClaim(
                REQUEST_ID, plan);
        when(control.requirePending(REQUEST_ID)).thenReturn(plan);
        when(uploadService.validate(plan, List.of())).thenReturn(uploadSet);
        when(processor.prepare(
                payloads.event().canonicalEventId(),
                payloads.event().providerEventId(),
                payloads.statistics(), payloads.incidents(), payloads.lineups()))
                .thenReturn(prepared.getFirst().processingPlan());
        when(control.confirmAndClaim(REQUEST_ID, "phrase", true)).thenReturn(claim);
        when(control.executionMayContinue(REQUEST_ID)).thenReturn(true);
        when(importer.importAtomically(claim, prepared, uploadSet.totalBytes()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertError(
                () -> service.execute(REQUEST_ID, "phrase", true, List.of()),
                J5OfflineBatchError.STORAGE_UNAVAILABLE);

        verify(control).fail(
                REQUEST_ID,
                J5OfflineBatchError.STORAGE_UNAVAILABLE,
                uploadSet.totalBytes());
        verify(control, never()).complete(any(), any());
    }

    @Test
    void leavesThePlanPendingWhenCanonicalStateChangesImmediatelyBeforeClaim() {
        J5OfflineBatchPlan plan = plan(1001L);
        J5OfflineBatchUploadSet uploadSet = uploadSet(plan);
        J5OfflineBatchEventPayloads payloads = uploadSet.events().getFirst();
        J5OfflineBatchPreparedEvent prepared = prepared(uploadSet).getFirst();
        when(control.requirePending(REQUEST_ID)).thenReturn(plan);
        when(uploadService.validate(plan, List.of())).thenReturn(uploadSet);
        when(processor.prepare(
                payloads.event().canonicalEventId(),
                payloads.event().providerEventId(),
                payloads.statistics(), payloads.incidents(), payloads.lineups()))
                .thenReturn(prepared.processingPlan());
        doNothing()
                .doThrow(new J5OfflineBatchException(J5OfflineBatchError.PLAN_CHANGED))
                .when(planService).requireCurrent(plan);

        assertError(
                () -> service.execute(REQUEST_ID, "phrase", true, List.of()),
                J5OfflineBatchError.PLAN_CHANGED);

        verify(planService, times(2)).requireCurrent(plan);
        verify(control, never()).confirmAndClaim(any(), anyString(), anyBoolean());
        verify(importer, never()).importAtomically(any(), any(), anyLong());
        verify(control, never()).fail(any(), any(), anyLong());
    }

    @Test
    void reportsCanonicalStoreFailuresAsStorageUnavailableBeforeClaim() {
        J5OfflineBatchPlan plan = plan(1001L);
        J5OfflineBatchUploadSet uploadSet = uploadSet(plan);
        J5OfflineBatchEventPayloads payloads = uploadSet.events().getFirst();
        J5LocalJsonImportProcessingException processingFailure = mock(
                J5LocalJsonImportProcessingException.class);
        when(processingFailure.code()).thenReturn("CANONICAL_EVENT_LOOKUP_ERROR");
        when(processingFailure.getCause()).thenReturn(
                new DataAccessResourceFailureException("database unavailable"));
        when(control.requirePending(REQUEST_ID)).thenReturn(plan);
        when(uploadService.validate(plan, List.of())).thenReturn(uploadSet);
        when(processor.prepare(
                payloads.event().canonicalEventId(),
                payloads.event().providerEventId(),
                payloads.statistics(), payloads.incidents(), payloads.lineups()))
                .thenThrow(processingFailure);

        assertError(
                () -> service.execute(REQUEST_ID, "phrase", true, List.of()),
                J5OfflineBatchError.STORAGE_UNAVAILABLE);

        verify(control, never()).confirmAndClaim(any(), anyString(), anyBoolean());
        verify(importer, never()).importAtomically(any(), any(), anyLong());
    }

    private static List<J5OfflineBatchPreparedEvent> prepared(
            J5OfflineBatchUploadSet uploadSet) {
        return uploadSet.events().stream()
                .map(payloads -> new J5OfflineBatchPreparedEvent(
                        payloads.event(),
                        new J5LocalJsonImportProcessingPlan(
                                payloads.event().canonicalEventId(),
                                payloads.event().providerEventId(),
                                payloads.statistics(),
                                payloads.incidents(),
                                payloads.lineups())))
                .toList();
    }

    private static J5OfflineBatchUploadSet uploadSet(J5OfflineBatchPlan plan) {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{}".getBytes(StandardCharsets.UTF_8));
        List<J5OfflineBatchEventPayloads> events = plan.events().stream()
                .map(event -> new J5OfflineBatchEventPayloads(
                        event, payload, payload, payload))
                .toList();
        return new J5OfflineBatchUploadSet(events, events.size() * 6L);
    }

    private static J5OfflineBatchPlan plan(long... providerEventIds) {
        List<J5OfflineBatchPlanEvent> events = java.util.Arrays.stream(providerEventIds)
                .mapToObj(providerEventId -> new J5OfflineBatchPlanEvent(
                        CanonicalEventIdentity.sofascore(providerEventId).value(),
                        providerEventId,
                        providerEventId,
                        Instant.parse("2026-08-22T14:00:00Z"),
                        "Home " + providerEventId,
                        "Away " + providerEventId,
                        "b".repeat(64),
                        J5OfflineBatchPlanEvent.expectedFileNames(providerEventId)))
                .toList();
        return new J5OfflineBatchPlan(
                REQUEST_ID,
                LocalDate.parse("2026-08-22"),
                ZoneId.of("Europe/Paris"),
                Instant.parse("2026-08-21T22:00:00Z"),
                Instant.parse("2026-08-22T22:00:00Z"),
                Instant.parse("2026-08-22T08:00:00Z"),
                Instant.parse("2026-08-22T08:15:00Z"),
                events,
                "a".repeat(64),
                "IMPORTER " + events.size() + " MATCHS J5 HORS LIGNE " + "a".repeat(64));
    }

    private static void assertError(Runnable action, J5OfflineBatchError error) {
        assertThatThrownBy(action::run)
                .isInstanceOf(J5OfflineBatchException.class)
                .extracting(exception -> ((J5OfflineBatchException) exception).error())
                .isEqualTo(error);
    }
}
