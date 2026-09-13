package com.bettingproject.sofascorelocal.adapter.sofascore.live;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV17Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV4Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV4Parser;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts;
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

    /** V4 retains the scheduler controls and adds the reviewable J4 suspension reason. */
    public static final String SCORE_PROJECTION_VERSION = "j4-live-score-v4";
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

    private final EventDetailsV4Parser detailsParser = new EventDetailsV4Parser();
    private final EventStatisticsV2Parser statisticsParser = new EventStatisticsV2Parser();
    private final EventIncidentsV17Parser incidentsParser = new EventIncidentsV17Parser();
    private final EventLineupsV4Parser lineupsParser = new EventLineupsV4Parser();

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
                J4ControlProjection controls = j4Controls(event);
                projection.put("sportStatus", parsed.details().orElseThrow().status().type());
                projection.put("homeScore", scoreSide(event.get("homeScore")));
                projection.put("awayScore", scoreSide(event.get("awayScore")));
                projection.put("isAwarded", presence(event.get("isAwarded"),
                        parsed.details().orElseThrow().isAwarded().orElse(null)));
                // Period descriptors remain source observations; no score or clock is calculated.
                projection.put("period", optionalScalar(event.get("period"), "period"));
                projection.put("currentPeriod", optionalScalar(event.get("currentPeriod"), "currentPeriod"));
                projection.putAll(controls.projection());
                return new LiveNormalizedPayload(
                        LiveNormalizedPayload.Status.PARSED, "PARSED", parserVersion(endpoint),
                        projectionVersion(endpoint), JSON.writeValueAsString(projection),
                        parsed.details(), Optional.empty(), Optional.empty(), List.of(), Optional.of(controls.facts()));
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
            case EVENT_DETAILS -> EventDetailsV4Parser.PARSER_VERSION;
            case EVENT_STATISTICS -> EventStatisticsV2Parser.PARSER_VERSION;
            case EVENT_INCIDENTS -> EventIncidentsV17Parser.PARSER_VERSION;
            case EVENT_LINEUPS -> EventLineupsV4Parser.PARSER_VERSION;
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

    /**
     * Scheduler facts are parsed independently from the historical EventDetails
     * model so the old parser remains immutable.  The projection keeps each
     * value's JSON presence for later review, and the typed record makes a
     * permission decision without relying on stringly JSON in the scheduler.
     */
    private static J4ControlProjection j4Controls(JsonNode event) {
        if (event == null || !event.isObject()) {
            throw new ProjectionSchemaException("LIVE_J4_EVENT_TYPE_MISMATCH");
        }
        JsonNode status = event.get("status");
        if (status != null && !status.isNull() && !status.isObject()) {
            throw new ProjectionSchemaException("LIVE_J4_STATUS_TYPE_MISMATCH");
        }
        JsonNode tournament = event.get("tournament");
        if (tournament != null && !tournament.isNull() && !tournament.isObject()) {
            throw new ProjectionSchemaException("LIVE_J4_TOURNAMENT_TYPE_MISMATCH");
        }
        JsonNode uniqueTournament = tournament == null || tournament.isNull() ? null : tournament.get("uniqueTournament");
        if (uniqueTournament != null && !uniqueTournament.isNull() && !uniqueTournament.isObject()) {
            throw new ProjectionSchemaException("LIVE_J4_UNIQUE_TOURNAMENT_TYPE_MISMATCH");
        }

        JsonNode finalResultOnly = event.get("finalResultOnly");
        JsonNode detailId = event.get("detailId");
        JsonNode eventPlayerStatistics = event.get("hasEventPlayerStatistics");
        JsonNode tournamentPlayerStatistics = uniqueTournament == null || uniqueTournament.isNull()
                ? null : uniqueTournament.get("hasEventPlayerStatistics");
        JsonNode description = status == null || status.isNull() ? null : status.get("description");
        JsonNode statusReason = event.get("statusReason");

        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("finalResultOnly", booleanPresence(finalResultOnly, "LIVE_J4_FINAL_RESULT_ONLY_INCOMPATIBLE"));
        projection.put("detailId", detailIdPresence(detailId));
        projection.put("hasEventPlayerStatistics", booleanPresence(eventPlayerStatistics,
                "LIVE_J4_HAS_EVENT_PLAYER_STATISTICS_INCOMPATIBLE"));
        projection.put("tournamentHasEventPlayerStatistics", booleanPresence(tournamentPlayerStatistics,
                "LIVE_J4_TOURNAMENT_PLAYER_STATISTICS_INCOMPATIBLE"));
        projection.put("statusDescription", textPresence(description, "LIVE_J4_STATUS_DESCRIPTION_INCOMPATIBLE"));
        projection.put("statusReason", textPresence(statusReason, "LIVE_J4_STATUS_REASON_INCOMPATIBLE"));

        return new J4ControlProjection(new LiveJ4ControlFacts(
                booleanFact(finalResultOnly, "LIVE_J4_FINAL_RESULT_ONLY_INCOMPATIBLE"),
                detailIdFact(detailId),
                booleanFact(eventPlayerStatistics, "LIVE_J4_HAS_EVENT_PLAYER_STATISTICS_INCOMPATIBLE"),
                booleanFact(tournamentPlayerStatistics, "LIVE_J4_TOURNAMENT_PLAYER_STATISTICS_INCOMPATIBLE"),
                statusDescription(description), statusReason(statusReason)), Map.copyOf(projection));
    }

    private static Map<String, Object> booleanPresence(JsonNode node, String code) {
        LiveJ4ControlFacts.BooleanFact value = booleanFact(node, code);
        return switch (value) {
            case ABSENT -> Map.of("presence", "ABSENT");
            case NULL -> Map.of("presence", "NULL");
            case TRUE -> Map.of("presence", "VALUE", "value", true);
            case FALSE -> Map.of("presence", "VALUE", "value", false);
        };
    }

    private static LiveJ4ControlFacts.BooleanFact booleanFact(JsonNode node, String code) {
        if (node == null) return LiveJ4ControlFacts.BooleanFact.ABSENT;
        if (node.isNull()) return LiveJ4ControlFacts.BooleanFact.NULL;
        if (!node.isBoolean()) throw new ProjectionSchemaException(code);
        return node.booleanValue() ? LiveJ4ControlFacts.BooleanFact.TRUE : LiveJ4ControlFacts.BooleanFact.FALSE;
    }

    private static Map<String, Object> detailIdPresence(JsonNode node) {
        LiveJ4ControlFacts.DetailIdFact value = detailIdFact(node);
        return switch (value) {
            case ABSENT -> Map.of("presence", "ABSENT");
            case NULL -> Map.of("presence", "NULL");
            case ONE, OTHER -> Map.of("presence", "VALUE", "value", node.longValue());
        };
    }

    private static LiveJ4ControlFacts.DetailIdFact detailIdFact(JsonNode node) {
        if (node == null) return LiveJ4ControlFacts.DetailIdFact.ABSENT;
        if (node.isNull()) return LiveJ4ControlFacts.DetailIdFact.NULL;
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new ProjectionSchemaException("LIVE_J4_DETAIL_ID_INCOMPATIBLE");
        }
        return node.longValue() == 1L ? LiveJ4ControlFacts.DetailIdFact.ONE : LiveJ4ControlFacts.DetailIdFact.OTHER;
    }

    private static Map<String, Object> textPresence(JsonNode node, String code) {
        if (node == null) return Map.of("presence", "ABSENT");
        if (node.isNull()) return Map.of("presence", "NULL");
        return Map.of("presence", "VALUE", "value", checkedControlText(node, code));
    }

    private static LiveJ4ControlFacts.StatusDescription statusDescription(JsonNode node) {
        if (node == null) return LiveJ4ControlFacts.StatusDescription.ABSENT;
        if (node.isNull()) return LiveJ4ControlFacts.StatusDescription.NULL;
        String text = checkedControlText(node, "LIVE_J4_STATUS_DESCRIPTION_INCOMPATIBLE").trim().toLowerCase(java.util.Locale.ROOT);
        return switch (text) {
            case "halftime" -> LiveJ4ControlFacts.StatusDescription.HALFTIME;
            case "2nd half" -> LiveJ4ControlFacts.StatusDescription.SECOND_HALF;
            default -> LiveJ4ControlFacts.StatusDescription.OTHER;
        };
    }

    private static LiveJ4ControlFacts.TextFact statusReason(JsonNode node) {
        if (node == null) return LiveJ4ControlFacts.TextFact.absent();
        if (node.isNull()) return LiveJ4ControlFacts.TextFact.nullValue();
        return LiveJ4ControlFacts.TextFact.value(
                checkedControlText(node, "LIVE_J4_STATUS_REASON_INCOMPATIBLE"));
    }

    private static String checkedControlText(JsonNode node, String code) {
        if (!node.isString() || node.stringValue().isBlank() || node.stringValue().length() > 100
                || node.stringValue().chars().anyMatch(Character::isISOControl)) {
            throw new ProjectionSchemaException(code);
        }
        return node.stringValue();
    }

    private record J4ControlProjection(LiveJ4ControlFacts facts, Map<String, Object> projection) { }

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
