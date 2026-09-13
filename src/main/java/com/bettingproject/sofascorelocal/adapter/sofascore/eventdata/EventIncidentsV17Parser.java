package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import java.util.HashSet;
import java.util.Set;

/**
 * Incident parser for the observed professional-handball card reason.
 *
 * <p>V17 preserves every V16 rule and adds only the exact card reason
 * {@code Professional handball}. The provider text remains unchanged in the raw evidence and
 * normalized reason. This extension neither changes the card class nor adds the value to any
 * other incident vocabulary. Unknown reasons and malformed fields remain incompatible.</p>
 */
public final class EventIncidentsV17Parser extends EventIncidentsV16Parser {

    public static final String PARSER_VERSION = "event-incidents-v17";

    private final Set<String> supportedCardReasons = withProfessionalHandball(super.cardReasons());

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected Set<String> cardReasons() {
        return supportedCardReasons;
    }

    private static Set<String> withProfessionalHandball(Set<String> historicalReasons) {
        Set<String> reasons = new HashSet<>(historicalReasons);
        reasons.add("Professional handball");
        return Set.copyOf(reasons);
    }
}
