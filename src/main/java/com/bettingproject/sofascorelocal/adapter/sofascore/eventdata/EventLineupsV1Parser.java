package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.fixture.FixtureContentKind;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class EventLineupsV1Parser {

    public static final String PARSER_VERSION = "event-lineups-v1";

    private static final Set<String> ROOT_FIELDS = Set.of(
            "eventId",
            "confirmed",
            "home",
            "away");
    private static final Set<String> SIDE_FIELDS = Set.of("formation", "players");
    private static final Set<String> PLAYER_ENTRY_FIELDS = Set.of(
            "player",
            "shirtNumber",
            "position",
            "substitute");
    private static final Set<String> PLAYER_FIELDS = Set.of("id", "name");

    public J5ParseResult<EventLineups> parse(LoadedFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        J5ParseEvidence evidence = evidence(fixture);
        J5ParseResult<EventLineups> preflight = preflight(fixture, evidence);
        if (preflight != null) {
            return preflight;
        }

        JsonNode root;
        try {
            root = J5JsonParserSupport.readTree(fixture.rawPayload());
        }
        catch (JacksonException exception) {
            return failed(
                    J5ParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    J5ParseProblem.Code.INVALID_JSON,
                    "$",
                    "The payload is not valid unambiguous JSON");
        }

        List<J5ParseWarning> warnings = new ArrayList<>();
        List<J5ParseProblem> problems = new ArrayList<>();
        if (!J5JsonParserSupport.requiredObject(root, "$", problems)) {
            return incompatible(evidence, warnings, problems);
        }
        J5JsonParserSupport.warnUnknownFields(root, ROOT_FIELDS, "$", warnings);
        Long eventId = J5JsonParserSupport.requiredPositiveLong(
                root.get("eventId"),
                "$.eventId",
                problems);
        Boolean confirmed = J5JsonParserSupport.requiredBoolean(
                root.get("confirmed"),
                "$.confirmed",
                problems);

        CompletenessCounter completeness = new CompletenessCounter();
        completeness.expect("$.confirmed=true", Boolean.TRUE.equals(confirmed));
        TeamLineup home = parseSide(
                root.get("home"),
                "$.home",
                LineupSide.HOME,
                warnings,
                problems,
                completeness);
        TeamLineup away = parseSide(
                root.get("away"),
                "$.away",
                LineupSide.AWAY,
                warnings,
                problems,
                completeness);

        if (!problems.isEmpty()
                || eventId == null
                || confirmed == null
                || home == null
                || away == null) {
            return incompatible(evidence, warnings, problems);
        }
        return J5ParseResult.parsed(
                evidence,
                new EventLineups(eventId, confirmed, home, away),
                completeness.report(),
                warnings);
    }

    private static TeamLineup parseSide(
            JsonNode node,
            String path,
            LineupSide side,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems,
            CompletenessCounter completeness) {
        if (!J5JsonParserSupport.requiredObject(node, path, problems)) {
            return null;
        }
        J5JsonParserSupport.warnUnknownFields(node, SIDE_FIELDS, path, warnings);
        Optional<String> formation = J5JsonParserSupport.optionalText(
                node.get("formation"),
                path + ".formation",
                32,
                warnings,
                problems);
        completeness.expect(path + ".formation", formation.isPresent());
        JsonNode playersNode = node.get("players");
        if (!J5JsonParserSupport.requiredArray(playersNode, path + ".players", problems)) {
            return null;
        }
        completeness.expect(path + ".players", !playersNode.isEmpty());
        List<EventLineupPlayer> players = new ArrayList<>();
        for (int index = 0; index < playersNode.size(); index++) {
            JsonNode playerEntry = playersNode.get(index);
            String playerPath = path + ".players[" + index + "]";
            EventLineupPlayer player = parsePlayer(
                    playerEntry,
                    playerPath,
                    warnings,
                    problems,
                    completeness);
            if (player != null) {
                players.add(player);
            }
        }
        return new TeamLineup(side, formation, players);
    }

    private static EventLineupPlayer parsePlayer(
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems,
            CompletenessCounter completeness) {
        if (!J5JsonParserSupport.requiredObject(node, path, problems)) {
            return null;
        }
        J5JsonParserSupport.warnUnknownFields(
                node,
                PLAYER_ENTRY_FIELDS,
                path,
                warnings);
        JsonNode playerNode = node.get("player");
        if (!J5JsonParserSupport.requiredObject(
                playerNode,
                path + ".player",
                problems)) {
            return null;
        }
        J5JsonParserSupport.warnUnknownFields(
                playerNode,
                PLAYER_FIELDS,
                path + ".player",
                warnings);
        Long playerId = J5JsonParserSupport.requiredPositiveLong(
                playerNode.get("id"),
                path + ".player.id",
                problems);
        String playerName = J5JsonParserSupport.requiredText(
                playerNode.get("name"),
                path + ".player.name",
                200,
                problems);
        Optional<Integer> shirtNumber = J5JsonParserSupport.optionalInteger(
                node.get("shirtNumber"),
                path + ".shirtNumber",
                1,
                999,
                warnings,
                problems);
        completeness.expect(path + ".shirtNumber", shirtNumber.isPresent());
        Optional<String> position = J5JsonParserSupport.optionalText(
                node.get("position"),
                path + ".position",
                32,
                warnings,
                problems);
        completeness.expect(path + ".position", position.isPresent());
        Boolean substitute = J5JsonParserSupport.requiredBoolean(
                node.get("substitute"),
                path + ".substitute",
                problems);
        if (playerId == null || playerName == null || substitute == null) {
            return null;
        }
        return new EventLineupPlayer(
                playerId,
                playerName,
                shirtNumber,
                position,
                !substitute);
    }

    private static J5ParseEvidence evidence(LoadedFixture fixture) {
        return new J5ParseEvidence(
                fixture.manifest().fixtureId(),
                fixture.manifest().endpointType(),
                fixture.rawSha256(),
                fixture.canonicalJsonSha256(),
                fixture.manifest().recordedAt(),
                PARSER_VERSION);
    }

    private static J5ParseResult<EventLineups> preflight(
            LoadedFixture fixture,
            J5ParseEvidence evidence) {
        if (fixture.manifest().endpointType() != SofascoreEndpointType.EVENT_LINEUPS) {
            return failed(
                    J5ParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    J5ParseProblem.Code.UNSUPPORTED_ENDPOINT,
                    "$",
                    "The lineups parser accepts EVENT_LINEUPS fixtures only");
        }
        if (!PARSER_VERSION.equals(fixture.manifest().parserVersion())) {
            return failed(
                    J5ParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    J5ParseProblem.Code.UNSUPPORTED_PARSER_VERSION,
                    "$",
                    "The fixture parser version does not match event-lineups-v1");
        }
        if (fixture.contentKind() != FixtureContentKind.JSON) {
            return failed(
                    J5ParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    J5ParseProblem.Code.UNEXPECTED_CONTENT_KIND,
                    "$",
                    "The lineups parser accepts JSON content only");
        }
        return null;
    }

    private static J5ParseResult<EventLineups> incompatible(
            J5ParseEvidence evidence,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return J5ParseResult.failed(
                J5ParseStatus.SCHEMA_INCOMPATIBLE,
                evidence,
                warnings,
                problems);
    }

    private static J5ParseResult<EventLineups> failed(
            J5ParseStatus status,
            J5ParseEvidence evidence,
            J5ParseProblem.Code code,
            String path,
            String message) {
        return J5ParseResult.failed(
                status,
                evidence,
                List.of(),
                List.of(J5JsonParserSupport.problem(code, path, message)));
    }

    private static final class CompletenessCounter {

        private int expected;
        private int present;
        private final List<String> missingPaths = new ArrayList<>();

        void expect(String path, boolean available) {
            expected++;
            if (available) {
                present++;
            }
            else {
                missingPaths.add(path);
            }
        }

        J5CompletenessReport report() {
            return J5CompletenessReport.measured(present, expected, missingPaths);
        }
    }
}
