package com.bettingproject.sofascorelocal.domain.eventdetails;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
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

    public EventDetailObservation {
        identity = Objects.requireNonNull(identity, "identity");
        details = Objects.requireNonNull(details, "details");
        source = Objects.requireNonNull(source, "source");
        normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
        if (identity.providerEventId() != details.providerEventId()) {
            throw new IllegalArgumentException(
                    "event detail provider identity must match its canonical event");
        }
        if (source.kind() != EventSourceKind.SYNTHETIC_FIXTURE) {
            throw new IllegalArgumentException(
                    "J4 event details accept synthetic fixture provenance only");
        }
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
                output.writeUTF("event-detail-observation-v1");
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
            }
            return Sha256.hex(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException("unable to hash event detail observation", exception);
        }
    }
}
