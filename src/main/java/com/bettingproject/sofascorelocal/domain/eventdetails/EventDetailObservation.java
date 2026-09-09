package com.bettingproject.sofascorelocal.domain.eventdetails;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.security.Sha256;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

public record EventDetailObservation(
        CanonicalEventIdentity identity,
        EventDetails details,
        EventSourceTrace source,
        String normalizedSha256) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final String DISPLAY_SCORE_PARSER_VERSION = "event-details-v3";

    public EventDetailObservation {
        identity = Objects.requireNonNull(identity, "identity");
        details = Objects.requireNonNull(details, "details");
        source = Objects.requireNonNull(source, "source");
        normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
        if (identity.providerEventId() != details.providerEventId()) {
            throw new IllegalArgumentException(
                    "event detail provider identity must match its canonical event");
        }
        if (!DISPLAY_SCORE_PARSER_VERSION.equals(source.parserVersion()) && !"event-details-v4".equals(source.parserVersion())
                && (details.isAwarded().isPresent() || details.homeDisplayScore().isPresent()
                    || details.awayDisplayScore().isPresent())) {
            throw new IllegalArgumentException(
                    "award and displayed scores require event-details-v3 provenance");
        }
        if ((!"event-details-v4".equals(source.parserVersion()) || source.kind() != com.bettingproject.sofascorelocal.domain.event.EventSourceKind.PROVIDER_SNAPSHOT)
                && (details.homeManager().isPresent() || details.awayManager().isPresent() || details.referee().isPresent()))
            throw new IllegalArgumentException("event officials require event-details-v4 provenance");
        if (!SHA_256_PATTERN.matcher(normalizedSha256).matches()) {
            throw new IllegalArgumentException("normalizedSha256 must be a lower-case SHA-256");
        }
    }

    public static EventDetailObservation from(
            CanonicalEventIdentity identity,
            EventDetails details,
            EventSourceTrace source) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(source, "source");
        return new EventDetailObservation(
                identity,
                details,
                source,
                normalizedHash(details, source));
    }

    private static String normalizedHash(EventDetails details, EventSourceTrace source) {
        try {
            String eventHash = CanonicalEventObservation.from(
                    details.asScheduledEvent(),
                    source).normalizedSha256();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                boolean peopleContract = "event-details-v4".equals(source.parserVersion());
                boolean displayScoreContract = peopleContract || DISPLAY_SCORE_PARSER_VERSION.equals(source.parserVersion());
                output.writeUTF(peopleContract ? "event-detail-observation-v3" : displayScoreContract
                        ? "event-detail-observation-v2" : "event-detail-observation-v1");
                output.writeUTF(eventHash);
                output.writeBoolean(details.venue().isPresent());
                if (details.venue().isPresent()) {
                    EventVenue venue = details.venue().orElseThrow();
                    output.writeLong(venue.providerVenueId());
                    output.writeUTF(venue.name());
                    output.writeBoolean(venue.city().isPresent());
                    if (venue.city().isPresent()) {
                        output.writeUTF(venue.city().orElseThrow());
                    }
                }
                output.writeBoolean(details.season().isPresent());
                if (details.season().isPresent()) {
                    EventSeason season = details.season().orElseThrow();
                    output.writeLong(season.providerSeasonId());
                    output.writeUTF(season.name());
                }
                output.writeBoolean(details.round().isPresent());
                if (details.round().isPresent()) {
                    output.writeUTF(details.round().orElseThrow());
                }
                if (displayScoreContract) {
                    output.writeBoolean(details.isAwarded().isPresent());
                    if (details.isAwarded().isPresent()) {
                        output.writeBoolean(details.isAwarded().orElseThrow());
                    }
                    output.writeBoolean(details.homeDisplayScore().isPresent());
                    if (details.homeDisplayScore().isPresent()) {
                        output.writeInt(details.homeDisplayScore().orElseThrow());
                    }
                    output.writeBoolean(details.awayDisplayScore().isPresent());
                    if (details.awayDisplayScore().isPresent()) {
                        output.writeInt(details.awayDisplayScore().orElseThrow());
                    }
                }
                if (peopleContract) {
                    writePerson(output, details.homeManager());
                    writePerson(output, details.awayManager());
                    writePerson(output, details.referee());
                }
            }
            return Sha256.hex(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException("unable to hash event detail observation", exception);
        }
    }

    private static void writePerson(DataOutputStream out, java.util.Optional<EventPerson> person) throws IOException {
        out.writeBoolean(person.isPresent());
        if (person.isEmpty()) return;
        EventPerson value = person.orElseThrow();
        out.writeUTF(value.name());
        out.writeBoolean(value.country().isPresent());
        if (value.country().isPresent()) {
            var country = value.country().orElseThrow();
            out.writeBoolean(country.name().isPresent());
            if (country.name().isPresent()) out.writeUTF(country.name().orElseThrow());
            out.writeBoolean(country.alpha2().isPresent());
            if (country.alpha2().isPresent()) out.writeUTF(country.alpha2().orElseThrow());
        }
    }
}
