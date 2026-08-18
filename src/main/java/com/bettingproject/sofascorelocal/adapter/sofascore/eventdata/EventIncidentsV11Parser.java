package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import java.util.HashSet;
import java.util.Set;

/**
 * Incident parser for the observed off-the-ball card reason.
 *
 * <p>V11 preserves every V10 rule. It accepts the exact provider card reason
 * {@code Off the ball foul}, which represents an obstruction away from the ball. The provider
 * wording is retained in both the immutable raw snapshot and the normalized card reason. Every
 * other undocumented card reason remains a schema incompatibility.</p>
 */
public class EventIncidentsV11Parser extends EventIncidentsV10Parser {

    public static final String PARSER_VERSION = "event-incidents-v11";

    private static final Set<String> V11_CARD_REASONS = withAdditionalValue(
            CARD_REASONS, "Other reason", "Off the ball foul");

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected Set<String> cardReasons() {
        return V11_CARD_REASONS;
    }

    private static Set<String> withAdditionalValue(
            Set<String> existing,
            String firstAdditional,
            String secondAdditional) {
        HashSet<String> values = new HashSet<>(existing);
        values.add(firstAdditional);
        values.add(secondAdditional);
        return Set.copyOf(values);
    }
}
