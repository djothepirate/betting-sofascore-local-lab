package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class J4EventQueryService {

    public static final String DEFAULT_ZONE_ID = "Europe/Paris";

    private final CanonicalEventStore canonicalEventStore;
    private final EventDetailsStore eventDetailsStore;

    public J4EventQueryService(
            CanonicalEventStore canonicalEventStore,
            EventDetailsStore eventDetailsStore) {
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore,
                "canonicalEventStore");
        this.eventDetailsStore = Objects.requireNonNull(eventDetailsStore, "eventDetailsStore");
    }

    public J4EventSearchResult search(LocalDate date, String zoneId) {
        Objects.requireNonNull(date, "date");
        ZoneId zone = resolveZone(zoneId);
        Instant fromInclusive = date.atStartOfDay(zone).toInstant();
        Instant toExclusive = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<J4EventSearchItem> events = canonicalEventStore.findLatestStartingBetween(
                        fromInclusive,
                        toExclusive)
                .stream()
                .map(event -> item(event, zone))
                .toList();
        return new J4EventSearchResult(
                date,
                zone,
                fromInclusive,
                toExclusive,
                events);
    }

    public Optional<J4EventDetailResult> findDetail(UUID canonicalEventId, String zoneId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        ZoneId zone = resolveZone(zoneId);
        return canonicalEventStore.findLatestByCanonicalId(canonicalEventId)
                .map(current -> new J4EventDetailResult(
                        zone,
                        item(current, zone),
                        canonicalEventStore.findHistory(canonicalEventId)
                                .stream()
                                .map(event -> item(event, zone))
                                .toList(),
                        eventDetailsStore.findLatest(canonicalEventId)));
    }

    public ZoneId resolveZone(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException("zoneId must be bounded non-blank text");
        }
        try {
            return ZoneId.of(value.trim());
        }
        catch (DateTimeException exception) {
            throw new IllegalArgumentException("zoneId is not recognized", exception);
        }
    }

    private static J4EventSearchItem item(
            CanonicalEventObservationView event,
            ZoneId zone) {
        return new J4EventSearchItem(
                event,
                event.startsAt().atZone(zone));
    }
}
