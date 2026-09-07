package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import tools.jackson.databind.JsonNode;

/**
 * Incident parser for an observed empty auxiliary-action array in a terminal shootout.
 *
 * <p>V15 preserves every V14 rule. It treats an omitted
 * {@code footballPassingNetworkAction} property and an exact empty JSON array as equivalent only
 * while the inherited V12 policy has already established a coherent terminal, wholly unminuted
 * shootout. Explicit {@code null}, non-array values and non-empty arrays are not widened by this
 * version. Mixed timed and unminuted shootouts remain schema incompatibilities.</p>
 */
public class EventIncidentsV15Parser extends EventIncidentsV14Parser {

    public static final String PARSER_VERSION = "event-incidents-v15";

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    protected boolean acceptsUnminutedShootoutAction(JsonNode action) {
        return action == null || (action.isArray() && action.isEmpty());
    }
}
