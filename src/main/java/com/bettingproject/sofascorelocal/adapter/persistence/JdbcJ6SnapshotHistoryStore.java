package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.history.J6RawPayloadState;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotOccurrenceOutcome;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotTrace;
import com.bettingproject.sofascorelocal.port.J6SnapshotHistoryStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Repository
public class JdbcJ6SnapshotHistoryStore implements J6SnapshotHistoryStore {

    private static final String FIND_TRACES_SQL = """
            select
                snapshot.id as snapshot_id,
                snapshot.payload_raw is not null as payload_retained,
                snapshot.payload_size_bytes,
                occurrence.id as occurrence_id,
                occurrence.received_at,
                occurrence.persistence_outcome
            from provider_snapshot snapshot
            join provider_snapshot_occurrence occurrence
              on occurrence.snapshot_id = snapshot.id
            where snapshot.id in (:snapshotIds)
            order by snapshot.id,
                     occurrence.received_at asc nulls first,
                     occurrence.id asc
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcJ6SnapshotHistoryStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, J6SnapshotTrace> findTraces(Set<Long> snapshotIds) {
        Objects.requireNonNull(snapshotIds, "snapshotIds");
        if (snapshotIds.isEmpty()) {
            return Map.of();
        }
        if (snapshotIds.stream().anyMatch(value -> value == null || value < 1)) {
            throw new IllegalArgumentException("snapshotIds must contain only positive values");
        }
        List<StoredOccurrence> rows = jdbcTemplate.query(
                FIND_TRACES_SQL,
                new MapSqlParameterSource("snapshotIds", snapshotIds),
                this::mapOccurrence);
        Map<Long, List<StoredOccurrence>> grouped = new LinkedHashMap<>();
        for (StoredOccurrence row : rows) {
            grouped.computeIfAbsent(row.snapshotId(), ignored -> new ArrayList<>()).add(row);
        }
        Map<Long, J6SnapshotTrace> traces = new LinkedHashMap<>();
        for (Map.Entry<Long, List<StoredOccurrence>> entry : grouped.entrySet()) {
            List<StoredOccurrence> occurrences = entry.getValue();
            StoredOccurrence latest = occurrences.getLast();
            int duplicateCount = Math.toIntExact(occurrences.stream()
                    .filter(value -> value.outcome()
                            == J6SnapshotOccurrenceOutcome.DEDUPLICATED)
                    .count());
            traces.put(entry.getKey(), new J6SnapshotTrace(
                    entry.getKey(),
                    occurrences.size(),
                    duplicateCount,
                    latest.outcome(),
                    java.util.Optional.ofNullable(latest.receivedAt()),
                    latest.payloadRetained()
                            ? J6RawPayloadState.RETAINED
                            : latest.payloadSizeBytes() == null
                                    ? J6RawPayloadState.LEGACY_ABSENT
                                    : J6RawPayloadState.PAYLOAD_PURGED));
        }
        return Map.copyOf(traces);
    }

    private StoredOccurrence mapOccurrence(ResultSet resultSet, int rowNumber)
            throws SQLException {
        OffsetDateTime receivedAt = resultSet.getObject("received_at", OffsetDateTime.class);
        return new StoredOccurrence(
                resultSet.getLong("snapshot_id"),
                resultSet.getBoolean("payload_retained"),
                resultSet.getObject("payload_size_bytes", Long.class),
                receivedAt == null ? null : receivedAt.toInstant(),
                J6SnapshotOccurrenceOutcome.valueOf(
                        resultSet.getString("persistence_outcome")));
    }

    private record StoredOccurrence(
            long snapshotId,
            boolean payloadRetained,
            Long payloadSizeBytes,
            java.time.Instant receivedAt,
            J6SnapshotOccurrenceOutcome outcome) {
    }
}
