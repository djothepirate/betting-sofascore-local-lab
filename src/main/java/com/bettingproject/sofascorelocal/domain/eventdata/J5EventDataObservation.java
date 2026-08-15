package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.security.Sha256;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record J5EventDataObservation(
        CanonicalEventIdentity identity,
        J5EventData data,
        EventSourceTrace source,
        J5CompletenessReport completeness,
        String normalizedSha256) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public J5EventDataObservation {
        identity = Objects.requireNonNull(identity, "identity");
        data = Objects.requireNonNull(data, "data");
        source = Objects.requireNonNull(source, "source");
        completeness = Objects.requireNonNull(completeness, "completeness");
        normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
        if (identity.providerEventId() != data.providerEventId()) {
            throw new IllegalArgumentException(
                    "J5 data provider identity must match its canonical event");
        }
        requireUnavailableShape(data, completeness);
        requireCompatibleProvenance(data, source, completeness);
        if (!SHA_256_PATTERN.matcher(normalizedSha256).matches()) {
            throw new IllegalArgumentException("normalizedSha256 must be a lower-case SHA-256");
        }
    }

    private static void requireUnavailableShape(
            J5EventData data,
            J5CompletenessReport completeness) {
        if (completeness.status() != J5CompletenessStatus.UNAVAILABLE) {
            return;
        }
        if (!J5UnavailableFamily.matchesEmptyObservation(data)) {
            throw new IllegalArgumentException(
                    "UNAVAILABLE J5 observations cannot contain normalized provider values");
        }
    }

    private static void requireCompatibleProvenance(
            J5EventData data,
            EventSourceTrace source,
            J5CompletenessReport completeness) {
        if (completeness.status() == J5CompletenessStatus.UNAVAILABLE) {
            if (!J5UnavailableFamily.normalizerVersion(data.endpointType())
                    .equals(source.parserVersion())) {
                throw new IllegalArgumentException(
                        "unavailable J5 observations require their availability normalizer");
            }
            return;
        }
        String expectedParserVersion = switch (data.endpointType()) {
            case EVENT_STATISTICS -> source.kind() == EventSourceKind.PROVIDER_SNAPSHOT
                    ? "event-statistics-v2"
                    : "event-statistics-v1";
            case EVENT_INCIDENTS -> source.kind() == EventSourceKind.PROVIDER_SNAPSHOT
                    ? "event-incidents-v3"
                    : "event-incidents-v1";
            case EVENT_LINEUPS -> source.kind() == EventSourceKind.PROVIDER_SNAPSHOT
                    ? "event-lineups-v2"
                    : "event-lineups-v1";
            default -> throw new IllegalArgumentException("unsupported J5 endpoint provenance");
        };
        if (!expectedParserVersion.equals(source.parserVersion())) {
            throw new IllegalArgumentException(
                    "J5 source kind, endpoint and parser version must match");
        }
    }

    public static J5EventDataObservation from(
            CanonicalEventIdentity identity,
            J5EventData data,
            EventSourceTrace source,
            J5CompletenessReport completeness) {
        return new J5EventDataObservation(
                identity,
                data,
                source,
                completeness,
                normalizedHash(data));
    }

    private static String normalizedHash(J5EventData data) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF("j5-event-data-observation-v1");
                output.writeUTF(data.endpointType().name());
                output.writeLong(data.providerEventId());
                switch (data) {
                    case EventStatistics statistics -> writeStatistics(output, statistics);
                    case EventIncidents incidents -> writeIncidents(output, incidents);
                    case EventLineups lineups -> writeLineups(output, lineups);
                }
            }
            return Sha256.hex(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException("unable to hash J5 normalized data", exception);
        }
    }

    private static void writeStatistics(
            DataOutputStream output,
            EventStatistics statistics) throws IOException {
        output.writeInt(statistics.metrics().size());
        for (EventStatisticMetric metric : statistics.metrics()) {
            output.writeUTF(metric.period());
            output.writeUTF(metric.groupName());
            output.writeUTF(metric.metricCode());
            output.writeUTF(metric.metricName());
            writeOptionalText(output, metric.homeValue());
            writeOptionalText(output, metric.awayValue());
        }
    }

    private static void writeIncidents(
            DataOutputStream output,
            EventIncidents incidents) throws IOException {
        output.writeInt(incidents.incidents().size());
        for (EventIncident incident : incidents.incidents()) {
            output.writeInt(incident.sequence());
            output.writeUTF(incident.incidentType());
            output.writeInt(incident.minute());
            writeOptionalInteger(output, incident.addedTime());
            writeOptionalBoolean(output, incident.home());
            writeOptionalLong(output, incident.participantProviderId());
            writeOptionalLong(output, incident.playerProviderId());
            writeOptionalText(output, incident.playerName());
            writeOptionalInteger(output, incident.homeScore());
            writeOptionalInteger(output, incident.awayScore());
        }
    }

    private static void writeLineups(
            DataOutputStream output,
            EventLineups lineups) throws IOException {
        output.writeBoolean(lineups.confirmed());
        writeTeamLineup(output, lineups.home());
        writeTeamLineup(output, lineups.away());
    }

    private static void writeTeamLineup(
            DataOutputStream output,
            TeamLineup lineup) throws IOException {
        output.writeUTF(lineup.side().name());
        writeOptionalText(output, lineup.formation());
        output.writeInt(lineup.players().size());
        for (EventLineupPlayer player : lineup.players()) {
            output.writeLong(player.providerPlayerId());
            output.writeUTF(player.name());
            writeOptionalInteger(output, player.shirtNumber());
            writeOptionalText(output, player.position());
            output.writeBoolean(player.starter());
        }
    }

    private static void writeOptionalText(
            DataOutputStream output,
            Optional<String> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeUTF(value.orElseThrow());
        }
    }

    private static void writeOptionalInteger(
            DataOutputStream output,
            Optional<Integer> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeInt(value.orElseThrow());
        }
    }

    private static void writeOptionalLong(
            DataOutputStream output,
            Optional<Long> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeLong(value.orElseThrow());
        }
    }

    private static void writeOptionalBoolean(
            DataOutputStream output,
            Optional<Boolean> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeBoolean(value.orElseThrow());
        }
    }
}
