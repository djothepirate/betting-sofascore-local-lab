package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.event.J4ScheduledEventsSnapshotSource;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J4ScheduledEventsSnapshotSourceStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class J4ScheduledEventsSnapshotNormalizationService {

    private final J4ScheduledEventsSnapshotSourceStore snapshotSourceStore;
    private final CanonicalEventStore canonicalEventStore;
    private final ScheduledEventsV1Parser parser;

    @Autowired
    public J4ScheduledEventsSnapshotNormalizationService(
            J4ScheduledEventsSnapshotSourceStore snapshotSourceStore,
            CanonicalEventStore canonicalEventStore) {
        this(snapshotSourceStore, canonicalEventStore, new ScheduledEventsV1Parser());
    }

    J4ScheduledEventsSnapshotNormalizationService(
            J4ScheduledEventsSnapshotSourceStore snapshotSourceStore,
            CanonicalEventStore canonicalEventStore,
            ScheduledEventsV1Parser parser) {
        this.snapshotSourceStore = Objects.requireNonNull(
                snapshotSourceStore,
                "snapshotSourceStore");
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore,
                "canonicalEventStore");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Transactional
    public J4SnapshotNormalizationResult normalize(long snapshotId) {
        if (snapshotId < 1) {
            throw new J4SnapshotNormalizationException(
                    J4SnapshotNormalizationError.INVALID_SELECTION);
        }
        J4ScheduledEventsSnapshotSource source;
        try {
            source = snapshotSourceStore.findById(snapshotId).orElseThrow(() ->
                    new J4SnapshotNormalizationException(
                            J4SnapshotNormalizationError.SNAPSHOT_NOT_FOUND));
        }
        catch (J4SnapshotNormalizationException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw new J4SnapshotNormalizationException(
                    J4SnapshotNormalizationError.LOCAL_DATABASE_UNAVAILABLE,
                    exception);
        }

        if (!Sha256.hex(source.payloadRaw()).equals(source.payloadSha256())) {
            throw new J4SnapshotNormalizationException(
                    J4SnapshotNormalizationError.PAYLOAD_INTEGRITY_FAILURE);
        }

        var parseResult = parser.parseTransportResponse(source.asTransportResponse());
        if (parseResult.status() != ScheduledEventsParseStatus.PARSED) {
            return new J4SnapshotNormalizationResult(
                    snapshotId,
                    source.historicalSchemaStatus(),
                    parseResult.status(),
                    "NONE",
                    0,
                    0,
                    0,
                    List.of());
        }

        ScheduledEventsPage page = parseResult.page().orElseThrow();
        EventSourceTrace trace = EventSourceTrace.providerSnapshot(
                source.snapshotId(),
                source.payloadSha256(),
                ScheduledEventsV1Parser.PARSER_VERSION,
                source.receivedAt());
        int inserted = 0;
        int deduplicated = 0;
        List<UUID> canonicalIds = new ArrayList<>();
        for (var event : page.events()) {
            var persistence = canonicalEventStore.save(
                    CanonicalEventObservation.from(event, trace));
            if (persistence.inserted()) {
                inserted++;
            }
            else {
                deduplicated++;
            }
            canonicalIds.add(persistence.canonicalEventId());
        }
        return new J4SnapshotNormalizationResult(
                snapshotId,
                source.historicalSchemaStatus(),
                parseResult.status(),
                page.payloadShape().name(),
                page.events().size(),
                inserted,
                deduplicated,
                canonicalIds);
    }
}
