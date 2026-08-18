package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Incident parser accepting the provider's explicit injury-substitution class.
 *
 * <p>V7 preserves every V6 rule and adds the observed {@code incidentClass="injury"}
 * representation. When the provider also supplies {@code injury}, an injury-class substitution
 * cannot contradict it with {@code false}. Missing optional metadata remains measured as partial
 * completeness instead of being invented.</p>
 */
public class EventIncidentsV7Parser extends EventIncidentsV6Parser {

    public static final String PARSER_VERSION = "event-incidents-v7";
    private static final Set<String> SUBSTITUTION_CLASSES = Set.of("regular", "injury");

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected Set<String> substitutionClasses() {
        return SUBSTITUTION_CLASSES;
    }

    @Override
    protected void validateSubstitution(
            String type,
            Optional<String> incidentClass,
            Optional<Boolean> injury,
            String path,
            List<J5ParseProblem> problems) {
        if ("substitution".equals(type)
                && incidentClass.filter("injury"::equals).isPresent()
                && injury.filter(value -> !value).isPresent()) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                    path,
                    "an injury-class substitution cannot declare injury=false"));
        }
    }
}
