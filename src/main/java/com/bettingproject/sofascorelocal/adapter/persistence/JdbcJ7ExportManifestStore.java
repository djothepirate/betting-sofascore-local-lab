package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.domain.export.J7ExportDecision;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifestDraft;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.port.J7ExportManifestStore;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcJ7ExportManifestStore implements J7ExportManifestStore {

    private static final String SELECT_COLUMNS = """
            id, export_uuid, canonical_event_id, schema_id, schema_version, generated_at,
            data_sha256, source_set_sha256, candidate_content_sha256, content_sha256,
            content_size_bytes, export_path, source_observations::text as source_observations_json,
            source_snapshot_ids, warnings::text as warnings_json, validation_status,
            decided_at, decision_reason
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<J7ExportManifest> rowMapper = this::mapManifest;

    public JdbcJ7ExportManifestStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCanonicalEvent(UUID canonicalEventId) {
        List<UUID> locked = jdbcTemplate.query("""
                select id,
                       pg_advisory_xact_lock(hashtextextended(id::text, 7007))
                from canonical_event
                where id = :canonicalEventId
                for update
                """,
                new MapSqlParameterSource().addValue(
                        "canonicalEventId",
                        Objects.requireNonNull(canonicalEventId, "canonicalEventId")),
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
        if (locked.size() != 1) {
            throw new J7ExportException(J7ExportError.EVENT_NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public J7ExportManifest insertCandidate(J7ExportManifestDraft draft) {
        Objects.requireNonNull(draft, "draft");
        String sql = """
                insert into export_manifest (
                    export_kind, export_uuid, canonical_event_id, schema_id, schema_version,
                    export_path, content_sha256, created_at, validation_status,
                    source_snapshot_ids, warnings, generated_at, data_sha256,
                    source_set_sha256, candidate_content_sha256, content_size_bytes,
                    source_observations
                ) values (
                    :exportKind, :exportId, :canonicalEventId, :schemaId, :schemaVersion,
                    :relativePath, :contentSha256, :generatedAt, 'COHERENCE_CHECKED',
                    cast(:sourceSnapshotIds as bigint[]), cast(:warningsJson as jsonb),
                    :generatedAt, :dataSha256, :sourceSetSha256, :candidateContentSha256,
                    :contentSizeBytes, cast(:sourceObservationsJson as jsonb)
                )
                returning
                """ + SELECT_COLUMNS;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("exportKind", J7ExportContract.EXPORT_KIND)
                .addValue("exportId", draft.exportId())
                .addValue("canonicalEventId", draft.canonicalEventId())
                .addValue("schemaId", draft.schemaId())
                .addValue("schemaVersion", draft.schemaVersion())
                .addValue("relativePath", draft.relativePath())
                .addValue("contentSha256", draft.candidateContentSha256())
                .addValue("generatedAt", OffsetDateTime.ofInstant(
                        draft.generatedAt(), java.time.ZoneOffset.UTC))
                .addValue("sourceSnapshotIds", postgresBigintArray(draft.sourceSnapshotIds()))
                .addValue("warningsJson", draft.warningsJson())
                .addValue("dataSha256", draft.dataSha256())
                .addValue("sourceSetSha256", draft.sourceSetSha256())
                .addValue("candidateContentSha256", draft.candidateContentSha256())
                .addValue("contentSizeBytes", draft.contentSizeBytes())
                .addValue("sourceObservationsJson", draft.sourceObservationsJson());
        return jdbcTemplate.queryForObject(sql, parameters, rowMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<J7ExportManifest> findByExportId(UUID exportId) {
        return single("""
                select %s
                from export_manifest
                where export_kind = :exportKind
                  and export_uuid = :exportId
                """.formatted(SELECT_COLUMNS),
                new MapSqlParameterSource()
                        .addValue("exportKind", J7ExportContract.EXPORT_KIND)
                        .addValue("exportId", Objects.requireNonNull(exportId, "exportId")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<J7ExportManifest> findByCanonicalEventId(UUID canonicalEventId) {
        return List.copyOf(jdbcTemplate.query("""
                select %s
                from export_manifest
                where export_kind = :exportKind
                  and canonical_event_id = :canonicalEventId
                order by created_at desc, id desc
                """.formatted(SELECT_COLUMNS),
                new MapSqlParameterSource()
                        .addValue("exportKind", J7ExportContract.EXPORT_KIND)
                        .addValue(
                                "canonicalEventId",
                                Objects.requireNonNull(canonicalEventId, "canonicalEventId")),
                rowMapper));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<J7ExportManifest> findPending(
            UUID canonicalEventId,
            String schemaVersion) {
        return single("""
                select %s
                from export_manifest
                where export_kind = :exportKind
                  and canonical_event_id = :canonicalEventId
                  and schema_version = :schemaVersion
                  and validation_status = 'COHERENCE_CHECKED'
                """.formatted(SELECT_COLUMNS),
                new MapSqlParameterSource()
                        .addValue("exportKind", J7ExportContract.EXPORT_KIND)
                        .addValue("canonicalEventId", canonicalEventId)
                        .addValue("schemaVersion", schemaVersion));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<J7ExportManifest> findValidatedByDataSha256(
            UUID canonicalEventId,
            String schemaVersion,
            String dataSha256) {
        return single("""
                select %s
                from export_manifest
                where export_kind = :exportKind
                  and canonical_event_id = :canonicalEventId
                  and schema_version = :schemaVersion
                  and data_sha256 = :dataSha256
                  and validation_status = 'HUMAN_VALIDATED'
                """.formatted(SELECT_COLUMNS),
                new MapSqlParameterSource()
                        .addValue("exportKind", J7ExportContract.EXPORT_KIND)
                        .addValue("canonicalEventId", canonicalEventId)
                        .addValue("schemaVersion", schemaVersion)
                        .addValue("dataSha256", dataSha256));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<J7ExportDecision> findDecisionIntent(UUID exportId) {
        List<J7ExportDecision> intents = jdbcTemplate.query("""
                select decision_intent_status, decision_intent_at,
                       decision_intent_reason, decision_intent_path,
                       decision_intent_content_sha256,
                       decision_intent_content_size_bytes
                from export_manifest
                where export_kind = :exportKind
                  and export_uuid = :exportId
                  and decision_intent_status is not null
                """,
                new MapSqlParameterSource()
                        .addValue("exportKind", J7ExportContract.EXPORT_KIND)
                        .addValue("exportId", Objects.requireNonNull(exportId, "exportId")),
                this::mapDecisionIntent);
        return intents.stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public J7ExportDecision recordDecisionIntent(
            UUID exportId,
            J7ExportDecision decision) {
        Objects.requireNonNull(exportId, "exportId");
        Objects.requireNonNull(decision, "decision");
        List<J7ExportDecision> updated = jdbcTemplate.query("""
                update export_manifest
                set decision_intent_status = :status,
                    decision_intent_at = :decidedAt,
                    decision_intent_reason = :decisionReason,
                    decision_intent_path = :relativePath,
                    decision_intent_content_sha256 = :contentSha256,
                    decision_intent_content_size_bytes = :contentSizeBytes
                where export_kind = :exportKind
                  and export_uuid = :exportId
                  and validation_status = 'COHERENCE_CHECKED'
                  and decision_intent_status is null
                returning decision_intent_status, decision_intent_at,
                          decision_intent_reason, decision_intent_path,
                          decision_intent_content_sha256,
                          decision_intent_content_size_bytes
                """,
                decisionParameters(exportId, decision),
                this::mapDecisionIntent);
        if (updated.size() == 1) {
            return updated.getFirst();
        }
        Optional<J7ExportDecision> existing = findDecisionIntent(exportId);
        if (existing.filter(decision::equals).isPresent()) {
            return existing.orElseThrow();
        }
        throw new J7ExportException(J7ExportError.INVALID_TRANSITION);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearDecisionIntent(
            UUID exportId,
            J7ExportDecision expectedDecision) {
        Objects.requireNonNull(exportId, "exportId");
        Objects.requireNonNull(expectedDecision, "expectedDecision");
        int updated = jdbcTemplate.update("""
                update export_manifest
                set decision_intent_status = null,
                    decision_intent_at = null,
                    decision_intent_reason = null,
                    decision_intent_path = null,
                    decision_intent_content_sha256 = null,
                    decision_intent_content_size_bytes = null
                where export_kind = :exportKind
                  and export_uuid = :exportId
                  and validation_status = 'COHERENCE_CHECKED'
                  and decision_intent_status = :status
                  and decision_intent_at = :decidedAt
                  and decision_intent_reason is not distinct from :decisionReason
                  and decision_intent_path = :relativePath
                  and decision_intent_content_sha256 = :contentSha256
                  and decision_intent_content_size_bytes = :contentSizeBytes
                """,
                decisionParameters(exportId, expectedDecision));
        if (updated != 1) {
            throw new J7ExportException(J7ExportError.INVALID_TRANSITION);
        }
    }

    @Override
    @Transactional
    public J7ExportManifest decide(UUID exportId, J7ExportDecision decision) {
        Objects.requireNonNull(exportId, "exportId");
        Objects.requireNonNull(decision, "decision");
        List<J7ExportManifest> updated = jdbcTemplate.query("""
                update export_manifest
                set validation_status = :status,
                    export_path = :relativePath,
                    content_sha256 = :contentSha256,
                    content_size_bytes = :contentSizeBytes,
                    decided_at = :decidedAt,
                    decision_reason = :decisionReason
                where export_kind = :exportKind
                  and export_uuid = :exportId
                  and validation_status = 'COHERENCE_CHECKED'
                returning %s
                """.formatted(SELECT_COLUMNS),
                new MapSqlParameterSource()
                        .addValue("status", decision.status().name())
                        .addValue("relativePath", decision.relativePath())
                        .addValue("contentSha256", decision.contentSha256())
                        .addValue("contentSizeBytes", decision.contentSizeBytes())
                        .addValue("decidedAt", OffsetDateTime.ofInstant(
                                decision.decidedAt(), java.time.ZoneOffset.UTC))
                        .addValue("decisionReason", decision.reason().orElse(null))
                        .addValue("exportKind", J7ExportContract.EXPORT_KIND)
                        .addValue("exportId", exportId),
                rowMapper);
        if (updated.size() == 1) {
            return updated.getFirst();
        }
        Optional<J7ExportManifest> existing = findByExportId(exportId);
        if (existing.isEmpty()) {
            throw new J7ExportException(J7ExportError.EXPORT_NOT_FOUND);
        }
        J7ExportManifest manifest = existing.orElseThrow();
        if (manifest.status() == decision.status()
                && manifest.relativePath().equals(decision.relativePath())
                && manifest.currentContentSha256().equals(decision.contentSha256())
                && manifest.currentSizeBytes() == decision.contentSizeBytes()
                && manifest.decidedAt().equals(Optional.of(decision.decidedAt()))
                && manifest.decisionReason().equals(decision.reason())) {
            return manifest;
        }
        throw new J7ExportException(J7ExportError.INVALID_TRANSITION);
    }

    private MapSqlParameterSource decisionParameters(
            UUID exportId,
            J7ExportDecision decision) {
        return new MapSqlParameterSource()
                .addValue("status", decision.status().name())
                .addValue("decidedAt", OffsetDateTime.ofInstant(
                        decision.decidedAt(), java.time.ZoneOffset.UTC))
                .addValue("decisionReason", decision.reason().orElse(null))
                .addValue("relativePath", decision.relativePath())
                .addValue("contentSha256", decision.contentSha256())
                .addValue("contentSizeBytes", decision.contentSizeBytes())
                .addValue("exportKind", J7ExportContract.EXPORT_KIND)
                .addValue("exportId", exportId);
    }

    private J7ExportDecision mapDecisionIntent(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new J7ExportDecision(
                J7ExportStatus.valueOf(resultSet.getString("decision_intent_status")),
                resultSet.getObject("decision_intent_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(resultSet.getString("decision_intent_reason")),
                resultSet.getString("decision_intent_path"),
                resultSet.getString("decision_intent_content_sha256"),
                resultSet.getLong("decision_intent_content_size_bytes"));
    }

    private Optional<J7ExportManifest> single(
            String sql,
            MapSqlParameterSource parameters) {
        List<J7ExportManifest> rows = jdbcTemplate.query(sql, parameters, rowMapper);
        return rows.stream().findFirst();
    }

    private J7ExportManifest mapManifest(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new J7ExportManifest(
                resultSet.getLong("id"),
                resultSet.getObject("export_uuid", UUID.class),
                resultSet.getObject("canonical_event_id", UUID.class),
                resultSet.getString("schema_id"),
                resultSet.getString("schema_version"),
                resultSet.getObject("generated_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("data_sha256"),
                resultSet.getString("source_set_sha256"),
                resultSet.getString("candidate_content_sha256"),
                resultSet.getString("content_sha256"),
                resultSet.getLong("content_size_bytes"),
                resultSet.getString("export_path"),
                resultSet.getString("source_observations_json"),
                readLongArray(resultSet.getArray("source_snapshot_ids")),
                resultSet.getString("warnings_json"),
                J7ExportStatus.valueOf(resultSet.getString("validation_status")),
                optionalInstant(resultSet, "decided_at"),
                Optional.ofNullable(resultSet.getString("decision_reason")));
    }

    private static Optional<java.time.Instant> optionalInstant(
            ResultSet resultSet,
            String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static List<Long> readLongArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object raw = sqlArray.getArray();
        if (raw instanceof Long[] values) {
            return List.copyOf(Arrays.asList(values));
        }
        if (raw instanceof Object[] values) {
            List<Long> converted = new ArrayList<>(values.length);
            for (Object value : values) {
                converted.add(((Number) value).longValue());
            }
            return List.copyOf(converted);
        }
        throw new SQLException("Unsupported PostgreSQL bigint array representation");
    }

    private static String postgresBigintArray(List<Long> values) {
        return values.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
