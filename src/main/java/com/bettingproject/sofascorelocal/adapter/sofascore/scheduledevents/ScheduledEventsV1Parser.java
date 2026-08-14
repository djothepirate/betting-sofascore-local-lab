package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.ScheduledEventDto;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.PayloadShape;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.ScheduledTournamentAvailabilityDto;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.StatusDto;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.TeamDto;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsEnvelopeDto.TournamentDto;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.fixture.FixtureContentKind;
import com.bettingproject.sofascorelocal.fixture.FixtureContentClassifier;
import com.bettingproject.sofascorelocal.fixture.FixtureLoadingException;
import com.bettingproject.sofascorelocal.fixture.FixturePayloadHasher;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class ScheduledEventsV1Parser {

    public static final String PARSER_VERSION = "scheduled-events-v1";

    private static final Set<String> ROOT_FIELDS = Set.of(
            "events",
            "scheduled",
            "hasNextPage");
    private static final Set<String> EVENT_FIELDS = Set.of(
            "id",
            "startTimestamp",
            "homeTeam",
            "awayTeam",
            "status",
            "tournament");
    private static final Set<String> TEAM_FIELDS = Set.of("id", "name");
    private static final Set<String> STATUS_FIELDS = Set.of("type", "description");
    private static final Set<String> TOURNAMENT_FIELDS = Set.of("id", "name");
    private static final Set<String> SCHEDULED_ENTRY_FIELDS = Set.of(
            "tournament",
            "timezoneEventCount");
    private static final Set<String> QUALIFIED_TOURNAMENT_FIELDS = Set.of(
            "category",
            "fieldTranslations",
            "id",
            "name",
            "priority",
            "qualificationOrPreliminary",
            "slug",
            "uniqueTournament");
    private static final Set<String> QUALIFIED_UNIQUE_TOURNAMENT_FIELDS = Set.of(
            "category",
            "displayInverseHomeAwayTeams",
            "fieldTranslations",
            "hasEventPlayerStatistics",
            "hasPerformanceGraphFeature",
            "id",
            "name",
            "slug",
            "userCount");

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final ScheduledEventsMapper mapper;

    public ScheduledEventsV1Parser() {
        this(new ScheduledEventsMapper());
    }

    ScheduledEventsV1Parser(ScheduledEventsMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public ScheduledEventsParseResult parse(LoadedFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        return parsePayload(
                fixture.manifest().endpointType(),
                fixture.rawPayload(),
                fixture.contentKind(),
                evidence(fixture));
    }

    public ScheduledEventsParseResult parseTransportResponse(
            ScheduledEventsTransportResponse response) {
        Objects.requireNonNull(response, "response");
        byte[] rawPayload = response.payload().bytes();
        FixtureContentKind contentKind = FixtureContentClassifier.classify(
                rawPayload,
                response.contentType());
        Optional<String> canonicalJsonSha256 = Optional.empty();
        if (contentKind == FixtureContentKind.JSON) {
            try {
                canonicalJsonSha256 = Optional.of(
                        FixturePayloadHasher.canonicalJsonSha256(rawPayload));
            }
            catch (FixtureLoadingException exception) {
                canonicalJsonSha256 = Optional.empty();
            }
        }
        ScheduledEventsParseEvidence evidence = new ScheduledEventsParseEvidence(
                "manual-call-scheduled-events",
                response.payload().sha256(),
                canonicalJsonSha256,
                response.receivedAt(),
                PARSER_VERSION);
        return parsePayload(
                response.endpointType(),
                rawPayload,
                contentKind,
                evidence);
    }

    private ScheduledEventsParseResult parsePayload(
            SofascoreEndpointType endpointType,
            byte[] rawPayload,
            FixtureContentKind contentKind,
            ScheduledEventsParseEvidence evidence) {

        if (endpointType != SofascoreEndpointType.SCHEDULED_EVENTS) {
            return failed(
                    ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    ScheduledEventsParseProblem.Code.UNSUPPORTED_ENDPOINT,
                    "$",
                    "The fixture endpoint is not SCHEDULED_EVENTS");
        }
        if (contentKind != FixtureContentKind.JSON) {
            return failed(
                    ScheduledEventsParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    ScheduledEventsParseProblem.Code.UNEXPECTED_CONTENT_KIND,
                    "$",
                    "The scheduled-events-v1 parser accepts JSON content only");
        }

        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(rawPayload);
        }
        catch (JacksonException exception) {
            return failed(
                    ScheduledEventsParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    ScheduledEventsParseProblem.Code.INVALID_JSON,
                    "$",
                    "The payload is not valid unambiguous JSON");
        }

        List<ScheduledEventsParseWarning> warnings = new ArrayList<>();
        List<ScheduledEventsParseProblem> problems = new ArrayList<>();
        if (root == null || !root.isObject()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    "$",
                    "The root value must be an object"));
            return incompatible(evidence, warnings, problems);
        }

        warnUnknownFields(root, ROOT_FIELDS, "$", warnings);
        Boolean hasNextPage = requiredBoolean(root.get("hasNextPage"), "$.hasNextPage", problems);
        boolean hasLegacyEvents = root.has("events");
        boolean hasQualifiedScheduled = root.has("scheduled");

        if (hasLegacyEvents && hasQualifiedScheduled) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    "$",
                    "The payload must expose either 'events' or 'scheduled', not both"));
        }

        ScheduledEventsEnvelopeDto externalValue;
        if (hasQualifiedScheduled && !hasLegacyEvents) {
            List<ScheduledTournamentAvailabilityDto> scheduledTournaments =
                    parseScheduledTournaments(root.get("scheduled"), warnings, problems);
            externalValue = new ScheduledEventsEnvelopeDto(
                    PayloadShape.SCHEDULED_TOURNAMENT_LIST,
                    List.of(),
                    scheduledTournaments,
                    Boolean.TRUE.equals(hasNextPage));
        }
        else {
            List<ScheduledEventDto> events = parseEvents(
                    root.get("events"),
                    warnings,
                    problems);
            externalValue = new ScheduledEventsEnvelopeDto(
                    PayloadShape.EVENT_LIST,
                    events,
                    List.of(),
                    Boolean.TRUE.equals(hasNextPage));
        }

        if (!problems.isEmpty()) {
            return incompatible(evidence, warnings, problems);
        }

        return ScheduledEventsParseResult.parsed(
                evidence,
                mapper.map(externalValue),
                warnings);
    }

    private static List<ScheduledTournamentAvailabilityDto> parseScheduledTournaments(
            JsonNode scheduledNode,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (scheduledNode == null) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    "$.scheduled",
                    "Required field 'scheduled' is missing"));
            return List.of();
        }
        if (!scheduledNode.isArray()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    "$.scheduled",
                    "Field 'scheduled' must be an array"));
            return List.of();
        }
        if (scheduledNode.isEmpty()) {
            warnings.add(warning(
                    ScheduledEventsParseWarning.Code.EMPTY_SCHEDULED_TOURNAMENTS,
                    "$.scheduled",
                    "The scheduled-tournament page contains no entries"));
        }

        List<ScheduledTournamentAvailabilityDto> scheduledTournaments = new ArrayList<>();
        for (int index = 0; index < scheduledNode.size(); index++) {
            ScheduledTournamentAvailabilityDto scheduledTournament = parseScheduledTournament(
                    scheduledNode.get(index),
                    "$.scheduled[" + index + "]",
                    warnings,
                    problems);
            if (scheduledTournament != null) {
                scheduledTournaments.add(scheduledTournament);
            }
        }
        return List.copyOf(scheduledTournaments);
    }

    private static ScheduledTournamentAvailabilityDto parseScheduledTournament(
            JsonNode scheduledTournamentNode,
            String path,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (!requireObject(scheduledTournamentNode, path, problems)) {
            return null;
        }

        int problemCountBeforeEntry = problems.size();
        warnUnknownFields(scheduledTournamentNode, SCHEDULED_ENTRY_FIELDS, path, warnings);
        QualifiedTournament qualifiedTournament = parseQualifiedTournament(
                scheduledTournamentNode.get("tournament"),
                path + ".tournament",
                warnings,
                problems);
        Map<Integer, Integer> timezoneEventCount = parseTimezoneEventCount(
                scheduledTournamentNode.get("timezoneEventCount"),
                path + ".timezoneEventCount",
                warnings,
                problems);

        if (problems.size() != problemCountBeforeEntry || qualifiedTournament == null) {
            return null;
        }
        return new ScheduledTournamentAvailabilityDto(
                qualifiedTournament.tournament(),
                qualifiedTournament.uniqueTournament(),
                timezoneEventCount);
    }

    private static QualifiedTournament parseQualifiedTournament(
            JsonNode tournamentNode,
            String path,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (!requireObject(tournamentNode, path, problems)) {
            return null;
        }

        int problemCountBeforeTournament = problems.size();
        warnUnknownFields(tournamentNode, QUALIFIED_TOURNAMENT_FIELDS, path, warnings);
        Long id = requiredPositiveLong(tournamentNode.get("id"), path + ".id", problems);
        String name = requiredText(tournamentNode.get("name"), path + ".name", problems);
        TournamentDto uniqueTournament = parseOptionalTournamentIdentity(
                tournamentNode.get("uniqueTournament"),
                path + ".uniqueTournament",
                warnings,
                problems);

        if (problems.size() != problemCountBeforeTournament) {
            return null;
        }
        return new QualifiedTournament(new TournamentDto(id, name), uniqueTournament);
    }

    private static TournamentDto parseOptionalTournamentIdentity(
            JsonNode tournamentNode,
            String path,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (tournamentNode == null) {
            warnings.add(warning(
                    ScheduledEventsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                    path,
                    "Optional field 'uniqueTournament' is absent"));
            return null;
        }
        if (!requireObject(tournamentNode, path, problems)) {
            return null;
        }

        int problemCountBeforeTournament = problems.size();
        warnUnknownFields(
                tournamentNode,
                QUALIFIED_UNIQUE_TOURNAMENT_FIELDS,
                path,
                warnings);
        Long id = requiredPositiveLong(tournamentNode.get("id"), path + ".id", problems);
        String name = requiredText(tournamentNode.get("name"), path + ".name", problems);
        if (problems.size() != problemCountBeforeTournament) {
            return null;
        }
        return new TournamentDto(id, name);
    }

    private static Map<Integer, Integer> parseTimezoneEventCount(
            JsonNode countsNode,
            String path,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (countsNode == null) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required timezone event-count value is missing"));
            return Map.of();
        }
        if (countsNode.isArray()) {
            if (countsNode.isEmpty()) {
                warnings.add(warning(
                        ScheduledEventsParseWarning.Code.EMPTY_TIMEZONE_EVENT_COUNT,
                        path,
                        "An empty timezone event-count array represents no counts"));
                return Map.of();
            }
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Timezone event counts must be an object or an empty array"));
            return Map.of();
        }
        if (!countsNode.isObject()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Timezone event counts must be an object or an empty array"));
            return Map.of();
        }
        if (countsNode.isEmpty()) {
            warnings.add(warning(
                    ScheduledEventsParseWarning.Code.EMPTY_TIMEZONE_EVENT_COUNT,
                    path,
                    "The timezone event-count map is empty"));
        }

        Map<Integer, Integer> counts = new TreeMap<>();
        countsNode.properties().forEach(entry -> {
            String entryPath = path + "." + entry.getKey();
            Integer offset = parseTimezoneOffset(entry.getKey(), entryPath, problems);
            Integer count = requiredNonNegativeInt(entry.getValue(), entryPath, problems);
            if (offset != null && count != null && counts.put(offset, count) != null) {
                problems.add(problem(
                        ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                        entryPath,
                        "Timezone offsets must be unique after integer normalization"));
            }
        });
        return Map.copyOf(counts);
    }

    private static Integer parseTimezoneOffset(
            String rawOffset,
            String path,
            List<ScheduledEventsParseProblem> problems) {
        try {
            return Integer.valueOf(rawOffset);
        }
        catch (NumberFormatException exception) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Timezone offset keys must be 32-bit integers"));
            return null;
        }
    }

    private static Integer requiredNonNegativeInt(
            JsonNode node,
            String path,
            List<ScheduledEventsParseProblem> problems) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Timezone event counts must be 32-bit integers"));
            return null;
        }
        int value = node.intValue();
        if (value < 0) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Timezone event counts must be non-negative"));
            return null;
        }
        return value;
    }

    private static List<ScheduledEventDto> parseEvents(
            JsonNode eventsNode,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (eventsNode == null) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    "$.events",
                    "Required field 'events' is missing"));
            return List.of();
        }
        if (!eventsNode.isArray()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    "$.events",
                    "Field 'events' must be an array"));
            return List.of();
        }
        if (eventsNode.isEmpty()) {
            warnings.add(warning(
                    ScheduledEventsParseWarning.Code.EMPTY_EVENTS,
                    "$.events",
                    "The scheduled-events page contains no events"));
        }

        List<ScheduledEventDto> events = new ArrayList<>();
        for (int index = 0; index < eventsNode.size(); index++) {
            ScheduledEventDto event = parseEvent(
                    eventsNode.get(index),
                    "$.events[" + index + "]",
                    warnings,
                    problems);
            if (event != null) {
                events.add(event);
            }
        }
        return List.copyOf(events);
    }

    private static ScheduledEventDto parseEvent(
            JsonNode eventNode,
            String path,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (eventNode == null || !eventNode.isObject()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Each event must be an object"));
            return null;
        }

        int problemCountBeforeEvent = problems.size();
        warnUnknownFields(eventNode, EVENT_FIELDS, path, warnings);

        Long id = requiredPositiveLong(eventNode.get("id"), path + ".id", problems);
        Long startTimestamp = requiredLong(
                eventNode.get("startTimestamp"),
                path + ".startTimestamp",
                problems);
        validateEpochSecond(startTimestamp, path + ".startTimestamp", problems);
        TeamDto homeTeam = parseTeam(
                eventNode.get("homeTeam"),
                path + ".homeTeam",
                warnings,
                problems);
        TeamDto awayTeam = parseTeam(
                eventNode.get("awayTeam"),
                path + ".awayTeam",
                warnings,
                problems);
        StatusDto status = parseStatus(
                eventNode.get("status"),
                path + ".status",
                warnings,
                problems);
        TournamentDto tournament = parseOptionalTournament(
                eventNode.get("tournament"),
                path + ".tournament",
                warnings,
                problems);

        if (problems.size() != problemCountBeforeEvent) {
            return null;
        }
        return new ScheduledEventDto(
                id,
                startTimestamp,
                homeTeam,
                awayTeam,
                status,
                tournament);
    }

    private static TeamDto parseTeam(
            JsonNode teamNode,
            String path,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (!requireObject(teamNode, path, problems)) {
            return null;
        }
        int problemCountBeforeTeam = problems.size();
        warnUnknownFields(teamNode, TEAM_FIELDS, path, warnings);
        Long id = requiredPositiveLong(teamNode.get("id"), path + ".id", problems);
        String name = requiredText(teamNode.get("name"), path + ".name", problems);
        if (problems.size() != problemCountBeforeTeam) {
            return null;
        }
        return new TeamDto(id, name);
    }

    private static StatusDto parseStatus(
            JsonNode statusNode,
            String path,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (!requireObject(statusNode, path, problems)) {
            return null;
        }
        int problemCountBeforeStatus = problems.size();
        warnUnknownFields(statusNode, STATUS_FIELDS, path, warnings);
        String type = requiredText(statusNode.get("type"), path + ".type", problems);
        String description = optionalText(
                statusNode.get("description"),
                path + ".description",
                warnings,
                problems);
        if (problems.size() != problemCountBeforeStatus) {
            return null;
        }
        return new StatusDto(type, description);
    }

    private static TournamentDto parseOptionalTournament(
            JsonNode tournamentNode,
            String path,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (tournamentNode == null) {
            warnings.add(warning(
                    ScheduledEventsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                    path,
                    "Optional field 'tournament' is absent"));
            return null;
        }
        if (!tournamentNode.isObject()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field 'tournament' must be an object when present"));
            return null;
        }

        int problemCountBeforeTournament = problems.size();
        warnUnknownFields(tournamentNode, TOURNAMENT_FIELDS, path, warnings);
        Long id = requiredPositiveLong(tournamentNode.get("id"), path + ".id", problems);
        String name = requiredText(tournamentNode.get("name"), path + ".name", problems);
        if (problems.size() != problemCountBeforeTournament) {
            return null;
        }
        return new TournamentDto(id, name);
    }

    private static boolean requireObject(
            JsonNode node,
            String path,
            List<ScheduledEventsParseProblem> problems) {
        if (node == null) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required object is missing"));
            return false;
        }
        if (!node.isObject()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Required value must be an object"));
            return false;
        }
        return true;
    }

    private static Long requiredPositiveLong(
            JsonNode node,
            String path,
            List<ScheduledEventsParseProblem> problems) {
        Long value = requiredLong(node, path, problems);
        if (value != null && value <= 0) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Identifier must be a positive integer"));
            return null;
        }
        return value;
    }

    private static Long requiredLong(
            JsonNode node,
            String path,
            List<ScheduledEventsParseProblem> problems) {
        if (node == null) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required integer field is missing"));
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Required field must be an integer representable as a 64-bit value"));
            return null;
        }
        return node.longValue();
    }

    private static Boolean requiredBoolean(
            JsonNode node,
            String path,
            List<ScheduledEventsParseProblem> problems) {
        if (node == null) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required boolean field is missing"));
            return null;
        }
        if (!node.isBoolean()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Required field must be a boolean"));
            return null;
        }
        return node.booleanValue();
    }

    private static String requiredText(
            JsonNode node,
            String path,
            List<ScheduledEventsParseProblem> problems) {
        if (node == null) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required text field is missing"));
            return null;
        }
        if (!node.isString() || node.stringValue().isBlank()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Required field must be a non-blank string"));
            return null;
        }
        return node.stringValue();
    }

    private static String optionalText(
            JsonNode node,
            String path,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (node == null) {
            warnings.add(warning(
                    ScheduledEventsParseWarning.Code.OPTIONAL_FIELD_MISSING,
                    path,
                    "Optional text field is absent"));
            return null;
        }
        if (!node.isString() || node.stringValue().isBlank()) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Optional field must be a non-blank string when present"));
            return null;
        }
        return node.stringValue();
    }

    private static void validateEpochSecond(
            Long value,
            String path,
            List<ScheduledEventsParseProblem> problems) {
        if (value == null) {
            return;
        }
        try {
            Instant.ofEpochSecond(value);
        }
        catch (DateTimeException exception) {
            problems.add(problem(
                    ScheduledEventsParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Timestamp is outside the supported Instant range"));
        }
    }

    private static void warnUnknownFields(
            JsonNode object,
            Set<String> knownFields,
            String path,
            List<ScheduledEventsParseWarning> warnings) {
        object.properties().stream()
                .filter(entry -> !knownFields.contains(entry.getKey()))
                .forEach(entry -> warnings.add(warning(
                        ScheduledEventsParseWarning.Code.UNKNOWN_FIELD,
                        path + "." + entry.getKey(),
                        "Unknown field is ignored by scheduled-events-v1")));
    }

    private static ScheduledEventsParseEvidence evidence(LoadedFixture fixture) {
        return new ScheduledEventsParseEvidence(
                fixture.manifest().fixtureId(),
                fixture.rawSha256(),
                fixture.canonicalJsonSha256(),
                fixture.manifest().recordedAt(),
                PARSER_VERSION);
    }

    private static ScheduledEventsParseResult failed(
            ScheduledEventsParseStatus status,
            ScheduledEventsParseEvidence evidence,
            ScheduledEventsParseProblem.Code code,
            String path,
            String message) {
        return ScheduledEventsParseResult.failed(
                status,
                evidence,
                List.of(),
                List.of(problem(code, path, message)));
    }

    private static ScheduledEventsParseResult incompatible(
            ScheduledEventsParseEvidence evidence,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        return ScheduledEventsParseResult.failed(
                ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE,
                evidence,
                warnings,
                problems);
    }

    private static ScheduledEventsParseWarning warning(
            ScheduledEventsParseWarning.Code code,
            String path,
            String message) {
        return new ScheduledEventsParseWarning(code, path, message);
    }

    private static ScheduledEventsParseProblem problem(
            ScheduledEventsParseProblem.Code code,
            String path,
            String message) {
        return new ScheduledEventsParseProblem(code, path, message);
    }

    private record QualifiedTournament(
            TournamentDto tournament,
            TournamentDto uniqueTournament) {
    }
}
