package com.bettingproject.sofascorelocal.application.retention;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.retention.J6BackupEvidence;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionCandidate;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionExecutionRequest;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionExecutionResult;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionPreview;
import com.bettingproject.sofascorelocal.port.J6RawPayloadRetentionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

@Service
public class J6RawPayloadRetentionService {

    public static final int MAXIMUM_CANDIDATES_PER_BATCH = 500;

    private final J6RawPayloadRetentionStore store;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public J6RawPayloadRetentionService(
            J6RawPayloadRetentionStore store,
            SofascoreProperties properties) {
        this(store, properties.getRawPayloadRetentionDays(), Clock.systemUTC());
    }

    J6RawPayloadRetentionService(
            J6RawPayloadRetentionStore store,
            int retentionDays,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        if (retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalArgumentException("retentionDays must be between 1 and 3650");
        }
        this.retentionDays = retentionDays;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public J6RetentionPreview preview() {
        Instant generatedAt = clock.instant();
        return store.preview(
                retentionDays,
                generatedAt,
                generatedAt.minus(retentionDays, ChronoUnit.DAYS),
                MAXIMUM_CANDIDATES_PER_BATCH);
    }

    public J6RetentionExecutionResult execute(J6RetentionExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        Instant executedAt = clock.instant();
        Instant newestAllowedCutoff = executedAt.minus(retentionDays, ChronoUnit.DAYS);
        if (request.cutoffAt().isAfter(newestAllowedCutoff)) {
            throw new J6RetentionException(J6RetentionError.STALE_PLAN);
        }
        J6RetentionPreview current = store.preview(
                retentionDays,
                executedAt,
                request.cutoffAt(),
                MAXIMUM_CANDIDATES_PER_BATCH);
        if (!current.planSha256().equals(request.planSha256())) {
            throw new J6RetentionException(J6RetentionError.STALE_PLAN);
        }
        if (current.candidates().isEmpty()) {
            throw new J6RetentionException(J6RetentionError.EMPTY_PLAN);
        }
        if (!current.confirmationPhrase().equals(request.confirmationPhrase())) {
            throw new J6RetentionException(J6RetentionError.INVALID_CONFIRMATION);
        }
        requireBackupCoverage(current, request.backupEvidence(), executedAt);
        return store.purge(
                retentionDays,
                request.cutoffAt(),
                MAXIMUM_CANDIDATES_PER_BATCH,
                request.planSha256(),
                request.backupEvidence(),
                UUID.randomUUID(),
                executedAt);
    }

    private static void requireBackupCoverage(
            J6RetentionPreview preview,
            J6BackupEvidence backup,
            Instant executedAt) {
        if (!backup.restoredAndQualified() || backup.qualifiedAt().isAfter(executedAt)) {
            throw new J6RetentionException(J6RetentionError.BACKUP_NOT_QUALIFIED);
        }
        long maximumSnapshotId = preview.candidates().stream()
                .mapToLong(J6RetentionCandidate::snapshotId)
                .max()
                .orElseThrow();
        Instant maximumReceivedAt = preview.candidates().stream()
                .map(J6RetentionCandidate::receivedAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        if (backup.coverageMaxSnapshotId() < maximumSnapshotId
                || backup.coverageReceivedAt().isBefore(maximumReceivedAt)) {
            throw new J6RetentionException(
                    J6RetentionError.BACKUP_COVERAGE_INSUFFICIENT);
        }
    }
}
