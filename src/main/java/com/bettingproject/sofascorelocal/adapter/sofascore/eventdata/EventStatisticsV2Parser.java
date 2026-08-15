package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Provider-shape parser. The event id comes only from the guarded request. */
public final class EventStatisticsV2Parser {

    public static final String PARSER_VERSION = "event-statistics-v2";
    private static final Set<String> ROOT_FIELDS = Set.of("statistics");
    private static final Set<String> PERIOD_FIELDS = Set.of("period", "groups");
    private static final Set<String> GROUP_FIELDS = Set.of("groupName", "statisticsItems");
    private static final Set<String> ITEM_FIELDS = Set.of(
            "key", "name", "home", "away", "compareCode", "statisticsType",
            "valueType", "homeValue", "awayValue", "renderType");

    public J5ParseResult<EventStatistics> parse(
            long snapshotId,
            long expectedEventId,
            RawPayloadEvidence payload,
            Instant receivedAt) {
        J5EventDataProviderRequest.requireEventId(expectedEventId);
        J5ParseEvidence evidence = evidence(snapshotId, payload, receivedAt);
        JsonNode root;
        try {
            root = J5JsonParserSupport.readTree(payload.bytes());
        }
        catch (JacksonException exception) {
            return failed(J5ParseStatus.UNEXPECTED_CONTENT, evidence,
                    J5ParseProblem.Code.INVALID_JSON, "$",
                    "The payload is not valid unambiguous JSON");
        }
        List<J5ParseWarning> warnings = new ArrayList<>();
        List<J5ParseProblem> problems = new ArrayList<>();
        if (!J5JsonParserSupport.requiredObject(root, "$", problems)) {
            return incompatible(evidence, warnings, problems);
        }
        J5JsonParserSupport.warnUnknownFields(root, ROOT_FIELDS, "$", warnings);
        JsonNode periods = root.get("statistics");
        if (!J5JsonParserSupport.requiredArray(periods, "$.statistics", problems)) {
            return incompatible(evidence, warnings, problems);
        }

        List<EventStatisticMetric> metrics = new ArrayList<>();
        Completeness completeness = new Completeness();
        for (int p = 0; p < periods.size(); p++) {
            JsonNode periodNode = periods.get(p);
            String periodPath = "$.statistics[" + p + "]";
            if (!J5JsonParserSupport.requiredObject(periodNode, periodPath, problems)) {
                continue;
            }
            J5JsonParserSupport.warnUnknownFields(periodNode, PERIOD_FIELDS, periodPath, warnings);
            String period = J5JsonParserSupport.requiredText(
                    periodNode.get("period"), periodPath + ".period", 32, problems);
            JsonNode groups = periodNode.get("groups");
            if (!J5JsonParserSupport.requiredArray(groups, periodPath + ".groups", problems)) {
                continue;
            }
            for (int g = 0; g < groups.size(); g++) {
                JsonNode groupNode = groups.get(g);
                String groupPath = periodPath + ".groups[" + g + "]";
                if (!J5JsonParserSupport.requiredObject(groupNode, groupPath, problems)) {
                    continue;
                }
                J5JsonParserSupport.warnUnknownFields(groupNode, GROUP_FIELDS, groupPath, warnings);
                String groupName = J5JsonParserSupport.requiredText(
                        groupNode.get("groupName"), groupPath + ".groupName", 100, problems);
                JsonNode items = groupNode.get("statisticsItems");
                if (!J5JsonParserSupport.requiredArray(
                        items, groupPath + ".statisticsItems", problems)) {
                    continue;
                }
                for (int i = 0; i < items.size(); i++) {
                    JsonNode item = items.get(i);
                    String itemPath = groupPath + ".statisticsItems[" + i + "]";
                    if (!J5JsonParserSupport.requiredObject(item, itemPath, problems)) {
                        continue;
                    }
                    J5JsonParserSupport.warnUnknownFields(item, ITEM_FIELDS, itemPath, warnings);
                    String key = J5JsonParserSupport.requiredText(
                            item.get("key"), itemPath + ".key", 100, problems);
                    String name = J5JsonParserSupport.requiredText(
                            item.get("name"), itemPath + ".name", 150, problems);
                    Optional<String> home = J5JsonParserSupport.optionalScalarText(
                            item.get("home"), itemPath + ".home", 100, warnings, problems);
                    Optional<String> away = J5JsonParserSupport.optionalScalarText(
                            item.get("away"), itemPath + ".away", 100, warnings, problems);
                    completeness.expect(itemPath + ".home", home.isPresent());
                    completeness.expect(itemPath + ".away", away.isPresent());
                    if (period != null && groupName != null && key != null && name != null) {
                        metrics.add(new EventStatisticMetric(
                                period, groupName, key, name, home, away));
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            return incompatible(evidence, warnings, problems);
        }
        J5CompletenessReport report = metrics.isEmpty()
                ? J5CompletenessReport.emptyValid()
                : completeness.report();
        return J5ParseResult.parsed(
                evidence, new EventStatistics(expectedEventId, metrics), report, warnings);
    }

    private static J5ParseEvidence evidence(
            long snapshotId, RawPayloadEvidence payload, Instant receivedAt) {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        return new J5ParseEvidence(
                "snapshot:" + snapshotId,
                SofascoreEndpointType.EVENT_STATISTICS,
                Objects.requireNonNull(payload, "payload").sha256(),
                Optional.empty(),
                Objects.requireNonNull(receivedAt, "receivedAt"),
                PARSER_VERSION);
    }

    private static J5ParseResult<EventStatistics> incompatible(
            J5ParseEvidence evidence,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return J5ParseResult.failed(
                J5ParseStatus.SCHEMA_INCOMPATIBLE, evidence, warnings, problems);
    }

    private static J5ParseResult<EventStatistics> failed(
            J5ParseStatus status,
            J5ParseEvidence evidence,
            J5ParseProblem.Code code,
            String path,
            String message) {
        return J5ParseResult.failed(status, evidence, List.of(),
                List.of(J5JsonParserSupport.problem(code, path, message)));
    }

    private static final class Completeness {
        private int expected;
        private int present;
        private final List<String> missing = new ArrayList<>();

        void expect(String path, boolean available) {
            expected++;
            if (available) {
                present++;
            }
            else {
                missing.add(path);
            }
        }

        J5CompletenessReport report() {
            return J5CompletenessReport.measured(present, expected, missing);
        }
    }
}
