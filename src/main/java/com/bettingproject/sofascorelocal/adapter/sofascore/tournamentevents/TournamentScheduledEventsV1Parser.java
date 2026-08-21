package com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentScheduledEventCandidate;
import com.bettingproject.sofascorelocal.fixture.FixtureContentClassifier;
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

/**
 * Strict, network-independent parser for
 * {@code /unique-tournament/{id}/scheduled-events/{date}}.
 */
public final class TournamentScheduledEventsV1Parser {

    public static final String PARSER_VERSION = "tournament-scheduled-v1";
    public static final int MAXIMUM_WARNINGS = 64;

    private static final int MAXIMUM_NAME_LENGTH = 200;
    private static final int MAXIMUM_STATUS_TYPE_LENGTH = 64;
    private static final int MAXIMUM_STATUS_DESCRIPTION_LENGTH = 200;
    private static final int MAXIMUM_WARNING_PATH_LENGTH = 512;
    private static final long MAXIMUM_EPOCH_SECOND = 253_402_300_799L;

    private static final Set<String> ROOT_FIELDS = Set.of("events", "hasNextPage");
    private static final Set<String> EVENT_FIELDS = Set.of(
            "id",
            "startTimestamp",
            "homeTeam",
            "awayTeam",
            "status",
            "tournament");
    private static final Set<String> TEAM_FIELDS = Set.of("id", "name");
    private static final Set<String> STATUS_FIELDS = Set.of("type", "description");
    private static final Set<String> TOURNAMENT_FIELDS = Set.of(
            "id",
            "name",
            "uniqueTournament");
    private static final Set<String> UNIQUE_TOURNAMENT_FIELDS = Set.of("id", "name");

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public TournamentScheduledEventsParseResult parse(LoadedFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        TournamentScheduledEventsParseEvidence evidence = new TournamentScheduledEventsParseEvidence(
                fixture.manifest().fixtureId(),
                fixture.rawSha256(),
                fixture.canonicalJsonSha256(),
                fixture.manifest().recordedAt(),
                PARSER_VERSION);

        if (fixture.manifest().endpointType()
                != SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS) {
            return failed(
                    TournamentScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    TournamentScheduledEventsParseProblem.Code.UNSUPPORTED_ENDPOINT,
                    "$",
                    "The fixture endpoint is not TOURNAMENT_SCHEDULED_EVENTS");
        }
        return parseClassifiedPayload(
                fixture.rawPayload(),
                fixture.contentKind(),
                evidence);
    }

    /**
     * Parses already captured bytes. Callers retain responsibility for persisting
     * the raw evidence before invoking this method.
     */
    public TournamentScheduledEventsParseResult parse(
            byte[] rawPayload,
            String declaredContentType,
            TournamentScheduledEventsParseEvidence evidence) {
        Objects.requireNonNull(rawPayload, "rawPayload");
        Objects.requireNonNull(declaredContentType, "declaredContentType");
        Objects.requireNonNull(evidence, "evidence");
        if (!PARSER_VERSION.equals(evidence.parserVersion())) {
            throw new IllegalArgumentException(
                    "evidence.parserVersion must be " + PARSER_VERSION);
        }
        byte[] capturedPayload = rawPayload.clone();
        return parseClassifiedPayload(
                capturedPayload,
                FixtureContentClassifier.classify(capturedPayload, declaredContentType),
                evidence);
    }

    private static TournamentScheduledEventsParseResult parseClassifiedPayload(
            byte[] rawPayload,
            FixtureContentKind contentKind,
            TournamentScheduledEventsParseEvidence evidence) {
        if (contentKind != FixtureContentKind.JSON) {
            return failed(
                    TournamentScheduledEventsParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    TournamentScheduledEventsParseProblem.Code.UNEXPECTED_CONTENT_KIND,
                    "$",
                    "The tournament-scheduled-v1 parser accepts JSON content only");
        }

        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(rawPayload);
        }
        catch (JacksonException exception) {
            return failed(
                    TournamentScheduledEventsParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    TournamentScheduledEventsParseProblem.Code.INVALID_JSON,
                    "$",
                    "The payload is not valid unambiguous JSON");
        }

        WarningCollector warnings = new WarningCollector();
        List<TournamentScheduledEventsParseProblem> problems = new ArrayList<>();
        if (root == null || !root.isObject()) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    "$",
                    "The root value must be an object"));
            return incompatible(evidence, warnings, problems);
        }

        warnUnknownFields(root, ROOT_FIELDS, "$", warnings);
        validatePagination(root.get("hasNextPage"), problems);
        List<TournamentScheduledEventCandidate> candidates = parseEvents(
                root.get("events"),
                warnings,
                problems);

        if (!problems.isEmpty()) {
            return incompatible(evidence, warnings, problems);
        }
        return TournamentScheduledEventsParseResult.parsed(
                evidence,
                candidates,
                warnings.snapshot());
    }

    private static void validatePagination(
            JsonNode node,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (node == null) {
            return;
        }
        if (!node.isBoolean()) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    "$.hasNextPage",
                    "Field 'hasNextPage' must be a boolean when present"));
        }
        else if (node.booleanValue()) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.PAGINATION_UNSUPPORTED,
                    "$.hasNextPage",
                    "A paginated response is incompatible with this non-paginated endpoint"));
        }
    }

    private static List<TournamentScheduledEventCandidate> parseEvents(
            JsonNode eventsNode,
            WarningCollector warnings,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (eventsNode == null) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    "$.events",
                    "Required field 'events' is missing"));
            return List.of();
        }
        if (!eventsNode.isArray()) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    "$.events",
                    "Field 'events' must be an array"));
            return List.of();
        }
        if (eventsNode.isEmpty()) {
            warnings.add(warning(
                    TournamentScheduledEventsParseWarning.Code.EMPTY_EVENTS,
                    "$.events",
                    "The tournament scheduled-events response contains no events"));
        }

        List<TournamentScheduledEventCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < eventsNode.size(); index++) {
            TournamentScheduledEventCandidate candidate = parseEvent(
                    eventsNode.get(index),
                    "$.events[" + index + "]",
                    warnings,
                    problems);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    private static TournamentScheduledEventCandidate parseEvent(
            JsonNode eventNode,
            String path,
            WarningCollector warnings,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (!requireObject(eventNode, path, problems)) {
            return null;
        }

        int problemCountBeforeEvent = problems.size();
        warnUnknownFields(eventNode, EVENT_FIELDS, path, warnings);
        Long eventId = requiredPositiveLong(eventNode.get("id"), path + ".id", problems);
        Instant startsAt = requiredStartInstant(
                eventNode.get("startTimestamp"),
                path + ".startTimestamp",
                problems);
        ScheduledTeam homeTeam = parseTeam(
                eventNode.get("homeTeam"),
                path + ".homeTeam",
                warnings,
                problems);
        ScheduledTeam awayTeam = parseTeam(
                eventNode.get("awayTeam"),
                path + ".awayTeam",
                warnings,
                problems);
        ScheduledEventStatus status = parseStatus(
                eventNode.get("status"),
                path + ".status",
                warnings,
                problems);
        TournamentPair tournaments = parseTournamentPair(
                eventNode.get("tournament"),
                path + ".tournament",
                warnings,
                problems);

        if (problems.size() != problemCountBeforeEvent
                || eventId == null
                || startsAt == null
                || homeTeam == null
                || awayTeam == null
                || status == null
                || tournaments == null) {
            return null;
        }

        ScheduledEvent event = new ScheduledEvent(
                eventId,
                startsAt,
                homeTeam,
                awayTeam,
                status,
                Optional.of(tournaments.tournament()));
        return new TournamentScheduledEventCandidate(event, tournaments.uniqueTournament());
    }

    private static ScheduledTeam parseTeam(
            JsonNode teamNode,
            String path,
            WarningCollector warnings,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (!requireObject(teamNode, path, problems)) {
            return null;
        }
        int problemCountBeforeTeam = problems.size();
        warnUnknownFields(teamNode, TEAM_FIELDS, path, warnings);
        Long id = requiredPositiveLong(teamNode.get("id"), path + ".id", problems);
        String name = requiredBoundedText(
                teamNode.get("name"),
                path + ".name",
                MAXIMUM_NAME_LENGTH,
                problems);
        if (problems.size() != problemCountBeforeTeam || id == null || name == null) {
            return null;
        }
        return new ScheduledTeam(id, name);
    }

    private static ScheduledEventStatus parseStatus(
            JsonNode statusNode,
            String path,
            WarningCollector warnings,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (!requireObject(statusNode, path, problems)) {
            return null;
        }
        int problemCountBeforeStatus = problems.size();
        warnUnknownFields(statusNode, STATUS_FIELDS, path, warnings);
        String type = requiredBoundedText(
                statusNode.get("type"),
                path + ".type",
                MAXIMUM_STATUS_TYPE_LENGTH,
                problems);
        String description = optionalBoundedText(
                statusNode.get("description"),
                path + ".description",
                MAXIMUM_STATUS_DESCRIPTION_LENGTH,
                problems);
        if (problems.size() != problemCountBeforeStatus || type == null) {
            return null;
        }
        return new ScheduledEventStatus(type, Optional.ofNullable(description));
    }

    private static TournamentPair parseTournamentPair(
            JsonNode tournamentNode,
            String path,
            WarningCollector warnings,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (!requireObject(tournamentNode, path, problems)) {
            return null;
        }
        int problemCountBeforeTournament = problems.size();
        warnUnknownFields(tournamentNode, TOURNAMENT_FIELDS, path, warnings);
        ScheduledTournament tournament = parseTournamentIdentity(
                tournamentNode,
                path,
                problems);
        ScheduledTournament uniqueTournament = parseUniqueTournament(
                tournamentNode.get("uniqueTournament"),
                path + ".uniqueTournament",
                warnings,
                problems);
        if (problems.size() != problemCountBeforeTournament
                || tournament == null
                || uniqueTournament == null) {
            return null;
        }
        return new TournamentPair(tournament, uniqueTournament);
    }

    private static ScheduledTournament parseUniqueTournament(
            JsonNode uniqueTournamentNode,
            String path,
            WarningCollector warnings,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (!requireObject(uniqueTournamentNode, path, problems)) {
            return null;
        }
        warnUnknownFields(uniqueTournamentNode, UNIQUE_TOURNAMENT_FIELDS, path, warnings);
        return parseTournamentIdentity(uniqueTournamentNode, path, problems);
    }

    private static ScheduledTournament parseTournamentIdentity(
            JsonNode node,
            String path,
            List<TournamentScheduledEventsParseProblem> problems) {
        int problemCountBeforeIdentity = problems.size();
        Long id = requiredPositiveLong(node.get("id"), path + ".id", problems);
        String name = requiredBoundedText(
                node.get("name"),
                path + ".name",
                MAXIMUM_NAME_LENGTH,
                problems);
        if (problems.size() != problemCountBeforeIdentity || id == null || name == null) {
            return null;
        }
        return new ScheduledTournament(id, name);
    }

    private static boolean requireObject(
            JsonNode node,
            String path,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (node == null) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required object is missing"));
            return false;
        }
        if (!node.isObject()) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Required value must be an object"));
            return false;
        }
        return true;
    }

    private static Long requiredPositiveLong(
            JsonNode node,
            String path,
            List<TournamentScheduledEventsParseProblem> problems) {
        Long value = requiredLong(node, path, problems);
        if (value != null && value <= 0) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Required integer must be strictly positive"));
            return null;
        }
        return value;
    }

    private static Long requiredLong(
            JsonNode node,
            String path,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (node == null) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required integer field is missing"));
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Required field must be an integer representable as a 64-bit value"));
            return null;
        }
        return node.longValue();
    }

    private static Instant requiredStartInstant(
            JsonNode node,
            String path,
            List<TournamentScheduledEventsParseProblem> problems) {
        Long value = requiredPositiveLong(node, path, problems);
        if (value == null) {
            return null;
        }
        if (value > MAXIMUM_EPOCH_SECOND) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Timestamp must be expressed in epoch seconds within the supported range"));
            return null;
        }
        try {
            return Instant.ofEpochSecond(value);
        }
        catch (DateTimeException | ArithmeticException exception) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Timestamp is outside the supported Instant range"));
            return null;
        }
    }

    private static String requiredBoundedText(
            JsonNode node,
            String path,
            int maximumLength,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (node == null) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required text field is missing"));
            return null;
        }
        return validateBoundedText(node, path, maximumLength, problems);
    }

    private static String optionalBoundedText(
            JsonNode node,
            String path,
            int maximumLength,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (node == null) {
            return null;
        }
        return validateBoundedText(node, path, maximumLength, problems);
    }

    private static String validateBoundedText(
            JsonNode node,
            String path,
            int maximumLength,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (!node.isString() || node.stringValue().isBlank()) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Text value must be a non-blank string"));
            return null;
        }
        String value = node.stringValue();
        if (value.length() > maximumLength) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.TEXT_TOO_LONG,
                    path,
                    "Text value exceeds its maximum accepted length"));
            return null;
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            problems.add(problem(
                    TournamentScheduledEventsParseProblem.Code.TEXT_CONTAINS_CONTROL_CHARACTER,
                    path,
                    "Text value must not contain control characters"));
            return null;
        }
        return value;
    }

    private static void warnUnknownFields(
            JsonNode object,
            Set<String> knownFields,
            String path,
            WarningCollector warnings) {
        object.properties().stream()
                .filter(entry -> !knownFields.contains(entry.getKey()))
                .forEach(entry -> warnings.add(warning(
                        TournamentScheduledEventsParseWarning.Code.UNKNOWN_FIELD,
                        boundedWarningPath(path + "." + entry.getKey()),
                        "Auxiliary field is ignored by tournament-scheduled-v1")));
    }

    private static String boundedWarningPath(String path) {
        if (path.length() <= MAXIMUM_WARNING_PATH_LENGTH) {
            return path;
        }
        return path.substring(0, MAXIMUM_WARNING_PATH_LENGTH - 1) + "\u2026";
    }

    private static TournamentScheduledEventsParseResult failed(
            TournamentScheduledEventsParseStatus status,
            TournamentScheduledEventsParseEvidence evidence,
            TournamentScheduledEventsParseProblem.Code code,
            String path,
            String message) {
        return TournamentScheduledEventsParseResult.failed(
                status,
                evidence,
                List.of(),
                List.of(problem(code, path, message)));
    }

    private static TournamentScheduledEventsParseResult incompatible(
            TournamentScheduledEventsParseEvidence evidence,
            WarningCollector warnings,
            List<TournamentScheduledEventsParseProblem> problems) {
        return TournamentScheduledEventsParseResult.failed(
                TournamentScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE,
                evidence,
                warnings.snapshot(),
                problems);
    }

    private static TournamentScheduledEventsParseProblem problem(
            TournamentScheduledEventsParseProblem.Code code,
            String path,
            String message) {
        return new TournamentScheduledEventsParseProblem(code, path, message);
    }

    private static TournamentScheduledEventsParseWarning warning(
            TournamentScheduledEventsParseWarning.Code code,
            String path,
            String message) {
        return new TournamentScheduledEventsParseWarning(code, path, message);
    }

    private record TournamentPair(
            ScheduledTournament tournament,
            ScheduledTournament uniqueTournament) {
    }

    private static final class WarningCollector {

        private final List<TournamentScheduledEventsParseWarning> values = new ArrayList<>();
        private boolean truncated;

        void add(TournamentScheduledEventsParseWarning warning) {
            Objects.requireNonNull(warning, "warning");
            if (truncated) {
                return;
            }
            if (values.size() < MAXIMUM_WARNINGS - 1) {
                values.add(warning);
                return;
            }
            values.add(TournamentScheduledEventsV1Parser.warning(
                    TournamentScheduledEventsParseWarning.Code.WARNING_LIMIT_REACHED,
                    "$",
                    "Additional parser warnings were suppressed"));
            truncated = true;
        }

        List<TournamentScheduledEventsParseWarning> snapshot() {
            return List.copyOf(values);
        }
    }
}
