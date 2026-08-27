package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import java.util.Set;

/**
 * Incident parser for the observed live extra-time period marker.
 *
 * <p>V14 preserves every V13 rule and adds only the exact provider label
 * {@code Extra time} for a live {@code period} incident. The label is retained verbatim and is
 * intentionally distinct from the inactive terminal marker {@code ET}. An explicitly inactive
 * {@code Extra time} marker remains an atomic schema incompatibility.</p>
 */
public final class EventIncidentsV14Parser extends EventIncidentsV13Parser {

    public static final String PARSER_VERSION = "event-incidents-v14";

    private static final Set<String> V14_PERIOD_TEXTS = Set.of(
            "HT", "FT", "ET", "PEN", "First half", "Second half", "Extra time");
    private static final Set<String> V14_LIVE_PERIOD_TEXTS = Set.of(
            "First half", "Second half", "Extra time");

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected Set<String> periodTexts() {
        return V14_PERIOD_TEXTS;
    }

    @Override
    protected Set<String> livePeriodTexts() {
        return V14_LIVE_PERIOD_TEXTS;
    }
}
