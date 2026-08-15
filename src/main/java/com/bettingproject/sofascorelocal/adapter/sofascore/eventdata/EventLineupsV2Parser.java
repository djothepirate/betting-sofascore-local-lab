package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Provider-shape lineups parser, including the valid unconfirmed empty shape. */
public final class EventLineupsV2Parser {

    public static final String PARSER_VERSION = "event-lineups-v2";
    private static final Set<String> ROOT_FIELDS = Set.of("confirmed", "home", "away");
    private static final Set<String> SIDE_FIELDS = Set.of(
            "formation", "players", "supportStaff", "playerColor", "goalkeeperColor",
            "missingPlayers");
    private static final Set<String> ENTRY_FIELDS = Set.of(
            "player", "shirtNumber", "jerseyNumber", "position", "substitute",
            "statistics", "captain");
    private static final Set<String> PLAYER_FIELDS = Set.of(
            "id", "name", "slug", "shortName", "position", "userCount",
            "height", "country", "preferredFoot", "dateOfBirth", "gender");

    public J5ParseResult<EventLineups> parse(
            long snapshotId,
            long expectedEventId,
            RawPayloadEvidence payload,
            Instant receivedAt) {
        J5EventDataProviderRequest.requireEventId(expectedEventId);
        J5ParseEvidence evidence = evidence(snapshotId, payload, receivedAt);
        JsonNode root;
        try {
            root = J5JsonParserSupport.readTree(payload.bytes());
        }
        catch (JacksonException exception) {
            return failed(J5ParseStatus.UNEXPECTED_CONTENT, evidence,
                    J5ParseProblem.Code.INVALID_JSON, "$",
                    "The payload is not valid unambiguous JSON");
        }
        List<J5ParseWarning> warnings = new ArrayList<>();
        List<J5ParseProblem> problems = new ArrayList<>();
        if (!J5JsonParserSupport.requiredObject(root, "$", problems)) {
            return incompatible(evidence, warnings, problems);
        }
        J5JsonParserSupport.warnUnknownFields(root, ROOT_FIELDS, "$", warnings);
        Boolean confirmed = J5JsonParserSupport.requiredBoolean(
                root.get("confirmed"), "$.confirmed", problems);
        boolean homeAbsent = J5JsonParserSupport.isAbsent(root.get("home"));
        boolean awayAbsent = J5JsonParserSupport.isAbsent(root.get("away"));
        if (Boolean.FALSE.equals(confirmed) && homeAbsent && awayAbsent && problems.isEmpty()) {
            TeamLineup home = new TeamLineup(LineupSide.HOME, Optional.empty(), List.of());
            TeamLineup away = new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of());
            return J5ParseResult.parsed(
                    evidence,
                    new EventLineups(expectedEventId, false, home, away),
                    J5CompletenessReport.emptyValid(),
                    warnings);
        }

        Completeness completeness = new Completeness();
        completeness.expect("$.confirmed=true", Boolean.TRUE.equals(confirmed));
        TeamLineup home = parseSide(root.get("home"), "$.home", LineupSide.HOME,
                warnings, problems, completeness);
        TeamLineup away = parseSide(root.get("away"), "$.away", LineupSide.AWAY,
                warnings, problems, completeness);
        if (!problems.isEmpty() || confirmed == null || home == null || away == null) {
            return incompatible(evidence, warnings, problems);
        }
        return J5ParseResult.parsed(
                evidence,
                new EventLineups(expectedEventId, confirmed, home, away),
                completeness.report(),
                warnings);
    }

    private static TeamLineup parseSide(
            JsonNode node,
            String path,
            LineupSide side,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems,
            Completeness completeness) {
        if (!J5JsonParserSupport.requiredObject(node, path, problems)) {
            return null;
        }
        J5JsonParserSupport.warnUnknownFields(node, SIDE_FIELDS, path, warnings);
        Optional<String> formation = J5JsonParserSupport.optionalText(
                node.get("formation"), path + ".formation", 32, warnings, problems);
        completeness.expect(path + ".formation", formation.isPresent());
        JsonNode playersNode = node.get("players");
        if (!J5JsonParserSupport.requiredArray(playersNode, path + ".players", problems)) {
            return null;
        }
        completeness.expect(path + ".players", !playersNode.isEmpty());
        List<EventLineupPlayer> players = new ArrayList<>();
        for (int i = 0; i < playersNode.size(); i++) {
            EventLineupPlayer player = parsePlayer(
                    playersNode.get(i), path + ".players[" + i + "]",
                    warnings, problems, completeness);
            if (player != null) {
                players.add(player);
            }
        }
        return new TeamLineup(side, formation, players);
    }

    private static EventLineupPlayer parsePlayer(
            JsonNode entry,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems,
            Completeness completeness) {
        if (!J5JsonParserSupport.requiredObject(entry, path, problems)) {
            return null;
        }
        J5JsonParserSupport.warnUnknownFields(entry, ENTRY_FIELDS, path, warnings);
        JsonNode player = entry.get("player");
        if (!J5JsonParserSupport.requiredObject(player, path + ".player", problems)) {
            return null;
        }
        J5JsonParserSupport.warnUnknownFields(player, PLAYER_FIELDS, path + ".player", warnings);
        Long id = J5JsonParserSupport.requiredPositiveLong(
                player.get("id"), path + ".player.id", problems);
        String name = J5JsonParserSupport.requiredText(
                player.get("name"), path + ".player.name", 200, problems);
        Optional<Integer> shirtNumber = optionalShirtNumber(entry, path, warnings, problems);
        completeness.expect(path + ".jerseyNumber", shirtNumber.isPresent());
        JsonNode positionNode = J5JsonParserSupport.isAbsent(entry.get("position"))
                ? player.get("position")
                : entry.get("position");
        Optional<String> position = J5JsonParserSupport.optionalText(
                positionNode, path + ".position", 32, warnings, problems);
        completeness.expect(path + ".position", position.isPresent());
        Boolean substitute = J5JsonParserSupport.requiredBoolean(
                entry.get("substitute"), path + ".substitute", problems);
        if (id == null || name == null || substitute == null) {
            return null;
        }
        return new EventLineupPlayer(id, name, shirtNumber, position, !substitute);
    }

    private static Optional<Integer> optionalShirtNumber(
            JsonNode entry,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        JsonNode value = !J5JsonParserSupport.isAbsent(entry.get("jerseyNumber"))
                ? entry.get("jerseyNumber")
                : entry.get("shirtNumber");
        if (J5JsonParserSupport.isAbsent(value)) {
            return J5JsonParserSupport.optionalInteger(
                    null, path + ".jerseyNumber", 1, 999, warnings, problems);
        }
        if (value.isIntegralNumber() && value.canConvertToInt()) {
            return J5JsonParserSupport.optionalInteger(
                    value, path + ".jerseyNumber", 1, 999, warnings, problems);
        }
        if (value.isString()) {
            String text = value.stringValue().trim();
            if (text.matches("[1-9][0-9]{0,2}")) {
                int number = Integer.parseInt(text);
                if (number <= 999) {
                    return Optional.of(number);
                }
            }
        }
        problems.add(J5JsonParserSupport.problem(
                J5ParseProblem.Code.TYPE_MISMATCH,
                path + ".jerseyNumber",
                "Jersey number must be an integer or bounded numeric text"));
        return Optional.empty();
    }

    private static J5ParseEvidence evidence(
            long snapshotId, RawPayloadEvidence payload, Instant receivedAt) {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        return new J5ParseEvidence(
                "snapshot:" + snapshotId,
                SofascoreEndpointType.EVENT_LINEUPS,
                Objects.requireNonNull(payload, "payload").sha256(),
                Optional.empty(),
                Objects.requireNonNull(receivedAt, "receivedAt"),
                PARSER_VERSION);
    }

    private static J5ParseResult<EventLineups> incompatible(
            J5ParseEvidence evidence,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return J5ParseResult.failed(
                J5ParseStatus.SCHEMA_INCOMPATIBLE, evidence, warnings, problems);
    }

    private static J5ParseResult<EventLineups> failed(
            J5ParseStatus status,
            J5ParseEvidence evidence,
            J5ParseProblem.Code code,
            String path,
            String message) {
        return J5ParseResult.failed(status, evidence, List.of(),
                List.of(J5JsonParserSupport.problem(code, path, message)));
    }

    private static final class Completeness {
        private int expected;
        private int present;
        private final List<String> missing = new ArrayList<>();

        void expect(String path, boolean available) {
            expected++;
            if (available) {
                present++;
            }
            else {
                missing.add(path);
            }
        }

        J5CompletenessReport report() {
            return J5CompletenessReport.measured(present, expected, missing);
        }
    }
}
