package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/**
 * Incident parser for the observed regular-goal origin alias.
 *
 * <p>V10 preserves every V9 rule. It recognizes {@code from=regular} only when the same goal
 * declares {@code incidentClass=regular}. The redundant provider value is retained in the raw
 * snapshot and omitted from the normalized special goal origin, just like the previously observed
 * {@code from=shot} alias. All crossed and unrelated uses remain schema incompatibilities.</p>
 */
public class EventIncidentsV10Parser extends EventIncidentsV9Parser {

    public static final String PARSER_VERSION = "event-incidents-v10";

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected Optional<String> normalizedGoalOrigin(
            Optional<String> incidentClass,
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (incidentClass.filter("regular"::equals).isPresent()
                && node != null
                && node.isString()
                && "regular".equals(node.stringValue())) {
            warnings.add(J5JsonParserSupport.warning(
                    J5ParseWarning.Code.PROVIDER_REGULAR_GOAL_ORIGIN_OMITTED,
                    path,
                    "Provider regular origin on a regular goal retained in raw evidence and omitted from the normalized special origin"));
            return Optional.empty();
        }
        return super.normalizedGoalOrigin(incidentClass, node, path, warnings, problems);
    }
}
