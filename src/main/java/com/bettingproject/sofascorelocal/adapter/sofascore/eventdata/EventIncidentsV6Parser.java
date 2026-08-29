package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
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

/**
 * Football incident parser aligned with the reviewed J5 incident business rules.
 *
 * <p>V6 accepts the eight documented football incident types and keeps their distinct business
 * signals. Optional provider metadata contributes to measured completeness; its absence does not
 * invalidate the whole family. Structural contradictions, unsupported types and invalid enum
 * values remain schema incompatibilities. Raw bytes are never rewritten.</p>
 */
public class EventIncidentsV6Parser {

    public static final String PARSER_VERSION = "event-incidents-v6";
    private static final int PERIOD_ADDED_TIME_SENTINEL = 999;

    private static final Set<String> ROOT_FIELDS = Set.of("incidents", "home", "away");
    private static final Set<String> INCIDENT_TYPES = Set.of(
            "period", "substitution", "goal", "card", "injuryTime", "varDecision",
            "inGamePenalty", "penaltyShootout");
    private static final Set<String> SIDELESS_INCIDENT_TYPES = Set.of("period", "injuryTime");
    protected static final Set<String> INCIDENT_FIELDS = Set.of(
            "incidentType", "incidentClass", "time", "addedTime", "benchTime", "isHome",
            "team", "teamId", "player", "playerIn", "playerOut", "playerName", "assist1",
            "homeScore", "awayScore", "id", "reason", "rescinded", "confirmed",
            "description", "sequence", "reversedPeriodTime", "reversedPeriodTimeSeconds",
            "text", "color", "from", "to", "length", "period", "periodTimeSeconds",
            "timeSeconds", "injuryTime", "injury", "isLive", "footballPassingNetworkAction");
    private static final Set<String> PLAYER_FIELDS = Set.of(
            "id", "name", "firstName", "lastName", "slug", "shortName", "position",
            "jerseyNumber", "height", "userCount", "gender", "sofascoreId",
            "marketValueCurrency", "dateOfBirth", "dateOfBirthTimestamp",
            "proposedMarketValueRaw", "fieldTranslations", "country", "preferredFoot");
    private static final Set<String> TEAM_FIELDS = Set.of(
            "id", "name", "slug", "shortName", "gender", "userCount", "sport",
            "teamColors", "fieldTranslations", "nameCode");

    private static final Set<String> PERIOD_TEXTS = Set.of(
            "HT", "FT", "ET", "PEN", "First half", "Second half");
    private static final Set<String> SUBSTITUTION_CLASSES = Set.of("regular");
    private static final Set<String> GOAL_CLASSES = Set.of("regular", "penalty", "ownGoal");
    private static final Set<String> GOAL_ORIGINS = Set.of("penalty", "ownGoal");
    private static final Set<String> CARD_CLASSES = Set.of("yellow", "red", "yellowRed");
    protected static final Set<String> CARD_REASONS = Set.of(
            "Argument", "Foul", "Violent conduct", "Simulation", "Time wasting",
            "Professional foul last man", "Handball", "Persistent fouling",
            "Unsporting behaviour", "Unallowed field entering");
    private static final Set<String> VAR_CLASSES = Set.of(
            "goalNotAwarded", "goalAwarded", "penaltyAwarded", "penaltyNotAwarded",
            "redCardGiven", "cardUpgrade", "review");
    private static final Set<String> MISSED_PENALTY_CLASSES = Set.of("missed");
    private static final Set<String> SHOOTOUT_CLASSES = Set.of("scored", "missed");
    private static final Set<String> MISSED_PENALTY_REASONS = Set.of(
            "offTarget", "goalkeeperSave");
    private static final Set<String> SHOOTOUT_REASONS = Set.of(
            "scored", "offTarget", "goalkeeperSave");
    private static final Set<String> MISSED_PENALTY_DESCRIPTIONS = Set.of(
            "Off target", "Goalkeeper save");
    private static final Set<String> SHOOTOUT_DESCRIPTIONS = Set.of(
            "Scored", "Off target", "Goalkeeper save");

    public J5ParseResult<EventIncidents> parse(
            long snapshotId,
            long expectedEventId,
            RawPayloadEvidence payload,
            Instant receivedAt) {
        J5JsonParserSupport.requirePositiveEventId(expectedEventId);
        J5ParseEvidence evidence = evidence(snapshotId, payload, receivedAt);
        JsonNode root;
        try {
            root = J5JsonParserSupport.readTree(payload.bytes());
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
        JsonNode items = root.get("incidents");
        if (!J5JsonParserSupport.requiredArray(items, "$.incidents", problems)) {
            return incompatible(evidence, warnings, problems);
        }

        List<EventIncident> incidents = new ArrayList<>();
        CompletenessCounter completeness = new CompletenessCounter();
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            String path = "$.incidents[" + index + "]";
            if (!J5JsonParserSupport.requiredObject(item, path, problems)) {
                continue;
            }
            J5JsonParserSupport.warnUnknownFields(item, incidentFields(), path, warnings);

            String type = requiredIncidentType(item.get("incidentType"), path, problems);
            Integer minute = normalizedMinute(type, item, items, path, warnings, problems);
            Optional<Integer> addedTime = normalizedAddedTime(
                    type, item, path, warnings, problems);
            Optional<Boolean> home = optionalBoolean(
                    item.get("isHome"), path + ".isHome", problems);
            if (type != null && !SIDELESS_INCIDENT_TYPES.contains(type)) {
                completeness.expect(path + ".isHome", home.isPresent());
            }

            Optional<Long> participantProviderId = optionalTeamId(
                    item, path, warnings, problems);
            Player player = optionalPlayer(item.get("player"), path + ".player", warnings, problems);
            player = mergeTopLevelPlayerName(
                    player, item.get("playerName"), path + ".playerName", problems);
            Player playerIn = optionalPlayer(
                    item.get("playerIn"), path + ".playerIn", warnings, problems);
            Player playerOut = optionalPlayer(
                    item.get("playerOut"), path + ".playerOut", warnings, problems);
            Player assist = optionalPlayer(
                    item.get("assist1"), path + ".assist1", warnings, problems);

            Optional<Integer> homeScore = optionalInteger(
                    item.get("homeScore"), path + ".homeScore", 0, 99, problems);
            Optional<Integer> awayScore = optionalInteger(
                    item.get("awayScore"), path + ".awayScore", 0, 99, problems);
            if (homeScore.isPresent() != awayScore.isPresent()) {
                problems.add(J5JsonParserSupport.problem(
                        J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                        path,
                        "homeScore and awayScore must be present together"));
            }

            Optional<String> incidentClass = normalizedIncidentClass(
                    type, item.get("incidentClass"), path + ".incidentClass", problems);
            Optional<String> reason = normalizedReason(
                    type, item.get("reason"), path + ".reason", problems);
            Optional<String> periodText = "period".equals(type)
                    ? optionalEnumText(
                            item.get("text"), path + ".text", 64, periodTexts(), problems)
                    : Optional.empty();
            Optional<Boolean> periodLive = "period".equals(type)
                    ? optionalBoolean(item.get("isLive"), path + ".isLive", problems)
                    : Optional.empty();
            Optional<Boolean> injury = "substitution".equals(type)
                    ? optionalBoolean(item.get("injury"), path + ".injury", problems)
                    : Optional.empty();
            Optional<String> goalOrigin = "goal".equals(type)
                    ? normalizedGoalOrigin(
                            incidentClass,
                            item.get("from"),
                            path + ".from",
                            warnings,
                            problems)
                    : Optional.empty();
            Optional<Integer> injuryTimeLength = "injuryTime".equals(type)
                    ? optionalInteger(item.get("length"), path + ".length", 0, 300, problems)
                    : Optional.empty();
            Optional<Boolean> varConfirmed = "varDecision".equals(type)
                    ? optionalBoolean(item.get("confirmed"), path + ".confirmed", problems)
                    : Optional.empty();
            Optional<Boolean> rescinded = "card".equals(type)
                    ? optionalBoolean(item.get("rescinded"), path + ".rescinded", problems)
                    : Optional.empty();
            Optional<String> description = isPenalty(type)
                    ? optionalEnumText(
                            item.get("description"), path + ".description", 64,
                            penaltyDescriptions(type), problems)
                    : Optional.empty();
            Optional<Integer> shootoutSequence = "penaltyShootout".equals(type)
                    ? optionalInteger(item.get("sequence"), path + ".sequence", 1, 999, problems)
                    : Optional.empty();

            validateSubstitution(type, incidentClass, injury, path, problems);
            validatePeriod(type, periodText, periodLive, path, problems);
            validateGoal(type, incidentClass, goalOrigin, path, problems);
            validatePenalty(type, incidentClass, reason, description, path, problems);
            measureCompleteness(
                    type,
                    path,
                    player,
                    playerIn,
                    playerOut,
                    homeScore,
                    awayScore,
                    incidentClass,
                    reason,
                    periodText,
                    periodLive,
                    injury,
                    goalOrigin,
                    injuryTimeLength,
                    varConfirmed,
                    description,
                    shootoutSequence,
                    completeness);
            measureAdditionalCompleteness(
                    type, item, items, path, minute, completeness);

            if (type != null
                    && (minute != null || allowsMissingMinute(type, item, items))
                    && player.valid()
                    && playerIn.valid()
                    && playerOut.valid()
                    && assist.valid()) {
                incidents.add(new EventIncident(
                        index,
                        type,
                        Optional.ofNullable(minute),
                        addedTime,
                        home,
                        participantProviderId,
                        player.id(),
                        player.name(),
                        playerIn.id(),
                        playerIn.name(),
                        playerOut.id(),
                        playerOut.name(),
                        homeScore,
                        awayScore,
                        incidentClass,
                        reason,
                        periodText,
                        injury,
                        assist.id(),
                        assist.name(),
                        goalOrigin,
                        injuryTimeLength,
                        varConfirmed,
                        rescinded,
                        description,
                        shootoutSequence));
            }
        }

        if (!problems.isEmpty()) {
            return incompatible(evidence, warnings, problems);
        }
        J5CompletenessReport report = incidents.isEmpty()
                ? J5CompletenessReport.emptyValid()
                : J5CompletenessReport.measured(
                        completeness.presentSignals,
                        completeness.expectedSignals,
                        completeness.missingPaths);
        return J5ParseResult.parsed(
                evidence,
                new EventIncidents(expectedEventId, incidents),
                report,
                warnings);
    }

    private static String requiredIncidentType(
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        String type = J5JsonParserSupport.requiredText(
                node, path + ".incidentType", 64, problems);
        if (type != null && !INCIDENT_TYPES.contains(type)) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path + ".incidentType",
                    "Unsupported football incident type"));
            return null;
        }
        return type;
    }

    protected Integer normalizedMinute(
            String type,
            JsonNode item,
            JsonNode allItems,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        JsonNode timeNode = item.get("time");
        if ("penaltyShootout".equals(type) && J5JsonParserSupport.isAbsent(timeNode)) {
            JsonNode actions = item.get("footballPassingNetworkAction");
            if (actions == null || !actions.isArray() || actions.isEmpty()) {
                problems.add(J5JsonParserSupport.problem(
                        J5ParseProblem.Code.REQUIRED_FIELD_MISSING,
                        path + ".footballPassingNetworkAction[0].time",
                        "Penalty shootout requires a top-level or first nested action time"));
                return null;
            }
            JsonNode firstAction = actions.get(0);
            if (!J5JsonParserSupport.requiredObject(
                    firstAction, path + ".footballPassingNetworkAction[0]", problems)) {
                return null;
            }
            Integer nestedTime = J5JsonParserSupport.requiredInteger(
                    firstAction.get("time"),
                    path + ".footballPassingNetworkAction[0].time",
                    0,
                    300,
                    problems);
            if (nestedTime != null) {
                warnings.add(J5JsonParserSupport.warning(
                        J5ParseWarning.Code.PROVIDER_NESTED_MINUTE_USED,
                        path + ".footballPassingNetworkAction[0].time",
                        "Provider nested shootout action time used as the normalized match minute"));
            }
            return nestedTime;
        }

        int minimum = "card".equals(type) ? Integer.MIN_VALUE : 0;
        Integer rawTime = J5JsonParserSupport.requiredInteger(
                timeNode, path + ".time", minimum, 300, problems);
        boolean benchTimePresent = !J5JsonParserSupport.isAbsent(item.get("benchTime"));
        if (rawTime != null && rawTime >= 0 && benchTimePresent) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path,
                    "A card benchTime requires a negative technical time"));
            return null;
        }
        if (rawTime == null || rawTime >= 0) {
            return rawTime;
        }
        Integer benchTime = J5JsonParserSupport.requiredInteger(
                item.get("benchTime"), path + ".benchTime", 0, 300, problems);
        if (benchTime != null) {
            warnings.add(J5JsonParserSupport.warning(
                    J5ParseWarning.Code.PROVIDER_BENCH_CARD_MINUTE_USED,
                    path + ".benchTime",
                    "Provider benchTime used for a card carrying a negative technical time"));
        }
        return benchTime;
    }

    protected boolean allowsMissingMinute(
            String type,
            JsonNode item,
            JsonNode allItems) {
        return false;
    }

    protected void measureAdditionalCompleteness(
            String type,
            JsonNode item,
            JsonNode allItems,
            String path,
            Integer normalizedMinute,
            CompletenessCounter counter) {
        // Versioned parsers may measure newly observed optional provider metadata.
    }

    protected Optional<Integer> normalizedAddedTime(
            String type,
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        JsonNode node = item.get("addedTime");
        String addedTimePath = path + ".addedTime";
        if ("period".equals(type)
                && node != null
                && node.isIntegralNumber()
                && node.canConvertToInt()
                && node.intValue() == PERIOD_ADDED_TIME_SENTINEL) {
            warnings.add(J5JsonParserSupport.warning(
                    J5ParseWarning.Code.PROVIDER_SENTINEL_NORMALIZED,
                    addedTimePath,
                    "Provider period-marker sentinel retained in raw evidence and omitted from normalized added time"));
            return Optional.empty();
        }
        return optionalInteger(node, addedTimePath, 0, 300, problems);
    }

    protected String parserVersion() {
        return PARSER_VERSION;
    }

    protected Set<String> incidentFields() {
        return INCIDENT_FIELDS;
    }

    protected Set<String> cardReasons() {
        return CARD_REASONS;
    }

    protected Set<String> periodTexts() {
        return PERIOD_TEXTS;
    }

    protected Set<String> livePeriodTexts() {
        return Set.of("First half", "Second half");
    }

    protected Set<String> substitutionClasses() {
        return SUBSTITUTION_CLASSES;
    }

    protected void validateSubstitution(
            String type,
            Optional<String> incidentClass,
            Optional<Boolean> injury,
            String path,
            List<J5ParseProblem> problems) {
        // V6 only accepts the historical regular class. V7 adds the observed injury class.
    }

    private Optional<String> normalizedIncidentClass(
            String type,
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        if (type == null) {
            return Optional.empty();
        }
        Set<String> allowed = switch (type) {
            case "substitution" -> substitutionClasses();
            case "goal" -> GOAL_CLASSES;
            case "card" -> CARD_CLASSES;
            case "varDecision" -> VAR_CLASSES;
            case "inGamePenalty" -> MISSED_PENALTY_CLASSES;
            case "penaltyShootout" -> SHOOTOUT_CLASSES;
            default -> Set.of();
        };
        if (allowed.isEmpty()) {
            return Optional.empty();
        }
        return optionalEnumText(node, path, 64, allowed, problems);
    }

    protected Optional<String> normalizedReason(
            String type,
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        if ("card".equals(type)) {
            return optionalEnumText(node, path, 200, cardReasons(), problems);
        }
        if (isPenalty(type)) {
            return optionalEnumText(node, path, 64, penaltyReasons(type), problems);
        }
        return Optional.empty();
    }

    protected void validatePeriod(
            String type,
            Optional<String> periodText,
            Optional<Boolean> periodLive,
            String path,
            List<J5ParseProblem> problems) {
        if (!"period".equals(type)
                || periodText.isEmpty()
                || !livePeriodTexts().contains(periodText.orElseThrow())) {
            return;
        }
        if (periodLive.isPresent() && !periodLive.orElseThrow()) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path,
                    "Live period labels require isLive=true"));
        }
    }

    protected Optional<String> normalizedGoalOrigin(
            Optional<String> incidentClass,
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        Optional<String> raw = optionalText(node, path, 64, problems);
        if (raw.isEmpty()) {
            return raw;
        }
        String value = raw.orElseThrow();
        if ("owngoal".equals(value)) {
            warnings.add(J5JsonParserSupport.warning(
                    J5ParseWarning.Code.PROVIDER_ALIAS_NORMALIZED,
                    path,
                    "Provider owngoal alias normalized to ownGoal"));
            return Optional.of("ownGoal");
        }
        if (!GOAL_ORIGINS.contains(value)) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Unsupported goal origin"));
            return Optional.empty();
        }
        return raw;
    }

    private static void validateGoal(
            String type,
            Optional<String> incidentClass,
            Optional<String> goalOrigin,
            String path,
            List<J5ParseProblem> problems) {
        if (!"goal".equals(type) || goalOrigin.isEmpty() || incidentClass.isEmpty()) {
            return;
        }
        if (!goalOrigin.orElseThrow().equals(incidentClass.orElseThrow())) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path,
                    "Goal origin must agree with incidentClass"));
        }
    }

    protected void validatePenalty(
            String type,
            Optional<String> incidentClass,
            Optional<String> reason,
            Optional<String> description,
            String path,
            List<J5ParseProblem> problems) {
        if (!isPenalty(type)) {
            return;
        }
        boolean compatible = true;
        if (incidentClass.isPresent() && reason.isPresent()) {
            compatible &= penaltyClassForReason(reason.orElseThrow())
                    .equals(incidentClass.orElseThrow());
        }
        if (reason.isPresent() && description.isPresent()) {
            compatible &= penaltyDescriptionForReason(reason.orElseThrow())
                    .equals(description.orElseThrow());
        }
        if (incidentClass.isPresent() && description.isPresent()) {
            String expectedClass = "Scored".equals(description.orElseThrow())
                    ? "scored"
                    : "missed";
            compatible &= expectedClass.equals(incidentClass.orElseThrow());
        }
        if (!compatible) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path,
                    "Penalty class, reason and description must describe the same outcome"));
        }
    }

    protected Set<String> penaltyReasons(String type) {
        return "inGamePenalty".equals(type)
                ? MISSED_PENALTY_REASONS
                : SHOOTOUT_REASONS;
    }

    protected Set<String> penaltyDescriptions(String type) {
        return "inGamePenalty".equals(type)
                ? MISSED_PENALTY_DESCRIPTIONS
                : SHOOTOUT_DESCRIPTIONS;
    }

    private static String penaltyClassForReason(String reason) {
        return "scored".equals(reason) ? "scored" : "missed";
    }

    protected String penaltyDescriptionForReason(String reason) {
        return switch (reason) {
            case "scored" -> "Scored";
            case "offTarget" -> "Off target";
            case "goalkeeperSave" -> "Goalkeeper save";
            default -> throw new IllegalStateException("unsupported normalized penalty reason");
        };
    }

    private void measureCompleteness(
            String type,
            String path,
            Player player,
            Player playerIn,
            Player playerOut,
            Optional<Integer> homeScore,
            Optional<Integer> awayScore,
            Optional<String> incidentClass,
            Optional<String> reason,
            Optional<String> periodText,
            Optional<Boolean> periodLive,
            Optional<Boolean> injury,
            Optional<String> goalOrigin,
            Optional<Integer> injuryTimeLength,
            Optional<Boolean> varConfirmed,
            Optional<String> description,
            Optional<Integer> shootoutSequence,
            CompletenessCounter counter) {
        if (type == null) {
            return;
        }
        switch (type) {
            case "period" -> {
                counter.expect(path + ".text", periodText.isPresent());
                counter.expect(path + ".score", homeScore.isPresent() && awayScore.isPresent());
                if (periodText.filter(livePeriodTexts()::contains).isPresent()) {
                    counter.expect(path + ".isLive", periodLive.isPresent());
                }
            }
            case "substitution" -> {
                counter.expect(path + ".incidentClass", incidentClass.isPresent());
                counter.expect(path + ".injury", injury.isPresent());
                counter.expect(path + ".playerIn", playerIn.present());
                counter.expect(path + ".playerOut", playerOut.present());
            }
            case "goal" -> {
                counter.expect(path + ".incidentClass", incidentClass.isPresent());
                counter.expect(path + ".player", player.present());
                counter.expect(path + ".score", homeScore.isPresent() && awayScore.isPresent());
                if (incidentClass.filter(value -> !"regular".equals(value)).isPresent()) {
                    counter.expect(path + ".from", goalOrigin.isPresent());
                }
            }
            case "card" -> {
                counter.expect(path + ".incidentClass", incidentClass.isPresent());
                counter.expect(path + ".player", player.present());
            }
            case "injuryTime" ->
                    counter.expect(path + ".length", injuryTimeLength.isPresent());
            case "varDecision" -> {
                counter.expect(path + ".incidentClass", incidentClass.isPresent());
                counter.expect(path + ".player", player.present());
                counter.expect(path + ".confirmed", varConfirmed.isPresent());
            }
            case "inGamePenalty", "penaltyShootout" -> {
                counter.expect(path + ".incidentClass", incidentClass.isPresent());
                counter.expect(path + ".player", player.present());
                counter.expect(path + ".reason", reason.isPresent());
                counter.expect(path + ".description", description.isPresent());
                if ("penaltyShootout".equals(type)) {
                    counter.expect(path + ".sequence", shootoutSequence.isPresent());
                }
            }
            default -> throw new IllegalStateException("unsupported normalized incident type");
        }
    }

    private static Optional<Long> optionalTeamId(
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        JsonNode direct = item.get("teamId");
        if (!J5JsonParserSupport.isAbsent(direct)) {
            return optionalPositiveLong(direct, path + ".teamId", problems);
        }
        JsonNode team = item.get("team");
        if (J5JsonParserSupport.isAbsent(team)) {
            return Optional.empty();
        }
        if (!J5JsonParserSupport.requiredObject(team, path + ".team", problems)) {
            return Optional.empty();
        }
        J5JsonParserSupport.warnUnknownFields(team, TEAM_FIELDS, path + ".team", warnings);
        return Optional.ofNullable(J5JsonParserSupport.requiredPositiveLong(
                team.get("id"), path + ".team.id", problems));
    }

    private static Player optionalPlayer(
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) {
            return Player.empty();
        }
        if (!J5JsonParserSupport.requiredObject(node, path, problems)) {
            return Player.invalid();
        }
        J5JsonParserSupport.warnUnknownFields(node, PLAYER_FIELDS, path, warnings);
        String name = J5JsonParserSupport.requiredText(node.get("name"), path + ".name", 200, problems);
        Optional<Long> id = optionalPositiveLong(node.get("id"), path + ".id", problems);
        return name == null ? Player.invalid() : Player.of(id, name);
    }

    private static Player mergeTopLevelPlayerName(
            Player player,
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        Optional<String> topLevelName = optionalText(node, path, 200, problems);
        if (topLevelName.isEmpty()) {
            return player;
        }
        if (!player.valid()) {
            return player;
        }
        if (player.name().isPresent()
                && !player.name().orElseThrow().equals(topLevelName.orElseThrow())) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path,
                    "playerName must agree with player.name"));
            return Player.invalid();
        }
        return player.name().isPresent()
                ? player
                : Player.of(Optional.empty(), topLevelName.orElseThrow());
    }

    private static Optional<String> optionalEnumText(
            JsonNode node,
            String path,
            int maximumLength,
            Set<String> allowed,
            List<J5ParseProblem> problems) {
        Optional<String> value = optionalText(node, path, maximumLength, problems);
        if (value.isPresent() && !allowed.contains(value.orElseThrow())) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Value is outside the documented football incident vocabulary"));
            return Optional.empty();
        }
        return value;
    }

    private static Optional<String> optionalText(
            JsonNode node,
            String path,
            int maximumLength,
            List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) {
            return Optional.empty();
        }
        return Optional.ofNullable(J5JsonParserSupport.requiredText(
                node, path, maximumLength, problems));
    }

    private static Optional<Integer> optionalInteger(
            JsonNode node,
            String path,
            int minimum,
            int maximum,
            List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) {
            return Optional.empty();
        }
        return Optional.ofNullable(J5JsonParserSupport.requiredInteger(
                node, path, minimum, maximum, problems));
    }

    private static Optional<Long> optionalPositiveLong(
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) {
            return Optional.empty();
        }
        return Optional.ofNullable(J5JsonParserSupport.requiredPositiveLong(node, path, problems));
    }

    private static Optional<Boolean> optionalBoolean(
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) {
            return Optional.empty();
        }
        return Optional.ofNullable(J5JsonParserSupport.requiredBoolean(node, path, problems));
    }

    private static boolean isPenalty(String type) {
        return "inGamePenalty".equals(type) || "penaltyShootout".equals(type);
    }

    private J5ParseEvidence evidence(
            long snapshotId,
            RawPayloadEvidence payload,
            Instant receivedAt) {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        return new J5ParseEvidence(
                "snapshot:" + snapshotId,
                SofascoreEndpointType.EVENT_INCIDENTS,
                Objects.requireNonNull(payload, "payload").sha256(),
                Optional.empty(),
                Objects.requireNonNull(receivedAt, "receivedAt"),
                parserVersion());
    }

    private static J5ParseResult<EventIncidents> incompatible(
            J5ParseEvidence evidence,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return J5ParseResult.failed(
                J5ParseStatus.SCHEMA_INCOMPATIBLE, evidence, warnings, problems);
    }

    private static J5ParseResult<EventIncidents> failed(
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

    private record Player(Optional<Long> id, Optional<String> name, boolean valid) {
        private static Player empty() {
            return new Player(Optional.empty(), Optional.empty(), true);
        }

        private static Player invalid() {
            return new Player(Optional.empty(), Optional.empty(), false);
        }

        private static Player of(Optional<Long> id, String name) {
            return new Player(id, Optional.of(name), true);
        }

        private boolean present() {
            return name.isPresent();
        }
    }

    protected static final class CompletenessCounter {
        private int presentSignals;
        private int expectedSignals;
        private final List<String> missingPaths = new ArrayList<>();

        protected void expect(String path, boolean present) {
            expectedSignals++;
            if (present) {
                presentSignals++;
            }
            else {
                missingPaths.add(path);
            }
        }
    }
}
