package com.bettingproject.sofascorelocal.application.history;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.MissingLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.PlayerMatchStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventSeason;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventVenue;
import com.bettingproject.sofascorelocal.domain.history.J6ChangeKind;
import com.bettingproject.sofascorelocal.domain.history.J6Score;
import com.bettingproject.sofascorelocal.domain.history.J6SemanticChange;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class J6SemanticDiffService {

    public List<J6SemanticChange> compareState(
            CanonicalEventObservationView before,
            CanonicalEventObservationView after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        requireSameEvent(before.identity().value(), after.identity().value());
        List<J6SemanticChange> changes = new ArrayList<>();
        scalar(changes, "startsAt", before.startsAt(), after.startsAt());
        scalar(changes, "homeTeam.providerId",
                before.homeTeam().providerTeamId(), after.homeTeam().providerTeamId());
        scalar(changes, "homeTeam.name", before.homeTeam().name(), after.homeTeam().name());
        scalar(changes, "awayTeam.providerId",
                before.awayTeam().providerTeamId(), after.awayTeam().providerTeamId());
        scalar(changes, "awayTeam.name", before.awayTeam().name(), after.awayTeam().name());
        scalar(changes, "status.type", before.status().type(), after.status().type());
        scalar(changes, "status.description",
                before.status().description().orElse(null),
                after.status().description().orElse(null));
        scalar(changes, "tournament.providerId",
                before.tournament().map(value -> value.providerTournamentId()).orElse(null),
                after.tournament().map(value -> value.providerTournamentId()).orElse(null));
        scalar(changes, "tournament.name",
                before.tournament().map(value -> value.name()).orElse(null),
                after.tournament().map(value -> value.name()).orElse(null));
        return List.copyOf(changes);
    }

    public List<J6SemanticChange> compareDetails(
            EventDetailObservationView before,
            EventDetailObservationView after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        requireSameEvent(before.identity().value(), after.identity().value());
        List<J6SemanticChange> changes = new ArrayList<>();
        EventVenue beforeVenue = before.details().venue().orElse(null);
        EventVenue afterVenue = after.details().venue().orElse(null);
        scalar(changes, "venue.providerId",
                beforeVenue == null ? null : beforeVenue.providerVenueId(),
                afterVenue == null ? null : afterVenue.providerVenueId());
        scalar(changes, "venue.name",
                beforeVenue == null ? null : beforeVenue.name(),
                afterVenue == null ? null : afterVenue.name());
        scalar(changes, "venue.city",
                beforeVenue == null ? null : beforeVenue.city().orElse(null),
                afterVenue == null ? null : afterVenue.city().orElse(null));
        EventSeason beforeSeason = before.details().season().orElse(null);
        EventSeason afterSeason = after.details().season().orElse(null);
        scalar(changes, "season.providerId",
                beforeSeason == null ? null : beforeSeason.providerSeasonId(),
                afterSeason == null ? null : afterSeason.providerSeasonId());
        scalar(changes, "season.name",
                beforeSeason == null ? null : beforeSeason.name(),
                afterSeason == null ? null : afterSeason.name());
        scalar(changes, "round",
                before.details().round().orElse(null),
                after.details().round().orElse(null));
        scalar(changes, "isAwarded",
                before.details().isAwarded().orElse(null),
                after.details().isAwarded().orElse(null));
        scalar(changes, "homeScore.display",
                before.details().homeDisplayScore().orElse(null),
                after.details().homeDisplayScore().orElse(null));
        scalar(changes, "awayScore.display",
                before.details().awayDisplayScore().orElse(null),
                after.details().awayDisplayScore().orElse(null));
        return List.copyOf(changes);
    }

    public List<J6SemanticChange> compareEventData(
            J5EventDataObservationView before,
            J5EventDataObservationView after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        requireSameEvent(before.identity().value(), after.identity().value());
        if (before.data().endpointType() != after.data().endpointType()) {
            throw new IllegalArgumentException("J5 observations must belong to the same stream");
        }
        List<J6SemanticChange> changes = new ArrayList<>();
        switch (before.data()) {
            case EventStatistics statistics -> compareStatistics(
                    changes,
                    statistics,
                    (EventStatistics) after.data());
            case EventIncidents incidents -> compareIncidents(
                    changes,
                    incidents,
                    (EventIncidents) after.data());
            case EventLineups lineups -> compareLineups(
                    changes,
                    lineups,
                    (EventLineups) after.data());
        }
        compareCompleteness(changes, before.completeness(), after.completeness());
        return List.copyOf(changes);
    }

    public Optional<J6Score> score(J5EventDataObservationView observation) {
        Objects.requireNonNull(observation, "observation");
        if (!(observation.data() instanceof EventIncidents incidents)) {
            return Optional.empty();
        }
        return score(incidents);
    }

    public Optional<J6Score> score(EventIncidents incidents) {
        Objects.requireNonNull(incidents, "incidents");
        List<EventIncident> values = incidents.incidents();
        for (int index = values.size() - 1; index >= 0; index--) {
            EventIncident incident = values.get(index);
            if (incident.homeScore().isPresent() && incident.awayScore().isPresent()) {
                return Optional.of(new J6Score(
                        incident.homeScore().orElseThrow(),
                        incident.awayScore().orElseThrow()));
            }
        }
        return Optional.empty();
    }

    private static void compareStatistics(
            List<J6SemanticChange> changes,
            EventStatistics before,
            EventStatistics after) {
        Map<StatisticKey, List<EventStatisticMetric>> beforeByKey = groupBy(
                before.metrics(), StatisticKey::from);
        Map<StatisticKey, List<EventStatisticMetric>> afterByKey = groupBy(
                after.metrics(), StatisticKey::from);
        Set<StatisticKey> keys = orderedUnion(
                beforeByKey.keySet(),
                afterByKey.keySet(),
                Comparator.comparing(StatisticKey::label));
        for (StatisticKey key : keys) {
            List<EventStatisticMetric> oldValues = mutable(beforeByKey.get(key));
            List<EventStatisticMetric> newValues = mutable(afterByKey.get(key));
            removeExactMatches(oldValues, newValues);
            String path = "statistics[" + key.label() + "]";
            if (oldValues.size() == 1 && newValues.size() == 1) {
                EventStatisticMetric oldValue = oldValues.getFirst();
                EventStatisticMetric newValue = newValues.getFirst();
                scalar(changes, path + ".label", oldValue.metricName(), newValue.metricName());
                scalar(changes, path + ".home",
                        oldValue.homeValue().orElse(null), newValue.homeValue().orElse(null));
                scalar(changes, path + ".away",
                        oldValue.awayValue().orElse(null), newValue.awayValue().orElse(null));
                continue;
            }
            removeEntities(changes, path, oldValues, J6SemanticDiffService::statisticSummary);
            addEntities(changes, path, newValues, J6SemanticDiffService::statisticSummary);
        }
    }

    private static void compareLineups(
            List<J6SemanticChange> changes,
            EventLineups before,
            EventLineups after) {
        scalar(changes, "lineups.confirmed", before.confirmed(), after.confirmed());
        compareLineupSide(changes, before.home(), after.home());
        compareLineupSide(changes, before.away(), after.away());
    }

    private static void compareLineupSide(
            List<J6SemanticChange> changes,
            TeamLineup before,
            TeamLineup after) {
        if (before.side() != after.side()) {
            throw new IllegalArgumentException("lineup sides must match");
        }
        String side = before.side().name();
        scalar(changes, "lineups[" + side + "].formation",
                before.formation().orElse(null), after.formation().orElse(null));
        compareMissingPlayers(changes, side, before.missingPlayers(), after.missingPlayers());
        Map<Long, List<EventLineupPlayer>> beforeById = groupBy(
                before.players(), EventLineupPlayer::providerPlayerId);
        Map<Long, List<EventLineupPlayer>> afterById = groupBy(
                after.players(), EventLineupPlayer::providerPlayerId);
        Set<Long> ids = orderedUnion(beforeById.keySet(), afterById.keySet(), Long::compareTo);
        for (Long id : ids) {
            List<EventLineupPlayer> oldValues = mutable(beforeById.get(id));
            List<EventLineupPlayer> newValues = mutable(afterById.get(id));
            removeExactMatches(oldValues, newValues);
            String path = "lineups[" + side + ",playerId=" + id + "]";
            if (oldValues.size() == 1 && newValues.size() == 1) {
                EventLineupPlayer oldValue = oldValues.getFirst();
                EventLineupPlayer newValue = newValues.getFirst();
                scalar(changes, path + ".name", oldValue.name(), newValue.name());
                scalar(changes, path + ".number",
                        oldValue.shirtNumber().orElse(null),
                        newValue.shirtNumber().orElse(null));
                scalar(changes, path + ".position",
                        oldValue.position().orElse(null), newValue.position().orElse(null));
                scalar(changes, path + ".starter", oldValue.starter(), newValue.starter());
                scalar(changes, path + ".captain",
                        oldValue.captain().orElse(null), newValue.captain().orElse(null));
                comparePlayerStatistics(changes, path + ".statistics",
                        oldValue.statistics(), newValue.statistics());
                continue;
            }
            removeEntities(changes, path, oldValues, J6SemanticDiffService::lineupSummary);
            addEntities(changes, path, newValues, J6SemanticDiffService::lineupSummary);
        }
    }

    private static void comparePlayerStatistics(
            List<J6SemanticChange> changes, String path,
            Optional<PlayerMatchStatistics> before, Optional<PlayerMatchStatistics> after) {
        scalar(changes, path + ".present",
                before.isPresent() ? true : null, after.isPresent() ? true : null);
        compareNumericValues(changes, path,
                before.map(PlayerMatchStatistics::values).orElse(Map.of()),
                after.map(PlayerMatchStatistics::values).orElse(Map.of()));
        compareNumericValues(changes, path + ".ratingVersions",
                before.map(PlayerMatchStatistics::ratingVersions).orElse(Map.of()),
                after.map(PlayerMatchStatistics::ratingVersions).orElse(Map.of()));
    }

    private static void compareNumericValues(
            List<J6SemanticChange> changes, String path,
            Map<String, java.math.BigDecimal> before, Map<String, java.math.BigDecimal> after) {
        for (String key : orderedUnion(before.keySet(), after.keySet(), String::compareTo)) {
            scalar(changes, path + "[" + key + "]",
                    before.containsKey(key) ? before.get(key).toPlainString() : null,
                    after.containsKey(key) ? after.get(key).toPlainString() : null);
        }
    }

    private static void compareMissingPlayers(
            List<J6SemanticChange> changes, String side,
            Optional<List<MissingLineupPlayer>> before, Optional<List<MissingLineupPlayer>> after) {
        String path = "lineups[" + side + "].missingPlayers";
        scalar(changes, path + ".present",
                before.isPresent() ? true : null, after.isPresent() ? true : null);
        Map<Long, List<MissingLineupPlayer>> beforeById = groupBy(
                before.orElse(List.of()), MissingLineupPlayer::providerPlayerId);
        Map<Long, List<MissingLineupPlayer>> afterById = groupBy(
                after.orElse(List.of()), MissingLineupPlayer::providerPlayerId);
        for (Long id : orderedUnion(beforeById.keySet(), afterById.keySet(), Long::compareTo)) {
            List<MissingLineupPlayer> oldValues = mutable(beforeById.get(id));
            List<MissingLineupPlayer> newValues = mutable(afterById.get(id));
            removeExactMatches(oldValues, newValues);
            String playerPath = path + "[playerId=" + id + "]";
            if (oldValues.size() == 1 && newValues.size() == 1) {
                MissingLineupPlayer oldValue = oldValues.getFirst();
                MissingLineupPlayer newValue = newValues.getFirst();
                scalar(changes, playerPath + ".name", oldValue.name(), newValue.name());
                scalar(changes, playerPath + ".number",
                        oldValue.shirtNumber().orElse(null), newValue.shirtNumber().orElse(null));
                scalar(changes, playerPath + ".position",
                        oldValue.position().orElse(null), newValue.position().orElse(null));
                scalar(changes, playerPath + ".type",
                        oldValue.type().orElse(null), newValue.type().orElse(null));
                scalar(changes, playerPath + ".reason",
                        oldValue.reason().orElse(null), newValue.reason().orElse(null));
                scalar(changes, playerPath + ".description",
                        oldValue.description().orElse(null), newValue.description().orElse(null));
                scalar(changes, playerPath + ".externalType",
                        oldValue.externalType().orElse(null), newValue.externalType().orElse(null));
                scalar(changes, playerPath + ".expectedEndDate",
                        oldValue.expectedEndDate().orElse(null), newValue.expectedEndDate().orElse(null));
            } else {
                removeEntities(changes, playerPath, oldValues, J6SemanticDiffService::missingPlayerSummary);
                addEntities(changes, playerPath, newValues, J6SemanticDiffService::missingPlayerSummary);
            }
        }
    }

    private static String missingPlayerSummary(MissingLineupPlayer player) {
        return player.name()
                + " · number=" + player.shirtNumber().map(Object::toString).orElse("absent")
                + " · position=" + player.position().orElse("absent")
                + " · type=" + player.type().orElse("absent")
                + " · reason=" + player.reason().map(Object::toString).orElse("absent")
                + " · description=" + player.description().orElse("absent")
                + " · externalType=" + player.externalType().map(Object::toString).orElse("absent")
                + " · expectedEndDate=" + player.expectedEndDate().map(Object::toString).orElse("absent");
    }

    private static void compareIncidents(
            List<J6SemanticChange> changes,
            EventIncidents before,
            EventIncidents after) {
        List<PositionedIncident> oldValues = positioned(before.incidents());
        List<PositionedIncident> newValues = positioned(after.incidents());
        List<IncidentPair> pairs = new ArrayList<>();

        pairExactIncidents(oldValues, newValues);
        pairUnique(
                oldValues,
                newValues,
                incident -> incident.incident().shootoutSequence()
                        .map(value -> "shootout:" + value)
                        .orElse(null),
                pairs);
        pairUnique(
                oldValues,
                newValues,
                incident -> providerIncidentKey(incident.incident()),
                pairs);
        pairUnique(
                oldValues,
                newValues,
                incident -> positionalIncidentKey(incident.incident()),
                pairs);

        pairs.sort(Comparator.comparingInt(pair -> pair.after().position()));
        for (IncidentPair pair : pairs) {
            compareIncident(changes, pair.before(), pair.after());
        }
        oldValues.sort(Comparator.comparingInt(PositionedIncident::position));
        newValues.sort(Comparator.comparingInt(PositionedIncident::position));
        removeEntities(changes, "incidents", oldValues,
                value -> incidentSummary(value.incident()));
        addEntities(changes, "incidents", newValues,
                value -> incidentSummary(value.incident()));

        Optional<J6Score> beforeScore = scoreFrom(before);
        Optional<J6Score> afterScore = scoreFrom(after);
        scalar(changes, "score.home",
                beforeScore.map(J6Score::home).orElse(null),
                afterScore.map(J6Score::home).orElse(null));
        scalar(changes, "score.away",
                beforeScore.map(J6Score::away).orElse(null),
                afterScore.map(J6Score::away).orElse(null));
    }

    private static void compareIncident(
            List<J6SemanticChange> changes,
            PositionedIncident before,
            PositionedIncident after) {
        EventIncident oldValue = before.incident();
        EventIncident newValue = after.incident();
        String path = "incidents[before=" + oldValue.sequence()
                + ",after=" + newValue.sequence() + "]";
        scalar(changes, path + ".order", oldValue.sequence(), newValue.sequence());
        scalar(changes, path + ".type", oldValue.incidentType(), newValue.incidentType());
        scalar(changes, path + ".minute",
                oldValue.minute().orElse(null), newValue.minute().orElse(null));
        scalar(changes, path + ".addedTime",
                oldValue.addedTime().orElse(null), newValue.addedTime().orElse(null));
        scalar(changes, path + ".side",
                oldValue.home().map(value -> value ? "HOME" : "AWAY").orElse(null),
                newValue.home().map(value -> value ? "HOME" : "AWAY").orElse(null));
        scalar(changes, path + ".participantProviderId",
                oldValue.participantProviderId().orElse(null),
                newValue.participantProviderId().orElse(null));
        scalar(changes, path + ".playerProviderId",
                oldValue.playerProviderId().orElse(null),
                newValue.playerProviderId().orElse(null));
        scalar(changes, path + ".playerName",
                oldValue.playerName().orElse(null), newValue.playerName().orElse(null));
        scalar(changes, path + ".playerInProviderId",
                oldValue.playerInProviderId().orElse(null),
                newValue.playerInProviderId().orElse(null));
        scalar(changes, path + ".playerInName",
                oldValue.playerInName().orElse(null), newValue.playerInName().orElse(null));
        scalar(changes, path + ".playerOutProviderId",
                oldValue.playerOutProviderId().orElse(null),
                newValue.playerOutProviderId().orElse(null));
        scalar(changes, path + ".playerOutName",
                oldValue.playerOutName().orElse(null), newValue.playerOutName().orElse(null));
        scalar(changes, path + ".homeScore",
                oldValue.homeScore().orElse(null), newValue.homeScore().orElse(null));
        scalar(changes, path + ".awayScore",
                oldValue.awayScore().orElse(null), newValue.awayScore().orElse(null));
        scalar(changes, path + ".class",
                oldValue.incidentClass().orElse(null),
                newValue.incidentClass().orElse(null));
        scalar(changes, path + ".reason",
                oldValue.reason().orElse(null), newValue.reason().orElse(null));
        scalar(changes, path + ".period",
                oldValue.periodText().orElse(null), newValue.periodText().orElse(null));
        scalar(changes, path + ".injury",
                oldValue.injury().orElse(null), newValue.injury().orElse(null));
        scalar(changes, path + ".assistProviderId",
                oldValue.assistProviderId().orElse(null),
                newValue.assistProviderId().orElse(null));
        scalar(changes, path + ".assistName",
                oldValue.assistName().orElse(null), newValue.assistName().orElse(null));
        scalar(changes, path + ".goalOrigin",
                oldValue.goalOrigin().orElse(null), newValue.goalOrigin().orElse(null));
        scalar(changes, path + ".injuryTimeLength",
                oldValue.injuryTimeLength().orElse(null),
                newValue.injuryTimeLength().orElse(null));
        scalar(changes, path + ".varConfirmed",
                oldValue.varConfirmed().orElse(null),
                newValue.varConfirmed().orElse(null));
        scalar(changes, path + ".rescinded",
                oldValue.rescinded().orElse(null), newValue.rescinded().orElse(null));
        scalar(changes, path + ".description",
                oldValue.description().orElse(null), newValue.description().orElse(null));
        scalar(changes, path + ".shootoutSequence",
                oldValue.shootoutSequence().orElse(null),
                newValue.shootoutSequence().orElse(null));
    }

    private static void compareCompleteness(
            List<J6SemanticChange> changes,
            J5CompletenessReport before,
            J5CompletenessReport after) {
        scalar(changes, "completeness.status", before.status(), after.status());
        scalar(changes, "completeness.percent", before.scorePercent(), after.scorePercent());
        scalar(changes, "completeness.presentSignals",
                before.presentSignals(), after.presentSignals());
        scalar(changes, "completeness.expectedSignals",
                before.expectedSignals(), after.expectedSignals());
    }

    private static void pairExactIncidents(
            List<PositionedIncident> before,
            List<PositionedIncident> after) {
        for (int beforeIndex = before.size() - 1; beforeIndex >= 0; beforeIndex--) {
            PositionedIncident candidate = before.get(beforeIndex);
            int match = -1;
            for (int afterIndex = 0; afterIndex < after.size(); afterIndex++) {
                if (candidate.incident().equals(after.get(afterIndex).incident())) {
                    match = afterIndex;
                    break;
                }
            }
            if (match >= 0) {
                before.remove(beforeIndex);
                after.remove(match);
            }
        }
    }

    private static void pairUnique(
            List<PositionedIncident> before,
            List<PositionedIncident> after,
            Function<PositionedIncident, String> keyFunction,
            List<IncidentPair> pairs) {
        Map<String, List<PositionedIncident>> beforeByKey = incidentGroups(
                before,
                keyFunction);
        Map<String, List<PositionedIncident>> afterByKey = incidentGroups(
                after,
                keyFunction);
        Set<PositionedIncident> matchedBefore = new LinkedHashSet<>();
        Set<PositionedIncident> matchedAfter = new LinkedHashSet<>();
        for (Map.Entry<String, List<PositionedIncident>> entry : beforeByKey.entrySet()) {
            List<PositionedIncident> candidatesAfter = afterByKey.get(entry.getKey());
            if (entry.getValue().size() == 1
                    && candidatesAfter != null
                    && candidatesAfter.size() == 1) {
                PositionedIncident oldValue = entry.getValue().getFirst();
                PositionedIncident newValue = candidatesAfter.getFirst();
                pairs.add(new IncidentPair(oldValue, newValue));
                matchedBefore.add(oldValue);
                matchedAfter.add(newValue);
            }
        }
        before.removeAll(matchedBefore);
        after.removeAll(matchedAfter);
    }

    private static Map<String, List<PositionedIncident>> incidentGroups(
            List<PositionedIncident> values,
            Function<PositionedIncident, String> keyFunction) {
        Map<String, List<PositionedIncident>> result = new LinkedHashMap<>();
        for (PositionedIncident value : values) {
            String key = keyFunction.apply(value);
            if (key != null) {
                result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
            }
        }
        return result;
    }

    private static String providerIncidentKey(EventIncident incident) {
        if (incident.shootoutSequence().isPresent()) {
            return null;
        }
        List<Long> ids = List.of(
                        incident.participantProviderId(),
                        incident.playerProviderId(),
                        incident.playerInProviderId(),
                        incident.playerOutProviderId())
                .stream()
                .flatMap(Optional::stream)
                .toList();
        if (ids.isEmpty()) {
            return null;
        }
        return incident.incidentType() + "|" + incident.sideLabel() + "|" + ids;
    }

    private static String positionalIncidentKey(EventIncident incident) {
        if (incident.shootoutSequence().isPresent()
                || providerIncidentKey(incident) != null) {
            return null;
        }
        return incident.sequence() + "|" + incident.incidentType() + "|" + incident.sideLabel();
    }

    private static List<PositionedIncident> positioned(List<EventIncident> values) {
        List<PositionedIncident> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(new PositionedIncident(index, values.get(index)));
        }
        return result;
    }

    private static Optional<J6Score> scoreFrom(EventIncidents incidents) {
        for (int index = incidents.incidents().size() - 1; index >= 0; index--) {
            EventIncident incident = incidents.incidents().get(index);
            if (incident.homeScore().isPresent() && incident.awayScore().isPresent()) {
                return Optional.of(new J6Score(
                        incident.homeScore().orElseThrow(),
                        incident.awayScore().orElseThrow()));
            }
        }
        return Optional.empty();
    }

    private static <K, V> Map<K, List<V>> groupBy(
            List<V> values,
            Function<V, K> keyFunction) {
        return values.stream().collect(Collectors.groupingBy(
                keyFunction,
                LinkedHashMap::new,
                Collectors.toList()));
    }

    private static <T> List<T> mutable(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static <T> void removeExactMatches(List<T> before, List<T> after) {
        for (int beforeIndex = before.size() - 1; beforeIndex >= 0; beforeIndex--) {
            int afterIndex = after.indexOf(before.get(beforeIndex));
            if (afterIndex >= 0) {
                before.remove(beforeIndex);
                after.remove(afterIndex);
            }
        }
    }

    private static <T> void removeEntities(
            List<J6SemanticChange> changes,
            String basePath,
            List<T> values,
            Function<T, String> summary) {
        for (int index = 0; index < values.size(); index++) {
            String path = values.size() == 1 ? basePath : basePath + "#" + (index + 1);
            changes.add(new J6SemanticChange(
                    path,
                    Optional.of(summary.apply(values.get(index))),
                    Optional.empty(),
                    J6ChangeKind.REMOVED));
        }
    }

    private static <T> void addEntities(
            List<J6SemanticChange> changes,
            String basePath,
            List<T> values,
            Function<T, String> summary) {
        for (int index = 0; index < values.size(); index++) {
            String path = values.size() == 1 ? basePath : basePath + "#" + (index + 1);
            changes.add(new J6SemanticChange(
                    path,
                    Optional.empty(),
                    Optional.of(summary.apply(values.get(index))),
                    J6ChangeKind.ADDED));
        }
    }

    private static <T> Set<T> orderedUnion(
            Set<T> before,
            Set<T> after,
            Comparator<T> comparator) {
        return java.util.stream.Stream.concat(before.stream(), after.stream())
                .distinct()
                .sorted(comparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void scalar(
            List<J6SemanticChange> changes,
            String field,
            Object before,
            Object after) {
        Optional<String> oldValue = stringValue(before);
        Optional<String> newValue = stringValue(after);
        if (oldValue.equals(newValue)) {
            return;
        }
        J6ChangeKind kind = oldValue.isEmpty()
                ? J6ChangeKind.ADDED
                : newValue.isEmpty() ? J6ChangeKind.REMOVED : J6ChangeKind.CHANGED;
        changes.add(new J6SemanticChange(field, oldValue, newValue, kind));
    }

    private static Optional<String> stringValue(Object value) {
        return Optional.ofNullable(value).map(Object::toString);
    }

    private static String statisticSummary(EventStatisticMetric metric) {
        return metric.metricName()
                + " · HOME=" + metric.homeValue().orElse("absent")
                + " · AWAY=" + metric.awayValue().orElse("absent");
    }

    private static String lineupSummary(EventLineupPlayer player) {
        return player.name()
                + " · number=" + player.shirtNumber().map(Object::toString).orElse("absent")
                + " · position=" + player.position().orElse("absent")
                + " · starter=" + player.starter()
                + player.captain().map(value -> " · captain=" + value).orElse("")
                + player.statistics().map(value -> " · statistics=" + value.values().size()
                        + " metrics, " + value.ratingVersions().size() + " rating versions").orElse("");
    }

    private static String incidentSummary(EventIncident incident) {
        return incident.incidentType()
                + " · minute=" + incident.minuteLabel()
                + " · side=" + incident.sideLabel()
                + " · player=" + incident.playerName().orElse("absent")
                + " · score=" + incident.homeScore()
                        .map(value -> value + "–" + incident.awayScore().orElseThrow())
                        .orElse("absent");
    }

    private static void requireSameEvent(Object before, Object after) {
        if (!Objects.equals(before, after)) {
            throw new IllegalArgumentException("history versions must belong to the same event");
        }
    }

    private record StatisticKey(String period, String group, String code) {

        private static StatisticKey from(EventStatisticMetric metric) {
            return new StatisticKey(metric.period(), metric.groupName(), metric.metricCode());
        }

        private String label() {
            return period + "/" + group + "/" + code;
        }
    }

    private record PositionedIncident(int position, EventIncident incident) {
    }

    private record IncidentPair(PositionedIncident before, PositionedIncident after) {
    }
}
