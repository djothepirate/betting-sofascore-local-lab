package com.bettingproject.sofascorelocal.domain.event;

import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.security.Sha256;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record CanonicalEventObservation(
        CanonicalEventIdentity identity,
        Instant startsAt,
        ScheduledTeam homeTeam,
        ScheduledTeam awayTeam,
        ScheduledEventStatus status,
        Optional<ScheduledTournament> tournament,
        EventSourceTrace source,
        String normalizedSha256) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public CanonicalEventObservation {
        identity = Objects.requireNonNull(identity, "identity");
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
        homeTeam = Objects.requireNonNull(homeTeam, "homeTeam");
        awayTeam = Objects.requireNonNull(awayTeam, "awayTeam");
        status = Objects.requireNonNull(status, "status");
        tournament = Objects.requireNonNull(tournament, "tournament");
        source = Objects.requireNonNull(source, "source");
        normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
        if (!SHA_256_PATTERN.matcher(normalizedSha256).matches()) {
            throw new IllegalArgumentException("normalizedSha256 must be a lower-case SHA-256");
        }
        requireBoundedText(homeTeam.name(), "homeTeam.name", 200);
        requireBoundedText(awayTeam.name(), "awayTeam.name", 200);
        requireBoundedText(status.type(), "status.type", 64);
        status.description().ifPresent(value ->
                requireBoundedText(value, "status.description", 200));
        tournament.ifPresent(value -> requireBoundedText(value.name(), "tournament.name", 200));
    }

    public static CanonicalEventObservation from(
            ScheduledEvent event,
            EventSourceTrace source) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(source, "source");
        CanonicalEventIdentity identity = CanonicalEventIdentity.sofascore(
                event.providerEventId());
        return new CanonicalEventObservation(
                identity,
                event.startsAt(),
                event.homeTeam(),
                event.awayTeam(),
                event.status(),
                event.tournament(),
                source,
                normalizedHash(event));
    }

    private static String normalizedHash(ScheduledEvent event) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF("canonical-event-observation-v1");
                output.writeLong(event.providerEventId());
                output.writeLong(event.startsAt().getEpochSecond());
                output.writeInt(event.startsAt().getNano());
                output.writeLong(event.homeTeam().providerTeamId());
                output.writeUTF(event.homeTeam().name());
                output.writeLong(event.awayTeam().providerTeamId());
                output.writeUTF(event.awayTeam().name());
                output.writeUTF(event.status().type());
                output.writeBoolean(event.status().description().isPresent());
                if (event.status().description().isPresent()) {
                    output.writeUTF(event.status().description().orElseThrow());
                }
                output.writeBoolean(event.tournament().isPresent());
                if (event.tournament().isPresent()) {
                    ScheduledTournament tournament = event.tournament().orElseThrow();
                    output.writeLong(tournament.providerTournamentId());
                    output.writeUTF(tournament.name());
                }
            }
            return Sha256.hex(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException("unable to hash canonical event observation", exception);
        }
    }

    private static void requireBoundedText(String value, String fieldName, int maximumLength) {
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " must contain between 1 and " + maximumLength + " characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + " cannot contain control characters");
        }
    }
}
