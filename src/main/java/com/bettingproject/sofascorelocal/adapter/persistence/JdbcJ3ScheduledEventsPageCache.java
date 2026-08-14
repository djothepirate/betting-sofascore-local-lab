package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.provider.J3CachedScheduledEventsPage;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.J3ScheduledEventsPageCache;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcJ3ScheduledEventsPageCache implements J3ScheduledEventsPageCache {

    private static final String SELECT_FRESH_PARSED_SQL = """
            select
                snapshot.id,
                snapshot.request_key,
                cache.cached_at,
                snapshot.requested_at,
                snapshot.received_at,
                snapshot.http_status,
                snapshot.content_type,
                snapshot.latency_ms,
                snapshot.payload_raw,
                snapshot.payload_size_bytes,
                snapshot.payload_sha256,
                snapshot.parser_version
            from provider_response_cache cache
            join provider_snapshot snapshot
              on snapshot.provider = cache.provider
             and snapshot.logical_endpoint = cache.logical_endpoint
             and snapshot.request_key = cache.request_key
             and snapshot.id = cache.snapshot_id
            where cache.provider = 'SOFASCORE'
              and cache.logical_endpoint = 'SCHEDULED_EVENTS'
              and cache.request_key = :requestKey
              and cache.parser_version = :parserVersion
              and cache.cached_at > :freshAfter
              and cache.cached_at <= :evaluatedAt
              and snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
              and snapshot.http_status between 200 and 299
              and snapshot.schema_status = 'PARSED'
              and snapshot.error_code is null
              and snapshot.parser_version = cache.parser_version
              and snapshot.payload_raw is not null
            order by cache.cached_at desc, snapshot.id desc
            limit 1
            """;

    private static final String UPSERT_PARSED_SQL = """
            insert into provider_response_cache (
                provider,
                logical_endpoint,
                request_key,
                snapshot_id,
                cached_at,
                parser_version
            ) values (
                'SOFASCORE',
                'SCHEDULED_EVENTS',
                :requestKey,
                :snapshotId,
                :cachedAt,
                :parserVersion
            )
            on conflict (provider, logical_endpoint, request_key)
            do update set
                snapshot_id = excluded.snapshot_id,
                cached_at = excluded.cached_at,
                parser_version = excluded.parser_version
            where provider_response_cache.cached_at <= excluded.cached_at
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcJ3ScheduledEventsPageCache(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<J3CachedScheduledEventsPage> findFreshParsed(
            ScheduledEventsProviderPageRequest request,
            Instant evaluatedAt,
            Duration timeToLive,
            String parserVersion) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(timeToLive, "timeToLive");
        Objects.requireNonNull(parserVersion, "parserVersion");
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("timeToLive must be positive");
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("requestKey", request.requestKey())
                .addValue("parserVersion", parserVersion)
                .addValue("freshAfter", evaluatedAt.minus(timeToLive).atOffset(ZoneOffset.UTC))
                .addValue("evaluatedAt", evaluatedAt.atOffset(ZoneOffset.UTC));
        List<J3CachedScheduledEventsPage> candidates = jdbcTemplate.query(
                SELECT_FRESH_PARSED_SQL,
                parameters,
                (resultSet, rowNumber) -> mapCandidate(resultSet));
        return candidates.stream().findFirst();
    }

    @Override
    @Transactional
    public void recordParsed(
            ScheduledEventsProviderPageRequest request,
            ScheduledEventsTransportResponse response,
            RawSnapshotPersistenceResult persistence,
            String parserVersion) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(parserVersion, "parserVersion");
        if (!request.requestKey().equals(response.requestKey())) {
            throw new IllegalArgumentException("cache request and response keys must match");
        }
        if (!persistence.payloadSha256().equals(response.payload().sha256())
                || persistence.payloadSizeBytes() != response.payload().sizeBytes()) {
            throw new IllegalArgumentException(
                    "cache persistence metadata must match the parsed response");
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("requestKey", request.requestKey())
                .addValue("snapshotId", persistence.snapshotId())
                .addValue("cachedAt", response.receivedAt().atOffset(ZoneOffset.UTC))
                .addValue("parserVersion", parserVersion);
        int updatedRows = jdbcTemplate.update(UPSERT_PARSED_SQL, parameters);
        if (updatedRows != 1) {
            throw new IllegalStateException("parsed cache checkpoint was not recorded");
        }
    }

    private static J3CachedScheduledEventsPage mapCandidate(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        byte[] rawBytes = resultSet.getBytes("payload_raw");
        Number storedSizeValue = (Number) resultSet.getObject("payload_size_bytes");
        Number httpStatusValue = (Number) resultSet.getObject("http_status");
        Number latencyValue = (Number) resultSet.getObject("latency_ms");
        OffsetDateTime requestedAt = resultSet.getObject("requested_at", OffsetDateTime.class);
        OffsetDateTime receivedAt = resultSet.getObject("received_at", OffsetDateTime.class);
        OffsetDateTime cachedAt = resultSet.getObject("cached_at", OffsetDateTime.class);
        String requestKey = resultSet.getString("request_key");
        String contentType = resultSet.getString("content_type");
        String storedSha256 = resultSet.getString("payload_sha256");
        String parserVersion = resultSet.getString("parser_version");
        if (rawBytes == null
                || storedSizeValue == null
                || httpStatusValue == null
                || latencyValue == null
                || requestedAt == null
                || receivedAt == null
                || cachedAt == null
                || requestKey == null
                || contentType == null
                || storedSha256 == null
                || parserVersion == null) {
            throw new IllegalStateException("fresh cache candidate is incomplete");
        }

        RawPayloadEvidence payload = RawPayloadEvidence.capture(rawBytes);
        if (storedSizeValue.longValue() != payload.sizeBytes()
                || !storedSha256.equals(payload.sha256())) {
            throw new IllegalStateException("fresh cache candidate failed its integrity check");
        }
        return new J3CachedScheduledEventsPage(
                resultSet.getLong("id"),
                requestKey,
                cachedAt.toInstant(),
                requestedAt.toInstant(),
                receivedAt.toInstant(),
                httpStatusValue.intValue(),
                contentType,
                Duration.ofMillis(latencyValue.longValue()),
                payload,
                parserVersion);
    }
}
