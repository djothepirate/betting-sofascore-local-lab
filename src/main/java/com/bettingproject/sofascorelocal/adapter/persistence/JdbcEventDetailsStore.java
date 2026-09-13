package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventPerson;
import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventSeason;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventVenue;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcEventDetailsStore implements EventDetailsStore {

    private static final String INSERT_SQL = """
            insert into event_detail_observation (
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
                venue_provider_id,
                venue_name,
                venue_city,
                season_provider_id,
                season_name,
                event_round,
                is_awarded,
                home_display_score,
                away_display_score,
                home_manager_name, home_manager_country_name, home_manager_country_alpha2,
                away_manager_name, away_manager_country_name, away_manager_country_alpha2,
                referee_name, referee_country_name, referee_country_alpha2,
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
                :venueProviderId,
                :venueName,
                :venueCity,
                :seasonProviderId,
                :seasonName,
                :eventRound,
                :isAwarded,
                :homeDisplayScore,
                :awayDisplayScore,
                :homeManagerName, :homeManagerCountryName, :homeManagerCountryAlpha2,
                :awayManagerName, :awayManagerCountryName, :awayManagerCountryAlpha2,
                :refereeName, :refereeCountryName, :refereeCountryAlpha2,
                :normalizedSha256
            )
            on conflict (
                canonical_event_id,
                source_kind,
                source_reference,
                normalized_sha256
            )
            do nothing
            """;

    private static final String SELECT_ID_SQL = """
            select id
            from event_detail_observation
            where canonical_event_id = :canonicalEventId
              and source_kind = :sourceKind
              and source_reference = :sourceReference
              and normalized_sha256 = :normalizedSha256
            """;

    private static final String VIEW_COLUMNS = """
                d.id as observation_id,
                e.id as canonical_event_id,
                e.provider,
                e.provider_event_id,
                d.source_kind,
                d.source_reference,
                d.source_snapshot_id,
                d.source_fixture_id,
                d.source_payload_sha256,
                d.parser_version,
                d.source_received_at,
                d.starts_at,
                d.home_team_provider_id,
                d.home_team_name,
                d.away_team_provider_id,
                d.away_team_name,
                d.status_type,
                d.status_description,
                d.tournament_provider_id,
                d.tournament_name,
                d.venue_provider_id,
                d.venue_name,
                d.venue_city,
                d.season_provider_id,
                d.season_name,
                d.event_round,
                d.is_awarded,
                d.home_display_score,
                d.away_display_score,
                d.home_manager_name, d.home_manager_country_name, d.home_manager_country_alpha2,
                d.away_manager_name, d.away_manager_country_name, d.away_manager_country_alpha2,
                d.referee_name, d.referee_country_name, d.referee_country_alpha2,
                d.normalized_sha256
            """;

    private static final String FIND_LATEST_SQL = """
            select
                """ + VIEW_COLUMNS + """
            from event_detail_observation d
            join canonical_event e on e.id = d.canonical_event_id
            where d.canonical_event_id = :canonicalEventId
            order by d.source_received_at desc, d.id desc
            limit 1
            """;

    private static final String FIND_HISTORY_SQL = """
            select
                """ + VIEW_COLUMNS + """
            from event_detail_observation d
            join canonical_event e on e.id = d.canonical_event_id
            where d.canonical_event_id = :canonicalEventId
            order by d.source_received_at desc, d.id desc
            """;

    private static final String FIND_BY_OBSERVATION_ID_SQL = """
            select
                """ + VIEW_COLUMNS + """
            from event_detail_observation d
            join canonical_event e on e.id = d.canonical_event_id
            where d.canonical_event_id = :canonicalEventId
              and d.id = :observationId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcEventDetailsStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional
    public EventDetailPersistenceResult save(EventDetailObservation observation) {
        Objects.requireNonNull(observation, "observation");
        MapSqlParameterSource parameters = parameters(observation);
        int insertedRows = jdbcTemplate.update(INSERT_SQL, parameters);
        if (insertedRows < 0 || insertedRows > 1) {
            throw new IllegalStateException("event detail insert affected unexpected rows");
        }
        Long observationId = jdbcTemplate.queryForObject(SELECT_ID_SQL, parameters, Long.class);
        if (observationId == null) {
            throw new IllegalStateException("stored event detail could not be resolved");
        }
        return new EventDetailPersistenceResult(
                observationId,
                observation.identity().value(),
                insertedRows == 1);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventDetailObservationView> findLatest(UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        List<EventDetailObservationView> matches = jdbcTemplate.query(
                FIND_LATEST_SQL,
                new MapSqlParameterSource("canonicalEventId", canonicalEventId),
                this::mapView);
        return matches.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventDetailObservationView> findHistory(UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        return jdbcTemplate.query(
                FIND_HISTORY_SQL,
                new MapSqlParameterSource("canonicalEventId", canonicalEventId),
                this::mapView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventDetailObservationView> findByObservationId(
            UUID canonicalEventId,
            long observationId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        if (observationId < 1) {
            throw new IllegalArgumentException("observationId must be positive");
        }
        List<EventDetailObservationView> matches = jdbcTemplate.query(
                FIND_BY_OBSERVATION_ID_SQL,
                new MapSqlParameterSource()
                        .addValue("canonicalEventId", canonicalEventId)
                        .addValue("observationId", observationId),
                this::mapView);
        return matches.stream().findFirst();
    }

    private static MapSqlParameterSource parameters(EventDetailObservation observation) {
        EventDetails details = observation.details();
        ScheduledTournament tournament = details.tournament().orElse(null);
        EventVenue venue = details.venue().orElse(null);
        EventSeason season = details.season().orElse(null);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("canonicalEventId", observation.identity().value())
                .addValue("sourceKind", observation.source().kind().name())
                .addValue("sourceReference", observation.source().sourceReference())
                .addValue(
                        "sourceSnapshotId",
                        observation.source().snapshotId().isPresent()
                                ? observation.source().snapshotId().getAsLong()
                                : null,
                        Types.BIGINT)
                .addValue(
                        "sourceFixtureId",
                        observation.source().fixtureId().orElse(null),
                        Types.VARCHAR)
                .addValue("sourcePayloadSha256", observation.source().payloadSha256())
                .addValue("parserVersion", observation.source().parserVersion())
                .addValue(
                        "sourceReceivedAt",
                        observation.source().receivedAt().atOffset(ZoneOffset.UTC))
                .addValue("startsAt", details.startsAt().atOffset(ZoneOffset.UTC))
                .addValue("homeTeamProviderId", details.homeTeam().providerTeamId())
                .addValue("homeTeamName", details.homeTeam().name())
                .addValue("awayTeamProviderId", details.awayTeam().providerTeamId())
                .addValue("awayTeamName", details.awayTeam().name())
                .addValue("statusType", details.status().type())
                .addValue(
                        "statusDescription",
                        details.status().description().orElse(null),
                        Types.VARCHAR)
                .addValue(
                        "tournamentProviderId",
                        tournament == null ? null : tournament.providerTournamentId(),
                        Types.BIGINT)
                .addValue(
                        "tournamentName",
                        tournament == null ? null : tournament.name(),
                        Types.VARCHAR)
                .addValue(
                        "venueProviderId",
                        venue == null ? null : venue.providerVenueId(),
                        Types.BIGINT)
                .addValue("venueName", venue == null ? null : venue.name(), Types.VARCHAR)
                .addValue(
                        "venueCity",
                        venue == null ? null : venue.city().orElse(null),
                        Types.VARCHAR)
                .addValue(
                        "seasonProviderId",
                        season == null ? null : season.providerSeasonId(),
                        Types.BIGINT)
                .addValue("seasonName", season == null ? null : season.name(), Types.VARCHAR)
                .addValue("eventRound", details.round().orElse(null), Types.VARCHAR)
                .addValue("isAwarded", details.isAwarded().orElse(null), Types.BOOLEAN)
                .addValue("homeDisplayScore", details.homeDisplayScore().orElse(null), Types.INTEGER)
                .addValue("awayDisplayScore", details.awayDisplayScore().orElse(null), Types.INTEGER)
                .addValue("normalizedSha256", observation.normalizedSha256());
        addPerson(parameters, "homeManager", details.homeManager());
        addPerson(parameters, "awayManager", details.awayManager());
        addPerson(parameters, "referee", details.referee());
        return parameters;
    }

    private static void addPerson(MapSqlParameterSource parameters, String prefix, Optional<EventPerson> person) {
        parameters.addValue(prefix + "Name", person.map(EventPerson::name).orElse(null), Types.VARCHAR)
                .addValue(prefix + "CountryName", person.flatMap(EventPerson::country).flatMap(ProviderCountry::name).orElse(null), Types.VARCHAR)
                .addValue(prefix + "CountryAlpha2", person.flatMap(EventPerson::country).flatMap(ProviderCountry::alpha2).orElse(null), Types.VARCHAR);
    }

    private static Optional<EventPerson> readPerson(ResultSet result, String prefix) throws SQLException {
        String name = result.getString(prefix + "_name");
        String countryName = result.getString(prefix + "_country_name"), code = result.getString(prefix + "_country_alpha2");
        Optional<ProviderCountry> country = countryName == null && code == null ? Optional.empty()
                : Optional.of(new ProviderCountry(Optional.ofNullable(countryName), Optional.ofNullable(code)));
        return name == null ? Optional.empty() : Optional.of(new EventPerson(name, country));
    }

    private EventDetailObservationView mapView(ResultSet resultSet, int rowNumber)
            throws SQLException {
        UUID canonicalId = resultSet.getObject("canonical_event_id", UUID.class);
        long providerEventId = resultSet.getLong("provider_event_id");
        OffsetDateTime startsAt = resultSet.getObject("starts_at", OffsetDateTime.class);
        OffsetDateTime receivedAt = resultSet.getObject(
                "source_received_at",
                OffsetDateTime.class);
        Long tournamentId = resultSet.getObject("tournament_provider_id", Long.class);
        Long venueId = resultSet.getObject("venue_provider_id", Long.class);
        Long seasonId = resultSet.getObject("season_provider_id", Long.class);
        if (canonicalId == null || startsAt == null || receivedAt == null) {
            throw new IllegalStateException("event detail persistence metadata is incomplete");
        }
        CanonicalEventIdentity identity = new CanonicalEventIdentity(
                canonicalId,
                resultSet.getString("provider"),
                providerEventId);
        EventDetails details = new EventDetails(
                providerEventId,
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
                        : Optional.of(new ScheduledTournament(
                                tournamentId,
                                resultSet.getString("tournament_name"))),
                venueId == null
                        ? Optional.empty()
                        : Optional.of(new EventVenue(
                                venueId,
                                resultSet.getString("venue_name"),
                                Optional.ofNullable(resultSet.getString("venue_city")))),
                seasonId == null
                        ? Optional.empty()
                        : Optional.of(new EventSeason(
                                seasonId,
                                resultSet.getString("season_name"))),
                Optional.ofNullable(resultSet.getString("event_round")),
                Optional.ofNullable(resultSet.getObject("is_awarded", Boolean.class)),
                Optional.ofNullable(resultSet.getObject("home_display_score", Integer.class)),
                Optional.ofNullable(resultSet.getObject("away_display_score", Integer.class)),
                readPerson(resultSet, "home_manager"), readPerson(resultSet, "away_manager"), readPerson(resultSet, "referee"));
        EventSourceKind sourceKind = EventSourceKind.valueOf(
                resultSet.getString("source_kind"));
        EventSourceTrace source = switch (sourceKind) {
            case PROVIDER_SNAPSHOT -> EventSourceTrace.providerSnapshot(
                    resultSet.getLong("source_snapshot_id"),
                    resultSet.getString("source_payload_sha256"),
                    resultSet.getString("parser_version"),
                    receivedAt.toInstant());
            case SYNTHETIC_FIXTURE -> EventSourceTrace.syntheticFixture(
                    resultSet.getString("source_fixture_id"),
                    resultSet.getString("source_payload_sha256"),
                    resultSet.getString("parser_version"),
                    receivedAt.toInstant());
        };
        return new EventDetailObservationView(
                resultSet.getLong("observation_id"),
                identity,
                details,
                source,
                resultSet.getString("normalized_sha256"));
    }
}
