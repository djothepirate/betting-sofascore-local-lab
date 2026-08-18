package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.retention.J6BackupEvidence;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionExecutionResult;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionPreview;

import java.time.Instant;
import java.util.UUID;

public interface J6RawPayloadRetentionStore {

    J6RetentionPreview preview(
            int retentionDays,
            Instant generatedAt,
            Instant cutoffAt,
            int maximumCandidates);

    J6RetentionExecutionResult purge(
            int retentionDays,
            Instant cutoffAt,
            int maximumCandidates,
            String expectedPlanSha256,
            J6BackupEvidence backupEvidence,
            UUID batchId,
            Instant executedAt);
}
