package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Current provider incident parser.
 *
 * <p>V5 preserves every V4 rule and adds one exact observed provider card shape. When a card's
 * {@code time} contains the observed {@code -5} technical marker, a valid {@code benchTime} supplies
 * the normalized match minute. The raw bytes remain immutable, and no minute is inferred from
 * unrelated timing fields. Card class and reason are retained when supplied.</p>
 */
public final class EventIncidentsV5Parser extends EventIncidentsV4Parser {

    public static final String PARSER_VERSION = "event-incidents-v5";
    private static final int BENCH_CARD_TECHNICAL_TIME = -5;
    private static final Set<String> INCIDENT_FIELDS_V5;

    static {
        Set<String> fields = new HashSet<>(INCIDENT_FIELDS);
        fields.add("benchTime");
        INCIDENT_FIELDS_V5 = Set.copyOf(fields);
    }

    @Override
    protected Set<String> incidentFields() {
        return INCIDENT_FIELDS_V5;
    }

    @Override
    protected Integer normalizedMinute(
            String incidentType,
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        int minimum = "card".equals(incidentType) ? BENCH_CARD_TECHNICAL_TIME : 0;
        Integer time = J5JsonParserSupport.requiredInteger(
                item.get("time"), path + ".time", minimum, 300, problems);
        if (time == null || time >= 0) {
            return time;
        }
        if (time != BENCH_CARD_TECHNICAL_TIME) {
            problems.add(J5JsonParserSupport.problem(
                    J5ParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path + ".time",
                    "Only the observed -5 bench-card technical marker is supported"));
            return null;
        }
        Integer benchTime = J5JsonParserSupport.requiredInteger(
                item.get("benchTime"), path + ".benchTime", 0, 300, problems);
        if (benchTime != null) {
            warnings.add(J5JsonParserSupport.warning(
                    J5ParseWarning.Code.PROVIDER_BENCH_CARD_MINUTE_USED,
                    path + ".benchTime",
                    "Provider benchTime used for a card with the -5 technical time marker"));
        }
        return benchTime;
    }

    @Override
    protected Optional<String> normalizedIncidentClass(
            String incidentType,
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (!"card".equals(incidentType)) {
            return Optional.empty();
        }
        return optionalMetadataText(
                item.get("incidentClass"), path + ".incidentClass", 64, problems);
    }

    @Override
    protected Optional<String> normalizedReason(
            String incidentType,
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (!"card".equals(incidentType)) {
            return Optional.empty();
        }
        return optionalMetadataText(item.get("reason"), path + ".reason", 200, problems);
    }

    @Override
    protected String parserVersion() {
        return PARSER_VERSION;
    }

    private static Optional<String> optionalMetadataText(
            JsonNode node,
            String path,
            int maximumLength,
            List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) {
            return Optional.empty();
        }
        return Optional.ofNullable(J5JsonParserSupport.requiredText(
                node, path, maximumLength, problems));
    }
}
