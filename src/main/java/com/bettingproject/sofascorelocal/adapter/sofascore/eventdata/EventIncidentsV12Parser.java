package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Incident parser for an observed terminal shootout without any provider minute source.
 *
 * <p>V12 preserves every V11 rule. It leaves the normalized minute absent only when the complete
 * response describes one exact inactive {@code PEN} marker, a finished {@code FT} or {@code ET}
 * period, and a contiguous shootout whose attempts all omit both the top-level time and the
 * auxiliary action array. The raw sentinel and omissions remain untouched, the UI renders an
 * em dash, and no minute is inferred from the shootout sequence. A missed penalty may omit both
 * {@code reason} and {@code description} for {@code inGamePenalty} and {@code penaltyShootout};
 * that inherited V6 rule remains measured as partial completeness. Malformed, mixed or
 * non-terminal omissions remain schema incompatibilities.</p>
 */
public class EventIncidentsV12Parser extends EventIncidentsV11Parser {

    public static final String PARSER_VERSION = "event-incidents-v12";

    private static final int PENALTY_PERIOD_TIME_SENTINEL = 999;

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
        if (acceptsMissingMinute(type, item, allItems)) {
            warnings.add(J5JsonParserSupport.warning(
                    J5ParseWarning.Code.PROVIDER_SHOOTOUT_MINUTE_ABSENT,
                    path + ".time",
                    "Provider omitted every effective minute source for a coherent terminal shootout; normalized minute remains absent"));
            return null;
        }
        return super.normalizedMinute(type, item, allItems, path, warnings, problems);
    }

    @Override
    protected boolean allowsMissingMinute(
            String type,
            JsonNode item,
            JsonNode allItems) {
        return acceptsMissingMinute(type, item, allItems);
    }

    @Override
    protected void measureAdditionalCompleteness(
            String type,
            JsonNode item,
            JsonNode allItems,
            String path,
            Integer normalizedMinute,
            CompletenessCounter counter) {
        if (acceptsMissingMinute(type, item, allItems)) {
            counter.expect(path + ".time", false);
        }
    }

    private boolean acceptsMissingMinute(
            String type,
            JsonNode item,
            JsonNode allItems) {
        if (!coherentUnminutedTerminalShootout(allItems)) {
            return false;
        }
        return ("penaltyShootout".equals(type) && isUnminutedShootoutAttempt(item))
                || ("period".equals(type) && isExactTerminalPenMarker(item));
    }

    private boolean coherentUnminutedTerminalShootout(JsonNode allItems) {
        if (allItems == null || !allItems.isArray() || allItems.isEmpty()) {
            return false;
        }

        JsonNode penMarker = null;
        JsonNode finalAttempt = null;
        boolean finishedPlayingPeriod = false;
        int shootoutCount = 0;
        int greatestSequence = 0;
        Set<Integer> sequences = new HashSet<>();

        for (int index = 0; index < allItems.size(); index++) {
            JsonNode candidate = allItems.get(index);
            if (candidate == null || !candidate.isObject()) {
                continue;
            }
            if (isExactTerminalPenMarker(candidate)) {
                if (penMarker != null) {
                    return false;
                }
                penMarker = candidate;
                continue;
            }
            if (isFinishedPlayingPeriod(candidate)) {
                finishedPlayingPeriod = true;
                continue;
            }
            if (!textEquals(candidate.get("incidentType"), "penaltyShootout")) {
                continue;
            }
            if (!isUnminutedShootoutAttempt(candidate)
                    || !textIn(candidate.get("incidentClass"), Set.of("scored", "missed"))
                    || candidate.get("isHome") == null
                    || !candidate.get("isHome").isBoolean()
                    || !integerBetween(candidate.get("homeScore"), 0, 99)
                    || !integerBetween(candidate.get("awayScore"), 0, 99)
                    || !integerBetween(candidate.get("sequence"), 1, 999)) {
                return false;
            }
            int sequence = candidate.get("sequence").intValue();
            if (!sequences.add(sequence)) {
                return false;
            }
            shootoutCount++;
            if (sequence > greatestSequence) {
                greatestSequence = sequence;
                finalAttempt = candidate;
            }
        }

        if (penMarker == null
                || finalAttempt == null
                || !finishedPlayingPeriod
                || shootoutCount != greatestSequence
                || sequences.size() != shootoutCount) {
            return false;
        }
        for (int expected = 1; expected <= shootoutCount; expected++) {
            if (!sequences.contains(expected)) {
                return false;
            }
        }
        return integerEquals(
                        penMarker.get("homeScore"), finalAttempt.get("homeScore").intValue())
                && integerEquals(
                        penMarker.get("awayScore"), finalAttempt.get("awayScore").intValue());
    }

    private boolean isUnminutedShootoutAttempt(JsonNode item) {
        return item != null
                && item.isObject()
                && textEquals(item.get("incidentType"), "penaltyShootout")
                && item.get("time") == null
                && acceptsUnminutedShootoutAction(item.get("footballPassingNetworkAction"));
    }

    /**
     * Defines the exact auxiliary-action absence accepted for an unminuted shootout attempt.
     *
     * <p>The V12 historical contract accepts only an omitted property. Later versioned parsers may
     * widen this single representation rule without changing the semantics of V12 through V14.</p>
     */
    protected boolean acceptsUnminutedShootoutAction(JsonNode action) {
        return action == null;
    }

    private static boolean isExactTerminalPenMarker(JsonNode item) {
        return item != null
                && item.isObject()
                && textEquals(item.get("incidentType"), "period")
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

    private static boolean isFinishedPlayingPeriod(JsonNode item) {
        return textEquals(item.get("incidentType"), "period")
                && textIn(item.get("text"), Set.of("FT", "ET"))
                && item.get("isLive") != null
                && item.get("isLive").isBoolean()
                && !item.get("isLive").booleanValue()
                && integerBetween(item.get("time"), 0, 300);
    }

    private static boolean textEquals(JsonNode node, String expected) {
        return node != null && node.isString() && expected.equals(node.stringValue());
    }

    private static boolean textIn(JsonNode node, Set<String> expected) {
        return node != null && node.isString() && expected.contains(node.stringValue());
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
