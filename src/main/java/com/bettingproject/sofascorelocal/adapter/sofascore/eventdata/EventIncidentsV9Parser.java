package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Incident parser for the observed generic card reason and bench stoppage time.
 *
 * <p>V9 preserves every V8 rule. It adds the provider card reason
 * {@code Other reason} and recognizes {@code benchAddedTime} only on a card carrying a negative
 * technical {@code time} and a {@code benchTime}. The latter becomes normalized added time while
 * the provider fields remain in the immutable raw snapshot. Conflicting or out-of-context bench
 * stoppage-time fields remain schema incompatibilities.</p>
 */
public class EventIncidentsV9Parser extends EventIncidentsV8Parser {

    public static final String PARSER_VERSION = "event-incidents-v9";

    private static final Set<String> V9_INCIDENT_FIELDS = withAdditionalValues(
            INCIDENT_FIELDS, "benchAddedTime");
    private static final Set<String> V9_CARD_REASONS = withAdditionalValues(
            CARD_REASONS, "Other reason");

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected Set<String> incidentFields() {
        return V9_INCIDENT_FIELDS;
    }

    @Override
    protected Set<String> cardReasons() {
        return V9_CARD_REASONS;
    }

    @Override
    protected Optional<Integer> normalizedAddedTime(
            String type,
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        JsonNode benchAddedTimeNode = item.get("benchAddedTime");
        if (J5JsonParserSupport.isAbsent(benchAddedTimeNode)) {
            return super.normalizedAddedTime(type, item, path, warnings, problems);
        }

        Optional<Integer> ordinaryAddedTime =
                super.normalizedAddedTime(type, item, path, warnings, problems);
        boolean compatibleContext = true;
        if (!"card".equals(type)) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path + ".benchAddedTime",
                    "Provider benchAddedTime is supported only for a card"));
            compatibleContext = false;
        }

        JsonNode rawTime = item.get("time");
        if (rawTime == null
                || !rawTime.isIntegralNumber()
                || !rawTime.canConvertToInt()
                || rawTime.intValue() >= 0
                || J5JsonParserSupport.isAbsent(item.get("benchTime"))) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path,
                    "Provider benchAddedTime requires a negative technical time and benchTime"));
            compatibleContext = false;
        }
        if (!J5JsonParserSupport.isAbsent(item.get("addedTime"))) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path,
                    "Provider addedTime and benchAddedTime cannot both be present"));
            compatibleContext = false;
        }

        Integer benchAddedTime = J5JsonParserSupport.requiredInteger(
                benchAddedTimeNode,
                path + ".benchAddedTime",
                0,
                300,
                problems);
        if (!compatibleContext || benchAddedTime == null || ordinaryAddedTime.isPresent()) {
            return Optional.empty();
        }

        warnings.add(J5JsonParserSupport.warning(
                J5ParseWarning.Code.PROVIDER_BENCH_CARD_ADDED_TIME_USED,
                path + ".benchAddedTime",
                "Provider benchAddedTime used as normalized added time for a bench card"));
        return Optional.of(benchAddedTime);
    }

    private static Set<String> withAdditionalValues(Set<String> existing, String additional) {
        HashSet<String> values = new HashSet<>(existing);
        values.add(additional);
        return Set.copyOf(values);
    }
}
