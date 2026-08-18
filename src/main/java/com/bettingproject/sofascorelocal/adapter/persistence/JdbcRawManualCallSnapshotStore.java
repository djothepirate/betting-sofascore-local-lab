package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.ZoneOffset;
import java.util.Objects;

@Repository
public class JdbcRawManualCallSnapshotStore implements RawManualCallSnapshotStore {

    static final String PROVIDER = "SOFASCORE";
    static final String ACQUISITION_MODE = "DIRECT_LOCAL_ENDPOINT";

    private static final String INSERT_SQL = """
            insert into provider_snapshot (
                provider,
                acquisition_mode,
                logical_endpoint,
                request_key,
                requested_at,
                received_at,
                http_status,
                content_type,
                latency_ms,
                payload_raw,
                payload_size_bytes,
                payload_sha256,
                parser_version,
                schema_status,
                error_code
            ) values (
                :provider,
                :acquisitionMode,
                :logicalEndpoint,
                :requestKey,
                :requestedAt,
                :receivedAt,
                :httpStatus,
                :contentType,
                :latencyMs,
                :payloadRaw,
                :payloadSizeBytes,
                :payloadSha256,
                :parserVersion,
                :schemaStatus,
                :errorCode
            )
            on conflict (provider, logical_endpoint, request_key, payload_sha256)
                where payload_sha256 is not null
            do nothing
            """;

    private static final String SELECT_ID_SQL = """
            select id
            from provider_snapshot
            where provider = :provider
              and logical_endpoint = :logicalEndpoint
              and request_key = :requestKey
              and payload_sha256 = :payloadSha256
            """;

    private static final String CLASSIFY_SQL = """
            update provider_snapshot
            set schema_status = :schemaStatus,
                error_code = :errorCode
            where id = :snapshotId
              and (
                    schema_status = 'RAW_ONLY'
                    or (
                        schema_status = :schemaStatus
                        and error_code is not distinct from :errorCode
                    )
              )
            """;

    private static final String INSERT_OCCURRENCE_SQL = """
            insert into provider_snapshot_occurrence (
                snapshot_id,
                requested_at,
                received_at,
                http_status,
                content_type,
                latency_ms,
                parser_version,
                persistence_outcome
            ) values (
                :snapshotId,
                :requestedAt,
                :receivedAt,
                :httpStatus,
                :contentType,
                :latencyMs,
                :parserVersion,
                :persistenceOutcome
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRawManualCallSnapshotStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public RawSnapshotPersistenceResult save(RawManualCallSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        RawPayloadEvidence payload = snapshot.payload();
        MapSqlParameterSource parameters = parameters(snapshot, payload);

        int insertedRows = jdbcTemplate.update(INSERT_SQL, parameters);
        if (insertedRows != 0 && insertedRows != 1) {
            throw new IllegalStateException(
                    "raw snapshot insert affected an unexpected number of rows: " + insertedRows);
        }

        Long snapshotId = jdbcTemplate.queryForObject(
                SELECT_ID_SQL,
                parameters,
                Long.class);
        if (snapshotId == null) {
            throw new IllegalStateException("raw snapshot persistence did not resolve an identifier");
        }

        RawSnapshotPersistenceOutcome outcome = insertedRows == 1
                ? RawSnapshotPersistenceOutcome.INSERTED
                : RawSnapshotPersistenceOutcome.DEDUPLICATED;
        parameters
                .addValue("snapshotId", snapshotId)
                .addValue("persistenceOutcome", outcome.name());
        int occurrenceRows = jdbcTemplate.update(INSERT_OCCURRENCE_SQL, parameters);
        if (occurrenceRows != 1) {
            throw new IllegalStateException(
                    "raw snapshot occurrence insert affected an unexpected number of rows: "
                            + occurrenceRows);
        }

        return new RawSnapshotPersistenceResult(
                snapshotId,
                outcome,
                payload.sha256(),
                payload.sizeBytes());
    }

    @Override
    @Transactional
    public void classify(
            long snapshotId,
            RawSnapshotSchemaStatus schemaStatus,
            String errorCode) {
        requireFinalClassification(snapshotId, schemaStatus, errorCode);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("schemaStatus", schemaStatus.name())
                .addValue("errorCode", errorCode, Types.VARCHAR);
        int updatedRows = jdbcTemplate.update(CLASSIFY_SQL, parameters);
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "raw snapshot classification requires one RAW_ONLY or identical row");
        }
    }

    private static MapSqlParameterSource parameters(
            RawManualCallSnapshot snapshot,
            RawPayloadEvidence payload) {
        return new MapSqlParameterSource()
                .addValue("provider", PROVIDER)
                .addValue("acquisitionMode", ACQUISITION_MODE)
                .addValue("logicalEndpoint", snapshot.endpointType().name())
                .addValue("requestKey", snapshot.requestKey())
                .addValue("requestedAt", snapshot.requestedAt().atOffset(ZoneOffset.UTC))
                .addValue("receivedAt", snapshot.receivedAt().atOffset(ZoneOffset.UTC))
                .addValue("httpStatus", snapshot.httpStatus())
                .addValue("contentType", snapshot.contentType())
                .addValue("latencyMs", snapshot.latencyMillis())
                .addValue("payloadRaw", payload.bytes(), Types.BINARY)
                .addValue("payloadSizeBytes", payload.sizeBytes())
                .addValue("payloadSha256", payload.sha256())
                .addValue("parserVersion", snapshot.parserVersion())
                .addValue("schemaStatus", snapshot.schemaStatus().name())
                .addValue("errorCode", snapshot.errorCode(), Types.VARCHAR);
    }

    private static void requireFinalClassification(
            long snapshotId,
            RawSnapshotSchemaStatus schemaStatus,
            String errorCode) {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        Objects.requireNonNull(schemaStatus, "schemaStatus");
        if (schemaStatus != RawSnapshotSchemaStatus.PARSED
                && schemaStatus != RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE
                && schemaStatus != RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE
                && schemaStatus != RawSnapshotSchemaStatus.UNEXPECTED_CONTENT
                && schemaStatus != RawSnapshotSchemaStatus.TRANSPORT_ERROR) {
            throw new IllegalArgumentException(
                    "raw snapshot classification must be a parser outcome");
        }
        if (schemaStatus == RawSnapshotSchemaStatus.PARSED
                || schemaStatus == RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE) {
            if (errorCode != null) {
                throw new IllegalArgumentException(
                        "a parsed or unavailable snapshot cannot have an error code");
            }
        }
        else if (schemaStatus == RawSnapshotSchemaStatus.TRANSPORT_ERROR) {
            if (errorCode == null
                    || errorCode.length() > 96
                    || !errorCode.matches("[A-Z0-9_]+")) {
                throw new IllegalArgumentException(
                        "a transport error requires a bounded safe error code");
            }
        }
        else if (!schemaStatus.name().equals(errorCode)) {
            throw new IllegalArgumentException(
                    "an incompatible snapshot requires its schema status as error code");
        }
    }
}
