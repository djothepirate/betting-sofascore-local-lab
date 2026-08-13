package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.provider.J3StoredQualificationPage;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J3QualificationCheckpointStore;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

@Repository
public class JdbcJ3QualificationCheckpointStore implements J3QualificationCheckpointStore {

    private static final String SELECT_SQL = """
            select
                id,
                request_key,
                requested_at,
                received_at,
                http_status,
                content_type,
                latency_ms,
                schema_status,
                payload_raw,
                payload_size_bytes,
                payload_sha256
            from provider_snapshot
            where provider = 'SOFASCORE'
              and acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
              and logical_endpoint = 'SCHEDULED_EVENTS'
              and request_key in (:requestKeys)
            order by id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcJ3QualificationCheckpointStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<J3StoredQualificationPage> findStoredPages(LocalDate qualificationDate) {
        Objects.requireNonNull(qualificationDate, "qualificationDate");
        if (!ScheduledEventsProviderPageRequest.QUALIFICATION_DATE.equals(qualificationDate)) {
            throw new IllegalArgumentException("qualificationDate is not authorized");
        }

        Map<String, Integer> pageByRequestKey = IntStream.rangeClosed(
                        ScheduledEventsProviderPageRequest.FIRST_PAGE,
                        ScheduledEventsProviderPageRequest.LAST_PAGE)
                .boxed()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        page -> requestKey(qualificationDate, page),
                        page -> page));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("requestKeys", pageByRequestKey.keySet());

        return List.copyOf(jdbcTemplate.query(
                SELECT_SQL,
                parameters,
                (resultSet, rowNumber) -> {
                    String requestKey = resultSet.getString("request_key");
                    Integer page = pageByRequestKey.get(requestKey);
                    if (page == null) {
                        throw new IllegalStateException("unexpected qualification request key");
                    }
                    byte[] rawBytes = resultSet.getBytes("payload_raw");
                    Number storedSizeValue = (Number) resultSet.getObject("payload_size_bytes");
                    Number httpStatusValue = (Number) resultSet.getObject("http_status");
                    Number latencyValue = (Number) resultSet.getObject("latency_ms");
                    OffsetDateTime requestedAt = resultSet.getObject(
                            "requested_at", OffsetDateTime.class);
                    OffsetDateTime receivedAt = resultSet.getObject(
                            "received_at", OffsetDateTime.class);
                    String contentType = resultSet.getString("content_type");
                    String historicalSchemaStatus = resultSet.getString("schema_status");
                    String storedSha256 = resultSet.getString("payload_sha256");
                    if (rawBytes == null
                            || storedSizeValue == null
                            || httpStatusValue == null
                            || latencyValue == null
                            || requestedAt == null
                            || receivedAt == null
                            || contentType == null
                            || historicalSchemaStatus == null
                            || storedSha256 == null) {
                        throw new IllegalStateException(
                                "stored qualification checkpoint is incomplete");
                    }
                    RawPayloadEvidence payload = RawPayloadEvidence.capture(rawBytes);
                    long storedSize = storedSizeValue.longValue();
                    if (storedSize != payload.sizeBytes()
                            || !payload.sha256().equals(storedSha256)) {
                        throw new IllegalStateException(
                                "stored qualification payload metadata is inconsistent");
                    }
                    return new J3StoredQualificationPage(
                            resultSet.getLong("id"),
                            qualificationDate,
                            page,
                            requestKey,
                            requestedAt.toInstant(),
                            receivedAt.toInstant(),
                            httpStatusValue.intValue(),
                            contentType,
                            Duration.ofMillis(latencyValue.longValue()),
                            RawSnapshotSchemaStatus.valueOf(historicalSchemaStatus),
                            payload);
                }));
    }

    private static String requestKey(LocalDate date, int page) {
        return SofascoreEndpointType.SCHEDULED_EVENTS.name()
                + "|date=" + date + "|page=" + page;
    }
}
