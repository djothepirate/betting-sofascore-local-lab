package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventSeason;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventVenue;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.fixture.FixtureContentKind;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class EventDetailsV1Parser {

    public static final String PARSER_VERSION = "event-details-v1";

    private static final Set<String> ROOT_FIELDS = Set.of(
            "id",
            "startTimestamp",
            "homeTeam",
            "awayTeam",
            "status",
            "tournament",
            "venue",
            "season",
            "round");
    private static final Set<String> TEAM_FIELDS = Set.of("id", "name");
    private static final Set<String> STATUS_FIELDS = Set.of("type", "description");
    private static final Set<String> TOURNAMENT_FIELDS = Set.of("id", "name");
    private static final Set<String> VENUE_FIELDS = Set.of("id", "name", "city");
    private static final Set<String> SEASON_FIELDS = Set.of("id", "name");

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public EventDetailsParseResult parse(LoadedFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        EventDetailsParseEvidence evidence = new EventDetailsParseEvidence(
                fixture.manifest().fixtureId(),
                fixture.rawSha256(),
                fixture.canonicalJsonSha256(),
                fixture.manifest().recordedAt(),
                PARSER_VERSION);

        if (fixture.manifest().endpointType() != SofascoreEndpointType.EVENT_DETAILS) {
            return failed(
                    EventDetailsParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    EventDetailsParseProblem.Code.UNSUPPORTED_ENDPOINT,
                    "$",
                    "The event-details-v1 parser accepts EVENT_DETAILS fixtures only");
        }
        if (!PARSER_VERSION.equals(fixture.manifest().parserVersion())) {
            return failed(
                    EventDetailsParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    EventDetailsParseProblem.Code.UNSUPPORTED_PARSER_VERSION,
                    "$",
                    "The fixture parser version does not match event-details-v1");
        }
        if (fixture.contentKind() != FixtureContentKind.JSON) {
            return failed(
                    EventDetailsParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    EventDetailsParseProblem.Code.UNEXPECTED_CONTENT_KIND,
                    "$",
                    "The event-details-v1 parser accepts JSON content only");
        }

        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(fixture.rawPayload());
        }
        catch (JacksonException exception) {
            return failed(
                    EventDetailsParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    EventDetailsParseProblem.Code.INVALID_JSON,
                    "$",
                    "The payload is not valid unambiguous JSON");
        }

        List<EventDetailsParseWarning> warnings = new ArrayList<>();
        List<EventDetailsParseProblem> problems = new ArrayList<>();
        if (root == null || !root.isObject()) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.TYPE_MISMATCH,
                    "$",
                    "The root value must be an object"));
            return incompatible(evidence, warnings, problems);
        }

        warnUnknownFields(root, ROOT_FIELDS, "$", warnings);
        Long providerEventId = requiredPositiveLong(root.get("id"), "$.id", problems);
        Instant startsAt = requiredInstant(
                root.get("startTimestamp"),
                "$.startTimestamp",
                problems);
        ScheduledTeam homeTeam = requiredTeam(
                root.get("homeTeam"),
                "$.homeTeam",
                warnings,
                problems);
        ScheduledTeam awayTeam = requiredTeam(
                root.get("awayTeam"),
                "$.awayTeam",
                warnings,
                problems);
        ScheduledEventStatus status = requiredStatus(
                root.get("status"),
                "$.status",
                warnings,
                problems);
        Optional<ScheduledTournament> tournament = optionalTournament(
                root.get("tournament"),
                "$.tournament",
                warnings,
                problems);
        Optional<EventVenue> venue = optionalVenue(
                root.get("venue"),
                "$.venue",
                warnings,
                problems);
        Optional<EventSeason> season = optionalSeason(
                root.get("season"),
                "$.season",
                warnings,
                problems);
        Optional<String> round = optionalText(
                root.get("round"),
                "$.round",
                64,
                warnings,
                problems);

        if (!problems.isEmpty()) {
            return incompatible(evidence, warnings, problems);
        }

        return EventDetailsParseResult.parsed(
                evidence,
                new EventDetails(
                        providerEventId,
                        startsAt,
                        homeTeam,
                        awayTeam,
                        status,
                        tournament,
                        venue,
                        season,
                        round),
                warnings);
    }

    private static ScheduledTeam requiredTeam(
            JsonNode node,
            String path,
            List<EventDetailsParseWarning> warnings,
            List<EventDetailsParseProblem> problems) {
        if (!requiredObject(node, path, problems)) {
            return null;
        }
        warnUnknownFields(node, TEAM_FIELDS, path, warnings);
        Long id = requiredPositiveLong(node.get("id"), path + ".id", problems);
        String name = requiredText(node.get("name"), path + ".name", 200, problems);
        return id == null || name == null ? null : new ScheduledTeam(id, name);
    }

    private static ScheduledEventStatus requiredStatus(
            JsonNode node,
            String path,
            List<EventDetailsParseWarning> warnings,
            List<EventDetailsParseProblem> problems) {
        if (!requiredObject(node, path, problems)) {
            return null;
        }
        warnUnknownFields(node, STATUS_FIELDS, path, warnings);
        String type = requiredText(node.get("type"), path + ".type", 64, problems);
        Optional<String> description = optionalText(
                node.get("description"),
                path + ".description",
                200,
                warnings,
                problems);
        return type == null ? null : new ScheduledEventStatus(type, description);
    }

    private static Optional<ScheduledTournament> optionalTournament(
            JsonNode node,
            String path,
            List<EventDetailsParseWarning> warnings,
            List<EventDetailsParseProblem> problems) {
        if (isMissing(node, path, warnings)) {
            return Optional.empty();
        }
        if (!object(node, path, problems)) {
            return Optional.empty();
        }
        warnUnknownFields(node, TOURNAMENT_FIELDS, path, warnings);
        Long id = requiredPositiveLong(node.get("id"), path + ".id", problems);
        String name = requiredText(node.get("name"), path + ".name", 200, problems);
        return id == null || name == null
                ? Optional.empty()
                : Optional.of(new ScheduledTournament(id, name));
    }

    private static Optional<EventVenue> optionalVenue(
            JsonNode node,
            String path,
            List<EventDetailsParseWarning> warnings,
            List<EventDetailsParseProblem> problems) {
        if (isMissing(node, path, warnings)) {
            return Optional.empty();
        }
        if (!object(node, path, problems)) {
            return Optional.empty();
        }
        warnUnknownFields(node, VENUE_FIELDS, path, warnings);
        Long id = requiredPositiveLong(node.get("id"), path + ".id", problems);
        String name = requiredText(node.get("name"), path + ".name", 200, problems);
        Optional<String> city = optionalText(
                node.get("city"),
                path + ".city",
                200,
                warnings,
                problems);
        return id == null || name == null
                ? Optional.empty()
                : Optional.of(new EventVenue(id, name, city));
    }

    private static Optional<EventSeason> optionalSeason(
            JsonNode node,
            String path,
            List<EventDetailsParseWarning> warnings,
            List<EventDetailsParseProblem> problems) {
        if (isMissing(node, path, warnings)) {
            return Optional.empty();
        }
        if (!object(node, path, problems)) {
            return Optional.empty();
        }
        warnUnknownFields(node, SEASON_FIELDS, path, warnings);
        Long id = requiredPositiveLong(node.get("id"), path + ".id", problems);
        String name = requiredText(node.get("name"), path + ".name", 100, problems);
        return id == null || name == null
                ? Optional.empty()
                : Optional.of(new EventSeason(id, name));
    }

    private static boolean requiredObject(
            JsonNode node,
            String path,
            List<EventDetailsParseProblem> problems) {
        if (node == null || node.isNull()) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required object is missing"));
            return false;
        }
        return object(node, path, problems);
    }

    private static boolean object(
            JsonNode node,
            String path,
            List<EventDetailsParseProblem> problems) {
        if (!node.isObject()) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field must be an object"));
            return false;
        }
        return true;
    }

    private static Long requiredPositiveLong(
            JsonNode node,
            String path,
            List<EventDetailsParseProblem> problems) {
        if (node == null || node.isNull()) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required integer field is missing"));
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field must be a 64-bit integer"));
            return null;
        }
        long value = node.longValue();
        if (value < 1) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Identifier or timestamp must be positive"));
            return null;
        }
        return value;
    }

    private static Instant requiredInstant(
            JsonNode node,
            String path,
            List<EventDetailsParseProblem> problems) {
        Long epochSecond = requiredPositiveLong(node, path, problems);
        if (epochSecond == null) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(epochSecond);
        }
        catch (DateTimeException exception) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Timestamp is outside the supported Instant range"));
            return null;
        }
    }

    private static String requiredText(
            JsonNode node,
            String path,
            int maximumLength,
            List<EventDetailsParseProblem> problems) {
        if (node == null || node.isNull()) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required text field is missing"));
            return null;
        }
        if (!node.isString()) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field must be text"));
            return null;
        }
        String value = node.stringValue().trim();
        if (value.isEmpty() || value.length() > maximumLength) {
            problems.add(problem(
                    EventDetailsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Text field is blank or exceeds its local bound"));
            return null;
        }
        return value;
    }

    private static Optional<String> optionalText(
            JsonNode node,
            String path,
            int maximumLength,
            List<EventDetailsParseWarning> warnings,
            List<EventDetailsParseProblem> problems) {
        if (isMissing(node, path, warnings)) {
            return Optional.empty();
        }
        String value = requiredText(node, path, maximumLength, problems);
        return Optional.ofNullable(value);
    }

    private static boolean isMissing(
            JsonNode node,
            String path,
            List<EventDetailsParseWarning> warnings) {
        if (node == null || node.isNull()) {
            warnings.add(warning(
                    EventDetailsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                    path,
                    "Optional event-detail field is absent"));
            return true;
        }
        return false;
    }

    private static void warnUnknownFields(
            JsonNode node,
            Set<String> knownFields,
            String path,
            List<EventDetailsParseWarning> warnings) {
        node.properties().stream()
                .map(entry -> entry.getKey())
                .filter(field -> !knownFields.contains(field))
                .sorted()
                .forEach(field -> warnings.add(warning(
                        EventDetailsParseWarning.Code.UNKNOWN_FIELD,
                        path + "." + field,
                        "Unknown field ignored by event-details-v1")));
    }

    private static EventDetailsParseResult incompatible(
            EventDetailsParseEvidence evidence,
            List<EventDetailsParseWarning> warnings,
            List<EventDetailsParseProblem> problems) {
        return EventDetailsParseResult.failed(
                EventDetailsParseStatus.SCHEMA_INCOMPATIBLE,
                evidence,
                warnings,
                problems);
    }

    private static EventDetailsParseResult failed(
            EventDetailsParseStatus status,
            EventDetailsParseEvidence evidence,
            EventDetailsParseProblem.Code code,
            String path,
            String message) {
        return EventDetailsParseResult.failed(
                status,
                evidence,
                List.of(),
                List.of(problem(code, path, message)));
    }

    private static EventDetailsParseWarning warning(
            EventDetailsParseWarning.Code code,
            String path,
            String message) {
        return new EventDetailsParseWarning(code, path, message);
    }

    private static EventDetailsParseProblem problem(
            EventDetailsParseProblem.Code code,
            String path,
            String message) {
        return new EventDetailsParseProblem(code, path, message);
    }
}
