package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventObservationPersistenceResult;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

@Repository
public class JdbcCanonicalEventStore implements CanonicalEventStore {

    private static final String INSERT_IDENTITY_SQL = """
            insert into canonical_event (id, provider, provider_event_id)
            values (:canonicalEventId, :provider, :providerEventId)
            on conflict (provider, provider_event_id) do nothing
            """;

    private static final String SELECT_IDENTITY_SQL = """
            select id
            from canonical_event
            where provider = :provider
              and provider_event_id = :providerEventId
            """;

    private static final String INSERT_OBSERVATION_SQL = """
            insert into canonical_event_observation (
                canonical_event_id,
                source_kind,
                source_reference,
                source_snapshot_id,
                source_fixture_id,
                source_payload_sha256,
                parser_version,
                source_received_at,
                starts_at,
                home_team_provider_id,
                home_team_name,
                away_team_provider_id,
                away_team_name,
                status_type,
                status_description,
                tournament_provider_id,
                tournament_name,
                normalized_sha256
            ) values (
                :canonicalEventId,
                :sourceKind,
                :sourceReference,
                :sourceSnapshotId,
                :sourceFixtureId,
                :sourcePayloadSha256,
                :parserVersion,
                :sourceReceivedAt,
                :startsAt,
                :homeTeamProviderId,
                :homeTeamName,
                :awayTeamProviderId,
                :awayTeamName,
                :statusType,
                :statusDescription,
                :tournamentProviderId,
                :tournamentName,
                :normalizedSha256
            )
            on conflict (
                canonical_event_id,
                source_kind,
                source_reference,
                normalized_sha256
            ) do nothing
            """;

    private static final String SELECT_OBSERVATION_ID_SQL = """
            select id
            from canonical_event_observation
            where canonical_event_id = :canonicalEventId
              and source_kind = :sourceKind
              and source_reference = :sourceReference
              and normalized_sha256 = :normalizedSha256
            """;

    private static final String COUNT_OBSERVATIONS_SQL = """
            select count(*)
            from canonical_event_observation
            where canonical_event_id = :canonicalEventId
            """;

    private static final String VIEW_COLUMNS = """
            o.id as observation_id,
            e.id as canonical_event_id,
            e.provider,
            e.provider_event_id,
            o.starts_at,
            o.home_team_provider_id,
            o.home_team_name,
            o.away_team_provider_id,
            o.away_team_name,
            o.status_type,
            o.status_description,
            o.tournament_provider_id,
            o.tournament_name,
            o.source_kind,
            o.source_snapshot_id,
            o.source_fixture_id,
            o.source_payload_sha256,
            o.parser_version,
            o.source_received_at,
            o.normalized_sha256,
            count(*) over (partition by o.canonical_event_id) as observation_count,
            row_number() over (
                partition by o.canonical_event_id
                order by o.source_received_at desc, o.id desc
            ) as recency_rank
            """;

    private static final String FIND_BY_DATE_SQL = """
            with ranked as (
                select
                    """ + VIEW_COLUMNS + """
                from canonical_event_observation o
                join canonical_event e on e.id = o.canonical_event_id
            )
            select *
            from ranked
            where recency_rank = 1
              and starts_at >= :fromInclusive
              and starts_at < :toExclusive
            order by starts_at, canonical_event_id
            """;

    private static final String FIND_LATEST_SQL = """
            with ranked as (
                select
                    """ + VIEW_COLUMNS + """
                from canonical_event_observation o
                join canonical_event e on e.id = o.canonical_event_id
                where e.id = :canonicalEventId
            )
            select *
            from ranked
            where recency_rank = 1
            """;

    private static final String FIND_HISTORY_SQL = """
            select
                """ + VIEW_COLUMNS + """
            from canonical_event_observation o
            join canonical_event e on e.id = o.canonical_event_id
            where e.id = :canonicalEventId
            order by o.source_received_at desc, o.id desc
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCanonicalEventStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional
    public EventObservationPersistenceResult save(CanonicalEventObservation observation) {
        Objects.requireNonNull(observation, "observation");
        MapSqlParameterSource parameters = parameters(observation);
        int identityRows = jdbcTemplate.update(INSERT_IDENTITY_SQL, parameters);
        if (identityRows < 0 || identityRows > 1) {
            throw new IllegalStateException("canonical identity insert affected unexpected rows");
        }
        UUID storedIdentity = jdbcTemplate.queryForObject(
                SELECT_IDENTITY_SQL,
                parameters,
                UUID.class);
        if (!observation.identity().value().equals(storedIdentity)) {
            throw new IllegalStateException("stored canonical event identity is inconsistent");
        }

        int insertedRows = jdbcTemplate.update(INSERT_OBSERVATION_SQL, parameters);
        if (insertedRows < 0 || insertedRows > 1) {
            throw new IllegalStateException("event observation insert affected unexpected rows");
        }
        Long observationId = jdbcTemplate.queryForObject(
                SELECT_OBSERVATION_ID_SQL,
                parameters,
                Long.class);
        Long observationCount = jdbcTemplate.queryForObject(
                COUNT_OBSERVATIONS_SQL,
                parameters,
                Long.class);
        if (observationId == null || observationCount == null) {
            throw new IllegalStateException("stored event observation could not be resolved");
        }
        return new EventObservationPersistenceResult(
                observationId,
                storedIdentity,
                observationCount,
                insertedRows == 1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanonicalEventObservationView> findLatestStartingBetween(
            Instant fromInclusive,
            Instant toExclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toExclusive, "toExclusive");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("date-search interval must be increasing");
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("fromInclusive", fromInclusive.atOffset(ZoneOffset.UTC))
                .addValue("toExclusive", toExclusive.atOffset(ZoneOffset.UTC));
        return jdbcTemplate.query(FIND_BY_DATE_SQL, parameters, this::mapView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CanonicalEventObservationView> findLatestByCanonicalId(
            UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        List<CanonicalEventObservationView> matches = jdbcTemplate.query(
                FIND_LATEST_SQL,
                new MapSqlParameterSource("canonicalEventId", canonicalEventId),
                this::mapView);
        return matches.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanonicalEventObservationView> findHistory(UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        return jdbcTemplate.query(
                FIND_HISTORY_SQL,
                new MapSqlParameterSource("canonicalEventId", canonicalEventId),
                this::mapView);
    }

    private static MapSqlParameterSource parameters(CanonicalEventObservation observation) {
        EventSourceTrace source = observation.source();
        ScheduledTournament tournament = observation.tournament().orElse(null);
        return new MapSqlParameterSource()
                .addValue("canonicalEventId", observation.identity().value())
                .addValue("provider", observation.identity().provider())
                .addValue("providerEventId", observation.identity().providerEventId())
                .addValue("sourceKind", source.kind().name())
                .addValue("sourceReference", source.sourceReference())
                .addValue(
                        "sourceSnapshotId",
                        source.snapshotId().isPresent() ? source.snapshotId().getAsLong() : null,
                        Types.BIGINT)
                .addValue(
                        "sourceFixtureId",
                        source.fixtureId().orElse(null),
                        Types.VARCHAR)
                .addValue("sourcePayloadSha256", source.payloadSha256())
                .addValue("parserVersion", source.parserVersion())
                .addValue("sourceReceivedAt", source.receivedAt().atOffset(ZoneOffset.UTC))
                .addValue("startsAt", observation.startsAt().atOffset(ZoneOffset.UTC))
                .addValue("homeTeamProviderId", observation.homeTeam().providerTeamId())
                .addValue("homeTeamName", observation.homeTeam().name())
                .addValue("awayTeamProviderId", observation.awayTeam().providerTeamId())
                .addValue("awayTeamName", observation.awayTeam().name())
                .addValue("statusType", observation.status().type())
                .addValue(
                        "statusDescription",
                        observation.status().description().orElse(null),
                        Types.VARCHAR)
                .addValue(
                        "tournamentProviderId",
                        tournament == null ? null : tournament.providerTournamentId(),
                        Types.BIGINT)
                .addValue(
                        "tournamentName",
                        tournament == null ? null : tournament.name(),
                        Types.VARCHAR)
                .addValue("normalizedSha256", observation.normalizedSha256());
    }

    private CanonicalEventObservationView mapView(ResultSet resultSet, int rowNumber)
            throws SQLException {
        UUID canonicalId = resultSet.getObject("canonical_event_id", UUID.class);
        String provider = resultSet.getString("provider");
        long providerEventId = resultSet.getLong("provider_event_id");
        Long tournamentId = resultSet.getObject("tournament_provider_id", Long.class);
        String tournamentName = resultSet.getString("tournament_name");
        Long snapshotId = resultSet.getObject("source_snapshot_id", Long.class);
        String fixtureId = resultSet.getString("source_fixture_id");
        OffsetDateTime receivedAt = resultSet.getObject(
                "source_received_at",
                OffsetDateTime.class);
        OffsetDateTime startsAt = resultSet.getObject("starts_at", OffsetDateTime.class);
        if (canonicalId == null || receivedAt == null || startsAt == null) {
            throw new IllegalStateException("event observation persistence metadata is incomplete");
        }
        EventSourceKind sourceKind = EventSourceKind.valueOf(
                resultSet.getString("source_kind"));
        EventSourceTrace source = new EventSourceTrace(
                sourceKind,
                snapshotId == null ? OptionalLong.empty() : OptionalLong.of(snapshotId),
                Optional.ofNullable(fixtureId),
                resultSet.getString("source_payload_sha256"),
                resultSet.getString("parser_version"),
                receivedAt.toInstant());
        return new CanonicalEventObservationView(
                resultSet.getLong("observation_id"),
                new CanonicalEventIdentity(canonicalId, provider, providerEventId),
                startsAt.toInstant(),
                new ScheduledTeam(
                        resultSet.getLong("home_team_provider_id"),
                        resultSet.getString("home_team_name")),
                new ScheduledTeam(
                        resultSet.getLong("away_team_provider_id"),
                        resultSet.getString("away_team_name")),
                new ScheduledEventStatus(
                        resultSet.getString("status_type"),
                        Optional.ofNullable(resultSet.getString("status_description"))),
                tournamentId == null
                        ? Optional.empty()
                        : Optional.of(new ScheduledTournament(tournamentId, tournamentName)),
                source,
                resultSet.getString("normalized_sha256"),
                resultSet.getLong("observation_count"));
    }
}
