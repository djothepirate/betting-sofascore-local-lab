package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.fixture.FixtureContentKind;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class EventStatisticsV1Parser {

    public static final String PARSER_VERSION = "event-statistics-v1";

    private static final Set<String> ROOT_FIELDS = Set.of("eventId", "statistics");
    private static final Set<String> PERIOD_FIELDS = Set.of("period", "groups");
    private static final Set<String> GROUP_FIELDS = Set.of("groupName", "statisticsItems");
    private static final Set<String> ITEM_FIELDS = Set.of("key", "name", "home", "away");

    public J5ParseResult<EventStatistics> parse(LoadedFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        J5ParseEvidence evidence = evidence(fixture);
        J5ParseResult<EventStatistics> preflight = preflight(fixture, evidence);
        if (preflight != null) {
            return preflight;
        }

        JsonNode root;
        try {
            root = J5JsonParserSupport.readTree(fixture.rawPayload());
        }
        catch (JacksonException exception) {
            return failed(
                    J5ParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    J5ParseProblem.Code.INVALID_JSON,
                    "$",
                    "The payload is not valid unambiguous JSON");
        }

        List<J5ParseWarning> warnings = new ArrayList<>();
        List<J5ParseProblem> problems = new ArrayList<>();
        if (!J5JsonParserSupport.requiredObject(root, "$", problems)) {
            return incompatible(evidence, warnings, problems);
        }
        J5JsonParserSupport.warnUnknownFields(root, ROOT_FIELDS, "$", warnings);
        Long eventId = J5JsonParserSupport.requiredPositiveLong(
                root.get("eventId"),
                "$.eventId",
                problems);
        JsonNode statisticsNode = root.get("statistics");
        if (!J5JsonParserSupport.requiredArray(
                statisticsNode,
                "$.statistics",
                problems)) {
            return incompatible(evidence, warnings, problems);
        }

        List<EventStatisticMetric> metrics = new ArrayList<>();
        List<String> missingPaths = new ArrayList<>();
        int expectedSignals = 0;
        int presentSignals = 0;
        for (int periodIndex = 0; periodIndex < statisticsNode.size(); periodIndex++) {
            JsonNode periodNode = statisticsNode.get(periodIndex);
            String periodPath = "$.statistics[" + periodIndex + "]";
            if (!J5JsonParserSupport.requiredObject(periodNode, periodPath, problems)) {
                continue;
            }
            J5JsonParserSupport.warnUnknownFields(
                    periodNode,
                    PERIOD_FIELDS,
                    periodPath,
                    warnings);
            String period = J5JsonParserSupport.requiredText(
                    periodNode.get("period"),
                    periodPath + ".period",
                    32,
                    problems);
            JsonNode groupsNode = periodNode.get("groups");
            if (!J5JsonParserSupport.requiredArray(
                    groupsNode,
                    periodPath + ".groups",
                    problems)) {
                continue;
            }
            for (int groupIndex = 0; groupIndex < groupsNode.size(); groupIndex++) {
                JsonNode groupNode = groupsNode.get(groupIndex);
                String groupPath = periodPath + ".groups[" + groupIndex + "]";
                if (!J5JsonParserSupport.requiredObject(groupNode, groupPath, problems)) {
                    continue;
                }
                J5JsonParserSupport.warnUnknownFields(
                        groupNode,
                        GROUP_FIELDS,
                        groupPath,
                        warnings);
                String groupName = J5JsonParserSupport.requiredText(
                        groupNode.get("groupName"),
                        groupPath + ".groupName",
                        100,
                        problems);
                JsonNode itemsNode = groupNode.get("statisticsItems");
                if (!J5JsonParserSupport.requiredArray(
                        itemsNode,
                        groupPath + ".statisticsItems",
                        problems)) {
                    continue;
                }
                for (int itemIndex = 0; itemIndex < itemsNode.size(); itemIndex++) {
                    JsonNode itemNode = itemsNode.get(itemIndex);
                    String itemPath = groupPath + ".statisticsItems[" + itemIndex + "]";
                    if (!J5JsonParserSupport.requiredObject(itemNode, itemPath, problems)) {
                        continue;
                    }
                    J5JsonParserSupport.warnUnknownFields(
                            itemNode,
                            ITEM_FIELDS,
                            itemPath,
                            warnings);
                    String key = J5JsonParserSupport.requiredText(
                            itemNode.get("key"),
                            itemPath + ".key",
                            100,
                            problems);
                    String name = J5JsonParserSupport.requiredText(
                            itemNode.get("name"),
                            itemPath + ".name",
                            150,
                            problems);
                    Optional<String> home = J5JsonParserSupport.optionalScalarText(
                            itemNode.get("home"),
                            itemPath + ".home",
                            100,
                            warnings,
                            problems);
                    Optional<String> away = J5JsonParserSupport.optionalScalarText(
                            itemNode.get("away"),
                            itemPath + ".away",
                            100,
                            warnings,
                            problems);
                    expectedSignals += 2;
                    if (home.isPresent()) {
                        presentSignals++;
                    }
                    else {
                        missingPaths.add(itemPath + ".home");
                    }
                    if (away.isPresent()) {
                        presentSignals++;
                    }
                    else {
                        missingPaths.add(itemPath + ".away");
                    }
                    if (period != null && groupName != null && key != null && name != null) {
                        metrics.add(new EventStatisticMetric(
                                period,
                                groupName,
                                key,
                                name,
                                home,
                                away));
                    }
                }
            }
        }

        if (!problems.isEmpty() || eventId == null) {
            return incompatible(evidence, warnings, problems);
        }
        J5CompletenessReport completeness = metrics.isEmpty()
                ? J5CompletenessReport.emptyValid()
                : J5CompletenessReport.measured(
                        presentSignals,
                        expectedSignals,
                        missingPaths);
        return J5ParseResult.parsed(
                evidence,
                new EventStatistics(eventId, metrics),
                completeness,
                warnings);
    }

    private static J5ParseEvidence evidence(LoadedFixture fixture) {
        return new J5ParseEvidence(
                fixture.manifest().fixtureId(),
                fixture.manifest().endpointType(),
                fixture.rawSha256(),
                fixture.canonicalJsonSha256(),
                fixture.manifest().recordedAt(),
                PARSER_VERSION);
    }

    private static J5ParseResult<EventStatistics> preflight(
            LoadedFixture fixture,
            J5ParseEvidence evidence) {
        if (fixture.manifest().endpointType() != SofascoreEndpointType.EVENT_STATISTICS) {
            return failed(
                    J5ParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    J5ParseProblem.Code.UNSUPPORTED_ENDPOINT,
                    "$",
                    "The statistics parser accepts EVENT_STATISTICS fixtures only");
        }
        if (!PARSER_VERSION.equals(fixture.manifest().parserVersion())) {
            return failed(
                    J5ParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    J5ParseProblem.Code.UNSUPPORTED_PARSER_VERSION,
                    "$",
                    "The fixture parser version does not match event-statistics-v1");
        }
        if (fixture.contentKind() != FixtureContentKind.JSON) {
            return failed(
                    J5ParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    J5ParseProblem.Code.UNEXPECTED_CONTENT_KIND,
                    "$",
                    "The statistics parser accepts JSON content only");
        }
        return null;
    }

    private static J5ParseResult<EventStatistics> incompatible(
            J5ParseEvidence evidence,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return J5ParseResult.failed(
                J5ParseStatus.SCHEMA_INCOMPATIBLE,
                evidence,
                warnings,
                problems);
    }

    private static J5ParseResult<EventStatistics> failed(
            J5ParseStatus status,
            J5ParseEvidence evidence,
            J5ParseProblem.Code code,
            String path,
            String message) {
        return J5ParseResult.failed(
                status,
                evidence,
                List.of(),
                List.of(J5JsonParserSupport.problem(code, path, message)));
    }
}
