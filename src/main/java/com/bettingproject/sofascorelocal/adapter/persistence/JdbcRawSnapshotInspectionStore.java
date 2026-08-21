package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcRawSnapshotInspectionStore implements RawSnapshotInspectionStore {

    private static final String SELECT_RECENT_SQL = """
            select
                id,
                acquisition_mode,
                logical_endpoint,
                request_key,
                received_at,
                http_status,
                content_type,
                payload_size_bytes,
                payload_sha256,
                parser_version,
                schema_status
            from provider_snapshot
            where provider = 'SOFASCORE'
              and acquisition_mode in ('DIRECT_LOCAL_ENDPOINT', 'MANUAL_LOCAL_JSON_IMPORT')
              and payload_raw is not null
              and payload_size_bytes is not null
              and payload_sha256 is not null
              and received_at is not null
              and http_status is not null
              and content_type is not null
              and parser_version is not null
            order by created_at desc, id desc
            limit :limit
            """;

    private static final String SELECT_ONE_SQL = """
            select
                id,
                acquisition_mode,
                logical_endpoint,
                request_key,
                received_at,
                http_status,
                content_type,
                payload_size_bytes,
                payload_sha256,
                parser_version,
                schema_status,
                payload_raw
            from provider_snapshot
            where id = :snapshotId
              and provider = 'SOFASCORE'
              and acquisition_mode in ('DIRECT_LOCAL_ENDPOINT', 'MANUAL_LOCAL_JSON_IMPORT')
              and payload_raw is not null
              and payload_size_bytes is not null
              and payload_sha256 is not null
              and received_at is not null
              and http_status is not null
              and content_type is not null
              and parser_version is not null
            """;

    private static final int MAXIMUM_LIST_LIMIT = 100;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRawSnapshotInspectionStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawSnapshotInspectionSummary> findRecent(int limit) {
        if (limit < 1 || limit > MAXIMUM_LIST_LIMIT) {
            throw new IllegalArgumentException("inspection list limit must be between 1 and 100");
        }
        return jdbcTemplate.query(
                SELECT_RECENT_SQL,
                new MapSqlParameterSource("limit", limit),
                (resultSet, rowNumber) -> mapSummary(resultSet));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RawSnapshotInspectionSource> findById(long snapshotId) {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        List<RawSnapshotInspectionSource> matches = jdbcTemplate.query(
                SELECT_ONE_SQL,
                new MapSqlParameterSource("snapshotId", snapshotId),
                (resultSet, rowNumber) -> {
                    byte[] payloadRaw = resultSet.getBytes("payload_raw");
                    if (payloadRaw == null || payloadRaw.length > RawPayloadEvidence.MAXIMUM_BYTES) {
                        throw new IllegalStateException(
                                "inspectable snapshot payload is missing or exceeds its bound");
                    }
                    return new RawSnapshotInspectionSource(
                            mapSummary(resultSet),
                            payloadRaw);
                });
        return matches.stream().findFirst();
    }

    private static RawSnapshotInspectionSummary mapSummary(ResultSet resultSet)
            throws SQLException {
        OffsetDateTime receivedAt = resultSet.getObject("received_at", OffsetDateTime.class);
        Number payloadSize = (Number) resultSet.getObject("payload_size_bytes");
        Integer httpStatus = resultSet.getObject("http_status", Integer.class);
        if (receivedAt == null || payloadSize == null || httpStatus == null) {
            throw new IllegalStateException("inspectable snapshot metadata is incomplete");
        }
        return new RawSnapshotInspectionSummary(
                resultSet.getLong("id"),
                RawSnapshotAcquisitionMode.valueOf(
                        resultSet.getString("acquisition_mode")),
                resultSet.getString("logical_endpoint"),
                resultSet.getString("request_key"),
                receivedAt.toInstant(),
                httpStatus,
                resultSet.getString("content_type"),
                payloadSize.longValue(),
                resultSet.getString("payload_sha256"),
                resultSet.getString("parser_version"),
                RawSnapshotSchemaStatus.valueOf(resultSet.getString("schema_status")));
    }
}
