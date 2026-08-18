package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Incident parser for the observed end-of-shootout marker and woodwork outcomes.
 *
 * <p>V8 preserves every V7 rule. It recognizes {@code time=999} only on an exact terminal
 * {@code period/PEN} marker backed by one or more shootout attempts with valid effective minutes.
 * The marker is normalized to the greatest effective shootout minute, while the provider sentinel
 * remains exclusively in the immutable raw snapshot. V8 also accepts the coherent
 * {@code Woodwork/woodwork} missed-penalty tuple for both in-game penalties and shootout attempts.
 * All incomplete woodwork tuples and unrelated sentinel uses remain schema incompatibilities.</p>
 */
public class EventIncidentsV8Parser extends EventIncidentsV7Parser {

    public static final String PARSER_VERSION = "event-incidents-v8";

    private static final int PENALTY_PERIOD_TIME_SENTINEL = 999;
    private static final Set<String> MISSED_PENALTY_REASONS = Set.of(
            "offTarget", "goalkeeperSave", "woodwork");
    private static final Set<String> SHOOTOUT_REASONS = Set.of(
            "scored", "offTarget", "goalkeeperSave", "woodwork");
    private static final Set<String> MISSED_PENALTY_DESCRIPTIONS = Set.of(
            "Off target", "Goalkeeper save", "Woodwork");
    private static final Set<String> SHOOTOUT_DESCRIPTIONS = Set.of(
            "Scored", "Off target", "Goalkeeper save", "Woodwork");

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected Integer normalizedMinute(
            String type,
            JsonNode item,
            JsonNode allItems,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (!isExactPenaltyPeriodSentinel(type, item)) {
            return super.normalizedMinute(type, item, allItems, path, warnings, problems);
        }

        OptionalInt shootoutEndMinute = shootoutEndMinute(allItems);
        if (shootoutEndMinute.isEmpty()) {
            return super.normalizedMinute(type, item, allItems, path, warnings, problems);
        }

        int normalizedMinute = shootoutEndMinute.orElseThrow();
        warnings.add(J5JsonParserSupport.warning(
                J5ParseWarning.Code.PROVIDER_PENALTY_PERIOD_SENTINEL_NORMALIZED,
                path + ".time",
                "Provider PEN period sentinel retained in raw evidence and normalized to the last effective shootout minute "
                        + normalizedMinute));
        return normalizedMinute;
    }

    @Override
    protected Set<String> penaltyReasons(String type) {
        return "inGamePenalty".equals(type)
                ? MISSED_PENALTY_REASONS
                : "penaltyShootout".equals(type)
                        ? SHOOTOUT_REASONS
                        : super.penaltyReasons(type);
    }

    @Override
    protected Set<String> penaltyDescriptions(String type) {
        return "inGamePenalty".equals(type)
                ? MISSED_PENALTY_DESCRIPTIONS
                : "penaltyShootout".equals(type)
                        ? SHOOTOUT_DESCRIPTIONS
                        : super.penaltyDescriptions(type);
    }

    @Override
    protected String penaltyDescriptionForReason(String reason) {
        return "woodwork".equals(reason)
                ? "Woodwork"
                : super.penaltyDescriptionForReason(reason);
    }

    @Override
    protected Optional<String> normalizedGoalOrigin(
            Optional<String> incidentClass,
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (incidentClass.filter("regular"::equals).isPresent()
                && textEquals(node, "shot")) {
            warnings.add(J5JsonParserSupport.warning(
                    J5ParseWarning.Code.PROVIDER_REGULAR_GOAL_ORIGIN_OMITTED,
                    path,
                    "Provider shot origin on a regular goal retained in raw evidence and omitted from the normalized special origin"));
            return Optional.empty();
        }
        return super.normalizedGoalOrigin(incidentClass, node, path, warnings, problems);
    }

    @Override
    protected void validatePenalty(
            String type,
            Optional<String> incidentClass,
            Optional<String> reason,
            Optional<String> description,
            String path,
            List<J5ParseProblem> problems) {
        super.validatePenalty(type, incidentClass, reason, description, path, problems);
        boolean mentionsWoodwork = reason.filter("woodwork"::equals).isPresent()
                || description.filter("Woodwork"::equals).isPresent();
        boolean exactWoodworkTuple = incidentClass.filter("missed"::equals).isPresent()
                && reason.filter("woodwork"::equals).isPresent()
                && description.filter("Woodwork"::equals).isPresent();
        if (mentionsWoodwork && !exactWoodworkTuple) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path,
                    "Woodwork requires the exact missed/Woodwork/woodwork penalty tuple"));
        }
    }

    private static boolean isExactPenaltyPeriodSentinel(String type, JsonNode item) {
        return "period".equals(type)
                && textEquals(item.get("text"), "PEN")
                && textEquals(item.get("period"), "penalties")
                && integerEquals(item.get("time"), PENALTY_PERIOD_TIME_SENTINEL)
                && integerEquals(item.get("addedTime"), PENALTY_PERIOD_TIME_SENTINEL)
                && item.get("isLive") != null
                && item.get("isLive").isBoolean()
                && !item.get("isLive").booleanValue()
                && integerBetween(item.get("homeScore"), 0, 99)
                && integerBetween(item.get("awayScore"), 0, 99);
    }

    private static OptionalInt shootoutEndMinute(JsonNode allItems) {
        if (allItems == null || !allItems.isArray()) {
            return OptionalInt.empty();
        }
        boolean foundShootout = false;
        int greatestMinute = -1;
        for (int index = 0; index < allItems.size(); index++) {
            JsonNode item = allItems.get(index);
            if (item == null
                    || !item.isObject()
                    || !textEquals(item.get("incidentType"), "penaltyShootout")) {
                continue;
            }
            foundShootout = true;
            OptionalInt effectiveMinute = effectiveShootoutMinute(item);
            if (effectiveMinute.isEmpty()) {
                return OptionalInt.empty();
            }
            greatestMinute = Math.max(greatestMinute, effectiveMinute.orElseThrow());
        }
        return foundShootout ? OptionalInt.of(greatestMinute) : OptionalInt.empty();
    }

    private static OptionalInt effectiveShootoutMinute(JsonNode item) {
        JsonNode topLevelTime = item.get("time");
        if (!J5JsonParserSupport.isAbsent(topLevelTime)) {
            return integerBetween(topLevelTime, 0, 300)
                    ? OptionalInt.of(topLevelTime.intValue())
                    : OptionalInt.empty();
        }
        JsonNode actions = item.get("footballPassingNetworkAction");
        if (actions == null || !actions.isArray() || actions.isEmpty()) {
            return OptionalInt.empty();
        }
        JsonNode firstAction = actions.get(0);
        if (firstAction == null || !firstAction.isObject()) {
            return OptionalInt.empty();
        }
        JsonNode nestedTime = firstAction.get("time");
        return integerBetween(nestedTime, 0, 300)
                ? OptionalInt.of(nestedTime.intValue())
                : OptionalInt.empty();
    }

    private static boolean textEquals(JsonNode node, String expected) {
        return node != null && node.isString() && expected.equals(node.stringValue());
    }

    private static boolean integerEquals(JsonNode node, int expected) {
        return node != null
                && node.isIntegralNumber()
                && node.canConvertToInt()
                && node.intValue() == expected;
    }

    private static boolean integerBetween(JsonNode node, int minimum, int maximum) {
        return node != null
                && node.isIntegralNumber()
                && node.canConvertToInt()
                && node.intValue() >= minimum
                && node.intValue() <= maximum;
    }
}
