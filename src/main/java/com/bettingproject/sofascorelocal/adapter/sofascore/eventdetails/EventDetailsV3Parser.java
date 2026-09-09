package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Adds optional provider award/display scores while preserving the immutable V2 base parser. */
public final class EventDetailsV3Parser {

    public static final String PARSER_VERSION = "event-details-v3";
    private static final int MAXIMUM_WARNINGS = 128;
    private static final Set<String> RECOGNIZED_V2_WARNING_PATHS = Set.of(
            "$.event.isAwarded", "$.event.homeScore", "$.event.awayScore");
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final EventDetailsV2Parser baseParser = new EventDetailsV2Parser();

    public EventDetailsParseResult parse(long snapshotId, RawPayloadEvidence payload, Instant receivedAt) {
        return parseWithPreferredRound(snapshotId, payload, receivedAt, Optional.empty());
    }

    // Only V4 uses the preferred-name path. All ordinary V3 calls retain legacy round validation.
    EventDetailsParseResult parseWithPreferredRound(long snapshotId, RawPayloadEvidence payload,
            Instant receivedAt, Optional<String> preferredRound) {
        EventDetailsParseResult base = baseParser.parseWithPreferredRound(snapshotId, payload, receivedAt, preferredRound);
        EventDetailsParseEvidence original = base.evidence();
        EventDetailsParseEvidence evidence = new EventDetailsParseEvidence(
                original.sourceReference(), original.rawSha256(), original.canonicalJsonSha256(),
                original.recordedAt(), PARSER_VERSION);
        List<EventDetailsParseWarning> warnings = new ArrayList<>(base.warnings().stream()
                .filter(warning -> warning.code() != EventDetailsParseWarning.Code.UNKNOWN_FIELD
                        || !RECOGNIZED_V2_WARNING_PATHS.contains(warning.path()))
                .toList());
        if (base.status() != EventDetailsParseStatus.PARSED) {
            return EventDetailsParseResult.failed(base.status(), evidence, warnings, base.problems());
        }

        // V2 has already checked the same immutable bytes, including duplicates and trailing tokens.
        JsonNode event = JSON_MAPPER.readTree(payload.bytes()).get("event");
        List<EventDetailsParseProblem> problems = new ArrayList<>();
        Optional<Boolean> awarded = optionalAwarded(event.get("isAwarded"), problems);
        Optional<Integer> homeScore = optionalDisplayScore(
                event.get("homeScore"), "$.event.homeScore", warnings, problems);
        Optional<Integer> awayScore = optionalDisplayScore(
                event.get("awayScore"), "$.event.awayScore", warnings, problems);
        if (!problems.isEmpty()) {
            return EventDetailsParseResult.failed(
                    EventDetailsParseStatus.SCHEMA_INCOMPATIBLE, evidence, warnings, problems);
        }
        EventDetails details = base.details().orElseThrow();
        return EventDetailsParseResult.parsed(evidence, new EventDetails(
                details.providerEventId(), details.startsAt(), details.homeTeam(), details.awayTeam(),
                details.status(), details.tournament(), details.venue(), details.season(), details.round(),
                awarded, homeScore, awayScore), warnings);
    }

    private static Optional<Boolean> optionalAwarded(
            JsonNode node, List<EventDetailsParseProblem> problems) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isBoolean()) {
            problems.add(new EventDetailsParseProblem(EventDetailsParseProblem.Code.TYPE_MISMATCH,
                    "$.event.isAwarded", "The optional award flag must be a boolean"));
            return Optional.empty();
        }
        return Optional.of(node.booleanValue());
    }

    private static Optional<Integer> optionalDisplayScore(
            JsonNode node, String path, List<EventDetailsParseWarning> warnings,
            List<EventDetailsParseProblem> problems) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isObject()) {
            problems.add(new EventDetailsParseProblem(EventDetailsParseProblem.Code.TYPE_MISMATCH,
                    path, "The optional score must be an object"));
            return Optional.empty();
        }
        node.properties().stream().map(property -> property.getKey())
                .filter(field -> !field.equals("display")).sorted()
                .limit(Math.max(0, MAXIMUM_WARNINGS - warnings.size()))
                .forEach(field -> warnings.add(new EventDetailsParseWarning(
                        EventDetailsParseWarning.Code.UNKNOWN_FIELD, path + "." + field,
                        "Unknown field ignored by event-details-v3")));
        JsonNode display = node.get("display");
        if (display == null || display.isNull()) {
            return Optional.empty();
        }
        if (!display.isIntegralNumber()) {
            problems.add(new EventDetailsParseProblem(EventDetailsParseProblem.Code.TYPE_MISMATCH,
                    path + ".display", "The optional displayed score must be an integer"));
            return Optional.empty();
        }
        if (!display.canConvertToInt() || display.intValue() < 0 || display.intValue() > 999) {
            problems.add(new EventDetailsParseProblem(EventDetailsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path + ".display", "The optional displayed score must be between 0 and 999"));
            return Optional.empty();
        }
        return Optional.of(display.intValue());
    }
}
