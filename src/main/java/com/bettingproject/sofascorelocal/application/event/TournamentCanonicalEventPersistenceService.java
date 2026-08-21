package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentScheduledEventsProjection;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class TournamentCanonicalEventPersistenceService {

    private final CanonicalEventStore canonicalEventStore;

    public TournamentCanonicalEventPersistenceService(
            CanonicalEventStore canonicalEventStore) {
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore, "canonicalEventStore");
    }

    @Transactional
    public TournamentCanonicalizationResult persist(
            TournamentScheduledEventsProjection projection,
            long snapshotId,
            String payloadSha256,
            Instant receivedAt) {
        Objects.requireNonNull(projection, "projection");
        if (!projection.normalizationAllowed()) {
            throw new IllegalArgumentException(
                    "a count mismatch cannot be persisted canonically");
        }
        EventSourceTrace source = EventSourceTrace.providerSnapshot(
                snapshotId,
                payloadSha256,
                TournamentScheduledEventsV1Parser.PARSER_VERSION,
                Objects.requireNonNull(receivedAt, "receivedAt"));
        int inserted = 0;
        int deduplicated = 0;
        List<TournamentDiscoveredEventView> events = new ArrayList<>();
        for (var event : projection.events()) {
            var persistence = canonicalEventStore.save(
                    CanonicalEventObservation.from(event, source));
            if (persistence.inserted()) {
                inserted++;
            }
            else {
                deduplicated++;
            }
            events.add(new TournamentDiscoveredEventView(
                    persistence.canonicalEventId(),
                    event.providerEventId(),
                    event.startsAt(),
                    event.homeTeam().name(),
                    event.awayTeam().name(),
                    event.status().type()));
        }
        return new TournamentCanonicalizationResult(inserted, deduplicated, events);
    }
}
