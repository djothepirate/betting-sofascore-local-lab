package com.bettingproject.sofascorelocal.application.retention;

import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.retention.J6BackupEvidence;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionCandidate;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionExecutionRequest;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionExecutionResult;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionPreview;
import com.bettingproject.sofascorelocal.port.J6RawPayloadRetentionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class J6RawPayloadRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-07-19T12:00:00Z");

    private J6RawPayloadRetentionStore store;
    private J6RawPayloadRetentionService service;
    private J6RetentionPreview preview;

    @BeforeEach
    void setUp() {
        store = mock(J6RawPayloadRetentionStore.class);
        service = new J6RawPayloadRetentionService(
                store,
                30,
                Clock.fixed(NOW, ZoneOffset.UTC));
        J6RetentionCandidate candidate = new J6RetentionCandidate(
                41,
                Instant.parse("2026-06-01T12:00:00Z"),
                "EVENT_DETAILS",
                RawSnapshotSchemaStatus.PARSED,
                128,
                "a".repeat(64));
        String planHash = J6RetentionPlanHasher.calculate(30, CUTOFF, List.of(candidate));
        preview = new J6RetentionPreview(
                30,
                NOW,
                CUTOFF,
                1,
                128,
                Optional.of(candidate.receivedAt()),
                List.of(candidate),
                planHash);
    }

    @Test
    void previewsUsingTheConfiguredRetentionAndBoundedBatch() {
        when(store.preview(30, NOW, CUTOFF, 500)).thenReturn(preview);

        assertThat(service.preview()).isEqualTo(preview);
    }

    @Test
    void executesOnlyTheExactConfirmedPlanCoveredByARestoredBackup() {
        when(store.preview(30, NOW, CUTOFF, 500)).thenReturn(preview);
        J6BackupEvidence backup = backup(true, 41, NOW);
        J6RetentionExecutionResult stored = new J6RetentionExecutionResult(
                UUID.fromString("70000000-0000-0000-0000-000000000007"),
                preview.planSha256(),
                1,
                128,
                NOW);
        when(store.purge(
                eq(30),
                eq(CUTOFF),
                eq(500),
                eq(preview.planSha256()),
                eq(backup),
                any(UUID.class),
                eq(NOW))).thenReturn(stored);

        J6RetentionExecutionResult result = service.execute(new J6RetentionExecutionRequest(
                CUTOFF,
                preview.planSha256(),
                preview.confirmationPhrase(),
                backup));

        assertThat(result).isEqualTo(stored);
        verify(store).purge(
                eq(30),
                eq(CUTOFF),
                eq(500),
                eq(preview.planSha256()),
                eq(backup),
                any(UUID.class),
                eq(NOW));
    }

    @Test
    void rejectsAStalePlanBeforeAnyMutation() {
        when(store.preview(30, NOW, CUTOFF, 500)).thenReturn(preview);

        assertThatThrownBy(() -> service.execute(new J6RetentionExecutionRequest(
                CUTOFF,
                "f".repeat(64),
                "PURGER 1 PAYLOADS J6 " + "f".repeat(64),
                backup(true, 41, NOW))))
                .isInstanceOfSatisfying(J6RetentionException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(J6RetentionError.STALE_PLAN));
        verify(store).preview(30, NOW, CUTOFF, 500);
        verifyNoMoreInteractions(store);
    }

    @Test
    void rejectsWrongConfirmationAndUnqualifiedOrInsufficientBackups() {
        when(store.preview(30, NOW, CUTOFF, 500)).thenReturn(preview);

        assertError(
                new J6RetentionExecutionRequest(
                        CUTOFF,
                        preview.planSha256(),
                        "wrong phrase",
                        backup(true, 41, NOW)),
                J6RetentionError.INVALID_CONFIRMATION);
        assertError(
                new J6RetentionExecutionRequest(
                        CUTOFF,
                        preview.planSha256(),
                        preview.confirmationPhrase(),
                        backup(false, 41, NOW)),
                J6RetentionError.BACKUP_NOT_QUALIFIED);
        assertError(
                new J6RetentionExecutionRequest(
                        CUTOFF,
                        preview.planSha256(),
                        preview.confirmationPhrase(),
                        backup(true, 40, NOW)),
                J6RetentionError.BACKUP_COVERAGE_INSUFFICIENT);
    }

    private void assertError(
            J6RetentionExecutionRequest request,
            J6RetentionError expected) {
        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOfSatisfying(J6RetentionException.class,
                        exception -> assertThat(exception.error()).isEqualTo(expected));
    }

    private static J6BackupEvidence backup(
            boolean qualified,
            long maxSnapshotId,
            Instant coverage) {
        return new J6BackupEvidence(
                "b".repeat(64),
                "c".repeat(64),
                NOW.minusSeconds(60),
                maxSnapshotId,
                coverage,
                qualified);
    }
}
