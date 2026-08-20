package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.export.J7ExportComponent;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotTrace;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pure deterministic assembler for the versioned J7 JSON envelope.
 *
 * <p>This class performs no database, filesystem, schema-registry or network access. It maps only
 * normalized domain views and provenance metadata selected by {@link J7CurrentEventSelectionReader}.
 * Contract/schema validation and sensitive-content scanning remain separate gates.</p>
 */
@Component
public final class J7EnvelopeAssembler {

    public static final String SCHEMA_ID = J7ExportContract.SCHEMA_ID;
    public static final String SCHEMA_VERSION = J7ExportContract.SCHEMA_VERSION;
    public static final String SELECTION_MODE = J7ExportContract.SELECTION_MODE;

    private static final String PRESENT = "PRESENT";
    private static final String UNAVAILABLE = "UNAVAILABLE";
    private static final String MISSING = "MISSING";
    private static final String EMPTY_VALID = "EMPTY_VALID";
    private static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    private static final Pattern GENERATOR_VERSION_PATTERN =
            Pattern.compile("[A-Za-z0-9._+-]{1,64}");
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public J7AssembledEnvelope assembleCandidate(
            UUID exportId,
            Instant generatedAt,
            String generatorVersion,
            J7CurrentEventSelection selection) {
        Objects.requireNonNull(exportId, "exportId");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(selection, "selection");
        String normalizedGeneratorVersion = requireGeneratorVersion(generatorVersion);

        verifyNormalizedObservations(selection);
        List<SourceSlot> slots = resolveSourceSlots(selection);
        ObjectNode data = dataNode(selection, slots);
        byte[] dataBytes = writeCompact(data);
        String dataSha256 = Sha256.hex(dataBytes);

        ArrayNode sources = sourcesNode(slots);
        String sourceSetSha256 = Sha256.hex(writeCompact(sources));
        ArrayNode warnings = warningsNode(slots);

        ObjectNode manifest = JSON_MAPPER.createObjectNode();
        manifest.put("exportId", exportId.toString());
        manifest.put("schemaId", SCHEMA_ID);
        manifest.put("schemaVersion", SCHEMA_VERSION);
        manifest.put("generatedAt", generatedAt.toString());
        manifest.put("generatorVersion", normalizedGeneratorVersion);
        manifest.put("selectionMode", SELECTION_MODE);
        manifest.put("dataSha256", dataSha256);
        manifest.put("sourceSetSha256", sourceSetSha256);
        manifest.set("validation", validationNode(
                J7ExportStatus.COHERENCE_CHECKED,
                null,
                null));
        manifest.set("sources", sources);
        manifest.set("warnings", warnings);

        ObjectNode envelope = JSON_MAPPER.createObjectNode();
        envelope.set("manifest", manifest);
        envelope.set("data", data);

        CanonicalEventIdentity identity = selection.eventState().identity();
        return result(
                exportId,
                identity.value(),
                J7ExportStatus.COHERENCE_CHECKED,
                generatedAt,
                Optional.empty(),
                envelope,
                dataSha256,
                sourceSetSha256,
                slots,
                sources,
                warnings);
    }

    public J7AssembledEnvelope decide(
            byte[] candidateBytes,
            J7ExportStatus terminalStatus,
            Instant decidedAt,
            String rejectionReasonOrNull) {
        Objects.requireNonNull(candidateBytes, "candidateBytes");
        Objects.requireNonNull(terminalStatus, "terminalStatus");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (!terminalStatus.isTerminal()) {
            throw new J7ExportException(J7ExportError.INVALID_TRANSITION);
        }
        String rejectionReason = requireDecisionReason(
                terminalStatus,
                rejectionReasonOrNull);

        ObjectNode envelope = readEnvelope(candidateBytes);
        ObjectNode manifest = requireObject(envelope.get("manifest"));
        ObjectNode data = requireObject(envelope.get("data"));
        requireContractIdentity(manifest);

        ObjectNode candidateValidation = requireObject(manifest.get("validation"));
        if (!J7ExportStatus.COHERENCE_CHECKED.name().equals(
                requireText(candidateValidation.get("status")))) {
            throw new J7ExportException(J7ExportError.INVALID_TRANSITION);
        }

        String embeddedDataSha256 = requireSha256(manifest.get("dataSha256"));
        String calculatedDataSha256 = Sha256.hex(writeCompact(data));
        if (!embeddedDataSha256.equals(calculatedDataSha256)) {
            throw new J7ExportException(J7ExportError.INVALID_HASH);
        }

        ArrayNode sources = requireArray(manifest.get("sources"));
        String embeddedSourceSetSha256 = requireSha256(manifest.get("sourceSetSha256"));
        String calculatedSourceSetSha256 = Sha256.hex(writeCompact(sources));
        if (!embeddedSourceSetSha256.equals(calculatedSourceSetSha256)) {
            throw new J7ExportException(J7ExportError.INVALID_HASH);
        }
        ArrayNode warnings = requireArray(manifest.get("warnings"));

        UUID exportId = parseUuid(requireText(manifest.get("exportId")));
        Instant generatedAt = parseInstant(requireText(manifest.get("generatedAt")));
        if (decidedAt.isBefore(generatedAt)) {
            throw new J7ExportException(J7ExportError.INVALID_TRANSITION);
        }
        ObjectNode identity = requireObject(data.get("identity"));
        UUID canonicalEventId = parseUuid(requireText(identity.get("canonicalEventId")));

        manifest.set("validation", validationNode(
                terminalStatus,
                decidedAt,
                rejectionReason));

        String terminalDataSha256 = Sha256.hex(writeCompact(requireObject(
                envelope.get("data"))));
        if (!embeddedDataSha256.equals(terminalDataSha256)) {
            throw new J7ExportException(J7ExportError.INVALID_HASH);
        }

        return new J7AssembledEnvelope(
                exportId,
                canonicalEventId,
                terminalStatus,
                generatedAt,
                Optional.of(decidedAt),
                writeEnvelope(envelope),
                embeddedDataSha256,
                embeddedSourceSetSha256,
                snapshotIds(sources),
                new String(writeCompact(sources), StandardCharsets.UTF_8),
                new String(writeCompact(warnings), StandardCharsets.UTF_8));
    }

    private static void verifyNormalizedObservations(J7CurrentEventSelection selection) {
        CanonicalEventObservationView eventState = selection.eventState();
        CanonicalEventIdentity identity = eventState.identity();
        CanonicalEventObservation recomputedEvent;
        try {
            ScheduledEvent scheduledEvent = new ScheduledEvent(
                    identity.providerEventId(),
                    eventState.startsAt(),
                    eventState.homeTeam(),
                    eventState.awayTeam(),
                    eventState.status(),
                    eventState.tournament());
            recomputedEvent = CanonicalEventObservation.from(
                    scheduledEvent,
                    eventState.source());
        }
        catch (IllegalArgumentException exception) {
            throw new J7ExportException(J7ExportError.INCOHERENT_SOURCE, exception);
        }
        if (!identity.equals(recomputedEvent.identity())) {
            throw new J7ExportException(J7ExportError.IDENTITY_MISMATCH);
        }
        requireMatchingNormalizedHash(
                eventState.normalizedSha256(),
                recomputedEvent.normalizedSha256());

        selection.eventDetails().ifPresent(value -> {
            requireSameIdentity(identity, value.identity());
            EventDetailObservation recomputed = recomputeDetails(value);
            requireMatchingNormalizedHash(
                    value.normalizedSha256(),
                    recomputed.normalizedSha256());
        });
        selection.eventData().statistics().ifPresent(value -> verifyJ5(identity, value));
        selection.eventData().incidents().ifPresent(value -> verifyJ5(identity, value));
        selection.eventData().lineups().ifPresent(value -> verifyJ5(identity, value));
    }

    private static EventDetailObservation recomputeDetails(EventDetailObservationView value) {
        try {
            return EventDetailObservation.from(
                    value.identity(),
                    value.details(),
                    value.source());
        }
        catch (IllegalArgumentException exception) {
            throw new J7ExportException(J7ExportError.INCOHERENT_SOURCE, exception);
        }
    }

    private static void verifyJ5(
            CanonicalEventIdentity identity,
            J5EventDataObservationView value) {
        requireSameIdentity(identity, value.identity());
        try {
            J5EventDataObservation recomputed = J5EventDataObservation.from(
                    value.identity(),
                    value.data(),
                    value.source(),
                    value.completeness());
            requireMatchingNormalizedHash(
                    value.normalizedSha256(),
                    recomputed.normalizedSha256());
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (IllegalArgumentException exception) {
            throw new J7ExportException(J7ExportError.INCOHERENT_SOURCE, exception);
        }
    }

    private static void requireSameIdentity(
            CanonicalEventIdentity expected,
            CanonicalEventIdentity actual) {
        if (!expected.equals(actual)) {
            throw new J7ExportException(J7ExportError.IDENTITY_MISMATCH);
        }
    }

    private static void requireMatchingNormalizedHash(String stored, String calculated) {
        if (stored == null
                || !SHA_256_PATTERN.matcher(stored).matches()
                || !stored.equals(calculated)) {
            throw new J7ExportException(J7ExportError.INVALID_HASH);
        }
    }

    private static List<SourceSlot> resolveSourceSlots(J7CurrentEventSelection selection) {
        List<SourceSlot> slots = new ArrayList<>(J7ExportComponent.values().length);
        CanonicalEventObservationView eventState = selection.eventState();
        slots.add(resolvePresent(
                J7ExportComponent.EVENT_STATE,
                PRESENT,
                eventState.observationId(),
                eventState.source(),
                eventState.normalizedSha256(),
                null,
                selection.snapshotTraces()));
        slots.add(selection.eventDetails()
                .map(value -> resolvePresent(
                        J7ExportComponent.EVENT_DETAILS,
                        PRESENT,
                        value.observationId(),
                        value.source(),
                        value.normalizedSha256(),
                        null,
                        selection.snapshotTraces()))
                .orElseGet(() -> SourceSlot.missing(J7ExportComponent.EVENT_DETAILS)));
        slots.add(j5Slot(
                J7ExportComponent.EVENT_STATISTICS,
                selection.eventData().statistics(),
                selection.snapshotTraces()));
        slots.add(j5Slot(
                J7ExportComponent.EVENT_INCIDENTS,
                selection.eventData().incidents(),
                selection.snapshotTraces()));
        slots.add(j5Slot(
                J7ExportComponent.EVENT_LINEUPS,
                selection.eventData().lineups(),
                selection.snapshotTraces()));
        return List.copyOf(slots);
    }

    private static SourceSlot j5Slot(
            J7ExportComponent component,
            Optional<J5EventDataObservationView> observation,
            Map<Long, J6SnapshotTrace> snapshotTraces) {
        return observation
                .map(value -> resolvePresent(
                        component,
                        availability(value.completeness().status()),
                        value.observationId(),
                        value.source(),
                        value.normalizedSha256(),
                        value.completeness(),
                        snapshotTraces))
                .orElseGet(() -> SourceSlot.missing(component));
    }

    private static SourceSlot resolvePresent(
            J7ExportComponent component,
            String availability,
            long observationId,
            EventSourceTrace source,
            String normalizedSha256,
            J5CompletenessReport completeness,
            Map<Long, J6SnapshotTrace> snapshotTraces) {
        String rawPayloadState;
        if (source.kind() == EventSourceKind.SYNTHETIC_FIXTURE) {
            rawPayloadState = NOT_APPLICABLE;
        }
        else {
            long snapshotId = source.snapshotId().orElseThrow(() ->
                    new J7ExportException(J7ExportError.INCOHERENT_SOURCE));
            J6SnapshotTrace trace = snapshotTraces.get(snapshotId);
            if (trace == null
                    || trace.snapshotId() != snapshotId
                    || !source.payloadSha256().equals(trace.payloadSha256())) {
                throw new J7ExportException(J7ExportError.INCOHERENT_SOURCE);
            }
            rawPayloadState = trace.rawPayloadState().name();
        }
        return new SourceSlot(
                component,
                availability,
                observationId,
                source,
                normalizedSha256,
                completeness,
                rawPayloadState);
    }

    private static String availability(J5CompletenessStatus status) {
        return switch (status) {
            case COMPLETE, PARTIAL -> PRESENT;
            case EMPTY_VALID -> EMPTY_VALID;
            case UNAVAILABLE -> UNAVAILABLE;
        };
    }

    private static ObjectNode dataNode(
            J7CurrentEventSelection selection,
            List<SourceSlot> slots) {
        ObjectNode data = JSON_MAPPER.createObjectNode();
        data.set("identity", identityNode(selection.eventState().identity()));
        data.set("eventState", eventStateNode(selection.eventState()));
        data.set("eventDetails", eventDetailsNode(selection.eventDetails()));
        data.set("statistics", statisticsNode(
                slot(slots, J7ExportComponent.EVENT_STATISTICS),
                selection.eventData().statistics()));
        data.set("incidents", incidentsNode(
                slot(slots, J7ExportComponent.EVENT_INCIDENTS),
                selection.eventData().incidents()));
        data.set("lineups", lineupsNode(
                slot(slots, J7ExportComponent.EVENT_LINEUPS),
                selection.eventData().lineups()));
        return data;
    }

    private static ObjectNode identityNode(CanonicalEventIdentity identity) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("canonicalEventId", identity.value().toString());
        node.put("provider", identity.provider());
        node.put("providerEventId", identity.providerEventId());
        return node;
    }

    private static ObjectNode eventStateNode(CanonicalEventObservationView eventState) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("availability", PRESENT);
        node.put("startsAt", eventState.startsAt().toString());
        node.set("homeTeam", teamNode(eventState.homeTeam()));
        node.set("awayTeam", teamNode(eventState.awayTeam()));
        node.set("status", statusNode(eventState.status()));
        putTournament(node, "tournament", eventState.tournament());
        return node;
    }

    private static ObjectNode eventDetailsNode(
            Optional<EventDetailObservationView> observation) {
        ObjectNode slot = JSON_MAPPER.createObjectNode();
        if (observation.isEmpty()) {
            slot.put("availability", MISSING);
            slot.putNull("details");
            return slot;
        }
        EventDetails details = observation.orElseThrow().details();
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("startsAt", details.startsAt().toString());
        node.set("homeTeam", teamNode(details.homeTeam()));
        node.set("awayTeam", teamNode(details.awayTeam()));
        node.set("status", statusNode(details.status()));
        putTournament(node, "tournament", details.tournament());
        if (details.venue().isPresent()) {
            var venue = details.venue().orElseThrow();
            ObjectNode venueNode = JSON_MAPPER.createObjectNode();
            venueNode.put("providerVenueId", venue.providerVenueId());
            venueNode.put("name", venue.name());
            putNullableText(venueNode, "city", venue.city());
            node.set("venue", venueNode);
        }
        else {
            node.putNull("venue");
        }
        if (details.season().isPresent()) {
            var season = details.season().orElseThrow();
            ObjectNode seasonNode = JSON_MAPPER.createObjectNode();
            seasonNode.put("providerSeasonId", season.providerSeasonId());
            seasonNode.put("name", season.name());
            node.set("season", seasonNode);
        }
        else {
            node.putNull("season");
        }
        putNullableText(node, "round", details.round());

        slot.put("availability", PRESENT);
        slot.set("details", node);
        return slot;
    }

    private static ObjectNode statisticsNode(
            SourceSlot sourceSlot,
            Optional<J5EventDataObservationView> observation) {
        ObjectNode node = familySlotHeader(sourceSlot);
        if (sourceSlot.missing() || UNAVAILABLE.equals(sourceSlot.availability())) {
            node.putNull("metrics");
            return node;
        }
        ArrayNode metrics = JSON_MAPPER.createArrayNode();
        observation.ifPresent(value -> {
            if (!(value.data() instanceof EventStatistics statistics)) {
                throw new J7ExportException(J7ExportError.INCOHERENT_SOURCE);
            }
            for (EventStatisticMetric metric : statistics.metrics()) {
                ObjectNode metricNode = JSON_MAPPER.createObjectNode();
                metricNode.put("period", metric.period());
                metricNode.put("groupName", metric.groupName());
                metricNode.put("metricCode", metric.metricCode());
                metricNode.put("metricName", metric.metricName());
                putNullableText(metricNode, "homeValue", metric.homeValue());
                putNullableText(metricNode, "awayValue", metric.awayValue());
                metrics.add(metricNode);
            }
        });
        node.set("metrics", metrics);
        return node;
    }

    private static ObjectNode incidentsNode(
            SourceSlot sourceSlot,
            Optional<J5EventDataObservationView> observation) {
        ObjectNode node = familySlotHeader(sourceSlot);
        if (sourceSlot.missing() || UNAVAILABLE.equals(sourceSlot.availability())) {
            node.putNull("incidents");
            return node;
        }
        ArrayNode incidents = JSON_MAPPER.createArrayNode();
        observation.ifPresent(value -> {
            if (!(value.data() instanceof EventIncidents eventIncidents)) {
                throw new J7ExportException(J7ExportError.INCOHERENT_SOURCE);
            }
            for (EventIncident incident : eventIncidents.incidents()) {
                incidents.add(incidentNode(incident));
            }
        });
        node.set("incidents", incidents);
        return node;
    }

    private static ObjectNode incidentNode(EventIncident incident) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("sequence", incident.sequence());
        node.put("incidentType", incident.incidentType());
        putNullableInteger(node, "minute", incident.minute());
        putNullableInteger(node, "addedTime", incident.addedTime());
        putNullableBoolean(node, "home", incident.home());
        putNullableLong(node, "participantProviderId", incident.participantProviderId());
        putNullableLong(node, "playerProviderId", incident.playerProviderId());
        putNullableText(node, "playerName", incident.playerName());
        putNullableLong(node, "playerInProviderId", incident.playerInProviderId());
        putNullableText(node, "playerInName", incident.playerInName());
        putNullableLong(node, "playerOutProviderId", incident.playerOutProviderId());
        putNullableText(node, "playerOutName", incident.playerOutName());
        putNullableInteger(node, "homeScore", incident.homeScore());
        putNullableInteger(node, "awayScore", incident.awayScore());
        putNullableText(node, "incidentClass", incident.incidentClass());
        putNullableText(node, "reason", incident.reason());
        putNullableText(node, "periodText", incident.periodText());
        putNullableBoolean(node, "injury", incident.injury());
        putNullableLong(node, "assistProviderId", incident.assistProviderId());
        putNullableText(node, "assistName", incident.assistName());
        putNullableText(node, "goalOrigin", incident.goalOrigin());
        putNullableInteger(node, "injuryTimeLength", incident.injuryTimeLength());
        putNullableBoolean(node, "varConfirmed", incident.varConfirmed());
        putNullableBoolean(node, "rescinded", incident.rescinded());
        putNullableText(node, "description", incident.description());
        putNullableInteger(node, "shootoutSequence", incident.shootoutSequence());
        return node;
    }

    private static ObjectNode lineupsNode(
            SourceSlot sourceSlot,
            Optional<J5EventDataObservationView> observation) {
        ObjectNode node = familySlotHeader(sourceSlot);
        if (sourceSlot.missing() || UNAVAILABLE.equals(sourceSlot.availability())) {
            node.putNull("lineups");
            return node;
        }
        if (!(observation.orElseThrow().data() instanceof EventLineups lineups)) {
            throw new J7ExportException(J7ExportError.INCOHERENT_SOURCE);
        }
        ObjectNode lineupsNode = JSON_MAPPER.createObjectNode();
        lineupsNode.put("confirmed", lineups.confirmed());
        lineupsNode.set("home", teamLineupNode(lineups.home()));
        lineupsNode.set("away", teamLineupNode(lineups.away()));
        node.set("lineups", lineupsNode);
        return node;
    }

    private static ObjectNode teamLineupNode(TeamLineup lineup) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("side", lineup.side().name());
        putNullableText(node, "formation", lineup.formation());
        ArrayNode players = JSON_MAPPER.createArrayNode();
        for (EventLineupPlayer player : lineup.players()) {
            ObjectNode playerNode = JSON_MAPPER.createObjectNode();
            playerNode.put("providerPlayerId", player.providerPlayerId());
            playerNode.put("name", player.name());
            putNullableInteger(playerNode, "shirtNumber", player.shirtNumber());
            putNullableText(playerNode, "position", player.position());
            playerNode.put("starter", player.starter());
            players.add(playerNode);
        }
        node.set("players", players);
        return node;
    }

    private static ObjectNode familySlotHeader(SourceSlot sourceSlot) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("availability", sourceSlot.availability());
        if (sourceSlot.completeness() == null) {
            node.putNull("completeness");
        }
        else {
            node.set("completeness", completenessNode(sourceSlot.completeness()));
        }
        return node;
    }

    private static ObjectNode teamNode(ScheduledTeam team) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("providerTeamId", team.providerTeamId());
        node.put("name", team.name());
        return node;
    }

    private static ObjectNode statusNode(ScheduledEventStatus status) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("type", status.type());
        putNullableText(node, "description", status.description());
        return node;
    }

    private static void putTournament(
            ObjectNode parent,
            String field,
            Optional<ScheduledTournament> tournament) {
        if (tournament.isEmpty()) {
            parent.putNull(field);
            return;
        }
        ScheduledTournament value = tournament.orElseThrow();
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("providerTournamentId", value.providerTournamentId());
        node.put("name", value.name());
        parent.set(field, node);
    }

    private static ArrayNode sourcesNode(List<SourceSlot> slots) {
        ArrayNode sources = JSON_MAPPER.createArrayNode();
        for (SourceSlot slot : slots) {
            ObjectNode node = JSON_MAPPER.createObjectNode();
            node.put("component", slot.component().name());
            node.put("availability", slot.availability());
            if (slot.missing()) {
                node.putNull("observationId");
                node.putNull("sourceKind");
                node.putNull("sourceReference");
                node.putNull("snapshotId");
                node.putNull("fixtureId");
                node.putNull("sourceSha256");
                node.putNull("normalizedSha256");
                node.putNull("parserVersion");
                node.putNull("receivedAt");
                node.putNull("rawPayloadState");
                node.putNull("completeness");
            }
            else {
                EventSourceTrace source = Objects.requireNonNull(slot.source(), "source");
                node.put("observationId", slot.observationId());
                node.put("sourceKind", source.kind().name());
                node.put("sourceReference", source.sourceReference());
                if (source.snapshotId().isPresent()) {
                    node.put("snapshotId", source.snapshotId().getAsLong());
                }
                else {
                    node.putNull("snapshotId");
                }
                putNullableText(node, "fixtureId", source.fixtureId());
                node.put("sourceSha256", source.payloadSha256());
                node.put("normalizedSha256", slot.normalizedSha256());
                node.put("parserVersion", source.parserVersion());
                node.put("receivedAt", source.receivedAt().toString());
                node.put("rawPayloadState", slot.rawPayloadState());
                if (slot.completeness() == null) {
                    node.putNull("completeness");
                }
                else {
                    node.set("completeness", completenessNode(slot.completeness()));
                }
            }
            sources.add(node);
        }
        return sources;
    }

    private static ObjectNode completenessNode(J5CompletenessReport completeness) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("status", completeness.status().name());
        node.put("scorePercent", completeness.scorePercent());
        node.put("presentSignals", completeness.presentSignals());
        node.put("expectedSignals", completeness.expectedSignals());
        ArrayNode paths = JSON_MAPPER.createArrayNode();
        completeness.missingPaths().forEach(paths::add);
        node.set("missingPaths", paths);
        return node;
    }

    private static ArrayNode warningsNode(List<SourceSlot> slots) {
        ArrayNode warnings = JSON_MAPPER.createArrayNode();
        appendWarnings(warnings, slots, "MISSING_COMPONENT", slot -> slot.missing());
        appendWarnings(
                warnings,
                slots,
                "UNAVAILABLE_COMPONENT",
                slot -> UNAVAILABLE.equals(slot.availability()));
        appendWarnings(
                warnings,
                slots,
                "PARTIAL_COMPLETENESS",
                slot -> slot.completeness() != null
                        && slot.completeness().status() == J5CompletenessStatus.PARTIAL);
        appendWarnings(
                warnings,
                slots,
                "SYNTHETIC_SOURCE",
                slot -> !slot.missing()
                        && slot.source().kind() == EventSourceKind.SYNTHETIC_FIXTURE);

        EnumSet<EventSourceKind> sourceKinds = EnumSet.noneOf(EventSourceKind.class);
        slots.stream()
                .filter(slot -> !slot.missing())
                .map(SourceSlot::source)
                .map(EventSourceTrace::kind)
                .forEach(sourceKinds::add);
        if (sourceKinds.size() > 1) {
            warnings.add(warningNode("MIXED_SOURCE_KINDS", null));
        }

        appendWarnings(
                warnings,
                slots,
                "RAW_PAYLOAD_PURGED",
                slot -> "PAYLOAD_PURGED".equals(slot.rawPayloadState()));
        appendWarnings(
                warnings,
                slots,
                "RAW_PAYLOAD_LEGACY_ABSENT",
                slot -> "LEGACY_ABSENT".equals(slot.rawPayloadState()));
        return warnings;
    }

    private static void appendWarnings(
            ArrayNode warnings,
            List<SourceSlot> slots,
            String code,
            java.util.function.Predicate<SourceSlot> predicate) {
        slots.stream()
                .filter(predicate)
                .map(slot -> warningNode(code, slot.component()))
                .forEach(warnings::add);
    }

    private static ObjectNode warningNode(String code, J7ExportComponent component) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("code", code);
        if (component == null) {
            node.putNull("component");
        }
        else {
            node.put("component", component.name());
        }
        return node;
    }

    private static ObjectNode validationNode(
            J7ExportStatus status,
            Instant decidedAt,
            String rejectionReason) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("status", status.name());
        if (decidedAt == null) {
            node.putNull("decidedAt");
        }
        else {
            node.put("decidedAt", decidedAt.toString());
        }
        if (rejectionReason == null) {
            node.putNull("rejectionReason");
        }
        else {
            node.put("rejectionReason", rejectionReason);
        }
        return node;
    }

    private static J7AssembledEnvelope result(
            UUID exportId,
            UUID canonicalEventId,
            J7ExportStatus status,
            Instant generatedAt,
            Optional<Instant> decidedAt,
            ObjectNode envelope,
            String dataSha256,
            String sourceSetSha256,
            List<SourceSlot> slots,
            ArrayNode sources,
            ArrayNode warnings) {
        TreeSet<Long> snapshotIds = new TreeSet<>();
        slots.stream()
                .filter(slot -> !slot.missing())
                .map(SourceSlot::source)
                .filter(source -> source.snapshotId().isPresent())
                .map(source -> source.snapshotId().getAsLong())
                .forEach(snapshotIds::add);
        return new J7AssembledEnvelope(
                exportId,
                canonicalEventId,
                status,
                generatedAt,
                decidedAt,
                writeEnvelope(envelope),
                dataSha256,
                sourceSetSha256,
                List.copyOf(snapshotIds),
                new String(writeCompact(sources), StandardCharsets.UTF_8),
                new String(writeCompact(warnings), StandardCharsets.UTF_8));
    }

    private static byte[] writeEnvelope(ObjectNode envelope) {
        byte[] compact = writeCompact(envelope);
        byte[] withLf = Arrays.copyOf(compact, compact.length + 1);
        withLf[withLf.length - 1] = (byte) '\n';
        return withLf;
    }

    private static byte[] writeCompact(JsonNode node) {
        try {
            return JSON_MAPPER.writeValueAsBytes(node);
        }
        catch (JacksonException exception) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA, exception);
        }
    }

    private static ObjectNode readEnvelope(byte[] content) {
        try {
            JsonNode parsed = JSON_MAPPER.readTree(content);
            return requireObject(parsed);
        }
        catch (JacksonException exception) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA, exception);
        }
    }

    private static void requireContractIdentity(ObjectNode manifest) {
        if (!SCHEMA_ID.equals(requireText(manifest.get("schemaId")))
                || !SCHEMA_VERSION.equals(requireText(manifest.get("schemaVersion")))
                || !SELECTION_MODE.equals(requireText(manifest.get("selectionMode")))) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
    }

    private static ObjectNode requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
        return (ObjectNode) node;
    }

    private static ArrayNode requireArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
        return (ArrayNode) node;
    }

    private static String requireText(JsonNode node) {
        if (node == null || !node.isString()) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
        return node.stringValue();
    }

    private static String requireSha256(JsonNode node) {
        String value = requireText(node);
        if (!SHA_256_PATTERN.matcher(value).matches()) {
            throw new J7ExportException(J7ExportError.INVALID_HASH);
        }
        return value;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException exception) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA, exception);
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        }
        catch (DateTimeException exception) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA, exception);
        }
    }

    private static List<Long> snapshotIds(ArrayNode sources) {
        TreeSet<Long> result = new TreeSet<>();
        sources.valueStream().forEach(source -> {
            JsonNode snapshotId = source.get("snapshotId");
            if (snapshotId != null && !snapshotId.isNull()) {
                if (!snapshotId.isIntegralNumber() || snapshotId.longValue() < 1) {
                    throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
                }
                result.add(snapshotId.longValue());
            }
        });
        return List.copyOf(result);
    }

    private static String requireGeneratorVersion(String generatorVersion) {
        if (generatorVersion == null) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
        String normalized = generatorVersion.trim();
        if (!GENERATOR_VERSION_PATTERN.matcher(normalized).matches()) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
        return normalized;
    }

    private static String requireDecisionReason(
            J7ExportStatus terminalStatus,
            String rejectionReasonOrNull) {
        if (terminalStatus == J7ExportStatus.HUMAN_VALIDATED) {
            if (rejectionReasonOrNull != null) {
                throw new J7ExportException(J7ExportError.INVALID_REJECTION_REASON);
            }
            return null;
        }
        if (rejectionReasonOrNull == null
                || rejectionReasonOrNull.isBlank()
                || rejectionReasonOrNull.length() > 500
                || rejectionReasonOrNull.chars().anyMatch(value ->
                        Character.isISOControl(value) || value == 0x7f)) {
            throw new J7ExportException(J7ExportError.INVALID_REJECTION_REASON);
        }
        return rejectionReasonOrNull;
    }

    private static SourceSlot slot(
            List<SourceSlot> slots,
            J7ExportComponent component) {
        return slots.get(component.ordinal());
    }

    private static void putNullableText(
            ObjectNode node,
            String field,
            Optional<String> value) {
        if (value.isPresent()) {
            node.put(field, value.orElseThrow());
        }
        else {
            node.putNull(field);
        }
    }

    private static void putNullableInteger(
            ObjectNode node,
            String field,
            Optional<Integer> value) {
        if (value.isPresent()) {
            node.put(field, value.orElseThrow());
        }
        else {
            node.putNull(field);
        }
    }

    private static void putNullableLong(
            ObjectNode node,
            String field,
            Optional<Long> value) {
        if (value.isPresent()) {
            node.put(field, value.orElseThrow());
        }
        else {
            node.putNull(field);
        }
    }

    private static void putNullableBoolean(
            ObjectNode node,
            String field,
            Optional<Boolean> value) {
        if (value.isPresent()) {
            node.put(field, value.orElseThrow());
        }
        else {
            node.putNull(field);
        }
    }

    private record SourceSlot(
            J7ExportComponent component,
            String availability,
            Long observationId,
            EventSourceTrace source,
            String normalizedSha256,
            J5CompletenessReport completeness,
            String rawPayloadState) {

        private SourceSlot {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(availability, "availability");
        }

        private static SourceSlot missing(J7ExportComponent component) {
            return new SourceSlot(component, MISSING, null, null, null, null, null);
        }

        private boolean missing() {
            return MISSING.equals(availability);
        }
    }
}
