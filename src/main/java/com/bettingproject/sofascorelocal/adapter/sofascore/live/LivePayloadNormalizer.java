package com.bettingproject.sofascorelocal.adapter.sofascore.live;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV15Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV2Parser;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.security.Sha256;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure adapter for WO-058. Historical normalized families keep their historical parser version;
 * score/phase extensions have independent versions and are never inferred by the scheduler.
 * The score field mapping is synthetic-contract qualified, not claimed as provider qualified.
 */
public class LivePayloadNormalizer {

    public static final String SCORE_PROJECTION_VERSION = "j4-live-score-v1";
    public static final String SIGNAL_PROJECTION_VERSION = "j5-live-signals-v1";
    public static final String FAMILY_PROJECTION_VERSION = "live-family-v1";

    private static final List<String> SCORE_FIELDS = List.of(
            "current", "display", "normaltime", "period1", "period2",
            "overtime", "extra1", "extra2", "penalties");
    private static final List<String> PHASE_FIELDS = List.of(
            "time", "addedTime", "length", "text", "isLive", "period",
            "timeSeconds", "periodTimeSeconds", "id");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final EventDetailsV2Parser detailsParser = new EventDetailsV2Parser();
    private final EventStatisticsV2Parser statisticsParser = new EventStatisticsV2Parser();
    private final EventIncidentsV15Parser incidentsParser = new EventIncidentsV15Parser();
    private final EventLineupsV2Parser lineupsParser = new EventLineupsV2Parser();

    public LiveNormalizedPayload normalize(
            long eventId,
            SofascoreEndpointType endpoint,
            long snapshotId,
            long occurrenceId,
            RawPayloadEvidence payload,
            Instant receivedAt) {
        JsonNode root;
        try {
            root = JSON.readTree(payload.bytes());
        }
        catch (JacksonException exception) {
            return failed(endpoint, LiveNormalizedPayload.Status.UNEXPECTED_CONTENT, "INVALID_JSON");
        }
        String identityFailure = identityFailure(root, eventId, endpoint);
        if (identityFailure != null) {
            return failed(endpoint, LiveNormalizedPayload.Status.IDENTITY_MISMATCH, identityFailure);
        }
        try {
            Map<String, Object> projection = projectionBase(endpoint);
            if (endpoint == SofascoreEndpointType.EVENT_DETAILS) {
                var parsed = detailsParser.parse(snapshotId, payload, receivedAt);
                if (parsed.status() != EventDetailsParseStatus.PARSED) {
                    return failed(endpoint,
                            parsed.status() == EventDetailsParseStatus.UNEXPECTED_CONTENT
                                    ? LiveNormalizedPayload.Status.UNEXPECTED_CONTENT
                                    : LiveNormalizedPayload.Status.SCHEMA_INCOMPATIBLE,
                            parsed.status().name());
                }
                JsonNode event = root.get("event");
                projection.put("sportStatus", parsed.details().orElseThrow().status().type());
                projection.put("homeScore", scoreSide(event.get("homeScore")));
                projection.put("awayScore", scoreSide(event.get("awayScore")));
                // Period descriptors remain source observations; no score or clock is calculated.
                projection.put("period", optionalScalar(event.get("period"), "period"));
                projection.put("currentPeriod", optionalScalar(event.get("currentPeriod"), "currentPeriod"));
                return new LiveNormalizedPayload(
                        LiveNormalizedPayload.Status.PARSED, "PARSED", parserVersion(endpoint),
                        projectionVersion(endpoint), JSON.writeValueAsString(projection),
                        parsed.details(), Optional.empty(), Optional.empty(), List.of());
            }
            J5ParseResult<? extends J5EventData> parsed = switch (endpoint) {
                case EVENT_STATISTICS -> statisticsParser.parse(snapshotId, eventId, payload, receivedAt);
                case EVENT_INCIDENTS -> incidentsParser.parse(snapshotId, eventId, payload, receivedAt);
                case EVENT_LINEUPS -> lineupsParser.parse(snapshotId, eventId, payload, receivedAt);
                default -> throw new IllegalArgumentException("unsupported live endpoint");
            };
            if (parsed.status() != J5ParseStatus.PARSED) {
                return failed(endpoint,
                        parsed.status() == J5ParseStatus.UNEXPECTED_CONTENT
                                ? LiveNormalizedPayload.Status.UNEXPECTED_CONTENT
                                : LiveNormalizedPayload.Status.SCHEMA_INCOMPATIBLE,
                        parsed.status().name());
            }
            List<LivePhaseSignal> signals = new ArrayList<>();
            if (endpoint == SofascoreEndpointType.EVENT_INCIDENTS) {
                projection.put("phaseObservations", phaseObservations(root.get("incidents"), eventId, signals));
            }
            projection.put("completeness", parsed.completeness().orElseThrow().status().name());
            return new LiveNormalizedPayload(
                    LiveNormalizedPayload.Status.PARSED, "PARSED", parserVersion(endpoint),
                    projectionVersion(endpoint), JSON.writeValueAsString(projection),
                    Optional.empty(), Optional.of(parsed.data().orElseThrow()),
                    parsed.completeness(), signals.stream().distinct().toList());
        }
        catch (ProjectionSchemaException exception) {
            return failed(endpoint, LiveNormalizedPayload.Status.SCHEMA_INCOMPATIBLE, exception.code);
        }
    }

    public static String parserVersion(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_DETAILS -> EventDetailsV2Parser.PARSER_VERSION;
            case EVENT_STATISTICS -> EventStatisticsV2Parser.PARSER_VERSION;
            case EVENT_INCIDENTS -> EventIncidentsV15Parser.PARSER_VERSION;
            case EVENT_LINEUPS -> EventLineupsV2Parser.PARSER_VERSION;
            default -> throw new IllegalArgumentException("unsupported live endpoint");
        };
    }

    public static String projectionVersion(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_DETAILS -> SCORE_PROJECTION_VERSION;
            case EVENT_INCIDENTS -> SIGNAL_PROJECTION_VERSION;
            case EVENT_STATISTICS, EVENT_LINEUPS -> FAMILY_PROJECTION_VERSION;
            default -> throw new IllegalArgumentException("unsupported live endpoint");
        };
    }

    private static String identityFailure(JsonNode root, long eventId, SofascoreEndpointType endpoint) {
        JsonNode event = root != null && root.isObject() ? root.get("event") : null;
        JsonNode nestedId = event != null && event.isObject() ? event.get("id") : null;
        if (endpoint == SofascoreEndpointType.EVENT_DETAILS && nestedId == null) {
            return "IDENTITY_NOT_VERIFIABLE";
        }
        if (nestedId != null && !matchesId(nestedId, eventId)) {
            return "EVENT_ID_MISMATCH";
        }
        JsonNode rootEventId = root != null && root.isObject() ? root.get("eventId") : null;
        if (rootEventId != null && !matchesId(rootEventId, eventId)) {
            return "EVENT_ID_MISMATCH";
        }
        return null;
    }

    private static boolean matchesId(JsonNode node, long expected) {
        return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() == expected;
    }

    private static Map<String, Object> projectionBase(SofascoreEndpointType endpoint) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("version", projectionVersion(endpoint));
        projection.put("parserVersion", parserVersion(endpoint));
        // Reception metadata belongs to the enclosing occurrence/result. Keeping it out of this
        // content projection makes identical receptions distinguishable from content revisions.
        return projection;
    }

    private static Map<String, Object> scoreSide(JsonNode node) {
        if (node == null || node.isNull()) {
            return presence(node, null);
        }
        if (!node.isObject()) {
            throw new ProjectionSchemaException("LIVE_SCORE_TYPE_MISMATCH");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : SCORE_FIELDS) {
            JsonNode score = node.get(field);
            if (score != null && !score.isNull()
                    && (!score.isIntegralNumber() || !score.canConvertToInt()
                            || score.intValue() < 0 || score.intValue() > 999)) {
                throw new ProjectionSchemaException("LIVE_SCORE_VALUE_INCOMPATIBLE");
            }
            values.put(field, presence(score, score == null || score.isNull() ? null : score.intValue()));
        }
        return presence(node, values);
    }

    private static List<Map<String, Object>> phaseObservations(
            JsonNode items, long eventId, List<LivePhaseSignal> signals) {
        List<Map<String, Object>> phases = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            String type = item.get("incidentType").stringValue();
            if (!"period".equals(type) && !"injuryTime".equals(type)) {
                continue;
            }
            Map<String, Object> phase = new LinkedHashMap<>();
            phase.put("sourcePath", "$.incidents[" + index + "]");
            phase.put("incidentType", type);
            for (String field : PHASE_FIELDS) {
                phase.put(field, optionalScalar(item.get(field), field));
            }
            String text = text(item.get("text"));
            Integer minute = integer(item.get("time"));
            String phaseIdentity = eventId + "|" + type + "|" + minute + "|" + text
                    + "|" + text(item.get("period"));
            String key = Sha256.hex(phaseIdentity.getBytes(StandardCharsets.UTF_8));
            phase.put("signalKey", key);
            LivePhaseSignal.Kind kind = signalKind(type, minute, text);
            if (kind != null) {
                signals.add(new LivePhaseSignal(key, kind));
                phase.put("check", kind.name());
            }
            // Revision excludes JSON index; a changed announced length is not a new signal.
            Map<String, Object> revision = new LinkedHashMap<>(phase);
            revision.remove("sourcePath");
            phase.put("revisionSha256", Sha256.hex(
                    JSON.writeValueAsBytes(revision)));
            phases.add(phase);
        }
        return phases;
    }

    private static LivePhaseSignal.Kind signalKind(String type, Integer minute, String text) {
        if ("injuryTime".equals(type)) {
            if (Integer.valueOf(45).equals(minute) || Integer.valueOf(105).equals(minute)) {
                return LivePhaseSignal.Kind.PERIOD_CHECK;
            }
            if (minute != null && minute >= 90) {
                return LivePhaseSignal.Kind.FINISH_CHECK;
            }
            return LivePhaseSignal.Kind.PERIOD_CHECK;
        }
        if ("HT".equals(text) || ("ET".equals(text) && Integer.valueOf(105).equals(minute))) {
            return LivePhaseSignal.Kind.PERIOD_CHECK;
        }
        if ("FT".equals(text) || "ET".equals(text) || "PEN".equals(text)) {
            return LivePhaseSignal.Kind.FINISH_CHECK;
        }
        return null;
    }

    private static Map<String, Object> optionalScalar(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return presence(node, null);
        }
        Object value;
        if (node.isString() && node.stringValue().length() <= 100
                && node.stringValue().chars().noneMatch(Character::isISOControl)) {
            value = node.stringValue();
        }
        else if (node.isBoolean()) {
            value = node.booleanValue();
        }
        else if (node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0) {
            value = node.longValue();
        }
        else {
            throw new ProjectionSchemaException("LIVE_PHASE_FIELD_INCOMPATIBLE");
        }
        // Fields used as time/identity/live indicators must not silently accept another scalar type.
        if ((List.of("time", "addedTime", "length", "timeSeconds", "periodTimeSeconds", "id")
                .contains(field) && !(value instanceof Long))
                || ("isLive".equals(field) && !(value instanceof Boolean))) {
            throw new ProjectionSchemaException("LIVE_PHASE_FIELD_INCOMPATIBLE");
        }
        return presence(node, value);
    }

    private static Map<String, Object> presence(JsonNode node, Object value) {
        if (node == null) {
            return Map.of("presence", "ABSENT");
        }
        if (node.isNull()) {
            return Map.of("presence", "NULL");
        }
        return Map.of("presence", "VALUE", "value", value);
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : "";
    }

    private static Integer integer(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToInt() ? node.intValue() : null;
    }

    private static LiveNormalizedPayload failed(
            SofascoreEndpointType endpoint, LiveNormalizedPayload.Status status, String code) {
        return new LiveNormalizedPayload(status, code, parserVersion(endpoint), projectionVersion(endpoint),
                "{}", Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private static final class ProjectionSchemaException extends RuntimeException {
        private final String code;

        private ProjectionSchemaException(String code) {
            super(code);
            this.code = code;
        }
    }
}
