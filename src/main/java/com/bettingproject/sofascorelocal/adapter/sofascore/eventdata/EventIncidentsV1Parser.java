package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
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

public final class EventIncidentsV1Parser {

    public static final String PARSER_VERSION = "event-incidents-v1";

    private static final Set<String> ROOT_FIELDS = Set.of("eventId", "incidents");
    private static final Set<String> INCIDENT_FIELDS = Set.of(
            "incidentType",
            "time",
            "addedTime",
            "isHome",
            "teamId",
            "player",
            "homeScore",
            "awayScore");
    private static final Set<String> PLAYER_FIELDS = Set.of("id", "name");

    public J5ParseResult<EventIncidents> parse(LoadedFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        J5ParseEvidence evidence = evidence(fixture);
        J5ParseResult<EventIncidents> preflight = preflight(fixture, evidence);
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
        JsonNode incidentsNode = root.get("incidents");
        if (!J5JsonParserSupport.requiredArray(
                incidentsNode,
                "$.incidents",
                problems)) {
            return incompatible(evidence, warnings, problems);
        }

        List<EventIncident> incidents = new ArrayList<>();
        List<String> missingPaths = new ArrayList<>();
        int presentSignals = 0;
        for (int index = 0; index < incidentsNode.size(); index++) {
            JsonNode incidentNode = incidentsNode.get(index);
            String path = "$.incidents[" + index + "]";
            if (!J5JsonParserSupport.requiredObject(incidentNode, path, problems)) {
                continue;
            }
            J5JsonParserSupport.warnUnknownFields(
                    incidentNode,
                    INCIDENT_FIELDS,
                    path,
                    warnings);
            String incidentType = J5JsonParserSupport.requiredText(
                    incidentNode.get("incidentType"),
                    path + ".incidentType",
                    64,
                    problems);
            Integer minute = J5JsonParserSupport.requiredInteger(
                    incidentNode.get("time"),
                    path + ".time",
                    0,
                    300,
                    problems);
            Optional<Integer> addedTime = J5JsonParserSupport.optionalInteger(
                    incidentNode.get("addedTime"),
                    path + ".addedTime",
                    0,
                    30,
                    warnings,
                    problems);
            Optional<Boolean> home = J5JsonParserSupport.optionalBoolean(
                    incidentNode.get("isHome"),
                    path + ".isHome",
                    warnings,
                    problems);
            if (home.isPresent()) {
                presentSignals++;
            }
            else {
                missingPaths.add(path + ".isHome");
            }
            Optional<Long> teamId = J5JsonParserSupport.optionalPositiveLong(
                    incidentNode.get("teamId"),
                    path + ".teamId",
                    warnings,
                    problems);
            Player player = optionalPlayer(
                    incidentNode.get("player"),
                    path + ".player",
                    warnings,
                    problems);
            Optional<Integer> homeScore = optionalScore(
                    incidentNode.get("homeScore"),
                    path + ".homeScore",
                    warnings,
                    problems);
            Optional<Integer> awayScore = optionalScore(
                    incidentNode.get("awayScore"),
                    path + ".awayScore",
                    warnings,
                    problems);
            if (homeScore.isPresent() != awayScore.isPresent()) {
                problems.add(J5JsonParserSupport.problem(
                        J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                        path,
                        "homeScore and awayScore must be present together"));
            }
            if (incidentType != null && minute != null && player.valid()) {
                incidents.add(new EventIncident(
                        index,
                        incidentType,
                        minute,
                        addedTime,
                        home,
                        teamId,
                        player.id(),
                        player.name(),
                        homeScore,
                        awayScore));
            }
        }

        if (!problems.isEmpty() || eventId == null) {
            return incompatible(evidence, warnings, problems);
        }
        J5CompletenessReport completeness = incidents.isEmpty()
                ? J5CompletenessReport.emptyValid()
                : J5CompletenessReport.measured(
                        presentSignals,
                        incidents.size(),
                        missingPaths);
        return J5ParseResult.parsed(
                evidence,
                new EventIncidents(eventId, incidents),
                completeness,
                warnings);
    }

    private static Player optionalPlayer(
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (J5JsonParserSupport.isAbsent(node)) {
            return Player.empty();
        }
        if (!J5JsonParserSupport.requiredObject(node, path, problems)) {
            return Player.invalid();
        }
        J5JsonParserSupport.warnUnknownFields(node, PLAYER_FIELDS, path, warnings);
        Long id = J5JsonParserSupport.requiredPositiveLong(
                node.get("id"),
                path + ".id",
                problems);
        String name = J5JsonParserSupport.requiredText(
                node.get("name"),
                path + ".name",
                200,
                problems);
        return id == null || name == null
                ? Player.invalid()
                : Player.of(id, name);
    }

    private static Optional<Integer> optionalScore(
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return J5JsonParserSupport.optionalInteger(
                node,
                path,
                0,
                99,
                warnings,
                problems);
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

    private static J5ParseResult<EventIncidents> preflight(
            LoadedFixture fixture,
            J5ParseEvidence evidence) {
        if (fixture.manifest().endpointType() != SofascoreEndpointType.EVENT_INCIDENTS) {
            return failed(
                    J5ParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    J5ParseProblem.Code.UNSUPPORTED_ENDPOINT,
                    "$",
                    "The incidents parser accepts EVENT_INCIDENTS fixtures only");
        }
        if (!PARSER_VERSION.equals(fixture.manifest().parserVersion())) {
            return failed(
                    J5ParseStatus.SCHEMA_INCOMPATIBLE,
                    evidence,
                    J5ParseProblem.Code.UNSUPPORTED_PARSER_VERSION,
                    "$",
                    "The fixture parser version does not match event-incidents-v1");
        }
        if (fixture.contentKind() != FixtureContentKind.JSON) {
            return failed(
                    J5ParseStatus.UNEXPECTED_CONTENT,
                    evidence,
                    J5ParseProblem.Code.UNEXPECTED_CONTENT_KIND,
                    "$",
                    "The incidents parser accepts JSON content only");
        }
        return null;
    }

    private static J5ParseResult<EventIncidents> incompatible(
            J5ParseEvidence evidence,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return J5ParseResult.failed(
                J5ParseStatus.SCHEMA_INCOMPATIBLE,
                evidence,
                warnings,
                problems);
    }

    private static J5ParseResult<EventIncidents> failed(
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

    private record Player(Optional<Long> id, Optional<String> name, boolean valid) {

        static Player empty() {
            return new Player(Optional.empty(), Optional.empty(), true);
        }

        static Player invalid() {
            return new Player(Optional.empty(), Optional.empty(), false);
        }

        static Player of(long id, String name) {
            return new Player(Optional.of(id), Optional.of(name), true);
        }
    }
}
