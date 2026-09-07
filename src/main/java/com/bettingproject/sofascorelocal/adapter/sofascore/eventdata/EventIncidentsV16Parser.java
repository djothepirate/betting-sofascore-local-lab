package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import java.util.Optional;
import java.util.Set;

/**
 * Incident parser for an observed in-game penalty award before a shot outcome exists.
 *
 * <p>V16 preserves every V15 rule and adds only the exact {@code inGamePenalty/awarded}
 * combination. An award remains an award: it is neither a goal nor a missed attempt. Its
 * minute and side retain the ordinary validation, and a supplied player remains validated and
 * preserved. A shooter and shot outcome are not expected completeness signals for an award.
 * The inherited penalty coherence checks still reject an award carrying a scored or missed
 * outcome, and every other incident-class vocabulary remains unchanged. V16 also returns the
 * existing atomic-field problem for a one-sided score before attempting domain construction;
 * it does not complete or normalize away that incomplete pair.</p>
 */
public final class EventIncidentsV16Parser extends EventIncidentsV15Parser {

    public static final String PARSER_VERSION = "event-incidents-v16";

    private static final Set<String> IN_GAME_PENALTY_CLASSES = Set.of("missed", "awarded");

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected Set<String> inGamePenaltyClasses() {
        return IN_GAME_PENALTY_CLASSES;
    }

    @Override
    protected boolean expectsPenaltyOutcomeDetails(
            String type,
            Optional<String> incidentClass) {
        return !("inGamePenalty".equals(type)
                && incidentClass.filter("awarded"::equals).isPresent());
    }

    @Override
    protected boolean rejectIncompleteScoreBeforeConstruction() {
        return true;
    }
}
