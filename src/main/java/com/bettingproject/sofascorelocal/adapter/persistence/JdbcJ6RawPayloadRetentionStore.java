package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.application.retention.J6RetentionError;
import com.bettingproject.sofascorelocal.application.retention.J6RetentionException;
import com.bettingproject.sofascorelocal.application.retention.J6RetentionPlanHasher;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.retention.J6BackupEvidence;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionCandidate;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionExecutionResult;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionPreview;
import com.bettingproject.sofascorelocal.port.J6RawPayloadRetentionStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcJ6RawPayloadRetentionStore implements J6RawPayloadRetentionStore {

    private static final String ELIGIBLE_PREDICATE = """
            snapshot.provider = 'SOFASCORE'
            and snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
            and snapshot.payload_raw is not null
            and snapshot.payload_size_bytes is not null
            and snapshot.payload_sha256 is not null
            and snapshot.payload_purged_at is null
            and snapshot.received_at is not null
            and snapshot.received_at < :cutoffAt
            and snapshot.schema_status in ('PARSED', 'ENDPOINT_UNAVAILABLE')
            and (
                exists (
                    select 1
                    from canonical_event_observation canonical
                    where canonical.source_snapshot_id = snapshot.id
                )
                or exists (
                    select 1
                    from event_detail_observation details
                    where details.source_snapshot_id = snapshot.id
                )
                or exists (
                    select 1
                    from j5_event_data_observation event_data
                    where event_data.source_snapshot_id = snapshot.id
                )
            )
            """;

    private static final String SUMMARY_SQL = """
            select
                count(*) as eligible_count,
                coalesce(sum(snapshot.payload_size_bytes), 0) as eligible_bytes,
                min(snapshot.received_at) as oldest_received_at
            from provider_snapshot snapshot
            where %s
            """.formatted(ELIGIBLE_PREDICATE);

    private static final String CANDIDATES_SQL = """
            select
                snapshot.id,
                snapshot.received_at,
                snapshot.logical_endpoint,
                snapshot.schema_status,
                snapshot.payload_size_bytes,
                snapshot.payload_sha256
            from provider_snapshot snapshot
            where %s
            order by snapshot.received_at, snapshot.id
            limit :maximumCandidates
            """.formatted(ELIGIBLE_PREDICATE);

    private static final String LOCKED_CANDIDATES_SQL = CANDIDATES_SQL
            + " for update of snapshot";

    private static final String INSERT_AUDIT_SQL = """
            insert into j6_raw_payload_purge_audit (
                batch_id,
                snapshot_id,
                snapshot_received_at_before,
                payload_size_bytes_before,
                payload_sha256_before,
                retention_days,
                cutoff_at,
                plan_sha256,
                backup_manifest_sha256,
                backup_cipher_sha256,
                backup_qualified_at,
                backup_coverage_max_snapshot_id,
                backup_coverage_received_at,
                executed_at
            ) values (
                :batchId,
                :snapshotId,
                :snapshotReceivedAt,
                :payloadSizeBytes,
                :payloadSha256,
                :retentionDays,
                :cutoffAt,
                :planSha256,
                :backupManifestSha256,
                :backupCipherSha256,
                :backupQualifiedAt,
                :backupCoverageMaxSnapshotId,
                :backupCoverageReceivedAt,
                :executedAt
            )
            """;

    private static final String PURGE_SQL = """
            update provider_snapshot
            set payload_raw = null,
                payload_purged_at = :executedAt
            where id in (:snapshotIds)
              and payload_raw is not null
              and payload_purged_at is null
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcJ6RawPayloadRetentionStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional(readOnly = true)
    public J6RetentionPreview preview(
            int retentionDays,
            Instant generatedAt,
            Instant cutoffAt,
            int maximumCandidates) {
        requireQuery(retentionDays, generatedAt, cutoffAt, maximumCandidates);
        return loadPreview(
                retentionDays,
                generatedAt,
                cutoffAt,
                maximumCandidates,
                false);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public J6RetentionExecutionResult purge(
            int retentionDays,
            Instant cutoffAt,
            int maximumCandidates,
            String expectedPlanSha256,
            J6BackupEvidence backupEvidence,
            UUID batchId,
            Instant executedAt) {
        Objects.requireNonNull(expectedPlanSha256, "expectedPlanSha256");
        Objects.requireNonNull(backupEvidence, "backupEvidence");
        Objects.requireNonNull(batchId, "batchId");
        requireQuery(retentionDays, executedAt, cutoffAt, maximumCandidates);
        J6RetentionPreview current = loadPreview(
                retentionDays,
                executedAt,
                cutoffAt,
                maximumCandidates,
                true);
        if (current.candidates().isEmpty()) {
            throw new J6RetentionException(J6RetentionError.EMPTY_PLAN);
        }
        if (!current.planSha256().equals(expectedPlanSha256)) {
            throw new J6RetentionException(J6RetentionError.STALE_PLAN);
        }
        long maximumSnapshotId = current.candidates().stream()
                .mapToLong(J6RetentionCandidate::snapshotId)
                .max()
                .orElseThrow();
        Instant maximumReceivedAt = current.candidates().stream()
                .map(J6RetentionCandidate::receivedAt)
                .max(java.util.Comparator.naturalOrder())
                .orElseThrow();
        if (!backupEvidence.restoredAndQualified()
                || backupEvidence.qualifiedAt().isAfter(executedAt)) {
            throw new J6RetentionException(J6RetentionError.BACKUP_NOT_QUALIFIED);
        }
        if (backupEvidence.coverageMaxSnapshotId() < maximumSnapshotId
                || backupEvidence.coverageReceivedAt().isBefore(maximumReceivedAt)) {
            throw new J6RetentionException(
                    J6RetentionError.BACKUP_COVERAGE_INSUFFICIENT);
        }

        for (J6RetentionCandidate candidate : current.candidates()) {
            int inserted = jdbcTemplate.update(
                    INSERT_AUDIT_SQL,
                    auditParameters(
                            current,
                            candidate,
                            backupEvidence,
                            batchId,
                            executedAt));
            if (inserted != 1) {
                throw new J6RetentionException(
                        J6RetentionError.DATABASE_MUTATION_MISMATCH);
            }
        }

        String configured = jdbcTemplate.queryForObject(
                "select set_config('sofascore.j6_purge_batch', :batchId, true)",
                new MapSqlParameterSource("batchId", batchId.toString()),
                String.class);
        if (!batchId.toString().equals(configured)) {
            throw new J6RetentionException(J6RetentionError.DATABASE_MUTATION_MISMATCH);
        }

        int updated = jdbcTemplate.update(
                PURGE_SQL,
                new MapSqlParameterSource()
                        .addValue("executedAt", executedAt.atOffset(ZoneOffset.UTC))
                        .addValue(
                                "snapshotIds",
                                current.candidates().stream()
                                        .map(J6RetentionCandidate::snapshotId)
                                        .toList()));
        if (updated != current.selectedCount()) {
            throw new J6RetentionException(J6RetentionError.DATABASE_MUTATION_MISMATCH);
        }
        return new J6RetentionExecutionResult(
                batchId,
                current.planSha256(),
                updated,
                current.selectedPayloadBytes(),
                executedAt);
    }

    private J6RetentionPreview loadPreview(
            int retentionDays,
            Instant generatedAt,
            Instant cutoffAt,
            int maximumCandidates,
            boolean lockCandidates) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("cutoffAt", cutoffAt.atOffset(ZoneOffset.UTC))
                .addValue("maximumCandidates", maximumCandidates);
        Summary summary = jdbcTemplate.queryForObject(
                SUMMARY_SQL,
                parameters,
                (resultSet, rowNumber) -> mapSummary(resultSet));
        if (summary == null) {
            throw new IllegalStateException("retention summary query returned no row");
        }
        List<J6RetentionCandidate> candidates = jdbcTemplate.query(
                lockCandidates ? LOCKED_CANDIDATES_SQL : CANDIDATES_SQL,
                parameters,
                JdbcJ6RawPayloadRetentionStore::mapCandidate);
        return new J6RetentionPreview(
                retentionDays,
                generatedAt,
                cutoffAt,
                summary.count(),
                summary.bytes(),
                Optional.ofNullable(summary.oldestReceivedAt()),
                candidates,
                J6RetentionPlanHasher.calculate(retentionDays, cutoffAt, candidates));
    }

    private static Summary mapSummary(ResultSet resultSet) throws SQLException {
        OffsetDateTime oldest = resultSet.getObject(
                "oldest_received_at",
                OffsetDateTime.class);
        return new Summary(
                resultSet.getLong("eligible_count"),
                resultSet.getLong("eligible_bytes"),
                oldest == null ? null : oldest.toInstant());
    }

    private static J6RetentionCandidate mapCandidate(ResultSet resultSet, int rowNumber)
            throws SQLException {
        OffsetDateTime receivedAt = resultSet.getObject("received_at", OffsetDateTime.class);
        if (receivedAt == null) {
            throw new IllegalStateException("eligible retention candidate lacks received_at");
        }
        return new J6RetentionCandidate(
                resultSet.getLong("id"),
                receivedAt.toInstant(),
                resultSet.getString("logical_endpoint"),
                RawSnapshotSchemaStatus.valueOf(resultSet.getString("schema_status")),
                resultSet.getLong("payload_size_bytes"),
                resultSet.getString("payload_sha256"));
    }

    private static MapSqlParameterSource auditParameters(
            J6RetentionPreview preview,
            J6RetentionCandidate candidate,
            J6BackupEvidence backup,
            UUID batchId,
            Instant executedAt) {
        return new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("snapshotId", candidate.snapshotId())
                .addValue("snapshotReceivedAt", candidate.receivedAt().atOffset(ZoneOffset.UTC))
                .addValue("payloadSizeBytes", candidate.payloadSizeBytes())
                .addValue("payloadSha256", candidate.payloadSha256())
                .addValue("retentionDays", preview.retentionDays())
                .addValue("cutoffAt", preview.cutoffAt().atOffset(ZoneOffset.UTC))
                .addValue("planSha256", preview.planSha256())
                .addValue("backupManifestSha256", backup.manifestSha256())
                .addValue("backupCipherSha256", backup.cipherSha256())
                .addValue("backupQualifiedAt", backup.qualifiedAt().atOffset(ZoneOffset.UTC))
                .addValue("backupCoverageMaxSnapshotId", backup.coverageMaxSnapshotId())
                .addValue(
                        "backupCoverageReceivedAt",
                        backup.coverageReceivedAt().atOffset(ZoneOffset.UTC))
                .addValue("executedAt", executedAt.atOffset(ZoneOffset.UTC));
    }

    private static void requireQuery(
            int retentionDays,
            Instant generatedAt,
            Instant cutoffAt,
            int maximumCandidates) {
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(cutoffAt, "cutoffAt");
        if (retentionDays < 1
                || retentionDays > 3650
                || maximumCandidates < 1
                || maximumCandidates > 500
                || !cutoffAt.isBefore(generatedAt)) {
            throw new IllegalArgumentException("retention query bounds are invalid");
        }
    }

    private record Summary(long count, long bytes, Instant oldestReceivedAt) {
    }
}
