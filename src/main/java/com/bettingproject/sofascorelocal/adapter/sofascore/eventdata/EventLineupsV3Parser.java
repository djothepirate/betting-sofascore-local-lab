package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.eventdata.PlayerMatchStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.MissingLineupPlayer;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;

/** Versioned provider lineups including explicit captain, numeric player statistics and missing players. */
public final class EventLineupsV3Parser {

    public static final String PARSER_VERSION = "event-lineups-v3";
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
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
    private static final Set<String> NUMERIC_STATISTICS = Set.of(
            "accurateCross", "accurateLongBalls", "accurateOppositionHalfPasses", "accurateOwnHalfPasses",
            "accuratePass", "aerialLost", "aerialWon", "ballCarriesCount", "ballRecovery",
            "bestBallCarryProgression", "bigChanceCreated", "blockedScoringAttempt", "challengeLost",
            "defensiveValueNormalized", "dispossessed", "dribbleValueNormalized", "duelLost", "duelWon",
            "expectedAssists", "expectedGoals", "expectedGoalsOnTarget", "fouls", "goalAssist",
            "goalkeeperValueNormalized", "goals", "goalsPrevented", "goodHighClaim", "interceptionWon",
            "keeperSaveValue", "keyPass", "minutesPlayed", "onTargetScoringAttempt", "outfielderBlock",
            "passValueNormalized", "possessionLostCtrl", "progressiveBallCarriesCount", "punches", "rating",
            "saves", "shotOffTarget", "shotValueNormalized", "totalBallCarriesDistance", "totalClearance",
            "totalContest", "totalCross", "totalLongBalls", "totalOffside", "totalOppositionHalfPasses",
            "totalOwnHalfPasses", "totalPass", "totalProgression", "totalProgressiveBallCarriesDistance",
            "totalShots", "totalTackle", "touches", "unsuccessfulTouch", "wasFouled", "wonContest", "wonTackle");
    private static final Set<String> MISSING_FIELDS = Set.of("player", "type", "reason", "description",
            "externalType", "expectedEndDate", "shirtNumber", "jerseyNumber", "position");

    public J5ParseResult<EventLineups> parse(
            long snapshotId,
            long expectedEventId,
            RawPayloadEvidence payload,
            Instant receivedAt) {
        J5JsonParserSupport.requirePositiveEventId(expectedEventId);
        J5ParseEvidence evidence = evidence(snapshotId, payload, receivedAt);
        JsonNode root;
        try {
            root = JSON.readTree(payload.bytes());
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
        return new TeamLineup(side, formation, players,
                missingPlayers(node.get("missingPlayers"), path + ".missingPlayers", warnings, problems));
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
        Optional<Boolean> captain = J5JsonParserSupport.optionalBoolean(
                entry.get("captain"), path + ".captain", warnings, problems);
        Optional<PlayerMatchStatistics> statistics = statistics(
                entry.get("statistics"), path + ".statistics", warnings, problems);
        if (id == null || name == null || substitute == null) {
            return null;
        }
        return new EventLineupPlayer(id, name, shirtNumber, position, !substitute, captain, statistics);
    }

    private static Optional<PlayerMatchStatistics> statistics(JsonNode node, String path,
            List<J5ParseWarning> warnings, List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) return Optional.empty();
        if (!J5JsonParserSupport.requiredObject(node, path, problems)) return Optional.empty();
        Map<String, BigDecimal> values = new TreeMap<>(), versions = new TreeMap<>();
        for (var field : node.properties()) {
            String key = field.getKey();
            String fieldPath = path + "." + safeKey(key);
            if (!PlayerMatchStatistics.validKey(key)) {
                invalid(problems, fieldPath, "Statistic key is outside the bounded ASCII contract");
                continue;
            }
            JsonNode value = field.getValue();
            if (J5JsonParserSupport.isAbsent(value)) continue;
            if ("ratingVersions".equals(key)) {
                ratingVersions(value, fieldPath, versions, problems);
            } else if ("statisticsType".equals(key)) {
                if (J5JsonParserSupport.requiredObject(value, fieldPath, problems)) {
                    J5JsonParserSupport.warnUnknownFields(value, Set.of("sportSlug", "statisticsType"), fieldPath, warnings);
                    for (String metadata : List.of("sportSlug", "statisticsType")) {
                        if (!J5JsonParserSupport.isAbsent(value.get(metadata)))
                            J5JsonParserSupport.requiredText(value.get(metadata), fieldPath + "." + metadata, 64, problems);
                    }
                }
            } else if (NUMERIC_STATISTICS.contains(key)) {
                numeric(value, fieldPath, problems).ifPresent(number -> values.put(key, number));
            } else {
                warnUnknown(warnings, fieldPath, value.isNumber()
                        ? "Unknown numeric statistic preserved without interpretation"
                        : "Unknown non-numeric statistic ignored");
                if (value.isNumber()) numeric(value, fieldPath, problems).ifPresent(number -> values.put(key, number));
            }
        }
        if (values.size() > PlayerMatchStatistics.MAXIMUM_VALUES
                || versions.size() > PlayerMatchStatistics.MAXIMUM_RATING_VERSIONS) {
            invalid(problems, path, "Too many numeric statistics or rating versions");
            return Optional.empty();
        }
        return Optional.of(new PlayerMatchStatistics(values, versions));
    }

    private static void ratingVersions(JsonNode node, String path, Map<String, BigDecimal> values,
            List<J5ParseProblem> problems) {
        if (!J5JsonParserSupport.requiredObject(node, path, problems)) return;
        if (node.size() > PlayerMatchStatistics.MAXIMUM_RATING_VERSIONS) {
            invalid(problems, path, "Too many rating versions");
            return;
        }
        for (var field : node.properties()) {
            String key = field.getKey();
            if (!PlayerMatchStatistics.validKey(key)) {
                invalid(problems, path + "." + safeKey(key), "Rating-version key is outside the bounded ASCII contract");
            } else if (!J5JsonParserSupport.isAbsent(field.getValue())) {
                numeric(field.getValue(), path + "." + key, problems).ifPresent(number -> values.put(key, number));
            }
        }
    }

    private static Optional<BigDecimal> numeric(JsonNode node, String path, List<J5ParseProblem> problems) {
        if (!node.isNumber()) {
            problems.add(J5JsonParserSupport.problem(J5ParseProblem.Code.TYPE_MISMATCH, path,
                    "Player statistic must be a JSON number"));
            return Optional.empty();
        }
        try {
            return Optional.of(PlayerMatchStatistics.canonicalNumber(node.decimalValue()));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            invalid(problems, path, "Numeric statistic exceeds bounded precision or scale");
            return Optional.empty();
        }
    }

    private static Optional<List<MissingLineupPlayer>> missingPlayers(JsonNode node, String path,
            List<J5ParseWarning> warnings, List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) return Optional.empty();
        if (!J5JsonParserSupport.requiredArray(node, path, problems)) return Optional.empty();
        if (node.size() > 128) {
            invalid(problems, path, "Too many missing players");
            return Optional.empty();
        }
        List<MissingLineupPlayer> result = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode entry = node.get(index);
            String entryPath = path + "[" + index + "]";
            if (!J5JsonParserSupport.requiredObject(entry, entryPath, problems)) continue;
            J5JsonParserSupport.warnUnknownFields(entry, MISSING_FIELDS, entryPath, warnings);
            JsonNode player = entry.get("player");
            if (!J5JsonParserSupport.requiredObject(player, entryPath + ".player", problems)) continue;
            J5JsonParserSupport.warnUnknownFields(player, PLAYER_FIELDS, entryPath + ".player", warnings);
            Long id = J5JsonParserSupport.requiredPositiveLong(player.get("id"), entryPath + ".player.id", problems);
            String name = J5JsonParserSupport.requiredText(player.get("name"), entryPath + ".player.name", 200, problems);
            JsonNode numberSource = !J5JsonParserSupport.isAbsent(entry.get("jerseyNumber"))
                    || !J5JsonParserSupport.isAbsent(entry.get("shirtNumber")) ? entry : player;
            Optional<Integer> number = optionalShirtNumber(numberSource, entryPath, warnings, problems);
            JsonNode positionSource = J5JsonParserSupport.isAbsent(entry.get("position")) ? player.get("position") : entry.get("position");
            Optional<String> position = J5JsonParserSupport.optionalText(positionSource, entryPath + ".position", 32, warnings, problems);
            Optional<String> type = J5JsonParserSupport.optionalText(entry.get("type"), entryPath + ".type", 64, warnings, problems);
            Optional<Integer> reason = J5JsonParserSupport.optionalInteger(entry.get("reason"), entryPath + ".reason",
                    Integer.MIN_VALUE, Integer.MAX_VALUE, warnings, problems);
            Optional<String> description = J5JsonParserSupport.optionalText(entry.get("description"), entryPath + ".description", 300, warnings, problems);
            Optional<Integer> externalType = J5JsonParserSupport.optionalInteger(entry.get("externalType"), entryPath + ".externalType",
                    Integer.MIN_VALUE, Integer.MAX_VALUE, warnings, problems);
            Optional<OffsetDateTime> expectedEndDate = expectedEndDate(entry.get("expectedEndDate"), entryPath + ".expectedEndDate", problems);
            if (id != null && name != null) result.add(new MissingLineupPlayer(id, name, number, position,
                    type, reason, description, externalType, expectedEndDate));
        }
        return Optional.of(List.copyOf(result));
    }

    private static Optional<OffsetDateTime> expectedEndDate(JsonNode node, String path, List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) return Optional.empty();
        String text = J5JsonParserSupport.requiredText(node, path, 80, problems);
        if (text == null) return Optional.empty();
        try {
            return Optional.of(OffsetDateTime.parse(text));
        } catch (DateTimeParseException exception) {
            invalid(problems, path, "Expected end date must be an ISO date-time with an offset");
            return Optional.empty();
        }
    }

    private static void invalid(List<J5ParseProblem> problems, String path, String message) {
        problems.add(J5JsonParserSupport.problem(J5ParseProblem.Code.VALUE_OUT_OF_RANGE, path, message));
    }

    private static String safeKey(String key) { return key.length() <= 80 ? key : key.substring(0, 80); }

    private static void warnUnknown(List<J5ParseWarning> warnings, String path, String message) {
        if (warnings.size() < 256) warnings.add(J5JsonParserSupport.warning(J5ParseWarning.Code.UNKNOWN_FIELD, path, message));
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
