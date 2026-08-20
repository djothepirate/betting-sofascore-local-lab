package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.J6SnapshotHistoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Reads all current J7 component slots in one PostgreSQL repeatable-read snapshot. */
@Service
public class J7CurrentEventSelectionReader {

    private final CanonicalEventStore canonicalEventStore;
    private final EventDetailsStore eventDetailsStore;
    private final J5EventDataStore eventDataStore;
    private final J6SnapshotHistoryStore snapshotHistoryStore;

    public J7CurrentEventSelectionReader(
            CanonicalEventStore canonicalEventStore,
            EventDetailsStore eventDetailsStore,
            J5EventDataStore eventDataStore,
            J6SnapshotHistoryStore snapshotHistoryStore) {
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore,
                "canonicalEventStore");
        this.eventDetailsStore = Objects.requireNonNull(eventDetailsStore, "eventDetailsStore");
        this.eventDataStore = Objects.requireNonNull(eventDataStore, "eventDataStore");
        this.snapshotHistoryStore = Objects.requireNonNull(
                snapshotHistoryStore,
                "snapshotHistoryStore");
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW)
    public Optional<J7CurrentEventSelection> load(UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        return canonicalEventStore.findLatestByCanonicalId(canonicalEventId)
                .map(eventState -> {
                    var eventDetails = eventDetailsStore.findLatest(canonicalEventId);
                    J5EventDataBundle eventData = eventDataStore.findLatest(canonicalEventId);
                    Set<Long> snapshotIds = new LinkedHashSet<>();
                    collectSnapshotId(snapshotIds, eventState.source());
                    eventDetails.ifPresent(value -> collectSnapshotId(
                            snapshotIds,
                            value.source()));
                    eventData.statistics().ifPresent(value -> collectSnapshotId(
                            snapshotIds,
                            value.source()));
                    eventData.incidents().ifPresent(value -> collectSnapshotId(
                            snapshotIds,
                            value.source()));
                    eventData.lineups().ifPresent(value -> collectSnapshotId(
                            snapshotIds,
                            value.source()));
                    return new J7CurrentEventSelection(
                            eventState,
                            eventDetails,
                            eventData,
                            snapshotHistoryStore.findTraces(Set.copyOf(snapshotIds)));
                });
    }

    private static void collectSnapshotId(Set<Long> snapshotIds, EventSourceTrace source) {
        if (source.snapshotId().isPresent()) {
            snapshotIds.add(source.snapshotId().getAsLong());
        }
    }
}
