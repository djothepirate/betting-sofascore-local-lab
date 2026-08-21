package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.provider.CachedTournamentScheduledEventsResponse;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsTransportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.net.URI;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcTournamentScheduledEventsCacheTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-18T09:00:00Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-18T09:00:01Z");
    private static final Instant EVALUATED_AT = Instant.parse("2026-08-18T09:05:00Z");
    private static final byte[] BODY = "{\"events\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private ResultSet resultSet;

    private JdbcTournamentScheduledEventsCache cache;

    @BeforeEach
    void setUp() {
        cache = new JdbcTournamentScheduledEventsCache(jdbcTemplate);
    }

    @Test
    void findsOnlyTheExactFreshParsedCacheScopeAndRechecksPayloadIntegrity()
            throws Exception {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(BODY);
        stubCompleteCandidate(payload.sha256(), payload.sizeBytes());
        when(jdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers
                        .<RowMapper<CachedTournamentScheduledEventsResponse>>any()))
                .thenAnswer(invocation -> {
                    RowMapper<CachedTournamentScheduledEventsResponse> mapper =
                            invocation.getArgument(2);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        var candidate = cache.findFreshParsed(
                request(),
                EVALUATED_AT,
                Duration.ofMinutes(10),
                CachedTournamentScheduledEventsResponse.PARSER_VERSION);

        assertThat(candidate).isPresent();
        assertThat(candidate.orElseThrow().snapshotId()).isEqualTo(41L);
        assertThat(candidate.orElseThrow().payload()).isEqualTo(payload);
        assertThat(candidate.orElseThrow().asTransportResponse().requestKey())
                .isEqualTo(request().requestKey());
        assertThat(candidate.orElseThrow().asPersistenceResult().payloadSha256())
                .isEqualTo(payload.sha256());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                parameters.capture(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<CachedTournamentScheduledEventsResponse>>any());
        assertThat(sql.getValue())
                .contains("cache.logical_endpoint = 'TOURNAMENT_SCHEDULED_EVENTS'")
                .contains("snapshot.schema_status = 'PARSED'")
                .contains("snapshot.parser_version = cache.parser_version");
        assertThat(parameters.getValue().getValue("requestKey"))
                .isEqualTo(request().requestKey());
        assertThat(parameters.getValue().getValue("parserVersion"))
                .isEqualTo(CachedTournamentScheduledEventsResponse.PARSER_VERSION);
        assertThat(parameters.getValue().getValue("freshAfter"))
                .isEqualTo(EVALUATED_AT.minus(Duration.ofMinutes(10))
                        .atOffset(ZoneOffset.UTC));
    }

    @Test
    void refusesAStoredCandidateWhoseBytesAndMetadataDiverge() throws Exception {
        stubCompleteCandidate("0".repeat(64), BODY.length);
        when(jdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers
                        .<RowMapper<CachedTournamentScheduledEventsResponse>>any()))
                .thenAnswer(invocation -> {
                    RowMapper<CachedTournamentScheduledEventsResponse> mapper =
                            invocation.getArgument(2);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        assertThatThrownBy(() -> cache.findFreshParsed(
                request(),
                EVALUATED_AT,
                Duration.ofMinutes(10),
                CachedTournamentScheduledEventsResponse.PARSER_VERSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("integrity");
    }

    @Test
    void recordsOnlyMatchingSuccessfulPersistedResponsesWithTheExactParser() {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(BODY);
        var response = response(request(), 200, payload);
        var persistence = new RawSnapshotPersistenceResult(
                41L,
                RawSnapshotPersistenceOutcome.INSERTED,
                payload.sha256(),
                payload.sizeBytes());
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class)))
                .thenReturn(1);

        cache.recordParsed(
                request(),
                response,
                persistence,
                CachedTournamentScheduledEventsResponse.PARSER_VERSION);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(sql.capture(), parameters.capture());
        assertThat(sql.getValue())
                .contains("'TOURNAMENT_SCHEDULED_EVENTS'")
                .contains("on conflict (provider, logical_endpoint, request_key)");
        assertThat(parameters.getValue().getValue("requestKey"))
                .isEqualTo(request().requestKey());
        assertThat(parameters.getValue().getValue("snapshotId")).isEqualTo(41L);
        assertThat(parameters.getValue().getValue("cachedAt"))
                .isEqualTo(RECEIVED_AT.atOffset(ZoneOffset.UTC));
    }

    @Test
    void rejectsInvalidTtlParserStatusKeyAndPersistenceBeforeJdbc() {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(BODY);
        var persistence = new RawSnapshotPersistenceResult(
                41L,
                RawSnapshotPersistenceOutcome.INSERTED,
                payload.sha256(),
                payload.sizeBytes());

        assertThatThrownBy(() -> cache.findFreshParsed(
                request(),
                EVALUATED_AT,
                Duration.ZERO,
                CachedTournamentScheduledEventsResponse.PARSER_VERSION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeToLive");
        assertThatThrownBy(() -> cache.findFreshParsed(
                request(),
                EVALUATED_AT,
                Duration.ofMinutes(10),
                "tournament-scheduled-v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly");
        assertThatThrownBy(() -> cache.recordParsed(
                request(),
                response(request(8L), 200, payload),
                persistence,
                CachedTournamentScheduledEventsResponse.PARSER_VERSION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keys");
        assertThatThrownBy(() -> cache.recordParsed(
                request(),
                response(request(), 500, payload),
                persistence,
                CachedTournamentScheduledEventsResponse.PARSER_VERSION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("successful");
        var mismatchedPersistence = new RawSnapshotPersistenceResult(
                41L,
                RawSnapshotPersistenceOutcome.INSERTED,
                "0".repeat(64),
                payload.sizeBytes());
        assertThatThrownBy(() -> cache.recordParsed(
                request(),
                response(request(), 200, payload),
                mismatchedPersistence,
                CachedTournamentScheduledEventsResponse.PARSER_VERSION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
    }

    private void stubCompleteCandidate(String storedSha256, int storedSize)
            throws Exception {
        when(resultSet.getBytes("payload_raw")).thenReturn(BODY);
        when(resultSet.getObject("payload_size_bytes")).thenReturn(storedSize);
        when(resultSet.getObject("http_status")).thenReturn(200);
        when(resultSet.getObject("latency_ms")).thenReturn(1_000L);
        when(resultSet.getObject("requested_at", OffsetDateTime.class))
                .thenReturn(REQUESTED_AT.atOffset(ZoneOffset.UTC));
        when(resultSet.getObject("received_at", OffsetDateTime.class))
                .thenReturn(RECEIVED_AT.atOffset(ZoneOffset.UTC));
        when(resultSet.getObject("cached_at", OffsetDateTime.class))
                .thenReturn(RECEIVED_AT.atOffset(ZoneOffset.UTC));
        when(resultSet.getString("request_key")).thenReturn(request().requestKey());
        when(resultSet.getString("content_type"))
                .thenReturn("application/json");
        when(resultSet.getString("payload_sha256")).thenReturn(storedSha256);
        when(resultSet.getString("parser_version"))
                .thenReturn(CachedTournamentScheduledEventsResponse.PARSER_VERSION);
        lenient().when(resultSet.getLong("id")).thenReturn(41L);
    }

    private static TournamentScheduledEventsTransportResponse response(
            TournamentScheduledEventsProviderRequest request,
            int status,
            RawPayloadEvidence payload) {
        return new TournamentScheduledEventsTransportResponse(
                request.requestKey(),
                REQUESTED_AT,
                RECEIVED_AT,
                status,
                "application/json",
                Duration.ofSeconds(1),
                payload);
    }

    private static TournamentScheduledEventsProviderRequest request() {
        return request(7L);
    }

    private static TournamentScheduledEventsProviderRequest request(long id) {
        return new TournamentScheduledEventsProviderRequest(
                URI.create(TournamentScheduledEventsProviderRequest.EXPECTED_ORIGIN),
                LocalDate.parse("2026-08-18"),
                id);
    }
}
