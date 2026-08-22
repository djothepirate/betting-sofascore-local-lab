package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
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

/**
 * Provider-shape incident parser with period sentinels and substitution participants.
 *
 * <p>The provider uses {@code addedTime=999} on technical {@code period} markers. The raw value
 * remains immutable in its snapshot; the normalized incident deliberately leaves added time empty.
 * Substitutions preserve the distinct {@code playerIn} and {@code playerOut} identities.</p>
 */
public class EventIncidentsV4Parser {

    public static final String PARSER_VERSION = "event-incidents-v4";
    private static final int PERIOD_ADDED_TIME_SENTINEL = 999;
    private static final Set<String> ROOT_FIELDS = Set.of("incidents", "home", "away");
    private static final Set<String> SIDELESS_INCIDENT_TYPES = Set.of("period", "injuryTime");
    protected static final Set<String> INCIDENT_FIELDS = Set.of(
            "incidentType", "incidentClass", "time", "addedTime", "isHome",
            "team", "teamId", "player", "playerIn", "playerOut", "playerName",
            "assist1", "homeScore", "awayScore", "id", "reason", "rescinded",
            "reversedPeriodTime", "reversedPeriodTimeSeconds", "text", "color",
            "from", "to", "length", "period", "periodTimeSeconds", "timeSeconds",
            "injuryTime", "injury", "isLive", "footballPassingNetworkAction");
    private static final Set<String> PLAYER_FIELDS = Set.of(
            "id", "name", "firstName", "lastName", "slug", "shortName", "position",
            "jerseyNumber", "height", "userCount", "gender", "sofascoreId",
            "marketValueCurrency", "dateOfBirth", "dateOfBirthTimestamp",
            "proposedMarketValueRaw", "fieldTranslations", "country", "preferredFoot");
    private static final Set<String> TEAM_FIELDS = Set.of(
            "id", "name", "slug", "shortName", "gender", "userCount",
            "sport", "teamColors", "fieldTranslations", "nameCode");

    public J5ParseResult<EventIncidents> parse(
            long snapshotId,
            long expectedEventId,
            RawPayloadEvidence payload,
            Instant receivedAt) {
        J5JsonParserSupport.requirePositiveEventId(expectedEventId);
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
        JsonNode items = root.get("incidents");
        if (!J5JsonParserSupport.requiredArray(items, "$.incidents", problems)) {
            return incompatible(evidence, warnings, problems);
        }

        List<EventIncident> incidents = new ArrayList<>();
        List<String> missingPaths = new ArrayList<>();
        int expectedSignals = 0;
        int presentSignals = 0;
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            String path = "$.incidents[" + i + "]";
            if (!J5JsonParserSupport.requiredObject(item, path, problems)) {
                continue;
            }
            J5JsonParserSupport.warnUnknownFields(
                    item, incidentFields(), path, warnings);
            String type = J5JsonParserSupport.requiredText(
                    item.get("incidentType"), path + ".incidentType", 64, problems);
            Integer minute = normalizedMinute(type, item, path, warnings, problems);
            Optional<String> incidentClass = normalizedIncidentClass(
                    type, item, path, warnings, problems);
            Optional<String> reason = normalizedReason(
                    type, item, path, warnings, problems);
            Optional<Integer> added = optionalAddedTime(
                    type, item.get("addedTime"), path + ".addedTime", warnings, problems);
            Optional<Boolean> home = J5JsonParserSupport.optionalBoolean(
                    item.get("isHome"), path + ".isHome", warnings, problems);
            if (type != null) {
                expectedSignals++;
                if (SIDELESS_INCIDENT_TYPES.contains(type) || home.isPresent()) {
                    presentSignals++;
                }
                else {
                    missingPaths.add(path + ".isHome");
                }
            }
            Optional<Long> teamId = optionalTeamId(item, path, warnings, problems);
            Player player = optionalPlayer(item.get("player"), path + ".player",
                    warnings, problems);
            Player playerIn = optionalPlayer(item.get("playerIn"), path + ".playerIn",
                    warnings, problems);
            Player playerOut = optionalPlayer(item.get("playerOut"), path + ".playerOut",
                    warnings, problems);
            if ("substitution".equals(type)) {
                expectedSignals += 2;
                if (playerIn.present()) {
                    presentSignals++;
                }
                else {
                    missingPaths.add(path + ".playerIn");
                }
                if (playerOut.present()) {
                    presentSignals++;
                }
                else {
                    missingPaths.add(path + ".playerOut");
                }
            }
            Optional<Integer> homeScore = J5JsonParserSupport.optionalInteger(
                    item.get("homeScore"), path + ".homeScore", 0, 99,
                    warnings, problems);
            Optional<Integer> awayScore = J5JsonParserSupport.optionalInteger(
                    item.get("awayScore"), path + ".awayScore", 0, 99,
                    warnings, problems);
            if (homeScore.isPresent() != awayScore.isPresent()) {
                problems.add(J5JsonParserSupport.problem(
                        J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH,
                        path,
                        "homeScore and awayScore must be present together"));
            }
            if (type != null
                    && minute != null
                    && player.valid()
                    && playerIn.valid()
                    && playerOut.valid()) {
                incidents.add(new EventIncident(
                        i,
                        type,
                        minute,
                        added,
                        home,
                        teamId,
                        player.id(),
                        player.name(),
                        playerIn.id(),
                        playerIn.name(),
                        playerOut.id(),
                        playerOut.name(),
                        homeScore,
                        awayScore,
                        incidentClass,
                        reason));
            }
        }
        if (!problems.isEmpty()) {
            return incompatible(evidence, warnings, problems);
        }
        J5CompletenessReport completeness = completeness(
                incidents, presentSignals, expectedSignals, missingPaths);
        return J5ParseResult.parsed(
                evidence,
                new EventIncidents(expectedEventId, incidents),
                completeness,
                warnings);
    }

    private static Optional<Integer> optionalAddedTime(
            String incidentType,
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if ("period".equals(incidentType)
                && node != null
                && node.isIntegralNumber()
                && node.canConvertToInt()
                && node.intValue() == PERIOD_ADDED_TIME_SENTINEL) {
            warnings.add(J5JsonParserSupport.warning(
                    J5ParseWarning.Code.PROVIDER_SENTINEL_NORMALIZED,
                    path,
                    "Provider period-marker sentinel retained in raw snapshot and omitted from normalized added time"));
            return Optional.empty();
        }
        return J5JsonParserSupport.optionalInteger(
                node, path, 0, 30, warnings, problems);
    }

    private static J5CompletenessReport completeness(
            List<EventIncident> incidents,
            int presentSignals,
            int expectedSignals,
            List<String> missingPaths) {
        if (incidents.isEmpty()) {
            return J5CompletenessReport.emptyValid();
        }
        return J5CompletenessReport.measured(
                presentSignals, expectedSignals, missingPaths);
    }

    private static Optional<Long> optionalTeamId(
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        JsonNode direct = item.get("teamId");
        if (!J5JsonParserSupport.isAbsent(direct)) {
            return J5JsonParserSupport.optionalPositiveLong(
                    direct, path + ".teamId", warnings, problems);
        }
        JsonNode team = item.get("team");
        if (J5JsonParserSupport.isAbsent(team)) {
            return Optional.empty();
        }
        if (!J5JsonParserSupport.requiredObject(team, path + ".team", problems)) {
            return Optional.empty();
        }
        J5JsonParserSupport.warnUnknownFields(team, TEAM_FIELDS, path + ".team", warnings);
        return Optional.ofNullable(J5JsonParserSupport.requiredPositiveLong(
                team.get("id"), path + ".team.id", problems));
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
        Long id = J5JsonParserSupport.requiredPositiveLong(node.get("id"), path + ".id", problems);
        String name = J5JsonParserSupport.requiredText(
                node.get("name"), path + ".name", 200, problems);
        return id == null || name == null ? Player.invalid() : Player.of(id, name);
    }

    private J5ParseEvidence evidence(
            long snapshotId,
            RawPayloadEvidence payload,
            Instant receivedAt) {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        return new J5ParseEvidence(
                "snapshot:" + snapshotId,
                SofascoreEndpointType.EVENT_INCIDENTS,
                Objects.requireNonNull(payload, "payload").sha256(),
                Optional.empty(),
                Objects.requireNonNull(receivedAt, "receivedAt"),
                parserVersion());
    }

    protected Set<String> incidentFields() {
        return INCIDENT_FIELDS;
    }

    protected Integer normalizedMinute(
            String incidentType,
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return J5JsonParserSupport.requiredInteger(
                item.get("time"), path + ".time", 0, 300, problems);
    }

    protected Optional<String> normalizedIncidentClass(
            String incidentType,
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return Optional.empty();
    }

    protected Optional<String> normalizedReason(
            String incidentType,
            JsonNode item,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return Optional.empty();
    }

    protected String parserVersion() {
        return PARSER_VERSION;
    }

    private static J5ParseResult<EventIncidents> incompatible(
            J5ParseEvidence evidence,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return J5ParseResult.failed(
                J5ParseStatus.SCHEMA_INCOMPATIBLE, evidence, warnings, problems);
    }

    private static J5ParseResult<EventIncidents> failed(
            J5ParseStatus status,
            J5ParseEvidence evidence,
            J5ParseProblem.Code code,
            String path,
            String message) {
        return J5ParseResult.failed(status, evidence, List.of(),
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

        boolean present() {
            return id.isPresent();
        }
    }
}
