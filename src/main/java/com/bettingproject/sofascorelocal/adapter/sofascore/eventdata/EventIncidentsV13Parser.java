package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import java.util.HashSet;
import java.util.Set;

/**
 * Incident parser for the observed leaving-the-field card reason.
 *
 * <p>V13 preserves every V12 rule, including the strict terminal unminuted shootout context. It
 * accepts the exact provider card reason {@code Leaving field}, which represents leaving the field
 * without prior permission. The provider wording is retained in both the immutable raw snapshot
 * and the normalized card reason. Every other undocumented card reason remains a schema
 * incompatibility.</p>
 */
public final class EventIncidentsV13Parser extends EventIncidentsV12Parser {

    public static final String PARSER_VERSION = "event-incidents-v13";

    private static final Set<String> V13_CARD_REASONS = withLeavingField(
            CARD_REASONS, "Other reason", "Off the ball foul", "Leaving field");

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected Set<String> cardReasons() {
        return V13_CARD_REASONS;
    }

    private static Set<String> withLeavingField(
            Set<String> existing,
            String firstHistorical,
            String secondHistorical,
            String additional) {
        HashSet<String> values = new HashSet<>(existing);
        values.add(firstHistorical);
        values.add(secondHistorical);
        values.add(additional);
        return Set.copyOf(values);
    }
}
