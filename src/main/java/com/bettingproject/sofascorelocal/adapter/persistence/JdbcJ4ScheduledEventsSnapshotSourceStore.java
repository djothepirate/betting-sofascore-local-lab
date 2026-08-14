package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.event.J4ScheduledEventsSnapshotSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.J4ScheduledEventsSnapshotSourceStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcJ4ScheduledEventsSnapshotSourceStore
        implements J4ScheduledEventsSnapshotSourceStore {

    private static final String SELECT_SQL = """
            select
                id,
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
                schema_status
            from provider_snapshot
            where id = :snapshotId
              and provider = 'SOFASCORE'
              and logical_endpoint = 'SCHEDULED_EVENTS'
              and acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
              and received_at is not null
              and http_status is not null
              and content_type is not null
              and latency_ms is not null
              and payload_raw is not null
              and payload_size_bytes = octet_length(payload_raw)
              and payload_sha256 is not null
              and parser_version is not null
              and schema_status in ('PARSED', 'SCHEMA_INCOMPATIBLE')
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcJ4ScheduledEventsSnapshotSourceStore(
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<J4ScheduledEventsSnapshotSource> findById(long snapshotId) {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        List<J4ScheduledEventsSnapshotSource> matches = jdbcTemplate.query(
                SELECT_SQL,
                new MapSqlParameterSource("snapshotId", snapshotId),
                this::mapSource);
        return matches.stream().findFirst();
    }

    private J4ScheduledEventsSnapshotSource mapSource(ResultSet resultSet, int rowNumber)
            throws SQLException {
        OffsetDateTime requestedAt = resultSet.getObject("requested_at", OffsetDateTime.class);
        OffsetDateTime receivedAt = resultSet.getObject("received_at", OffsetDateTime.class);
        byte[] payload = resultSet.getBytes("payload_raw");
        if (requestedAt == null || receivedAt == null || payload == null) {
            throw new IllegalStateException("J4 snapshot source metadata is incomplete");
        }
        return new J4ScheduledEventsSnapshotSource(
                resultSet.getLong("id"),
                resultSet.getString("request_key"),
                requestedAt.toInstant(),
                receivedAt.toInstant(),
                resultSet.getInt("http_status"),
                resultSet.getString("content_type"),
                Duration.ofMillis(resultSet.getLong("latency_ms")),
                payload,
                resultSet.getString("payload_sha256"),
                resultSet.getString("parser_version"),
                RawSnapshotSchemaStatus.valueOf(resultSet.getString("schema_status")));
    }
}
