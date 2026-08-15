package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcJ5EventDataStore implements J5EventDataStore {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();

    private static final String INSERT_OBSERVATION_SQL = """
            insert into j5_event_data_observation (
                canonical_event_id,
                endpoint_type,
                source_kind,
                source_reference,
                source_snapshot_id,
                source_fixture_id,
                source_payload_sha256,
                parser_version,
                source_received_at,
                completeness_status,
                completeness_score,
                present_signals,
                expected_signals,
                missing_paths_json,
                lineups_confirmed,
                normalized_sha256
            ) values (
                :canonicalEventId,
                :endpointType,
                :sourceKind,
                :sourceReference,
                :sourceSnapshotId,
                :sourceFixtureId,
                :sourcePayloadSha256,
                :parserVersion,
                :sourceReceivedAt,
                :completenessStatus,
                :completenessScore,
                :presentSignals,
                :expectedSignals,
                cast(:missingPathsJson as jsonb),
                :lineupsConfirmed,
                :normalizedSha256
            )
            on conflict (
                canonical_event_id,
                endpoint_type,
                source_kind,
                source_reference,
                normalized_sha256
            )
            do nothing
            """;

    private static final String SELECT_OBSERVATION_ID_SQL = """
            select id
            from j5_event_data_observation
            where canonical_event_id = :canonicalEventId
              and endpoint_type = :endpointType
              and source_kind = :sourceKind
              and source_reference = :sourceReference
              and normalized_sha256 = :normalizedSha256
            """;

    private static final String FIND_LATEST_SQL = """
            select distinct on (d.endpoint_type)
                d.id as observation_id,
                d.canonical_event_id,
                e.provider,
                e.provider_event_id,
                d.endpoint_type,
                d.source_kind,
                d.source_reference,
                d.source_snapshot_id,
                d.source_fixture_id,
                d.source_payload_sha256,
                d.parser_version,
                d.source_received_at,
                d.completeness_status,
                d.completeness_score,
                d.present_signals,
                d.expected_signals,
                d.missing_paths_json::text as missing_paths_json,
                d.lineups_confirmed,
                d.normalized_sha256
            from j5_event_data_observation d
            join canonical_event e on e.id = d.canonical_event_id
            where d.canonical_event_id = :canonicalEventId
            order by d.endpoint_type, d.source_received_at desc, d.id desc
            """;

    private static final String INSERT_METRIC_SQL = """
            insert into j5_event_metric (
                observation_id,
                endpoint_type,
                metric_order,
                period,
                group_name,
                metric_code,
                metric_name,
                home_value,
                away_value
            ) values (
                :observationId,
                'EVENT_STATISTICS',
                :metricOrder,
                :period,
                :groupName,
                :metricCode,
                :metricName,
                :homeValue,
                :awayValue
            )
            """;

    private static final String FIND_METRICS_SQL = """
            select
                period,
                group_name,
                metric_code,
                metric_name,
                home_value,
                away_value
            from j5_event_metric
            where observation_id = :observationId
            order by metric_order
            """;

    private static final String INSERT_INCIDENT_SQL = """
            insert into j5_event_incident (
                observation_id,
                endpoint_type,
                incident_order,
                incident_type,
                minute,
                added_time,
                is_home,
                participant_provider_id,
                player_provider_id,
                player_name,
                home_score,
                away_score
            ) values (
                :observationId,
                'EVENT_INCIDENTS',
                :incidentOrder,
                :incidentType,
                :minute,
                :addedTime,
                :isHome,
                :participantProviderId,
                :playerProviderId,
                :playerName,
                :homeScore,
                :awayScore
            )
            """;

    private static final String FIND_INCIDENTS_SQL = """
            select
                incident_order,
                incident_type,
                minute,
                added_time,
                is_home,
                participant_provider_id,
                player_provider_id,
                player_name,
                home_score,
                away_score
            from j5_event_incident
            where observation_id = :observationId
            order by incident_order
            """;

    private static final String INSERT_LINEUP_SIDE_SQL = """
            insert into j5_event_lineup_side (
                observation_id,
                endpoint_type,
                side,
                formation
            ) values (
                :observationId,
                'EVENT_LINEUPS',
                :side,
                :formation
            )
            """;

    private static final String FIND_LINEUP_SIDES_SQL = """
            select side, formation
            from j5_event_lineup_side
            where observation_id = :observationId
            """;

    private static final String INSERT_LINEUP_PLAYER_SQL = """
            insert into j5_event_lineup_player (
                observation_id,
                endpoint_type,
                side,
                player_order,
                player_provider_id,
                player_name,
                shirt_number,
                position,
                starter
            ) values (
                :observationId,
                'EVENT_LINEUPS',
                :side,
                :playerOrder,
                :playerProviderId,
                :playerName,
                :shirtNumber,
                :position,
                :starter
            )
            """;

    private static final String FIND_LINEUP_PLAYERS_SQL = """
            select
                side,
                player_order,
                player_provider_id,
                player_name,
                shirt_number,
                position,
                starter
            from j5_event_lineup_player
            where observation_id = :observationId
            order by side, player_order
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcJ5EventDataStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional
    public J5EventDataPersistenceResult save(J5EventDataObservation observation) {
        Objects.requireNonNull(observation, "observation");
        MapSqlParameterSource parameters = observationParameters(observation);
        int insertedRows = jdbcTemplate.update(INSERT_OBSERVATION_SQL, parameters);
        if (insertedRows < 0 || insertedRows > 1) {
            throw new IllegalStateException("J5 observation insert affected unexpected rows");
        }
        Long observationId = jdbcTemplate.queryForObject(
                SELECT_OBSERVATION_ID_SQL,
                parameters,
                Long.class);
        if (observationId == null) {
            throw new IllegalStateException("stored J5 observation could not be resolved");
        }
        if (insertedRows == 1) {
            insertNormalizedRows(observationId, observation.data());
        }
        return new J5EventDataPersistenceResult(
                observationId,
                observation.identity().value(),
                observation.data().endpointType(),
                insertedRows == 1);
    }

    @Override
    @Transactional(readOnly = true)
    public J5EventDataBundle findLatest(UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        List<StoredParent> parents = jdbcTemplate.query(
                FIND_LATEST_SQL,
                new MapSqlParameterSource("canonicalEventId", canonicalEventId),
                this::mapParent);
        Map<SofascoreEndpointType, J5EventDataObservationView> views =
                new EnumMap<>(SofascoreEndpointType.class);
        for (StoredParent parent : parents) {
            views.put(parent.endpointType(), toView(parent));
        }
        return new J5EventDataBundle(
                Optional.ofNullable(views.get(SofascoreEndpointType.EVENT_STATISTICS)),
                Optional.ofNullable(views.get(SofascoreEndpointType.EVENT_INCIDENTS)),
                Optional.ofNullable(views.get(SofascoreEndpointType.EVENT_LINEUPS)));
    }

    private static MapSqlParameterSource observationParameters(
            J5EventDataObservation observation) {
        EventSourceTrace source = observation.source();
        J5CompletenessReport completeness = observation.completeness();
        Boolean lineupsConfirmed = observation.data() instanceof EventLineups lineups
                ? lineups.confirmed()
                : null;
        return new MapSqlParameterSource()
                .addValue("canonicalEventId", observation.identity().value())
                .addValue("endpointType", observation.data().endpointType().name())
                .addValue("sourceKind", source.kind().name())
                .addValue("sourceReference", source.sourceReference())
                .addValue(
                        "sourceSnapshotId",
                        source.snapshotId().isPresent()
                                ? source.snapshotId().getAsLong()
                                : null,
                        Types.BIGINT)
                .addValue("sourceFixtureId", source.fixtureId().orElse(null), Types.VARCHAR)
                .addValue("sourcePayloadSha256", source.payloadSha256())
                .addValue("parserVersion", source.parserVersion())
                .addValue("sourceReceivedAt", source.receivedAt().atOffset(ZoneOffset.UTC))
                .addValue("completenessStatus", completeness.status().name())
                .addValue("completenessScore", completeness.scorePercent())
                .addValue("presentSignals", completeness.presentSignals())
                .addValue("expectedSignals", completeness.expectedSignals())
                .addValue("missingPathsJson", writeMissingPaths(completeness.missingPaths()))
                .addValue("lineupsConfirmed", lineupsConfirmed, Types.BOOLEAN)
                .addValue("normalizedSha256", observation.normalizedSha256());
    }

    private void insertNormalizedRows(long observationId, J5EventData data) {
        switch (data) {
            case EventStatistics statistics -> insertMetrics(observationId, statistics);
            case EventIncidents incidents -> insertIncidents(observationId, incidents);
            case EventLineups lineups -> insertLineups(observationId, lineups);
        }
    }

    private void insertMetrics(long observationId, EventStatistics statistics) {
        SqlParameterSource[] batches = new SqlParameterSource[statistics.metrics().size()];
        for (int index = 0; index < statistics.metrics().size(); index++) {
            EventStatisticMetric metric = statistics.metrics().get(index);
            batches[index] = new MapSqlParameterSource()
                    .addValue("observationId", observationId)
                    .addValue("metricOrder", index)
                    .addValue("period", metric.period())
                    .addValue("groupName", metric.groupName())
                    .addValue("metricCode", metric.metricCode())
                    .addValue("metricName", metric.metricName())
                    .addValue("homeValue", metric.homeValue().orElse(null), Types.VARCHAR)
                    .addValue("awayValue", metric.awayValue().orElse(null), Types.VARCHAR);
        }
        if (batches.length > 0) {
            jdbcTemplate.batchUpdate(INSERT_METRIC_SQL, batches);
        }
    }

    private void insertIncidents(long observationId, EventIncidents incidents) {
        SqlParameterSource[] batches = new SqlParameterSource[incidents.incidents().size()];
        for (int index = 0; index < incidents.incidents().size(); index++) {
            EventIncident incident = incidents.incidents().get(index);
            batches[index] = new MapSqlParameterSource()
                    .addValue("observationId", observationId)
                    .addValue("incidentOrder", incident.sequence())
                    .addValue("incidentType", incident.incidentType())
                    .addValue("minute", incident.minute())
                    .addValue("addedTime", incident.addedTime().orElse(null), Types.SMALLINT)
                    .addValue("isHome", incident.home().orElse(null), Types.BOOLEAN)
                    .addValue(
                            "participantProviderId",
                            incident.participantProviderId().orElse(null),
                            Types.BIGINT)
                    .addValue(
                            "playerProviderId",
                            incident.playerProviderId().orElse(null),
                            Types.BIGINT)
                    .addValue("playerName", incident.playerName().orElse(null), Types.VARCHAR)
                    .addValue("homeScore", incident.homeScore().orElse(null), Types.SMALLINT)
                    .addValue("awayScore", incident.awayScore().orElse(null), Types.SMALLINT);
        }
        if (batches.length > 0) {
            jdbcTemplate.batchUpdate(INSERT_INCIDENT_SQL, batches);
        }
    }

    private void insertLineups(long observationId, EventLineups lineups) {
        TeamLineup[] sides = {lineups.home(), lineups.away()};
        SqlParameterSource[] sideBatches = new SqlParameterSource[sides.length];
        List<SqlParameterSource> playerBatches = new ArrayList<>();
        for (int sideIndex = 0; sideIndex < sides.length; sideIndex++) {
            TeamLineup side = sides[sideIndex];
            sideBatches[sideIndex] = new MapSqlParameterSource()
                    .addValue("observationId", observationId)
                    .addValue("side", side.side().name())
                    .addValue("formation", side.formation().orElse(null), Types.VARCHAR);
            for (int playerIndex = 0; playerIndex < side.players().size(); playerIndex++) {
                EventLineupPlayer player = side.players().get(playerIndex);
                playerBatches.add(new MapSqlParameterSource()
                        .addValue("observationId", observationId)
                        .addValue("side", side.side().name())
                        .addValue("playerOrder", playerIndex)
                        .addValue("playerProviderId", player.providerPlayerId())
                        .addValue("playerName", player.name())
                        .addValue(
                                "shirtNumber",
                                player.shirtNumber().orElse(null),
                                Types.SMALLINT)
                        .addValue("position", player.position().orElse(null), Types.VARCHAR)
                        .addValue("starter", player.starter()));
            }
        }
        jdbcTemplate.batchUpdate(INSERT_LINEUP_SIDE_SQL, sideBatches);
        if (!playerBatches.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    INSERT_LINEUP_PLAYER_SQL,
                    playerBatches.toArray(SqlParameterSource[]::new));
        }
    }

    private StoredParent mapParent(ResultSet resultSet, int rowNumber) throws SQLException {
        long observationId = resultSet.getLong("observation_id");
        UUID canonicalEventId = resultSet.getObject("canonical_event_id", UUID.class);
        long providerEventId = resultSet.getLong("provider_event_id");
        SofascoreEndpointType endpointType = SofascoreEndpointType.valueOf(
                resultSet.getString("endpoint_type"));
        EventSourceKind sourceKind = EventSourceKind.valueOf(
                resultSet.getString("source_kind"));
        Long snapshotId = resultSet.getObject("source_snapshot_id", Long.class);
        String fixtureId = resultSet.getString("source_fixture_id");
        OffsetDateTime receivedAt = resultSet.getObject(
                "source_received_at",
                OffsetDateTime.class);
        if (canonicalEventId == null || receivedAt == null) {
            throw new IllegalStateException("J5 persistence metadata is incomplete");
        }
        EventSourceTrace source = switch (sourceKind) {
            case PROVIDER_SNAPSHOT -> EventSourceTrace.providerSnapshot(
                    Objects.requireNonNull(snapshotId, "snapshotId"),
                    resultSet.getString("source_payload_sha256"),
                    resultSet.getString("parser_version"),
                    receivedAt.toInstant());
            case SYNTHETIC_FIXTURE -> EventSourceTrace.syntheticFixture(
                    Objects.requireNonNull(fixtureId, "fixtureId"),
                    resultSet.getString("source_payload_sha256"),
                    resultSet.getString("parser_version"),
                    receivedAt.toInstant());
        };
        J5CompletenessReport completeness = new J5CompletenessReport(
                J5CompletenessStatus.valueOf(resultSet.getString("completeness_status")),
                resultSet.getInt("completeness_score"),
                resultSet.getInt("present_signals"),
                resultSet.getInt("expected_signals"),
                readMissingPaths(resultSet.getString("missing_paths_json")));
        Boolean confirmed = resultSet.getObject("lineups_confirmed", Boolean.class);
        return new StoredParent(
                observationId,
                new CanonicalEventIdentity(
                        canonicalEventId,
                        resultSet.getString("provider"),
                        providerEventId),
                endpointType,
                source,
                completeness,
                confirmed,
                resultSet.getString("normalized_sha256"));
    }

    private J5EventDataObservationView toView(StoredParent parent) {
        J5EventData data = switch (parent.endpointType()) {
            case EVENT_STATISTICS -> readStatistics(parent);
            case EVENT_INCIDENTS -> readIncidents(parent);
            case EVENT_LINEUPS -> readLineups(parent);
            default -> throw new IllegalStateException("unsupported persisted J5 endpoint");
        };
        return new J5EventDataObservationView(
                parent.observationId(),
                parent.identity(),
                data,
                parent.source(),
                parent.completeness(),
                parent.normalizedSha256());
    }

    private EventStatistics readStatistics(StoredParent parent) {
        List<EventStatisticMetric> metrics = jdbcTemplate.query(
                FIND_METRICS_SQL,
                new MapSqlParameterSource("observationId", parent.observationId()),
                (resultSet, rowNumber) -> new EventStatisticMetric(
                        resultSet.getString("period"),
                        resultSet.getString("group_name"),
                        resultSet.getString("metric_code"),
                        resultSet.getString("metric_name"),
                        Optional.ofNullable(resultSet.getString("home_value")),
                        Optional.ofNullable(resultSet.getString("away_value"))));
        return new EventStatistics(parent.identity().providerEventId(), metrics);
    }

    private EventIncidents readIncidents(StoredParent parent) {
        List<EventIncident> incidents = jdbcTemplate.query(
                FIND_INCIDENTS_SQL,
                new MapSqlParameterSource("observationId", parent.observationId()),
                (resultSet, rowNumber) -> new EventIncident(
                        resultSet.getInt("incident_order"),
                        resultSet.getString("incident_type"),
                        resultSet.getInt("minute"),
                        optionalInteger(resultSet, "added_time"),
                        Optional.ofNullable(resultSet.getObject("is_home", Boolean.class)),
                        optionalLong(resultSet, "participant_provider_id"),
                        optionalLong(resultSet, "player_provider_id"),
                        Optional.ofNullable(resultSet.getString("player_name")),
                        optionalInteger(resultSet, "home_score"),
                        optionalInteger(resultSet, "away_score")));
        return new EventIncidents(parent.identity().providerEventId(), incidents);
    }

    private EventLineups readLineups(StoredParent parent) {
        Map<LineupSide, Optional<String>> formations = new EnumMap<>(LineupSide.class);
        jdbcTemplate.query(
                FIND_LINEUP_SIDES_SQL,
                new MapSqlParameterSource("observationId", parent.observationId()),
                (RowCallbackHandler) resultSet -> formations.put(
                        LineupSide.valueOf(resultSet.getString("side")),
                        Optional.ofNullable(resultSet.getString("formation"))));
        Map<LineupSide, List<EventLineupPlayer>> players = new EnumMap<>(LineupSide.class);
        players.put(LineupSide.HOME, new ArrayList<>());
        players.put(LineupSide.AWAY, new ArrayList<>());
        jdbcTemplate.query(
                FIND_LINEUP_PLAYERS_SQL,
                new MapSqlParameterSource("observationId", parent.observationId()),
                (RowCallbackHandler) resultSet -> players.get(
                                LineupSide.valueOf(resultSet.getString("side")))
                        .add(new EventLineupPlayer(
                                resultSet.getLong("player_provider_id"),
                                resultSet.getString("player_name"),
                                optionalInteger(resultSet, "shirt_number"),
                                Optional.ofNullable(resultSet.getString("position")),
                                resultSet.getBoolean("starter"))));
        TeamLineup home = new TeamLineup(
                LineupSide.HOME,
                requireFormationSlot(formations, LineupSide.HOME),
                players.get(LineupSide.HOME));
        TeamLineup away = new TeamLineup(
                LineupSide.AWAY,
                requireFormationSlot(formations, LineupSide.AWAY),
                players.get(LineupSide.AWAY));
        return new EventLineups(
                parent.identity().providerEventId(),
                Objects.requireNonNull(parent.lineupsConfirmed(), "lineupsConfirmed"),
                home,
                away);
    }

    private static Optional<String> requireFormationSlot(
            Map<LineupSide, Optional<String>> formations,
            LineupSide side) {
        Optional<String> value = formations.get(side);
        if (value == null) {
            throw new IllegalStateException("persisted lineup side is missing: " + side);
        }
        return value;
    }

    private static Optional<Integer> optionalInteger(ResultSet resultSet, String column)
            throws SQLException {
        return Optional.ofNullable(resultSet.getObject(column, Integer.class));
    }

    private static Optional<Long> optionalLong(ResultSet resultSet, String column)
            throws SQLException {
        return Optional.ofNullable(resultSet.getObject(column, Long.class));
    }

    private static String writeMissingPaths(List<String> missingPaths) {
        try {
            return JSON_MAPPER.writeValueAsString(missingPaths);
        }
        catch (JacksonException exception) {
            throw new IllegalStateException("unable to serialize J5 completeness paths", exception);
        }
    }

    private static List<String> readMissingPaths(String value) {
        try {
            return List.of(JSON_MAPPER.readValue(value, String[].class));
        }
        catch (JacksonException exception) {
            throw new IllegalStateException("invalid persisted J5 completeness paths", exception);
        }
    }

    private record StoredParent(
            long observationId,
            CanonicalEventIdentity identity,
            SofascoreEndpointType endpointType,
            EventSourceTrace source,
            J5CompletenessReport completeness,
            Boolean lineupsConfirmed,
            String normalizedSha256) {
    }
}
